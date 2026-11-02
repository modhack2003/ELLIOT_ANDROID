package com.eiliot.app.capabilities

/** Facts only: no API keys, prompts, notification bodies or screen text. */
enum class Availability(val label: String) {
    READY("Ready"), CONDITIONAL("Conditional"), DISABLED("Off"), SETUP("Needs setup"), PAUSED("Paused"), UNKNOWN("Not checked"), UNAVAILABLE("Unavailable")
}
enum class Recovery(val label: String) {
    NONE(""), RESUME("Resume ELLIOT"), CONTROLS("Open Settings"), ACCESSIBILITY("Accessibility settings"),
    OVERLAY("Overlay access"), INTERNET("Internet settings"), UNLOCK("Unlock phone"), OPEN_APP("Open ELLIOT")
}
enum class NetworkState { VALIDATED, UNVALIDATED, OFFLINE, UNKNOWN }
enum class NanoState { UNCHECKED, READY, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE, FAILED }
enum class HealthState { UNCHECKED, SUCCEEDED, AUTH, RATE_LIMIT, FAILED }
data class ConnectorHealth(val state: HealthState = HealthState.UNCHECKED, val observedAt: Long = 0) {
    fun fresh(now: Long) = observedAt > 0 && now >= observedAt && now - observedAt < 5 * 60_000
}
data class CapabilityFacts(
    val running: Boolean = false, val tools: Boolean = true, val web: Boolean = true,
    val memory: Boolean = true, val memoryHealthy: Boolean = true,
    val screen: Boolean = false, val control: Boolean = false, val accessibility: Boolean = false,
    val locked: Boolean = false, val foreground: Boolean = true,
    val network: NetworkState = NetworkState.UNKNOWN, val cloudConfigured: Boolean = false,
    val nano: NanoState = NanoState.UNCHECKED, val mode: String = "cloud",
    val microphone: Boolean = false, val armed: Boolean = false, val speechRecognizer: Boolean = false,
    val maleVoice: Boolean = false, val networkVoice: Boolean = false,
    val wake: Boolean = false, val wakeModel: Boolean = false, val wakeActive: Boolean = false,
    val overlay: Boolean = true, val overlayPermission: Boolean = false,
    val overlayVisible: Boolean = false, val overlayInApp: Boolean = true,
    val checkIns: Boolean = false, val snoozed: Boolean = false, val activeHours: Boolean = true,
    val quiet: Boolean = false, val interactive: Boolean = true, val roomMode: Boolean = false,
    val followUp: Boolean = true, val notificationAccess: Boolean = false, val notificationApps: Boolean = false,
    val learning: Boolean = false, val fixedVolume: Boolean = false,
    val cloudHealth: ConnectorHealth = ConnectorHealth(), val webHealth: ConnectorHealth = ConnectorHealth(),
    val now: Long = 0, val notices:Boolean=checkIns
)
data class CapabilityEntry(
    val id: String, val title: String, val group: String, val availability: Availability,
    val reason: String, val recovery: Recovery = Recovery.NONE, val tool: Boolean = false
) {
    // Conditional/unknown still permit an attempt; absence of a connectivity probe is not denial.
    val canAttempt get() = availability in setOf(Availability.READY, Availability.CONDITIONAL, Availability.UNKNOWN)
}
class CapabilityBlocked(val entry: CapabilityEntry) : IllegalStateException("${entry.title}: ${entry.reason} ${entry.recovery.label}")
data class CapabilitySnapshot(val entries: List<CapabilityEntry> = emptyList(), val observedAt: Long = 0) {
    fun entry(id: String) = entries.firstOrNull { it.id == id }
    val readyTools get() = entries.count { it.tool && it.availability == Availability.READY }
    val availableTools get() = entries.count { it.tool && it.canAttempt }
    fun modelContext() = "Verified capability snapshot (observations, not action results):\n" + entries.joinToString("\n") {
        "${it.id}: ${it.availability.label}. ${it.reason}" + if (it.recovery != Recovery.NONE) " Recovery: ${it.recovery.label}." else ""
    }
}

/** One prerequisite policy shared by tool dispatch, models and presentation. */
object CapabilityPolicy {
    fun evaluate(f: CapabilityFacts): CapabilitySnapshot {
        val out = mutableListOf<CapabilityEntry>()
        fun add(id: String, title: String, group: String, state: Availability, reason: String, recovery: Recovery = Recovery.NONE, tool: Boolean = false) {
            out += CapabilityEntry(id, title, group, state, reason, recovery, tool)
        }
        fun tool(id: String, title: String, group: String = "Phone", check: () -> Triple<Availability, String, Recovery> = { Triple(Availability.READY, "Ready for a matching request; result verified at execution.", Recovery.NONE) }) {
            val result = when { !f.running -> Triple(Availability.PAUSED, "ELLIOT is off. Nothing will execute.", Recovery.RESUME)
                !f.tools -> Triple(Availability.DISABLED, "Companion tools are disabled.", Recovery.CONTROLS)
                else -> check() }
            add(id, title, group, result.first, result.second, result.third, true)
        }
        fun blocked(reason: String, recovery: Recovery = Recovery.CONTROLS) = Triple(Availability.SETUP, reason, recovery)
        fun conditional(reason: String, recovery: Recovery = Recovery.NONE) = Triple(Availability.CONDITIONAL, reason, recovery)
        fun network() = when (f.network) {
            NetworkState.OFFLINE -> conditional("No active network observed. Reconnect for this internet task.", Recovery.INTERNET)
            NetworkState.UNVALIDATED -> conditional("Network present; internet not validated. A request may still work.", Recovery.INTERNET)
            NetworkState.UNKNOWN -> Triple(Availability.UNKNOWN, "Network could not be checked. Actual request decides availability.", Recovery.NONE)
            else -> Triple(Availability.READY, "Internet validated by Android; provider access is checked per request.", Recovery.NONE)
        }
        fun web() = when {
            !f.web -> Triple(Availability.DISABLED, "Web tools are disabled.", Recovery.CONTROLS)
            f.webHealth.fresh(f.now) && f.webHealth.state == HealthState.FAILED -> conditional("A recent public-web request failed. Retry or use the browser; sources may block access.")
            else -> network()
        }
        fun screen(actions: Boolean) = when {
            !f.screen -> blocked("Enable screen reading in Settings.")
            actions && !f.control -> blocked("Enable phone control in Settings.")
            !f.accessibility -> blocked("Accessibility is not connected.", Recovery.ACCESSIBILITY)
            f.locked -> Triple(Availability.PAUSED, "Unlock the phone before reading or changing another app.", Recovery.UNLOCK)
            else -> conditional("Needs an eligible external app and fresh screen references. Protected content is excluded.")
        }
        tool("device_info", "Phone status")
        tool("list_apps", "Find apps")
        tool("open_app", "Open an app") { conditional("Needs a visible launchable app matching your request. Opening does not complete its workflow.") }
        tool("open_settings", "Open phone settings") { conditional("Settings handler checked on dispatch. Restricted switches are changed in Android settings.") }
        tool("set_media_volume", "Media volume") {
            if (f.fixedVolume) Triple(Availability.UNAVAILABLE, "This device reports fixed media volume.", Recovery.NONE)
            else Triple(Availability.READY, "Set requested media percentage; calls and alarms are unchanged.", Recovery.NONE)
        }
        tool("set_timer", "Clock timer") { conditional("Clock handler checked on dispatch; timer running state cannot be independently verified.") }
        tool("search_web", "Web search", "Internet") { web() }
        tool("read_web_page", "Read public pages", "Internet") { web() }
        tool("open_url", "Open browser", "Internet") {
            if (!f.web) Triple(Availability.DISABLED, "Web tools are disabled.", Recovery.CONTROLS)
            else conditional("Opens a public HTTPS page; browser handler and page reachability are checked on dispatch.")
        }
        tool("read_screen", "Read screen", "Screen") { screen(false) }
        tool("click_screen", "Tap screen item", "Screen") { screen(true) }
        tool("type_text", "Fill text field", "Screen") { screen(true) }
        tool("scroll_screen", "Scroll screen", "Screen") { screen(true) }
        tool("navigate", "Phone navigation", "Screen") { when {
            !f.control -> blocked("Enable phone control in Settings.")
            !f.accessibility -> blocked("Accessibility is not connected.", Recovery.ACCESSIBILITY)
            f.locked -> Triple(Availability.PAUSED, "Unlock the phone before navigation.", Recovery.UNLOCK)
            else -> Triple(Availability.READY, "Back, Home, Recents or Notifications for your request.", Recovery.NONE)
        } }
        for ((id, title) in listOf("save_memory" to "Remember a note", "recall_memory" to "Recall notes")) tool(id, title, "Memory") {
            when { !f.memory -> Triple(Availability.DISABLED, "Saved memory is off; session conversation remains available.", Recovery.CONTROLS)
                !f.memoryHealthy -> blocked("Local memory needs recovery. Check the memory status in Settings.")
                else -> Triple(Availability.READY, "Encrypted local notes; no model key required for direct controls.", Recovery.NONE) }
        }
        val cloud = when {
            !f.cloudConfigured -> blocked("Configure a valid HTTPS API root, key and model in Settings.")
            f.cloudHealth.fresh(f.now) && f.cloudHealth.state == HealthState.AUTH -> conditional("Recent provider request rejected authentication or access. Check the configured key/model or retry.", Recovery.CONTROLS)
            f.cloudHealth.fresh(f.now) && f.cloudHealth.state == HealthState.RATE_LIMIT -> conditional("Provider recently rate-limited a request. Wait and retry.")
            f.cloudHealth.fresh(f.now) && f.cloudHealth.state == HealthState.FAILED -> conditional("A recent provider request failed. Retry or check your connection/provider.")
            f.network != NetworkState.VALIDATED -> network()
            f.cloudHealth.fresh(f.now) && f.cloudHealth.state == HealthState.SUCCEEDED -> Triple(Availability.READY, "A recent provider request succeeded. This is not a permanent availability guarantee.", Recovery.NONE)
            else -> conditional("Configured; provider access and model/tool support are not verified by settings alone.", Recovery.CONTROLS)
        }
        add("cloud_model", "Cloud reasoning", "Models", if (f.running) cloud.first else Availability.PAUSED,
            if (f.running) cloud.second else "ELLIOT is off; model configuration is retained.", if (f.running) cloud.third else Recovery.RESUME)
        val nano = when {
            !f.foreground -> Triple(Availability.PAUSED, "Gemini Nano requires ELLIOT in front. No silent cloud fallback.", Recovery.OPEN_APP)
            f.nano == NanoState.READY -> Triple(Availability.READY, "AICore reported the model available. Actual generation may still fail.", Recovery.NONE)
            f.nano == NanoState.DOWNLOADING -> Triple(Availability.PAUSED, "AICore model download is in progress.", Recovery.NONE)
            f.nano == NanoState.DOWNLOADABLE -> blocked("Supported device; download the AICore model in Settings.")
            f.nano == NanoState.UNAVAILABLE -> Triple(Availability.UNAVAILABLE, "AICore reported this feature unavailable.", Recovery.CONTROLS)
            else -> Triple(Availability.UNKNOWN, "Use Check Nano in Settings to verify this device/configuration.", Recovery.CONTROLS)
        }
        add("nano_model", "Gemini Nano", "Models", if (f.running) nano.first else Availability.PAUSED,
            if (f.running) nano.second else "ELLIOT is off; model setup is retained.", if (f.running) nano.third else Recovery.RESUME)
        fun feature(id: String, title: String, group: String, result: Triple<Availability, String, Recovery>) {
            val r = if (!f.running) Triple(Availability.PAUSED, "ELLIOT is off; saved settings are retained.", Recovery.RESUME) else result
            add(id, title, group, r.first, r.second, r.third)
        }
        val speech = when {
            !f.maleVoice -> blocked("Select an available confirmed male voice in Settings.")
            f.networkVoice && f.network != NetworkState.VALIDATED -> conditional("Selected male voice requires network access; offline speech is not guaranteed.", Recovery.INTERNET)
            else -> Triple(Availability.READY, "Selected male voice is available in the current TTS engine.", Recovery.NONE)
        }
        feature("speech", "ELLIOT's voice", "Voice", speech)
        val input = when {
            !f.microphone -> blocked("Grant microphone access in Settings.")
            !f.foreground && !f.armed -> blocked("Open ELLIOT and arm background voice.", Recovery.OPEN_APP)
            f.wakeActive -> Triple(Availability.READY, "Local English wake/command stream is active; room accuracy requires phone testing.", Recovery.NONE)
            f.speechRecognizer -> conditional("Manual speech service is available; its provider may use the network.")
            else -> blocked("Download/arm local wake or install an Android speech recognition service.")
        }
        feature("voice_input", "Voice input", "Voice", input)
        feature("wake_word", "Wake interruption", "Voice", when {
            !f.wake -> Triple(Availability.DISABLED, "Wake word is off.", Recovery.CONTROLS)
            !f.microphone -> blocked("Grant microphone access in Settings.")
            !f.wakeModel -> blocked("Repair offline voice in Settings.")
            !f.armed -> blocked("Open ELLIOT and arm background voice.", Recovery.OPEN_APP)
            !f.wakeActive -> Triple(Availability.PAUSED, "Wake capture is not active. Check the wake status in Settings.", Recovery.CONTROLS)
            else -> Triple(Availability.READY, "Say Elliot or hey Elliot to interrupt. Acoustic acceptance still needs a phone.", Recovery.NONE)
        })
        feature("floating_orb", "Floating captions", "Interface", when {
            !f.overlay -> Triple(Availability.DISABLED, "Floating orb is off.", Recovery.CONTROLS)
            !f.overlayPermission -> blocked("Allow drawing over other apps.", Recovery.OVERLAY)
            f.locked || !f.interactive -> Triple(Availability.PAUSED, "Private transcript hidden while locked or screen off.", Recovery.UNLOCK)
            f.foreground && !f.overlayInApp -> conditional("Configured to appear only outside ELLIOT.")
            !f.overlayVisible -> conditional("Access granted; floating window is not currently attached.", Recovery.CONTROLS)
            else -> Triple(Availability.READY, "Floating window is attached; captions follow the shared conversation.", Recovery.NONE)
        })
        feature("check_ins", "Scheduled check-ins", "Initiative", when {
            !f.checkIns -> Triple(Availability.DISABLED, "Automatic spoken check-ins are off.", Recovery.CONTROLS)
            f.snoozed -> Triple(Availability.PAUSED, "Check-ins are snoozed.", Recovery.CONTROLS)
            !f.activeHours -> Triple(Availability.PAUSED, "Outside your active hours.", Recovery.CONTROLS)
            f.quiet -> Triple(Availability.PAUSED, "Quiet while Do Not Disturb is active.", Recovery.NONE)
            !f.roomMode && (f.locked || !f.interactive) -> Triple(Availability.PAUSED, "Waiting for an active unlocked screen or Room mode.", Recovery.CONTROLS)
            speech.first !in setOf(Availability.READY, Availability.CONDITIONAL) -> speech
            f.followUp && !f.foreground && !f.armed -> blocked("Arm background voice for hands-free spoken replies.", Recovery.OPEN_APP)
            else -> conditional("Scheduled; a question waits for the interval and an idle conversation. Off and quiet controls apply.")
        })
        feature("notification_awareness", "Allowed notifications", "Initiative", when {
            !f.notices -> Triple(Availability.DISABLED, "Enable notification awareness in Settings.", Recovery.CONTROLS)
            !f.notificationAccess -> blocked("Grant notification listener access in Settings.")
            !f.notificationApps -> blocked("Choose allowed notification apps in Settings.")
            else -> conditional("Only allowed eligible notices; schedule, quiet rules and lock privacy still apply.")
        })
        feature("web_learning", "Background web learning", "Internet", when {
            !f.learning -> Triple(Availability.DISABLED, "Background learning is off.", Recovery.CONTROLS)
            !f.memory -> Triple(Availability.PAUSED,"Turn on Memory to save new web references.",Recovery.CONTROLS)
            else -> conditional("Scheduled public-feed ingestion; Android timing/network and source availability apply. It is not model training.")
        })
        return CapabilitySnapshot(out, f.now)
    }
}

enum class SessionPhase(val label: String) { OFF("Off"), LISTENING("Listening"), SPEAKING("Speaking"), REVIEW("Awaiting your reply"), WORKING("Working"), IDLE("Here with you") }
data class SessionPresentation(val phase: SessionPhase, val detail: String, val activityCount: Int = 0)
object SessionPolicy {
    fun reduce(running: Boolean, listening: Boolean, speaking: Boolean, busy: Boolean, review: Boolean, status: String, transcript: String, activities: Int) =
        when {
            !running -> SessionPresentation(SessionPhase.OFF, "Voice, learning and autonomy are off")
            listening -> SessionPresentation(SessionPhase.LISTENING, transcript.ifBlank { "I'm listening…" }, activities)
            speaking -> SessionPresentation(SessionPhase.SPEAKING, "Say hey Elliot to interrupt when wake is active", activities)
            review -> SessionPresentation(SessionPhase.REVIEW, "Optional review: say allow or decline", activities)
            busy -> SessionPresentation(SessionPhase.WORKING, status, activities)
            else -> SessionPresentation(SessionPhase.IDLE, status, activities)
        }
}
