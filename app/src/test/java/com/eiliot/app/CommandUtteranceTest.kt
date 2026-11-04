package com.eiliot.app

import com.eiliot.app.voice.CommandUtterance
import org.junit.Assert.*
import org.junit.Test

class CommandUtteranceTest {
    private val wake = FloatArray(512) { .25f }
    private val request = FloatArray(512) { .5f }
    private val quiet = FloatArray(512)

    @Test fun separatesWakeTailAndKeepsSpeechAcrossAnOrdinaryPause() {
        val capture = CommandUtterance(waitForWakePause = true)
        repeat(10) { assertNull(capture.accept(wake, .95f)) }
        repeat(8) { assertNull(capture.accept(quiet, .01f)) }
        assertFalse(capture.speechStarted)
        repeat(4) { assertNull(capture.accept(request, .8f)) }
        repeat(20) { assertNull(capture.accept(quiet, .01f)) }
        repeat(4) { assertNull(capture.accept(request, .8f)) }
        repeat(35) { assertNull(capture.accept(quiet, .01f)) }
        val pcm = (capture.accept(quiet, .01f) as CommandUtterance.Result.Ready).pcm
        assertTrue(capture.speechStarted)
        assertEquals(4096, pcm.count { it == .5f })
        assertFalse(pcm.any { it == .25f })
    }

    @Test fun ignoresSingleSpeechSpikesAndRejectsAnOverlongUtterance() {
        val capture = CommandUtterance(waitForWakePause = false)
        repeat(12) {
            assertNull(capture.accept(request, .8f))
            assertNull(capture.accept(quiet, .01f))
        }
        assertFalse(capture.speechStarted)
        var result: CommandUtterance.Result? = null
        repeat(390) {
            if (result == null) result = capture.accept(request, .8f)
        }
        assertEquals(CommandUtterance.Result.TooLong, result)
    }
}
