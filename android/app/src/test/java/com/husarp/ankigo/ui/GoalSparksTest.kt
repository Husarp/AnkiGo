package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalSparksTest {
    private fun levelAt(percent: Int) = GoalTone.flameLevel(percent / 100f)
    private fun sparksAt(percent: Int) = GoalSparks.count(levelAt(percent))

    @Test fun celebrationStaysSparseAtEveryReachedLevel() {
        assertEquals(0, sparksAt(99))
        for (pct in listOf(100, 150, 200, 500)) {
            val frame = GoalSparks.frame(levelAt(pct), 777L)
            assertTrue(frame.size in 3..5)
            assertTrue(frame.all { it.shape in listOf(0, 5, 6) })
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
                assertTrue(s.radius in (if (s.shape == 0) 1.17f..1.43f else 2.16f..2.64f))
                assertEquals(255, s.color ushr 24)
            }
        }
        val first = GoalSparks.frame(1, 1000L).first()
        val later = GoalSparks.frame(1, 1100L).single { it.x == first.x }
        assertTrue(later.rise - first.rise in (15f * 100 / 7750)..(15f * 100 / 6400))
    }

    @Test fun reducedMotionHasADeterministicVisibleMix() {
        val a = GoalSparks.frame(3, null)
        val b = GoalSparks.frame(3, null)
        assertEquals(a.map { it.rise }, b.map { it.rise })
        assertEquals(a.map { it.alpha }, b.map { it.alpha })
        assertEquals(a.size, a.map { it.rise }.toSet().size)
        assertTrue(a.all { it.alpha > 0f })
    }

    @Test fun birthsAreSeededAcrossTheWholeWidthAndOffTheTimeGrid() {
        val times = (0L..1000L).map { GoalSparks.spawnTime(it) }
        val gaps = times.zipWithNext { a, b -> b - a }
        assertTrue(gaps.toSet().size > 500)
        assertTrue(gaps.all { it in 530..3010 })
        assertEquals(1770.0, gaps.average(), 3.0)
        assertTrue(times.map { it % GoalSparks.SPAWN_INTERVAL_MS }.toSet().size > 500)
        val births = times.map { t -> GoalSparks.frame(1, t + 1).minBy { it.rise } }
        assertTrue((0..9).all { bin -> births.any { (it.x * 10).toInt() == bin } })
        assertTrue(births.map { it.x }.toSet().size > 990)
        assertTrue(births.minOf { it.x } < .01f && births.maxOf { it.x } > .99f)
        assertEquals(births.map { it.x }, times.map { t -> GoalSparks.frame(1, t + 1).minBy { it.rise }.x })
        assertTrue(births.map { it.color }.toSet().size >= 4)
        for (shape in listOf(0, 5, 6)) {
            val sizes = births.filter { it.shape == shape }.map { it.radius }
            assertTrue(sizes.toSet().size > 50)
        }
        val counts = (0L..60_000L step 100).map { GoalSparks.frame(1, it).size }
        assertTrue(counts.toSet().size > 1)
        assertTrue(counts.all { it in 2..5 })
        assertEquals(4.0, counts.average(), .3)
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
