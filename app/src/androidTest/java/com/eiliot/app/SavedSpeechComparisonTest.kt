package com.eiliot.app

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eiliot.app.core.SentinelRuntime
import com.eiliot.app.voice.MultilingualModelPack
import com.eiliot.app.voice.VoiceModelContract
import com.eiliot.app.voice.remote.*
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Explicit opt-in comparison probe; passing means execution, not transcription accuracy. */
@RunWith(AndroidJUnit4::class)
class SavedSpeechComparisonTest {
    @Test fun compareConsentedRecording():Unit=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("consentedSpeechComparison")=="true")
        val app=ApplicationProvider.getApplicationContext<Context>()
        val file=File(checkNotNull(app.getExternalFilesDir(null)),"voice-evaluation/consented-sample.wav")
        check(file.length() in 46..256044)
        val bytes=file.readBytes();val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        check(String(bytes,0,4)=="RIFF" && String(bytes,8,4)=="WAVE" && String(bytes,36,4)=="data")
        check(b.getShort(20).toInt()==1 && b.getShort(22).toInt()==1 && b.getInt(24)==16000 && b.getShort(34).toInt()==16)
        check(b.getInt(40)==bytes.size-44)
        val audio=FloatArray((bytes.size-44)/2){b.getShort(44+it*2)/32768f}
        val silence=InstrumentationRegistry.getArguments().getString("trailingSilenceSeconds")?.toInt()?:0
        require(silence in 0..20 && audio.size+silence*16000<=28*16000)
        val decodeAudio=if(silence==0)audio else audio.copyOf(audio.size+silence*16000)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            SentinelRuntime.stop();NativeSpeechClient.shutdown()
            SpeechFailureFence(app.getSharedPreferences("native_speech_health",0),VoiceModelContract.fingerprint(app)).retry()
        }
        try {
            withContext(Dispatchers.IO) {
                NativeSpeechClient.connect(app)
                val options=InstrumentationRegistry.getArguments()
                val model=MultilingualModelPack.spec(options.getString("speechModel")?:MultilingualModelPack.TURBO.id)
                if(options.getString("prepareModel")=="true")MultilingualModelPack.prepare(app,model){progress->
                    InstrumentationRegistry.getInstrumentation().sendStatus(0,Bundle().apply {putString("preparation",progress)})
                }
                check(MultilingualModelPack.available(app,model))
                for(language in listOf("","bn")) {
                    val started=SystemClock.elapsedRealtime()
                    val r=RemoteWhisper(language,model.id)
                    val result=try {r.transcribe(decodeAudio)}finally{r.release()}
                    InstrumentationRegistry.getInstrumentation().sendStatus(0,Bundle().apply {
                        putString("model",model.id);putString("language",language.ifEmpty {"auto"})
                        putInt("trailing_silence_seconds",silence)
                        putString("text",result);putLong("total_ms",SystemClock.elapsedRealtime()-started)
                        putString("audio_sha256",MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)})
                    })
                }
            }
        }finally {audio.fill(0f);decodeAudio.fill(0f);NativeSpeechClient.shutdown()}
    }
}
