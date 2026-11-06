package com.eiliot.app.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.*
import android.speech.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import com.eiliot.app.core.*
import com.eiliot.app.overlay.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

data class VoiceOption(val id: String, val language: String, val network: Boolean, val quality: Int) {
    val label: String get() = "$language · ${if (network) "network" else "offline"} · $id"
}

object VoiceIo {
    private val main = Handler(Looper.getMainLooper())
    private var ctx: Context? = null
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var ready = false
    private var engineGeneration = 0L
    private var recognitionGeneration = 0L
    private var speechGeneration = 0L
    private var finalUtterance = ""
    private var done: (() -> Unit)? = null
    private var unavailable: (() -> Unit)? = null
    private var localTicket: Long? = null
    private var speechTicket = 0L
    private var speechEpoch = 0L
    private var focus: AudioFocusRequest? = null
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening
    val transcript = MutableStateFlow("")
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking
    val allVoices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val maleVoices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val status = MutableStateFlow("Loading device voices…")

    fun init(context: Context) {
        ctx = context.applicationContext
        NeuralSpeechPlayer.prepare(context)
        if (tts != null) { refreshVoices(); return }
        val engineTicket = ++engineGeneration
        tts = TextToSpeech(ctx) { result -> main.post {
            if (engineTicket != engineGeneration) return@post
            ready = result == TextToSpeech.SUCCESS
            if (!ready) { status.value = "Install or enable an Android TTS engine"; return@post }
            tts?.language = Locale.US
            tts?.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { main.post {
                    if (id?.startsWith("$speechGeneration:") == true && SentinelRuntime.alive()) {
                        _isSpeaking.value = true; OverlayManager.setOrbState(OrbState.SPEAKING)
                    }
                } }
                override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) { main.post {
                    if (id?.startsWith("$speechGeneration:") == true && SentinelRuntime.alive()) OverlayManager.setOrbAmplitude(0.5f)
                } }
                override fun onDone(id: String?) { main.post { if (id == finalUtterance) finish() } }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) { main.post { if (id?.startsWith("$speechGeneration:") == true) { status.value = "Voice playback failed"; finish(false) } } }
            })
            refreshVoices()
            val s = SentinelRuntime.store; applyVoice(s.ttsVoice, s.ttsPitch, s.ttsRate)
        } }
    }
    fun reload() { val c = ctx ?: return; shutdown(); allVoices.value = emptyList(); maleVoices.value = emptyList(); status.value = "Refreshing engines…"; init(c) }
    fun refreshVoices() {
        if (!ready && !NeuralSpeechPlayer.ready.value) return
        val installed = runCatching { tts?.voices?.toList().orEmpty() }.getOrDefault(emptyList())
        allVoices.value = installed.map { VoiceOption(it.name, it.locale.getDisplayName(Locale.ENGLISH), it.isNetworkConnectionRequired, it.quality) }
            .sortedWith(compareBy<VoiceOption> { !it.language.startsWith("English") }.thenBy { it.network }.thenByDescending { it.quality }.thenBy { it.id })
        val verified = SentinelRuntime.store.confirmedMaleVoices
        val explicit = installed.filter { v -> v.features.orEmpty().any { it.lowercase().replace(" ", "") in setOf("gender=male", "gender:male", "male") } }.map { it.name }.toSet()
        maleVoices.value = allVoices.value.filter { it.id in verified || it.id in explicit }
        if(NeuralSpeechPlayer.ready.value) {
            val bundled=VoiceOption(NeuralSpeechPlayer.VOICE_ID,"English · ELLIOT",false,500)
            maleVoices.value=listOf(bundled)+maleVoices.value
            if(SentinelRuntime.store.ttsVoice.isBlank())SentinelRuntime.store.ttsVoice=bundled.id
        }
        status.value = if (maleVoices.value.isEmpty()) "Your offline voice is getting ready" else "${maleVoices.value.size} confirmed male voice(s) · ${allVoices.value.size} installed"
    }
    fun applyVoice(voiceName: String, pitch: Float, rate: Float) {
        val t = tts ?: return
        t.setPitch(pitch.coerceIn(0.7f, 1.3f)); t.setSpeechRate(rate.coerceIn(0.7f, 1.4f))
        if (maleVoices.value.any { it.id == voiceName }) t.voices?.firstOrNull { it.name == voiceName }?.let { t.voice = it }
    }
    fun canSpeak() = NeuralSpeechPlayer.ready.value || (ready && maleVoices.value.any { it.id == SentinelRuntime.store.ttsVoice })
    private var streamId=Long.MIN_VALUE
    private var streamCancelled:(()->Unit)?=null
    fun beginSentenceStream(id:Long,epoch:Long,onDone:()->Unit={},onUnavailable:()->Unit={},onCancelled:()->Unit={}):Boolean {
        if(!SentinelRuntime.alive(epoch)||!NeuralSpeechPlayer.ready.value)return false
        stopListening();stopSpeaking();streamId=id
        streamCancelled=onCancelled
        speechEpoch=epoch;speechTicket=VoiceSession.gate.begin(epoch,VoicePhase.THINKING,"streamed reply")
        val generation=speechGeneration
        return NeuralSpeechPlayer.begin(id,epoch,onStart={
            if(streamId==id && generation==speechGeneration && SentinelRuntime.alive(epoch)) {
                _isSpeaking.value=true;VoiceSession.gate.move(speechTicket,epoch,VoicePhase.SPEAKING)
                OverlayManager.setOrbState(OrbState.SPEAKING);WakeListener.resumeWhenIdle()
            }
        },onDone={
            if(streamId==id && generation==speechGeneration) {
                _isSpeaking.value=false;streamId=Long.MIN_VALUE;streamCancelled=null;VoiceSession.gate.move(speechTicket,epoch,VoicePhase.IDLE)
                OverlayManager.setOrbState(OrbState.IDLE);onDone();WakeListener.resumeWhenIdle()
            }
        },onFailure={
            if(streamId==id && generation==speechGeneration) { _isSpeaking.value=false;streamId=Long.MIN_VALUE;streamCancelled=null;VoiceSession.gate.move(speechTicket,epoch,VoicePhase.IDLE);onUnavailable();WakeListener.resumeWhenIdle() }
        })
    }
    fun enqueueSentence(sentence:String,turnTicket:Long)=NeuralSpeechPlayer.enqueue(turnTicket,sentence)
    fun finishSentenceStream(id:Long)=NeuralSpeechPlayer.finish(id)
    fun spokenForTurn(id:Long)=NeuralSpeechPlayer.spoken(id)
    fun stopSpeakingImmediately()=stopSpeaking()
    fun speak(text: String, onDone: () -> Unit = {}, onUnavailable: () -> Unit = {}) {
        val ticket = speechGeneration
        main.post {
            if (ticket != speechGeneration) return@post
            if (!SentinelRuntime.alive() || text.isBlank()) { onUnavailable(); return@post }
            if(NeuralSpeechPlayer.ready.value) {
                val id=System.nanoTime()
                if(beginSentenceStream(id,SentinelRuntime.epoch,onDone,onUnavailable)) {
                    val chunker=SentenceStreamChunker()
                    (chunker.append(text)+chunker.finish()).forEach { enqueueSentence(it,id) }
                    finishSentenceStream(id)
                }else onUnavailable()
                return@post
            }
            if(!ready) { onUnavailable();return@post }
            val s = SentinelRuntime.store
            if (maleVoices.value.none { it.id == s.ttsVoice }) { status.value = "Text is ready · select a confirmed male voice in Settings"; onUnavailable(); return@post }
            applyVoice(s.ttsVoice, s.ttsPitch, s.ttsRate)
            if (tts?.voice?.name != s.ttsVoice) { status.value = "Selected male voice is unavailable; choose another confirmed voice"; onUnavailable(); return@post }
            play(text, onDone, onUnavailable)
        }
    }
    private fun play(text: String, onDone: () -> Unit = {}, onUnavailable: () -> Unit = {}) {
        val t = tts ?: return
        stopListening(); stopSpeaking()
        val am = ctx?.getSystemService(AudioManager::class.java)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { if (it == AudioManager.AUDIOFOCUS_LOSS || it == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stopSpeaking() }.build()
        if (am?.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { status.value = "Audio unavailable; reply remains in chat"; onUnavailable(); return }
        focus = request; done = onDone; unavailable = onUnavailable
        val generation = ++speechGeneration
        val chunks = text.take(12000).chunked(minOf(2800, TextToSpeech.getMaxSpeechInputLength()))
        finalUtterance = "$generation:${chunks.lastIndex}"
        _isSpeaking.value = true
        speechEpoch = SentinelRuntime.epoch
        speechTicket = VoiceSession.gate.begin(speechEpoch, VoicePhase.SPEAKING, "tts")
        WakeListener.resumeWhenIdle()
        chunks.forEachIndexed { i, chunk ->
            val result = t.speak(chunk, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "$generation:$i")
            if (result == TextToSpeech.ERROR) { status.value = "Voice playback failed"; finish(false); return }
        }
    }
    private fun finish(success: Boolean = true) {
        speechGeneration++; tts?.stop()
        _isSpeaking.value = false; OverlayManager.setOrbAmplitude(0f); OverlayManager.setOrbState(OrbState.IDLE)
        focus?.let { ctx?.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(it) }; focus = null
        VoiceSession.gate.move(speechTicket, speechEpoch, VoicePhase.IDLE)
        val cb = if (success) done else unavailable; done = null; unavailable = null; finalUtterance = ""; cb?.invoke(); WakeListener.resumeWhenIdle()
    }
    fun stopSpeaking() {
        val cancelled=streamCancelled;streamCancelled=null
        NeuralSpeechPlayer.stop();streamId=Long.MIN_VALUE
        speechGeneration++; tts?.stop(); done = null; unavailable = null; finalUtterance = ""; _isSpeaking.value = false
        cancelled?.invoke()
        if (VoiceSession.state.value.phase == VoicePhase.SPEAKING) VoiceSession.gate.cancel(SentinelRuntime.epoch, SentinelRuntime.alive())
        focus?.let { ctx?.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(it) }; focus = null
        OverlayManager.setOrbAmplitude(0f); OverlayManager.setOrbState(OrbState.IDLE)
        val epoch = SentinelRuntime.epoch
        main.postDelayed({ if (SentinelRuntime.alive(epoch)) WakeListener.resumeWhenIdle() }, 500)
    }
    fun usesDeviceRecognizer() = recognizer != null
    fun beginLocalListening(origin: String, modelLabel: String? = null): Long {
        stopListening()
        val ticket = VoiceSession.gate.begin(SentinelRuntime.epoch, VoicePhase.LISTENING, origin)
        localTicket = ticket; _isListening.value = true; transcript.value = ""
        status.value = modelLabel?.let { "$it · say your request" } ?: "Offline English conversation · say your request"
        OverlayManager.setOrbState(OrbState.LISTENING)
        return ticket
    }
    fun localTranscript(ticket: Long, text: String) {
        if (localTicket == ticket && VoiceSession.gate.accepts(ticket, SentinelRuntime.epoch)) transcript.value = text
    }
    fun endLocalListening(ticket: Long) {
        if (localTicket != ticket) return
        localTicket = null; _isListening.value = false; transcript.value = ""
        VoiceSession.gate.move(ticket, SentinelRuntime.epoch, VoicePhase.IDLE)
        OverlayManager.setOrbState(OrbState.IDLE)
    }
    fun isRecognitionAvailable(): Boolean = ctx?.let(SpeechRecognizer::isRecognitionAvailable) ?: false
    fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit = {}) {
        val ticket = recognitionGeneration
        val epoch = SentinelRuntime.epoch
        main.post {
            val c = ctx ?: return@post
            if (ticket != recognitionGeneration || !SentinelRuntime.alive(epoch)) return@post
            if (ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { onError("Grant microphone permission in Settings"); return@post }
            if (!SentinelRuntime.foreground && !SentinelRuntime.microphoneReady) { onError("Open ELLIOT and tap Resume once to enable background voice"); return@post }
            stopListening(); WakeListener.suspendCapture(); val token = ++recognitionGeneration
            val local = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(c)
            if (!local && !SpeechRecognizer.isRecognitionAvailable(c)) { onError("Install an Android speech recognition service"); return@post }
            val sr = runCatching {
                if (local) SpeechRecognizer.createOnDeviceSpeechRecognizer(c) else SpeechRecognizer.createSpeechRecognizer(c)
            }.getOrElse {
                onError("Speech service unavailable; check the device's voice settings")
                WakeListener.resumeWhenIdle(); return@post
            }
            recognizer = sr; transcript.value = ""; _isListening.value = true
            val turnTicket = VoiceSession.gate.begin(epoch, VoicePhase.LISTENING, "device speech")
            status.value = if (local) "On-device speech recognition" else "Device speech service · may use its network provider"
            sr.setRecognitionListener(object : RecognitionListener {
                private fun valid() = token == recognitionGeneration && SentinelRuntime.alive(epoch) && VoiceSession.gate.accepts(turnTicket, epoch)
                override fun onReadyForSpeech(p: Bundle?) { if (valid()) OverlayManager.setOrbState(OrbState.LISTENING) }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(v: Float) { if (valid()) OverlayManager.setOrbAmplitude((v / 10).coerceIn(0f, 1f)) }
                override fun onBufferReceived(b: ByteArray?) = Unit
                override fun onEndOfSpeech() { if (valid()) OverlayManager.setOrbState(OrbState.THINKING) }
                override fun onError(error: Int) { if (valid()) { stopListening(); OverlayManager.setOrbState(OrbState.IDLE); onError("Speech unavailable or not heard (code $error)"); WakeListener.resumeWhenIdle() } }
                override fun onResults(result: Bundle?) { if (valid()) {
                    stopListening(); OverlayManager.setOrbState(OrbState.IDLE)
                    val text = result?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isBlank()) onError("No speech detected") else onResult(text)
                    WakeListener.resumeWhenIdle()
                } }
                override fun onPartialResults(p: Bundle?) { if (valid()) transcript.value = p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty() }
                override fun onEvent(t: Int, p: Bundle?) = Unit
            })
            runCatching { sr.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)) }
                .onFailure { stopListening(); OverlayManager.setOrbState(OrbState.IDLE); onError("Microphone unavailable: ${it.message}"); WakeListener.resumeWhenIdle() }
        }
    }
    fun stopListening() {
        WakeListener.cancelTurn(); localTicket = null
        if (VoiceSession.state.value.phase == VoicePhase.LISTENING) VoiceSession.gate.cancel(SentinelRuntime.epoch, SentinelRuntime.alive())
        recognitionGeneration++; _isListening.value = false; transcript.value = ""
        val old = recognizer; recognizer = null
        val release = Runnable { runCatching { old?.cancel(); old?.destroy() } }
        if (Looper.myLooper() == main.looper) release.run() else main.post(release)
    }
    fun shutdown() { engineGeneration++; stopListening(); stopSpeaking(); tts?.shutdown(); tts = null; ready = false }
}
