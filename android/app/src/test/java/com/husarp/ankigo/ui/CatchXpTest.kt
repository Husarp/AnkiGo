package com.husarp.ankigo.ui

import com.husarp.ankigo.game.CashKind
import com.husarp.ankigo.game.DuplicateResult
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** R9-W6 CX-A: the burst out of the catch ball (plan timings, ball glow / fade, number spot) and the look-ahead for the XpGained of a catch. */
class CatchXpTest {
    private val ours = "m"
    private fun xp(uid: String = ours, n: Int = 40) = GameEvent.XpGained(uid, "Mainy", n, 7, 10, 100)
    private val caught = GameEvent.Caught("w1", 147, "Wildling", 12, false, null, true, true)
    private val counted = GameEvent.Counted(3, 10, 6, 1, false)
    private val encounter = GameEvent.Encounter(1, "Next", 5, false, Tier.NORMAL, 10, 10, false, false, "u2")

    // ---------------------------------------------------------------- plan

    @Test fun burstLaunchesInsideTheFirstSixHundredMilliseconds() {
        for (seed in 1L..60L) {
            val p = XpParticles.plan(seed, burst = true)
            assertEquals(16, p.sparks.size)
            assertTrue("first ${p.firstLaunch}", p.firstLaunch >= XpParticles.BALL_FROM)
            assertTrue("last ${p.lastLaunch}", p.lastLaunch <= XpParticles.BALL_FROM + XpParticles.BALL_SPAN)
            for (s in p.sparks) assertTrue("flight ${s.dur}", s.dur in 750f..1100f)   // same flight times as today
        }
    }

    @Test fun burstKeepsTheMockupsRandomOrder() {
        val today = XpParticles.plan(XpParticles.MOCK_SEED)
        val burst = XpParticles.plan(XpParticles.MOCK_SEED, burst = true)
        for (i in 0 until 16) {
            val a = today.sparks[i]; val b = burst.sparks[i]
            assertEquals(a.dur, b.dur, 0f); assertEquals(a.a1, b.a1, 0f); assertEquals(a.r2, b.r2, 0f); assertEquals(a.ez, b.ez, 0f); assertEquals(a.size, b.size, 0f)
            assertEquals(a.sx, b.sx, 0f); assertEquals(a.ey, b.ey, 0f)
        }
    }

    @Test fun theDefaultPlanIsUnchanged() {
        val p = XpParticles.plan(XpParticles.MOCK_SEED)
        assertEquals(380f, p.popFrom, 0f); assertEquals(640f, p.popTo, 0f)
        assertTrue(p.firstLaunch >= 520f && p.lastLaunch < 1520f)
        val slow = XpParticles.plan(XpParticles.MOCK_SEED, 0.5f)
        assertEquals(190f, slow.popFrom, 0f); assertEquals(320f, slow.popTo, 0f)
    }

    @Test fun theBallFadesAfterTheLastLaunchAndTheBannerAfterTheLastLanding() {
        val p = XpParticles.plan(XpParticles.MOCK_SEED, burst = true)
        assertEquals(p.lastLaunch + 100f, p.ballFadeFrom, 0.001f)
        assertEquals(300f, p.ballFadeMs, 0f)
        assertEquals(p.lastLanding + 200f, p.bannerEnd, 0.001f)
        assertEquals(p.bannerEnd - 250f, p.bannerFadeFrom, 0.001f)
        assertTrue("the ball is gone before the banner", p.ballFadeFrom + p.ballFadeMs <= p.bannerEnd)
        assertEquals(1f, XpParticles.ballAlpha(p, p.ballFadeFrom - 1f), 0f)
        assertEquals(0f, XpParticles.ballAlpha(p, p.ballFadeFrom + p.ballFadeMs), 0f)
        assertEquals(0.5f, XpParticles.ballAlpha(p, p.ballFadeFrom + 150f), 0.001f)
    }

    @Test fun theGlowGoesDownWhileTheOrbsLeave() {
        val p = XpParticles.plan(XpParticles.MOCK_SEED, burst = true)
        assertEquals(1f, XpParticles.ballGlow(p, 0f), 0f)
        assertTrue(XpParticles.ballGlow(p, p.lastLaunch / 2f) in 0.45f..0.55f)
        assertEquals(0f, XpParticles.ballGlow(p, p.lastLaunch), 0f)
        assertEquals(0f, XpParticles.ballGlow(p, p.lastLaunch + 500f), 0f)
        assertEquals(0f, XpParticles.ballGlow(p, -5f), 0f)
    }

    @Test fun noGapTheWholeCatchTakesAboutFourAndAHalfSeconds() {
        val click = 2250f
        for (seed in 1L..40L) {
            val p = XpParticles.plan(seed, burst = true)
            assertTrue("first orb leaves with the click (${p.firstLaunch})", p.firstLaunch <= 100f)
            // today: the XP event starts when the ball is gone (3.9 s), its first orb leaves 520 ms later and the last lands ~1.3 s after the ball
            val total = click + p.end
            assertTrue("total $total", total < 5000f)
            assertTrue("banner end ${click + p.bannerEnd}", click + p.bannerEnd < 4500f)
        }
        val p = XpParticles.plan(XpParticles.MOCK_SEED, burst = true)
        assertEquals(4.5f, (click + p.end) / 1000f, 0.4f)
    }

    @Test fun theNumberPopsBeforeTheFirstLaunchAndCountsOverTheBall() {
        val p = XpParticles.plan(XpParticles.MOCK_SEED, burst = true)
        assertEquals(p.firstLaunch - 140f, p.popFrom, 0.001f)
        assertEquals(p.popFrom + 260f, p.popTo, 0.001f)
        assertTrue(XpParticles.numberAlpha(p, 0f) > 0f)           // already coming in at the click
        assertEquals(0, XpParticles.count(p, p.firstLaunch, 100))
        assertEquals(100, XpParticles.count(p, p.countTo, 100))
        assertEquals(0f, XpParticles.numberAlpha(p, p.fadeTo), 0f)
        val ball = XpParticles.Box(180f, 200f, 48f, 48f)
        val (x, y) = XpParticles.numberSpot(ball, 412f, 40f, 12f, 2f, lift = 12f)
        assertEquals(ball.cx, x, 0f); assertEquals(188f, y, 0f)   // 12 above the ball's top
        assertEquals(14f, XpParticles.numberSpot(XpParticles.Box(180f, 10f, 48f, 48f), 412f, 40f, 12f, 2f, lift = 12f).second, 0f)   // kept inside the arena
    }

    // ---------------------------------------------------------------- look-ahead

    @Test fun findsThePlayersXpBehindBadgesASwapAndAHeal() {
        val mine = xp()
        val between = listOf<GameEvent?>(
            GameEvent.BadgeEarned(7, "First catch"), GameEvent.Duplicate("Wildling", 12, 10, DuplicateResult.SWAPPED, releasedName = "Old"),
            GameEvent.Healed("m", "Mainy", 5, 20, 30, com.husarp.ankigo.game.HealReason.CATCH), GameEvent.Cash(10, 20, CashKind.DEFEAT), null, mine, xp("other"), encounter)
        assertSame(mine, CatchXp.find(between, ours))
    }

    @Test fun skipsAnotherPokemonsXpAndTakesTheMainsLater() {
        val mine = xp()
        assertSame(mine, CatchXp.find(listOf(xp("holder", 20), mine), ours))
        assertNull(CatchXp.find(listOf(xp("holder", 20), encounter), ours))
    }

    @Test fun noXpMeansNothingIsFound() {
        assertNull(CatchXp.find(emptyList(), ours))
        assertNull(CatchXp.find(listOf(GameEvent.Cash(10, 20, CashKind.DEFEAT), encounter), ours))
        assertNull(CatchXp.find(listOf(null, null), ours))
    }

    @Test fun neverReachesIntoTheNextAnswerOrFight() {
        assertNull(CatchXp.find(listOf(counted, xp()), ours))
        assertNull(CatchXp.find(listOf(encounter, xp()), ours))
        assertNull(CatchXp.find(listOf(GameEvent.Defeated(1, "Other", 30), xp()), ours))
        assertNull(CatchXp.find(listOf(caught, xp()), ours))
        assertNull(CatchXp.find(listOf(xp()), null))
    }

    @Test fun theLaterStepOnlyWaitsForWhatIsLeft() {
        assertEquals(700L, CatchXp.remaining(1700L, 1000L))
        assertEquals(0L, CatchXp.remaining(1000L, 1700L))
        assertEquals(0L, CatchXp.remaining(1000L, 1000L))
        assertNotNull(caught)
    }
}
