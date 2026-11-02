package com.eiliot.app.capabilities

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.SharedPreferences
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.eiliot.app.core.*
import com.eiliot.app.llm.NanoClient
import com.eiliot.app.overlay.OverlayManager
import com.eiliot.app.overlay.OrbState
import com.eiliot.app.tools.ActionApprovals
import com.eiliot.app.voice.VoiceIo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.LocalTime

/** Process-local observations. No new sensors, network probes or persistent diagnostic records. */
object CapabilityRegistry {
    private val main = Handler(Looper.getMainLooper())
    private val mutable = MutableStateFlow(CapabilitySnapshot())
    val snapshot: StateFlow<CapabilitySnapshot> = mutable.asStateFlow()
    private val sessionMutable = MutableStateFlow(SessionPresentation(SessionPhase.OFF, "ELLIOT is off"))
    val session: StateFlow<SessionPresentation> = sessionMutable.asStateFlow()
    private var initialized = false
    @Volatile private var cloud = ConnectorHealth()
    @Volatile private var web = ConnectorHealth()
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val refreshLater = Runnable { refresh() }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val c = context.applicationContext
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in setOf("base_url", "model", "api_key_cipher")) cloud = ConnectorHealth()
            requestRefresh()
        }
        preferenceListener = listener // SharedPreferences holds only a weak listener reference.
        c.getSharedPreferences("eiliot_v2_settings", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(listener)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = requestRefresh()
            override fun onLost(network: Network) = requestRefresh()
            override fun onUnavailable() = requestRefresh()
        }
        runCatching { c.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(callback); networkCallback = callback }
        SentinelRuntime.scope.launch {
            combine(SentinelRuntime.enabled, EiliotBrain.state, VoiceIo.isListening, VoiceIo.isSpeaking, ActionApprovals.pending) { running, brain, listening, speaking, review ->
                SessionPolicy.reduce(running && SentinelRuntime.alive(), listening, speaking, brain.busy, review != null,
                    brain.error ?: brain.status, VoiceIo.transcript.value, brain.activities.size)
            }.collect { sessionMutable.value = it; updateOrb(it); requestRefresh() }
        }
        SentinelRuntime.scope.launch { VoiceIo.transcript.collect { updateSession() } }
        SentinelRuntime.scope.launch { ScreenReader.connected.collect { requestRefresh() } }
        SentinelRuntime.scope.launch { WakeListener.status.collect { requestRefresh() } }
        SentinelRuntime.scope.launch { VoiceIo.maleVoices.collect { requestRefresh() } }
        SentinelRuntime.scope.launch { NanoClient.availability.collect { requestRefresh() } }
        SentinelRuntime.scope.launch {
            while (isActive) {
                // Runtime flags, lock state, quiet hours and external permission revocation.
                if (SentinelRuntime.foreground || SentinelRuntime.alive()) refresh()
                delay(2000)
            }
        }
        refresh()
    }
    fun requestRefresh() { main.removeCallbacks(refreshLater); main.postDelayed(refreshLater, 150) }
    private fun updateSession() {
        val b = EiliotBrain.state.value
        sessionMutable.value = SessionPolicy.reduce(SentinelRuntime.alive(), VoiceIo.isListening.value, VoiceIo.isSpeaking.value,
            b.busy, ActionApprovals.pending.value != null, b.error ?: b.status, VoiceIo.transcript.value, b.activities.size)
        updateOrb(sessionMutable.value)
    }
    private fun updateOrb(p: SessionPresentation) {
        OverlayManager.setOrbState(when (p.phase) {
            SessionPhase.LISTENING -> OrbState.LISTENING
            SessionPhase.SPEAKING -> OrbState.SPEAKING
            SessionPhase.WORKING, SessionPhase.REVIEW -> OrbState.THINKING
            else -> OrbState.IDLE
        })
    }
    fun currentSession(): SessionPresentation { updateSession(); return sessionMutable.value }
    fun refresh(): CapabilitySnapshot {
        val c = SentinelRuntime.context; val s = SentinelRuntime.store
        val network = runCatching {
            val cm = c.getSystemService(ConnectivityManager::class.java)
            val n = cm.activeNetwork
            val caps = n?.let(cm::getNetworkCapabilities)
            when { n == null -> NetworkState.OFFLINE
                caps == null -> NetworkState.UNKNOWN
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> NetworkState.VALIDATED
                else -> NetworkState.UNVALIDATED }
        }.getOrDefault(NetworkState.UNKNOWN)
        val voice = VoiceIo.maleVoices.value.firstOrNull { it.id == if(com.eiliot.app.voice.NeuralSpeechPlayer.ready.value)com.eiliot.app.voice.NeuralSpeechPlayer.VOICE_ID else s.ttsVoice }
        val now = System.currentTimeMillis()
        val f = CapabilityFacts(
            running = SentinelRuntime.alive(), tools = s.phoneToolsEnabled, web = s.webToolsEnabled,
            memory = s.memoryEnabled, memoryHealthy = !SentinelRuntime.status.value.startsWith("Memory needs recovery") &&
                !SentinelRuntime.memoryStatus.value.startsWith("Erasure needs attention") &&
                SentinelRuntime.memory.messages.value.none { it.text == "[Encrypted record unavailable: recovery required]" } &&
                SentinelRuntime.memory.notes.value.none { it.text == "[Encrypted record unavailable: recovery required]" },
            screen = s.screenEnabled, control = s.controlEnabled, accessibility = ScreenReader.connected.value,
            locked = c.getSystemService(KeyguardManager::class.java).isKeyguardLocked,
            foreground = SentinelRuntime.foreground, network = network,
            cloudConfigured = validCloud(s.baseUrl, s.apiKey, s.model), nano = NanoClient.availability.value, mode = s.inferenceMode,
            microphone = ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
            armed = SentinelRuntime.microphoneReady,
            speechRecognizer = runCatching { SpeechRecognizer.isRecognitionAvailable(c) }.getOrDefault(false),
            maleVoice = voice != null && VoiceIo.canSpeak(), networkVoice = voice?.network == true,
            wake = s.wakeEnabled, wakeModel = WakeListener.hasModel(c), wakeActive = WakeListener.available(),
            overlay = s.orbEnabled, overlayPermission = OverlayManager.canDraw(c), overlayVisible = OverlayManager.isShowing(), overlayInApp = s.orbInApp,
            checkIns = s.proactiveEnabled, snoozed = now < s.checkInSnooze,
            activeHours = CheckInSchedule(s.checkInStart, s.checkInEnd, s.checkInInterval).inside(LocalTime.now()),
            quiet = c.getSystemService(NotificationManager::class.java).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL,
            interactive = c.getSystemService(PowerManager::class.java).isInteractive, roomMode = s.roomMode, followUp = s.followUpEnabled,
            notificationAccess = EiliotPermissions.isNotificationAccessGranted(c), notificationApps = s.notificationApps.split(',').any { it.isNotBlank() },
            learning = s.learningEnabled, fixedVolume = c.getSystemService(AudioManager::class.java).isVolumeFixed,
            cloudHealth = cloud, webHealth = web, now = now,notices=s.notificationAwarenessEnabled
        )
        val result = CapabilityPolicy.evaluate(f)
        // Do not redraw every card solely because the wall clock ticked.
        if (result.entries != mutable.value.entries) mutable.value = result
        updateSession()
        return result
    }
    fun requireTool(name: String) {
        val entry = refresh().entry(name) ?: error("Unknown capability: $name")
        if (!entry.tool || !entry.canAttempt) throw CapabilityBlocked(entry)
    }
    fun recordCloud(state: HealthState) { cloud = ConnectorHealth(state, System.currentTimeMillis()); requestRefresh() }
    fun recordWeb(success: Boolean) { web = ConnectorHealth(if (success) HealthState.SUCCEEDED else HealthState.FAILED, System.currentTimeMillis()); requestRefresh() }
    fun recovery(action: Recovery, onControls: () -> Unit) {
        val c = SentinelRuntime.context
        when (action) {
            Recovery.NONE -> Unit
            Recovery.CONTROLS -> onControls()
            Recovery.RESUME -> SentinelCoreService.start(c)
            Recovery.OVERLAY -> OverlayManager.requestPermission(c)
            Recovery.ACCESSIBILITY -> open(c, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Recovery.INTERNET -> open(c, Intent(Settings.ACTION_WIRELESS_SETTINGS))
            Recovery.OPEN_APP -> open(c, Intent(c, com.eiliot.app.MainActivity::class.java))
            Recovery.UNLOCK -> EiliotBrain.notice("Unlock your phone to continue")
        }
        requestRefresh()
    }
    private fun open(c: Context, intent: Intent) { runCatching {
        if(intent.action?.startsWith("android.settings.")==true) OverlayManager.suspendForSettings()
        c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { EiliotBrain.notice("That settings page is unavailable. Open Android settings manually.") } }
    fun validCloud(base: String, key: String, model: String): Boolean {
        val raw = base.trim().replace(Regex("\\s+"), "").let { if (it.contains("://")) it else "https://$it" }
        val url = raw.toHttpUrlOrNull()
        return url?.isHttps == true && url.username.isEmpty() && url.password.isEmpty() && key.isNotBlank() && model.isNotBlank()
    }
    fun classifyCloudFailure(error: Throwable): HealthState {
        val code = Regex("HTTP (\\d{3})").find(error.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        return when (code) { 401, 403 -> HealthState.AUTH; 429 -> HealthState.RATE_LIMIT; else -> HealthState.FAILED }
    }
}
