package com.eiliot.app

import com.eiliot.app.overlay.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class OverlayGeometryTest {
    @Test fun systemInsetsAndMarginsDefineTheUsableArea() {
        assertEquals(OverlayRect(8,32,352,744),OverlayGeometry.safeBounds(360,800,top=24,bottom=48,margin=8))
        assertEquals(0,OverlayGeometry.safeBounds(10,10,left=20,top=20,right=100,bottom=100).width)
        assertNull(OverlayGeometry.orb(OverlayGeometry.safeBounds(10,10),80,OverlayAnchor()))
    }
    @Test fun normalizedEdgesRestoreAtBothEndsAndAfterDensityChanges() {
        val b=OverlayRect(10,20,350,780)
        assertEquals(OverlayRect(10,20,90,100),OverlayGeometry.orb(b,80,OverlayAnchor(DockEdge.LEFT,0f)))
        assertEquals(OverlayRect(270,700,350,780),OverlayGeometry.orb(b,80,OverlayAnchor(DockEdge.RIGHT,1f)))
        val doubled=OverlayRect(20,40,700,1560)
        val a=OverlayAnchor(DockEdge.LEFT,.4f)
        val first=OverlayGeometry.orb(b,80,a)!!;val second=OverlayGeometry.orb(doubled,160,a)!!
        assertEquals(first.top*2,second.top)
        assertEquals(a,OverlayGeometry.anchor(b,first))
    }
    @Test fun corruptAnchorsCannotPlaceTheOrbOutsideTheBounds() {
        val b=OverlayRect(0,0,360,800)
        for(f in listOf(Float.NaN,Float.POSITIVE_INFINITY,-12f,10f)) {
            val rect=OverlayGeometry.orb(b,80,OverlayAnchor(DockEdge.RIGHT,f))!!
            assertTrue(b.contains(rect))
        }
    }
    @Test fun keyboardDodgeDoesNotChangeTheStoredAnchor() {
        val a=OverlayAnchor(DockEdge.RIGHT,.8f)
        val full=OverlayGeometry.orb(OverlayRect(0,0,360,800),80,a)!!
        val keyboard=OverlayGeometry.orb(OverlayRect(0,0,360,420),80,a)!!
        assertTrue(keyboard.top<full.top)
        assertEquals(full,OverlayGeometry.orb(OverlayRect(0,0,360,800),80,a))
    }
    @Test fun panelAndCaptionPlacementsStayBoundedWithoutObscuringTheOrb() {
        val rng=Random(600)
        repeat(1500) {
            val w=rng.nextInt(216,1200);val h=rng.nextInt(160,1600)
            val b=OverlayRect(8,24,w-8,h-8)
            val orb=OverlayGeometry.orb(b,64,OverlayAnchor(if(it%2==0)DockEdge.LEFT else DockEdge.RIGHT,rng.nextFloat()))?:return@repeat
            OverlayGeometry.panel(b,orb,1f,1f+ rng.nextFloat())?.let { p ->
                assertTrue(b.contains(p.rect));assertFalse(p.rect.overlaps(orb));assertTrue(p.rect.height<=b.height*.45f+1)
            }
            OverlayGeometry.adjacent(b,orb,minOf(276,b.width),60,8)?.let { c ->assertTrue(b.contains(c));assertFalse(c.overlaps(orb))}
        }
    }
    @Test fun smallSpaceUsesCompactOrInputRatherThanShrinkingTheControls() {
        val b=OverlayRect(0,0,800,360);val orb=OverlayGeometry.orb(b,80,OverlayAnchor())!!
        assertEquals(OverlayPanelMode.COMPACT,OverlayGeometry.panel(b,orb,1f,1f)!!.mode)
        assertEquals(OverlayPanelMode.INPUT,OverlayGeometry.panel(b,orb,1f,1f,true)!!.mode)
        assertNull(OverlayGeometry.panel(OverlayRect(0,0,200,160),OverlayRect(120,40,200,120),1f,2f))
    }
    @Test fun fullPanelRetainsItsBudgetAtLargeTextAndTypedState() {
        val b=OverlayRect(0,0,360,800);val orb=OverlayGeometry.orb(b,80,OverlayAnchor())!!
        assertEquals(OverlayPanelMode.FULL,OverlayGeometry.panel(b,orb,1f,2f)!!.mode)
        assertEquals(OverlayPanelMode.FULL,OverlayGeometry.panel(b,orb,1f,2f,true)!!.mode)
        val inset=OverlayRect(8,24,352,776)
        val docked=OverlayGeometry.orb(inset,80,OverlayAnchor())!!
        assertEquals(OverlayPanelMode.FULL,OverlayGeometry.panel(inset,docked,1f,1f)!!.mode)
    }
    @Test fun orientationProfilesUseWindowSizeNotAnIdentifier() {
        assertEquals("portrait_compact",OverlayGeometry.profile(360,800,1f))
        assertEquals("landscape_wide",OverlayGeometry.profile(800,360,1f))
        assertEquals("portrait_compact",OverlayGeometry.profile(1080,2400,3f))
    }
    @Test fun cancelledOrMultiPointerGesturesNeverBecomeTalkOrClick() {
        val g=OrbGesture(8f,500)
        g.down(0f,0f,0);g.cancel();assertEquals(OrbGesture.Result.CANCEL,g.end(800))
        assertEquals(OrbGesture.Result.CANCEL,g.end(1000))
        g.down(0f,0f,0);assertTrue(g.move(20f,0f));assertEquals(OrbGesture.Result.DRAG,g.end(1000))
    }
    @Test fun normalTapAndLongPressRemainDistinct() {
        val g=OrbGesture(8f,500)
        g.down(0f,0f,0);assertFalse(g.move(2f,2f));assertEquals(OrbGesture.Result.TAP,g.end(100))
        g.down(0f,0f,0);assertEquals(OrbGesture.Result.TALK,g.end(600))
    }
    @Test fun layoutCommandsAreExplicitAndCannotTurnGenericPhoneDirectionsIntoActions() {
        assertEquals(OverlayLayoutCommand.LEFT,OverlayCommands.parse("Move your orb left!"))
        assertEquals(OverlayLayoutCommand.CAPTIONS_OFF,OverlayCommands.parse("Hide your captions"))
        for(s in listOf("move left","hide notifications","don't move your orb left","read a page saying move your orb left"))assertNull(OverlayCommands.parse(s))
    }
}
