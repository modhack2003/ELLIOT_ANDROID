package com.eiliot.app.voice.remote

import android.app.Service
import android.content.Intent
import android.os.*
import com.eiliot.app.voice.*
import com.k2fsa.sherpa.onnx.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** JNI is confined to :speech. This process never initializes the companion runtime. */
class SpeechEngineService:Service() {
    private val ids=AtomicLong()
    private val objects=ConcurrentHashMap<Long,Any>()
    private val ttsLock=Any()
    private var tts:OfflineTts?=null
    private var initialized=false
    private lateinit var root:File
    private lateinit var contract:VoiceModelContract
    private fun hold(value:Any)=ids.incrementAndGet().also {objects[it]=value}
    @Suppress("UNCHECKED_CAST") private fun <T> obj(id:Long)=checkNotNull(objects[id]) {"Speech session expired"} as T
    private fun model(kind:String):OnlineModelConfig {
        val s=contract.spec(kind)
        return OnlineModelConfig(transducer=OnlineTransducerModelConfig(encoder="${root.path}/${s.folder}/encoder.onnx",decoder="${root.path}/${s.folder}/decoder.onnx",joiner="${root.path}/${s.folder}/joiner.onnx"),
            tokens="${root.path}/${s.folder}/tokens.txt",numThreads=contract.threads,provider="cpu",modelType=s.family,modelingUnit=s.modelingUnit,bpeVocab=if(s.vocabulary.isBlank())"" else "${root.path}/${s.vocabulary}")
    }
    private val binder=object:Binder() {
        override fun onTransact(code:Int,data:Parcel,reply:Parcel?,flags:Int):Boolean {
            if(code!=SpeechWire.REQUEST)return super.onTransact(code,data,reply,flags)
            check(Binder.getCallingUid()==android.os.Process.myUid())
            data.enforceInterface(SpeechWire.TOKEN)
            val op=data.readString().orEmpty();val a=data.readBundle(javaClass.classLoader)?:Bundle();val callback=data.readStrongBinder()
            val result=try { dispatch(op,a,callback) }catch(e:Exception) {Bundle().apply {putString("error",e.message?.take(200)?:"Speech operation failed")}}
            reply?.writeNoException();reply?.writeBundle(result);return true
        }
    }
    override fun onBind(intent:Intent):IBinder=binder
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int)=START_NOT_STICKY
    private fun dispatch(op:String,a:Bundle,callback:IBinder?):Bundle {
        val out=Bundle();val id=a.getLong("id");val sid=a.getLong("stream")
        if(op=="hello") {
            synchronized(this) {
                if(!initialized) {
                    root=ModelAssets.directory(applicationContext)
                    VoiceModelContract.verify(applicationContext,root)
                    contract=VoiceModelContract.load(applicationContext);initialized=true
                }
            }
            out.putInt("pid",android.os.Process.myPid());return out
        }
        check(initialized)
        when(op) {
            "kws"->{
                val setting=WakeSetting(false,a.getFloat("score",2f),a.getFloat("threshold",.15f))
                out.putLong("id",hold(KeywordSpotter(config=KeywordSpotterConfig(modelConfig=model("kws"),keywordsFile="${root.path}/kws/keywords.txt",keywordsScore=setting.score,keywordsThreshold=setting.threshold,numTrailingBlanks=1))))
            }
            "asr"->out.putLong("id",hold(OnlineRecognizer(config=OnlineRecognizerConfig(modelConfig=model("asr"),decodingMethod="modified_beam_search",maxActivePaths=4,hotwordsScore=1.5f,enableEndpoint=true,endpointConfig=EndpointConfig(EndpointRule(false,2.4f,0f),EndpointRule(true,1.4f,0f),EndpointRule(false,0f,60f))))))
            "whisper"->{
                val language=a.getString("language").orEmpty();require(language in setOf("","en","hi","bn"))
                val spec=MultilingualModelPack.spec(a.getString("model")?:MultilingualModelPack.SMALL.id)
                val pack=MultilingualModelPack.verify(applicationContext,spec)
                out.putLong("id",hold(OfflineRecognizer(config=OfflineRecognizerConfig(modelConfig=OfflineModelConfig(
                    whisper=OfflineWhisperModelConfig(encoder=File(pack,spec.encoder).path,decoder=File(pack,spec.decoder).path,language=language,task="transcribe"),
                    tokens=File(pack,spec.tokens).path,numThreads=4,provider="cpu")))))
            }
            "vad"->out.putLong("id",hold(Vad(config=VadModelConfig(sileroVadModelConfig=SileroVadModelConfig(model="${root.path}/silero_vad.onnx",threshold=.65f,minSilenceDuration=.5f,minSpeechDuration=.192f,windowSize=512),sampleRate=16000,numThreads=1))))
            "stream"->out.putLong("id",hold(when(val owner=objects[id]) {is KeywordSpotter->owner.createStream();is OnlineRecognizer->owner.createStream(a.getString("hotwords").orEmpty());is OfflineRecognizer->owner.createStream();else->error("Invalid stream owner")}))
            "accept"->{val pcm=checkNotNull(a.getFloatArray("pcm"));require(pcm.size<=16000);when(val stream=objects[id]){is OnlineStream->stream.acceptWaveform(pcm,16000);is OfflineStream->stream.acceptWaveform(pcm,16000);else->error("Invalid speech stream")}}
            "ready"->out.putBoolean("value",when(val owner=objects[id]) {is KeywordSpotter->owner.isReady(obj(sid));is OnlineRecognizer->owner.isReady(obj(sid));else->false})
            "decode"->when(val owner=objects[id]) {is KeywordSpotter->owner.decode(obj(sid));is OnlineRecognizer->owner.decode(obj(sid));is OfflineRecognizer->owner.decode(obj(sid));else->error("Invalid decoder")}
            "result"->out.putString("text",when(val owner=objects[id]) {is KeywordSpotter->owner.getResult(obj(sid)).keyword;is OnlineRecognizer->owner.getResult(obj(sid)).text;is OfflineRecognizer->owner.getResult(obj(sid)).text;else->""})
            "endpoint"->out.putBoolean("value",obj<OnlineRecognizer>(id).isEndpoint(obj(sid)))
            "reset"->when(val owner=objects[id]) {is KeywordSpotter->owner.reset(obj(sid));is OnlineRecognizer->owner.reset(obj(sid));is Vad->owner.reset();else->error("Invalid reset")}
            "compute"->{val pcm=checkNotNull(a.getFloatArray("pcm"));require(pcm.size==512);out.putFloat("value",obj<Vad>(id).compute(pcm))}
            "release"->when(val item=objects.remove(id)) {is OnlineStream->item.release();is OfflineStream->item.release();is KeywordSpotter->item.release();is OnlineRecognizer->item.release();is OfflineRecognizer->item.release();is Vad->item.release()}
            "tts"->synchronized(ttsLock) {
                if(tts==null)tts=OfflineTts(config=OfflineTtsConfig(model=OfflineTtsModelConfig(vits=OfflineTtsVitsModelConfig(model="${root.path}/tts/model.onnx",tokens="${root.path}/tts/tokens.txt",dataDir="${root.path}/tts/espeak-ng-data"),numThreads=contract.threads,provider="cpu"),maxNumSentences=1))
                out.putInt("rate",tts!!.sampleRate());out.putInt("speakers",tts!!.numSpeakers())
            }
            "generate"->synchronized(ttsLock) {
                val target=checkNotNull(callback);val text=a.getString("text").orEmpty();require(text.length<=60000)
                checkNotNull(tts).generateWithCallback(text,0,a.getFloat("speed",1f).coerceIn(.7f,1.4f), NativeTtsCallback { pcm ->
                    var proceed=1;var offset=0
                    while(offset<pcm.size && proceed!=0) {
                        val count=minOf(4096,pcm.size-offset)
                        proceed=SpeechWire.call(target,"pcm",Bundle().apply {putFloatArray("pcm",pcm.copyOfRange(offset,offset+count))}).getInt("continue")
                        offset+=count
                    };proceed
                })
            }
            else->error("Unsupported speech request")
        }
        return out
    }
    override fun onDestroy() {
        // Killing this private worker also stops any JNI call stuck outside Kotlin cancellation.
        super.onDestroy();android.os.Process.killProcess(android.os.Process.myPid())
    }
}
