package com.eiliot.app.core

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import com.eiliot.app.voice.VoiceIo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import java.time.LocalTime

data class ProactiveMessage(val text: String, val timestamp: Long = System.currentTimeMillis())

/** Scheduled local questions and notification dialogue; notification bodies never enter a cloud request. */
object ProactiveEngine {
    private var loop: Job? = null
    private var question = 0
    private data class Notice(val body: String, val expires: Long, val epoch: Long)
    private var notice: Notice? = null
    val status = MutableStateFlow("Automatic check-ins off")
    private val questions = listOf("What are you working on?", "How are you doing?", "How is your day going?", "Would you like some help with what you are doing?")
    private val _messages = MutableSharedFlow<ProactiveMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<ProactiveMessage> = _messages
    var enabled: Boolean
        get() = SentinelRuntime.store.proactiveEnabled
        set(value) {
            SentinelRuntime.store.proactiveEnabled = value
            if (value) { SentinelRuntime.store.lastCheckIn = System.currentTimeMillis(); start(SentinelRuntime.context); status.value = "Scheduled check-ins enabled" }
            else { notice = null; VoiceIo.stopSpeaking(); status.value = "Automatic check-ins off" }
        }
    fun start(context: Context) {
        if (loop?.isActive == true) return
        if (SentinelRuntime.store.lastCheckIn == 0L) SentinelRuntime.store.lastCheckIn = System.currentTimeMillis()
        loop = SentinelRuntime.scope.launch {
            while (isActive && SentinelRuntime.alive()) {
                delay(10000)
                if (!enabled) continue
                val s = SentinelRuntime.store; val now = System.currentTimeMillis()
                val schedule = CheckInSchedule(s.checkInStart, s.checkInEnd, s.checkInInterval)
                if (schedule.due(now, s.lastCheckIn, s.checkInSnooze, LocalTime.now())) checkInNow(false)
            }
        }
    }
    fun resetSchedule() { SentinelRuntime.store.lastCheckIn = System.currentTimeMillis(); status.value = "Schedule saved · next question after the selected interval" }
    fun snooze(minutes: Int = 60) {
        SentinelRuntime.store.checkInSnooze = System.currentTimeMillis() + minutes * 60000L
        notice = null; status.value = "Check-ins snoozed for $minutes minutes"
    }
    fun noteInteraction() = Unit // Check-ins follow their schedule, not a permanent inactivity reset.
    fun emitExternal(text: String) { if (SentinelRuntime.alive()) _messages.tryEmit(ProactiveMessage(text)) }
    private fun eligible(checkIn:Boolean=true): Boolean {
        if (!SentinelRuntime.alive() || (checkIn && !enabled) || (!checkIn && !SentinelRuntime.store.notificationAwarenessEnabled)) return false
        if(WakeListener.liveCall.value)return false
        if (VoiceIo.isSpeaking.value || VoiceIo.isListening.value || EiliotBrain.state.value.busy || EiliotBrain.state.value.audioPending || com.eiliot.app.tools.ActionApprovals.pending.value != null) return false
        val c = SentinelRuntime.context; val s = SentinelRuntime.store
        if (!s.roomMode && (c.getSystemService(KeyguardManager::class.java).isDeviceLocked || !c.getSystemService(PowerManager::class.java).isInteractive)) { status.value = "Waiting for an unlocked active screen, or enable Room mode"; return false }
        if (c.getSystemService(NotificationManager::class.java).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) { status.value = "Quiet while Do Not Disturb is active"; return false }
        if (!VoiceIo.canSpeak()) { status.value = "Select an available confirmed male voice before spoken check-ins"; return false }
        if (s.followUpEnabled && !SentinelRuntime.microphoneReady && !SentinelRuntime.foreground) { status.value = "Open ELLIOT and arm background voice for spoken replies"; return false }
        return true
    }
    fun checkInNow(explicit: Boolean = true): Boolean {
        if (!eligible()) return false
        val s = SentinelRuntime.store; val now = System.currentTimeMillis()
        if (!explicit && !CheckInSchedule(s.checkInStart, s.checkInEnd, s.checkInInterval).due(now, s.lastCheckIn, s.checkInSnooze, LocalTime.now())) return false
        s.lastCheckIn = now; s.lastProactiveAt = now
        notice = null
        val text = questions[question++ % questions.size]
        sayQuestion(text, "check-in")
        status.value = "Asked a question · next check-in follows your schedule"
        return true
    }
    fun onNotification(pkg: String, title: String, text: String) {
        if (!SentinelRuntime.alive() || !SentinelRuntime.store.notificationAwarenessEnabled) return
        val s = SentinelRuntime.store
        val apps = s.notificationApps.split(',').map(String::trim).filter(String::isNotBlank).toSet()
        if (!ContentPolicy.notificationAllowed(pkg, title, text, apps) || !eligible(false)) return
        val now = System.currentTimeMillis()
        if (now - s.lastProactiveAt < 15 * 60000L) return
        if (!CheckInSchedule(s.checkInStart, s.checkInEnd, s.checkInInterval).inside(LocalTime.now()) || now < s.checkInSnooze) return
        s.lastProactiveAt = now
        val body = "${pkg.substringAfterLast('.')}: ${title.take(70)}. ${text.take(160)}"
        val locked = SentinelRuntime.context.getSystemService(KeyguardManager::class.java).isDeviceLocked
        if (s.notificationContents && (!locked || s.noticesWhenLocked)) { notice = null; sayQuestion(body, "notification") }
        else {
            notice = Notice(body, now + 45000, SentinelRuntime.epoch)
            sayQuestion("There is a notification from ${pkg.substringAfterLast('.')}. Would you like me to read it?", "notification")
        }
    }
    private fun sayQuestion(text: String, source: String) {
        _messages.tryEmit(ProactiveMessage(text)); EiliotBrain.presentPrompt(text, source)
        val epoch = SentinelRuntime.epoch
        VoiceIo.speak(text, onDone = {
            if (SentinelRuntime.alive(epoch) && (if(source=="notification")SentinelRuntime.store.notificationAwarenessEnabled else enabled) && SentinelRuntime.store.followUpEnabled) EiliotBrain.listenForFollowUp()
        }, onUnavailable = { status.value = "Question is in the transcript · speech was unavailable" })
    }
    fun consumeReply(raw: String): Boolean {
        val pending = notice ?: return false
        notice = null
        if (!SentinelRuntime.alive(pending.epoch) || System.currentTimeMillis() > pending.expires) return false
        val text = raw.lowercase().trim().trimEnd('.', '!', '?')
        if (text in setOf("no", "not now", "dismiss", "no thanks")) { EiliotBrain.notice("Notification dismissed"); return true }
        if (text in setOf("yes", "read it", "read the notification", "go ahead")) {
            val locked = SentinelRuntime.context.getSystemService(KeyguardManager::class.java).isDeviceLocked
            if (locked && !SentinelRuntime.store.noticesWhenLocked) VoiceIo.speak("Notification contents are private while locked. Change the room privacy setting if you want them read aloud.")
            else { _messages.tryEmit(ProactiveMessage(pending.body)); EiliotBrain.notice("Reading notification locally"); VoiceIo.speak(pending.body) }
            return true
        }
        return false
    }
    fun stop() { loop?.cancel(); loop = null; notice = null; status.value = "Automatic check-ins stopped" }
}
