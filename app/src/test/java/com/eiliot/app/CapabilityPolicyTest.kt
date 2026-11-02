package com.eiliot.app

import com.eiliot.app.capabilities.*
import com.eiliot.app.tools.ToolCatalog
import org.junit.Assert.*
import org.junit.Test

class CapabilityPolicyTest {
    private val base = CapabilityFacts(running = true, now = 1_000_000)
    private fun get(id: String, f: CapabilityFacts = base) = checkNotNull(CapabilityPolicy.evaluate(f).entry(id))

    @Test fun inventoryPreservesAllSixteenActualToolsWithoutInventingOne() {
        val snapshot = CapabilityPolicy.evaluate(base)
        assertEquals(16, snapshot.entries.count { it.tool })
        assertEquals(ToolCatalog.specs.map { it.name }.toSet(), snapshot.entries.filter { it.tool }.map { it.id }.toSet())
        assertEquals(snapshot.entries.size, snapshot.entries.map { it.id }.distinct().size)
    }
    @Test fun offMakesEveryActionNonExecutableEvenWhenPermissionsRemainGranted() {
        val f = base.copy(running = false, microphone = true, armed = true, wakeActive = true,
            cloudConfigured = true, nano = NanoState.READY, maleVoice = true, accessibility = true)
        val s = CapabilityPolicy.evaluate(f)
        assertTrue(s.entries.none { it.canAttempt }); assertEquals(0, s.availableTools)
        assertTrue(s.entries.all { it.recovery == Recovery.RESUME })
    }
    @Test fun disablingToolsDoesNotDisableIndependentVoiceOrAConfiguredModel() {
        val f = base.copy(tools = false, maleVoice = true, cloudConfigured = true)
        assertTrue(CapabilityPolicy.evaluate(f).entries.filter { it.tool }.none { it.canAttempt })
        assertTrue(get("speech",f).canAttempt); assertTrue(get("cloud_model",f).canAttempt)
    }
    @Test fun localVolumeAndNotesNeedNeitherCloudKeysNorInternet() {
        val f = base.copy(network = NetworkState.OFFLINE, cloudConfigured = false)
        assertEquals(Availability.READY,get("set_media_volume",f).availability)
        assertEquals(Availability.READY,get("save_memory",f).availability)
        assertFalse(get("cloud_model",f).canAttempt)
    }
    @Test fun webToggleRevocationLeavesNativePhoneToolsAvailable() {
        val f = base.copy(web = false)
        for (id in listOf("search_web","read_web_page","open_url")) assertFalse(get(id,f).canAttempt)
        assertTrue(get("device_info",f).canAttempt)
    }
    @Test fun unvalidatedNetworksRemainAttemptableWithoutAdvertisingVerifiedInternet() {
        for (network in listOf(NetworkState.UNVALIDATED,NetworkState.OFFLINE,NetworkState.UNKNOWN)) {
            val e = get("search_web",base.copy(network = network))
            assertTrue(e.canAttempt); assertNotEquals(Availability.READY,e.availability)
        }
    }
    @Test fun connectedAccessibilityAloneDoesNotGrantScreenReading() {
        assertFalse(get("read_screen",base.copy(accessibility = true)).canAttempt)
        assertFalse(get("click_screen",base.copy(screen = true,accessibility = true)).canAttempt)
    }
    @Test fun revokedAccessibilityIsImmediatelyASetupIssue() {
        val f = base.copy(screen = true,control = true,accessibility = true)
        assertTrue(get("read_screen",f).canAttempt)
        val revoked = get("read_screen",f.copy(accessibility = false))
        assertFalse(revoked.canAttempt);assertEquals(Recovery.ACCESSIBILITY,revoked.recovery)
    }
    @Test fun lockedScreenActionsCannotBeAttempted() {
        val f = base.copy(screen = true,control = true,accessibility = true,locked = true)
        for (id in listOf("read_screen","click_screen","type_text","scroll_screen","navigate")) {
            assertFalse(id,get(id,f).canAttempt);assertEquals(Recovery.UNLOCK,get(id,f).recovery)
        }
    }
    @Test fun screenReadinessNeverClaimsTheCurrentTargetIsAlreadyApprovedOrFresh() {
        val e = get("read_screen",base.copy(screen = true,control = true,accessibility = true))
        assertEquals(Availability.CONDITIONAL,e.availability)
        assertTrue(e.reason.contains("fresh"));assertTrue(e.reason.contains("Protected"))
    }
    @Test fun cloudConfigurationIsNotProofOfProviderHealth() {
        val e = get("cloud_model",base.copy(cloudConfigured = true,network = NetworkState.VALIDATED))
        assertEquals(Availability.CONDITIONAL,e.availability)
        assertTrue(e.reason.contains("not verified"))
    }
    @Test fun recentProviderSuccessExpiresRatherThanClaimingPermanentAvailability() {
        val f = base.copy(cloudConfigured = true,network = NetworkState.VALIDATED,
            cloudHealth = ConnectorHealth(HealthState.SUCCEEDED,base.now - 1))
        assertEquals(Availability.READY,get("cloud_model",f).availability)
        assertEquals(Availability.CONDITIONAL,get("cloud_model",f.copy(now = base.now+300_000)).availability)
    }
    @Test fun providerAuthAndRateLimitsHaveHonestRetryableRecovery() {
        for (health in listOf(HealthState.AUTH,HealthState.RATE_LIMIT,HealthState.FAILED)) {
            val e = get("cloud_model",base.copy(cloudConfigured = true,cloudHealth = ConnectorHealth(health,base.now)))
            assertEquals(Availability.CONDITIONAL,e.availability); assertTrue(e.canAttempt)
        }
    }
    @Test fun nanoNeverSilentlyBecomesCloudWhenBackgrounded() {
        assertEquals(Availability.UNKNOWN,get("nano_model").availability)
        assertFalse(get("nano_model",base.copy(nano = NanoState.READY,foreground = false)).canAttempt)
        assertEquals(Recovery.OPEN_APP,get("nano_model",base.copy(nano = NanoState.READY,foreground = false)).recovery)
    }
    @Test fun unknownVoicesAreNotSpeechReady() {
        assertFalse(get("speech").canAttempt)
        assertEquals(Availability.READY,get("speech",base.copy(maleVoice = true)).availability)
        assertEquals(Availability.CONDITIONAL,get("speech",base.copy(maleVoice = true,networkVoice = true,network = NetworkState.OFFLINE)).availability)
    }
    @Test fun wakeCannotBeReadyFromAModelFileOrMicrophoneFlagAlone() {
        val f = base.copy(wake = true,wakeModel = true,microphone = true,armed = true)
        assertFalse(get("wake_word",f).canAttempt)
        assertEquals(Availability.READY,get("wake_word",f.copy(wakeActive = true)).availability)
        assertFalse(get("wake_word",f.copy(wakeActive = true,microphone = false)).canAttempt)
    }
    @Test fun quietHoursSnoozeAndScreenPrivacyTakePriorityOverEnabledCheckIns() {
        val f = base.copy(checkIns = true,maleVoice = true)
        for (changed in listOf(f.copy(quiet=true),f.copy(snoozed=true),f.copy(activeHours=false),f.copy(locked=true)))
            assertEquals(Availability.PAUSED,get("check_ins",changed).availability)
        assertTrue(get("check_ins",f).canAttempt)
    }
    @Test fun overlayPermissionIsNotProofOfAnAttachedFloatingWindow() {
        val f = base.copy(overlayPermission = true)
        assertEquals(Availability.CONDITIONAL,get("floating_orb",f).availability)
        assertEquals(Availability.READY,get("floating_orb",f.copy(overlayVisible=true)).availability)
        assertEquals(Availability.PAUSED,get("floating_orb",f.copy(overlayVisible=true,locked=true)).availability)
    }
    @Test fun disabledMemoryDoesNotEraseOrDisableConversationReadiness() {
        assertFalse(get("save_memory",base.copy(memory=false)).canAttempt)
        assertTrue(get("device_info",base.copy(memory=false)).canAttempt)
    }
    @Test fun fixedVolumeAndUnhealthyMemoryHaveExplicitFailures() {
        assertEquals(Availability.UNAVAILABLE,get("set_media_volume",base.copy(fixedVolume=true)).availability)
        assertFalse(get("recall_memory",base.copy(memoryHealthy=false)).canAttempt)
    }
    @Test fun sessionStateHasTheSamePrecedenceForEverySurface() {
        fun p(running:Boolean=true,listening:Boolean=false,speaking:Boolean=false,busy:Boolean=false,review:Boolean=false) =
            SessionPolicy.reduce(running,listening,speaking,busy,review,"Task status","New words",2)
        assertEquals(SessionPhase.OFF,p(running=false,listening=true,speaking=true,busy=true).phase)
        assertEquals(SessionPhase.LISTENING,p(listening=true,speaking=true,busy=true).phase)
        assertEquals("New words",p(listening=true).detail)
        assertEquals(SessionPhase.SPEAKING,p(speaking=true,busy=true).phase)
        assertEquals(SessionPhase.REVIEW,p(review=true,busy=true).phase)
        assertEquals(SessionPhase.WORKING,p(busy=true).phase)
        assertEquals(SessionPhase.IDLE,p().phase)
    }
    @Test fun nativeReadinessAnswersComeFromTheSameSnapshotAndCannotAuthorizeTasks() {
        val s = CapabilityPolicy.evaluate(base.copy(microphone=false,web=false))
        val reply = CapabilityQuestions.answer("Check voice readiness?",s)!!
        assertTrue(reply.contains(s.entry("voice_input")!!.reason))
        assertTrue(reply.contains(s.entry("speech")!!.reason))
        assertTrue(CapabilityQuestions.answer("What tools do you have?",s)!!.contains("16"))
        assertNull(CapabilityQuestions.answer("Open an app and say what can you do",s))
    }
}
