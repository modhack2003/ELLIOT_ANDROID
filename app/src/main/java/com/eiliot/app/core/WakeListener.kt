package com.eiliot.app.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.eiliot.app.voice.*
import com.eiliot.app.tools.PhoneTools
import com.eiliot.app.overlay.OverlayManager
import com.eiliot.app.voice.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/** One capture: restricted KWS at idle; full streaming ASR only during an utterance. */
object WakeListener {
    val status=MutableStateFlow("Preparing offline listening")
    val echoStatus=MutableStateFlow("Echo cancellation is checked when voice starts")
    val liveCall=VoiceSession.gate.isLiveCallMode
    private val main=Handler(Looper.getMainLooper())
    private var capture:Job?=null
    @Volatile private var input:VoiceAudioInput?=null
    @Volatile private var running=false
    @Volatile private var generation=0L
    @Volatile private var ready=false
    private data class Turn(val ticket:Long,val epoch:Long,val origin:String,val deadline:ListeningDeadline)
    @Volatile private var turn:Turn?=null
    private var timeout:Runnable?=null
    private var lastTrigger=0L
    private val activationPending=AtomicBoolean(false)
    private val reset=AtomicBoolean(false)
    private val resetMode=AtomicBoolean(false)
    private val resume=Runnable { resumeWhenIdle() }
    fun hasModel(c:Context)=runCatching { c.assets.open("models/manifest.json").close();true }.getOrDefault(false)
    fun available()=running && ready && input!=null && capture?.isActive==true && SentinelRuntime.microphoneReady
    fun setLiveCall(context:Context,enabled:Boolean):Boolean {
        if(enabled && (!SentinelRuntime.alive() || !SentinelRuntime.microphoneReady)) { status.value="Allow the microphone and turn ELLIOT on to start a call";return false }
        VoiceSession.gate.setLiveCallMode(enabled)
        resetMode.set(true)
        if(enabled) { running=true;resumeWhenIdle() }
        else { EiliotBrain.cancelCurrent();if(!SentinelRuntime.store.wakeEnabled)stop() }
        return true
    }
    fun start(context:Context) {
        if(!SentinelRuntime.alive() || (!SentinelRuntime.store.wakeEnabled && !liveCall.value))return
        if(!SentinelRuntime.microphoneReady) { status.value="Open ELLIOT to allow voice";return }
        running=true;resumeWhenIdle()
    }
    fun beginInput(origin:String,expectsWake:Boolean=false):Boolean {
        if(!available() || !SentinelRuntime.alive())return false
        cancelTurn()
        val ticket=VoiceIo.beginLocalListening(origin)
        val deadline=ListeningDeadline(android.os.SystemClock.elapsedRealtime(),if(origin=="live call")75000 else 18000)
        turn=Turn(ticket,SentinelRuntime.epoch,origin,deadline);reset.set(true)
        val expiry=object:Runnable {
            override fun run() {
                if(turn?.ticket!=ticket)return
                val remaining=deadline.remaining(android.os.SystemClock.elapsedRealtime())
                if(remaining>0) {main.postDelayed(this,remaining);return}
                turn=null;timeout=null;VoiceIo.endLocalListening(ticket);EiliotBrain.notice("I'm here when you need me")
            }
        }
        timeout=expiry;main.postDelayed(expiry,deadline.remaining(android.os.SystemClock.elapsedRealtime()))
        status.value=if(liveCall.value)"Live call · listening" else "Listening to your request"
        return true
    }
    fun cancelTurn() { timeout?.let(main::removeCallbacks);timeout=null;turn=null;reset.set(true) }
    fun suspendCapture() {
        generation++;ready=false;cancelTurn();main.removeCallbacks(resume)
        val old=input;input=null;old?.stopCapture();capture?.cancel()
    }
    fun resumeWhenIdle() {
        main.removeCallbacks(resume)
        if(!running || !SentinelRuntime.alive() || (!SentinelRuntime.store.wakeEnabled && !liveCall.value))return
        if(NativeSpeechClient.blocked(SentinelRuntime.context)) {status.value="Offline speech needs Repair offline voice in Settings";return}
        if(VoiceIo.usesDeviceRecognizer()) { main.postDelayed(resume,500);return }
        if(capture?.isActive==true)return
        val old=capture;val gen=++generation;val epoch=SentinelRuntime.epoch
        capture=SentinelRuntime.scope.launch(Dispatchers.IO) {
            old?.join()
            var kws:RemoteKeywordSpotter?=null;var kwsStream:RemoteStream?=null
            var recognizer:RemoteRecognizer?=null;var stream:RemoteStream?=null;var vad:RemoteVad?=null
            var audio:VoiceAudioInput?=null
            try {
                status.value="Getting your offline voice ready"
                val root=ModelAssets.await(SentinelRuntime.context)
                NativeSpeechClient.connect(SentinelRuntime.context)
                val spotter=RemoteKeywordSpotter()
                kws=spotter;val keywordStream=spotter.createStream();kwsStream=keywordStream
                val asr=RemoteRecognizer()
                recognizer=asr
                val detector=RemoteVad()
                vad=detector
                val preroll=AudioPreRoll();val onset=BargeInDetector();var decodedTicket=Long.MIN_VALUE
                val apps=withContext(Dispatchers.Main) { PhoneTools.apps().take(80).map { it.name } }
                val hotwords=(listOf("ELLIOT","BATTERY","VOLUME","TIMER","SCROLL","CLICK","YOUTUBE","WHATSAPP")+apps.map { it.uppercase().replace(Regex("[^A-Z0-9 ]")," ") }).filter(String::isNotBlank).distinct().joinToString("\n")
                ensureActive();check(gen==generation && SentinelRuntime.alive(epoch))
                audio=VoiceAudioInput(SentinelRuntime.context,com.eiliot.app.BuildConfig.VOICE_REFERENCE_CAPTURE) { }
                ensureActive();check(gen==generation && SentinelRuntime.alive(epoch))
                input=audio;ready=true
                if(com.eiliot.app.BuildConfig.VOICE_DIAGNOSTICS)android.util.Log.i("ElliotWake", "capture ready; live=${liveCall.value}; wake=${SentinelRuntime.store.wakeEnabled}; echo=${audio.echoEnabled}")
                var diagnosticAt=android.os.SystemClock.elapsedRealtime()
                var diagnosticFrames=0
                var diagnosticPeak=0f
                var diagnosticEnergy=0.0
                echoStatus.value=if(audio.echoEnabled)"Device echo cancellation is on" else "Headphones can help avoid speaker echo"
                audio.onFrame={ frame ->
                    if(gen!=generation || !SentinelRuntime.alive(epoch))throw CancellationException()
                    if(com.eiliot.app.BuildConfig.VOICE_DIAGNOSTICS) {
                        diagnosticFrames++
                        for(sample in frame) {diagnosticPeak=maxOf(diagnosticPeak,kotlin.math.abs(sample));diagnosticEnergy+=sample.toDouble()*sample}
                    }
                    val diagnosticNow=android.os.SystemClock.elapsedRealtime()
                    if(com.eiliot.app.BuildConfig.VOICE_DIAGNOSTICS && diagnosticNow-diagnosticAt>=5000) {
                        if(com.eiliot.app.BuildConfig.VOICE_DIAGNOSTICS)android.util.Log.i("ElliotWake", "frames=$diagnosticFrames elapsedMs=${diagnosticNow-diagnosticAt} peak=$diagnosticPeak rms=${kotlin.math.sqrt(diagnosticEnergy/(diagnosticFrames*512))} live=${liveCall.value} turn=${turn?.origin ?: "idle"}")
                        diagnosticAt=diagnosticNow;diagnosticFrames=0;diagnosticPeak=0f;diagnosticEnergy=0.0
                    }
                    if(resetMode.getAndSet(false)) {
                        spotter.reset(keywordStream);detector.reset();onset.reset();preroll.clear()
                    }
                    preroll.add(frame)
                    val probability=detector.compute(frame)
                    val current=turn
                    if(probability>.65f)current?.deadline?.speech(android.os.SystemClock.elapsedRealtime())
                    val eligible=liveCall.value && current==null && !VoiceIo.usesDeviceRecognizer()
                    if(onset.accept(probability,eligible) && activationPending.compareAndSet(false,true)) {
                        main.post {
                            try {
                                if(gen==generation && SentinelRuntime.alive(epoch) && liveCall.value && turn==null) {
                                    if(VoiceSession.gate.interruptForBargeIn(epoch) || (!EiliotBrain.state.value.busy && !VoiceIo.isSpeaking.value)) {
                                        EiliotBrain.interruptForBargeIn();beginInput("live call")
                                    }
                                }
                            }finally { activationPending.set(false) }
                        }
                    }
                    if(current==null && !liveCall.value) {
                        keywordStream.acceptWaveform(frame,16000)
                        drainWakeResults({spotter.isReady(keywordStream)},{spotter.decode(keywordStream)},
                            {spotter.getResult(keywordStream).keyword},{spotter.reset(keywordStream)}) { wake ->
                        if(wake.isNotBlank())if(com.eiliot.app.BuildConfig.VOICE_DIAGNOSTICS)android.util.Log.i("ElliotWake", "wake detected: $wake")
                        if(!liveCall.value && wake.isNotBlank() && activationPending.compareAndSet(false,true)) {
                            main.post {
                                try {
                                    val now=android.os.SystemClock.elapsedRealtime()
                                    if(gen==generation && SentinelRuntime.alive(epoch) && now-lastTrigger>=900) {
                                        lastTrigger=now;EiliotBrain.interruptForWake();val started=beginInput("wake")
                                        if(com.eiliot.app.BuildConfig.VOICE_DIAGNOSTICS)android.util.Log.i("ElliotWake", "wake listening started=$started")
                                        if(!SentinelRuntime.foreground)OverlayManager.openPanel()
                                    }
                                }finally { activationPending.set(false) }
                            }
                        }
                        }
                    } else if(current!=null && VoiceSession.gate.accepts(current.ticket,current.epoch)) {
                        val needsReset=reset.getAndSet(false)
                        if(decodedTicket!=current.ticket || needsReset) {
                            stream?.release();stream=asr.createStream(hotwords);decodedTicket=current.ticket
                            stream!!.acceptWaveform(preroll.snapshot(),16000)
                        }else stream!!.acceptWaveform(frame,16000)
                        val s=stream!!
                        while(asr.isReady(s))asr.decode(s)
                        val heard=asr.getResult(s).text.trim()
                        main.post { if(turn?.ticket==current.ticket)VoiceIo.localTranscript(current.ticket,heard) }
                        if(asr.isEndpoint(s) && heard.isNotBlank()) {
                            asr.reset(s)
                            main.post {
                                if(turn?.ticket==current.ticket && VoiceSession.gate.accepts(current.ticket,current.epoch)) {
                                    val command=TranscriptNormalizer.normalize(WakeWords.command(heard),apps)
                                    if(command.isNotBlank()) {
                                        cancelTurn();VoiceIo.endLocalListening(current.ticket)
                                        EiliotBrain.receiveVoice(command)
                                    } else {
                                        // The pre-roll can contain just the wake phrase. Keep the
                                        // same listening ticket open for the following command.
                                        VoiceIo.localTranscript(current.ticket, "")
                                        if(com.eiliot.app.BuildConfig.VOICE_DIAGNOSTICS)android.util.Log.i("ElliotWake", "wake-only utterance; waiting for command")
                                    }
                                }
                            }
                        }
                    }
                }
                status.value=if(liveCall.value)"Live call · speak naturally" else "Say Hey Elliot"
                val bytes=ByteArray(1024)
                while(isActive && gen==generation && SentinelRuntime.alive(epoch)) { if(audio.read(bytes,0,bytes.size)<0)break }
            } catch(e:CancellationException) { throw e }
            catch(e:Throwable) {
                if(e is VirtualMachineError)throw e
                android.util.Log.e("ElliotWake", "capture failed: ${e.javaClass.simpleName}")
                if(gen==generation) {
                    status.value="Voice is unavailable. Open Settings to retry.";liveCall.value=false
                    main.post { if(gen==generation) {val pending=turn;cancelTurn();pending?.let {VoiceIo.endLocalListening(it.ticket)}} }
                }
            } finally {
                audio?.onFrame=null;runCatching { audio?.close() }
                runCatching { stream?.release() };runCatching { kwsStream?.release() };runCatching { recognizer?.release() };runCatching { kws?.release() };runCatching { vad?.release() }
                if(gen==generation) { input=null;ready=false }
            }
        }
    }
    fun stop() {
        running=false;liveCall.value=false;suspendCapture();activationPending.set(false)
        status.value="Voice listening is off"
    }
}

