package com.eiliot.app

import com.eiliot.app.voice.*
import org.junit.Assert.*
import org.junit.Test

class ListeningPolicyTest {
    @Test fun waitingWithoutSpeechExpires() {
        val deadline=ListeningDeadline(1000)
        assertEquals(1L,deadline.remaining(18999))
        assertEquals(0L,deadline.remaining(19000))
        deadline.speech(19001)
        assertEquals(0L,deadline.remaining(19001))
    }
    @Test fun speechNearWaitDeadlineGetsItsOwnBoundedBudget() {
        val deadline=ListeningDeadline(1000)
        deadline.speech(18000)
        assertEquals(59000L,deadline.remaining(19000))
        deadline.speech(70000) // Later frames must not extend the maximum indefinitely.
        assertEquals(0L,deadline.remaining(78000))
    }
    @Test fun detectionIsReadAndResetBeforeNextDecode() {
        val events=mutableListOf<String>();var frame=0;var word=""
        drainWakeResults({frame<3},{frame++;word=if(frame==1)"elliot" else "";events.add("decode")},
            {word},{events.add("reset");word=""},{events.add(it)})
        assertEquals(listOf("decode","reset","elliot","decode","decode"),events)
    }
    @Test fun blankFramesDoNotResetOrActivate() {
        var frame=0
        drainWakeResults({frame<2},{frame++},{""},{fail("Unexpected reset")},{fail("Unexpected detection")})
        assertEquals(2,frame)
    }
}
