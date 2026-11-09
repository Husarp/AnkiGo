package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalSparksTest {
    @Test fun liveFramesReuseParticlesWithoutSharingThemBetweenBarsOrSnapshots() {
        val live = GoalSparks.MutableFrame()
        val first = live.at(1, 0L)
        val particles = first.toList()
        val snapshot = GoalSparks.frame(1, 0L)
        val other = GoalSparks.MutableFrame().at(1, 0L)
        for (level in 1..3) for (time in listOf(333L, 6400L, 3_000_000_000L, null)) {
            val next = live.at(level, time)
            val expected = GoalSparks.frame(level, time)
            org.junit.Assert.assertSame(first, next)
            next.forEachIndexed { i, spark ->
                org.junit.Assert.assertSame(particles[i], spark)
                assertEquals(expected[i].rise, spark.rise, 0f)
                assertEquals(expected[i].alpha, spark.alpha, 0f)
                assertEquals(expected[i].rotation, spark.rotation, 0f)
                org.junit.Assert.assertNotSame(other[i], spark)
            }
        }
        assertEquals(0f, snapshot[0].rise, 0f)
        assertEquals(0f, other[0].rise, 0f)
        assertTrue(live.at(0, 100L).isEmpty())
        org.junit.Assert.assertSame(first, live.at(3, 100L))
    }

    private fun levelAt(percent: Int) = GoalTone.flameLevel(percent / 100f)
    private fun sparksAt(percent: Int) = GoalSparks.count(levelAt(percent))

    @Test fun celebrationStaysSparseAtEveryReachedLevel() {
        assertEquals(0, sparksAt(99))
        for (pct in listOf(100, 150, 200, 500)) {
            val frame = GoalSparks.frame(levelAt(pct), 777L)
            assertEquals(4, frame.size)
            assertEquals(3, frame.count { it.shape == 5 || it.shape == 6 })
            assertEquals(1, frame.count { it.shape == 0 })
        }
        assertTrue(GoalSparks.frame(0, 777L).isEmpty())
        assertEquals(4, GoalSparks.count(9))
        assertEquals(0, GoalSparks.count(-1))
    }

    @Test fun slowDriftStaysWithinBoundsEvenAfterLongUptime() {
        for (lv in 1..3) for (t in listOf(0L, 333L, 6400L, 3_000_000_000L)) {
            for (s in GoalSparks.frame(lv, t)) {
                assertTrue(s.x in 0f..1f)
                assertTrue(s.rise in 0f..GoalSparks.maxRise(lv))
                assertTrue(s.alpha in 0f..0.65f)
                assertTrue(s.radius in 1.2f..GoalSparks.MAX_RADIUS)
                assertTrue(s.color == GoalSparks.COLOR_ODD || s.color == GoalSparks.COLOR_EVEN)
            }
        }
        assertEquals(GoalSparks.frame(1, 0L)[0].rise, GoalSparks.frame(1, 6400L)[0].rise, .001f)
        assertTrue(GoalSparks.frame(1, 1000L)[0].rise < GoalSparks.frame(1, 2000L)[0].rise)
    }

    @Test fun reducedMotionHasADeterministicVisibleMix() {
        val a = GoalSparks.frame(3, null)
        val b = GoalSparks.frame(3, null)
        assertEquals(a.map { it.rise }, b.map { it.rise })
        assertEquals(a.map { it.alpha }, b.map { it.alpha })
        assertEquals(4, a.map { it.rise }.toSet().size)
        assertTrue(a.all { it.alpha > 0f })
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
