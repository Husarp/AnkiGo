package com.husarp.ankigo.game

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Round 10, W9: what the move choice does to the battles. The same session as RoundBalanceTest (current RoundRule + WILD_POWER,
 * equal-level made-up Pokémon, PLAN 80 healing, 4000 battles) played with RANDOM / SMART / SMARTEST on both sides.
 * Fixtures: "attacks only" = RoundBalanceTest's own (every move damages, so only the immunity rule can change anything);
 * "starter-like" = two attacks and two debuffs (Growl, Leer, Sand Attack) on each side, what a young Pokémon really has;
 * "stress" = two attacks and two other status moves on each side, with a stat buff (Swords Dance), a heal, sleep, poison and
 * paralysis in the mix. Prints the tables the report quotes.
 */
class MoveChoiceBalanceTest {
    private val base = TestData.world()
    private val data = GameData(
        base.species,
        TestData.MOVES + listOf(
            Move("sandattack", "Sand Attack", Type.NORMAL, Category.STATUS, 0, 100, boosts = mapOf("accuracy" to -1)),
            Move("leer", "Leer", Type.NORMAL, Category.STATUS, 0, 100, boosts = mapOf("def" to -1)),
        ).associateBy { it.id },
        base.growth, base.itemPrices,
    )

    private class Fixture(val name: String, val main: List<String>, val wilds: List<Pair<Int, List<String>>>)

    private val attacksOnly = Fixture(
        "attacks only (RoundBalanceTest's)", listOf("tackle", "thunderbolt", "quickattack"),
        listOf(16 to listOf("tackle", "quickattack"), 19 to listOf("tackle", "quickattack"), 54 to listOf("watergun", "tackle"),
            1 to listOf("tackle", "vinewhip"), 133 to listOf("tackle", "bodyslam"), 25 to listOf("tackle", "thunderbolt")),
    )

    private val starter = Fixture(
        "starter-like: 2 attacks + 2 debuffs", listOf("tackle", "thunderbolt", "growl", "leer"),
        listOf(16 to listOf("tackle", "quickattack", "growl", "sandattack"), 19 to listOf("tackle", "quickattack", "growl", "leer"),
            54 to listOf("watergun", "tackle", "growl", "leer"), 1 to listOf("tackle", "vinewhip", "growl", "sandattack"),
            133 to listOf("tackle", "bodyslam", "growl", "sandattack"), 25 to listOf("tackle", "thunderbolt", "growl", "leer")),
    )

    private val mixed = Fixture(
        "stress: 2 attacks + buff/heal/status moves", listOf("tackle", "thunderbolt", "growl", "swordsdance"),
        listOf(16 to listOf("tackle", "quickattack", "growl", "sandattack"), 19 to listOf("tackle", "quickattack", "growl", "swordsdance"),
            54 to listOf("watergun", "tackle", "toxic", "growl"), 1 to listOf("tackle", "vinewhip", "hypnosis", "growl"),
            133 to listOf("tackle", "bodyslam", "recover", "growl"), 25 to listOf("tackle", "thunderbolt", "thunderwave", "growl")),
    )

    private class Result(val battles: Int, val wins: Int, val faints: Int, val rounds: Int, val hpAtStart: Double) {
        val winRate get() = wins.toDouble() / battles
        val faintsPer100 get() = 100.0 * faints / battles
        val roundsPerBattle get() = rounds.toDouble() / battles
        override fun toString() = "win ${"%5.1f".format(100 * winRate)}%, faints/100 ${"%5.1f".format(faintsPer100)}, " +
            "rounds/battle ${"%5.2f".format(roundsPerBattle)}, main HP at start ${"%3.0f".format(100 * hpAtStart)}%"
    }

    /** Buttons 1..4 by percent (Again, Hard, Good, Easy). */
    private fun pick(r: Random, mix: IntArray): Int {
        var x = r.nextInt(100)
        for (b in 1..4) { x -= mix[b - 1]; if (x < 0) return b }
        return 3
    }

    private fun session(f: Fixture, choice: MoveChoice, mix: IntArray, level: Int, battles: Int, seed: Int): Result {
        val r = Random(seed)
        val main = TestData.mon(data, 25, level, f.main, uid = "main")
        val maxMain = StatCalc.maxHp(data, main)
        var wins = 0; var faints = 0; var rounds = 0; var hpSum = 0.0
        repeat(battles) {
            hpSum += main.hp.toDouble() / maxMain
            val (sp, moves) = f.wilds[r.nextInt(f.wilds.size)]
            val wild = TestData.mon(data, sp, level, moves, uid = "wild")
            val b = Battle(wild, Tier.NORMAL)
            var n = 0
            while (wild.hp > 0 && main.hp > 0 && n < 300) {
                val plan = RoundRule.plan(listOf(pick(r, mix), pick(r, mix)))
                BattleEngine.playRound(data, b, main, plan.multiplier, plan.enemyActs, r, plan.enemyPower, plan.mainActs, choice)
                n++
            }
            rounds += n
            if (wild.hp <= 0) { wins++; Healing.healMain(data, main, HealReason.DEFEAT, ArrayList()) }
            else if (main.hp <= 0) { faints++; main.hp = maxMain }
        }
        return Result(battles, wins, faints, rounds, hpSum / battles)
    }

    private fun all(f: Fixture, mix: IntArray, level: Int, battles: Int = 4000): Map<MoveChoice, Result> =
        MoveChoice.entries.associateWith { session(f, it, mix, level, battles, 7) }

    private fun print(title: String, rs: Map<MoveChoice, Result>) {
        println("MOVECHOICE $title\n" + rs.entries.joinToString("\n") { "  ${it.key.label.padEnd(9)} ${it.value}" })
    }

    /** Loose sanity: the smarter choices win about as often as Random (within [slack]) and fights are not longer than 10 % more rounds. */
    private fun sane(what: String, rs: Map<MoveChoice, Result>, slack: Double = 0.03) {
        val random = rs.getValue(MoveChoice.RANDOM)
        for (c in listOf(MoveChoice.SMART, MoveChoice.SMARTEST)) {
            val r = rs.getValue(c)
            assertTrue("$what $c win rate ${r.winRate} vs random ${random.winRate}", r.winRate >= random.winRate - slack)
            assertTrue("$what $c rounds/battle ${r.roundsPerBattle} vs random ${random.roundsPerBattle}", r.roundsPerBattle <= random.roundsPerBattle * 1.10)
        }
    }

    @Test fun realisticMixOnTheAttacksOnlyFixtures() {
        val mix = intArrayOf(10, 15, 65, 10) // 10 % Again, 15 % Hard, 65 % Good, 10 % Easy
        for (level in listOf(10, 30, 60)) {
            val rs = all(attacksOnly, mix, level)
            print("${attacksOnly.name}, realistic mix (10/15/65/10), Lv $level", rs)
            sane("attacks only Lv $level", rs)
        }
    }

    @Test fun realisticMixWithTwoAttacksAndTwoDebuffs() {
        // Smart / Smartest keep the win rate within about 4 points of Random (the fights get about 40 % shorter, so they are swingier,
        // and the Main, which acts in more rounds than the wild one, uses fewer debuffs than with Random), so the bound is 5 points here
        val mix = intArrayOf(10, 15, 65, 10)
        for (level in listOf(10, 30, 60)) {
            val rs = all(starter, mix, level)
            print("${starter.name}, realistic mix (10/15/65/10), Lv $level", rs)
            sane("starter-like Lv $level", rs, slack = 0.05)
        }
    }

    @Test fun realisticMixWithBuffsHealsAndStatusMoves() {
        // the stress case: Smart uses Swords Dance and the other status moves a fifth of the time, so the Main gives up some of its
        // buffing (about 3 to 4 points of win rate) while the fights get about a third shorter
        val mix = intArrayOf(10, 15, 65, 10)
        for (level in listOf(10, 30, 60)) {
            val rs = all(mixed, mix, level)
            print("${mixed.name}, realistic mix (10/15/65/10), Lv $level", rs)
            sane("stress Lv $level", rs, slack = 0.05)
        }
    }

    @Test fun roughMixAtLevelThirty() {
        val mix = intArrayOf(20, 25, 45, 10)
        for (f in listOf(attacksOnly, starter, mixed)) {
            val rs = all(f, mix, 30)
            print("${f.name}, rough mix (20/25/45/10), Lv 30", rs)
            // a bad day may be harder than before (see RoundBalanceTest); the choice must not make it worse than the random one by much
            for (c in listOf(MoveChoice.SMART, MoveChoice.SMARTEST))
                assertTrue("${f.name} $c rough ${rs.getValue(c).winRate} vs random ${rs.getValue(MoveChoice.RANDOM).winRate}",
                    rs.getValue(c).winRate >= rs.getValue(MoveChoice.RANDOM).winRate - 0.05)
        }
    }
}
