package com.eiliot.app

import android.app.Application
import com.eiliot.app.voice.MultilingualModelPack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class, sdk=[28])
class MultilingualModelPackTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private fun fixture(id:String):MultilingualModelPack.Spec {
        val source=File(app.getExternalFilesDir(null),"speech-packs/$id").apply {mkdirs()}
        val files=listOf("encoder","decoder","tokens").map {name->
            val bytes="fixture-$name".toByteArray()
            File(source,name).writeBytes(bytes)
            MultilingualModelPack.Entry(name,bytes.size.toLong(),MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)})
        }
        return MultilingualModelPack.Spec(id,"Fixture","unused","revision1",files)
    }
    @Test fun verifiesInstalledBytesAndRepairsSameLengthCorruption():Unit=runBlocking {
        val model=fixture("repair-test")
        val root=MultilingualModelPack.prepare(app,model)
        assertTrue(MultilingualModelPack.available(app,model))
        File(root,model.encoder).writeBytes(ByteArray(model.files[0].bytes.toInt()))
        assertThrows(IllegalStateException::class.java){MultilingualModelPack.verify(app,model)}
        MultilingualModelPack.prepare(app,model)
        assertEquals(root,MultilingualModelPack.verify(app,model))
    }
    @Test fun cancelledPreparationKeepsVerifiedFilesForRetryAndDoesNotActivate():Unit=runBlocking {
        val model=fixture("cancel-test")
        try {
            MultilingualModelPack.prepare(app,model){if(it.contains("2 of 3"))throw CancellationException("test cancellation")}
            fail("Expected cancellation")
        }catch(expected:CancellationException) { }
        assertFalse(MultilingualModelPack.available(app,model))
        // Removing only this fixture's source proves the verified staged encoder is reused.
        assertTrue(File(app.getExternalFilesDir(null),"speech-packs/${model.id}/${model.encoder}").delete())
        MultilingualModelPack.prepare(app,model)
        assertTrue(MultilingualModelPack.available(app,model))
    }
    @Test fun preparingAnotherPackPreservesInstalledPackAndRejectsUnknownIds():Unit=runBlocking {
        val first=fixture("first-test");val second=fixture("second-test")
        val root=MultilingualModelPack.prepare(app,first)
        MultilingualModelPack.prepare(app,second)
        assertEquals(root,MultilingualModelPack.verify(app,first))
        assertThrows(IllegalArgumentException::class.java){MultilingualModelPack.spec("../unknown")}
    }
    @Test fun reusesIdenticalVerifiedFileWithoutDownloadingItAgain():Unit=runBlocking {
        val model=fixture("reuse-test")
        val source=File(app.getExternalFilesDir(null),"speech-packs/${model.id}/${model.encoder}")
        val installed=MultilingualModelPack.directory(app,MultilingualModelPack.SMALL).apply{mkdirs()}
        source.copyTo(File(installed,model.encoder))
        assertTrue(source.delete())
        MultilingualModelPack.prepare(app,model)
        assertTrue(MultilingualModelPack.available(app,model))
        assertTrue(File(installed,model.encoder).exists())
    }
}
