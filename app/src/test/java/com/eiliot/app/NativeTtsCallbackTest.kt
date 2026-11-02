package com.eiliot.app

import com.eiliot.app.voice.remote.NativeTtsCallback
import org.junit.Assert.*
import org.junit.Test

class NativeTtsCallbackTest {
    @Test fun preservesContinueSignal() {
        assertEquals(1, NativeTtsCallback { 1 }.invoke(floatArrayOf(.1f)))
    }
    @Test fun nativeLookupFindsBoxedMethodAndPreservesStopSignal() {
        val pcm = floatArrayOf(.1f, -.1f)
        var received: FloatArray? = null
        val callback = NativeTtsCallback { received = it; 0 }
        val method = callback.javaClass.getMethod("invoke", FloatArray::class.java)
        assertEquals(java.lang.Integer::class.java, method.returnType)
        assertEquals(0, method.invoke(callback, pcm))
        assertSame(pcm, received)
    }
}
