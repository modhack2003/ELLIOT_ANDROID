package com.eiliot.app.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Local, explicit export only. PCM16 at the capture rate, bounded to eight seconds. */
object DiagnosticWav {
    fun encode(audio:FloatArray):ByteArray {
        require(audio.isNotEmpty() && audio.size<=8*16000 && audio.all {it.isFinite()})
        val bytes=audio.size*2
        return ByteBuffer.allocate(44+bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray());putInt(36+bytes);put("WAVEfmt ".toByteArray());putInt(16)
            putShort(1);putShort(1);putInt(16000);putInt(32000);putShort(2);putShort(16)
            put("data".toByteArray());putInt(bytes)
            audio.forEach {putShort((it.coerceIn(-1f,1f)*32768).roundToInt().coerceIn(-32768,32767).toShort())}
        }.array()
    }
}
