package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C8.attacks
import com.husarp.ankigo.game.R17C8.effects
import com.husarp.ankigo.game.R17C8.high
import com.husarp.ankigo.game.R17C8.kinds
import com.husarp.ankigo.game.R17C8.lines
import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R17C-8: move control: Taunt, Encore, Disable, Torment, Imprison, Heal Block, Throat Chop, and Struggle when every move is blocked (both sides).
 * Fixtures in [R17C8]: the Main "Fastie" acts first; "slowMain" / "fastWild" swap the order.
 */
class R17C8ControlTest {
    private val data = R17C8.data
    private fun main(vararg m: String) = R17C8.main(*m)
    private fun wild(vararg m: String, sp: Int = 801) = R17C8.wild(*m, sp = sp)
    /** A Main that is slower than a [fastWild]. */
    private fun slowMain(vararg m: String) = R17C8.mon(801, 50, *m)
    private fun fastWild(vararg m: String) = R17C8.mon(800, 100, *m)
    private fun rd(main: Mon, b: Battle, rng: Random = Random(1), wildActs: Boolean = true, choice: MoveChoice = MoveChoice.RANDOM) =
        R17C8.round(main, b, rng, enemyActs = wildActs, choice = choice)

    /** The Main uses [id] (and the wild one [wildId]) on every pick of [block]. */
    private fun <T> forced(id: String? = null, wildId: String? = null, block: () -> T): T =
        R17C8.withRules(*listOfNotNull(id?.let { R17C8.forcing(it) }, wildId?.let { R17C8.forcingWild(it) }).toTypedArray(), block = block)

    private fun List<GameEvent>.wildMoves() = attacks().filter { !it.byPlayer }.map { it.move }
    private fun List<GameEvent>.mainMoves() = attacks().filter { it.byPlayer }.map { it.move }

    // ---------------------------------------------------------------- Taunt

    @Test fun tauntBlocksStatusMovesAndThenTheWildOneStrugglesForItsThreeActions() {
        val m = main("tackle"); val b = R17C8.battle(wild("swordsdance"))
        val r1 = forced("taunt") { rd(m, b) }
        assertEquals(listOf("Slowpoke fell for the taunt!", "Slowpoke can't use Swords Dance after the taunt!"), r1.lines().filter { "taunt" in it })
        assertTrue("the blocked move shows as a failed attack", r1.attacks().single { !it.byPlayer }.failed)
        assertEquals(2, b.enemy.volatiles["taunt"]) // 3 at the cast, the round is over
        val r2 = rd(m, b)
        assertEquals(listOf("Struggle"), r2.wildMoves()) // every move it has is a status move: Struggle
        val r3 = rd(m, b)
        assertEquals(listOf("Struggle"), r3.wildMoves())
        assertNull(b.enemy.volatiles["taunt"])
        assertTrue(r3.lines().any { it == "Slowpoke's taunt wore off!" })
        val r4 = rd(m, b)
        assertEquals(listOf("Swords Dance"), r4.wildMoves())
        assertFalse(r4.attacks().single { !it.byPlayer }.failed)
    }

    @Test fun tauntLastsOneRoundLongerWhenTheTargetHasMovedAlready() {
        val m = slowMain("tackle"); val b = R17C8.battle(fastWild("swordsdance"))
        forced("taunt") { rd(m, b) } // the wild one (faster) used Swords Dance first
        assertEquals(3, b.enemy.volatiles["taunt"]) // 4 at the cast: its next three actions are covered
    }

    @Test fun aTauntedPokemonCanStillUseAttacksAndTheSmartChoiceLeavesStatusMovesOut() {
        val m = main("tackle"); val b = R17C8.battle(wild("swordsdance", "tackle"))
        forced("taunt") { rd(m, b, Random(3), choice = MoveChoice.SMART) }
        for (i in 1..2) assertEquals(listOf("Tackle"), rd(m, b, Random(i + 10), choice = MoveChoice.SMART).wildMoves())
        // the Main, taunted by the wild one: Swords Dance is left out of its picks (it is picked now and then without the Taunt)
        val m2 = main("tackle", "swordsdance"); val b2 = R17C8.battle(wild("tackle"))
        forced(wildId = "taunt") { rd(m2, b2, Random(2), choice = MoveChoice.SMART) }
        assertEquals(3, b2.player.volatiles["taunt"]) // 4 at the cast (the Main had moved), one round gone
        repeat(3) { assertEquals(listOf("Tackle"), rd(m2, b2, Random(it + 20), choice = MoveChoice.SMART).mainMoves()) }
        assertNull(b2.player.volatiles["taunt"])
    }

    // ---------------------------------------------------------------- Encore

    @Test fun encoreMakesTheTargetRepeatItsLastMoveForThreeActions() {
        val m = slowMain("tackle"); val b = R17C8.battle(fastWild("ember", "watergun"))
        forced("encore", "ember") { rd(m, b) } // the wild one (faster) used Ember, then got the Encore
        assertEquals("ember", b.enemy.notes["encore"])
        assertEquals(3, b.enemy.volatiles["encore"]) // 4 at the cast
        repeat(3) { assertEquals(listOf("Ember"), rd(m, b, Random(it + 1)).wildMoves()) }
        assertNull(b.enemy.volatiles["encore"]); assertNull(b.enemy.notes["encore"])
    }

    @Test fun encoreWorksOnTheMainToo() {
        val m = main("tackle", "ember"); val b = R17C8.battle(wild("tackle"))
        val first = rd(m, b, Random(4)).mainMoves().single() // the Main moved first
        forced(wildId = "encore") { rd(m, b, Random(4)) }
        repeat(3) { assertEquals(listOf(first), rd(m, b, Random(it + 7)).mainMoves()) }
    }

    @Test fun encoreFailsWithoutALastMoveAndOnMovesItCannotRepeat() {
        val m = main("tackle"); val b = R17C8.battle(wild("ember"))
        val e = forced("encore") { rd(m, b, wildActs = false) } // the wild one has not used a move
        assertTrue(e.attacks().single { it.byPlayer }.failed); assertTrue("But it failed!" in e.lines())
        assertNull(b.enemy.volatiles["encore"])
        for (last in listOf("encore", "transform", "mimic", "sketch", "struggle", "metronome", "mirrormove", "naturepower", "sleeptalk", "copycat", "mefirst")) {
            b.enemy.lastMove = last
            assertTrue("$last", forced("encore") { rd(m, b, wildActs = false) }.attacks().single { it.byPlayer }.failed)
            assertNull(last, b.enemy.volatiles["encore"])
        }
    }

    @Test fun anEncoredMoveThatIsBlockedMeansStruggle() {
        val m = main("tackle"); val b = R17C8.battle(wild("swordsdance", "tackle"))
        b.enemy.volatiles["encore"] = 3; b.enemy.notes["encore"] = "swordsdance"; b.enemy.volatiles["taunt"] = 3
        assertEquals(listOf("Struggle"), rd(m, b).wildMoves())
    }

    // ---------------------------------------------------------------- Disable

    @Test fun disableBlocksTheLastMoveForFourActions() {
        val m = slowMain("tackle"); val b = R17C8.battle(fastWild("ember", "watergun"))
        val r1 = forced("disable", "ember") { rd(m, b) }
        assertTrue("Fastie's Ember was disabled!" in r1.lines())
        assertEquals("ember", b.enemy.notes["disable"])
        assertEquals(4, b.enemy.volatiles["disable"]) // 5 at the cast
        repeat(4) { assertEquals(listOf("Water Gun"), rd(m, b, Random(it + 1)).wildMoves()) }
        assertNull(b.enemy.volatiles["disable"]); assertNull(b.enemy.notes["disable"])
    }

    @Test fun disablingTheOnlyMoveMeansStruggleAndTheLineNamesTheReason() {
        val m = main("tackle"); val b = R17C8.battle(wild("ember"))
        b.enemy.lastMove = "ember"
        val r1 = forced("disable") { rd(m, b) }
        assertTrue("Slowpoke can't use Ember because it is disabled!" in r1.lines()) // it had picked Ember before the Disable came
        assertEquals(listOf("Struggle"), rd(m, b).wildMoves())
    }

    @Test fun disableFailsWithoutALastMove() {
        val m = main("tackle"); val b = R17C8.battle(wild("ember"))
        assertTrue(forced("disable") { rd(m, b, wildActs = false) }.attacks().single { it.byPlayer }.failed)
        assertNull(b.enemy.volatiles["disable"])
    }

    // ---------------------------------------------------------------- Torment

    @Test fun tormentStopsTheSameMoveTwiceInARow() {
        val m = main("tackle"); val b = R17C8.battle(wild("ember", "watergun"))
        val moves = ArrayList<String>()
        moves += forced("torment") { rd(m, b, Random(1)) }.wildMoves()
        for (i in 2..9) moves += rd(m, b, Random(i)).wildMoves()
        assertEquals(9, moves.size)
        for (i in 1 until moves.size) assertNotEquals("round ${i + 1}: $moves", moves[i - 1], moves[i])
    }

    @Test fun aTormentedPokemonWithOneMoveAlternatesWithStruggleOnBothSides() {
        val m = main("tackle"); val b = R17C8.battle(wild("ember"))
        val e = forced("torment") { rd(m, b) }
        assertTrue("Slowpoke was subjected to torment!" in e.lines())
        assertEquals(listOf("Ember"), e.wildMoves()) // its first Ember still works
        assertEquals(listOf("Struggle"), rd(m, b).wildMoves())
        assertEquals(listOf("Ember"), rd(m, b).wildMoves())
        // the Main, tormented by the wild one
        val m2 = main("tackle"); val b2 = R17C8.battle(wild("ember"))
        forced(wildId = "torment") { rd(m2, b2) }
        assertEquals(listOf("Struggle"), rd(m2, b2).mainMoves())
        assertEquals(listOf("Tackle"), rd(m2, b2).mainMoves())
    }

    @Test fun aTormentDoesNotBreakAChargeOrALockedMove() {
        val m = main("outrage"); val b = R17C8.battle(wild("tackle"))
        b.player.volatiles["torment"] = 1; b.player.lastMove = "outrage"
        b.player.volatiles["lockedmove"] = 1; b.player.notes["lockedmove"] = "outrage"
        assertNull(MoveControl.moveBlocked(R17C8.view(m, b), true, data.move("outrage")!!))
        b.player.volatiles.remove("lockedmove")
        assertEquals("twice in a row due to the torment", MoveControl.moveBlocked(R17C8.view(m, b), true, data.move("outrage")!!))
    }

    // ---------------------------------------------------------------- Imprison

    @Test fun imprisonSealsEveryMoveBothKnow() {
        val m = main("tackle", "ember"); val b = R17C8.battle(wild("tackle"))
        val r1 = forced("imprison") { rd(m, b) }
        assertTrue("Fastie sealed any moves its target shares with it!" in r1.lines())
        assertTrue("Slowpoke can't use Tackle because of Imprison!" in r1.lines())
        assertEquals(listOf("Struggle"), rd(m, b).wildMoves())
        // a move the Main does not know is free
        val m2 = main("tackle", "ember"); val b2 = R17C8.battle(wild("tackle", "watergun"))
        forced("imprison") { rd(m2, b2) }
        repeat(3) { assertEquals(listOf("Water Gun"), rd(m2, b2, Random(it)).wildMoves()) }
    }

    @Test fun imprisonFailsWhenNothingIsShared() {
        val m = main("ember"); val b = R17C8.battle(wild("watergun"))
        assertTrue(forced("imprison") { rd(m, b, wildActs = false) }.attacks().single { it.byPlayer }.failed)
        assertNull(b.player.volatiles["imprison"])
    }

    // ---------------------------------------------------------------- Heal Block and Throat Chop

    @Test fun healBlockStopsHealingMovesForFiveActions() {
        val m = main("tackle"); val w = wild("recover"); w.hp = 300; val b = R17C8.battle(w)
        val r1 = forced("healblock") { rd(m, b) }
        assertTrue("Slowpoke was prevented from healing!" in r1.lines())
        assertTrue("Slowpoke can't use Recover because of Heal Block!" in r1.lines())
        assertEquals(4, b.enemy.volatiles["healblock"]) // 5 at the cast
        assertEquals(listOf("Struggle"), rd(m, b).wildMoves()) // nothing else to use
        assertEquals(3, b.enemy.volatiles["healblock"])
    }

    @Test fun healBlockStopsADrainThatHasNoHealFlagAndGrassyTerrain() {
        // Leech Life drains but is not flagged "heal" here: the engine's drain line itself checks Heal Block
        val hurt = { val m = slowMain("leechlife"); m.hp = m.hp / 2; m }
        val m1 = hurt(); val hp1 = m1.hp; val b1 = R17C8.battle(fastWild("swordsdance"))
        val e1 = rd(m1, b1)
        assertTrue("drain heals without Heal Block", e1.kinds(EffectKind.DRAIN).isNotEmpty() && m1.hp > hp1)
        val m2 = hurt(); val hp2 = m2.hp; val b2 = R17C8.battle(fastWild("swordsdance"))
        val e2 = forced(wildId = "healblock") { rd(m2, b2) }
        assertTrue("Slowpoke was prevented from healing!" in e2.lines())
        assertTrue(e2.kinds(EffectKind.DRAIN).isEmpty()); assertEquals(hp2, m2.hp)
        // Grassy Terrain heals a hurt grounded Pokémon, unless Heal Block is on it
        for (blocked in listOf(false, true)) {
            val m = main("tackle"); m.hp = m.hp / 2; val b = R17C8.battle(wild("tackle"))
            b.field.terrain = "grassy"; b.field.terrainTurns = 5
            if (blocked) b.player.volatiles["healblock"] = 3
            val healedMain = rd(m, b, wildActs = false).effects().any { it.onPlayer && "grassy terrain" in it.text }
            assertEquals("blocked = $blocked", !blocked, healedMain)
        }
    }

    @Test fun psychicNoiseStartsAHealBlockOfTwoTurnsAndItCountsDown() {
        val m = main("psychicnoise"); val b = R17C8.battle(wild("tackle"))
        val r1 = rd(m, b, Random(1), wildActs = false)
        assertTrue("Slowpoke was prevented from healing!" in r1.lines())
        assertEquals(1, b.enemy.volatiles["healblock"])
        val r2 = rd(m, b, Random(1), wildActs = false)
        assertNull(b.enemy.volatiles["healblock"])
        assertTrue(r2.lines().any { "Heal Block on Slowpoke ended" in it })
    }

    @Test fun aHealingMoveThatIsCalledByAnotherMoveFailsUnderHealBlock() {
        val m = main("tackle"); val w = wild("tackle"); w.hp = 100; val b = R17C8.battle(w)
        b.enemy.volatiles["healblock"] = 3
        val r = BattleEngine.Round(data, b, m, 1.0, 1.0, Random(1))
        assertEquals("Slowpoke can't heal while Heal Block lasts!", MoveControl.failReason(r, false, data.move("recover")!!))
        assertNull(MoveControl.failReason(r, true, data.move("recover")!!)) // only the blocked side
        assertNull(MoveControl.failReason(r, false, data.move("swordsdance")!!))
    }

    @Test fun throatChopStopsSoundMovesForTwoActions() {
        val m = main("tackle"); val b = R17C8.battle(wild("hypervoice"))
        val r1 = forced("throatchop") { rd(m, b) }
        assertEquals(1, b.enemy.volatiles["throatchop"])
        assertTrue("Slowpoke can't use Hyper Voice after the throat chop!" in r1.lines())
        assertEquals(listOf("Struggle"), rd(m, b).wildMoves())
        assertNull(b.enemy.volatiles["throatchop"])
        assertEquals(listOf("Hyper Voice"), rd(m, b).wildMoves())
    }

    // ---------------------------------------------------------------- Struggle, both sides

    @Test fun struggleIsNeverBlocked() {
        val m = main("tackle"); val b = R17C8.battle(wild("tackle"))
        b.enemy.volatiles["taunt"] = 3; b.enemy.volatiles["torment"] = 1; b.enemy.lastMove = "struggle"; b.enemy.volatiles["imprison"] = 1
        b.enemy.volatiles["throatchop"] = 2; b.enemy.volatiles["healblock"] = 2; b.enemy.volatiles["disable"] = 3; b.enemy.notes["disable"] = "struggle"
        assertNull(MoveControl.moveBlocked(R17C8.view(m, b), false, BattleEngine.struggle(data)))
    }

    @Test fun aPokemonWithEveryMoveBlockedUsesStruggleAndTheOtherSideIsNotAffected() {
        val m = main("tackle"); val b = R17C8.battle(wild("tackle", "ember"))
        b.player.volatiles["disable"] = 3; b.player.notes["disable"] = "tackle"
        val e = rd(m, b, Random(2))
        assertEquals(listOf("Struggle"), e.mainMoves())
        assertTrue(e.wildMoves().single() in listOf("Tackle", "Ember"))
    }

    @Test fun aSwitchHitFollowsTheSameRules() {
        val m = main("tackle"); val w = wild("swordsdance"); val b = R17C8.battle(w)
        b.enemy.volatiles["taunt"] = 3
        val e = BattleEngine.switchHit(data, b, m, Random(1), MoveChoice.RANDOM, 0.2)
        assertEquals(listOf("Struggle"), e.attacks().map { it.move })
    }

    // ---------------------------------------------------------------- the save

    @Test fun theConditionsSurviveASaveAndStillWork() {
        val m = slowMain("tackle"); val b = R17C8.battle(fastWild("ember", "watergun"))
        forced("encore", "ember") { rd(m, b) }
        b.player.volatiles["lockon"] = 5; b.player.notes["mc.types"] = "FIRE,WATER"
        val s = GameState(schema = GameState.SCHEMA)
        s.mons += Mon("u1", 19, "Ratty", 10, 0, Stats(15, 15, 15, 15, 15, 15), Stats.ZERO, Nature.HARDY, 'M', false, "x", mutableListOf("tackle"), 20)
        s.team += "u1"; s.battle = b
        val back = SaveCodec.decode(SaveCodec.encode(s)).battle!!
        assertEquals(b.enemy.volatiles, back.enemy.volatiles); assertEquals(b.enemy.notes, back.enemy.notes)
        assertEquals(b.player.volatiles, back.player.volatiles); assertEquals(b.player.notes, back.player.notes)
        assertEquals(listOf("Ember"), rd(m, back).wildMoves()) // the Encore goes on after the load
    }

    @Test fun highRandomNumbersDoNotMakeTheFixtureMiss() {
        // a sanity check of the test setup: a status move with 100 % accuracy works even with the biggest random numbers
        val m = main("tackle"); val b = R17C8.battle(wild("tackle"))
        assertFalse(forced("taunt") { rd(m, b, high, wildActs = false) }.attacks().single { it.byPlayer }.missed)
    }
}
