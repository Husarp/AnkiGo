package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C7.attacks
import com.husarp.ankigo.game.R17C7.effects
import com.husarp.ankigo.game.R17C7.fields
import com.husarp.ankigo.game.R17C7.kinds
import com.husarp.ankigo.game.R17C7.lines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R17C-7: rooms and sports (Trick Room, Gravity, Wonder Room, Mud Sport, Water Sport, Ion Deluge) and the clearing moves (Defog, Court Change,
 * Tidy Up): lines, floats, durations, what each changes, and the move choice. Fixtures in [R17C7].
 */
class R17C7RoomsTest {
    private val data = R17C7.data
    private val SLOW = 701
    private val GLIDER = 706
    private val BEEFY = 715 // low Defense, high Sp. Def
    private fun main(vararg ids: String) = R17C7.main(*ids)
    private fun battle(wildSp: Int = SLOW, vararg ids: String) = R17C7.battle(R17C7.wild(wildSp, *(if (ids.isEmpty()) arrayOf("tackle") else ids)))
    private fun room(b: Battle, key: String, turns: Int = 5) { b.field.rooms[key] = turns }
    private fun view(b: Battle, m: Mon) = SimpleView.of(data, b, m)

    private val starts = listOf(
        Triple("trickroom", "Fastie twisted the dimensions!", "Trick Room"),
        Triple("gravity", "Gravity intensified!", "Gravity"),
        Triple("wonderroom", "It created a bizarre area in which the Defense and Sp. Def stats are swapped!", "Wonder Room"),
        Triple("mudsport", "Electricity's power was weakened!", "Mud Sport"),
        Triple("watersport", "Fire's power was weakened!", "Water Sport"),
    )
    private val ends = mapOf(
        "trickroom" to "The twisted dimensions returned to normal!", "gravity" to "Gravity returned to normal!",
        "wonderroom" to "Wonder Room wore off, and the Defense and Sp. Def stats returned to normal!",
        "mudsport" to "The effects of Mud Sport have faded.", "watersport" to "The effects of Water Sport have faded.",
    )

    // ---------------------------------------------------------------- starting, repeating, ending

    @Test fun eachRoomMoveStartsItsRoomWithItsLineAndFloatLabel() {
        for ((move, text, label) in starts) {
            val b = battle(); val e = R17C7.play(main(move), b, listOf(move)).single()
            val f = e.fields().single { it.stages == 1 }
            assertEquals(move, text, f.text); assertEquals(move, label, f.label); assertEquals(move, move, f.stat); assertTrue(move, f.onPlayer)
            assertFalse(move, e.attacks().single().failed)
            assertEquals("$move: 5 rounds, the round it started in counts", 4, b.field.rooms[move])
        }
        val b = battle(); val e = R17C7.play(main("tackle"), b, listOf(null), listOf("gravity")).single()
        assertFalse("the wild one starts rooms too", e.fields().single().onPlayer)
    }

    @Test fun gravityAndTheSportsFailWhenAlreadyUpWhileTheTwoRoomsSwitchOff() {
        for (move in listOf("gravity", "mudsport", "watersport")) {
            val b = battle(); val e = R17C7.play(main(move), b, listOf(move, move))
            assertTrue(move, e[1].attacks().single().failed)
            assertEquals(move, listOf("But it failed!"), e[1].lines())
            assertEquals("$move was not restarted", 3, b.field.rooms[move])
        }
        for (move in listOf("trickroom", "wonderroom")) {
            val b = battle(); val e = R17C7.play(main(move), b, listOf(move, move))
            assertFalse(move, e[1].attacks().single().failed)
            assertEquals(move, listOf(ends.getValue(move)), e[1].fields().map { it.text })
            assertEquals(-1, e[1].fields().single().stages)
            assertNull("$move is off", b.field.rooms[move])
        }
    }

    @Test fun aRoomLastsFiveRoundsAndEndsWithItsLine() {
        for ((move, _, _) in starts) {
            val b = battle(); val m = main(move)
            val e = R17C7.play(m, b, listOf(move, "tackle", "tackle", "tackle", "tackle", "tackle"))
            for (i in 0..3) assertTrue("$move round ${i + 1}", e[i].fields().none { it.stages < 0 })
            assertEquals(move, listOf(ends.getValue(move)), e[4].fields().filter { it.stages < 0 }.map { it.text })
            assertNull(move, b.field.rooms[move])
            assertTrue(move, e[5].fields().isEmpty())
        }
    }

    @Test fun theCountdownLeavesRoomsOfOtherSetsAlone() {
        val b = battle(); room(b, "magicroom", 3)
        R17C7.play(main("tackle"), b, listOf("tackle", "tackle", "tackle", "tackle"))
        assertEquals(3, b.field.rooms["magicroom"])
    }

    // ---------------------------------------------------------------- Trick Room

    @Test fun trickRoomMakesTheSlowerPokemonMoveFirstForFourMoreRounds() {
        val b = battle(SLOW, "tackle"); val m = main("trickroom", "tackle")
        val six = List(6) { "tackle" }
        val e = R17C7.play(m, b, listOf("trickroom") + six.drop(1), six)
        assertEquals("Trick Room goes last (priority -7)", listOf(false, true), e[0].attacks().map { it.byPlayer })
        for (i in 1..4) assertEquals("round ${i + 1}: the slower wild one first", listOf(false, true), e[i].attacks().map { it.byPlayer })
        assertEquals("round 6: back to normal", listOf(true, false), e[5].attacks().map { it.byPlayer })
    }

    @Test fun withoutTrickRoomTheFasterOneIsFirst() {
        val b = battle(); val e = R17C7.play(main("tackle"), b, listOf("tackle"), listOf("tackle")).single()
        assertEquals(listOf(true, false), e.attacks().map { it.byPlayer })
    }

    @Test fun trickRoomIsSymmetricAndPriorityStillCounts() {
        val b = battle(); room(b, "trickroom")
        val e = R17C7.play(main("quickattack"), b, listOf("quickattack"), listOf("tackle")).single()
        assertEquals("a move that goes first still does", listOf(true, false), e.attacks().map { it.byPlayer })
        // the wild one's Trick Room turns the Main's order around too
        val w = battle(SLOW, "trickroom"); val m = main("tackle")
        val e2 = R17C7.play(m, w, listOf("tackle", "tackle"), listOf("trickroom", "tackle"))
        assertEquals("round 1: the Main is faster", true, e2[0].attacks().first().byPlayer)
        assertEquals("round 2: the wild one is first now", false, e2[1].attacks().first().byPlayer)
    }

    // ---------------------------------------------------------------- Gravity

    @Test fun gravityPutsEveryoneOnTheGround() {
        val m = main("tackle"); val b = battle(GLIDER)
        assertFalse(view(b, m).grounded(false))
        room(b, "gravity")
        assertTrue(view(b, m).grounded(false)); assertTrue(view(b, m).grounded(true))
    }

    @Test fun aGroundMoveHitsAFlyingTypeUnderGravity() {
        val m = main("earthquake"); val b = battle(GLIDER)
        assertEquals("No gravity: it does nothing", 0, R17C7.dmg(b, m, "earthquake"))
        room(b, "gravity")
        assertTrue("Gravity: it hits", R17C7.dmg(b, m, "earthquake") > 0)
        // the Main's Flying type too
        val glider = R17C7.mon(GLIDER, 100, "tackle")
        val wb = R17C7.battle(R17C7.mon(701, 100, "earthquake")); room(wb, "gravity")
        assertTrue("the wild one hits the Main's Flying type as well", R17C7.dmg(wb, glider, "earthquake", byMain = false) > 0)
    }

    @Test fun gravityRaisesEveryAccuracyByTwoThirds() {
        val m = main(); val b = battle(); room(b, "gravity")
        assertEquals(150.0, FieldRules.accuracy(R17C7.hit(b, m, "highjumpkick"), 90.0)!!, 0.01)
        assertEquals(100.0, FieldRules.accuracy(R17C7.hit(b, m, "plainspecial"), 60.0)!!, 0.01)
        assertNull("never misses stays so", FieldRules.accuracy(R17C7.hit(b, m, "plainspecial"), null))
        field(b, "sun")
        assertEquals("after the weather: 50 x 5/3", 50.0 * 5 / 3, FieldRules.accuracy(R17C7.hit(b, m, "thunder"), 70.0)!!, 0.01)
    }

    private fun field(b: Battle, weather: String?) { b.field.weather = weather; b.field.weatherTurns = 5 }

    @Test fun aSixtyPercentMoveHitsUnderGravityEvenOnTheWorstRoll() {
        val dry = battle(); R17C7.play(main("hypnosis"), dry, listOf("hypnosis"), rng = R17C7.high)
        assertNull("misses without gravity", dry.enemy.status)
        val g = battle(); room(g, "gravity"); R17C7.play(main("hypnosis"), g, listOf("hypnosis"), rng = R17C7.high)
        assertEquals("slp", g.enemy.status)
    }

    @Test fun gravityStopsFlyBounceHighJumpKickAndMagnetRise() {
        for (id in listOf("fly", "bounce", "highjumpkick", "magnetrise")) {
            val b = battle(); room(b, "gravity"); val m = main(id)
            val e = R17C7.play(m, b, listOf(id)).single()
            val name = data.move(id)!!.name
            assertTrue(id, e.attacks().single().failed)
            assertEquals(id, listOf("Fastie can't use $name because of gravity!"), e.lines())
            assertEquals(id, R17C7.maxHp(b.wild), b.wild.hp)
        }
        // a move without flags is stopped by its id
        val splash = data.move("splash")!!
        assertEquals("because of gravity", FieldRules.moveBlocked(view(battle(), main()).also { it.field.rooms["gravity"] = 5 }, true, splash))
        assertNull(FieldRules.moveBlocked(view(battle(), main()), true, splash))
        // other moves are not stopped
        val b = battle(); room(b, "gravity")
        assertTrue(R17C7.play(main("tackle"), b, listOf("tackle")).single().attacks().single().damage > 0)
    }

    @Test fun gravityEndsAChargeInProgress() {
        val m = main("fly"); val b = battle()
        R17C7.play(m, b, listOf("fly"))
        assertEquals("fly", b.player.charging)
        room(b, "gravity")
        val e = R17C7.play(m, b, listOf("fly")).single()
        assertTrue(e.attacks().single().failed)
        assertNull(b.player.charging)
        assertEquals(listOf("Fastie can't use Fly because of gravity!"), e.lines())
    }

    @Test fun theMoveChoiceLeavesOutWhatGravityStops() {
        val m = main("fly", "tackle"); val b = battle(); room(b, "gravity")
        for (c in MoveChoice.entries) repeat(15) { assertEquals("$c", "tackle", MoveChooser.pick(c, view(b, m), true, Random(it)).id) }
        val free = (0 until 40).map { MoveChooser.pick(MoveChoice.RANDOM, view(battle(), m), true, Random(it)).id }.toSet()
        assertEquals(setOf("fly", "tackle"), free)
    }

    // ---------------------------------------------------------------- Wonder Room, Mud Sport, Water Sport

    @Test fun wonderRoomSwapsDefenseAndSpDefOfEveryPokemon() {
        val m = main("tackle"); val b = battle(BEEFY); val plain = StatCalc.stats(data, b.wild)
        assertEquals(plain, view(b, m).stats(false))
        room(b, "wonderroom")
        val s = view(b, m).stats(false)
        assertEquals(plain.spd, s.def); assertEquals(plain.def, s.spd)
        assertEquals(plain.atk, s.atk); assertEquals(plain.hp, s.hp)
        val mine = StatCalc.stats(data, m); val ms = view(b, m).stats(true)
        assertEquals(mine.def, ms.spd); assertEquals(mine.spd, ms.def)
    }

    @Test fun wonderRoomMakesPhysicalMovesHitSpDefAndSpecialMovesDefense() {
        val m = main()
        val physical = R17C7.ratio(m, BEEFY, "plainphysical") { it.rooms["wonderroom"] = 5 }
        val special = R17C7.ratio(m, BEEFY, "plainspecial") { it.rooms["wonderroom"] = 5 }
        assertTrue("physical hits the high Sp. Def now: $physical", physical < 0.25)
        assertTrue("special hits the low Defense now: $special", special > 4.0)
        assertEquals("a Pokémon with equal defenses is not changed", 1.0, R17C7.ratio(m, SLOW, "plainphysical") { it.rooms["wonderroom"] = 5 }, 0.0)
        assertTrue("the wild one's hits on a Main with unequal defenses", R17C7.ratio(R17C7.mon(BEEFY, 100), SLOW, "plainphysical", byMain = false) { it.rooms["wonderroom"] = 5 } < 0.25)
    }

    @Test fun mudSportAndWaterSportCutAThirdOfElectricAndFireMoves() {
        val m = main()
        assertEquals(1.0 / 3, R17C7.ratio(m, SLOW, "thunderbolt") { it.rooms["mudsport"] = 5 }, 0.05)
        assertEquals("the wild one's too", 1.0 / 3, R17C7.ratio(m, SLOW, "thunderbolt", byMain = false) { it.rooms["mudsport"] = 5 }, 0.05)
        assertEquals(1.0, R17C7.ratio(m, SLOW, "flamethrower") { it.rooms["mudsport"] = 5 }, 0.0)
        assertEquals(1.0 / 3, R17C7.ratio(m, SLOW, "flamethrower") { it.rooms["watersport"] = 5 }, 0.05)
        assertEquals(1.0, R17C7.ratio(m, SLOW, "thunderbolt") { it.rooms["watersport"] = 5 }, 0.0)
        assertEquals(1.0, R17C7.ratio(m, SLOW, "hydropump") { it.rooms["watersport"] = 5 }, 0.0)
    }

    // ---------------------------------------------------------------- Ion Deluge

    @Test fun ionDelugeTurnsNormalMovesIntoElectricOnesForTheRestOfTheRound() {
        val m = R17C7.mon(703, 100, "iondeluge") // a Ground type: immune to Electric moves; Ion Deluge goes first
        val b = R17C7.battle(R17C7.mon(701, 100, "tackle"))
        val e = R17C7.play(m, b, listOf("iondeluge", "tackle"), listOf("tackle", "tackle"))
        val f = e[0].fields().single()
        assertEquals("A deluge of ions showers the battlefield!", f.text); assertEquals("Ion Deluge", f.label)
        val hitsOnMain = { r: List<GameEvent> -> r.attacks().single { !it.byPlayer } }
        assertEquals("round 1: the wild Tackle is Electric now", 0.0, hitsOnMain(e[0]).effectiveness, 0.0)
        assertEquals(0, hitsOnMain(e[0]).damage)
        assertTrue("it is gone in round 2", b.field.rooms.isEmpty())
        assertTrue(hitsOnMain(e[1]).damage > 0)
        assertTrue("no end line", e[0].fields().none { it.stages < 0 })
    }

    @Test fun ionDelugeOnlyChangesNormalMoves() {
        val m = main(); val b = battle(); room(b, "iondeluge", 1)
        assertEquals(Type.ELECTRIC, R17C7.hit(b, m, "tackle").type)
        assertEquals(Type.WATER, R17C7.hit(b, m, "hydropump").type)
        assertEquals(Type.ELECTRIC, R17C7.hit(b, m, "weatherball").type)
        field(b, "rain")
        assertEquals("Weather Ball in rain is Water, not Normal", Type.WATER, R17C7.hit(b, m, "weatherball").type)
    }

    @Test fun ionDelugeFailsWhenAlreadyUp() {
        val b = battle(); room(b, "iondeluge", 1)
        val e = R17C7.play(main("iondeluge"), b, listOf("iondeluge")).single()
        assertTrue(e.attacks().single().failed)
    }

    @Test fun plasmaFistsStartsIonDelugeAfterItsHit() {
        val b = battle(); val e = R17C7.play(main("plasmafists"), b, listOf("plasmafists")).single()
        assertTrue(e.attacks().single().damage > 0)
        assertEquals(listOf("A deluge of ions showers the battlefield!"), e.fields().map { it.text })
        assertTrue("and it ends with the round", b.field.rooms.isEmpty())
    }

    // ---------------------------------------------------------------- Defog

    @Test fun defogLowersEvasionAndEndsTheTargetsSideConditionsAndTheTerrain() = R17C7.isolated {
        val b = battle(); val m = main("defog")
        for (key in listOf("reflect", "lightscreen", "auroraveil", "safeguard", "mist", "tailwind")) b.field.enemy[key] = 3
        b.field.player["reflect"] = 4
        b.field.terrain = "grassy"; b.field.terrainTurns = 3
        val e = R17C7.play(m, b, listOf("defog")).single()
        assertEquals(-1, b.enemy.boosts[6])
        assertEquals(listOf("Slowpoke's evasiveness fell!"), e.kinds(EffectKind.STAT_CHANGE).map { it.text })
        val gone = e.fields().filter { it.stages < 0 }.map { it.text }
        assertEquals(
            listOf("Slowpoke's Reflect wore off!", "Slowpoke's Light Screen wore off!", "Slowpoke's Aurora Veil wore off!", "Slowpoke's Safeguard wore off!",
                "Slowpoke's Mist faded!", "The grass disappeared from the battlefield."), gone,
        )
        assertEquals("Tailwind stays", mapOf("tailwind" to 3), b.field.enemy.toMap())
        assertEquals("the user's own side stays", mapOf("reflect" to 4), b.field.player.toMap())
        assertNull(b.field.terrain)
        assertFalse(e.attacks().single().failed)
    }

    @Test fun theWildOnesDefogClearsTheMainsSide() = R17C7.isolated {
        val b = battle(SLOW, "defog"); b.field.player["lightscreen"] = 2
        val e = R17C7.play(main("tackle"), b, listOf(null), listOf("defog")).single()
        assertTrue(b.field.player.isEmpty())
        assertEquals(-1, b.player.boosts[6])
        assertTrue(e.fields().any { it.text == "Fastie's Light Screen wore off!" })
    }

    @Test fun defogOnlyEvasionWorksWithoutAnythingToEnd() {
        val b = battle(); val e = R17C7.play(main("defog"), b, listOf("defog")).single()
        assertEquals(-1, b.enemy.boosts[6])
        assertFalse(e.attacks().single().failed)
        // evasion at the floor and nothing else: it fails
        val floor = battle(); floor.enemy.boosts[6] = -6
        val e2 = R17C7.play(main("defog"), floor, listOf("defog")).single()
        assertTrue(e2.attacks().single().failed)
        assertEquals(-6, floor.enemy.boosts[6])
        // evasion at the floor, but a terrain to end: it works
        val t = battle(); t.enemy.boosts[6] = -6; t.field.terrain = "misty"; t.field.terrainTurns = 4
        assertFalse(R17C7.play(main("defog"), t, listOf("defog")).single().attacks().single().failed)
        assertNull(t.field.terrain)
    }

    // ---------------------------------------------------------------- Court Change

    @Test fun courtChangeSwapsTheConditionsOfTheTwoSides() = R17C7.isolated {
        val b = battle(); val m = main("courtchange")
        b.field.player["reflect"] = 3
        b.field.enemy["tailwind"] = 2; b.field.enemy["reflect"] = 5
        b.field.player["wish"] = 1; b.field.enemy["other"] = 2 // not side conditions of the kind that swap
        val e = R17C7.play(m, b, listOf("courtchange")).single()
        assertEquals(mapOf("reflect" to 5, "tailwind" to 2, "wish" to 1), b.field.player.toMap())
        assertEquals(mapOf("reflect" to 3, "other" to 2), b.field.enemy.toMap())
        val f = e.fields().single()
        assertEquals("Fastie swapped the battle effects affecting each side!", f.text); assertEquals("Court Change", f.label)
    }

    @Test fun aMoveAimedAtTheWholeFieldNeverMisses() = R17C7.isolated {
        // Court Change has an accuracy of 100 in the data: an Accuracy drop of the user must not make it miss
        val b = battle(); b.player.boosts[5] = -6; b.field.enemy["reflect"] = 2
        val e = R17C7.play(main("courtchange"), b, listOf("courtchange"), rng = R17C7.high).single()
        assertFalse(e.attacks().single().missed)
        assertEquals(mapOf("reflect" to 2), b.field.player.toMap())
    }

    @Test fun courtChangeFailsWhenThereIsNothingToSwap() = R17C7.isolated {
        val b = battle(); b.field.player["wish"] = 1
        val e = R17C7.play(main("courtchange"), b, listOf("courtchange")).single()
        assertTrue(e.attacks().single().failed)
        assertEquals(listOf("But it failed!"), e.lines())
        assertEquals(mapOf("wish" to 1), b.field.player.toMap())
    }

    // ---------------------------------------------------------------- Tidy Up

    @Test fun tidyUpRaisesAttackAndSpeedAndRemovesEverySubstitute() {
        val b = battle(); val m = main("tidyup")
        b.player.volatiles["substitute"] = 20; b.enemy.volatiles["substitute"] = 30
        val e = R17C7.play(m, b, listOf("tidyup")).single()
        assertEquals(1, b.player.boosts[0]); assertEquals(1, b.player.boosts[4])
        assertFalse("substitute" in b.player.volatiles); assertFalse("substitute" in b.enemy.volatiles)
        assertEquals("Tidying up complete!", e.kinds(EffectKind.VOLATILE).single().text)
        assertFalse(e.attacks().single().failed)
    }

    @Test fun tidyUpWithoutASubstituteJustRaisesTheStats() {
        val b = battle(); val e = R17C7.play(main("tidyup"), b, listOf("tidyup")).single()
        assertEquals(1, b.player.boosts[0])
        assertTrue(e.kinds(EffectKind.VOLATILE).isEmpty())
        val capped = battle(); capped.player.boosts[0] = 6; capped.player.boosts[4] = 6
        assertTrue(R17C7.play(main("tidyup"), capped, listOf("tidyup")).single().attacks().single().failed)
    }

    // ---------------------------------------------------------------- the move choice and the texts

    @Test fun trickRoomIsUsefulOnlyWhenItTurnsTheOrderInYourFavour() {
        val trick = data.move("trickroom")!!
        fun useful(main: Mon, wild: Mon, p: Boolean, room: Boolean): Boolean {
            val b = R17C7.battle(wild); if (room) room(b, "trickroom")
            return MoveChooser.statusUseful(MoveChoice.SMART, trick, view(b, main), p)
        }
        val fast = main(); val slow = R17C7.mon(701, 100)
        assertFalse("the faster Main already moves first", useful(fast, slow, true, room = false))
        assertTrue("the slower wild one would move first", useful(fast, slow, false, room = false))
        assertTrue("in Trick Room the faster Main moves last: end it", useful(fast, slow, true, room = true))
        assertFalse("in Trick Room the slower wild one moves first already", useful(fast, slow, false, room = true))
        assertFalse("same speed: nothing changes", useful(main(), R17C7.mon(700, 100), true, room = false))
    }

    @Test fun otherRoomsAreUsefulOnlyWhileTheyAreNotUp() {
        val m = main(); val b = battle(); val v = view(b, m)
        for (id in listOf("gravity", "wonderroom", "mudsport", "watersport", "iondeluge")) {
            assertTrue(id, MoveChooser.statusUseful(MoveChoice.SMART, data.move(id)!!, v, true))
            room(b, id)
            assertFalse("$id is up", MoveChooser.statusUseful(MoveChoice.SMART, data.move(id)!!, v, true))
            b.field.rooms.clear()
        }
    }

    @Test fun defogCourtChangeAndTidyUpAreUsefulOnlyWhenTheyChangeSomething() {
        val m = main(); val b = battle(); val v = view(b, m)
        fun useful(id: String, p: Boolean = true) = MoveChooser.statusUseful(MoveChoice.SMART, data.move(id)!!, v, p)
        assertTrue("Defog can still lower evasion", useful("defog"))
        b.enemy.boosts[6] = -2
        assertFalse("evasion is at the smart limit and nothing else is up", useful("defog"))
        b.field.enemy["safeguard"] = 2
        assertTrue("a Safeguard to end", useful("defog"))
        b.field.enemy.clear(); b.field.terrain = "electric"
        assertTrue("a terrain to end", useful("defog"))

        b.field.player.clear(); b.field.enemy.clear()
        assertFalse("nothing to swap", useful("courtchange"))
        b.field.enemy["reflect"] = 3
        assertTrue("they have a screen you lack", useful("courtchange"))
        b.field.player["tailwind"] = 2
        assertFalse("you would give your Tailwind away", useful("courtchange"))

        assertTrue(useful("tidyup"))
        b.player.boosts[0] = 6; b.player.boosts[4] = 6
        assertFalse(useful("tidyup"))
    }

    @Test fun theMoveTextsSayWhatHappens() {
        fun say(id: String) = MoveText.describe(data.move(id)!!)
        for (id in listOf("trickroom", "gravity", "wonderroom", "mudsport", "watersport", "iondeluge", "defog", "courtchange", "tidyup")) {
            assertFalse(id, say(id).contains("Has no effect"))
            assertTrue("$id ends with a full stop", say(id).endsWith("."))
        }
        assertTrue(say("trickroom").startsWith("For 5 turns the slower Pokémon moves first. Using it again ends it."))
        assertTrue(say("gravity").contains("Fly, Bounce, Sky Drop, High Jump Kick"))
        assertTrue(say("wonderroom").contains("swapped"))
        assertTrue(say("mudsport").contains("Electric moves deal a third"))
        assertTrue(say("watersport").contains("Fire moves deal a third"))
        assertTrue(say("iondeluge").contains("Normal moves become Electric moves"))
        assertTrue(say("defog").contains("Ends Reflect, Light Screen, Aurora Veil, Safeguard and Mist on the target's side, and the terrain."))
        assertTrue(say("courtchange").contains("Swaps"))
        assertTrue(say("tidyup").contains("Raises your Attack and Speed by 1 and removes all substitutes."))
        assertTrue(say("plasmafists").contains("become Electric moves"))
    }

    @Test fun everyMoveTheSetHandlesHasAFixtureMove() {
        for (id in FieldRules.handles) assertTrue(id, data.move(id) != null)
    }
}
