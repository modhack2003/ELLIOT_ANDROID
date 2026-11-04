package com.eiliot.app

import android.app.Application
import com.eiliot.app.voice.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class, sdk=[28])
class WakeCapturePreferenceTest {
    @Test fun changingCaptureInvalidatesSensitivityForOldMicrophone() {
        val app=RuntimeEnvironment.getApplication()
        assertFalse(WakeCapturePreference.reference(app))
        val profile=WakeSetting(false,2.5f,.1f,true,true,true,phonemeModel=true,requireHey=true,
            learnedPhones=listOf("HH","EY1","y","ī","l","iè"))
        VoiceCalibrationStore.save(app,profile)
        assertEquals(profile,VoiceCalibrationStore.load(app))
        WakeCapturePreference.setReference(app,true)
        assertTrue(WakeCapturePreference.reference(app))
        assertNull(VoiceCalibrationStore.load(app))
        WakeCapturePreference.setReference(app,false)
        assertFalse(WakeCapturePreference.reference(app))
        VoiceCalibrationStore.save(app,profile)
        WakeCapturePreference.setNoiseReduction(app,true)
        assertTrue(WakeCapturePreference.noiseReduction(app))
        assertNull(VoiceCalibrationStore.load(app))
        WakeCapturePreference.setNoiseReduction(app,false)
        assertFalse(WakeCapturePreference.noiseReduction(app))
        VoiceCalibrationStore.save(app,profile)
        WakeCapturePreference.setSoftwareNoiseReduction(app,true)
        assertTrue(WakeCapturePreference.softwareNoiseReduction(app))
        assertNull(VoiceCalibrationStore.load(app))
    }
}
