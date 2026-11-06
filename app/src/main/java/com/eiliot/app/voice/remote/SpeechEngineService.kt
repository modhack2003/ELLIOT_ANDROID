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
    /** Recognizers that outlive a single request, keyed by model and language. */
    private val resident=ConcurrentHashMap<String,Long>()
    /** Handles the client may not release; the worker owns their lifetime. */
    private val pinned=ConcurrentHashMap.newKeySet<Long>()
    /** Packs already hash-verified in this process. */
    private val verified=ConcurrentHashMap.newKeySet<String>()
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
                val kind=if(a.getBoolean("phoneme_model"))"phoneme_kws" else "kws"
                val folder=contract.spec(kind).folder
                val requireHey=a.getBoolean("require_hey")
                val keywordFile=if(requireHey)"keywords-hey.txt" else "keywords.txt"
                out.putLong("id",hold(KeywordSpotter(config=KeywordSpotterConfig(
                    modelConfig=model(kind),keywordsFile="${root.path}/$folder/$keywordFile",
                    keywordsScore=setting.score,keywordsThreshold=setting.threshold,
                    maxActivePaths=if(kind=="kws" && requireHey)8 else 4,numTrailingBlanks=1
                ))))
            }
            "asr"->out.putLong("id",hold(OnlineRecognizer(config=OnlineRecognizerConfig(modelConfig=model("asr"),decodingMethod="modified_beam_search",maxActivePaths=4,hotwordsScore=1.5f,enableEndpoint=true,endpointConfig=EndpointConfig(EndpointRule(false,2.4f,0f),EndpointRule(true,1.4f,0f),EndpointRule(false,0f,60f))))))
            "wake_phones"->out.putLong("id",hold(OnlineRecognizer(config=OnlineRecognizerConfig(
                modelConfig=model("phoneme_kws"),decodingMethod="modified_beam_search",
                maxActivePaths=4,enableEndpoint=false
            ))))
            "phone_tokens"->out.putStringArray("tokens",obj<OnlineRecognizer>(id).getResult(obj(sid)).tokens)
            // Building this recognizer costs seconds: a multi-hundred-megabyte pack is read and
            // hashed, the token table is rewritten and an ONNX session is created. Keep one
            // resident so only decoding is paid per request, the way the TTS engine already works.
            "whisper"->synchronized(resident) {
                val language=a.getString("language").orEmpty();require(language in setOf("","en","hi","bn"))
                val spec=MultilingualModelPack.spec(a.getString("model")?:MultilingualModelPack.SMALL.id)
                val key="${spec.id}|$language"
                val cached=resident[key]?.takeIf { objects.containsKey(it) }
                if(cached!=null) {out.putLong("id",cached);return@synchronized}
                // Only one multilingual recognizer stays loaded; switching packs frees the old weights.
                for((other,id) in resident.entries.toList()) {
                    if(other==key)continue
                    resident.remove(other);pinned.remove(id)
                    (objects.remove(id) as? OfflineRecognizer)?.let {runCatching {it.release()}}
                }
                // Full hashing is a download-time and first-load guarantee, not a per-request one.
                val pack=if(verified.add(spec.id))MultilingualModelPack.verify(applicationContext,spec)
                    else MultilingualModelPack.directory(applicationContext,spec).also {
                        check(MultilingualModelPack.available(applicationContext,spec)) {"Prepare multilingual speech in Settings first"}
                    }
                val id=hold(OfflineRecognizer(config=OfflineRecognizerConfig(modelConfig=OfflineModelConfig(
                    whisper=OfflineWhisperModelConfig(encoder=File(pack,spec.encoder).path,decoder=File(pack,spec.decoder).path,language=language,task="transcribe"),
                    tokens=WhisperTokenTransport.prepare(File(pack,spec.tokens),File(cacheDir,"whisper-text/${spec.id}")).path,numThreads=4,provider="cpu"))))
                resident[key]=id;pinned.add(id)
                out.putLong("id",id)
            }
            "vad"->out.putLong("id",hold(Vad(config=VadModelConfig(sileroVadModelConfig=SileroVadModelConfig(model="${root.path}/silero_vad.onnx",threshold=.65f,minSilenceDuration=.5f,minSpeechDuration=.192f,windowSize=512),sampleRate=16000,numThreads=1))))
            "denoiser"->out.putLong("id",hold(OnlineSpeechDenoiser(config=OnlineSpeechDenoiserConfig(model=OfflineSpeechDenoiserModelConfig(
                gtcrn=OfflineSpeechDenoiserGtcrnModelConfig(model="${root.path}/denoise/gtcrn.onnx"),numThreads=1,provider="cpu")))))
            "denoise_reset"->obj<OnlineSpeechDenoiser>(id).reset()
            "denoise","denoise_flush"->{
                val denoiser=obj<OnlineSpeechDenoiser>(id)
                val cleaned=if(op=="denoise_flush")denoiser.flush()else {
                    val pcm=checkNotNull(a.getFloatArray("pcm"));require(pcm.size in 1..4096 && pcm.all(Float::isFinite))
                    denoiser.run(pcm,16000)
                }
                check(cleaned.sampleRate==16000 && cleaned.samples.size<=8192 && cleaned.samples.all(Float::isFinite))
                out.putFloatArray("pcm",cleaned.samples)
            }
            "stream"->out.putLong("id",hold(when(val owner=objects[id]) {
                is KeywordSpotter->{
                    val keywords=a.getString("hotwords").orEmpty()
                    if(keywords.isNotBlank()) {
                        require(keywords.length<=2048)
                        val tokens=File(owner.config.modelConfig.tokens).readLines()
                            .map {it.substringBeforeLast(' ')}.toSet()
                        require(keywords.lines().all {line->
                            val label=line.substringAfter('@',"")
                            label.matches(Regex("[a-z_]{1,32}")) &&
                                line.substringBefore('@').trim().split(' ').all {it in tokens}
                        }) {"Invalid wake pronunciation"}
                    }
                    owner.createStream(keywords)
                }
                is OnlineRecognizer->owner.createStream(a.getString("hotwords").orEmpty())
                is OfflineRecognizer->owner.createStream()
                else->error("Invalid stream owner")
            }))
            "accept"->{val pcm=checkNotNull(a.getFloatArray("pcm"));require(pcm.size<=16000);when(val stream=objects[id]){is OnlineStream->stream.acceptWaveform(pcm,16000);is OfflineStream->stream.acceptWaveform(pcm,16000);else->error("Invalid speech stream")}}
            "ready"->out.putBoolean("value",when(val owner=objects[id]) {is KeywordSpotter->owner.isReady(obj(sid));is OnlineRecognizer->owner.isReady(obj(sid));else->false})
            "decode"->when(val owner=objects[id]) {is KeywordSpotter->owner.decode(obj(sid));is OnlineRecognizer->owner.decode(obj(sid));is OfflineRecognizer->owner.decode(obj(sid));else->error("Invalid decoder")}
            "result"->out.putString("text",when(val owner=objects[id]) {is KeywordSpotter->owner.getResult(obj(sid)).keyword;is OnlineRecognizer->owner.getResult(obj(sid)).text;is OfflineRecognizer->WhisperTokenTransport.decode(owner.getResult(obj(sid)).text);else->""})
            "endpoint"->out.putBoolean("value",obj<OnlineRecognizer>(id).isEndpoint(obj(sid)))
            "reset"->when(val owner=objects[id]) {is KeywordSpotter->owner.reset(obj(sid));is OnlineRecognizer->owner.reset(obj(sid));is Vad->owner.reset();else->error("Invalid reset")}
            "compute"->{val pcm=checkNotNull(a.getFloatArray("pcm"));require(pcm.size==512);out.putFloat("value",obj<Vad>(id).compute(pcm))}
            // Streams are still released normally; only the resident recognizers are pinned.
            "release"->if(id !in pinned) when(val item=objects.remove(id)) {is OnlineStream->item.release();is OfflineStream->item.release();is KeywordSpotter->item.release();is OnlineRecognizer->item.release();is OfflineRecognizer->item.release();is OnlineSpeechDenoiser->item.release();is Vad->item.release()}
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
