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

/** One microphone capture, with restricted keyword spotting at idle. */
object WakeListener {
    @Volatile var calibrationActive=false
    val status=MutableStateFlow("Preparing offline listening")
    val echoStatus=MutableStateFlow("Echo cancellation is checked when voice starts")
    val liveCall=VoiceSession.gate.isLiveCallMode
    private val main=Handler(Looper.getMainLooper())
    private var capture:Job?=null
    @Volatile private var input:VoiceAudioInput?=null
    @Volatile private var running=false
    @Volatile private var generation=0L
    @Volatile private var ready=false
    private data class Turn(
        val ticket:Long,val epoch:Long,val origin:String,val deadline:ListeningDeadline,
        val includePreRoll:Boolean,val commandSpeech:CommandSpeech?
    )
    @Volatile private var turn:Turn?=null
    private var timeout:Runnable?=null
    private var lastTrigger=0L
    private val activationPending=AtomicBoolean(false)
    private val reset=AtomicBoolean(false)
    private val resetMode=AtomicBoolean(false)
    private val resume=Runnable { resumeWhenIdle() }
    fun hasModel(c:Context)=runCatching { c.assets.open("models/manifest.json").close();true }.getOrDefault(false)
    fun available()=!calibrationActive && running && ready && input!=null && capture?.isActive==true && SentinelRuntime.microphoneReady
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
    fun beginInput(origin:String,includePreRoll:Boolean=true):Boolean {
        if(!available() || !SentinelRuntime.alive())return false
        cancelTurn()
        val commandSpeech=if(origin=="live call")null else CommandSpeech.load(SentinelRuntime.context)
        val ticket=VoiceIo.beginLocalListening(origin,commandSpeech?.label)
        val deadline=ListeningDeadline(android.os.SystemClock.elapsedRealtime(),if(origin=="live call")75000 else 18000)
        turn=Turn(ticket,SentinelRuntime.epoch,origin,deadline,includePreRoll,commandSpeech);reset.set(true)
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
    private fun finishCommand(current:Turn,heard:String?,error:String?=null) {
        if(turn?.ticket!=current.ticket || !VoiceSession.gate.accepts(current.ticket,current.epoch))return
        cancelTurn()
        VoiceIo.endLocalListening(current.ticket)
        if(heard.isNullOrBlank())EiliotBrain.notice(error ?: "No request recognized. Please try again.")
        else EiliotBrain.receiveVoice(heard)
    }
    fun suspendCapture() {
        generation++;ready=false;cancelTurn();main.removeCallbacks(resume)
        val old=input;input=null;old?.stopCapture();capture?.cancel()
    }
    fun resumeWhenIdle() {
        main.removeCallbacks(resume)
        if(calibrationActive)return
        if(!running || !SentinelRuntime.alive() || (!SentinelRuntime.store.wakeEnabled && !liveCall.value))return
        if(NativeSpeechClient.blocked(SentinelRuntime.context)) {status.value="Offline speech needs Repair offline voice in Settings";return}
        if(VoiceIo.usesDeviceRecognizer()) { main.postDelayed(resume,500);return }
        if(capture?.isActive==true)return
        val old=capture;val gen=++generation;val epoch=SentinelRuntime.epoch
        capture=SentinelRuntime.scope.launch(Dispatchers.IO) {
            old?.join()
            var kws:RemoteWakeDetector?=null
            var recognizer:RemoteRecognizer?=null;var stream:RemoteStream?=null;var vad:RemoteVad?=null
            var audio:VoiceAudioInput?=null
            var noiseReducer:RemoteDenoiser?=null
            var enhancedFrames:PcmFrameBuffer?=null
            var utterance:CommandUtterance?=null
            var commandTicket=Long.MIN_VALUE
            var decoderJob:Job?=null
            var decodingTicket=Long.MIN_VALUE
            try {
                status.value="Getting your offline voice ready"
                val root=ModelAssets.await(SentinelRuntime.context)
                NativeSpeechClient.connect(SentinelRuntime.context)
                val calibration=VoiceCalibrationStore.load(SentinelRuntime.context)
                val wakeSetting=calibration?:WakeSetting(false,requireHey=WakeCapturePreference.requireHey(SentinelRuntime.context))
                val spotter=RemoteWakeDetector(wakeSetting)
                kws=spotter
                var wakeWasIdle=true
                val asr=RemoteRecognizer()
                recognizer=asr
                val detector=RemoteVad()
                vad=detector
                val preroll=AudioPreRoll();val onset=BargeInDetector();var decodedTicket=Long.MIN_VALUE
                val apps=withContext(Dispatchers.Main) { PhoneTools.apps().take(80).map { it.name } }
                val hotwords=(listOf("ELLIOT","BATTERY","VOLUME","TIMER","SCROLL","CLICK","YOUTUBE","WHATSAPP")+apps.map { it.uppercase().replace(Regex("[^A-Z0-9 ]")," ") }).filter(String::isNotBlank).distinct().joinToString("\n")
                ensureActive();check(gen==generation && SentinelRuntime.alive(epoch))
                val direct=calibration?.referenceCapture?:
                    (WakeCapturePreference.reference(SentinelRuntime.context) || com.eiliot.app.BuildConfig.VOICE_REFERENCE_CAPTURE)
                val reduceNoise=calibration?.noiseReduction?:WakeCapturePreference.noiseReduction(SentinelRuntime.context)
                val enhanced=calibration?.softwareNoiseReduction?:WakeCapturePreference.softwareNoiseReduction(SentinelRuntime.context)
                val reducer=if(enhanced)RemoteDenoiser()else null;noiseReducer=reducer
                val assembled=PcmFrameBuffer();enhancedFrames=assembled
                audio=VoiceAudioInput(SentinelRuntime.context,direct,reduceNoise) { }
                ensureActive();check(gen==generation && SentinelRuntime.alive(epoch))
                input=audio;ready=true
                if(com.eiliot.app.BuildConfig.VOICE_DIAGNOSTICS)android.util.Log.i("ElliotWake", "capture ready; direct=$direct; live=${liveCall.value}; wake=${SentinelRuntime.store.wakeEnabled}; echo=${audio.echoEnabled}; noise=${audio.noiseEnabled}; gain=${audio.gainEnabled}; enhanced=$enhanced; wakeProfile=${wakeSetting.label}; requireHey=${wakeSetting.requireHey}")
                var diagnosticAt=android.os.SystemClock.elapsedRealtime()
                var diagnosticFrames=0
                var diagnosticPeak=0f
                var diagnosticEnergy=0.0
                echoStatus.value=if(audio.echoEnabled)"Device echo cancellation is on" else "Headphones can help avoid speaker echo"
                val processFrame:(FloatArray)->Unit=process@ { frame ->
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
                        spotter.reset();wakeWasIdle=true;detector.reset();onset.reset();preroll.clear()
                    }
                    preroll.add(frame)
                    val probability=detector.compute(frame)
                    val current=turn
                    if(current?.ticket!=commandTicket) {
                        utterance?.clear()
                        utterance=null
                    }
                    if(current?.ticket!=decodingTicket)decoderJob?.cancel()
                    if(probability>.65f && current?.commandSpeech==null)current?.deadline?.speech(android.os.SystemClock.elapsedRealtime())
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
                    val wakeIdle=current==null && !liveCall.value
                    if(wakeIdle && !wakeWasIdle)spotter.reset()
                    wakeWasIdle=wakeIdle
                    if(wakeIdle) {
                        val wake=spotter.accept(frame,probability)
                        if(wake!=null && com.eiliot.app.BuildConfig.VOICE_DIAGNOSTICS) {
                            android.util.Log.i("ElliotWake", "wake detected: $wake")
                        }
                        if(wake!=null && !liveCall.value && activationPending.compareAndSet(false,true)) {
                            main.post {
                                try {
                                    val now=android.os.SystemClock.elapsedRealtime()
                                    if(gen==generation && SentinelRuntime.alive(epoch) && now-lastTrigger>=900) {
                                        lastTrigger=now
                                        EiliotBrain.interruptForWake()
                                        val started=beginInput("wake",includePreRoll=wake!=ACOUSTIC_WAKE)
                                        if(com.eiliot.app.BuildConfig.VOICE_DIAGNOSTICS)android.util.Log.i("ElliotWake", "wake listening started=$started")
                                        if(!SentinelRuntime.foreground)OverlayManager.openPanel()
                                    }
                                }finally {activationPending.set(false)}
                            }
                        }
                    } else if(current!=null && VoiceSession.gate.accepts(current.ticket,current.epoch)) {
                        val needsReset=reset.getAndSet(false)
                        val commandSpeech=current.commandSpeech
                        if(commandSpeech!=null) {
                            if(decodingTicket==current.ticket)return@process
                            if(utterance==null || needsReset) {
                                stream?.release()
                                stream=null
                                utterance?.clear()
                                utterance=CommandUtterance(current.origin=="wake" && current.includePreRoll)
                                commandTicket=current.ticket
                            }
                            val recording=checkNotNull(utterance)
                            val result=recording.accept(frame,probability)
                            if(recording.speechStarted)current.deadline.speech(android.os.SystemClock.elapsedRealtime())
                            if(result==null)return@process
                            recording.clear()
                            utterance=null
                            val previous=decoderJob
                            decodingTicket=current.ticket
                            if(result is CommandUtterance.Result.TooLong) {
                                main.post {finishCommand(current,null,"Please keep this voice request under 12 seconds.")}
                                return@process
                            }
                            val pcm=(result as CommandUtterance.Result.Ready).pcm
                            main.post {
                                if(turn?.ticket==current.ticket)EiliotBrain.notice("Transcribing on this phone…")
                            }
                            decoderJob=launch {
                                try {
                                    previous?.join()
                                    ensureActive()
                                    val started=android.os.SystemClock.elapsedRealtime()
                                    val whisper=RemoteWhisper(commandSpeech.language,commandSpeech.model.id)
                                    val raw=try {whisper.transcribe(pcm)}finally {runCatching {whisper.release()}}
                                    if(com.eiliot.app.BuildConfig.VOICE_DIAGNOSTICS) {
                                        android.util.Log.i("ElliotWake","command model=${commandSpeech.model.id}; language=${commandSpeech.language}; samples=${pcm.size}; elapsedMs=${android.os.SystemClock.elapsedRealtime()-started}; recognized=${raw.isNotBlank()}")
                                    }
                                    val heard=raw.takeUnless { '\uFFFD' in it }?.let {TranscriptNormalizer.normalize(it,apps)}
                                    withContext(Dispatchers.Main) {finishCommand(current,heard)}
                                }catch(e:CancellationException) {
                                    throw e
                                }catch(e:Exception) {
                                    withContext(Dispatchers.Main) {
                                        finishCommand(current,null,"Offline transcription failed. Verify the selected speech pack in Settings.")
                                    }
                                }
                            }.also {job->job.invokeOnCompletion {pcm.fill(0f)}}
                            return@process
                        }
                        if(decodedTicket!=current.ticket || needsReset) {
                            stream?.release();stream=asr.createStream(hotwords);decodedTicket=current.ticket
                            stream!!.acceptWaveform(if(current.includePreRoll)preroll.snapshot()else frame,16000)
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
                audio.onFrame={frame->if(reducer==null)processFrame(frame)else assembled.push(reducer.filterChunk(frame),processFrame)}
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
                utterance?.clear()
                audio?.onFrame=null;runCatching { audio?.close() }
                enhancedFrames?.clear();runCatching{noiseReducer?.release()}
                runCatching { stream?.release() };runCatching { recognizer?.release() };runCatching { kws?.close() };runCatching { vad?.release() }
                if(gen==generation) { input=null;ready=false }
            }
        }
    }
    fun stop() {
        running=false;liveCall.value=false;suspendCapture();activationPending.set(false)
        status.value="Voice listening is off"
    }
}

