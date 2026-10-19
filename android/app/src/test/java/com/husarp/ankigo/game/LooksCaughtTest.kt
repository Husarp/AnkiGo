package com.husarp.ankigo.game

import com.husarp.ankigo.ui.LookPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R9-B1: "Change look" offers an earlier form only when it is caught (Pokédex); its own species always. Made-up species only. */
class LooksCaughtTest {
    private val data = TestData.world() // 1 -> 2 -> 3 is a made-up 3-stage line
    private fun nums(sp: Int, caught: Set<Int>) = Looks.choices(data, TestData.mon(data, sp, 20), caught).map { it.num }

    @Test fun onlyCaughtEarlierFormsAndTheOwnSpeciesAreOffered() {
        assertEquals(listOf(1, 3), nums(3, setOf(1)))
        assertEquals(listOf(2, 3), nums(3, setOf(2)))
        assertEquals(listOf(1, 2, 3), nums(3, setOf(1, 2, 3)))
    }

    @Test fun theOwnSpeciesIsOfferedEvenWhenNotCaught() {
        assertEquals(listOf(3), nums(3, emptySet()))
    }

    @Test fun hasChoiceNeedsAnotherCaughtForm() {
        val mon = TestData.mon(data, 3, 20)
        assertFalse(Looks.hasChoice(data, mon, emptySet()))
        assertFalse(Looks.hasChoice(data, mon, setOf(3)))
        assertTrue(Looks.hasChoice(data, mon, setOf(2)))
    }

    @Test fun sanitizeClearsAnUncaughtLookAndKeepsACaughtOne() {
        val s = TestData.state(data, 3)
        val a = TestData.mon(data, 3, 20, uid = "a").also { it.look = 1 }
        val b = TestData.mon(data, 3, 20, uid = "b").also { it.look = 2 }
        val c = TestData.mon(data, 3, 20, uid = "c").also { it.look = 3 }
        s.mons += listOf(a, b, c); s.caught += 2
        Looks.sanitize(s, data)
        assertNull(a.look); assertEquals(2, b.look); assertEquals(3, c.look)
    }

    @Test fun setLookRefusesAnUncaughtForm() {
        val s = TestData.state(data, 3)
        val mon = TestData.mon(data, 3, 20, uid = "x")
        s.mons += mon; s.caught += 2
        val g = Game(data, s)
        assertFalse(g.setLook("x", 1).ok)
        assertNull(mon.look)
        assertTrue(g.setLook("x", 2).ok)
        assertEquals(2, mon.look)
    }

    @Test fun captionsKeepFullLineStageNumbers() {
        // line 1,2,3 with only 2 and 3 shown: form 2 is still "Stage 2"
        assertEquals("Stage 2", LookPicker.caption(1, 2, 3))
        assertEquals("Current", LookPicker.caption(2, 3, 3))
    }
}
