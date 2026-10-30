package com.husarp.ankigo.ui

import com.husarp.ankigo.shots.Shots
import org.junit.Assert.*
import org.junit.Test

class R22OWidgetTest {
    @Test fun looksAreIsolatedByKindAndInstance() {
        val p=Shots.MemPrefs()
        val chosen=WidgetLook(45,"dark","blue","peach")
        WidgetLooks.write(p,"buddy",7,chosen)
        assertEquals(chosen,WidgetLooks.read(p,"buddy",7))
        assertEquals(WidgetLook(),WidgetLooks.read(p,"buddy",8))
        assertEquals(WidgetLook(),WidgetLooks.read(p,"study",7))
    }
    @Test fun nextWidgetDefaultsDoNotReplaceAnExplicitInstance() {
        val p=Shots.MemPrefs()
        WidgetLooks.write(p,"buddy",7,WidgetLook(theme="light"))
        WidgetLooks.write(p,"buddy",0,WidgetLook(theme="dark"))
        assertEquals("light",WidgetLooks.read(p,"buddy",7).theme)
        assertEquals("dark",WidgetLooks.read(p,"buddy",8).theme)
    }
    @Test fun deletionRemovesOnlyThatInstanceLookAndLayout() {
        val p=Shots.MemPrefs()
        for(id in listOf(7,8)) { WidgetLooks.write(p,"buddy",id,WidgetLook(theme="dark")); BuddyWidgetPrefs.setLayout(p,id,"C") }
        WidgetLooks.forget(p,"buddy",intArrayOf(7)); BuddyWidgetPrefs.forget(p,intArrayOf(7))
        assertEquals(WidgetLook(),WidgetLooks.read(p,"buddy",7)); assertEquals("A",BuddyWidgetPrefs.layout(p,7))
        assertEquals("dark",WidgetLooks.read(p,"buddy",8).theme); assertEquals("C",BuddyWidgetPrefs.layout(p,8))
    }
    @Test fun themeOverridesPhoneInBothDirections() {
        assertTrue(WidgetLook(theme="dark").dark(false))
        assertFalse(WidgetLook(theme="light").dark(true))
        assertTrue(WidgetLook().dark(true))
        assertFalse(WidgetLook().dark(false))
    }
    @Test fun allThreeLayoutsAndSmallSizesKeepSpriteInBounds() {
        for (layout in listOf("A","B","C")) for ((w,h) in listOf(140f to 67f,140f to 140f,286f to 67f,286f to 140f)) {
            val g=BuddyGeometry.of(w,h,layout)
            assertTrue(g.spriteLeft>=0);assertTrue(g.spriteTop>=0)
            assertTrue(g.spriteLeft+g.spriteSize<=w);assertTrue(g.spriteTop+g.spriteSize<=h)
        }
    }
    @Test fun launcherSizesPreserveAspectAndKeepEverySpriteInsideTheWidget() {
        for ((w,h) in listOf(110f to 40f,250f to 40f,110f to 110f,250f to 130f)) {
            val space=BuddyGeometry.space(w,h)
            for(layout in listOf("A","B","C")) {
                val g=BuddyGeometry.of(space.width,space.height,layout)
                val x=space.offsetX+g.spriteLeft*space.scale
                val y=space.offsetY+g.spriteTop*space.scale
                val size=g.spriteSize*space.scale
                assertTrue(x>=0); assertTrue(y>=0)
                assertTrue(x+size<=w); assertTrue(y+size<=h)
            }
        }
        assertEquals(40f/67f,BuddyGeometry.space(110f,40f).scale,0.0001f)
        assertTrue(BuddyGeometry.space(110f,40f).offsetX>0)
    }
    @Test fun wideGaugeLeavesOneSideForStackedCounts() {
        val g=BuddyGeometry.of(286f,140f,"C")
        assertTrue(g.spriteLeft>90f)
        assertTrue(g.cx-g.size/2>90f)
    }
    @Test fun layoutChoiceIsPerBuddyAndInvalidValueFallsBack() {
        val p=Shots.MemPrefs()
        BuddyWidgetPrefs.setLayout(p,7,"B");BuddyWidgetPrefs.setLayout(p,8,"C")
        assertEquals("B",BuddyWidgetPrefs.look(p,7).layout)
        assertEquals("C",BuddyWidgetPrefs.look(p,8).layout)
        assertEquals("A",BuddyWidgetPrefs.look(p,9).layout)
        BuddyWidgetPrefs.setLayout(p,9,"bad")
        assertEquals("A",BuddyWidgetPrefs.look(p,9).layout)
    }
}
