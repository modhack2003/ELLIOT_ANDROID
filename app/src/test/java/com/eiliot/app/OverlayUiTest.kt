package com.eiliot.app

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.eiliot.app.overlay.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[36],qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
abstract class OverlayUiTestBase {
    abstract val scale:Float
    abstract val landscape:Boolean
    @Test fun actualOverlayViewsReflowAndRetainReachableActions() {
        val activity=Robolectric.buildActivity(Activity::class.java)
        activity.get().setTheme(android.R.style.Theme_Material_NoActionBar)
        activity.setup()
        try {
            val host=activity.get()
            val configuration=Configuration(host.resources.configuration).apply { fontScale=scale }
            val context=host.createConfigurationContext(configuration)
            val width=if(landscape)800 else 360;val height=if(landscape)360 else 800
            val bounds=OverlayGeometry.safeBounds(width,height,top=24,bottom=24,margin=8)
            val rect=OverlayGeometry.orb(bounds,80,OverlayAnchor())!!
            val placement=OverlayGeometry.panel(bounds,rect,1f,scale,true)!!
            val frame=FrameLayout(host).apply { setBackgroundColor(android.graphics.Color.rgb(6,11,19)) }
            var submitted="";var closed=false;var off=false
            val panel=OverlayTranscriptView(context,placement.mode,OverlayAppearance(haptics=false),TranscriptActions(
                open={},off={off=true},close={closed=true},talk={},stop={},more={},
                send={submitted=it;true},focus={},draft={}),"My draft")
            panel.status.text="Listening · Ready for your next request"
            panel.query.text="You: Help me plan my day"
            panel.answer.text="Let's make room for what matters.\n\n1. Finish the task in front of you.\n2. Take a short break.\n3. Tell me what you want to do next.\n\n".repeat(30)+"🙂 বাংলাও রাখি"
            val orb=ArcCoreView(context).apply { setPresentation("reduced",false);setState(OrbState.LISTENING);setPaused(true) }
            frame.addView(orb,FrameLayout.LayoutParams(rect.width,rect.height).apply { leftMargin=rect.left;topMargin=rect.top })
            frame.addView(panel,FrameLayout.LayoutParams(placement.rect.width,placement.rect.height).apply { leftMargin=placement.rect.left;topMargin=placement.rect.top })
            host.setContentView(frame)
            frame.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
            frame.layout(0,0,width,height)
            val all=mutableListOf<View>()
            fun visit(v:View) { all+=v;if(v is ViewGroup)for(i in 0 until v.childCount)visit(v.getChildAt(i)) }
            visit(panel)
            val visible=all.filter { it.isClickable && it.isShown }
            for(button in visible.filter { it is TextView && it !is android.widget.EditText && it!==panel.answer }) {
                assertTrue("${button.contentDescription}: width ${button.width}",button.width>=48)
                assertTrue("${button.contentDescription}: height ${button.height}",button.height>=48)
                assertTrue(button.contentDescription?.isNotBlank()==true)
                val visibleRect=android.graphics.Rect()
                assertTrue(button.getGlobalVisibleRect(visibleRect))
                assertTrue("${button.contentDescription} must not be clipped",visibleRect.width()>=48 && visibleRect.height()>=48)
            }
            val caption=File("build/ui-review/orb-${if(landscape)"landscape" else "portrait"}-${scale}.png")
            checkNotNull(caption.parentFile).mkdirs()
            val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
            frame.draw(Canvas(bitmap));caption.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
            assertTrue(bounds.contains(placement.rect));assertFalse(placement.rect.overlaps(rect))
            assertEquals("My draft",panel.input.text.toString())
            val send=all.first { it.contentDescription=="Send message" }
            assertTrue(send.isShown);send.performClick();assertEquals("My draft",submitted)
            all.first { it.contentDescription=="Turn ELLIOT off" }.performClick();assertTrue(off)
            all.first { it.contentDescription=="Close transcript" }.performClick();assertTrue(closed)
            if(placement.mode==OverlayPanelMode.FULL) {
                assertTrue(panel.scroll.height>=48)
                panel.scroll.fullScroll(View.FOCUS_DOWN)
                assertTrue(panel.answer.height>panel.scroll.height)
            } else assertEquals(OverlayPanelMode.INPUT,placement.mode)
            assertFalse(orb.isAnimating())
        } finally { activity.pause().stop().destroy();Shadows.shadowOf(Looper.getMainLooper()).idle() }
    }
}
class OverlayUiTest:OverlayUiTestBase() { override val scale=1f;override val landscape=false }
class OverlayLargeTextUiTest:OverlayUiTestBase() { override val scale=2f;override val landscape=false }
@Config(qualifiers="w800dp-h360dp-land-mdpi")
class OverlayLandscapeUiTest:OverlayUiTestBase() { override val scale=1.5f;override val landscape=true }
