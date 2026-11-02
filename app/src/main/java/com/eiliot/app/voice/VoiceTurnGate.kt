package com.eiliot.app.voice

import kotlinx.coroutines.flow.MutableStateFlow

enum class VoicePhase { OFF, IDLE, LISTENING, THINKING, SPEAKING }
data class VoiceTurnState(val ticket: Long = 0, val epoch: Long = 0, val phase: VoicePhase = VoicePhase.OFF, val origin: String = "")

/** Tokens are shared across wake, TTS and STT; old completions cannot take over a new turn. */
class VoiceTurnGate {
    val state = MutableStateFlow(VoiceTurnState())
    val isLiveCallMode = MutableStateFlow(false)
    fun setLiveCallMode(enabled:Boolean) { isLiveCallMode.value=enabled }
    @Synchronized fun interruptForBargeIn(epoch:Long):Boolean = isLiveCallMode.value && state.value.epoch==epoch && state.value.phase!=VoicePhase.OFF && state.value.phase!=VoicePhase.LISTENING
    @Synchronized fun begin(epoch: Long, phase: VoicePhase, origin: String): Long {
        state.value = VoiceTurnState(state.value.ticket + 1, epoch, phase, origin)
        return state.value.ticket
    }
    @Synchronized fun accepts(ticket: Long, epoch: Long) = state.value.ticket == ticket && state.value.epoch == epoch && state.value.phase != VoicePhase.OFF
    @Synchronized fun move(ticket: Long, epoch: Long, phase: VoicePhase): Boolean {
        if (!accepts(ticket, epoch)) return false
        state.value = state.value.copy(phase = phase); return true
    }
    @Synchronized fun cancel(epoch: Long, running: Boolean) { if(!running)isLiveCallMode.value=false;begin(epoch, if (running) VoicePhase.IDLE else VoicePhase.OFF, "cancelled") }
}

object VoiceSession { val gate = VoiceTurnGate(); val state get() = gate.state }

object WakeWords {
    private val word = Regex("\\b(?:(?:hey|hi|okay|wake\\s+up)\\s+)?(?:elliot|elliott|eiliot)\\b", RegexOption.IGNORE_CASE)
    fun contains(text: String) = word.containsMatchIn(text)
    fun afterWake(text: String): String? = word.find(text)?.let { text.substring(it.range.last + 1).trim(' ', ',', '.', ':', '!', '?') }
    fun command(text: String) = if(word.find(text.trim())?.range?.first==0)afterWake(text.trim()).orEmpty()else text.trim()
}
