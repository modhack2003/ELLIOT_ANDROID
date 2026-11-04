package com.eiliot.app

import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eiliot.app.voice.*
import com.eiliot.app.voice.remote.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Actual Android JNI/process checks; these do not certify microphone/acoustic quality. */
@RunWith(AndroidJUnit4::class)
class NativeSpeechDeviceTest {
    @Test fun softwareDenoiserPreservesSamplesAcrossChunksAndResets()=runBlocking {
        withContext(Dispatchers.IO) {
            NativeSpeechClient.connect(app)
            val denoiser=RemoteDenoiser()
            try {
                for(n in listOf(64000,12345)) {
                    val audio=FloatArray(n){(.03*kotlin.math.sin(it*.021)).toFloat()}
                    val cleaned=denoiser.clean(audio)
                    assertEquals(n,cleaned.size);assertTrue(cleaned.all(Float::isFinite))
                    audio.fill(0f);cleaned.fill(0f)
                }
                denoiser.reset()
                val streaming=FloatArray(64000){(.03*kotlin.math.sin(it*.021)).toFloat()}
                val frames=PcmFrameBuffer();var outputSamples=0;var frameCount=0
                val started=android.os.SystemClock.elapsedRealtime()
                val consume:(FloatArray)->Unit={assertEquals(512,it.size);assertTrue(it.all(Float::isFinite));frameCount++}
                for(at in streaming.indices step 512) {
                    val output=denoiser.filterChunk(streaming.copyOfRange(at,at+512));outputSamples+=output.size
                    frames.push(output,consume);output.fill(0f)
                }
                val tail=denoiser.flush();outputSamples+=tail.size;frames.push(tail,consume)
                val elapsed=android.os.SystemClock.elapsedRealtime()-started
                assertEquals(streaming.size,outputSamples);assertEquals(125,frameCount)
                InstrumentationRegistry.getInstrumentation().sendStatus(0,android.os.Bundle().apply {
                    putLong("denoiser_stream_4_seconds_ms",elapsed)
                })
                tail.fill(0f);streaming.fill(0f);frames.clear()
                assertTrue(runCatching {denoiser.clean(floatArrayOf(Float.NaN))}.isFailure)
                assertTrue(runCatching {denoiser.filterChunk(FloatArray(4097))}.isFailure)
            }finally{denoiser.release()}
        }
    }
    @Test fun calibrationPlaybackCanFinishAndCancel()=runBlocking {
        WakeCalibration.play(FloatArray(1600))
        val playback=launch {WakeCalibration.play(FloatArray(32000))}
        delay(100);playback.cancelAndJoin();assertTrue(playback.isCancelled)
        WakeCalibration.play(FloatArray(1600))
    }
    @Test fun calibrationReplayAndProfileRoundTrip()=runBlocking {
        val previous=VoiceCalibrationStore.load(app)
        try {
            withContext(Dispatchers.IO) {
                NativeSpeechClient.connect(app)
                val tts=RemoteTts();val chunks=mutableListOf<FloatArray>()
                tts.generateWithCallback("Hey Elliot. What is my battery level?",0,1f){chunks.add(it.copyOf());1}
                val source=FloatArray(chunks.sumOf{it.size});var at=0
                chunks.forEach{it.copyInto(source,at);at+=it.size}
                val audio=FloatArray(source.size*16000/tts.sampleRate()){i->source[minOf((i.toLong()*tts.sampleRate()/16000).toInt(),source.lastIndex)]}
                assertTrue(WakeCalibration.detect(WakeSetting(false),audio))
                assertTrue(WakeCalibration.detect(WakeSetting(true,2.5f,.1f),audio))
                assertTrue(WakeCalibration.transcribe(audio).contains("battery",ignoreCase=true))
                val setting=WakeSetting(true,2.5f,.1f,true)
                assertTrue(WakeCalibration.detect(setting,audio))
                val phoneme=setting.copy(phonemeModel=true)
                assertTrue(WakeCalibration.detect(phoneme,audio))
                assertFalse(WakeCalibration.detect(phoneme,FloatArray(16000)))
                VoiceCalibrationStore.save(app,phoneme);assertEquals(phoneme,VoiceCalibrationStore.load(app))
                VoiceCalibrationStore.save(app,setting);assertEquals(setting,VoiceCalibrationStore.load(app))
                VoiceCalibrationStore.clear(app);assertNull(VoiceCalibrationStore.load(app))
                assertTrue(runCatching{RemoteKeywordSpotter(4f,.1f)}.isFailure)
            }
        }finally{if(previous==null)VoiceCalibrationStore.clear(app)else VoiceCalibrationStore.save(app,previous)}
    }
    @Test fun wakeDetectorRequiresHeyInFullPhraseMode()=runBlocking {
        withContext(Dispatchers.IO) {
            NativeSpeechClient.connect(app)
            val tts=RemoteTts()
            for((phrase,expectWake) in listOf("Hey Elliot" to true,"Elliot" to false,"What is my battery level?" to false,"Hello, how are you?" to false)) {
            val chunks=mutableListOf<FloatArray>()
            tts.generateWithCallback(phrase,0,1f) { chunks.add(it.copyOf());1 }
            val source=FloatArray(chunks.sumOf { it.size });var at=0
            chunks.forEach { it.copyInto(source,at);at+=it.size }
            val rate=tts.sampleRate()
            val resampled=FloatArray(source.size*16000/rate+16000) { i ->
                val pos=i.toDouble()*rate/16000;val index=pos.toInt()
                if(index>=source.size-1)0f else (source[index]*(1-(pos-index))+source[index+1]*(pos-index)).toFloat()
            }
            val detector=RemoteWakeDetector(WakeSetting(false,requireHey=true))
            val hits=mutableListOf<String>()
            try {
                for(start in resampled.indices step 512) {
                    detector.accept(resampled.copyOfRange(start,minOf(start+512,resampled.size)))?.let(hits::add)
                }
                assertEquals("Wake detection for $phrase: $hits",expectWake,hits.isNotEmpty())
                if(expectWake)assertTrue(WakeCalibration.observePhones(resampled).isNotEmpty())
            }finally {detector.close()}
            }
        }
    }
    @Test fun learnedWakeGrammarAcceptsValidPhonesAndRejectsSilence()=runBlocking {
        withContext(Dispatchers.IO) {
            NativeSpeechClient.connect(app)
            val setting=WakeSetting(false,phonemeModel=true,requireHey=true,
                learnedPhones=listOf("HH","EY1","y","ī","l","iè"))
            assertFalse(WakeCalibration.detect(setting,FloatArray(16000)))
        }
    }
    @Test fun packagedCallbackHasNativeSignature() {
        val callback=NativeTtsCallback { 0 }
        val method=callback.javaClass.getMethod("invoke",FloatArray::class.java)
        assertEquals(java.lang.Integer::class.java,method.returnType)
        assertEquals(0,method.invoke(callback,floatArrayOf(.1f)))
    }
    private val app get()=ApplicationProvider.getApplicationContext<Context>()
    @Before fun setup() {InstrumentationRegistry.getInstrumentation().runOnMainSync {com.eiliot.app.core.SentinelRuntime.stop();NativeSpeechClient.shutdown();SpeechFailureFence(app.getSharedPreferences("native_speech_health",0),VoiceModelContract.fingerprint(app)).retry()}}
    @After fun stop() {InstrumentationRegistry.getInstrumentation().runOnMainSync {NativeSpeechClient.shutdown()}}
    @Test fun actualPackagedEnginesInitializeAndProcessAudioInOtherProcess()=runBlocking {
        withContext(Dispatchers.IO) {
            NativeSpeechClient.connect(app)
            val pid=NativeSpeechClient.call("hello").getInt("pid");assertTrue(pid>0);assertNotEquals(Process.myPid(),pid)
            val kws=RemoteKeywordSpotter();val stream=kws.createStream()
            val asr=RemoteRecognizer();val dictation=asr.createStream("ELLIOT\nVOLUME")
            val vad=RemoteVad()
            repeat(32) {
                val silence=FloatArray(512);assertTrue(vad.compute(silence).isFinite())
                stream.acceptWaveform(silence,16000);while(kws.isReady(stream))kws.decode(stream)
                dictation.acceptWaveform(silence,16000);while(asr.isReady(dictation))asr.decode(dictation)
            }
            val tts=RemoteTts();assertEquals(22050,tts.sampleRate());var count=0
            tts.generateWithCallback("I'm Elliot. I'm ready.",0,1f) {samples->assertTrue(samples.all(Float::isFinite));count+=samples.size;1}
            assertTrue(count>1000)
            stream.release();dictation.release();kws.release();asr.release();vad.release()
        }
    }
    @Test fun workerDeathBlocksAutomaticRetryAndLeavesMainProcessAlive()=runBlocking {
        withContext(Dispatchers.IO) {
            NativeSpeechClient.connect(app);val pid=NativeSpeechClient.call("hello").getInt("pid");assertNotEquals(Process.myPid(),pid)
            val oldSession=NativeSpeechClient.session
            Process.killProcess(pid)
            withTimeout(10000) {while(NativeSpeechClient.session==oldSession || !NativeSpeechClient.blocked(app))delay(50)}
            assertTrue(Process.myPid()>0)
            val failure=runCatching {NativeSpeechClient.connect(app)}.exceptionOrNull();assertNotNull(failure)
            SpeechFailureFence(app.getSharedPreferences("native_speech_health",0),VoiceModelContract.fingerprint(app)).retry()
            NativeSpeechClient.connect(app);assertNotEquals(pid,NativeSpeechClient.call("hello").getInt("pid"))
        }
    }
}
