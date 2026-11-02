package com.eiliot.app.voice.remote

import android.content.*
import android.os.*
import com.eiliot.app.core.SentinelRuntime
import com.eiliot.app.voice.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Persist an interrupted startup or observed worker failure; normal OS restart can recover after a completed handshake. */
class SpeechFailureFence(private val prefs:android.content.SharedPreferences,private val key:String) {
    fun blocked()=prefs.getString("blocked",null)==key || prefs.getString("pending",null)==key
    fun begin() {check(!blocked()) {"Offline speech needs a manual retry"};check(prefs.edit().putString("pending",key).commit())}
    fun fail() {prefs.edit().putString("blocked",key).remove("pending").commit()}
    fun finish() {prefs.edit().remove("pending").commit()}
    fun retry() {prefs.edit().remove("pending").remove("blocked").commit()}
}
object NativeSpeechClient {
    private val mutex=Mutex()
    private val main=Handler(Looper.getMainLooper())
    @Volatile private var remote:IBinder?=null
    @Volatile private var generation=0L
    @Volatile private var initializing=false
    private var connection:ServiceConnection?=null
    private var context:Context?=null
    private var fence:SpeechFailureFence?=null
    fun androidVm()=System.getProperty("java.vm.name").orEmpty().contains("Dalvik",true)
    suspend fun connect(c:Context) = mutex.withLock {
        if(remote?.isBinderAlive==true)return@withLock
        check(androidVm()) {"Native speech needs an Android runtime"}
        val app=c.applicationContext;context=app
        check(!blocked(app)) {"Offline speech needs a manual retry in Settings"}
        val root=ModelAssets.await(app);VoiceModelContract.verify(app,root)
        currentCoroutineContext().ensureActive()
        val guard=SpeechFailureFence(app.getSharedPreferences("native_speech_health",Context.MODE_PRIVATE),VoiceModelContract.fingerprint(app));fence=guard
        guard.begin();initializing=true
        val token=++generation;val ready=CompletableDeferred<IBinder>()
        val binding=object:ServiceConnection {
            override fun onServiceConnected(name:ComponentName,service:IBinder) {
                if(token!=generation)return
                remote=service
                runCatching {service.linkToDeath({workerDied(token)},0)}.onFailure {ready.completeExceptionally(it)}
                ready.complete(service)
            }
            override fun onServiceDisconnected(name:ComponentName) {workerDied(token)}
            override fun onBindingDied(name:ComponentName) {workerDied(token);ready.completeExceptionally(IllegalStateException("Speech worker disconnected"))}
            override fun onNullBinding(name:ComponentName) {ready.completeExceptionally(IllegalStateException("Speech worker unavailable"))}
        }
        connection=binding
        try {
            withContext(Dispatchers.Main) {check(app.bindService(Intent(app,SpeechEngineService::class.java),binding,Context.BIND_AUTO_CREATE))}
            val service=withTimeout(15000) {ready.await()}
            withContext(Dispatchers.IO) {SpeechWire.call(service,"hello")}
            check(token==generation && service.isBinderAlive)
            guard.finish()
        }catch(e:CancellationException) {
            if(token==generation) {if(e is TimeoutCancellationException)workerDied(token)else disconnect(true)}
            throw e
        }catch(e:Exception) {if(token==generation)workerDied(token);throw e}
        finally {initializing=false}
    }
    val session:Long get()=generation
    fun blocked(c:Context)=!initializing && remote?.isBinderAlive!=true && SpeechFailureFence(c.getSharedPreferences("native_speech_health",Context.MODE_PRIVATE),VoiceModelContract.fingerprint(c)).blocked()
    fun call(op:String,a:Bundle=Bundle(),callback:IBinder?=null,expected:Long=generation):Bundle {
        check(expected==generation) {"Speech session expired"}
        val service=remote?:error("Offline speech is disconnected")
        return try {
            val result=SpeechWire.call(service,op,a,callback)
            check(expected==generation && service===remote) {"Speech session expired"};result
        }catch(e:android.os.RemoteException) {
            if(service===remote)workerDied(expected)
            throw IllegalStateException("Offline speech stopped. Use Repair offline voice in Settings.",e)
        }
    }
    private fun workerDied(token:Long) {
        val failedGeneration=synchronized(this) {
            if(token!=generation)return
            fence?.fail();disconnect(false);generation
        }
        main.post {
            if(generation!=failedGeneration)return@post
            NeuralSpeechPlayer.workerFailed()
            com.eiliot.app.core.WakeListener.stop()
            com.eiliot.app.core.WakeListener.status.value="Offline speech stopped. Repair offline voice in Settings."
            VoiceIo.stopListening();VoiceIo.stopSpeaking()
        }
    }
    @Synchronized private fun disconnect(clean:Boolean) {
        generation++;remote=null;initializing=false
        if(clean)fence?.finish()
        val app=context;val old=connection;connection=null
        if(app!=null && old!=null)runCatching {app.unbindService(old)}
    }
    fun shutdown() {disconnect(true);NeuralSpeechPlayer.workerFailed()}
    fun retry(c:Context) {
        com.eiliot.app.core.WakeListener.stop();VoiceIo.stopListening();VoiceIo.stopSpeaking()
        disconnect(true)
        SpeechFailureFence(c.getSharedPreferences("native_speech_health",Context.MODE_PRIVATE),VoiceModelContract.fingerprint(c)).retry()
        NeuralSpeechPlayer.workerFailed();NeuralSpeechPlayer.prepare(c)
        if(SentinelRuntime.alive() && SentinelRuntime.microphoneReady && SentinelRuntime.store.wakeEnabled)com.eiliot.app.core.WakeListener.start(c)
    }
}

data class SpeechHandle(val id:Long,val session:Long)
private fun newHandle(kind:String,options:Bundle=Bundle()):SpeechHandle {
    val token=NativeSpeechClient.session
    return SpeechHandle(NativeSpeechClient.call(kind,options,expected=token).getLong("id"),token)
}
open class RemoteObject(private val key:SpeechHandle) {
    val handle get()=key.id
    protected val session get()=key.session
    fun args(stream:RemoteStream?=null)=Bundle().apply {putLong("id",handle);stream?.let {putLong("stream",it.handle)}}
    protected fun rpc(op:String,args:Bundle=args())=NativeSpeechClient.call(op,args,expected=session)
    fun release() {rpc("release")}
}
class RemoteStream(key:SpeechHandle):RemoteObject(key) {
    fun acceptWaveform(pcm:FloatArray,rate:Int) {require(rate==16000);rpc("accept",args().apply {putFloatArray("pcm",pcm)})}
}
data class RemoteResult(val text:String) {val keyword get()=text}
open class RemoteDecoder(kind:String,options:Bundle=Bundle()):RemoteObject(newHandle(kind,options)) {
    fun createStream(hotwords:String="")=RemoteStream(SpeechHandle(rpc("stream",args().apply {putString("hotwords",hotwords)}).getLong("id"),session))
    fun isReady(stream:RemoteStream)=rpc("ready",args(stream)).getBoolean("value")
    fun decode(stream:RemoteStream) {rpc("decode",args(stream))}
    fun getResult(stream:RemoteStream)=RemoteResult(rpc("result",args(stream)).getString("text").orEmpty())
    fun reset(stream:RemoteStream) {rpc("reset",args(stream))}
}
class RemoteKeywordSpotter(score:Float?=null,threshold:Float?=null):RemoteDecoder("kws",Bundle().apply {
    score?.let {putFloat("score",it)};threshold?.let {putFloat("threshold",it)}
})
class RemoteRecognizer:RemoteDecoder("asr") {
    fun isEndpoint(stream:RemoteStream)=rpc("endpoint",args(stream)).getBoolean("value")
}
class RemoteVad:RemoteObject(newHandle("vad")) {
    fun compute(frame:FloatArray)=rpc("compute",args().apply {putFloatArray("pcm",frame)}).getFloat("value")
    fun reset() {rpc("reset")}
}
class RemoteTts {
    private val session=NativeSpeechClient.session
    private val info=NativeSpeechClient.call("tts",expected=session)
    fun sampleRate()=info.getInt("rate")
    fun numSpeakers()=info.getInt("speakers")
    fun generateWithCallback(text:String,speaker:Int,speed:Float,consume:(FloatArray)->Int) {
        require(speaker==0)
        val receiver=object:Binder() {
            override fun onTransact(code:Int,data:Parcel,reply:Parcel?,flags:Int):Boolean {
                if(code!=SpeechWire.REQUEST)return super.onTransact(code,data,reply,flags)
                check(Binder.getCallingUid()==android.os.Process.myUid());data.enforceInterface(SpeechWire.TOKEN)
                check(data.readString()=="pcm");val frame=data.readBundle(javaClass.classLoader)?.getFloatArray("pcm")?:FloatArray(0);data.readStrongBinder()
                require(frame.size<=4096)
                val proceed=if(session==NativeSpeechClient.session)consume(frame)else 0
                reply?.writeNoException();reply?.writeBundle(Bundle().apply {putInt("continue",proceed)});return true
            }
        }
        NativeSpeechClient.call("generate",Bundle().apply {putString("text",text);putFloat("speed",speed)},receiver,session)
    }
}
