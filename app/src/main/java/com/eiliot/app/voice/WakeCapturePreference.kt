package com.eiliot.app.voice

import android.content.Context

/** Manual microphone choice; this is not a successful sensitivity calibration. */
object WakeCapturePreference {
    fun reference(c:Context)=c.getSharedPreferences("wake_capture",0).getBoolean("direct",false)
    fun noiseReduction(c:Context)=c.getSharedPreferences("wake_capture",0).getBoolean("reduce_noise",false)
    fun setNoiseReduction(c:Context,value:Boolean) {
        check(c.getSharedPreferences("wake_capture",0).edit().putBoolean("reduce_noise",value).commit())
        VoiceCalibrationStore.clear(c)
    }
    fun setReference(c:Context,value:Boolean) {
        check(c.getSharedPreferences("wake_capture",0).edit().putBoolean("direct",value).commit())
        // Scores validated on another microphone path must not carry over.
        VoiceCalibrationStore.clear(c)
    }
}
