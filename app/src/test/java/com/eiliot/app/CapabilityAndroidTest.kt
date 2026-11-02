package com.eiliot.app

import android.app.Application
import android.content.ComponentName
import android.provider.Settings
import com.eiliot.app.capabilities.*
import com.eiliot.app.core.*
import com.eiliot.app.llm.ToolCall
import com.eiliot.app.tools.PhoneTools
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
abstract class CapabilityAndroidTestBase {
    @Test fun sharedSnapshotExecutionAndRevocationAgreeAndDiagnosticsExcludeSecrets() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        SentinelRuntime.init(app)
        val s = SentinelRuntime.store
        s.masterEnabled = true;s.phoneToolsEnabled = true;s.webToolsEnabled = false
        s.screenEnabled = true;s.controlEnabled = true;ScreenReader.connected.value = true
        // AndroidKeyStore is a hardware/framework boundary not supplied by this JVM sandbox.
        // An unreadable vault record must be treated as unconfigured, never echoed into diagnostics.
        app.getSharedPreferences("eiliot_v2_settings",Application.MODE_PRIVATE).edit()
            .putString("api_key_cipher","private-api-key-never-in-readiness").commit()
        s.model = "private-model-name";s.baseUrl = "https://private-provider.example/v1"
        try {
            val snapshot = CapabilityRegistry.refresh()
            assertEquals(16,snapshot.entries.count { it.tool })
            assertTrue(snapshot.entry("read_screen")!!.canAttempt)
            assertFalse(snapshot.entry("search_web")!!.canAttempt)
            val context = snapshot.modelContext()
            assertFalse(context.contains("private-api-key"));assertFalse(context.contains("private-model"));assertFalse(context.contains("private-provider"))
            ScreenReader.connected.value = false
            assertFalse(CapabilityRegistry.refresh().entry("read_screen")!!.canAttempt)
            val receipt = JSONObject(PhoneTools.execute(ToolCall("revoked","read_screen","{}")))
            assertFalse(receipt.getBoolean("ok"));assertEquals("capability_not_ready",receipt.getString("error_code"))
            assertEquals("ACCESSIBILITY",receipt.getString("recovery"))
            val web = JSONObject(PhoneTools.execute(ToolCall("web","search_web","{\"query\":\"Android\"}")))
            assertEquals("DISABLED",web.getString("availability"))
            SentinelRuntime.stop()
            assertTrue(CapabilityRegistry.refresh().entries.filter { it.tool }.none { it.canAttempt })
        } finally { ScreenReader.connected.value = false; SentinelRuntime.stop() }
    }
    @Test fun notificationGrantRequiresTheExactComponentNotAPackageSubstring() {
        val app = RuntimeEnvironment.getApplication()
        Settings.Secure.putString(app.contentResolver,"enabled_notification_listeners",app.packageName+".unrelated/.Listener")
        assertFalse(EiliotPermissions.isNotificationAccessGranted(app))
        Settings.Secure.putString(app.contentResolver,"enabled_notification_listeners",ComponentName(app,EiliotNotificationListener::class.java).flattenToShortString())
        assertTrue(EiliotPermissions.isNotificationAccessGranted(app))
    }
    @Test fun endpointValidationAndFailureClassificationNeverPersistProviderMessages() {
        assertTrue(CapabilityRegistry.validCloud("provider.example/v1","key","model"))
        assertFalse(CapabilityRegistry.validCloud("http://provider.example","key","model"))
        assertFalse(CapabilityRegistry.validCloud("https://username:secret@provider.example","key","model"))
        assertFalse(CapabilityRegistry.validCloud("https://provider.example","","model"))
        assertEquals(HealthState.AUTH,CapabilityRegistry.classifyCloudFailure(Exception("Request failed (HTTP 401) secret body")))
        assertEquals(HealthState.RATE_LIMIT,CapabilityRegistry.classifyCloudFailure(Exception("Request failed (HTTP 429)")))
        assertEquals(HealthState.FAILED,CapabilityRegistry.classifyCloudFailure(Exception("API key secret")))
    }
}
@Config(sdk=[36]) class CapabilityAndroidTest : CapabilityAndroidTestBase()
@Config(sdk=[28]) class CapabilityAndroidLegacyTest : CapabilityAndroidTestBase()
