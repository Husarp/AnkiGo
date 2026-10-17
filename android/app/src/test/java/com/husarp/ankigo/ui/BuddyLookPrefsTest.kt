package com.husarp.ankigo.ui

import com.husarp.ankigo.shots.Shots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R5-06: the Buddy widget's ring colour and animated option (per widget id) and how a ring setting turns into a colour. */
class BuddyLookPrefsTest {
    @Test fun defaultsWhenMissing() {
        val p = Shots.MemPrefs()
        assertEquals(BuddyRing.APP, BuddyWidgetPrefs.ring(p, 4))
        assertFalse(BuddyWidgetPrefs.anim(p, 4))
        assertEquals(BuddyLook(BuddyRing.APP, false), BuddyWidgetPrefs.look(p, 4))
    }

    @Test fun parseAndFormat() {
        assertEquals("app", BuddyRing.APP.format())
        assertEquals("type", BuddyRing(BuddyRing.Mode.TYPE).format())
        assertEquals("pick:pink", BuddyRing(BuddyRing.Mode.PICK, "pink").format())
        for (r in listOf(BuddyRing.APP, BuddyRing(BuddyRing.Mode.TYPE), BuddyRing(BuddyRing.Mode.PICK, "teal"))) assertEquals(r, BuddyRing.parse(r.format()))
        for (name in BuddyRing.PICKS) assertEquals(BuddyRing(BuddyRing.Mode.PICK, name), BuddyRing.parse("pick:$name"))
    }

    @Test fun badTextFallsBack() {
        assertEquals(BuddyRing.APP, BuddyRing.parse(null))
        assertEquals(BuddyRing.APP, BuddyRing.parse(""))
        assertEquals(BuddyRing.APP, BuddyRing.parse("rainbow"))
        assertEquals(BuddyRing(BuddyRing.Mode.PICK, "mauve"), BuddyRing.parse("pick:"))
        assertEquals(BuddyRing(BuddyRing.Mode.PICK, "mauve"), BuddyRing.parse("pick:chartreuse"))
    }

    @Test fun eightPicksInTheMockupOrder() =
        assertEquals(listOf("mauve", "blue", "pink", "green", "peach", "teal", "yellow", "red"), BuddyRing.PICKS)

    @Test fun savedPerWidget() {
        val p = Shots.MemPrefs()
        BuddyWidgetPrefs.setRing(p, 1, BuddyRing(BuddyRing.Mode.PICK, "red"))
        BuddyWidgetPrefs.setAnim(p, 1, true)
        BuddyWidgetPrefs.setRing(p, 2, BuddyRing(BuddyRing.Mode.TYPE))
        assertEquals(BuddyLook(BuddyRing(BuddyRing.Mode.PICK, "red"), true), BuddyWidgetPrefs.look(p, 1))
        assertEquals(BuddyLook(BuddyRing(BuddyRing.Mode.TYPE), false), BuddyWidgetPrefs.look(p, 2))
        assertEquals(BuddyLook(), BuddyWidgetPrefs.look(p, 3))
    }

    @Test fun forgetRemovesTheLookToo() {
        val p = Shots.MemPrefs()
        for (id in intArrayOf(1, 2)) {
            BuddyWidgetPrefs.set(p, id, "x"); BuddyWidgetPrefs.setRing(p, id, BuddyRing(BuddyRing.Mode.TYPE)); BuddyWidgetPrefs.setAnim(p, id, true)
        }
        BuddyWidgetPrefs.forget(p, intArrayOf(1))
        assertFalse(p.contains("b1.mon")); assertFalse(p.contains("b1.ring")); assertFalse(p.contains("b1.anim"))
        assertTrue(p.contains("b2.mon")); assertTrue(p.contains("b2.ring")); assertTrue(p.contains("b2.anim"))
    }

    @Test fun existingKeysKeepTheirNames() {
        assertEquals("b7.mon", BuddyWidgetPrefs.key(7))
        assertEquals("b7.ring", BuddyWidgetPrefs.ringKey(7))
        assertEquals("b7.anim", BuddyWidgetPrefs.animKey(7))
    }

    @Test fun ringColourPerMode() {
        val pick: (String) -> Int = { 0xFF000000.toInt() or (BuddyRing.PICKS.indexOf(it) + 1) }
        assertNull(BuddyRing.APP.solid(0xFFEE8130.toInt(), pick))                          // follows the app's goal colours
        assertEquals(0xFFEE8130.toInt(), BuddyRing(BuddyRing.Mode.TYPE).solid(0xFFEE8130.toInt(), pick))
        assertNull(BuddyRing(BuddyRing.Mode.TYPE).solid(null, pick))                      // type unknown: app colours
        assertEquals(0xFF000003.toInt(), BuddyRing(BuddyRing.Mode.PICK, "pink").solid(0xFFEE8130.toInt(), pick))
    }
}
