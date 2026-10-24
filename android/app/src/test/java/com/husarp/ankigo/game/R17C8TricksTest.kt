package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C8.attacks
import com.husarp.ankigo.game.R17C8.high
import com.husarp.ankigo.game.R17C8.lines
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R17C-8: stat tricks: Psych Up, the Swaps and Splits, Power Trick / Power Shift, Topsy-Turvy, Acupressure, Venom Drench, Rototiller, Flower
 * Shield, Charge, Defense Curl, Minimize, Tar Shot. The wild one idles in most tests ([rd] with `wildActs = false`).
 */
class R17C8TricksTest {
    private val data = R17C8.data
    private fun main(vararg m: String) = R17C8.main(*m)
    private fun wild(vararg m: String, sp: Int = 801) = R17C8.wild(*m, sp = sp)
    private fun rd(main: Mon, b: Battle, rng: Random = Random(1), wildActs: Boolean = false) = R17C8.round(main, b, rng, enemyActs = wildActs)
    private fun <T> forced(id: String? = null, wildId: String? = null, block: () -> T): T =
        R17C8.withRules(*listOfNotNull(id?.let { R17C8.forcing(it) }, wildId?.let { R17C8.forcingWild(it) }).toTypedArray(), block = block)
    private fun boosts(vararg v: Int) = intArrayOf(*v)
    private fun List<GameEvent>.failedMain() = attacks().single { it.byPlayer }.failed

    /** A hit of [move] by the Main (or by the wild one) for reading the rule hooks. */
    private fun hit(m: Mon, b: Battle, move: String, byMain: Boolean = true): Hit {
        val v = R17C8.view(m, b)
        val mv = data.move(move)!!
        return if (byMain) BattleEngine.newHit(v, true, m, b.player, b.wild, b.enemy, mv) else BattleEngine.newHit(v, false, b.wild, b.enemy, m, b.player, mv)
    }

    // ---------------------------------------------------------------- stages

    @Test fun psychUpCopiesTheTargetsStatChanges() {
        val m = main("tackle"); val b = R17C8.battle(wild("tackle"))
        b.player.boosts[1] = 1
        boosts(2, 0, -1, 0, 3, 0, 0).copyInto(b.enemy.boosts)
        val e = forced("psychup") { rd(m, b) }
        assertArrayEquals(boosts(2, 0, -1, 0, 3, 0, 0), b.player.boosts)
        assertArrayEquals(boosts(2, 0, -1, 0, 3, 0, 0), b.enemy.boosts)
        assertTrue("Fastie copied Slowpoke's stat changes!" in e.lines())
    }

    @Test fun heartSwapSwapsAllStatChanges() {
        val m = main("tackle"); val b = R17C8.battle(wild("tackle"))
        boosts(1, 0, 0, 0, 0, 0, 0).copyInto(b.player.boosts)
        boosts(0, 2, 0, 0, 0, 0, -1).copyInto(b.enemy.boosts)
        forced("heartswap") { rd(m, b) }
        assertArrayEquals(boosts(0, 2, 0, 0, 0, 0, -1), b.player.boosts)
        assertArrayEquals(boosts(1, 0, 0, 0, 0, 0, 0), b.enemy.boosts)
    }

    @Test fun powerSwapAndGuardSwapSwapOnlyTheirOwnStages() {
        val m = main("tackle"); val b = R17C8.battle(wild("tackle"))
        boosts(1, 2, 3, 4, 5, 0, 0).copyInto(b.player.boosts)
        boosts(-1, -2, -3, -4, -5, 0, 0).copyInto(b.enemy.boosts)
        forced("powerswap") { rd(m, b) }
        assertArrayEquals(boosts(-1, 2, -3, 4, 5, 0, 0), b.player.boosts) // Attack and Sp. Atk
        assertArrayEquals(boosts(1, -2, 3, -4, -5, 0, 0), b.enemy.boosts)
        forced("guardswap") { rd(m, b) }
        assertArrayEquals(boosts(-1, -2, -3, -4, 5, 0, 0), b.player.boosts) // Defense and Sp. Def
        assertArrayEquals(boosts(1, 2, 3, 4, -5, 0, 0), b.enemy.boosts)
    }

    @Test fun topsyTurvyTurnsTheTargetsStatChangesAround() {
        val m = main("tackle"); val b = R17C8.battle(wild("tackle"))
        boosts(2, -1, 0, 0, 3, 1, -2).copyInto(b.enemy.boosts)
        val e = forced("topsyturvy") { rd(m, b) }
        assertArrayEquals(boosts(-2, 1, 0, 0, -3, -1, 2), b.enemy.boosts)
        assertTrue("All of Slowpoke's stat changes were inverted!" in e.lines())
        assertTrue("nothing to turn around", forced("topsyturvy") { rd(m, R17C8.battle(wild("tackle"))) }.failedMain())
    }

    @Test fun acupressureRaisesOneRandomStatByTwo() {
        for (seed in 1..12) {
            val m = main("tackle"); val b = R17C8.battle(wild("tackle"))
            forced("acupressure") { rd(m, b, Random(seed)) }
            assertEquals("seed $seed: ${b.player.boosts.toList()}", 2, b.player.boosts.sum())
            assertEquals(1, b.player.boosts.count { it == 2 })
        }
        val m = main("tackle"); val b = R17C8.battle(wild("tackle"))
        boosts(6, 6, 6, 6, 6, 6, 5).copyInto(b.player.boosts)
        forced("acupressure") { rd(m, b) }
        assertEquals(6, b.player.boosts[6]) // the only stat that could still rise
        assertTrue(forced("acupressure") { rd(m, b) }.failedMain()) // everything at +6: it fails
    }

    @Test fun venomDrenchLowersThreeStatsOfAPoisonedTarget() {
        for (status in listOf("psn", "tox")) {
            val m = main("tackle"); val b = R17C8.battle(wild("tackle")); b.enemy.status = status
            forced("venomdrench") { rd(m, b) }
            assertArrayEquals(boosts(-1, 0, -1, 0, -1, 0, 0), b.enemy.boosts)
        }
        val m = main("tackle"); val b = R17C8.battle(wild("tackle"))
        assertTrue(forced("venomdrench") { rd(m, b) }.failedMain())
        assertArrayEquals(boosts(0, 0, 0, 0, 0, 0, 0), b.enemy.boosts)
    }

    @Test fun rototillerRaisesGrassTypesOnTheGroundOnBothSides() {
        val m = R17C8.mon(812, 50, "tackle"); val b = R17C8.battle(R17C8.mon(803, 100, "tackle")) // two Grass types
        forced("rototiller") { rd(m, b) }
        assertArrayEquals(boosts(1, 0, 1, 0, 0, 0, 0), b.player.boosts)
        assertArrayEquals(boosts(1, 0, 1, 0, 0, 0, 0), b.enemy.boosts)
        // a flying Grass type is not on the ground
        val m2 = R17C8.mon(812, 50, "tackle"); val b2 = R17C8.battle(R17C8.mon(813, 100, "tackle"))
        forced("rototiller") { rd(m2, b2) }
        assertArrayEquals(boosts(1, 0, 1, 0, 0, 0, 0), b2.player.boosts)
        assertArrayEquals(boosts(0, 0, 0, 0, 0, 0, 0), b2.enemy.boosts)
        // no Grass type at all: it fails
        assertTrue(forced("rototiller") { rd(main("tackle"), R17C8.battle(wild("tackle"))) }.failedMain())
    }

    @Test fun flowerShieldRaisesTheDefenseOfEveryGrassTypeEvenInTheAir() {
        val m = R17C8.mon(812, 50, "tackle"); val b = R17C8.battle(R17C8.mon(813, 100, "tackle"))
        forced("flowershield") { rd(m, b) }
        assertArrayEquals(boosts(0, 1, 0, 0, 0, 0, 0), b.player.boosts)
        assertArrayEquals(boosts(0, 1, 0, 0, 0, 0, 0), b.enemy.boosts)
        assertTrue(forced("flowershield") { rd(main("tackle"), R17C8.battle(wild("tackle"))) }.failedMain())
    }

    // ---------------------------------------------------------------- stats

    @Test fun speedSwapSwapsTheSpeedStatsAndTheTurnOrderFollows() {
        val m = main("tackle"); val b = R17C8.battle(wild("tackle"))
        val before = R17C8.view(m, b)
        val (mine, theirs) = before.stats(true).spe to before.stats(false).spe
        assertTrue(mine > theirs)
        forced("speedswap") { rd(m, b) }
        val after = R17C8.view(m, b)
        assertEquals(theirs, after.stats(true).spe); assertEquals(mine, after.stats(false).spe)
        val order = rd(m, b, wildActs = true).attacks()
        assertFalse("the wild one is faster now", order.first().byPlayer)
    }

    @Test fun powerSplitAndGuardSplitGiveBothTheAverage() {
        val m = main("tackle"); val b = R17C8.battle(R17C8.mon(802, 100, "tackle")) // Torchy: very different stats
        val s = R17C8.view(m, b)
        val u = s.stats(true); val t = s.stats(false)
        forced("powersplit") { rd(m, b) }
        val a1 = R17C8.view(m, b)
        assertEquals((u.atk + t.atk) / 2, a1.stats(true).atk); assertEquals((u.atk + t.atk) / 2, a1.stats(false).atk)
        assertEquals((u.spa + t.spa) / 2, a1.stats(true).spa); assertEquals((u.spa + t.spa) / 2, a1.stats(false).spa)
        assertEquals(u.def, a1.stats(true).def) // the guard stats are untouched
        forced("guardsplit") { rd(m, b) }
        val a2 = R17C8.view(m, b)
        assertEquals((u.def + t.def) / 2, a2.stats(true).def); assertEquals((u.def + t.def) / 2, a2.stats(false).def)
        assertEquals((u.spd + t.spd) / 2, a2.stats(true).spd); assertEquals((u.spd + t.spd) / 2, a2.stats(false).spd)
    }

    @Test fun powerTrickSwapsAttackAndDefenseAndItIsATrickToUndo() {
        val m = R17C8.mon(802, 50, "tackle"); val b = R17C8.battle(wild("tackle"))
        val s0 = R17C8.view(m, b).stats(true)
        assertTrue(s0.atk != s0.def)
        forced("powertrick") { rd(m, b) }
        val s1 = R17C8.view(m, b).stats(true)
        assertEquals(s0.def, s1.atk); assertEquals(s0.atk, s1.def); assertEquals(s0.spa, s1.spa)
        forced("powertrick") { rd(m, b) } // again: back
        assertEquals(s0, R17C8.view(m, b).stats(true))
    }

    @Test fun powerShiftSwapsTheOffensiveWithTheDefensiveStats() {
        val m = R17C8.mon(802, 50, "tackle"); val b = R17C8.battle(wild("tackle"))
        val s0 = R17C8.view(m, b).stats(true)
        forced("powershift") { rd(m, b) }
        val s1 = R17C8.view(m, b).stats(true)
        assertEquals(s0.def, s1.atk); assertEquals(s0.atk, s1.def); assertEquals(s0.spd, s1.spa); assertEquals(s0.spa, s1.spd); assertEquals(s0.spe, s1.spe)
        forced("powershift") { rd(m, b) }
        assertEquals(s0, R17C8.view(m, b).stats(true))
    }

    @Test fun wonderRoomStillSwapsTheDefensesOfChangedStats() {
        val m = R17C8.mon(802, 50, "tackle"); val b = R17C8.battle(wild("tackle"))
        b.field.rooms["wonderroom"] = 5
        val s0 = R17C8.view(m, b).stats(true) // the room shows the real Sp. Def as Defense and the real Defense as Sp. Def
        forced("powertrick") { rd(m, b) }
        val s1 = R17C8.view(m, b).stats(true)
        assertEquals("the real Defense became the Attack", s0.spd, s1.atk)
        assertEquals("the Attack sits in the real Defense, which the room shows as Sp. Def", s0.atk, s1.spd)
        assertEquals("the real Sp. Def is untouched and still shown as Defense", s0.def, s1.def)
    }

    // ---------------------------------------------------------------- Charge, Defense Curl, Minimize, Tar Shot

    @Test fun chargeRaisesSpDefAndDoublesTheNextElectricMoveOnly() {
        val m = main("thunderbolt", "tackle"); val b = R17C8.battle(wild("tackle"))
        val e = forced("charge") { rd(m, b) }
        assertEquals(1, b.player.boosts[3])
        assertTrue("Fastie began charging power!" in e.lines())
        assertEquals(2.0, MoveControl.powerMod(hit(m, b, "thunderbolt")), 0.0)
        assertEquals(1.0, MoveControl.powerMod(hit(m, b, "tackle")), 0.0)
        assertEquals(1.0, MoveControl.powerMod(hit(m, b, "charge")), 0.0)
        forced("thunderbolt") { rd(m, b) }
        assertNull("used up by the Electric move", b.player.volatiles["charge"])
        assertEquals(1.0, MoveControl.powerMod(hit(m, b, "thunderbolt")), 0.0)
    }

    /** R17C FIX-A (review item 13): mainline Gen 9: the charge stays until the user's next Electric move (no countdown), so a wild one that acts rarely still has it. */
    @Test fun anUnusedChargeStaysUntilAnElectricMoveIsUsed() {
        val m = main("tackle"); val b = R17C8.battle(wild("tackle"))
        forced("charge") { rd(m, b) }
        assertEquals(1, b.player.volatiles["charge"])
        repeat(4) { rd(m, b) }
        assertEquals(1, b.player.volatiles["charge"])
    }

    @Test fun defenseCurlSetsTheKeyRolloutReadsAndRaisesDefense() {
        val m = main("rollout"); val b = R17C8.battle(wild("tackle"))
        assertEquals(30, MovePower.basePower(hit(m, b, "rollout")))
        forced("defensecurl") { rd(m, b) }
        assertEquals(1, b.player.boosts[1])
        assertTrue("defensecurl" in b.player.volatiles)
        assertEquals(60, MovePower.basePower(hit(m, b, "rollout")))
        // curling up again with the stage at the limit still works only once
        b.player.boosts[1] = 6
        assertTrue(forced("defensecurl") { rd(m, b) }.failedMain())
    }

    @Test fun minimizeRaisesEvasionAndTheStompingMovesNeverMissAndHitTwiceAsHard() {
        val m = main("tackle"); val b = R17C8.battle(wild("stomp"))
        forced("minimize") { rd(m, b) }
        assertEquals(2, b.player.boosts[6]); assertTrue("minimize" in b.player.volatiles)
        assertEquals(2.0, MoveControl.powerMod(hit(m, b, "stomp", byMain = false)), 0.0)
        assertEquals(2.0, MoveControl.powerMod(hit(m, b, "bodyslam", byMain = false)), 0.0)
        assertEquals(1.0, MoveControl.powerMod(hit(m, b, "tackle", byMain = false)), 0.0)
        assertNull(MoveControl.accuracy(hit(m, b, "stomp", byMain = false), 50.0))
        assertEquals(50.0, MoveControl.accuracy(hit(m, b, "tackle", byMain = false), 50.0)!!, 0.0)
        // end to end with the biggest random numbers: a 50 % move misses a plain target and hits a minimized one
        val m2 = main("tackle"); val plain = R17C8.battle(wild("stomp"))
        val missed = forced(wildId = "stomp") { rd(m2, plain, high, wildActs = true) }.attacks().single { !it.byPlayer }
        assertTrue(missed.missed)
        val m3 = main("tackle"); val mini = R17C8.battle(wild("stomp")); mini.player.volatiles["minimize"] = 1
        val hit = forced(wildId = "stomp") { rd(m3, mini, high, wildActs = true) }.attacks().single { !it.byPlayer }
        assertFalse(hit.missed); assertTrue(hit.damage > 0)
    }

    @Test fun tarShotLowersSpeedAndMakesTheTargetWeakToFire() {
        val m = main("ember"); val b = R17C8.battle(wild("tackle"))
        val ember = data.move("ember")!!
        fun eff(): Double = BattleEngine.damage(data, m, b.player, b.wild, b.enemy, ember, Random(1), forceCrit = false, forceRoll = 100, view = R17C8.view(m, b), attackerIsMain = true).effectiveness
        assertEquals(1.0, eff(), 0.0)
        val e = forced("tarshot") { rd(m, b) }
        assertEquals(-1, b.enemy.boosts[4])
        assertEquals(2.0, eff(), 0.0)
        assertTrue("Slowpoke became weaker to fire!" in e.lines())
        assertEquals(1.0, BattleEngine.damage(data, m, b.player, b.wild, b.enemy, data.move("watergun")!!, Random(1), forceCrit = false, forceRoll = 100, view = R17C8.view(m, b), attackerIsMain = true).effectiveness, 0.0)
    }

    @Test fun theSmartChoiceKnowsWhenAMoveCanStillChangeSomething() {
        val m = main("tackle"); val b = R17C8.battle(wild("swordsdance"))
        fun useful(id: String, p: Boolean = true) = MoveControl.useful(R17C8.view(m, b), p, data.move(id)!!)
        for (id in listOf("psychup", "heartswap", "powerswap", "guardswap", "topsyturvy", "venomdrench", "encore", "disable", "powertrick", "powershift", "rototiller", "flowershield", "sleeptalk", "mimic", "sketch", "copycat"))
            assertEquals("$id in a fresh fight", false, useful(id))
        for (id in listOf("taunt", "torment", "healblock", "tarshot", "soak", "forestscurse", "telekinesis", "magnetrise", "metronome", "transform", "snatch", "minimize"))
            assertEquals("$id in a fresh fight", true, useful(id))
        assertEquals("Tackle against Swords Dance: nothing to seal", false, useful("imprison"))
        // after something happened
        b.enemy.boosts[0] = 2; b.enemy.status = "psn"; b.enemy.lastMove = "swordsdance"; b.enemy.volatiles["taunt"] = 2
        assertEquals(true, useful("psychup")); assertEquals(true, useful("heartswap")); assertEquals(true, useful("powerswap")); assertEquals(true, useful("topsyturvy"))
        assertEquals(true, useful("venomdrench")); assertEquals(true, useful("encore")); assertEquals(true, useful("disable"))
        assertEquals("already taunted", false, useful("taunt"))
        assertEquals(true, useful("mimic")); assertEquals(true, useful("sketch"))
        // the wild one asks about its own side
        assertEquals("the wild one has nothing to copy from the Main", false, useful("psychup", p = false))
    }

    @Test fun everyTrickSaysWhatItDoes() {
        for (id in listOf("psychup", "heartswap", "powerswap", "guardswap", "speedswap", "powersplit", "guardsplit", "powertrick", "powershift", "topsyturvy",
            "acupressure", "venomdrench", "rototiller", "flowershield", "charge", "defensecurl", "minimize", "tarshot")) {
            val text = MoveText.describe(data.move(id)!!)
            assertTrue("$id: $text", text.endsWith(".") && "Has no effect" !in text && "→" !in text)
        }
    }
}
