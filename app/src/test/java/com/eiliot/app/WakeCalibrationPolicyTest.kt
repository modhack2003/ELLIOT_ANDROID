package com.eiliot.app

import com.eiliot.app.voice.*
import org.junit.Assert.*
import org.junit.Test

class WakeCalibrationPolicyTest {
    private fun result(setting:WakeSetting,hits:Int=4,falseHits:Int=0,phrases:Set<String> = setOf("hey","name"))=
        WakeAssessment(setting,hits,4,falseHits,2,phrases)
    @Test fun missesOnePhraseOrFalseTriggersCannotPass() {
        assertNull(WakeCalibrationPolicy.choose(listOf(result(WakeSetting(false),phrases=setOf("name")))))
        assertNull(WakeCalibrationPolicy.choose(listOf(result(WakeSetting(false),falseHits=1))))
        assertNull(WakeCalibrationPolicy.choose(listOf(result(WakeSetting(false),hits=2))))
    }
    @Test fun prefersMoreRestrictiveThresholdOnEqualRecall() {
        val strict=result(WakeSetting(true,2f,.15f))
        val permissive=result(WakeSetting(false,3f,.08f))
        assertEquals(strict,WakeCalibrationPolicy.choose(listOf(permissive,strict)))
    }
    @Test fun validationNeedsBothFreshHitsAndNoFalseHits() {
        assertTrue(WakeCalibrationPolicy.validationPass(listOf(true,true,false,false)))
        assertFalse(WakeCalibrationPolicy.validationPass(listOf(false,true,false,false)))
        assertFalse(WakeCalibrationPolicy.validationPass(listOf(true,true,true,false)))
    }
    @Test fun invalidParametersAndDistortedAudioAreRejected() {
        assertFalse(WakeCalibration.usable(FloatArray(16000)))
        assertFalse(WakeCalibration.usable(FloatArray(16000){1f}))
        assertTrue(WakeCalibration.usable(FloatArray(16000){if(it%2==0).1f else -.1f}))
        assertThrows(IllegalArgumentException::class.java){WakeSetting(false,Float.NaN,.1f)}
    }
}
