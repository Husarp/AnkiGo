package com.husarp.ankigo.ui

import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Type
import org.junit.Assert.*
import org.junit.Test

class R23UWidgetTest {
    @Test fun spriteBoundsClearProgressAtEverySizeAndSliderStep() {
        for (w in listOf(110f,140f,219f,220f,286f,400f)) for (h in listOf(55f,67f,99f,100f,140f,250f))
        for (layout in listOf("A","B","C")) for (percent in 50..100) {
            val space=BuddyGeometry.space(w,h)
            val g=BuddyGeometry.of(space.width,space.height,layout).scaled(percent)
            assertTrue(g.spriteLeft >= 0 && g.spriteTop >= 0)
            assertTrue(g.spriteLeft+g.spriteSize <= space.width)
            assertTrue(g.spriteTop+g.spriteSize <= space.height)
            if (layout != "B") {
                val radius=g.size/2-3-g.size*.065f/2
                for (x in listOf(g.spriteLeft,g.spriteLeft+g.spriteSize))
                for (y in listOf(g.spriteTop,g.spriteTop+g.spriteSize)) {
                    val dx=x-g.cx; val dy=y-(g.top+g.size/2)
                    assertTrue("$w x $h $layout $percent", kotlin.math.sqrt(dx*dx+dy*dy) <= radius-1.99f)
                }
            } else if (space.height < 100) assertTrue(g.spriteLeft+g.spriteSize < 70f)
            else if (space.width >= 220) assertTrue(g.spriteLeft+g.spriteSize < 146f)
            else assertTrue(g.spriteTop+g.spriteSize <= 82f)
        }
    }

    @Test fun pickerCombinesPlaceTypeFavouriteShinyAndSearchBeforeSelectedFirstSort() {
        val data=TestData.world()
        val a=TestData.mon(data,16,10,uid="a").also { it.favourite=true; it.shiny=true }
        val b=TestData.mon(data,16,20,uid="b").also { it.favourite=true }
        val c=TestData.mon(data,16,30,uid="c").also { it.shiny=true }
        val mons=listOf(a,b,c)
        fun find(f: BuddyPicker.Filters) = BuddyPicker.mons(mons,"c","",BuddyPicker.Sort.LEVEL,f,listOf("a","b")) { listOf(Type.FLYING) }.map { it.uid }
        assertEquals(listOf("b","a"),find(BuddyPicker.Filters(place=BuddyPicker.Place.TEAM)))
        assertEquals(listOf("c"),find(BuddyPicker.Filters(place=BuddyPicker.Place.BOX)))
        assertEquals(listOf("a"),find(BuddyPicker.Filters(favourites=true,shiny=true,types=TypeFilter(setOf(Type.FLYING)))))
        assertTrue(find(BuddyPicker.Filters(types=TypeFilter(setOf(Type.WATER)))).isEmpty())
        assertTrue(find(BuddyPicker.Filters(place=BuddyPicker.Place.BOX,favourites=true)).isEmpty())
        assertTrue(BuddyPicker.mons(mons,"a","no match",BuddyPicker.Sort.LEVEL,BuddyPicker.Filters(shiny=true)).isEmpty())
        assertFalse(BuddyPicker.Filters().active)
        assertTrue(BuddyPicker.Filters(shiny=true).active)
    }
}
