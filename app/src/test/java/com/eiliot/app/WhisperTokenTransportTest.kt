package com.eiliot.app

import com.eiliot.app.voice.WhisperTokenTransport
import java.util.Base64
import org.junit.Test
import org.junit.Assert.*

class WhisperTokenTransportTest {
    private fun render(bytes:ByteArray):String = String(Base64.getDecoder().decode(
        WhisperTokenTransport.encodeToken(Base64.getEncoder().encodeToString(bytes))),Charsets.US_ASCII)

    @Test fun preservesCharactersSplitAcrossTokens() {
        for(text in listOf("Elliot, আমার ফোনের battery level কত?", "मेरे फोन की battery कितनी है?", "Hello, Elliot!", "مرحبا", "😀")) {
            val rendered=text.toByteArray(Charsets.UTF_8).joinToString("") {render(byteArrayOf(it))}
            assertEquals(text,WhisperTokenTransport.decode(rendered))
        }
    }

    @Test fun reproducesNativeByteCleanupLoss() {
        val text="আমার"
        val fragments=text.toByteArray(Charsets.UTF_8).map {byteArrayOf(it)}
        val lossy=fragments.joinToString("") {it.toString(Charsets.UTF_8).replace("\uFFFD","")}
        assertEquals("",lossy)
        assertEquals(text,WhisperTokenTransport.decode(fragments.joinToString(""){render(it)}))
    }

    @Test fun keepsWhitespaceAndReportsIncompleteOutput() {
        val placeholder=String(Base64.getDecoder().decode(WhisperTokenTransport.encodeToken("=")),Charsets.US_ASCII)
        assertEquals(" ",WhisperTokenTransport.decode(placeholder))
        assertEquals("  hi\n",WhisperTokenTransport.decode(render("  hi\n".toByteArray())))
        assertEquals("",WhisperTokenTransport.decode(""))
        assertEquals("\uFFFD",WhisperTokenTransport.decode("e0a6"))
        assertThrows(IllegalArgumentException::class.java){WhisperTokenTransport.decode("abc")}
        assertThrows(IllegalArgumentException::class.java){WhisperTokenTransport.decode("zz")}
    }
}
