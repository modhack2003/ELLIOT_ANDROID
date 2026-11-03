package com.eiliot.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.media.audiofx.AutomaticGainControl
import java.io.InputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** One PCM capture shared by wake and command recognition; audio is never written to disk. */
class VoiceAudioInput(context: Context, private val referenceCapture: Boolean = false, suppressNoise:Boolean=false, private val beforeRead: () -> Unit) : InputStream() {
    private val closed = AtomicBoolean(false)
    private val recorder: AudioRecord
    private val echo: AcousticEchoCanceler?
    private val noise: NoiseSuppressor?
    private val gain: AutomaticGainControl?
    var onFrame: ((FloatArray) -> Unit)? = null
    private val frame = FloatArray(512)
    private var frameIndex = 0
    private var lowByte = -1
    val echoEnabled: Boolean
    val noiseEnabled: Boolean
    val gainEnabled: Boolean
    init {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) throw SecurityException("Grant microphone access before arming voice")
        val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "16 kHz microphone input is unavailable" }
        val record = AudioRecord.Builder().setAudioSource(if(referenceCapture) MediaRecorder.AudioSource.MIC else MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(minimum * 2, 12800)).build()
        recorder = record
        if (record.state != AudioRecord.STATE_INITIALIZED) { record.release(); error("Microphone could not initialize") }
        echo = runCatching { if(!referenceCapture && AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(record.audioSessionId)?.also { it.enabled = true } else null }.getOrNull()
        echoEnabled = echo?.enabled == true
        noise = runCatching { if((!referenceCapture || suppressNoise) && NoiseSuppressor.isAvailable()) NoiseSuppressor.create(record.audioSessionId)?.also { it.enabled=true } else null }.getOrNull()
        noiseEnabled=noise?.enabled==true
        gain = runCatching { if(AutomaticGainControl.isAvailable()) AutomaticGainControl.create(record.audioSessionId)?.also { it.enabled=!referenceCapture } else null }.getOrNull()
        gainEnabled=gain?.enabled==true
        try { record.startRecording(); check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone is in use or blocked" } }
        catch (e: Exception) { echo?.release();noise?.release();gain?.release();record.release();throw e }
    }
    override fun read(): Int = throw IOException("PCM capture requires a byte buffer")
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (closed.get()) return -1
        beforeRead()
        val count = recorder.read(buffer, offset, length)
        if (closed.get()) return -1
        if (count < 0) throw IOException("Microphone read failed ($count)")
        if(onFrame!=null) for(i in offset until offset+count) {
            val value=buffer[i].toInt() and 255
            if(lowByte<0) lowByte=value else {
                frame[frameIndex++]=((value shl 8) or lowByte).toShort().toFloat()/32768f;lowByte=-1
                if(frameIndex==512) { onFrame?.invoke(frame.copyOf());frameIndex=0 }
            }
        }
        return count
    }
    /** Stop unblocks the read; release happens after the recognizer thread has joined. */
    fun stopCapture() { if (closed.compareAndSet(false, true)) runCatching { recorder.stop() } }
    override fun close() { stopCapture();echo?.release();noise?.release();gain?.release();recorder.release();onFrame=null }
}
