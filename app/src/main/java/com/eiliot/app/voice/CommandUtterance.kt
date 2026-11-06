package com.eiliot.app.voice

/** Captures a single bounded command, using the live VAD before any recognizer runs. */
internal class CommandUtterance(waitForWakePause: Boolean) {
    sealed interface Result {
        data class Ready(val pcm: FloatArray) : Result
        data object TooLong : Result
    }

    // Half a second of history, matching the wake path, so the opening word survives whichever
    // branch starts the capture.
    private val preRoll = AudioPreRoll(PRE_ROLL_SAMPLES)
    private val samples = FloatArray(16000 * 12)
    private var quiet = 0
    private var onset = 0
    private var used = 0
    private var waiting = waitForWakePause
    private var talking = 0
    val speechStarted get() = used > 0

    fun accept(frame: FloatArray, probability: Float): Result? {
        require(frame.size == 512)
        if (waiting) {
            // Retain the audio rather than discarding it: a request that runs straight out of the
            // wake phrase ("hey Elliot what's my battery") never pauses, and its opening words are
            // only recoverable from this buffer.
            preRoll.add(frame)
            if (probability < .35f) { quiet++; talking = 0 } else { quiet = 0; talking++ }
            when {
                // A pause proves the wake phrase finished, so anything before it is the phrase
                // itself and must not reach the recognizer.
                quiet >= WAKE_TAIL_FRAMES -> { waiting = false; quiet = 0; preRoll.clear() }
                // Speech this far past the wake hit is the request. Keep the retained audio; any
                // leading "hey Elliot" is removed from the transcript instead.
                talking >= CONTINUOUS_SPEECH_FRAMES -> { waiting = false; quiet = 0 }
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
        if (quiet < END_OF_SPEECH_FRAMES) return null
        // Keep the last 256 ms of tail for soft final consonants.
        return Result.Ready(samples.copyOf(used - (quiet - 8) * frame.size))
    }

    fun clear() {
        preRoll.clear()
        samples.fill(0f, 0, used)
        used = 0
    }

    private companion object {
        /** 500 ms of retained audio. */
        const val PRE_ROLL_SAMPLES = 8000

        /** 256 ms of quiet marks the end of the wake phrase itself. */
        const val WAKE_TAIL_FRAMES = 8

        /** 800 ms of unbroken speech past the wake hit is a request, not a wake tail. */
        const val CONTINUOUS_SPEECH_FRAMES = 25

        /** 1.15 s of quiet ends the request. */
        const val END_OF_SPEECH_FRAMES = 36
    }
}
