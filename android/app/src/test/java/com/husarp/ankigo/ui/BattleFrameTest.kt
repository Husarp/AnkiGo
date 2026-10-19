package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R9 D2: the battle frame styles match the mockup's `FR` table (docs/design/round9.html), spot-checked cell by cell. */
class BattleFrameTest {
    private fun c(v: Long) = v.toInt()

    @Test fun simple() {
        val s = BattleFrameStyle.SIMPLE
        assertEquals(2, s.t); assertEquals(2, s.reach)
        assertEquals(c(0xFF1B1B26), s.edge(0, 0, 't'))
        assertEquals(c(0x99FFFFFF), s.edge(5, 1, 'l'))
        assertEquals(0, s.edge(5, 2, 't'))
        assertFalse(s.hasCorner)
    }

    @Test fun wood() {
        val s = BattleFrameStyle.WOOD
        assertEquals(3, s.t); assertEquals(3, s.reach)
        assertEquals(c(0xFF2B180B), s.edge(3, 0, 't'))
        assertEquals(c(0xFF4A2E14), s.edge(3, 2, 'b'))
        assertEquals(c(0xFF6E4520), s.edge(0, 1, 't'))    // i % 9 == 0
        assertEquals(c(0xFF6E4520), s.edge(5, 1, 't'))    // i % 13 == 5
        assertEquals(c(0xFF97632F), s.edge(1, 1, 't'))    // odd
        assertEquals(c(0xFF8A5A2B), s.edge(2, 1, 't'))    // even
        assertEquals(0, s.edge(2, 3, 't'))
        assertEquals(c(0xFF2B180B), s.cornerAt(0, 0))
        assertEquals(c(0xFFB8892E), s.cornerAt(1, 1))
        assertEquals(c(0xFFF3D27A), s.cornerAt(2, 2))
        assertEquals(c(0xFF7A5A16), s.cornerAt(3, 2))
        assertEquals(c(0xFF2B180B), s.cornerAt(5, 5))
        assertEquals(0, s.cornerAt(6, 0))                 // outside the 6 x 6 pattern
    }

    @Test fun stone() {
        val s = BattleFrameStyle.STONE
        assertEquals(c(0xFF33343B), s.edge(4, 0, 'r'))
        assertEquals(c(0xFF5B5D66), s.edge(0, 1, 't'))    // mortar: (i + 0) % 6 == 0
        assertEquals(c(0xFFACAEB8), s.edge(1, 1, 't'))
        assertEquals(c(0xFF5B5D66), s.edge(3, 2, 't'))    // mortar of the second course: (i + 3) % 6 == 0
        assertEquals(c(0xFF8B8D97), s.edge(0, 2, 't'))
        assertEquals(0, s.edge(0, 3, 't'))
        assertEquals(c(0xFFA6A8B3), s.cornerAt(2, 2))
        assertEquals(c(0xFF6B6D77), s.cornerAt(4, 4))
        assertEquals(c(0xFFD0D2DB), s.cornerAt(1, 4))
    }

    @Test fun gold() {
        val s = BattleFrameStyle.GOLD
        assertEquals(c(0xFF5A3D06), s.edge(7, 0, 'b'))
        assertEquals(c(0xFFFFF3B8), s.edge(6, 1, 't'))    // a shine every sixth
        assertEquals(c(0xFFF2C94C), s.edge(7, 1, 't'))
        assertEquals(c(0xFFB8860B), s.edge(7, 2, 't'))
        assertEquals(7, s.corner.size)
        assertEquals(c(0xFFFFFFFF), s.cornerAt(2, 2))
        assertEquals(c(0xFFE0245E), s.cornerAt(3, 3))
        assertEquals(c(0xFF8C1238), s.cornerAt(4, 3))
        assertEquals(c(0xFF5A3D06), s.cornerAt(6, 6))
    }

    @Test fun iceIciclesHangFromTheTopOnly() {
        val s = BattleFrameStyle.ICE
        assertEquals(2, s.t); assertEquals(6, s.reach)
        assertEquals(c(0xFF2F5F7C), s.edge(0, 0, 't'))
        assertEquals(c(0xFFFFFFFF), s.edge(9, 1, 't'))
        assertEquals(c(0xFFBFE9FF), s.edge(1, 1, 't'))
        // column 2 has an icicle 3 long: two pale cells, then the white tip
        assertEquals(c(0xFFDFF6FF), s.edge(2, 2, 't'))
        assertEquals(c(0xFFDFF6FF), s.edge(2, 3, 't'))
        assertEquals(c(0xFFFFFFFF), s.edge(2, 4, 't'))
        assertEquals(0, s.edge(2, 5, 't'))
        assertEquals(c(0xFFFFFFFF), s.edge(3, 2, 't'))    // length 1: just the tip
        assertEquals(c(0xFFFFFFFF), s.edge(8, 5, 't'))    // length 4 reaches depth 5
        assertEquals(0, s.edge(0, 2, 't'))                // length 0
        for (side in "blr") assertEquals("side $side", 0, s.edge(2, 2, side))
        assertEquals(0, s.edge(2, 6, 't'))                // beyond the reach
        assertEquals(c(0xFF2F5F7C), s.cornerAt(4, 0))
        assertEquals(0, s.cornerAt(5, 0))                 // '.' is transparent
        assertEquals(0, s.cornerAt(0, 6))
    }

    @Test fun metal() {
        val s = BattleFrameStyle.METAL
        assertEquals(c(0xFF0E1015), s.edge(0, 0, 't'))
        assertEquals(c(0xFFC5CCD9), s.edge(6, 1, 't'))    // rivet
        assertEquals(c(0xFF7D8596), s.edge(5, 1, 't'))
        assertEquals(c(0xFF7D8596), s.edge(7, 1, 't'))
        assertEquals(c(0xFFC5CCD9), s.edge(18, 1, 't'))   // the pattern repeats every 12
        assertEquals(c(0xFF4A5060), s.edge(0, 1, 't'))
        assertEquals(c(0xFF23262F), s.edge(0, 2, 't'))
        assertEquals(c(0xFFD5DBE6), s.cornerAt(2, 2))
        assertEquals(c(0xFF3A3F4B), s.cornerAt(3, 2))
    }

    @Test fun noneDrawsNothing() {
        val s = BattleFrameStyle.NONE
        assertEquals(0, s.t); assertEquals(0, s.reach)
        assertFalse(s.hasCorner)
        for (d in 0..3) for (i in 0..20) for (side in "tblr") assertEquals(0, s.edge(i, d, side))
        assertEquals(0, s.cornerAt(0, 0))
    }

    @Test fun everyEdgeCellLiesInsideTheReachAndEveryStyleHasItsOwnPixels() {
        for (s in BattleFrameStyle.entries) {
            if (s == BattleFrameStyle.NONE) continue
            assertTrue("${s.name} reach covers the thickness", s.reach >= s.t)
            for (d in 0 until s.reach) assertTrue("${s.name} depth $d has a cell", (0 until 40).any { s.edge(it, d, 't') != 0 })
            assertEquals("${s.name} nothing past the reach", 0, s.edge(3, s.reach, 't'))
            assertTrue("${s.name} has a label and a sub line", s.label.isNotBlank() && s.sub.isNotBlank())
        }
    }

    @Test fun savedNameParsesAndUnknownMeansNone() {
        for (s in BattleFrameStyle.entries) assertEquals(s, BattleFrameStyle.parse(s.name))
        assertEquals(BattleFrameStyle.NONE, BattleFrameStyle.parse(null))
        assertEquals(BattleFrameStyle.NONE, BattleFrameStyle.parse(""))
        assertEquals(BattleFrameStyle.NONE, BattleFrameStyle.parse("LEAF"))
        assertEquals(BattleFrameStyle.NONE, BattleFrameStyle.parse("wood"))
    }

    @Test fun theLeafFrameIsNotOffered() {
        assertEquals(listOf("NONE", "SIMPLE", "WOOD", "STONE", "GOLD", "ICE", "METAL"), BattleFrameStyle.entries.map { it.name })
    }

    @Test fun oneArtPixelIsTwoDpOnScreen() {
        assertEquals(6, BattleFrame.unitPx(3f, 1f))                 // 2 dp x 3 px/dp
        assertEquals(12, BattleFrame.unitPx(3f, 0.5f))              // the arena is drawn at half size: twice as many virtual px
        assertEquals(2, BattleFrame.unitPx(1f, 2f))                 // never below 2 px
        assertEquals(2, BattleFrame.unitPx(1f, 1f))
        assertEquals(5, BattleFrame.unitPx(2.625f, 1f))
    }
}
