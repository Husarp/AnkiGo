package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalGlowTest {
    @Test fun reachedAtOneHundredPercentAndAbove() {
        assertFalse(GoalGlow.reached(0f))
        assertFalse(GoalGlow.reached(0.99f))
        assertTrue(GoalGlow.reached(1f))
        assertTrue(GoalGlow.reached(2.3f))
        assertFalse(GoalGlow.reached(Float.NaN))
    }

    @Test fun glowShowsExactlyWhereTheSparksDo() {
        for (pct in listOf(0, 50, 99, 100, 112, 150, 199, 200, 300)) {
            val level = GoalTone.flameLevel(pct / 100f)
            assertEquals("$pct %", GoalSparks.count(level) > 0, GoalGlow.shown(level))
            assertEquals("$pct %", pct >= 100, GoalGlow.shown(level))
        }
    }

    @Test fun sizesAndAlphaFollowTheMockup() {
        assertEquals(0f, GoalGlow.outlineAlpha(0), 0f)
        assertEquals(0.40f, GoalGlow.outlineAlpha(1), 1e-5f)
        assertEquals(0.55f, GoalGlow.outlineAlpha(2), 1e-5f)
        assertEquals(0.70f, GoalGlow.outlineAlpha(3), 1e-5f)
        assertEquals(0f, GoalGlow.blur(0), 0f)
        assertEquals(8f, GoalGlow.blur(1), 0f)
        assertEquals(12f, GoalGlow.blur(2), 0f)
        assertEquals(16f, GoalGlow.blur(3), 0f)
        assertEquals(18f, GoalGlow.extent(3), 0f)
        assertEquals(GoalGlow.outlineAlpha(3), GoalGlow.outlineAlpha(9), 0f)   // clamped
    }

    @Test fun noRingsWithoutAGlow() {
        assertTrue(GoalGlow.rings(0).isEmpty())
        assertTrue(GoalGlow.rings(-3).isEmpty())
        assertTrue(GoalGlow.rings(2, 0f).isEmpty())
        assertTrue(GoalGlow.rings(2, -4f).isEmpty())
    }

    @Test fun ringsAreContiguousAndFadeOutward() {
        for (lv in 1..3) {
            val rings = GoalGlow.rings(lv)
            val outline = rings.first()
            assertEquals(GoalGlow.OUTLINE_COLOR, outline.color)
            assertEquals(GoalGlow.OUTLINE_WIDTH, outline.width, 0f)
            assertEquals(GoalGlow.outlineAlpha(lv), outline.alpha, 0f)
            var edge = outline.outset + outline.width / 2f
            assertEquals(GoalGlow.OUTLINE_WIDTH, edge, 1e-5f)
            var prevAlpha = outline.alpha
            for (g in rings.drop(1)) {
                assertEquals(GoalGlow.HALO_COLOR, g.color)
                assertEquals(edge, g.outset - g.width / 2f, 1e-4f)   // starts where the last one ended
                edge = g.outset + g.width / 2f
                assertTrue(g.alpha < prevAlpha && g.alpha > 0f)
                prevAlpha = g.alpha
            }
            assertEquals(GoalGlow.extent(lv), edge, 1e-4f)
        }
    }

    @Test fun roomCapsTheReach() {
        for (cap in listOf(1f, 2f, 5f, 9.5f, 12f)) {
            val rings = GoalGlow.rings(3, cap)
            val reach = rings.maxOf { it.outset + it.width / 2f }
            assertEquals(minOf(cap, GoalGlow.extent(3)), reach, 1e-4f)
            assertTrue(rings.all { it.outset - it.width / 2f >= -1e-5f })
        }
        assertEquals(1, GoalGlow.rings(3, 2f).size)                    // just the outline
        assertEquals(1f, GoalGlow.rings(3, 1f).first().width, 1e-5f)   // narrower outline
        assertEquals(GoalGlow.rings(3).size, GoalGlow.rings(3, 500f).size)
    }

    @Test fun thickBarIsAboutOnePointSevenFiveTimesTheBattleBar() {
        assertEquals(14, GoalGlow.THICK_BAR_DP)
        assertEquals(1.75f, GoalGlow.THICK_BAR_DP / GoalSparks.BAR_DP, 0f)
    }
}
