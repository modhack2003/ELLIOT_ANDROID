package com.eiliot.app

import android.app.Application
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.eiliot.app.core.SentinelRuntime
import com.eiliot.app.data.MemoryMessage
import com.eiliot.app.ui.*
import com.eiliot.app.voice.ModelAssets
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[36],qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SetupAndReaderUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun offlineSetupCompletesOnceAndDoesNotRepeatAfterActivityRecreation() {
        val app=RuntimeEnvironment.getApplication();SentinelRuntime.init(app);SentinelRuntime.store.isInitialSetupComplete=false
        val activity=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            compose.waitForIdle();compose.onNodeWithText("Meet ELLIOT").assertIsDisplayed()
            compose.onNodeWithText("Skip for offline commands only").performScrollTo().performClick()
            compose.onNodeWithText("Voice & wake word").assertExists()
            compose.onNodeWithText("Continue",ignoreCase=true).performScrollTo().performClick()
            compose.onNodeWithText("Continue with text for now").performScrollTo().performClick()
            compose.waitForIdle();assertTrue(SentinelRuntime.store.isInitialSetupComplete);assertEquals("offline",SentinelRuntime.store.inferenceMode)
            compose.onNodeWithText("Companion").assertIsDisplayed()
            activity.recreate();compose.waitForIdle()
            compose.onAllNodesWithText("Meet ELLIOT").assertCountEquals(0);compose.onNodeWithText("Vault").assertIsDisplayed()
        }finally {activity.pause().stop().destroy();SentinelRuntime.stop()}
    }
    @Test fun expandedMessageUsesOnlyTheParentVerticalScroll() {
        val activity=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val body=(1..35).joinToString("\n\n") {"Paragraph $it. A readable explanation that belongs to the main chat scroll."}
        try {
            activity.get().setContent {EiliotTheme {LazyColumn(Modifier.fillMaxSize()) {item {MessageCard(MemoryMessage(99,"assistant",body,1000))}}}}
            compose.waitForIdle();compose.onNodeWithText("Expand full message").performScrollTo().performClick()
            compose.onNodeWithText("Paragraph 35.",substring=true).performScrollTo().assertIsDisplayed()
            compose.onAllNodes(hasScrollAction()).assertCountEquals(1)
            compose.onNodeWithText("Collapse message").performScrollTo().performClick()
            compose.onAllNodes(hasScrollAction()).assertCountEquals(1)
        }finally {activity.pause().stop().destroy()}
    }
    @Test fun bundledFilesInstallAndRepairWithoutNetworkPermission() {
        val app=RuntimeEnvironment.getApplication();Shadows.shadowOf(app).denyPermissions(Manifest.permission.INTERNET)
        val folder=ModelAssets.install(app)
        val encoder=File(folder,"asr/encoder.onnx");val bytes=encoder.length()
        assertTrue(bytes>40_000_000);assertTrue(File(folder,"tts/model.onnx").length()>18_000_000)
        assertTrue(File(folder,"kws/keywords.txt").readText().contains("@hey_elliot"))
        assertTrue(File(app.filesDir,"models/.v061_extracted").isFile)
        encoder.writeBytes(byteArrayOf(1,2,3))
        assertEquals(folder,ModelAssets.install(app));assertEquals(bytes,encoder.length())
    }
}
