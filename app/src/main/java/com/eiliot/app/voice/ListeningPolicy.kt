package com.eiliot.app.voice

/** Independent wait-for-speech and maximum-utterance budgets, using monotonic time. */
class ListeningDeadline(
    private val startedAt: Long,
    private val waitMs: Long = 18000,
    private val utteranceMs: Long = 60000,
) {
    init { require(waitMs > 0 && utteranceMs > 0) }
    @Volatile private var speechAt: Long? = null
    @Synchronized fun speech(now: Long) {
        if (speechAt == null && now - startedAt < waitMs) speechAt = now
    }
    fun remaining(now: Long): Long = ((speechAt?.let { it + utteranceMs }
        ?: (startedAt + waitMs)) - now).coerceAtLeast(0)
}

/** Read each transient result before the following decode can replace it. */
fun drainWakeResults(
    ready: () -> Boolean,
    decode: () -> Unit,
    result: () -> String,
    reset: () -> Unit,
    detected: (String) -> Unit,
) {
    while (ready()) {
        decode()
        val word = result()
        if (word.isNotBlank()) {
            reset()
            detected(word)
        }
    }
}
