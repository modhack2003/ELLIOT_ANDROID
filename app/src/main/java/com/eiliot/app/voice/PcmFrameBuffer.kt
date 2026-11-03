package com.eiliot.app.voice

/** Reassembles variable denoiser output into the 512 samples required by VAD. */
class PcmFrameBuffer {
    private val frame=FloatArray(512)
    private var used=0
    fun push(samples:FloatArray,consume:(FloatArray)->Unit) {
        for(sample in samples) {
            frame[used++]=sample
            if(used==frame.size) {used=0;consume(frame.copyOf())}
        }
    }
    fun clear() {frame.fill(0f);used=0}
}
