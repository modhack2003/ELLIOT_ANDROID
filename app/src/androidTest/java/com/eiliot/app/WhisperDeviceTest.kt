package com.eiliot.app

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eiliot.app.core.SentinelRuntime
import com.eiliot.app.voice.*
import com.eiliot.app.voice.remote.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Model compatibility/latency smoke check; not an accent or code-switch benchmark. */
@RunWith(AndroidJUnit4::class)
class WhisperDeviceTest {
    private val app get()=ApplicationProvider.getApplicationContext<Context>()
    @Before fun stopCapture() {InstrumentationRegistry.getInstrumentation().runOnMainSync {SentinelRuntime.stop();NativeSpeechClient.shutdown();SpeechFailureFence(app.getSharedPreferences("native_speech_health",0),VoiceModelContract.fingerprint(app)).retry()}}
    @After fun close() {InstrumentationRegistry.getInstrumentation().runOnMainSync {NativeSpeechClient.shutdown()}}
    @Test fun multilingualSmallLoadsAndTranscribesOnVivo():Unit=runBlocking {
        checkModel(MultilingualModelPack.SMALL)
    }
    @Test fun multilingualTurboLoadsAndTranscribesOnVivo():Unit=runBlocking {
        checkModel(MultilingualModelPack.TURBO)
    }
    private suspend fun checkModel(model:MultilingualModelPack.Spec) {
        withContext(Dispatchers.IO) {
            MultilingualModelPack.prepare(app,model){progress->
                InstrumentationRegistry.getInstrumentation().sendStatus(0,Bundle().apply {putString("model_preparation",progress)})
            }
            NativeSpeechClient.connect(app)
            val tts=RemoteTts();val chunks=mutableListOf<FloatArray>()
            tts.generateWithCallback("What is my battery level?",0,1f){chunks.add(it.copyOf());1}
            val source=FloatArray(chunks.sumOf{it.size});var at=0
            chunks.forEach{it.copyInto(source,at);at+=it.size}
            val rate=tts.sampleRate()
            val audio=FloatArray(source.size*16000/rate){i->source[minOf((i.toLong()*rate/16000).toInt(),source.lastIndex)]}
            val started=SystemClock.elapsedRealtime();val recognizer=RemoteWhisper("en",model.id)
            try {
                val loadMs=SystemClock.elapsedRealtime()-started
                val decodeStart=SystemClock.elapsedRealtime();val text=recognizer.transcribe(audio)
                val decodeMs=SystemClock.elapsedRealtime()-decodeStart
                InstrumentationRegistry.getInstrumentation().sendStatus(0,Bundle().apply {
                    putString("whisper_model",model.id);putString("load_ms",loadMs.toString());putString("decode_ms",decodeMs.toString())
                    putString("audio_seconds",(audio.size/16000.0).toString());putString("synthetic_text",text)
                })
                assertTrue("Whisper output: $text",text.contains("battery",ignoreCase=true))
                assertThrows(IllegalArgumentException::class.java){recognizer.transcribe(FloatArray(29*16000))}
            }finally{recognizer.release()}
        }
    }
}
