package com.eiliot.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.eiliot.app.capabilities.*
import com.eiliot.app.core.*
import com.eiliot.app.data.MemoryMessage
import com.eiliot.app.ui.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Real Compose shell with sample state; not a physical handset or an audio acceptance test. */
@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[36],qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class PremiumShellUiBase {
    abstract val scale:Float
    abstract val label:String
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun actualShellKeepsThreeDestinationsAndSingleVoicePowerControls() {
        val app=RuntimeEnvironment.getApplication();SentinelRuntime.init(app)
        val store=SentinelRuntime.store;store.masterEnabled=true;store.memoryEnabled=false;store.inferenceMode="offline"
        (SentinelRuntime.enabled as MutableStateFlow).value=true
        CapabilityRegistry.refresh()
        val controller=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            controller.get().setContent {CompositionLocalProvider(LocalDensity provides Density(1f,scale)) {EiliotTheme {WithOverlayAppearance {EiliotShell()}}}}
            compose.waitForIdle()
            (EiliotBrain.messages as MutableStateFlow).value=listOf(
                MemoryMessage(-2,"user","Help me make a little room for today.",1000),
                MemoryMessage(-1,"assistant","## Start with one thing\nChoose what matters most. I can help with your phone, remember a note, or look something up when you need it.",2000))
            compose.waitForIdle()
            compose.onAllNodesWithText("Companion",useUnmergedTree=true).assertCountEquals(1)
            compose.onAllNodesWithText("Vault",useUnmergedTree=true).assertCountEquals(1)
            compose.onAllNodesWithText("Settings",useUnmergedTree=true).assertCountEquals(1)
            compose.onAllNodesWithText("Turn off").assertCountEquals(1)
            compose.onNodeWithContentDescription("Start live call").assertIsDisplayed()
            compose.onNodeWithContentDescription("Action shortcuts").assertIsDisplayed()
            capture(controller.get(),"companion-$label.png")
            compose.onNodeWithText("Vault").performClick();compose.waitForIdle()
            compose.onNodeWithText("Your vault").assertIsDisplayed()
            compose.onNodeWithText("Notes").assertIsDisplayed();compose.onNodeWithText("History").assertIsDisplayed();compose.onNodeWithText("Web feed").assertIsDisplayed()
            capture(controller.get(),"vault-$label.png")
            compose.onNodeWithText("Settings").performClick();compose.waitForIdle()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Voice & wake word"))
            compose.onNodeWithText("Voice & wake word").assertIsDisplayed()
            compose.onAllNodesWithText("Find a male voice").assertCountEquals(0)
            compose.onAllNodesWithText("Download offline wake model").assertCountEquals(0)
            capture(controller.get(),"settings-$label.png")
        }finally {controller.pause().stop().destroy();SentinelRuntime.stop()}
    }
    private fun capture(activity:ComponentActivity,name:String) {
        val root=activity.window.decorView;val image=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
        compose.runOnUiThread {root.draw(Canvas(image))}
        val file=File("build/ui-review/v061/$name");file.parentFile!!.mkdirs()
        file.outputStream().use {assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))}
    }
}
class PremiumCompactUiTest:PremiumShellUiBase() {override val scale=1f;override val label="360-1x"}
class PremiumLargeTextUiTest:PremiumShellUiBase() {override val scale=1.5f;override val label="360-1.5x"}
class PremiumExtraLargeTextUiTest:PremiumShellUiBase() {override val scale=2f;override val label="360-2x"}
@Config(qualifiers="w800dp-h360dp-land-mdpi")
class PremiumLandscapeUiTest:PremiumShellUiBase() {override val scale=1f;override val label="800-landscape"}
@Config(qualifiers="w840dp-h900dp-mdpi")
class PremiumFoldableUiTest:PremiumShellUiBase() {override val scale=1.5f;override val label="840-wide"}
