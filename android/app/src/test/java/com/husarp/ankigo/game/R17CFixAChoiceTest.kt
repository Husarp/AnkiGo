package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R17C FIX-A, the move choice (review items 3, 4, 7, 15, 16): RANDOM's Struggle fallback uses the SMART "can hurt" test, the reactive moves are
 * not picked for a round the other side idles in (and not in a switch's free hit), the answers of `useful` combine as "any false wins" (and
 * Substitute, Good as Gold, Soundproof, Magnet Rise, Oblivious ... are considered), Poltergeist is never picked for the Main in RANDOM, and
 * SMARTEST halves the expected damage of a charge or recharge move.
 */
class R17CFixAChoiceTest {
    private val F = R17CFixA
    private val data = F.data

    private fun picks(choice: MoveChoice, main: Mon, wild: Mon, p: Boolean = true, mainActs: Boolean = true, seeds: Int = 120, setup: (Battle) -> Unit = {}): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (seed in 1..seeds) {
            val b = F.battle(wild).also(setup)
            val id = BattleRules.using(BattleRules.ALL) { MoveChooser.pick(choice, F.view(main, b, mainActs), p, Random(seed)) }.id
            out[id] = (out[id] ?: 0) + 1
        }
        return out
    }

    private fun useful(main: Mon, wild: Mon, move: String, p: Boolean = true, mainActs: Boolean = true, setup: (Battle) -> Unit = {}): Boolean? {
        val b = F.battle(wild).also(setup)
        return BattleRules.using(BattleRules.ALL) { rulesUseful(F.view(main, b, mainActs), p, data.move(move)!!) }
    }

    // ---------------------------------------------------------------- item 3: RANDOM's Struggle

    @Test fun randomMainStrugglesWhenEveryAttackIsAbsorbedOrImmune() {
        // Volt Absorb takes the only attack in; Levitate stops the only Ground move; Normalize turns a Ghost move Normal against a Ghost type
        assertEquals(setOf("struggle"), picks(MoveChoice.RANDOM, F.main("thunderbolt"), F.wild("tackle", ability = "voltabsorb")).keys)
        assertEquals(setOf("struggle"), picks(MoveChoice.RANDOM, F.main("earthquake"), F.wild("tackle", ability = "levitate")).keys)
        assertEquals(setOf("struggle"), picks(MoveChoice.RANDOM, F.main("shadowball", ability = "normalize"), F.wild("tackle", sp = 902)).keys)
        // a Wonder Guard target takes nothing from a move that is not super effective
        assertEquals(setOf("struggle"), picks(MoveChoice.RANDOM, F.main("tackle"), F.wild("tackle", ability = "wonderguard")).keys)
    }

    @Test fun randomMainStillPicksAmongAllMovesWhenSomethingCanHurt() {
        val seen = picks(MoveChoice.RANDOM, F.main("earthquake", "tackle"), F.wild("tackle", ability = "levitate")).keys
        assertEquals(setOf("earthquake", "tackle"), seen) // random: the absorbed move is still one of the four
    }

    @Test fun theWildOneKeepsItsRandomPick() {
        val seen = picks(MoveChoice.RANDOM, F.main("tackle"), F.wild("earthquake", "tackle"), p = false).keys
        assertEquals(setOf("earthquake", "tackle"), seen)
    }

    // ---------------------------------------------------------------- item 15: Poltergeist

    @Test fun poltergeistIsNeverPickedForTheMainInAnyMode() {
        val darkWild = { F.wild("tackle", sp = 905) } // a Dark type: Ghost moves hit it, Normal moves too
        for (choice in MoveChoice.entries) {
            val seen = picks(choice, F.main("poltergeist", "tackle"), darkWild()).keys
            assertFalse("$choice: $seen", "poltergeist" in seen)
            assertTrue("$choice: $seen", "tackle" in seen)
        }
        assertEquals(setOf("struggle"), picks(MoveChoice.RANDOM, F.main("poltergeist"), darkWild()).keys)
    }

    @Test fun poltergeistStillWorksForTheWildOneAgainstAnItem() {
        val seen = picks(MoveChoice.RANDOM, F.main("tackle", sp = 905), F.wild("poltergeist", "tackle"), p = false).keys
        assertTrue("poltergeist" in seen) // the wild one's RANDOM pick is unchanged
        val smart = picks(MoveChoice.SMART, F.main("tackle", sp = 905).also { it.heldItem = "leftovers" }, F.wild("poltergeist", "tackle"), p = false).keys
        assertTrue("poltergeist" in smart)
    }

    // ---------------------------------------------------------------- item 4: reactive moves need an action

    private val reactiveDamaging = listOf("counter", "mirrorcoat", "metalburst", "comeuppance", "suckerpunch", "thunderclap")
    private val reactiveStatus = listOf("protect", "detect", "endure", "destinybond", "quickguard", "wideguard", "craftyshield", "mefirst", "snatch", "magiccoat", "electrify")

    @Test fun reactiveMovesAreUsefulOnlyWhenTheOtherSideActs() {
        // the Main knows what the guards and the thieves look for: a priority move, a wide move, a status move, an attack, a self-move
        val main = F.main("quickattack", "earthquake", "thunderwave", "tackle", "swordsdance")
        val wild = F.wild("tackle")
        for (id in reactiveDamaging + reactiveStatus) {
            assertNotEquals("$id with an acting Main", false, useful(main, wild, id, p = false, mainActs = true))
            assertEquals("$id in a round the Main does not act in", false, useful(main, wild, id, p = false, mainActs = false))
        }
        // and for the Main when the wild one idles (a round): the Round says so
        val b = F.battle(F.wild("quickattack", "earthquake", "thunderwave", "tackle", "swordsdance"))
        val verdicts = HashMap<String, Boolean?>()
        val probe = object : BattleRules {
            override fun forcedMove(v: BattleView, p: Boolean): Move? {
                if (p) for (id in reactiveDamaging + reactiveStatus) verdicts[id] = rulesUseful(v, true, data.move(id)!!)
                return null
            }
        }
        BattleRules.using(listOf<BattleRules>(probe) + BattleRules.ALL) { BattleEngine.playRound(data, b, F.main("tackle"), 1.0, false, Random(1)) }
        assertTrue(verdicts.keys.containsAll(reactiveDamaging + reactiveStatus))
        for ((id, v) in verdicts) assertEquals("$id when the wild one idles", false, v)
    }

    @Test fun smartMainDoesNotWasteAnAnswerOnCounterWhenTheWildOneIdles() {
        fun used(enemyActs: Boolean, seed: Int): String {
            val m = F.main("counter", "tackle"); val b = F.battle(F.wild("tackle"))
            val ev = BattleRules.using(BattleRules.ALL) { BattleEngine.playRound(data, b, m, 1.0, enemyActs, Random(seed), moveChoice = MoveChoice.SMART) }
            return ev.filterIsInstance<GameEvent.Attack>().first { it.byPlayer }.moveId
        }
        assertEquals(setOf("tackle"), (1..60).map { used(false, it) }.toSet())
        assertTrue("counter" in (1..60).map { used(true, it) }.toSet()) // when the wild one acts it is one of the attacks
    }

    @Test fun theWildOneDoesNotPickCounterInTheFreeHitOfASwitch() {
        fun used(seed: Int): String {
            val m = F.main("tackle"); val b = F.battle(F.wild("counter", "tackle"))
            val ev = BattleRules.using(BattleRules.ALL) { BattleEngine.switchHit(data, b, m, Random(seed), MoveChoice.SMART, 1.0) }
            return ev.filterIsInstance<GameEvent.Attack>().single().moveId
        }
        assertEquals(setOf("tackle"), (1..60).map { used(it) }.toSet())
        // ... but a Main that acts leaves Counter among the picks
        assertTrue("counter" in picks(MoveChoice.SMART, F.main("tackle"), F.wild("counter", "tackle"), p = false).keys)
    }

    // ---------------------------------------------------------------- item 7: "any false wins" and the missing vetoes

    @Test fun anEarlyTrueNoLongerHidesALaterVeto() {
        val main = F.main("leechseed", "tackle")
        assertEquals(true, useful(main, F.wild("tackle"), "leechseed")) // works against a plain target
        assertEquals(false, useful(main, F.wild("tackle", ability = "goodasgold"), "leechseed"))
        assertEquals(false, useful(main, F.wild("tackle", ability = "sapsipper"), "leechseed"))
        assertEquals(false, useful(F.main("flamethrower", "tackle"), F.wild("tackle", ability = "flashfire"), "flamethrower"))
        // the Smart pick keeps away from such a move
        assertEquals(setOf("tackle"), picks(MoveChoice.SMART, F.main("leechseed", "tackle"), F.wild("tackle", ability = "goodasgold")).keys)
    }

    @Test fun aSubstituteStopsTheStatusMovesButNotSoundOrFieldMoves() {
        val sub = { b: Battle -> b.enemy.volatiles["substitute"] = 40 }
        val m = F.main("thunderwave", "growl", "sandstorm", "swordsdance", "taunt")
        fun ok(id: String, wild: Mon = F.wild("tackle")) = BattleRules.using(BattleRules.ALL) {
            val b = F.battle(wild).also(sub); MoveChooser.statusUseful(MoveChoice.SMART, data.move(id)!!, F.view(m, b), true)
        }
        assertFalse(ok("thunderwave")); assertFalse(ok("taunt"))
        assertTrue("sound goes through", ok("growl"))
        assertTrue("a field move aims at nobody", ok("sandstorm"))
        assertTrue("a move on itself", ok("swordsdance"))
        // an Infiltrator ignores the substitute
        val inf = F.main("thunderwave", ability = "infiltrator")
        assertTrue(BattleRules.using(BattleRules.ALL) { MoveChooser.statusUseful(MoveChoice.SMART, data.move("thunderwave")!!, F.view(inf, F.battle(F.wild("tackle")).also(sub)), true) })
    }

    @Test fun soundproofGoodAsGoldAndTheAbilitiesThatStopAConditionAreRespected() {
        val m = F.main("supersonic", "taunt", "yawn", "confuseray")
        assertEquals(true, useful(m, F.wild("tackle"), "supersonic"))
        assertEquals(false, useful(m, F.wild("tackle", ability = "soundproof"), "supersonic"))
        assertEquals(false, useful(m, F.wild("tackle", ability = "owntempo"), "confuseray"))
        assertEquals(false, useful(m, F.wild("tackle", ability = "goodasgold"), "taunt"))
        assertEquals(false, useful(m, F.wild("tackle", ability = "oblivious"), "taunt"))
        assertEquals(false, useful(m, F.wild("tackle", ability = "aromaveil"), "taunt"))
        assertNotEquals(false, useful(m, F.wild("tackle", "swordsdance"), "taunt"))
        assertEquals(false, useful(m, F.wild("tackle", ability = "insomnia"), "yawn"))
        assertEquals(false, useful(m, F.wild("tackle", ability = "sweetveil"), "yawn"))
        assertEquals(false, useful(m, F.wild("tackle", ability = "comatose"), "yawn"))
        assertNotEquals(false, useful(m, F.wild("tackle"), "yawn"))
    }

    @Test fun theFieldStopsWhatTheTerrainStops() {
        val m = F.main("spore", "yawn", "willowisp", "supersonic", "thunderwave")
        val electric = { b: Battle -> b.field.terrain = "electric"; b.field.terrainTurns = 5 }
        val misty = { b: Battle -> b.field.terrain = "misty"; b.field.terrainTurns = 5 }
        assertEquals(false, useful(m, F.wild("tackle"), "spore", setup = electric))
        assertEquals(false, useful(m, F.wild("tackle"), "yawn", setup = electric))
        assertNotEquals(false, useful(m, F.wild("tackle"), "thunderwave", setup = electric))
        for (id in listOf("willowisp", "thunderwave", "supersonic")) assertEquals(id, false, useful(m, F.wild("tackle"), id, setup = misty))
        // a flying target is not on the ground
        assertNotEquals(false, useful(m, F.wild("tackle", sp = 906), "spore", setup = electric))
    }

    @Test fun smartDoesNotUseEarthquakeIntoAMagnetRise() {
        val m = F.main("earthquake", "tackle")
        val lifted = { b: Battle -> b.enemy.volatiles["magnetrise"] = 4 }
        assertEquals(false, useful(m, F.wild("tackle"), "earthquake", setup = lifted))
        assertNotEquals(false, useful(m, F.wild("tackle"), "earthquake"))
        assertEquals(setOf("tackle"), picks(MoveChoice.SMART, m, F.wild("tackle"), setup = lifted).keys)
        assertEquals(setOf("tackle"), picks(MoveChoice.SMARTEST, m, F.wild("tackle"), setup = lifted).keys)
        // Smack Down style: a Pokémon that is brought down is hit again (the rule is not just "Ground never")
        val down = { b: Battle -> b.enemy.volatiles["magnetrise"] = 4; b.enemy.volatiles["smackdown"] = 1 }
        assertNotEquals(false, useful(m, F.wild("tackle"), "earthquake", setup = down))
    }

    // ---------------------------------------------------------------- item 16: SMARTEST and two-action moves

    @Test fun smartestHalvesTheExpectedDamageOfChargeAndRechargeMoves() {
        val m = F.main("solarbeam", "hyperbeam", "flamethrower"); val b = F.battle(F.wild("tackle"))
        val v = F.view(m, b)
        fun halfOf(id: String, plain: Move): Double = MoveChooser.expectedDamage(v, true, data.move(id)!!) / MoveChooser.expectedDamage(v, true, plain)
        val solar = data.move("solarbeam")!!; val hyper = data.move("hyperbeam")!!
        // the same move under another id and without the flags: one action per hit
        val plainSolar = solar.copy(id = "plainbeam", flags = setOf("protect"))
        val plainHyper = hyper.copy(id = "plainhyper", flags = setOf("protect"), selfVolatile = null)
        assertEquals(0.5, halfOf("solarbeam", plainSolar), 1e-9)
        assertEquals(0.5, halfOf("hyperbeam", plainHyper), 1e-9)
        // in the sun Solar Beam needs no charge turn: not halved
        b.field.weather = "sun"; b.field.weatherTurns = 5
        assertEquals(1.0, MoveChooser.expectedDamage(F.view(m, b), true, solar) / MoveChooser.expectedDamage(F.view(m, b), true, plainSolar), 1e-9)
    }

    @Test fun smartestPrefersAnAttackThatHitsEveryActionOverASlightlyStrongerTwoActionOne() {
        // Solar Beam (120) charges for a turn: 60 per action against Flamethrower's 90
        val seen = picks(MoveChoice.SMARTEST, F.main("solarbeam", "flamethrower"), F.wild("tackle"), seeds = 200)
        assertTrue("$seen", (seen["flamethrower"] ?: 0) > (seen["solarbeam"] ?: 0))
        val sun = picks(MoveChoice.SMARTEST, F.main("solarbeam", "flamethrower"), F.wild("tackle"), seeds = 200) { it.field.weather = "sun"; it.field.weatherTurns = 5 }
        // in the sun Solar Beam is a one-action 120 and Flamethrower a 135 (1.5 x 90): still the second, but only just
        assertTrue("$sun", (sun["solarbeam"] ?: 0) > 0)
        assertNull(seen["struggle"])
    }
}
