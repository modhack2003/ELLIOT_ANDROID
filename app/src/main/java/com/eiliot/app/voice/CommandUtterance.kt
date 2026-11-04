package com.eiliot.app.voice

/** Captures a single bounded command, using the live VAD before any recognizer runs. */
internal class CommandUtterance(waitForWakePause: Boolean) {
    sealed interface Result {
        data class Ready(val pcm: FloatArray) : Result
        data object TooLong : Result
    }

    private val preRoll = AudioPreRoll(4096)
    private val samples = FloatArray(16000 * 12)
    private var quiet = 0
    private var onset = 0
    private var used = 0
    private var waiting = waitForWakePause
    val speechStarted get() = used > 0

    fun accept(frame: FloatArray, probability: Float): Result? {
        require(frame.size == 512)
        if (waiting) {
            quiet = if (probability < .35f) quiet + 1 else 0
            if (quiet == 8) {
                waiting = false
                quiet = 0
            }
            return null
        }
        if (used == 0) {
            preRoll.add(frame)
            onset = if (probability > .5f) onset + 1 else 0
            if (onset == 2) {
                val start = preRoll.snapshot()
                start.copyInto(samples)
                used = start.size
                start.fill(0f)
                preRoll.clear()
            }
            return null
        }
        if (used + frame.size > samples.size) return Result.TooLong
        frame.copyInto(samples, used)
        used += frame.size
        quiet = if (probability > .35f) 0 else quiet + 1
        if (quiet < 36) return null
        // Keep the last 256 ms of tail for soft final consonants.
        return Result.Ready(samples.copyOf(used - (quiet - 8) * frame.size))
    }

    fun clear() {
        preRoll.clear()
        samples.fill(0f, 0, used)
        used = 0
    }
}
