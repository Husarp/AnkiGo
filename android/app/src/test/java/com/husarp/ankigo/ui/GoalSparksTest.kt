package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalSparksTest {
    private fun levelAt(percent: Int) = GoalTone.flameLevel(percent / 100f)
    private fun sparksAt(percent: Int) = GoalSparks.count(levelAt(percent))

    @Test fun sparkCountFollowsTheGoalFraction() {
        assertEquals(0, sparksAt(0))
        assertEquals(0, sparksAt(80))
        assertEquals(0, sparksAt(99))
        assertEquals(3, sparksAt(100))
        assertEquals(3, sparksAt(112))
        assertEquals(3, sparksAt(149))
        assertEquals(6, sparksAt(150))
        assertEquals(6, sparksAt(165))
        assertEquals(10, sparksAt(200))
        assertEquals(10, sparksAt(230))
    }

    @Test fun levelsOutsideTheRangeAreClamped() {
        assertEquals(0, GoalSparks.count(-1))
        assertEquals(10, GoalSparks.count(7))
        assertTrue(GoalSparks.frame(0, 1234L).isEmpty())
        assertEquals(10, GoalSparks.frame(9, 1234L).size)
    }

    @Test fun framesHaveTheLevelsCount() {
        for (lv in 0..3) {
            assertEquals(GoalSparks.count(lv), GoalSparks.frame(lv, 5000L).size)
            assertEquals(GoalSparks.count(lv), GoalSparks.frame(lv, null).size)
        }
    }

    @Test fun positionsStayInsideTheBarWidthAndFollowTheGoldenStep() {
        for (lv in 1..3) for (t in listOf(0L, 333L, 12_345L, 3_000_000_000L)) {
            val f = GoalSparks.frame(lv, t)
            f.forEachIndexed { i, s ->
                assertTrue(s.x >= 0f && s.x < 1f)
                assertEquals(((i * 0.618) % 1.0).toFloat(), s.x, 1e-4f)
            }
        }
    }

    @Test fun riseAndAlphaStayInRangeAndFadeAsTheySpark() {
        for (lv in 1..3) {
            val up = 10f + 5f * lv
            assertEquals(up, GoalSparks.maxRise(lv), 0f)
            for (t in 0L..20_000L step 137L) for (s in GoalSparks.frame(lv, t)) {
                assertTrue(s.rise >= 0f && s.rise <= up)
                assertTrue(s.alpha > 0f && s.alpha <= 1f)
                assertEquals(1f - s.rise / up, s.alpha, 1e-4f)
                assertTrue(s.radius >= 1.2f && s.radius <= GoalSparks.MAX_RADIUS)
            }
        }
        assertEquals(0f, GoalSparks.maxRise(0), 0f)
    }

    @Test fun sparksRiseOverTime() {
        // Spark 0 has phase q = t/1000 * 0.8 (mod 1): at 250 ms it is 0.2 of the way, at 500 ms 0.4.
        val a = GoalSparks.frame(1, 250L)[0]
        val b = GoalSparks.frame(1, 500L)[0]
        assertEquals(0.2f * 15f, a.rise, 1e-3f)
        assertEquals(0.4f * 15f, b.rise, 1e-3f)
        assertTrue(b.alpha < a.alpha)
    }

    @Test fun aCycleWrapsBackToTheBar() {
        // 1250 ms is one full cycle of spark 0 (speed 0.8): a little later it is back at the bar, bright again.
        val s = GoalSparks.frame(2, 1260L)[0]
        assertEquals(0.2f, s.rise, 0.05f)
        assertEquals(0.99f, s.alpha, 0.01f)
    }

    @Test fun stillFrameIsTheSameEveryTimeAndUsesFixedPhases() {
        val a = GoalSparks.frame(3, null)
        val b = GoalSparks.frame(3, null)
        assertEquals(a.map { it.rise }, b.map { it.rise })
        assertEquals(a.map { it.alpha }, b.map { it.alpha })
        a.forEachIndexed { i, s ->
            val q = (2.0 * i / 10) % 1.0
            assertEquals((q * 25.0).toFloat(), s.rise, 1e-3f)
        }
        // not all on the same height: the still picture looks like a scatter of sparks
        assertTrue(a.map { (it.rise * 100).toInt() }.toSet().size > 3)
    }

    @Test fun sizesAndColoursAlternate() {
        val f = GoalSparks.frame(3, 777L)
        f.forEachIndexed { i, s ->
            assertEquals(if (i % 2 == 1) 0xFFFFD447.toInt() else 0xFFFB8F2B.toInt(), s.color)
            assertEquals(1.2f + 0.4f * ((i * 0.3) % 1.0).toFloat(), s.radius, 1e-4f)
        }
    }

    @Test fun tickerKeepsGoingOnlyWhileAllowed() {
        assertTrue(GoalSparks.keepTicking(2, attached = true, shown = true, windowVisible = true, reducedMotion = false))
        assertEquals(false, GoalSparks.keepTicking(0, true, true, true, false))
        assertEquals(false, GoalSparks.keepTicking(2, false, true, true, false))
        assertEquals(false, GoalSparks.keepTicking(2, true, false, true, false))
        assertEquals(false, GoalSparks.keepTicking(2, true, true, false, false))
        assertEquals(false, GoalSparks.keepTicking(2, true, true, true, true))
    }
}
