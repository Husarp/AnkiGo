package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R17C FIX-A (review items 9, 11, 13, 14, Wonder Guard's text and the coders' notes): a non-Ghost Curse is a move on its user, an unknown weather /
 * terrain key from a save does not crash, Charge lasts until the next Electric move (mainline Gen 9; no countdown), the texts say "moves" where
 * the user's own uses are counted, Steadfast needs the flinch to take the action, Comatose counts as asleep for Sleep Talk and Nightmare, the
 * entry abilities fire once per fight and Pokémon.
 */
class R17CFixARulesTest {
    private val F = R17CFixA
    private val data = F.data

    private fun List<GameEvent>.attacks() = filterIsInstance<GameEvent.Attack>()
    private fun List<GameEvent>.lines() = filterIsInstance<GameEvent.Effect>().map { it.text }

    // ---------------------------------------------------------------- item 9: Curse of a non-Ghost user

    @Test fun aNonGhostCurseIsAMoveOnItsUser() {
        // Good as Gold, Prankster against a Dark type and Crafty Shield all treated it as aimed at the other Pokémon
        fun rises(m: Mon, wild: Mon, wildMove: String? = null): Triple<Boolean, Int, Int> {
            val b = F.battle(wild)
            val ev = F.round(m, b, "curse", wildMove)
            return Triple(ev.attacks().first { it.byPlayer }.failed, b.player.boosts[0], b.player.boosts[4])
        }
        assertEquals(Triple(false, 1, -1), rises(F.main("curse"), F.wild("tackle", ability = "goodasgold")))
        assertEquals(Triple(false, 1, -1), rises(F.main("curse", ability = "prankster"), F.wild("tackle", sp = 905)))
        assertEquals(Triple(false, 1, -1), rises(F.main("curse"), F.wild("craftyshield", "tackle", sp = 903), "craftyshield"))
        // a Ghost type's Curse is aimed at the target: Good as Gold stops it and it costs no HP then
        val m = F.main("curse", sp = 902); val b = F.battle(F.wild("tackle", ability = "goodasgold"))
        val ev = F.round(m, b, "curse", null)
        assertTrue(ev.attacks().first { it.byPlayer }.failed)
        assertEquals(F.maxHp(m), m.hp)
        assertNull(b.enemy.volatiles["curse"])
    }

    @Test fun theSmartChoiceKnowsASelfCurseWorksAgainstGoodAsGold() {
        val b = F.battle(F.wild("tackle", ability = "goodasgold"))
        val v = F.view(F.main("curse", "tackle"), b)
        assertNotNull(v)
        assertTrue(BattleRules.using(BattleRules.ALL) { rulesUseful(v, true, data.move("curse")!!) } != false)
        val ghost = F.view(F.main("curse", "tackle", sp = 902), b)
        assertEquals(false, BattleRules.using(BattleRules.ALL) { rulesUseful(ghost, true, data.move("curse")!!) })
    }

    // ---------------------------------------------------------------- item 11: an unknown weather / terrain key

    @Test fun anUnknownWeatherOrTerrainKeyIsDroppedFromASave() {
        val s = GameState(schema = GameState.SCHEMA)
        s.mons += TestData.mon(data, 900, 5); s.team += s.mons.first().uid
        val b = F.battle(F.wild("tackle"))
        b.field.weather = "monsoon"; b.field.weatherTurns = 3; b.field.terrain = "lava"; b.field.terrainTurns = 2
        s.battle = b
        val back = SaveCodec.decode(SaveCodec.encode(s)).battle!!
        assertNull(back.field.weather); assertNull(back.field.terrain)
        // the known ones survive
        b.field.weather = "rain"; b.field.terrain = "grassy"
        val kept = SaveCodec.decode(SaveCodec.encode(s)).battle!!
        assertEquals("rain", kept.field.weather); assertEquals("grassy", kept.field.terrain)
    }

    @Test fun anUnknownKeyThatGotIntoTheFightEndsWithoutACrash() {
        val m = F.main("tackle"); val b = F.battle(F.wild("tackle"))
        b.field.weather = "monsoon"; b.field.weatherTurns = 1; b.field.terrain = "lava"; b.field.terrainTurns = 1
        F.round(m, b, "tackle", "tackle") // the end of the round counts both down
        assertNull(b.field.weather); assertNull(b.field.terrain)
    }

    // ---------------------------------------------------------------- item 13: Charge lasts until the next Electric move

    @Test fun chargeStaysUntilTheNextElectricMoveAndDoublesIt() {
        fun thunderbolt(rounds: Int, charge: Boolean): Pair<Int, Battle> {
            val m = F.main("charge", "thunderbolt", "tackle"); val b = F.battle(F.wild("tackle", sp = 904)) // Sparky resists nothing relevant: Electric vs Electric is 0.5 either way
            if (charge) F.round(m, b, "charge", null)
            repeat(rounds) { F.round(m, b, "tackle", null) } // idle rounds of other moves
            val ev = F.round(m, b, "thunderbolt", null)
            return ev.attacks().first { it.byPlayer }.damage to b
        }
        val (plain, _) = thunderbolt(0, false)
        val (soon, b1) = thunderbolt(0, true)
        val (late, b2) = thunderbolt(4, true) // four rounds in between: the charge is still there (it used to be gone after two)
        assertEquals(soon, late)
        assertTrue("$soon vs $plain", soon.toDouble() / plain in 1.8..2.2)
        assertNull("used up by the Electric move", b1.player.volatiles["charge"]); assertNull(b2.player.volatiles["charge"])
        // it stays while no Electric move is used, and never says it lost its charge
        val m = F.main("charge", "tackle"); val b = F.battle(F.wild("tackle"))
        F.round(m, b, "charge", null)
        val ev = (1..5).flatMap { F.round(m, b, "tackle", null) }
        assertEquals(1, b.player.volatiles["charge"])
        assertTrue(ev.lines().none { "lost its charge" in it })
    }

    @Test fun electromorphosisGivesTheSameCharge() {
        val m = F.main("tackle"); val b = F.battle(F.wild("tackle", ability = "electromorphosis"))
        F.round(m, b, "tackle", null)
        assertEquals(1, b.enemy.volatiles["charge"])
        repeat(4) { F.round(m, b, "tackle", null) }
        assertEquals(1, b.enemy.volatiles["charge"]) // no countdown: a wild one acts rarely
        assertTrue(MoveText.describe(data.move("charge")!!).contains("Your next Electric move deals double damage."))
        assertEquals("When a move hits it, its next Electric move deals double damage.", AbilityText.of("Electromorphosis"))
    }

    // ---------------------------------------------------------------- item 14 and the text fixes

    @Test fun textsCountTheUsersOwnMovesAsMoves() {
        val outrage = data.move("outrage")!!
        val uproar = data.move("uproar")!!
        val bide = data.move("bide")!!
        assertTrue(MoveText.describe(outrage), "2–3 moves in a row, then you become confused." in MoveText.describe(outrage))
        assertTrue(MoveText.describe(uproar), "3 moves in a row." in MoveText.describe(uproar))
        assertTrue(MoveText.describe(bide), "Stores energy for 2 of your moves" in MoveText.describe(bide))
        assertFalse("turns, then you become confused" in MoveText.describe(outrage))
        assertFalse("for 3 turns" in MoveText.describe(uproar))
        assertTrue("While you keep using it" in MoveText.describe(data.move("rage")!!))
    }

    @Test fun finalGambitSaysItNeedsAHitAndWonderGuardSaysStruggle() {
        assertEquals("You faint after using it if it hits. Deals damage equal to your current HP.", MoveText.describe(data.move("finalgambit")!!))
        val explosion = MoveText.describe(data.move("explosion")!!)
        assertTrue(explosion, explosion.startsWith("You faint after using it.") && "if it hits" !in explosion)
        assertEquals("Only super-effective moves and Struggle hurt it.", AbilityText.of("Wonder Guard"))
    }

    @Test fun theNewMovesSpeakAndNothingSaysHasNoEffect() {
        val parting = MoveText.describe(data.move("partingshot")!!)
        assertEquals("Lowers the target's Attack and Sp. Atk by 1. You do not switch out.", parting)
        assertTrue(MoveText.describe(data.move("powder")!!).contains("The target's Fire move this turn fails, and it loses a quarter of its max HP."))
        assertTrue(MoveText.describe(data.move("spectralthief")!!).contains("Steals the target's raised stats for itself before it hits."))
    }

    // ---------------------------------------------------------------- Steadfast

    @Test fun steadfastRisesWhenTheFlinchTakesTheAction() {
        val bite = Move("bite", "bite", Type.DARK, Category.PHYSICAL, 60, 100, flags = setOf("contact", "protect"), secondary = Secondary(100, flinch = true, volatileStatus = "flinch"))
        val d = TestData.data(data.species.values.toList(), data.moves + ("bite" to bite))
        fun fight(status: String? = null, rng: Random = F.low): Pair<Battle, List<GameEvent>> {
            val m = TestData.mon(d, 900, 50, listOf("bite")).also { it.ability = "" }
            val w = TestData.mon(d, 901, 100, listOf("tackle")).also { it.ability = "steadfast" }
            val b = F.battle(w); b.enemy.status = status
            val script = R17C9.Script(ArrayDeque(listOf("bite")), ArrayDeque(listOf("tackle")))
            val ev = BattleRules.using(listOf<BattleRules>(script) + BattleRules.ALL) { BattleEngine.playRound(d, b, m, 1.0, true, rng) }
            return b to ev
        }
        val (b, ev) = fight()
        assertTrue("Slowpoke flinched and couldn't move!" in ev.lines())
        assertEquals(1, b.enemy.boosts[4])
        assertTrue(ev.lines().any { "Steadfast" in it })
        // asleep: the sleep takes the action first, the flinch is never used, so there is no Speed rise (it used to rise when the flinch was set)
        val (asleep, ev2) = fight("slp", F.high)
        assertTrue(ev2.lines().any { "fast asleep" in it })
        assertEquals(0, asleep.enemy.boosts[4])
    }

    // ---------------------------------------------------------------- Comatose

    @Test fun comatoseCountsAsAsleepForSleepTalkAndNightmare() {
        // Sleep Talk calls another move without a status
        val m = F.main("sleeptalk", "tackle", ability = "comatose"); val b = F.battle(F.wild("tackle"))
        val ev = F.round(m, b, "sleeptalk", null)
        assertEquals(listOf("sleeptalk", "tackle"), ev.attacks().filter { it.byPlayer }.map { it.moveId })
        assertTrue(ev.attacks().filter { it.byPlayer }.none { it.failed })
        // ... and fails for an awake one
        val awake = F.round(F.main("sleeptalk", "tackle"), F.battle(F.wild("tackle")), "sleeptalk", null)
        assertTrue(awake.attacks().first { it.byPlayer }.failed)
        // Nightmare works on a Comatose target and hurts it at the end of the round
        val nm = F.main("nightmare"); val w = F.wild("tackle", ability = "comatose"); val nb = F.battle(w)
        val nev = F.round(nm, nb, "nightmare", null)
        assertFalse(nev.attacks().first { it.byPlayer }.failed)
        assertEquals(F.maxHp(w) - F.maxHp(w) / 4, w.hp)
        assertTrue(nev.lines().any { "locked in a nightmare" in it })
        // an awake target without it: the move fails
        assertTrue(F.round(F.main("nightmare"), F.battle(F.wild("tackle")), "nightmare", null).attacks().first { it.byPlayer }.failed)
    }

    // ---------------------------------------------------------------- once per fight

    @Test fun intrepidSwordDauntlessShieldAndSupersweetSyrupFireOncePerFightAndPokemon() {
        fun fight(ability: String, stat: Int, side: (Battle) -> IntArray) {
            val m = F.main("tackle", ability = ability); val b = F.battle(F.wild("tackle"))
            F.round(m, b, "tackle", null)
            assertEquals("$ability on the first entry", if (stat == 6) -1 else 1, side(b)[stat])
            // switched out and in again: the same Pokémon does not trigger it a second time
            b.switchOutMain(data, m)
            if (stat == 6) { b.enemy.boosts[6] = 0 } // (the wild one keeps its drop; start the check from 0)
            else b.player.boosts[stat] = 0
            F.round(m, b, "tackle", null)
            assertEquals("$ability again", 0, side(b)[stat])
            // another Pokémon with the ability does
            val other = F.mon(900, 51, ability, "tackle")
            b.switchOutMain(data, m)
            F.round(other, b, "tackle", null)
            assertEquals("$ability for the next Pokémon", if (stat == 6) -1 else 1, side(b)[stat])
        }
        fight("intrepidsword", 0) { it.player.boosts }
        fight("dauntlessshield", 1) { it.player.boosts }
        fight("supersweetsyrup", 6) { it.enemy.boosts }
    }

    // ---------------------------------------------------------------- notes: the stale texts and the unit tests' own claim

    @Test fun thePlayerNeverLosesAnItemToTheNewRules() {
        // the rules of this batch read items only to decide a pick; no move takes or uses one up
        val m = F.main("poltergeist", "tackle", sp = 905).also { it.heldItem = "leftovers" }
        val b = F.battle(F.wild("poltergeist", "tackle"))
        F.play(m, b, listOf("tackle", "tackle"), listOf("poltergeist", "tackle"))
        assertEquals("leftovers", m.heldItem)
    }
}
