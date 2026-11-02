package com.eiliot.app.voice

import android.content.Context
import android.media.*
import com.eiliot.app.core.SentinelRuntime
import com.eiliot.app.voice.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicLong

/** Bundled Piper male voice. Synthesis runs off-main; audio buffers can be flushed on interruption. */
object NeuralSpeechPlayer {
    const val VOICE_ID="elliot:ryan"
    val ready=MutableStateFlow(false)
    val amplitude=MutableStateFlow(0f)
    val status=MutableStateFlow("Preparing offline male voice")
    private var context:Context?=null
    private var engine:RemoteTts?=null
    private var setup:Job?=null
    private val nativeLock=Any()
    private val setupStateLock=Any()
    private val generations=AtomicLong()
    private val setupGeneration=AtomicLong()
    private data class Sentence(val text:String)
    private class Playback(val id:Long,val epoch:Long,val generation:Long) {
        val queue=Channel<Sentence>(Channel.UNLIMITED)
        val ledger=SpokenLedger()
        @Volatile var track:AudioTrack?=null
        @Volatile var frames=0L
        var characters=0
        var job:Job?=null
        fun played():Long=runCatching { track?.playbackHeadPosition?.toLong()?.and(0xffffffffL)?:0L }.getOrDefault(0L)
    }
    @Volatile private var active:Playback?=null
    private var lastId=Long.MIN_VALUE
    private var lastSpoken=""
    fun prepare(c:Context) {
        context=c.applicationContext
        if(ready.value || setup?.isActive==true)return
        val setupTicket=setupGeneration.incrementAndGet()
        setup=SentinelRuntime.scope.launch(Dispatchers.IO) {
            try {
                NativeSpeechClient.connect(c)
                val candidate=RemoteTts()
                check(candidate.sampleRate()>=16000 && candidate.numSpeakers()>0)
                ensureActive()
                synchronized(setupStateLock) {
                    check(setupTicket==setupGeneration.get())
                    engine=candidate;ready.value=true;status.value="ELLIOT · offline male voice"
                }
                withContext(Dispatchers.Main) {
                    if(setupTicket==setupGeneration.get()) {
                        VoiceIo.refreshVoices()
                        if(SentinelRuntime.alive() && SentinelRuntime.microphoneReady && SentinelRuntime.store.wakeEnabled)com.eiliot.app.core.WakeListener.start(c)
                    }
                }
            } catch(e:CancellationException) { throw e }
            catch(e:Throwable) { if(e is VirtualMachineError)throw e;status.value="Offline voice needs recovery. A confirmed system voice can be used instead." }
        }
    }
    fun workerFailed() {
        val old=synchronized(setupStateLock) {
            setupGeneration.incrementAndGet();ready.value=false;engine=null
            setup.also {setup=null}
        }
        old?.cancel();stop();VoiceIo.refreshVoices()
        status.value="Offline voice is paused. Use Repair offline voice in Settings."
    }
    fun begin(id:Long,epoch:Long,onStart:()->Unit,onDone:()->Unit,onFailure:()->Unit):Boolean {
        if(!ready.value)return false
        stop()
        val run=Playback(id,epoch,generations.incrementAndGet());active=run
        run.job=SentinelRuntime.scope.launch(Dispatchers.IO) {
            var focus:AudioFocusRequest?=null
            var successful=false
            fun valid()=active===run && generations.get()==run.generation && SentinelRuntime.alive(epoch)
            try {
                val c=checkNotNull(context);val t=checkNotNull(engine);val sampleRate=t.sampleRate()
                val attrs=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                val am=c.getSystemService(AudioManager::class.java)
                val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener { change -> if(valid() && (change==AudioManager.AUDIOFOCUS_LOSS || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))VoiceIo.stopSpeakingImmediately() }.build()
                check(am.requestAudioFocus(request)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED);focus=request
                val minimum=AudioTrack.getMinBufferSize(sampleRate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_FLOAT)
                check(minimum>0)
                val track=AudioTrack.Builder().setAudioAttributes(attrs).setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
                    .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(minimum,sampleRate/10*4)).build()
                run.track=track;check(track.state==AudioTrack.STATE_INITIALIZED)
                var started=false
                for(sentence in run.queue) {
                    ensureActive();if(!valid())break
                    var invalid=false
                    synchronized(nativeLock) {
                        t.generateWithCallback(sentence.text,0,SentinelRuntime.store.ttsRate.coerceIn(.7f,1.4f)) { samples ->
                            if(!valid())return@generateWithCallback 0
                            if(samples.any { !it.isFinite() }) { invalid=true;return@generateWithCallback 0 }
                            val level=if(samples.isEmpty())0f else kotlin.math.sqrt(samples.sumOf { (it*it).toDouble() }/samples.size).toFloat().times(3f).coerceIn(0f,1f)
                            SentinelRuntime.scope.launch { if(valid()) {amplitude.value=level;com.eiliot.app.overlay.OverlayManager.setOrbAmplitude(level)} }
                            if(!started) {
                                started=true
                                runCatching { track.playbackParams=android.media.PlaybackParams().setPitch(SentinelRuntime.store.ttsPitch.coerceIn(.7f,1.3f)).setSpeed(1f) }
                                track.play();SentinelRuntime.scope.launch { if(valid())onStart() }
                            }
                            var offset=0
                            while(offset<samples.size && valid()) {
                                val wrote=track.write(samples,offset,minOf(1024,samples.size-offset),AudioTrack.WRITE_BLOCKING)
                                if(wrote<=0)return@generateWithCallback 0
                                offset+=wrote;run.frames+=wrote
                            }
                            if(valid())1 else 0
                        }
                    }
                    check(!invalid) { "Invalid synthesized audio" }
                    if(valid())run.ledger.queued(sentence.text,run.frames)
                }
                while(valid() && run.played()<run.frames) { ensureActive();delay(10) }
                successful=valid()
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { status.value="Voice playback paused. Your reply is still in the conversation." }
            finally {
                val wasCurrent=active===run
                if(wasCurrent) { lastId=id;lastSpoken=run.ledger.completed(run.played());active=null;amplitude.value=0f }
                runCatching { run.track?.pause();run.track?.flush();run.track?.release() };run.track=null
                focus?.let { context?.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(it) }
                withContext(NonCancellable+Dispatchers.Main) { if(wasCurrent && generations.get()==run.generation && SentinelRuntime.alive(epoch)) { if(successful)onDone()else onFailure() } }
            }
        }
        return true
    }
    fun enqueue(id:Long,text:String):Boolean {
        val run=active?.takeIf { it.id==id && SentinelRuntime.alive(it.epoch) }?:return false
        val value=text.trim().take((60000-run.characters).coerceAtLeast(0))
        if(value.isEmpty())return false
        run.characters+=value.length
        return run.queue.trySend(Sentence(value)).isSuccess
    }
    fun finish(id:Long) { active?.takeIf { it.id==id }?.queue?.close() }
    fun spoken(id:Long):String = active?.takeIf { it.id==id }?.let { it.ledger.completed(it.played()) }?:if(lastId==id)lastSpoken else ""
    fun stop():String {
        val run=active?:return lastSpoken
        val spoken=run.ledger.completed(run.played());lastId=run.id;lastSpoken=spoken
        active=null;generations.incrementAndGet();run.queue.close();run.job?.cancel()
        amplitude.value=0f
        // Release is owned by the worker; flush immediately without waiting for native synthesis.
        runCatching { run.track?.pause();run.track?.flush() }
        return spoken
    }
}
