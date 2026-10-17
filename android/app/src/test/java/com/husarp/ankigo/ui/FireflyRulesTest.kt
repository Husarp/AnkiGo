package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FireflyRulesTest {
    private val flies = FireflyRules.FLIES

    @Test fun thereAreTwentyToThirtyOnTheirOwnPixels() {
        assertTrue(flies.size in 20..30)
        for (f in flies) {
            assertTrue(f.x in 0f..1f && f.y in 0f..1f)
            assertTrue("${f.wide}x${f.tall}", f.wide in 1..2 && f.tall in 1..2)
        }
    }

    @Test fun theSetIsTheSameEveryTime() {
        assertEquals(flies.map { it.x }, FireflyRules.FLIES.map { it.x })
        assertTrue(flies.map { it.x }.toSet().size > 20) // spread out, not stacked
    }

    @Test fun alphaStaysInRangeAndTwinklesSlowly() {
        for (f in flies) {
            var lo = 1f; var hi = 0f; var maxStep = 0f; var prev = FireflyRules.alpha(f, 0)
            var t = 0L
            while (t <= 14_000) {
                val a = FireflyRules.alpha(f, t)
                assertTrue(a in 0f..1f)
                lo = minOf(lo, a); hi = maxOf(hi, a); maxStep = maxOf(maxStep, kotlin.math.abs(a - prev)); prev = a
                t += FireflyRules.FRAME_MS
            }
            assertEquals(0f, lo, 0.001f)   // goes dark
            assertEquals(1f, hi, 0.05f)    // and fully lit
            assertTrue("jumps $maxStep per frame", maxStep < 0.2f) // fades, never flashes
        }
    }

    @Test fun phasesDiffer() {
        val at = flies.map { FireflyRules.alpha(it, 1234) }
        assertTrue(at.toSet().size > 10)
    }

    @Test fun stillLookNeedsNoClockAndShowsEveryDot() {
        for (f in flies) assertTrue(FireflyRules.still(f) in 0.45f..1f)
    }
}
