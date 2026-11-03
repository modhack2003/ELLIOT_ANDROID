package com.eiliot.app

import com.eiliot.app.voice.PcmFrameBuffer
import org.junit.Assert.*
import org.junit.Test

class PcmFrameBufferTest {
    @Test fun preservesOrderAcrossVariableDenoiserOutputAndKeepsFramesIndependent() {
        val buffer=PcmFrameBuffer();val frames=mutableListOf<FloatArray>()
        val source=FloatArray(2048){it.toFloat()}
        var at=0
        for(size in listOf(0,256,512,13,1024,243)) {
            buffer.push(source.copyOfRange(at,at+size)){frames.add(it)};at+=size
        }
        assertEquals(4,frames.size);assertTrue(frames.all{it.size==512})
        assertArrayEquals(source,frames.flatMap{it.asList()}.toFloatArray(),0f)
    }
    @Test fun clearingDiscardsOnlyIncompleteFrame() {
        val buffer=PcmFrameBuffer();val frames=mutableListOf<FloatArray>()
        buffer.push(FloatArray(256){1f}){frames.add(it)};buffer.clear()
        buffer.push(FloatArray(512){2f}){frames.add(it)}
        assertEquals(1,frames.size);assertTrue(frames[0].all{it==2f})
    }
}
