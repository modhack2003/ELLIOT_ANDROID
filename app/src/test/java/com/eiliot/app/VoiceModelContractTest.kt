package com.eiliot.app

import android.app.Application
import android.content.Context
import com.eiliot.app.voice.*
import com.eiliot.app.voice.remote.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[36])
class VoiceModelContractTest {
    @Test fun packagedModelFamiliesAgreeWithAndroidSpecification() {
        val app=RuntimeEnvironment.getApplication();val root=ModelAssets.install(app);val spec=VoiceModelContract.load(app)
        spec.validate(root)
        assertEquals("zipformer2",spec.spec("kws").family);assertEquals("zipformer",spec.spec("asr").family)
        assertFalse(OnnxMetadata.read(File(root,"kws/encoder.onnx")).containsKey("attention_dims"))
        assertTrue(OnnxMetadata.read(File(root,"asr/encoder.onnx")).containsKey("attention_dims"))
    }
    @Test fun oldCrashingConfigurationIsRejectedBeforeJni() {
        val app=RuntimeEnvironment.getApplication();val root=ModelAssets.install(app)
        val old=VoiceModelContract.load(app).source.replace("\"zipformer2\"","\"zipformer\"")
        assertThrows(IllegalArgumentException::class.java) {VoiceModelContract(old).validate(root)}
    }
    @Test fun interruptedInitializationRequiresExplicitRetryAndChangesAreVersionScoped() {
        val app=RuntimeEnvironment.getApplication();val prefs=app.getSharedPreferences("fence_test",Context.MODE_PRIVATE)
        val first=SpeechFailureFence(prefs,"version-a");first.begin()
        val recreated=SpeechFailureFence(prefs,"version-a");assertTrue(recreated.blocked())
        assertThrows(IllegalStateException::class.java) {recreated.begin()}
        assertFalse(SpeechFailureFence(prefs,"version-b").blocked())
        recreated.retry();assertFalse(recreated.blocked());recreated.begin();recreated.finish();assertFalse(recreated.blocked())
    }
    @Test fun workerFailureSurvivesNormalShutdownUntilRetry() {
        val app=RuntimeEnvironment.getApplication();val f=SpeechFailureFence(app.getSharedPreferences("fence_failure",0),"key")
        f.begin();f.fail();f.finish();assertTrue(f.blocked());f.retry();assertFalse(f.blocked())
    }
    @Test fun malformedOrTruncatedModelIsRejectedWithoutNativeCode() {
        val app=RuntimeEnvironment.getApplication();val file=File(app.cacheDir,"truncated.onnx")
        file.writeBytes(byteArrayOf(0x72,0x7f,1));assertThrows(IllegalArgumentException::class.java) {OnnxMetadata.read(file)}
    }
    @Test fun manifestAssignsNativeServiceToPrivateProcess() {
        val app=RuntimeEnvironment.getApplication();val service=app.packageManager.getServiceInfo(android.content.ComponentName(app,SpeechEngineService::class.java),0)
        assertFalse(service.exported);assertTrue(service.processName.endsWith(":speech"))
    }
    @Test fun pcmWireRoundTripPreservesSamplesAndControlResult() {
        val target=object:android.os.Binder() {
            override fun onTransact(code:Int,data:android.os.Parcel,reply:android.os.Parcel?,flags:Int):Boolean {
                data.enforceInterface(SpeechWire.TOKEN);assertEquals("pcm",data.readString())
                val values=data.readBundle(javaClass.classLoader)!!.getFloatArray("pcm")!!
                assertArrayEquals(floatArrayOf(-.5f,0f,.5f),values,0f);data.readStrongBinder()
                reply!!.writeNoException();reply.writeBundle(android.os.Bundle().apply {putInt("continue",1)});return true
            }
        }
        assertEquals(1,SpeechWire.call(target,"pcm",android.os.Bundle().apply {putFloatArray("pcm",floatArrayOf(-.5f,0f,.5f))}).getInt("continue"))
    }
    @Test fun workerErrorsReturnAsRecoverableExceptions() {
        val target=object:android.os.Binder() {
            override fun onTransact(code:Int,data:android.os.Parcel,reply:android.os.Parcel?,flags:Int):Boolean {
                reply!!.writeNoException();reply.writeBundle(android.os.Bundle().apply {putString("error","Model mismatch")});return true
            }
        }
        assertEquals("Model mismatch",assertThrows(IllegalStateException::class.java) {SpeechWire.call(target,"kws")}.message)
    }
    @Test fun oldWorkerHandlesCannotReleaseObjectsInReplacementSession() {
        val stale=RemoteStream(SpeechHandle(1,Long.MIN_VALUE))
        assertEquals("Speech session expired",assertThrows(IllegalStateException::class.java) {stale.release()}.message)
        assertThrows(IllegalStateException::class.java) {stale.acceptWaveform(FloatArray(512),16000)}
    }
    @Test fun repairReplacesSameLengthCorruptionFromBundledFiles() {
        val app=RuntimeEnvironment.getApplication();com.eiliot.app.core.SentinelRuntime.init(app)
        val root=ModelAssets.install(app);val file=File(root,"kws/encoder.onnx")
        java.io.RandomAccessFile(file,"rw").use {f->val b=f.readByte();f.seek(0);f.writeByte(b.toInt() xor 1)}
        assertThrows(IllegalArgumentException::class.java) {VoiceModelContract.verify(app,root)}
        ModelAssets.retry(app)
        kotlinx.coroutines.runBlocking {kotlinx.coroutines.withTimeout(15000) {ModelAssets.await(app)}}
        VoiceModelContract.verify(app,root)
    }
}
