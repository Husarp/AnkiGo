package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C8.attacks
import com.husarp.ankigo.game.R17C8.high
import com.husarp.ankigo.game.R17C8.lines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R17C-8: the moves that call other moves (Metronome, Sleep Talk, Copycat, Mirror Move, Me First, Nature Power), Snatch and Magic Coat (and
 * their order with Protect), Mimic and Sketch. The Main acts first; "slowMain" / "fastWild" swap the order.
 */
class R17C8CallingTest {
    private val data = R17C8.data
    private fun main(vararg m: String) = R17C8.main(*m)
    private fun wild(vararg m: String, sp: Int = 801) = R17C8.wild(*m, sp = sp)
    private fun slowMain(vararg m: String) = R17C8.mon(801, 50, *m)
    private fun fastWild(vararg m: String) = R17C8.mon(800, 100, *m)
    private fun rd(main: Mon, b: Battle, rng: Random = Random(1), wildActs: Boolean = false) = R17C8.round(main, b, rng, enemyActs = wildActs)
    private fun <T> forced(id: String? = null, wildId: String? = null, block: () -> T): T =
        R17C8.withRules(*listOfNotNull(id?.let { R17C8.forcing(it) }, wildId?.let { R17C8.forcingWild(it) }).toTypedArray(), block = block)
    private fun List<GameEvent>.mainIds() = attacks().filter { it.byPlayer }.map { it.moveId }
    private fun List<GameEvent>.wildIds() = attacks().filter { !it.byPlayer }.map { it.moveId }

    // ---------------------------------------------------------------- Metronome

    @Test fun metronomeCallsOnlyMovesTheDataFlagsAndNeverANeverPickedOneOrACaller() {
        val pool = MoveControl.metronomePool(data).map { it.id }.toSet()
        assertTrue("tackle" in pool && "taunt" in pool)
        for (id in listOf("splash", "fly", "hyperbeam", "metronome", "sleeptalk", "copycat", "mirrormove", "mefirst", "naturepower", "mimic", "sketch", "transform", "outrage", "struggle"))
            assertFalse("$id can't be called", id in pool)
        assertTrue("never-picked moves stay out even when the data flags them", pool.none { it in CoreRules.NEVER_PICKED })
        for (seed in 1..40) {
            val m = main("tackle"); val b = R17C8.battle(wild("tackle"))
            val ids = forced("metronome") { rd(m, b, Random(seed)) }.mainIds()
            assertEquals("seed $seed: $ids", "metronome", ids.first())
            assertTrue("seed $seed: $ids", ids.size == 2 && ids[1] in pool) // one call, one called move: it can't call again
        }
    }

    @Test fun withoutAFlagInTheDataMetronomeUsesTheMainlineExclusionList() {
        val bare = TestData.data(R17C8.species, TestData.MOVES + R17C8.moves.associate { it.id to it.copy(flags = emptySet()) })
        val pool = MoveControl.metronomePool(bare).map { it.id }.toSet()
        assertTrue("tackle" in pool && "taunt" in pool && "recover" in pool)
        for (id in listOf("splash", "fly", "hyperbeam", "metronome", "sleeptalk", "copycat", "mirrormove", "mefirst", "naturepower", "mimic", "sketch", "transform",
            "snatch", "magiccoat", "struggle", "outrage"))
            assertFalse("$id can't be called", id in pool)
    }

    @Test fun metronomeFailsWhenThereIsNothingToCall() {
        val onlyCallers = TestData.data(R17C8.species, TestData.MOVES + R17C8.moves.associate { it.id to it.copy(flags = if (it.id in listOf("metronome", "copycat")) setOf("metronome") else emptySet()) })
        assertTrue(MoveControl.metronomePool(onlyCallers).isEmpty())
    }

    @Test fun aCalledMoveCannotCallAgainEvenWhenTheRulesAreAskedToLoop() {
        // Copycat after Metronome: the last move used was a caller, so it fails instead of looping
        val m = slowMain("tackle"); val b = R17C8.battle(fastWild("metronome"))
        val e = forced("copycat", "metronome") { rd(m, b, Random(3), wildActs = true) }
        assertTrue(e.attacks().filter { it.byPlayer }.single().failed)
        assertTrue("the wild one's Metronome called one move", e.wildIds().size == 2)
    }

    // ---------------------------------------------------------------- Sleep Talk

    @Test fun sleepTalkUsesAnotherMoveWhileAsleepAndFailsWhenAwake() {
        val m = main("sleeptalk", "tackle"); val b = R17C8.battle(wild("tackle")); b.player.status = "slp"
        val e = forced("sleeptalk") { rd(m, b, high) }
        assertEquals(listOf("sleeptalk", "tackle"), e.mainIds())
        assertEquals("slp", b.player.status) // using Sleep Talk does not wake it
        val awake = R17C8.battle(wild("tackle"))
        assertTrue(forced("sleeptalk") { rd(main("sleeptalk", "tackle"), awake) }.attacks().single { it.byPlayer }.failed)
    }

    @Test fun sleepTalkSkipsChargeAndCallingMoves() {
        val m = main("sleeptalk", "fly", "metronome", "ember"); val b = R17C8.battle(wild("tackle")); b.player.status = "slp"
        for (seed in 1..6) {
            val ids = forced("sleeptalk") { rd(m, b, high) }.mainIds()
            assertEquals(listOf("sleeptalk", "ember"), ids)
        }
        val m2 = main("sleeptalk", "fly"); val b2 = R17C8.battle(wild("tackle")); b2.player.status = "slp"
        assertTrue("nothing it can call", forced("sleeptalk") { rd(m2, b2, high) }.attacks().single { it.byPlayer }.failed)
    }

    // ---------------------------------------------------------------- Copycat and Mirror Move

    @Test fun copycatUsesTheMoveUsedLastByAnybody() {
        val m = slowMain("tackle"); val b = R17C8.battle(fastWild("ember"))
        val e = forced("copycat") { rd(m, b, wildActs = true) }
        assertEquals(listOf("ember"), e.wildIds())
        assertEquals(listOf("copycat", "ember"), e.mainIds())
        // nothing was used yet: it fails
        assertTrue(forced("copycat") { rd(main("tackle"), R17C8.battle(wild("tackle"))) }.attacks().single { it.byPlayer }.failed)
        // a move it can't copy
        val b2 = R17C8.battle(wild("tackle")); b2.enemy.notes["mc.last"] = "metronome"
        assertTrue(forced("copycat") { rd(main("tackle"), b2) }.attacks().single { it.byPlayer }.failed)
    }

    @Test fun mirrorMoveUsesTheTargetsLastMoveWhenItCanBeMirrored() {
        val m = slowMain("tackle"); val b = R17C8.battle(fastWild("ember"))
        val e = forced("mirrormove") { rd(m, b, wildActs = true) }
        assertEquals(listOf("mirrormove", "ember"), e.mainIds())
        val m2 = slowMain("tackle"); val b2 = R17C8.battle(fastWild("mirrorless"))
        val e2 = forced("mirrormove") { rd(m2, b2, wildActs = true) }
        assertEquals(listOf("mirrormove"), e2.mainIds()); assertTrue(e2.attacks().single { it.byPlayer }.failed)
        assertTrue(forced("mirrormove") { rd(main("tackle"), R17C8.battle(wild("ember"))) }.attacks().single { it.byPlayer }.failed) // it has not moved yet
    }

    // ---------------------------------------------------------------- Me First

    @Test fun meFirstUsesTheTargetsMoveFirstWithMorePower() {
        fun run(forcedId: String): GameEvent.Attack {
            val m = main("tackle"); val b = R17C8.battle(wild("tackle"))
            return forced(forcedId) { rd(m, b, Random(9), wildActs = true) }.attacks().first { it.byPlayer && it.moveId == "tackle" }
        }
        val plain = run("tackle"); val first = run("mefirst")
        assertTrue("x1.5: ${first.damage} against ${plain.damage}", first.damage in (plain.damage * 13 / 10)..(plain.damage * 17 / 10))
        val e = forced("mefirst") { rd(main("tackle"), R17C8.battle(wild("tackle")), Random(9), wildActs = true) }
        assertEquals(listOf("mefirst", "tackle"), e.mainIds()); assertEquals(listOf("tackle"), e.wildIds()) // the wild one still uses its own move
    }

    @Test fun meFirstFailsWhenTheTargetMovedOrWillNotAttack() {
        val slow = slowMain("tackle")
        assertTrue(forced("mefirst") { rd(slow, R17C8.battle(fastWild("tackle")), wildActs = true) }.attacks().single { it.byPlayer }.failed)
        val e = forced("mefirst") { rd(main("tackle"), R17C8.battle(wild("swordsdance")), wildActs = true) }
        assertTrue(e.attacks().single { it.byPlayer }.failed)
        assertTrue(forced("mefirst") { rd(main("tackle"), R17C8.battle(wild("tackle")), wildActs = false) }.attacks().single { it.byPlayer }.failed)
    }

    // ---------------------------------------------------------------- Nature Power

    @Test fun naturePowerFollowsTheTerrain() {
        for ((terrain, id) in listOf(null to "triattack", "electric" to "thunderbolt", "grassy" to "energyball", "misty" to "moonblast", "psychic" to "psychic")) {
            val b = R17C8.battle(wild("tackle")); b.field.terrain = terrain; b.field.terrainTurns = if (terrain == null) 0 else 5
            val ids = forced("naturepower") { rd(main("tackle"), b) }.mainIds()
            assertEquals("terrain $terrain", listOf("naturepower", id), ids)
        }
    }

    // ---------------------------------------------------------------- Snatch and Magic Coat

    @Test fun snatchStealsTheFoesNextSelfTargetingStatusMove() {
        val m = main("tackle"); val w = wild("swordsdance"); val b = R17C8.battle(w)
        val e = forced("snatch") { rd(m, b, wildActs = true) }
        assertTrue("Fastie snatched Slowpoke's Swords Dance!" in e.lines())
        assertEquals(2, b.player.boosts[0]); assertEquals(0, b.enemy.boosts[0])
        assertNull("it is spent", b.player.volatiles["snatch"])
        // the next round the wild one's move is its own again
        rd(m, b, wildActs = true)
        assertEquals(2, b.enemy.boosts[0])
    }

    @Test fun theWildOneCanSnatchToo() {
        val m = main("tackle"); val b = R17C8.battle(wild("tackle"))
        forced("swordsdance", "snatch") { rd(m, b, wildActs = true) }
        assertEquals(2, b.enemy.boosts[0]); assertEquals(0, b.player.boosts[0])
    }

    @Test fun snatchIgnoresAttacksAndLastsOneRound() {
        val m = main("tackle"); val b = R17C8.battle(wild("tackle"))
        val e = forced("snatch") { rd(m, b, wildActs = true) }
        assertEquals(listOf("tackle"), e.wildIds()); assertTrue(e.lines().none { "snatched" in it })
        assertNull(b.player.volatiles["snatch"]) // gone with the round
        // and it works whoever is faster, because it goes first
        val slow = slowMain("tackle"); val b2 = R17C8.battle(fastWild("swordsdance"))
        forced("snatch") { rd(slow, b2, wildActs = true) }
        assertEquals(2, b2.player.boosts[0]); assertEquals(0, b2.enemy.boosts[0])
    }

    @Test fun magicCoatBouncesAReflectableStatusMoveBackAtItsUser() {
        val m = main("tackle"); val w = wild("toxic"); val b = R17C8.battle(w)
        val e = forced("magiccoat") { rd(m, b, wildActs = true) }
        assertTrue("Fastie bounced the Toxic back!" in e.lines())
        assertEquals("tox", b.enemy.status); assertNull(b.player.status)
        assertNull("one round only", b.player.volatiles["magiccoat"])
        rd(m, b, wildActs = true) // the coat is gone: the next Toxic reaches the Main
        assertEquals("tox", b.player.status)
    }

    @Test fun aBouncedMoveIsNotBouncedAgainAndANonReflectableMoveIsNot() {
        val m = main("tackle"); val b = R17C8.battle(wild("tackle"))
        b.player.volatiles["magiccoat"] = 1; b.enemy.volatiles["magiccoat"] = 1
        forced("toxic") { rd(m, b) } // the Main's Toxic is bounced by the wild one's coat... and not once more by the Main's own
        assertEquals("tox", b.player.status); assertNull(b.enemy.status)
        val m2 = main("tackle"); val b2 = R17C8.battle(wild("tackle")); b2.enemy.volatiles["magiccoat"] = 1; b2.player.boosts[0] = 2
        forced("psychup") { rd(m2, b2) } // Psych Up is not reflectable: it works
        assertEquals(0, b2.player.boosts[0])
        assertNull(b2.player.status)
    }

    @Test fun protectComesBeforeMagicCoat() {
        val m = main("tackle"); val b = R17C8.battle(wild("toxic"))
        b.player.volatiles["magiccoat"] = 1; b.player.volatiles["protect"] = 1
        val e = rd(m, b, wildActs = true)
        assertTrue("Fastie protected itself!" in e.lines())
        assertTrue(e.lines().none { "bounced" in it })
        assertNull(b.enemy.status); assertNull(b.player.status)
    }

    // ---------------------------------------------------------------- Mimic and Sketch

    @Test fun mimicTakesTheTargetsLastMoveForTheRestOfTheFight() {
        val m = slowMain("mimic"); val b = R17C8.battle(fastWild("ember"))
        val e = forced("mimic") { rd(m, b, wildActs = true) }
        assertTrue("Slowpoke learned Ember!" in e.lines())
        assertEquals(listOf("ember"), R17C8.view(m, b).moves(true).map { it.id })
        assertEquals("the Pokémon's own list is untouched", listOf("mimic"), m.moves)
        val r2 = rd(m, b, wildActs = true)
        assertEquals(listOf("ember"), r2.mainIds())
        // a new Main is a new fight: the copy is gone
        assertEquals(listOf("mimic"), R17C8.view(m, R17C8.battle(wild("tackle"))).moves(true).map { it.id })
    }

    @Test fun mimicFailsOnMovesItCannotCopyAndOnMovesItKnows() {
        val m = slowMain("mimic", "ember"); val b = R17C8.battle(fastWild("ember"))
        assertTrue("it knows Ember already", forced("mimic") { rd(m, b, wildActs = true) }.attacks().single { it.byPlayer && it.moveId == "mimic" }.failed)
        for (last in listOf("mimic", "sketch", "transform", "struggle", "metronome", "mirrormove", "naturepower", "sleeptalk", "copycat", "mefirst")) {
            val b2 = R17C8.battle(wild("tackle")); b2.enemy.lastMove = last
            assertTrue(last, forced("mimic") { rd(main("mimic"), b2) }.attacks().single { it.byPlayer }.failed)
        }
        assertTrue("no last move", forced("mimic") { rd(main("mimic"), R17C8.battle(wild("tackle"))) }.attacks().single { it.byPlayer }.failed)
    }

    @Test fun sketchCopiesTheLastMoveButDoesNotTeachItForGood() {
        val m = slowMain("sketch"); val b = R17C8.battle(fastWild("ember"))
        val e = forced("sketch") { rd(m, b, wildActs = true) }
        assertTrue("Slowpoke sketched Ember!" in e.lines())
        assertEquals(listOf("ember"), R17C8.view(m, b).moves(true).map { it.id })
        assertEquals("not learned for good", listOf("sketch"), m.moves)
        assertTrue(MoveText.describe(data.move("sketch")!!).contains("not learned for good"))
        assertEquals(listOf("ember"), rd(m, b, wildActs = true).mainIds())
        // Sketch fails on itself and on Struggle
        for (last in listOf("sketch", "struggle")) {
            val b2 = R17C8.battle(wild("tackle")); b2.enemy.lastMove = last
            assertTrue(last, forced("sketch") { rd(main("sketch"), b2) }.attacks().single { it.byPlayer }.failed)
        }
    }

    // ---------------------------------------------------------------- texts and the move choice

    @Test fun theCallingMovesSayWhatTheyDoInPlainWords() {
        for (id in listOf("metronome", "sleeptalk", "copycat", "mirrormove", "mefirst", "naturepower", "snatch", "magiccoat", "mimic", "sketch")) {
            val text = MoveText.describe(data.move(id)!!)
            assertTrue("$id: $text", text.endsWith(".") && "Has no effect" !in text && "→" !in text)
        }
    }

    @Test fun theSmartChoiceDoesNotWasteTurnsOnCallsThatCannotWork() {
        val m = main("sleeptalk", "tackle"); val b = R17C8.battle(wild("tackle"))
        val picks = (1..30).map { MoveChooser.pickMain(MoveChoice.SMART, data, b, m, Random(it)).id }.toSet()
        assertEquals("Sleep Talk only works while asleep", setOf("tackle"), picks)
        b.player.status = "slp"
        val asleep = (1..60).map { MoveChooser.pickMain(MoveChoice.SMART, data, b, m, Random(it)).id }.toSet()
        assertTrue("sleeptalk" in asleep)
    }
}
