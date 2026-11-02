package com.eiliot.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.eiliot.app.capabilities.*
import com.eiliot.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Actual Compose rendering/interaction in simulated Android, not a phone screenshot. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk=[36], qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class CapabilityUiTestBase {
    abstract val fontScale: Float
    abstract val filename: String
    @get:Rule val compose = createEmptyComposeRule()
    private fun render(fontScale:Float, filename:String, interaction:()->Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val snapshot = CapabilityPolicy.evaluate(CapabilityFacts(running=true,now=1000))
            controller.get().setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f,fontScale)) {
                    EiliotTheme {
                        Column(Modifier.fillMaxSize().background(EiliotColors.Void).verticalScroll(rememberScrollState()).padding(16.dp)) {
                            CapabilityDashboardContent(snapshot,SessionPresentation(SessionPhase.IDLE,"Ready when you are"),{}, {}, {})
                        }
                    }
                }
            }
            compose.waitForIdle()
            fun capture(name: String) {
                val root = controller.get().window.decorView
                val bitmap = Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
                compose.runOnUiThread { root.draw(Canvas(bitmap)) }
                val file = File("build/ui-review/$name");checkNotNull(file.parentFile).mkdirs()
                file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
                assertTrue(bitmap.width > 0 && bitmap.height > 0)
            }
            capture(filename.removeSuffix(".png")+"-overview.png")
            interaction()
            capture(filename)
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun searchScrollAndReadableActionsWorkAtThisTextScale() = render(fontScale,filename) {
        compose.onNodeWithText("WHAT I CAN DO").assertIsDisplayed()
        val controls = compose.onNodeWithText("Settings").performScrollTo().fetchSemanticsNode().boundsInRoot
        assertTrue("Controls label must not wrap letter by letter",controls.height < 90f)
        compose.onNodeWithText("Find a capability or missing permission").performScrollTo().performTextInput("wake")
        compose.onNodeWithText("Wake interruption").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Open Settings").performScrollTo().assertIsDisplayed()
    }
}
// Isolate native Compose/Android runtimes, matching the existing multi-SDK test policy.
class CapabilityUiTest : CapabilityUiTestBase() {
    override val fontScale=1f
    override val filename="capabilities-360.png"
}
class CapabilityLargeTextUiTest : CapabilityUiTestBase() {
    override val fontScale=2f
    override val filename="capabilities-large-text.png"
}
