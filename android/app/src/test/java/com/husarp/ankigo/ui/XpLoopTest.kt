package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R14 PLAN 138 + 182: the pure runs of the XP bar loop (main bar and side card). */
class XpLoopTest {
    private fun s(level: Int, from: Float, to: Float, toEnd: Boolean) = XpLoop.Seg(level, from, to, toEnd)

    @Test fun noLevelIsOneRun() {
        assertEquals(listOf(s(7, 0.2f, 0.6f, false)), XpLoop.segments(7, 0.2f, 7, 0.6f))
    }

    @Test fun oneLevelFillsToTheEndThenRestartsFromEmpty() {
        assertEquals(listOf(s(7, 0.8f, 1f, true), s(8, 0f, 0.2f, false)), XpLoop.segments(7, 0.8f, 8, 0.2f))
    }

    @Test fun threeLevelsMakeOneFullRunPerLevelInBetween() {
        assertEquals(
            listOf(s(7, 0.5f, 1f, true), s(8, 0f, 1f, true), s(9, 0f, 1f, true), s(10, 0f, 0.3f, false)),
            XpLoop.segments(7, 0.5f, 10, 0.3f))
    }

    @Test fun aGainThatLandsExactlyOnALevelEndsEmptyInTheNewLevel() {
        val r = XpLoop.segments(7, 0.9f, 8, 0f)
        assertEquals(listOf(s(7, 0.9f, 1f, true), s(8, 0f, 0f, false)), r)
        assertEquals(0f, r.last().length, 0f)
    }

    @Test fun zeroXpIsAnEmptyRunAndNeverGoesBackwards() {
        assertEquals(listOf(s(7, 0.4f, 0.4f, false)), XpLoop.segments(7, 0.4f, 7, 0.4f))
        assertEquals(listOf(s(7, 0.4f, 0.4f, false)), XpLoop.segments(7, 0.4f, 7, 0.1f))
        // a to-level below the from-level counts as no level
        assertEquals(listOf(s(7, 0.4f, 0.9f, false)), XpLoop.segments(7, 0.4f, 6, 0.9f))
    }

    @Test fun neededZeroAtMaxLevelEndsOnAFullBar() {
        // toFrac 1 (the max level has no XP to go): the last run fills it
        assertEquals(listOf(s(99, 0.5f, 1f, true), s(100, 0f, 1f, false)), XpLoop.segments(99, 0.5f, 100, 1f))
    }

    @Test fun fractionsAreClamped() {
        assertEquals(listOf(s(7, 0f, 1f, false)), XpLoop.segments(7, -1f, 7, 5f))
    }

    @Test fun onlyTheLastRunIsNotToEnd() {
        val r = XpLoop.segments(1, 0.1f, 5, 0.5f)
        assertEquals(5, r.size)
        assertTrue(r.dropLast(1).all { it.toEnd }); assertFalse(r.last().toEnd)
        assertEquals((1..5).toList(), r.map { it.level })
    }

    @Test fun aRunTakesItsShareOfTheFullTimeAtOneSpeed() {
        assertEquals(0L, XpLoop.runMs(0f))
        assertEquals(XpLoop.FULL_MS, XpLoop.runMs(1f))
        assertEquals(XpLoop.MIN_MS, XpLoop.runMs(0.01f))
        assertTrue(XpLoop.runMs(0.5f) < XpLoop.runMs(1f) && XpLoop.runMs(0.5f) > XpLoop.runMs(0.2f))
    }

    @Test fun needsGivesOneNumberPerLevelAndFillsTheUnknownOnes() {
        assertEquals(listOf(90, 100), XpLoop.needs(1, listOf(90), 100))
        assertEquals(listOf(90, 95, 100, 120), XpLoop.needs(3, listOf(90, 95, 100), 120))
        assertEquals(listOf(120, 120, 120), XpLoop.needs(2, emptyList(), 120))
        assertEquals(listOf(120), XpLoop.needs(0, emptyList(), 120))
    }
}
