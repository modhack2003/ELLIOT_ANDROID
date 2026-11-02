package com.eiliot.app

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.media.AudioManager
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.eiliot.app.core.*
import com.eiliot.app.voice.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAudioRecord
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale
import java.time.Duration

/** Real VoiceIo methods against simulated Android services; no physical audio is exercised. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
abstract class VoiceAndroidTestBase {
    @Test fun interruptedPlaybackCannotFinishANewTurnAndLocalCommandsNeedNoModel() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        SentinelRuntime.init(app)
        withTimeout(5000) { SentinelRuntime.scope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() } }
        val store = SentinelRuntime.store
        store.masterEnabled = true; store.phoneToolsEnabled = true; store.reviewActions = false
        store.memoryEnabled = false; store.wakeEnabled = false; store.followUpEnabled = false
        store.roomMode = true; store.proactiveEnabled = true; SentinelRuntime.microphoneReady = true
        val manager = app.getSystemService(NotificationManager::class.java)
        Shadows.shadowOf(manager).setNotificationPolicyAccessGranted(true)
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        ShadowTextToSpeech.addLanguageAvailability(Locale.US)
        ShadowTextToSpeech.addVoice(Voice("test-male", Locale.US, Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, false, setOf("gender=male")))
        VoiceIo.init(app)
        val speech = checkNotNull(ShadowTextToSpeech.getLastTextToSpeechInstance())
        val shadowSpeech = Shadows.shadowOf(speech)
        shadowSpeech.onInitListener.onInit(TextToSpeech.SUCCESS)
        val main = Shadows.shadowOf(Looper.getMainLooper()); main.idle()
        store.ttsVoice = "test-male"; VoiceIo.applyVoice("test-male",1f,1f)
        try {
            assertTrue(VoiceIo.canSpeak())
            var completed = false
            VoiceIo.speak("First answer in progress", onDone = { completed = true })
            main.runOneTask()
            assertTrue(VoiceIo.isSpeaking.value)
            EiliotBrain.interruptForWake()
            val listening = VoiceIo.beginLocalListening("wake")
            main.idle()
            assertTrue(shadowSpeech.isStopped)
            assertFalse(completed)
            assertTrue(VoiceIo.isListening.value)
            assertEquals(VoicePhase.LISTENING,VoiceSession.state.value.phase)
            assertTrue(VoiceSession.gate.accepts(listening,SentinelRuntime.epoch))
            VoiceIo.speak("A manual playback replaces the listening window")
            main.idle()
            assertFalse(VoiceIo.isListening.value)
            assertFalse(VoiceSession.gate.accepts(listening,SentinelRuntime.epoch))

            assertTrue(ProactiveEngine.checkInNow())
            main.idle()
            assertTrue(shadowSpeech.lastSpokenText.isNotBlank())
            assertTrue(store.lastCheckIn > 0)
            assertFalse(ProactiveEngine.checkInNow(false))
            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            assertFalse(ProactiveEngine.checkInNow())
            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)

            assertTrue(EiliotBrain.send("Set volume to forty percent"))
            withTimeout(5000) {
                while (EiliotBrain.state.value.busy) { main.idleFor(Duration.ofMillis(100)); delay(10) }
            }
            main.idle()
            val audio = app.getSystemService(AudioManager::class.java)
            assertEquals((40 * audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 100.0).toInt(),audio.getStreamVolume(AudioManager.STREAM_MUSIC))
            assertTrue(EiliotBrain.state.value.reply.contains("Media volume"))
            assertNull(com.eiliot.app.tools.ActionApprovals.pending.value)

            store.notificationApps = "org.sample.chat"; store.notificationContents = true
            store.notificationAwarenessEnabled = true
            store.lastProactiveAt = 0; store.checkInStart = 0; store.checkInEnd = 0
            ProactiveEngine.onNotification("org.sample.chat","Lunch","Meet at noon")
            main.idle()
            assertTrue(shadowSpeech.lastSpokenText.contains("Meet at noon"))
            assertTrue(EiliotBrain.messages.value.none { it.text.contains("Meet at noon") })

            var resetChecks = 0
            ShadowAudioRecord.setSource(object : ShadowAudioRecord.AudioRecordSource {
                override fun readInByteArray(buffer: ByteArray, offset: Int, size: Int, blocking: Boolean): Int {
                    buffer.fill(42.toByte(),offset,offset+size); return size
                }
            })
            VoiceAudioInput(app) { resetChecks++ }.use { input ->
                val bytes = ByteArray(8)
                assertEquals(8,input.read(bytes,0,bytes.size)); assertTrue(bytes.all { it == 42.toByte() })
                assertEquals(1,resetChecks)
                input.stopCapture(); assertEquals(-1,input.read(bytes,0,bytes.size))
            }
        } finally {
            ShadowAudioRecord.clearSource(); SentinelRuntime.stop(); main.idle(); VoiceIo.shutdown()
        }
    }
}

@Config(sdk = [28]) class VoiceAndroidLegacyTest : VoiceAndroidTestBase()
@Config(sdk = [36]) class VoiceAndroidTest : VoiceAndroidTestBase()
