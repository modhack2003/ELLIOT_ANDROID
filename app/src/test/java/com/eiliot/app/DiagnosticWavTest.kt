package com.eiliot.app
import com.eiliot.app.voice.DiagnosticWav
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Test
import org.junit.Assert.*
class DiagnosticWavTest {
    @Test fun preservesPcmAndWritesPlayableHeader() {
        val pcm=shortArrayOf(-32768,-12345,0,12345,32767)
        val wav=DiagnosticWav.encode(FloatArray(pcm.size){pcm[it]/32768f})
        val b=ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF",String(wav,0,4));assertEquals(wav.size-8,b.getInt(4))
        assertEquals(16000,b.getInt(24));assertEquals(16,b.getShort(34).toInt())
        assertEquals(pcm.size*2,b.getInt(40))
        pcm.forEachIndexed {i,v->assertEquals(v,b.getShort(44+i*2))}
    }
    @Test fun rejectsInvalidOrUnboundedAudio() {
        for(a in listOf(FloatArray(0),floatArrayOf(Float.NaN),FloatArray(16000*8+1)))
            assertThrows(IllegalArgumentException::class.java){DiagnosticWav.encode(a)}
    }
}
