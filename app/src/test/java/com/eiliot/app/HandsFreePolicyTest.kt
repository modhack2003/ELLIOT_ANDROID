package com.eiliot.app

import com.eiliot.app.core.CheckInSchedule
import com.eiliot.app.tools.*
import com.eiliot.app.voice.*
import java.time.LocalTime
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HandsFreePolicyTest {
    @Test fun interruptedTtsCompletionCannotCloseTheNewListeningTurn() {
        val gate = VoiceTurnGate()
        val speaking = gate.begin(1, VoicePhase.SPEAKING, "old answer")
        val wake = gate.begin(1, VoicePhase.LISTENING, "wake")
        assertFalse(gate.move(speaking, 1, VoicePhase.IDLE))
        assertEquals(VoicePhase.LISTENING, gate.state.value.phase)
        assertTrue(gate.accepts(wake, 1))
    }
    @Test fun offInvalidatesListeningAndSpeechEvenAfterResume() {
        val gate = VoiceTurnGate(); val before = gate.begin(3, VoicePhase.LISTENING, "reply")
        gate.cancel(4, false)
        assertFalse(gate.accepts(before, 3)); assertEquals(VoicePhase.OFF, gate.state.value.phase)
        val after = gate.begin(4, VoicePhase.LISTENING, "new")
        assertFalse(gate.move(before, 3, VoicePhase.SPEAKING)); assertTrue(gate.accepts(after, 4))
    }
    @Test fun sameUtteranceAfterWakeKeepsTheCommandAndSecondName() {
        assertEquals("open YouTube", WakeWords.afterWake("hey Elliot open YouTube"))
        assertEquals("search for Elliot Alderson", WakeWords.afterWake("Elliot search for Elliot Alderson"))
        assertEquals("", WakeWords.afterWake("Elliot"))
        assertFalse(WakeWords.contains("elliotson")); assertFalse(WakeWords.contains("heliotrope"))
    }
    @Test fun checkInsUseTheScheduleRatherThanUserInactivity() {
        val schedule = CheckInSchedule(480, 1320, 60)
        assertTrue(schedule.due(3600000, 0, 0, LocalTime.of(12,0)))
        assertFalse(schedule.due(3599999, 0, 0, LocalTime.of(12,0)))
        assertFalse(schedule.due(7200000, 0, 8000000, LocalTime.of(12,0)))
    }
    @Test fun scheduleHasExclusiveEndAndSupportsMidnightWindows() {
        val day = CheckInSchedule(480, 1320, 60)
        assertTrue(day.inside(LocalTime.of(8,0))); assertFalse(day.inside(LocalTime.of(22,0)))
        val night = CheckInSchedule(1320, 360, 30)
        assertTrue(night.inside(LocalTime.of(23,0))); assertTrue(night.inside(LocalTime.of(5,59)))
        assertFalse(night.inside(LocalTime.of(6,0))); assertFalse(night.inside(LocalTime.NOON))
        assertTrue(CheckInSchedule(0,0,60).inside(LocalTime.MIDNIGHT))
    }
    @Test fun missedCheckInsProduceOneQuestionNotAnAccumulatedBurst() {
        val schedule = CheckInSchedule(0,0,30); val now = 12 * 3600000L
        assertTrue(schedule.due(now, 0, 0, LocalTime.NOON))
        assertFalse(schedule.due(now + 10000, now, 0, LocalTime.NOON))
        assertFalse(schedule.due(now - 10000, now, 0, LocalTime.NOON))
    }
    @Test fun invalidSchedulesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { CheckInSchedule(1440,0,60) }
        assertThrows(IllegalArgumentException::class.java) { CheckInSchedule(0,60,0) }
    }
    @Test fun clearVolumeCommandAllowsOnlyTheRequestedValue() {
        val grant = CommandGrant.request(4, "Set media volume to thirty percent")
        assertTrue(grant.permits("set_media_volume", JSONObject().put("percent",30)))
        assertFalse(grant.permits("set_media_volume", JSONObject().put("percent",100)))
        assertFalse(grant.permits("open_app", JSONObject().put("package","org.other")))
    }
    @Test fun otherNumbersInATaskCannotChangeTheRequestedVolume() {
        val grant = CommandGrant.request(1, "Set volume to 30 percent and start a timer for 60 seconds")
        assertTrue(grant.permits("set_media_volume", JSONObject().put("percent",30)))
        assertFalse(grant.permits("set_media_volume", JSONObject().put("percent",60)))
    }
    @Test fun negatedAndHypotheticalCommandsDoNotAuthorizeChanges() {
        for (text in listOf("Don't set volume to 30", "What if I set volume to 30", "Tell me how to set volume to 30"))
            assertFalse(text, CommandGrant.request(1,text).permits("set_media_volume", JSONObject().put("percent",30)))
        assertFalse(CommandGrant.request(1,"What is my home address?").permits("navigate",JSONObject().put("action","home")))
    }
    @Test fun timerDurationAndSettingsTargetAreBoundToTheRequest() {
        val timer = CommandGrant.request(1, "Start a timer for five minutes")
        assertTrue(timer.permits("set_timer", JSONObject().put("seconds",300)))
        assertFalse(timer.permits("set_timer", JSONObject().put("seconds",600)))
        val settings = CommandGrant.request(1,"Open WiFi settings")
        assertTrue(settings.permits("open_settings",JSONObject().put("page","wifi")))
        assertFalse(settings.permits("open_settings",JSONObject().put("page","bluetooth")))
    }
    @Test fun screenTextAndScrollDirectionCannotBeChangedByTheModel() {
        val field = CommandGrant.request(1,"Type Hello friend in the search field")
        assertTrue(field.permits("type_text",JSONObject().put("text","Hello friend")))
        assertFalse(field.permits("type_text",JSONObject().put("text","A different message")))
        val scroll = CommandGrant.request(1,"Scroll down")
        assertTrue(scroll.permits("read_screen",JSONObject()))
        assertTrue(scroll.permits("scroll_screen",JSONObject().put("direction","down")))
        assertFalse(scroll.permits("scroll_screen",JSONObject().put("direction","up")))
        val tap = CommandGrant.request(1,"Tap the Next button")
        assertTrue(tap.permitsScreenLabel("Next")); assertFalse(tap.permitsScreenLabel("Like"))
    }
    @Test fun explicitNativeButtonGrantDoesNotAuthorizeOtherTools() {
        val grant = CommandGrant.direct(8,"set_media_volume")
        assertTrue(grant.permits("set_media_volume",JSONObject().put("percent",42)))
        assertFalse(grant.permits("open_app",JSONObject()))
    }
    @Test fun suppliedUrlCannotBeReplacedWithAnotherDomain() {
        val grant = CommandGrant.request(1,"Open https://example.org/guide")
        assertTrue(grant.permits("open_url",JSONObject().put("url","https://example.org/guide")))
        assertFalse(grant.permits("open_url",JSONObject().put("url","https://other.example/")))
    }
    @Test fun localCommandsWorkWithoutAModelAndResolveOnlyUnambiguousApps() {
        assertEquals(30,JSONObject(LocalCommands.parse("set volume to thirty percent") { emptyList() }!!.arguments).getInt("percent"))
        assertEquals(300,JSONObject(LocalCommands.parse("start a timer for five minutes") { emptyList() }!!.arguments).getInt("seconds"))
        val apps = listOf(LaunchableApp("YouTube","com.example.video"))
        assertEquals("com.example.video",JSONObject(LocalCommands.parse("open YouTube") { apps }!!.arguments).getString("package"))
        assertNull(LocalCommands.parse("open YouTube") { apps + LaunchableApp("YouTube","com.example.other") })
        assertNull(LocalCommands.parse("don't set volume to 30 percent") { emptyList() })
        for(question in listOf("What is my battery level?", "मेरे फोन का battery level कितना है।", "আমার ফোনের battery level কত?")) {
            assertEquals("device_info",LocalCommands.parse(question) {emptyList()}?.name)
        }
        assertNull(LocalCommands.parse("What does my back to deliver") {emptyList()})
    }
    @Test fun spokenDurationsAreBoundedAndSupportCompoundUnits() {
        assertEquals(3720,SpokenNumbers.timerSeconds("one hour and two minutes"))
        assertEquals(125,SpokenNumbers.timerSeconds("two minutes five seconds"))
        assertNull(SpokenNumbers.timerSeconds("9999999999999999999999999999 hours"))
        assertNull(SpokenNumbers.timerSeconds("zero seconds"))
    }
}
