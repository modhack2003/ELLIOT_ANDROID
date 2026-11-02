package com.eiliot.app

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.media.AudioManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import com.eiliot.app.core.SentinelRuntime
import com.eiliot.app.llm.ToolCall
import com.eiliot.app.tools.ActionApprovals
import com.eiliot.app.tools.PhoneTools
import com.eiliot.app.tools.CommandGrant
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Android framework simulation; does not certify physical device or OEM behavior. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
abstract class PhoneToolsAndroidTestBase {
    @Test fun actualToolExecutionRespectsApprovalCancellationAndAndroidIntents() = runBlocking {
        val application = RuntimeEnvironment.getApplication()
        SentinelRuntime.init(application)
        // Complete startup database work before allowing the sandbox to be torn down.
        withTimeout(5000) { SentinelRuntime.scope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() } }
        val store = SentinelRuntime.store
        store.reviewActions = true
        store.masterEnabled = true; store.phoneToolsEnabled = true; store.webToolsEnabled = true
        store.screenEnabled = false; store.controlEnabled = false
        val audio = application.getSystemService(AudioManager::class.java)
        val shadowApp = Shadows.shadowOf(application)
        var serial = 0
        suspend fun execute(name: String, args: String = "{}") = JSONObject(PhoneTools.execute(ToolCall("android_${++serial}", name, args)))
        suspend fun approved(name: String, args: String, allow: Boolean = true): JSONObject = coroutineScope {
            val result = async { execute(name, args) }
            yield()
            val approval = checkNotNull(ActionApprovals.pending.value) { "Expected native owner approval for $name" }
            ActionApprovals.resolve(approval.id, allow)
            withTimeout(3000) { result.await() }
        }
        try {
            val info = execute("device_info")
            assertTrue(info.getBoolean("ok"))
            val fields = JSONObject(info.getString("result"))
            assertTrue(fields.getString("Android").contains("API ${Build.VERSION.SDK_INT}"))
            assertEquals("Not connected", fields.getString("Accessibility"))

            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val packageManager = Shadows.shadowOf(application.packageManager)
            repeat(65) { index ->
                val pkg = "org.example.pagination.app$index"
                packageManager.addResolveInfoForIntent(launcher, ResolveInfo().apply {
                    nonLocalizedLabel = "Pagination test app $index"
                    activityInfo = ActivityInfo().apply {
                        name = "$pkg.Main"; packageName = pkg
                        applicationInfo = ApplicationInfo().apply { packageName = pkg }
                    }
                })
            }
            val firstPage = JSONObject(execute("list_apps", "{\"query\":\"Pagination test\"}").getString("result"))
            assertEquals(65, firstPage.getInt("visible_matches")); assertEquals(60, firstPage.getInt("returned"))
            assertTrue(firstPage.getBoolean("has_more")); assertEquals(60, firstPage.getInt("next_offset"))
            val lastPage = JSONObject(execute("list_apps", "{\"query\":\"Pagination test\",\"offset\":60}").getString("result"))
            assertEquals(5, lastPage.getInt("returned")); assertFalse(lastPage.getBoolean("has_more")); assertTrue(lastPage.isNull("next_offset"))
            val firstPackages = (0 until 60).map { firstPage.getJSONArray("apps").getJSONObject(it).getString("package") }
            assertTrue((0 until 5).none { lastPage.getJSONArray("apps").getJSONObject(it).getString("package") in firstPackages })

            assertTrue(approved("set_media_volume", "{\"percent\":0}").getBoolean("ok"))
            assertEquals(0, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
            assertFalse(approved("set_media_volume", "{\"percent\":100}", allow = false).getBoolean("ok"))
            assertEquals(0, audio.getStreamVolume(AudioManager.STREAM_MUSIC))

            assertTrue(approved("open_settings", "{\"page\":\"internet\"}").getBoolean("ok"))
            assertEquals(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS,
                shadowApp.nextStartedActivity.action)
            assertTrue(approved("open_settings", "{\"page\":\"app\"}").getBoolean("ok"))
            val appSettings = shadowApp.nextStartedActivity
            assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appSettings.action)
            assertEquals("package:${application.packageName}", appSettings.dataString)
            assertTrue(appSettings.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)

            assertTrue(approved("set_timer", "{\"seconds\":300,\"label\":\"Tea\"}").getBoolean("ok"))
            val timer = shadowApp.nextStartedActivity
            assertEquals(AlarmClock.ACTION_SET_TIMER, timer.action)
            assertEquals(300, timer.getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
            assertEquals("Tea", timer.getStringExtra(AlarmClock.EXTRA_MESSAGE))
            assertFalse(timer.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true))
            assertTrue(approved("open_url", "{\"url\":\"https://example.org/guide\"}").getBoolean("ok"))
            val browser = shadowApp.nextStartedActivity
            assertEquals(Intent.ACTION_VIEW, browser.action)
            assertEquals("https://example.org/guide", browser.dataString)
            assertTrue(browser.hasCategory(Intent.CATEGORY_BROWSABLE))

            assertFalse(execute("read_screen").getBoolean("ok"))
            assertFalse(execute("navigate", "{\"action\":\"back\"}").getBoolean("ok"))
            assertFalse(execute("set_media_volume", "{\"percent\":101}").getBoolean("ok"))
            assertNull(ActionApprovals.pending.value)

            // Turn Off invalidates an outstanding approval even if the owner later resumes.
            val waiting = async { execute("set_media_volume", "{\"percent\":100}") }
            yield(); val oldApproval = checkNotNull(ActionApprovals.pending.value)
            SentinelRuntime.stop(); store.masterEnabled = true
            ActionApprovals.resolve(oldApproval.id, true)
            assertFalse(waiting.await().getBoolean("ok"))
            assertEquals(0, audio.getStreamVolume(AudioManager.STREAM_MUSIC))

            // Off also invalidates an already approved action during the overlay dismissal delay.
            val allowed = async { execute("set_media_volume", "{\"percent\":100}") }
            yield(); ActionApprovals.resolve(checkNotNull(ActionApprovals.pending.value).id, true)
            yield(); SentinelRuntime.stop(); store.masterEnabled = true
            assertFalse(allowed.await().getBoolean("ok"))
            assertEquals(0, audio.getStreamVolume(AudioManager.STREAM_MUSIC))

            store.phoneToolsEnabled = false
            assertFalse(execute("device_info").getBoolean("ok"))

            store.phoneToolsEnabled = true; store.reviewActions = false
            suspend fun requested(name: String, args: String, request: String, token: Long = SentinelRuntime.epoch) =
                JSONObject(PhoneTools.execute(ToolCall("direct_${++serial}",name,args),CommandGrant.request(token,request)))
            assertTrue(requested("set_media_volume","{\"percent\":30}","Set volume to thirty percent").getBoolean("ok"))
            assertNull(ActionApprovals.pending.value)
            val expectedVolume = (30 * audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 100.0).toInt()
            assertEquals(expectedVolume,audio.getStreamVolume(AudioManager.STREAM_MUSIC))
            assertFalse(requested("set_media_volume","{\"percent\":100}","Set volume to thirty percent").getBoolean("ok"))
            assertEquals(expectedVolume,audio.getStreamVolume(AudioManager.STREAM_MUSIC))
            assertFalse(requested("set_media_volume","{\"percent\":0}","Check my battery").getBoolean("ok"))
            assertNull(ActionApprovals.pending.value)
            assertTrue(requested("set_timer","{\"seconds\":300}","Start a timer for five minutes").getBoolean("ok"))
            assertTrue(shadowApp.nextStartedActivity.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI,false))
            val oldEpoch = SentinelRuntime.epoch
            SentinelRuntime.stop(); store.masterEnabled = true
            assertFalse(requested("set_media_volume","{\"percent\":100}","Set volume to 100",oldEpoch).getBoolean("ok"))
            val queued = async { requested("set_media_volume","{\"percent\":100}","Set volume to 100") }
            yield(); SentinelRuntime.stop(); store.masterEnabled = true
            assertFalse(queued.await().getBoolean("ok"))
            assertEquals(expectedVolume,audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        } finally { SentinelRuntime.stop(); ActionApprovals.cancel() }
    }
}

@Config(sdk = [36])
class PhoneToolsAndroidTest : PhoneToolsAndroidTestBase()

@Config(sdk = [28])
class PhoneToolsAndroidLegacyTest : PhoneToolsAndroidTestBase()
