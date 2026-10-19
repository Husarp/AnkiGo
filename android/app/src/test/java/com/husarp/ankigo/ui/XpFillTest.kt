package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R9-C8 (the XP bar grows with the orbs) and the landing picks of R9-C3 (the soft absorb pling), pure maths. */
class XpFillTest {
    private val plan = XpParticles.plan(XpParticles.MOCK_SEED)
    private fun landed(t: Float) = plan.sparks.count { it.land <= t }

    @Test fun fillStartsAtZeroAndEndsExactlyAtOne() {
        assertEquals(0f, XpParticles.fill(plan, 0f), 0f)
        assertEquals(0f, XpParticles.fill(plan, plan.firstLanding - XpParticles.FILL_EASE_MS * plan.factor - 1f), 0f)
        assertEquals(1f, XpParticles.fill(plan, plan.lastLanding), 0f)
        assertTrue(XpParticles.fill(plan, plan.lastLanding - 20f) < 1f)
        assertEquals(1f, XpParticles.fill(plan, plan.end), 0f)
        assertEquals(1f, XpParticles.fill(plan, plan.end + 5000f), 0f)
    }

    @Test fun fillNeverGoesDownAndStepsWithTheLandings() {
        var prev = 0f
        var t = 0f
        while (t <= plan.end) {
            val f = XpParticles.fill(plan, t)
            assertTrue("t=$t $f < $prev", f >= prev)
            // never behind the orbs that have landed, never more than one ease ahead of them
            assertTrue("t=$t fill $f behind landed ${landed(t)}", f >= landed(t) / 16f - 1e-4f)
            assertTrue("t=$t fill $f too far ahead ${landed(t + XpParticles.FILL_EASE_MS)}", f <= landed(t + XpParticles.FILL_EASE_MS) / 16f + 1e-4f)
            prev = f; t += 7f
        }
    }

    @Test fun fillFollowsThePaceFactor() {
        val fast = XpParticles.plan(XpParticles.MOCK_SEED, 0.5f)
        assertEquals(1f, XpParticles.fill(fast, fast.lastLanding), 0f)
        assertTrue(XpParticles.fill(fast, fast.lastLanding - 10f) < 1f)
    }

    @Test fun anOrbAddsOneSixteenthOfTheGrowth() {
        val s = plan.sparks.sortedBy { it.land }
        assertEquals(1f / 16f, XpParticles.fill(plan, s[0].land).coerceAtMost(1f / 16f + 1e-4f), 1e-3f)
    }

    // ---- the bar's fraction and label ----

    @Test fun aNormalGainGrowsFromTheOldFractionToTheTargetExactly() {
        val f = XpFill(0.2f, 640, 1000, false)
        assertEquals(0.2f, f.frac(0f), 0f)
        assertEquals(0.64f, f.frac(1f), 0f)
        assertEquals(640, f.current(1f))
        var prevFrac = 0f; var prevCur = -1
        for (i in 0..100) {
            val fill = i / 100f
            assertTrue(f.frac(fill) >= prevFrac && f.current(fill) >= prevCur)
            assertTrue(f.frac(fill) in 0.2f..0.64f)
            prevFrac = f.frac(fill); prevCur = f.current(fill)
        }
        assertEquals(420, f.current(0.5f))   // 0.42 of 1000
    }

    @Test fun aLevelUpFillsToOneHundredPercentWithTheOrbs() {
        val f = XpFill(0.7f, 120, 900, true)
        assertEquals(0.7f, f.frac(0f), 0f)
        assertEquals(1f, f.frac(1f), 0f)
        assertEquals(900, f.current(1f))   // the label ends full; the restart at 120 is the event player's
        assertTrue(f.frac(0.5f) in 0.7f..1f)
    }

    @Test fun noNeedMeansAFullBarAndFillOutsideZeroToOneIsClamped() {
        val f = XpFill(0f, 0, 0, false)
        assertEquals(1f, f.frac(1f), 0f)
        val g = XpFill(0.1f, 50, 100, false)
        assertEquals(0.1f, g.frac(-3f), 0f)
        assertEquals(0.5f, g.frac(9f), 0f)
    }

    @Test fun theFillOfARealPlanDrivesTheBarMonotonicallyToTheTarget() {
        val f = XpFill(0.35f, 777, 1000, false)
        var prev = 0f
        var t = 0f
        while (t <= plan.end + 50f) {
            val frac = f.frac(XpParticles.fill(plan, t))
            assertTrue(frac >= prev); prev = frac; t += 11f
        }
        assertEquals(0.777f, prev, 0f)
    }

    // ---- which landings get a pling ----

    @Test fun picksAreSpacedAndCountedForTheDefaultPlan() {
        val picks = XpParticles.soundPicks(plan)
        assertTrue("count ${picks.size}", picks.size in 6..12)
        for (i in 1 until picks.size) assertTrue("gap ${picks[i].atMs - picks[i - 1].atMs}", picks[i].atMs - picks[i - 1].atMs >= XpParticles.SOUND_GAP_MS)
        assertEquals(plan.lastLanding, picks.last().atMs, 0f)
        assertEquals(1, picks.count { it.last })
        assertTrue(picks.last().last)
        assertTrue(picks.all { it.rate in 0.9f..1.25f })
        assertEquals(plan.firstLanding, picks.first().atMs, 0f)
    }

    @Test fun picksAreDeterministicAndKeepTheirRulesForManySeedsAndPaces() {
        assertEquals(XpParticles.soundPicks(plan).map { it.atMs to it.rate }, XpParticles.soundPicks(XpParticles.plan(XpParticles.MOCK_SEED)).map { it.atMs to it.rate })
        val gaps = ArrayList<Float>()
        for (seed in 1L..60L) for (factor in listOf(1f, 0.5f)) {
            val p = XpParticles.plan(seed, factor)
            val picks = XpParticles.soundPicks(p)
            assertTrue("seed $seed x$factor count ${picks.size}", picks.size in (if (factor == 1f) 5..13 else 3..11))
            for (i in 1 until picks.size) assertTrue("seed $seed gap", picks[i].atMs - picks[i - 1].atMs >= XpParticles.SOUND_GAP_MS)
            gaps += (1 until picks.size).map { picks[it].atMs - picks[it - 1].atMs }
            assertEquals(p.lastLanding, picks.last().atMs, 0f)
            assertTrue(picks.last().last && picks.dropLast(1).none { it.last })
        }
        // R9-W4-FIX: the ripple is twice as fast as before (the pick gap's median was about 202 ms at pace 1; now about 101 ms at pace 1 and 75 ms at pace 0.5, over 400 seeds, 6-13 plings)
        gaps.sort()
        assertTrue("median gap ${gaps[gaps.size / 2]}", gaps[gaps.size / 2] in 60f..115f)
    }
}
