package com.eiliot.app

import android.app.Application
import com.eiliot.app.core.*
import com.eiliot.app.data.*
import com.eiliot.app.learning.LearningScheduler
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[36])
@LooperMode(LooperMode.Mode.PAUSED)
class VaultPrivacyTest {
    @Test fun memoryOffRejectsEveryWriterIncludingManualNotesAndKnowledgeWithoutErasingOldRows() {
        val app=RuntimeEnvironment.getApplication();val settings=SecureStore(app);settings.memoryEnabled=true
        val memory=MemoryStore(app)
        memory.writableDatabase.execSQL("DELETE FROM messages");memory.writableDatabase.execSQL("DELETE FROM notes")
        memory.writableDatabase.execSQL("INSERT INTO notes(body,url,created,kind,fingerprint) VALUES('old ciphertext','',1,'note','old')")
        settings.memoryEnabled=false
        assertFalse(memory.addNote("manual note"));assertFalse(memory.addNote("public reference","https://example.org","knowledge"))
        assertEquals(-1L,memory.addMessage("assistant","not persisted"))
        memory.readableDatabase.rawQuery("SELECT count(*) FROM notes",null).use {it.moveToFirst();assertEquals(1,it.getInt(0))}
        memory.readableDatabase.rawQuery("SELECT count(*) FROM messages",null).use {it.moveToFirst();assertEquals(0,it.getInt(0))}
        memory.close()
    }
    @Test fun learningCannotWriteOrStartAReferenceFetchWhenMemoryIsOff() {
        val app=RuntimeEnvironment.getApplication();SentinelRuntime.init(app)
        SentinelRuntime.store.masterEnabled=true;SentinelRuntime.store.learningEnabled=true;SentinelRuntime.store.memoryEnabled=false
        var done=false;LearningScheduler.runNow {done=true}
        assertTrue(done);assertTrue(LearningScheduler.status.value.contains("Memory"))
        SentinelRuntime.stop()
    }
    @Test fun setupCompletionAndNoticeChoicePersistIndependentlyOfCheckIns() {
        val app=RuntimeEnvironment.getApplication();val s=SecureStore(app)
        assertFalse(s.isInitialSetupComplete);s.isInitialSetupComplete=true;s.notificationAwarenessEnabled=true;s.proactiveEnabled=false
        val restored=SecureStore(app)
        assertTrue(restored.isInitialSetupComplete);assertTrue(restored.notificationAwarenessEnabled);assertFalse(restored.proactiveEnabled)
    }
}
