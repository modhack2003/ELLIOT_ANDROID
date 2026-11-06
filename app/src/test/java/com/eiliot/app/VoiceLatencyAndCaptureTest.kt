package com.eiliot.app

import com.eiliot.app.voice.CommandUtterance
import com.eiliot.app.voice.TranscriptNormalizer
import com.eiliot.app.voice.WhisperTokenTransport
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Base64

/**
 * Covers the capture and startup behavior that decides whether a spoken request is heard
 * completely and answered promptly. These are pure-Kotlin checks; acoustic quality still needs a
 * handset.
 */
class VoiceLatencyAndCaptureTest {
    @get:Rule val folder = TemporaryFolder()

    private val speech = FloatArray(512) { .5f }
    private val quiet = FloatArray(512)

    /** The case that previously lost words: the request runs straight out of the wake phrase. */
    @Test fun requestSpokenWithoutAPauseAfterTheWakePhraseKeepsItsOpeningWords() {
        val capture = CommandUtterance(waitForWakePause = true)
        // No gap at all between "hey Elliot" and the request.
        repeat(60) { assertNull(capture.accept(speech, .9f)) }
        assertTrue("capture must start without waiting for a pause", capture.speechStarted)
        repeat(40) { capture.accept(quiet, .01f) }
        val result = capture.accept(quiet, .01f) ?: capture.accept(quiet, .01f)
        val pcm = (result as CommandUtterance.Result.Ready).pcm
        assertTrue("retained audio should cover the opening words", pcm.count { it == .5f } >= 4096)
    }

    /** The ordinary case must still skip the wake tail rather than transcribe it. */
    @Test fun requestAfterAClearPauseStillDropsTheWakeTail() {
        val wake = FloatArray(512) { .25f }
        val capture = CommandUtterance(waitForWakePause = true)
        repeat(4) { assertNull(capture.accept(wake, .95f)) }
        repeat(10) { assertNull(capture.accept(quiet, .01f)) }
        assertFalse(capture.speechStarted)
        repeat(8) { assertNull(capture.accept(speech, .8f)) }
        repeat(40) { capture.accept(quiet, .01f) }
        val pcm = ((capture.accept(quiet, .01f) ?: capture.accept(quiet, .01f)) as CommandUtterance.Result.Ready).pcm
        assertFalse("the wake phrase itself must not reach the recognizer", pcm.any { it == .25f })
    }

    @Test fun spokenAddressIsRemovedSoOfflineCommandsStillMatch() {
        assertEquals("what is my battery level", TranscriptNormalizer.stripWakeAddress("hey Elliot what is my battery level"))
        assertEquals("what is my battery level", TranscriptNormalizer.stripWakeAddress("Elliot, what is my battery level"))
        assertEquals("set a timer for five minutes", TranscriptNormalizer.stripWakeAddress("OK Elliot set a timer for five minutes"))
        // A bare wake phrase is left intact for the caller to recognise as "no request yet".
        assertEquals("hey Elliot", TranscriptNormalizer.stripWakeAddress("hey Elliot"))
        // Never strip a request that merely mentions the name mid-sentence.
        assertEquals("tell Elliot about it", TranscriptNormalizer.stripWakeAddress("tell Elliot about it"))
        assertEquals("What is my battery level", TranscriptNormalizer.normalize("hey Elliot what is my battery level"))
    }

    /** The token table is rewritten once, not on every request. */
    @Test fun preparedTokenTableIsReusedInsteadOfRebuiltPerRequest() {
        val source = folder.newFile("tokens.txt")
        val encoded = Base64.getEncoder().encodeToString("hi".toByteArray())
        source.writeText((0 until 40).joinToString("\n") { "$encoded $it" } + "\n")
        val cache = folder.newFolder("cache")

        val first = WhisperTokenTransport.prepare(source, cache)
        assertTrue(first.isFile)
        val stamped = first.lastModified()
        val body = first.readText()

        // A second call must not rewrite the file.
        first.setLastModified(stamped - 60_000)
        val again = WhisperTokenTransport.prepare(source, cache)
        assertEquals(first.absolutePath, again.absolutePath)
        assertEquals(stamped - 60_000, again.lastModified())
        assertEquals(body, again.readText())

        // A changed source pack is picked up again.
        source.writeText("$encoded 99\n")
        val rebuilt = WhisperTokenTransport.prepare(source, cache)
        assertNotEquals(body, rebuilt.readText())
    }
}
