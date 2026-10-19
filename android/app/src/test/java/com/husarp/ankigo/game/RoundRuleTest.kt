package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Round 10, B22: who attacks in a round, by answer (RoundRule + BattleEngine.playRound). */
class RoundRuleTest {
    private val data = TestData.world()
    private val AGAIN = 1; private val HARD = 2; private val GOOD = 3; private val EASY = 4

    private fun plan(vararg b: Int) = RoundRule.plan(b.toList())

    @Test fun badAnswersMakeTheWildOneAttackScaledByEachAnswer() {
        assertEquals(2.0, plan(AGAIN, AGAIN).enemyPower, 1e-9)
        assertEquals(1.5, plan(AGAIN, HARD).enemyPower, 1e-9)
        assertEquals(1.0, plan(HARD, HARD).enemyPower, 1e-9)
        assertEquals(1.0, plan(AGAIN, GOOD).enemyPower, 1e-9)
        assertEquals(1.0, plan(AGAIN, EASY).enemyPower, 1e-9)
        assertEquals(0.5, plan(HARD, GOOD).enemyPower, 1e-9)
        assertEquals(0.5, plan(HARD, EASY).enemyPower, 1e-9)
        for (p in listOf(plan(AGAIN, AGAIN), plan(AGAIN, HARD), plan(HARD, HARD), plan(AGAIN, GOOD), plan(HARD, EASY))) assertTrue(p.enemyActs)
    }

    @Test fun goodAndEasyAnswersMakeTheMainAttackWithTheirSum() {
        assertEquals(1.0, plan(GOOD, GOOD).multiplier, 1e-9)
        assertEquals(1.5, plan(GOOD, EASY).multiplier, 1e-9)
        assertEquals(2.0, plan(EASY, EASY).multiplier, 1e-9)
        assertEquals(0.5, plan(GOOD, AGAIN).multiplier, 1e-9)
        assertEquals(0.5, plan(HARD, GOOD).multiplier, 1e-9)
        assertEquals(1.0, plan(AGAIN, EASY).multiplier, 1e-9)
        for (p in listOf(plan(GOOD, GOOD), plan(GOOD, EASY), plan(EASY, EASY), plan(GOOD, AGAIN), plan(HARD, EASY))) assertTrue(p.mainActs)
    }

    @Test fun noGoodOrEasyMeansTheMainDoesNotAttackAndNoBadAnswerMeansTheWildOneDoesNot() {
        for (p in listOf(plan(AGAIN, AGAIN), plan(AGAIN, HARD), plan(HARD, HARD))) { assertFalse(p.mainActs); assertEquals(0.0, p.multiplier, 0.0) }
        for (p in listOf(plan(GOOD, GOOD), plan(GOOD, EASY), plan(EASY, EASY))) { assertFalse(p.enemyActs); assertEquals(0.0, p.enemyPower, 0.0) }
    }

    @Test fun theLoopholeIsClosedAgainPlusEasyNowHurts() {
        val p = plan(AGAIN, EASY)
        assertTrue(p.enemyActs && p.mainActs) // old rule: sum 20 = multiplier 1 = the wild one stayed quiet
    }

    @Test fun anUnknownButtonCountsAsGoodAndHardIsMilderThanAgain() {
        assertEquals(plan(GOOD, GOOD), plan(0, GOOD))
        assertEquals(plan(GOOD, GOOD), plan(0, 0))
        assertTrue(Tuning.WILD_POWER[HARD] < Tuning.WILD_POWER[AGAIN] / 1.5) // clearly milder
    }

    @Test fun restoreGivesTheExactButtonOfASingleOldAnswer() {
        assertEquals(listOf(AGAIN), RoundRule.restore(1, 0))
        assertEquals(listOf(HARD), RoundRule.restore(1, 5))
        assertEquals(listOf(GOOD), RoundRule.restore(1, 10))
        assertEquals(listOf(EASY), RoundRule.restore(1, 20))
        assertEquals(emptyList<Int>(), RoundRule.restore(0, 0))
    }

    // ---- the engine ----

    private fun mon(sp: Int, lv: Int, vararg moves: String) = TestData.mon(data, sp, lv, moves.toList())

    private fun play(plan: RoundRule.Plan, seed: Int = 1, main: Mon = mon(19, 20, "tackle"), wild: Mon = mon(150, 40, "tackle")): Pair<List<GameEvent>, Mon> {
        val b = Battle(wild, Tier.NORMAL)
        val ev = BattleEngine.playRound(data, b, main, plan.multiplier, plan.enemyActs, Random(seed), plan.enemyPower, plan.mainActs)
        return ev to wild
    }

    @Test fun aRoundWithoutGoodOrEasyHasNoMainAttackAndNoOneHpHit() {
        val (ev, wild) = play(plan(AGAIN, AGAIN))
        assertTrue(ev.filterIsInstance<GameEvent.Attack>().none { it.byPlayer })
        assertEquals(StatCalc.maxHp(data, wild), wild.hp)
        assertTrue(ev.filterIsInstance<GameEvent.Attack>().any { !it.byPlayer })
        assertTrue(ev.filterIsInstance<GameEvent.Splash>().isEmpty())
    }

    @Test fun aSafeRoundHasOnlyTheMainAttackAndTheWildSplash() {
        val (ev, _) = play(plan(GOOD, EASY))
        val attacks = ev.filterIsInstance<GameEvent.Attack>()
        assertEquals(1, attacks.size); assertTrue(attacks.single().byPlayer)
        assertEquals(1, ev.filterIsInstance<GameEvent.Splash>().size)
    }

    @Test fun mixedRoundBothAttackInSpeedOrder() {
        val (ev, _) = play(plan(HARD, EASY), main = mon(19, 20, "tackle"), wild = mon(150, 40, "tackle"))
        val attacks = ev.filterIsInstance<GameEvent.Attack>()
        assertEquals(listOf(false, true), attacks.map { it.byPlayer }) // the faster wild one first, as before
        assertTrue(ev.filterIsInstance<GameEvent.Splash>().isEmpty())
        val (ev2, _) = play(plan(HARD, EASY), main = mon(150, 60, "tackle"), wild = mon(150, 50, "tackle")) // the main is faster and the wild one survives
        assertEquals(listOf(true, false), ev2.filterIsInstance<GameEvent.Attack>().map { it.byPlayer })
    }

    @Test fun priorityStillDecidesTheOrderWhenBothAttack() {
        val (ev, _) = play(plan(AGAIN, EASY), main = mon(19, 20, "quickattack"), wild = mon(150, 40, "tackle"))
        assertEquals(listOf(true, false), ev.filterIsInstance<GameEvent.Attack>().map { it.byPlayer })
    }

    @Test fun theWildDamageScalesWithThePowerAndNeverBelowOne() {
        for (seed in 1..6) {
            val dmg = listOf(1.0, 0.6, 0.35).map { power ->
                val main = mon(150, 60, "tackle") // plenty of HP: the hit is never cut short
                val b = Battle(mon(150, 40, "tackle"), Tier.NORMAL)
                BattleEngine.playRound(data, b, main, 1.0, true, Random(seed), power, false)
                    .filterIsInstance<GameEvent.Attack>().single { !it.byPlayer }.damage
            }
            assertTrue("$dmg", dmg[0] > dmg[1] && dmg[1] > dmg[2] && dmg[2] >= 1)
            assertEquals(Math.floor(dmg[0] * 0.6).toInt().coerceAtLeast(1), dmg[1])
        }
        val tiny = Battle(mon(19, 1, "tackle"), Tier.NORMAL)
        val hit = BattleEngine.playRound(data, tiny, mon(150, 40, "tackle"), 1.0, true, Random(1), 0.01, false)
            .filterIsInstance<GameEvent.Attack>().single { !it.byPlayer }
        assertEquals(1, hit.damage)
    }

    @Test fun theDefaultsKeepTheOldCallsWorking() {
        val b = Battle(mon(150, 40, "tackle"), Tier.NORMAL)
        val ev = BattleEngine.playRound(data, b, mon(150, 60, "tackle"), 0.0, true, Random(1)) // old call: the main always attacks
        val own = ev.filterIsInstance<GameEvent.Attack>().single { it.byPlayer }
        assertEquals(1, own.damage)
        assertNotNull(ev.filterIsInstance<GameEvent.Attack>().singleOrNull { !it.byPlayer })
    }
}
