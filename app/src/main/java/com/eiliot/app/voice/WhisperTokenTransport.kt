package com.eiliot.app.voice

import java.io.File
import java.util.Base64

/**
 * Sherpa 1.13.8 normalizes each Whisper byte token as UTF-8 before joining it.
 * A token may contain only part of a Unicode character; that cleanup loses bytes.
 * Render tokens as ASCII hex through native/JNI, then decode the complete byte
 * sequence here. IDs, model inputs and predictions are unchanged. Do not enable
 * native ITN/homophone rules on this transport representation.
 */
object WhisperTokenTransport {
    private const val HEX="0123456789abcdef"

    fun encodeToken(base64:String):String {
        // Upstream table's final placeholder uses "="; Sherpa maps it to space.
        val bytes=if(base64=="=")byteArrayOf(32) else Base64.getDecoder().decode(base64)
        val hex=buildString(bytes.size*2) {
            for(byte in bytes) {val n=byte.toInt() and 255;append(HEX[n ushr 4]);append(HEX[n and 15])}
        }
        return Base64.getEncoder().encodeToString(hex.toByteArray(Charsets.US_ASCII))
    }

    /** Called only after the original pinned pack has passed SHA-256 checks. */
    @Synchronized fun prepare(source:File,cache:File):File {
        check(cache.isDirectory || cache.mkdirs())
        val target=File(cache,"whisper-hex-tokens-v1.txt")
        val stage=File(cache,"whisper-hex-tokens-v1.tmp")
        stage.bufferedWriter(Charsets.US_ASCII).use {out->
            source.forEachLine(Charsets.US_ASCII) {line->
                val parts=line.trim().split(Regex("\\s+"))
                require(parts.size==2 && parts[1].toIntOrNull()!=null)
                out.append(encodeToken(parts[0])).append(' ').append(parts[1]).append('\n')
            }
        }
        check(stage.renameTo(target)){"Could not prepare speech text decoder"}
        return target
    }

    fun decode(text:String):String {
        require(text.length%2==0 && text.all {it in HEX}) {"Invalid speech text transport"}
        val bytes=ByteArray(text.length/2) {i->
            ((HEX.indexOf(text[i*2]) shl 4) or HEX.indexOf(text[i*2+1])).toByte()
        }
        // A model hitting its token limit may end mid-character. Keep a visible
        // replacement character instead of silently deleting incomplete output.
        return bytes.toString(Charsets.UTF_8)
    }
}
