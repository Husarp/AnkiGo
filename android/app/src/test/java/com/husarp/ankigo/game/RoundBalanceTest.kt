package com.husarp.ankigo.game

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Round 10, B22: balance check of "who attacks, by answer" against the old point-sum rule, with a realistic answer mix and
 * equal-level Pokémon (made-up fixtures). The healing rules of PLAN 80 apply (10 % of max HP after a win, a fainted main is
 * healed at once and the wild one lost); level-ups, bench rest and items are left out, so a long session is the hard case.
 */
class RoundBalanceTest {
    private val data = TestData.world()

    private class Result(val battles: Int, val wins: Int, val faints: Int, val rounds: Int, val hpAtStart: Double) {
        val winRate get() = wins.toDouble() / battles
        val faintsPer100 get() = 100.0 * faints / battles
        val roundsPerBattle get() = rounds.toDouble() / battles
        override fun toString() = "win ${"%.1f".format(100 * winRate)}%, faints/100 battles ${"%.1f".format(faintsPer100)}, " +
            "rounds/battle ${"%.2f".format(roundsPerBattle)}, main HP at battle start ${"%.0f".format(100 * hpAtStart)}%"
    }

    /** Buttons 1..4 by percent (Again, Hard, Good, Easy). */
    private fun pick(r: Random, mix: IntArray): Int {
        var x = r.nextInt(100)
        for (b in 1..4) { x -= mix[b - 1]; if (x < 0) return b }
        return 3
    }

    private fun oldRule(a: Int, b: Int): RoundRule.Plan {
        val m = (Tuning.POINTS[a] + Tuning.POINTS[b]) / 20.0
        return RoundRule.Plan(mainActs = true, multiplier = m, enemyActs = m < 1.0, enemyPower = 1.0)
    }

    private fun session(rule: (Int, Int) -> RoundRule.Plan, mix: IntArray, level: Int, battles: Int, seed: Int): Result {
        val r = Random(seed)
        val main = TestData.mon(data, 25, level, listOf("tackle", "thunderbolt", "quickattack"), uid = "main")
        val maxMain = StatCalc.maxHp(data, main)
        // average fixtures (a glass cannon like the ghost one would only add noise): the same kind as the main, plus others of a similar total
        val wilds = listOf(16 to listOf("tackle", "quickattack"), 19 to listOf("tackle", "quickattack"), 54 to listOf("watergun", "tackle"),
            1 to listOf("tackle", "vinewhip"), 133 to listOf("tackle", "bodyslam"), 25 to listOf("tackle", "thunderbolt"))
        var wins = 0; var faints = 0; var rounds = 0; var hpSum = 0.0
        repeat(battles) {
            hpSum += main.hp.toDouble() / maxMain
            val (sp, moves) = wilds[r.nextInt(wilds.size)]
            val wild = TestData.mon(data, sp, level, moves, uid = "wild")
            val b = Battle(wild, Tier.NORMAL)
            var n = 0
            while (wild.hp > 0 && main.hp > 0 && n < 300) {
                val plan = rule(pick(r, mix), pick(r, mix))
                BattleEngine.playRound(data, b, main, plan.multiplier, plan.enemyActs, r, plan.enemyPower, plan.mainActs)
                n++
            }
            rounds += n
            if (wild.hp <= 0) { wins++; Healing.healMain(data, main, HealReason.DEFEAT, ArrayList()) }
            else if (main.hp <= 0) { faints++; main.hp = maxMain }
        }
        return Result(battles, wins, faints, rounds, hpSum / battles)
    }

    private fun both(mix: IntArray, level: Int, battles: Int = 4000): Pair<Result, Result> {
        val old = session(::oldRule, mix, level, battles, 7)
        val new = session({ a, b -> RoundRule.plan(listOf(a, b)) }, mix, level, battles, 7)
        return old to new
    }

    @Test fun realisticMixStillWinsMostBattlesAndDoesNotDrainTheMain() {
        val mix = intArrayOf(10, 15, 65, 10) // 10 % Again, 15 % Hard, 65 % Good, 10 % Easy
        for (level in listOf(10, 30, 60)) {
            val (old, new) = both(mix, level)
            println("BALANCE realistic mix (10/15/65/10), Lv $level equal-level\n  before: $old\n  after:  $new")
            assertTrue("win rate before ${old.winRate} after ${new.winRate}", new.winRate >= 0.65 && new.winRate >= old.winRate - 0.02)
            assertTrue("faints before ${old.faintsPer100} after ${new.faintsPer100}", new.faintsPer100 <= old.faintsPer100 + 2.0)
            assertTrue("main HP at battle start before ${old.hpAtStart} after ${new.hpAtStart}", new.hpAtStart >= 0.50 && new.hpAtStart >= old.hpAtStart - 0.10)
            // the price is time: the Main sits out rounds without a Good/Easy answer, so a fight takes a few more rounds
            assertTrue("rounds/battle before ${old.roundsPerBattle} after ${new.roundsPerBattle}", new.roundsPerBattle <= old.roundsPerBattle * 1.4)
        }
    }

    @Test fun aRoughSessionIsHarderButStillWinnable() {
        val mix = intArrayOf(20, 25, 45, 10) // a bad day
        val (old, new) = both(mix, 30)
        println("BALANCE rough mix (20/25/45/10), Lv 30\n  before: $old\n  after:  $new")
        // Adam 2026-10-19: equal power on both sides (Again 1 / Hard 0.5 vs Good 0.5 / Easy 1) — a bad day may be harder than before
        assertTrue("win rate before ${old.winRate} after ${new.winRate}", new.winRate >= 0.35)
    }

    @Test fun anAllGoodSessionIsSafeAsBefore() {
        val (old, new) = both(intArrayOf(0, 0, 90, 10), 30, 1000)
        println("BALANCE all good/easy, Lv 30\n  before: $old\n  after:  $new")
        assertTrue(new.faints == 0 && old.faints == 0)
    }
}
