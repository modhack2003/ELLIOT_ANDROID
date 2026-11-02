package com.eiliot.app

import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.os.Looper
import android.os.PowerManager
import com.eiliot.app.core.*
import com.eiliot.app.overlay.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,qualifiers="w360dp-h800dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
abstract class OverlayAndroidTestBase {
    @Test fun legacyPositionMigratesOnceAndNeverChangesConversationSettings() {
        val c=RuntimeEnvironment.getApplication()
        val legacy=c.getSharedPreferences("eiliot_orb_geometry",Context.MODE_PRIVATE)
        legacy.edit().putInt("x",12).putInt("y",240).commit()
        c.getSharedPreferences("eiliot_v2_settings",Context.MODE_PRIVATE).edit().putString("owner-marker","retain").commit()
        val s=OverlayLayoutStore(c)
        try {
            val b=OverlayRect(8,24,352,776)
            val imported=s.anchor("portrait_compact",b,80)
            assertEquals(DockEdge.LEFT,imported.edge)
            assertEquals(12,legacy.getInt("x",0))
            s.saveAnchor("landscape_compact",OverlayAnchor(DockEdge.RIGHT,.9f))
            assertEquals(imported,s.anchor("portrait_compact",b,80))
            assertEquals(.9f,s.anchor("landscape_compact",b,80).fraction)
            s.reset();assertEquals(OverlayAnchor(),s.anchor("portrait_compact",b,80))
            assertEquals("retain",c.getSharedPreferences("eiliot_v2_settings",Context.MODE_PRIVATE).getString("owner-marker",""))
        } finally { s.close() }
    }
    @Test fun corruptedAppearanceRecoversAndOwnerSettingsDoNotOverrideSystemMotionOff() {
        val c=RuntimeEnvironment.getApplication()
        c.getSharedPreferences("elliot_overlay_layout_v1",Context.MODE_PRIVATE).edit().putString("size","bad").putFloat("opacity",Float.NaN).putString("motion","unknown").commit()
        val s=OverlayLayoutStore(c)
        try {
            assertEquals(80,s.appearance().sizeDp);assertEquals(1f,s.appearance().opacity)
            assertEquals("system",s.appearance().motion)
            s.save(OverlayAppearance(motion="expressive",haptics=false))
            android.provider.Settings.Global.putFloat(c.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,0f)
            assertTrue(s.reducedMotion(c));assertFalse(OverlayLayoutStore.hapticsEnabled(c))
        } finally { s.close() }
    }
    @Test fun offAndPermissionRevocationRemoveActualWindowStateAndCannotReopenFromLayoutEvents() {
        val c=RuntimeEnvironment.getApplication()
        SentinelRuntime.init(c);SentinelRuntime.store.memoryEnabled=false;SentinelRuntime.store.orbEnabled=true
        SentinelRuntime.start(c,false);SentinelRuntime.foreground=true
        ShadowSettings.setCanDrawOverlays(true)
        Shadows.shadowOf(c.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        Shadows.shadowOf(c.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        val main=Shadows.shadowOf(Looper.getMainLooper())
        try {
            OverlayManager.show(c);main.idle()
            assertTrue(OverlayManager.isShowing())
            val bounds=OverlayWindowController(c) { }.viewport().usable
            assertTrue(OverlayManager.windowRects().all(bounds::contains))
            OverlayManager.openPanel();main.idle();assertTrue(OverlayManager.hasPrivateWindows())
            SentinelRuntime.stop();main.idleFor(java.time.Duration.ofMillis(2500))
            assertFalse(OverlayManager.isShowing());assertFalse(OverlayManager.hasPrivateWindows())
            OverlayManager.setOrbPaused(false);OverlayManager.openPanel();main.idle()
            assertFalse(OverlayManager.isShowing())
            SentinelRuntime.start(c,false);OverlayManager.show(c);main.idle();assertTrue(OverlayManager.isShowing())
            ShadowSettings.setCanDrawOverlays(false);main.idleFor(java.time.Duration.ofMillis(1500))
            assertFalse(OverlayManager.isShowing())
        } finally { SentinelRuntime.stop();OverlayManager.hide();ShadowSettings.setCanDrawOverlays(false) }
    }
    @Test fun nativeLayoutCommandsSavePreferencesWithoutACloudKeyOrPhoneAction()=runBlocking {
        val c=RuntimeEnvironment.getApplication()
        SentinelRuntime.init(c);SentinelRuntime.store.memoryEnabled=false;SentinelRuntime.store.phoneToolsEnabled=false
        SentinelRuntime.start(c,false)
        try {
            assertTrue(EiliotBrain.send("Move your orb left"))
            withTimeout(4000) { while(EiliotBrain.state.value.busy) { Shadows.shadowOf(Looper.getMainLooper()).idle();delay(20) } }
            assertTrue(EiliotBrain.state.value.reply.contains("placement saved"))
            assertTrue(EiliotBrain.state.value.status.contains("Reply"))
            assertTrue(c.getSharedPreferences("elliot_overlay_layout_v1",Application.MODE_PRIVATE).all.any { (key,value)->key.endsWith("_edge") && value=="LEFT" })
            assertNull(com.eiliot.app.tools.ActionApprovals.pending.value)
        } finally { SentinelRuntime.stop() }
    }
}
@Config(sdk=[28])class OverlayAndroidLegacyTest:OverlayAndroidTestBase()
@Config(sdk=[36])class OverlayAndroidTest:OverlayAndroidTestBase()
