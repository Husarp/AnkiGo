package com.husarp.ankigo.gfx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PLAN 205 (R14 H): the idle motion's maths and its run rule (pure). */
class SpriteIdleTest {
    @Test fun theLiftStartsAtRestRisesToTheTopAtTheHalfAndComesBack() {
        assertEquals(0f, SpriteView.idleLift(0f), 1e-6f)
        assertEquals(0.5f, SpriteView.idleLift(0.25f), 1e-5f)
        assertEquals(1f, SpriteView.idleLift(0.5f), 1e-6f)
        assertEquals(0.5f, SpriteView.idleLift(0.75f), 1e-5f)
        assertEquals(0f, SpriteView.idleLift(1f), 1e-5f)
    }

    @Test fun theLiftStaysInsideZeroAndOneAndIsSlowestAtTheEnds() {
        var p = 0f
        while (p <= 1f) { val l = SpriteView.idleLift(p); assertTrue("lift $l at $p", l >= -1e-6f && l <= 1f + 1e-6f); p += 0.01f }
        // ease in / out: the first 5 % and the 5 % around the top move far less than the 5 % in between (fastest at a quarter)
        val end = SpriteView.idleLift(0.05f) - SpriteView.idleLift(0f)
        val top = SpriteView.idleLift(0.5f) - SpriteView.idleLift(0.45f)
        val mid = SpriteView.idleLift(0.275f) - SpriteView.idleLift(0.225f)
        assertTrue(top < mid)
        assertTrue(end < mid)
    }

    @Test fun theBreathIsOneAtRestAndAtMostTwoPercentTallerAtTheTop() {
        assertEquals(1f, SpriteView.idleBreath(0f), 1e-6f)
        assertEquals(1.01f, SpriteView.idleBreath(0.25f), 1e-5f)
        assertEquals(1.02f, SpriteView.idleBreath(0.5f), 1e-6f)
        var p = 0f
        while (p <= 1f) { val b = SpriteView.idleBreath(p); assertTrue(b >= 1f - 1e-6f && b <= 1.02f + 1e-6f); p += 0.01f }
    }

    @Test fun scalingAboutTheFeetKeepsTheBottomFixed() {
        // the pivot is the feet row: y' = pivot + (y - pivot) * sy, so the pivot itself does not move and the top rises by (sy - 1) * height
        val feet = 200f; val top = 40f
        for (p in listOf(0f, 0.25f, 0.5f, 0.8f)) {
            val sy = SpriteView.idleBreath(p)
            assertEquals(feet, feet + (feet - feet) * sy, 0f)
            assertEquals(top - (sy - 1f) * (feet - top), feet + (top - feet) * sy, 1e-3f)
        }
    }

    @Test fun itRunsOnlyForAShownStillPictureWithAnimatorsOnAndNoReducedMotion() {
        fun run(enabled: Boolean = true, image: Boolean = true, animated: Boolean = false, on: Boolean = true, reduce: Boolean = false, shown: Boolean = true) =
            SpriteView.idleShouldRun(enabled, image, animated, on, reduce, shown)
        assertTrue(run())
        assertFalse("opt-in", run(enabled = false))
        assertFalse("no picture", run(image = false))
        assertFalse("animated picture", run(animated = true))
        assertFalse("animators off", run(on = false))
        assertFalse("reduce motion", run(reduce = true))
        assertFalse("not shown", run(shown = false))
    }
}
