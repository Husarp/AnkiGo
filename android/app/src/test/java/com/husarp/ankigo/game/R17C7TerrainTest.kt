package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C7.attacks
import com.husarp.ankigo.game.R17C7.effects
import com.husarp.ankigo.game.R17C7.fields
import com.husarp.ankigo.game.R17C7.kinds
import com.husarp.ankigo.game.R17C7.lines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R17C-7: terrain. The four terrains and their moves (lines, floats, 5 rounds, replace / same again), what each does to GROUNDED Pokémon only
 * (boosts, sleep, statuses and confusion, priority moves, the Grassy Terrain heal), the terrain moves (Terrain Pulse, Expanding Force, Rising
 * Voltage, Misty Explosion, Grassy Glide, Steel Roller, Ice Spinner) and the move choice. Fixtures in [R17C7].
 */
class R17C7TerrainTest {
    private val data = R17C7.data
    private val SLOW = 701
    private val GLIDER = 706 // a Normal / Flying type: never grounded (unless Gravity)
    private fun main(vararg ids: String) = R17C7.main(*ids)
    private fun battle(wildSp: Int = SLOW, vararg ids: String) = R17C7.battle(R17C7.wild(wildSp, *(if (ids.isEmpty()) arrayOf("tackle") else ids)))
    private fun terrain(b: Battle, key: String?, turns: Int = 5) { b.field.terrain = key; b.field.terrainTurns = turns }
    private fun idle(m: Mon, b: Battle, rounds: Int = 1) = R17C7.play(m, b, List(rounds) { null })

    private val starts = listOf(
        Triple("electricterrain", "electric", "An electric current runs across the battlefield!" to "Electric Terrain"),
        Triple("grassyterrain", "grassy", "Grass grew to cover the battlefield!" to "Grassy Terrain"),
        Triple("mistyterrain", "misty", "Mist swirled about the battlefield!" to "Misty Terrain"),
        Triple("psychicterrain", "psychic", "The battlefield got weird!" to "Psychic Terrain"),
    )
    private val ends = mapOf(
        "electric" to "The electricity disappeared from the battlefield.", "grassy" to "The grass disappeared from the battlefield.",
        "misty" to "The mist disappeared from the battlefield.", "psychic" to "The weirdness disappeared from the battlefield.",
    )

    // ---------------------------------------------------------------- the moves

    @Test fun eachTerrainMoveStartsItsTerrainWithItsLineAndFloatLabel() {
        for ((move, key, text) in starts) {
            val b = battle(); val e = R17C7.play(main(move), b, listOf(move)).single()
            val f = e.fields().single { it.stages == 1 }
            assertEquals(move, text.first, f.text)
            assertEquals(move, text.second, f.label)
            assertEquals(move, key, f.stat)
            assertTrue(move, f.onPlayer)
            assertFalse(move, e.attacks().single().failed)
            assertEquals(move, key, b.field.terrain)
            assertEquals("$move: 5 rounds, the round it started in counts", 4, b.field.terrainTurns)
        }
    }

    @Test fun theWildOneStartsATerrainToo() {
        val b = battle(); val e = R17C7.play(main("tackle"), b, listOf(null), listOf("psychicterrain")).single()
        assertEquals("psychic", b.field.terrain)
        assertFalse(e.fields().single().onPlayer)
    }

    @Test fun theSameTerrainAgainFailsAndANewOneReplacesTheOld() {
        val b = battle(); val m = main("grassyterrain")
        val e = R17C7.play(m, b, listOf("grassyterrain", "grassyterrain", "mistyterrain"))
        assertTrue(e[1].attacks().single().failed)
        assertEquals(listOf("But it failed!"), e[1].lines())
        assertEquals("misty", b.field.terrain)
        assertEquals(listOf("Mist swirled about the battlefield!"), e[2].fields().map { it.text })
        assertEquals("the new terrain has its own 5 rounds (4 left after the round it started in)", 4, b.field.terrainTurns)
        val again = battle(); R17C7.play(main("grassyterrain"), again, listOf("grassyterrain", "grassyterrain"))
        assertEquals("the same one again was not restarted", 3, again.field.terrainTurns)
    }

    @Test fun aTerrainLastsFiveRoundsAndEndsWithItsLine() {
        for ((move, key, _) in starts) {
            val b = battle(); val m = main(move)
            val e = R17C7.play(m, b, listOf(move, "tackle", "tackle", "tackle", "tackle", "tackle"))
            for (i in 0..3) assertTrue("$key round ${i + 1}", e[i].fields().none { it.stages < 0 })
            assertEquals(key, listOf(ends.getValue(key)), e[4].fields().filter { it.stages < 0 }.map { it.text })
            assertNull(key, b.field.terrain)
            assertTrue(key, e[5].fields().isEmpty())
        }
    }

    // ---------------------------------------------------------------- Electric Terrain

    @Test fun electricTerrainBoostsElectricMovesOfAGroundedUser() {
        val m = main()
        assertEquals(1.3, R17C7.ratio(m, SLOW, "thunderbolt") { it.terrain = "electric" }, 0.03)
        assertEquals("other types", 1.0, R17C7.ratio(m, SLOW, "plainspecial") { it.terrain = "electric" }, 0.0)
        assertEquals("the wild one too", 1.3, R17C7.ratio(m, SLOW, "thunderbolt", byMain = false) { it.terrain = "electric" }, 0.03)
        assertEquals("a Flying user is not on the ground", 1.0, R17C7.ratio(R17C7.mon(GLIDER, 100), SLOW, "thunderbolt") { it.terrain = "electric" }, 0.0)
        assertEquals("only Electric Terrain", 1.0, R17C7.ratio(m, SLOW, "thunderbolt") { it.terrain = "grassy" }, 0.0)
    }

    @Test fun nobodyOnTheGroundFallsAsleepInElectricTerrain() {
        val b = battle(); terrain(b, "electric")
        val e = R17C7.play(main("hypnosis"), b, listOf("hypnosis"), rng = R17C7.low).single()
        assertNull(b.enemy.status)
        val blocked = e.kinds(EffectKind.BLOCKED).single()
        assertEquals("Slowpoke surrounds itself with electrified terrain!", blocked.text)
        assertFalse(blocked.onPlayer)
        assertEquals("Blocked", blocked.label)
        assertFalse("no extra failed line", e.lines().contains("But it failed!"))
        // a Flying type can fall asleep, and the terrain is not needed for the sleep
        val flyer = battle(GLIDER); terrain(flyer, "electric")
        R17C7.play(main("hypnosis"), flyer, listOf("hypnosis"), rng = R17C7.low)
        assertEquals("slp", flyer.enemy.status)
        val dry = battle(); R17C7.play(main("hypnosis"), dry, listOf("hypnosis"), rng = R17C7.low)
        assertEquals("slp", dry.enemy.status)
        // paralysis is still possible
        val par = battle(); terrain(par, "electric"); R17C7.play(main("thunderwave"), par, listOf("thunderwave"), rng = R17C7.low)
        assertEquals("par", par.enemy.status)
    }

    @Test fun electricTerrainStopsYawnOnTheGround() {
        val m = main("tackle"); val b = battle(); terrain(b, "electric")
        assertFalse(R17C7.round(m, b).addVolatile(false, "yawn", 2, byFoe = true))
        assertTrue("a Flying type", R17C7.round(m, battle(GLIDER).also { terrain(it, "electric") }).addVolatile(false, "yawn", 2, byFoe = true))
        assertTrue("another terrain", R17C7.round(m, battle().also { terrain(it, "misty") }).addVolatile(false, "yawn", 2, byFoe = true))
    }

    // ---------------------------------------------------------------- Grassy Terrain

    @Test fun grassyTerrainBoostsGrassMoves() {
        val m = main()
        assertEquals(1.3, R17C7.ratio(m, SLOW, "energyball") { it.terrain = "grassy" }, 0.03)
        assertEquals(1.0, R17C7.ratio(R17C7.mon(GLIDER, 100), SLOW, "energyball") { it.terrain = "grassy" }, 0.0)
        assertEquals(1.0, R17C7.ratio(m, SLOW, "thunderbolt") { it.terrain = "grassy" }, 0.0)
    }

    @Test fun grassyTerrainHealsEveryGroundedPokemonAtTheEndOfTheRound() {
        val b = battle(); val m = main(); terrain(b, "grassy")
        m.hp -= 100; b.wild.hp -= 100
        val wildMax = R17C7.maxHp(b.wild); val mainMax = R17C7.maxHp(m)
        val before = m.hp to b.wild.hp
        val e = idle(m, b).single()
        val heals = e.kinds(EffectKind.HEAL)
        assertEquals(listOf("Fastie is healed by the grassy terrain!", "Slowpoke is healed by the grassy terrain!"), heals.map { it.text })
        assertEquals(mainMax / 16, heals[0].amount); assertEquals(wildMax / 16, heals[1].amount)
        assertEquals(before.first + mainMax / 16, m.hp); assertEquals(before.second + wildMax / 16, b.wild.hp)
    }

    @Test fun grassyTerrainDoesNotHealAFlyingTypeOrAFullHealthPokemon() {
        val b = battle(GLIDER); val m = main(); terrain(b, "grassy")
        b.wild.hp -= 100
        val before = b.wild.hp
        val e = idle(m, b).single()
        assertTrue("the Flying wild one and the full-health Main", e.kinds(EffectKind.HEAL).isEmpty())
        assertEquals(before, b.wild.hp)
    }

    @Test fun grassyTerrainHealsOnItsLastRoundToo() {
        val b = battle(); val m = main(); terrain(b, "grassy", turns = 1)
        b.wild.hp = 1
        val e = idle(m, b).single()
        assertEquals(1 + R17C7.maxHp(b.wild) / 16, b.wild.hp)
        assertEquals(listOf("The grass disappeared from the battlefield."), e.fields().map { it.text })
        assertEquals("heal first, then the end", listOf(EffectKind.HEAL, EffectKind.FIELD), e.effects().map { it.kind })
    }

    @Test fun earthquakeBulldozeAndMagnitudeDoHalfDamageToAGroundedTargetInGrassyTerrain() {
        val m = main()
        for (move in listOf("earthquake", "bulldoze")) {
            assertEquals(move, 0.5, R17C7.ratio(m, SLOW, move) { it.terrain = "grassy" }, 0.03)
            assertEquals("$move in another terrain", 1.0, R17C7.ratio(m, SLOW, move) { it.terrain = "electric" }, 0.0)
        }
        val b = battle(); terrain(b, "grassy")
        assertEquals(0.5, FieldRules.powerMod(R17C7.hit(b, m, "magnitude")), 0.0)
        assertEquals("a Flying target is not on the ground", 1.0, FieldRules.powerMod(R17C7.hit(battle(GLIDER).also { terrain(it, "grassy") }, m, "magnitude")), 0.0)
        assertEquals("the wild one's quake against the Main", 0.5, R17C7.ratio(m, SLOW, "earthquake", byMain = false) { it.terrain = "grassy" }, 0.03)
    }

    // ---------------------------------------------------------------- Misty Terrain

    @Test fun mistyTerrainHalvesDragonMovesAgainstAGroundedTarget() {
        val m = main()
        assertEquals(0.5, R17C7.ratio(m, SLOW, "dragonpulse") { it.terrain = "misty" }, 0.03)
        assertEquals("a Flying target", 1.0, R17C7.ratio(m, GLIDER, "dragonpulse") { it.terrain = "misty" }, 0.0)
        assertEquals("other types", 1.0, R17C7.ratio(m, SLOW, "plainspecial") { it.terrain = "misty" }, 0.0)
        assertEquals("the wild one's Dragon move against the Main", 0.5, R17C7.ratio(m, SLOW, "dragonpulse", byMain = false) { it.terrain = "misty" }, 0.03)
        assertEquals("a grounded user is not needed", 0.5, R17C7.ratio(R17C7.mon(GLIDER, 100), SLOW, "dragonpulse") { it.terrain = "misty" }, 0.03)
    }

    @Test fun mistyTerrainBlocksEveryStatusOnTheGround() {
        for (move in listOf("thunderwave", "toxic", "hypnosis")) {
            val b = battle(); terrain(b, "misty")
            val e = R17C7.play(main(move), b, listOf(move), rng = R17C7.low).single()
            assertNull(move, b.enemy.status)
            assertEquals(move, listOf("Slowpoke surrounds itself with a protective mist!"), e.kinds(EffectKind.BLOCKED).map { it.text })
            val flyer = battle(GLIDER); terrain(flyer, "misty")
            R17C7.play(main(move), flyer, listOf(move), rng = R17C7.low)
            assertNotNull("$move on a Flying type", flyer.enemy.status)
        }
    }

    @Test fun mistyTerrainStopsAChanceOfAStatusQuietly() {
        val b = battle(); terrain(b, "misty")
        val e = R17C7.play(main("thunder"), b, listOf("thunder"), rng = R17C7.low).single()
        assertNull(b.enemy.status)
        assertTrue("a secondary says nothing", e.kinds(EffectKind.BLOCKED).isEmpty())
    }

    @Test fun mistyTerrainStopsConfusionOnTheGround() {
        val b = battle(); terrain(b, "misty")
        val e = R17C7.play(main("confuseray"), b, listOf("confuseray"), rng = R17C7.low).single()
        assertFalse("confusion" in b.enemy.volatiles)
        assertEquals(listOf("Slowpoke surrounds itself with a protective mist!"), e.lines())
        val flyer = battle(GLIDER); terrain(flyer, "misty")
        R17C7.play(main("confuseray"), flyer, listOf("confuseray"), rng = R17C7.low)
        assertTrue("confusion" in flyer.enemy.volatiles)
        val electric = battle(); terrain(electric, "electric")
        R17C7.play(main("confuseray"), electric, listOf("confuseray"), rng = R17C7.low)
        assertTrue("only Misty Terrain", "confusion" in electric.enemy.volatiles)
    }

    // ---------------------------------------------------------------- Psychic Terrain

    @Test fun psychicTerrainBoostsPsychicMovesOfAGroundedUser() {
        val m = main()
        assertEquals(1.3, R17C7.ratio(m, SLOW, "psychic") { it.terrain = "psychic" }, 0.03)
        assertEquals(1.0, R17C7.ratio(R17C7.mon(GLIDER, 100), SLOW, "psychic") { it.terrain = "psychic" }, 0.0)
        assertEquals(1.0, R17C7.ratio(m, SLOW, "plainspecial") { it.terrain = "psychic" }, 0.0)
    }

    @Test fun psychicTerrainStopsMovesThatGoFirstAgainstAGroundedPokemon() {
        val b = battle(); terrain(b, "psychic"); val m = main("quickattack")
        val e = R17C7.play(m, b, listOf("quickattack")).single()
        val a = e.attacks().single()
        assertTrue(a.failed); assertEquals(0, a.damage)
        assertEquals(R17C7.maxHp(b.wild), b.wild.hp)
        val blocked = e.kinds(EffectKind.BLOCKED).single()
        assertEquals("Slowpoke surrounds itself with psychic terrain!", blocked.text)
        assertEquals("Blocked", blocked.label)
        // a move with no priority gets through, and so does a priority move against a Flying type
        val tackle = battle(); terrain(tackle, "psychic")
        assertTrue(R17C7.play(main("tackle"), tackle, listOf("tackle")).single().attacks().single().damage > 0)
        val flyer = battle(GLIDER); terrain(flyer, "psychic")
        assertTrue(R17C7.play(main("quickattack"), flyer, listOf("quickattack")).single().attacks().single().damage > 0)
        // the wild one is stopped the same way
        val wb = battle(SLOW, "quickattack"); terrain(wb, "psychic")
        val we = R17C7.play(main("tackle"), wb, listOf(null), listOf("quickattack")).single()
        assertTrue(we.attacks().single().failed)
    }

    @Test fun psychicTerrainDoesNotStopAMoveAimedAtTheField() {
        // Ion Deluge has priority +1 but is not aimed at the Pokémon
        val b = battle(); terrain(b, "psychic")
        val e = R17C7.play(main("iondeluge"), b, listOf("iondeluge")).single()
        assertFalse(e.attacks().single().failed)
        assertTrue(e.kinds(EffectKind.BLOCKED).isEmpty())
    }

    // ---------------------------------------------------------------- the terrain moves

    @Test fun terrainPulseTakesTheTerrainsTypeAndDoublesItsPower() {
        val torch = R17C7.mon(711, 100, "terrainpulse") // a Fire user: no STAB with any of the types
        val expected = mapOf("electric" to (Type.ELECTRIC to 2.6), "grassy" to (Type.GRASS to 2.6), "misty" to (Type.FAIRY to 2.0), "psychic" to (Type.PSYCHIC to 2.6))
        for ((key, want) in expected) {
            val b = battle(); terrain(b, key)
            assertEquals(key, want.first, R17C7.hit(b, torch, "terrainpulse").type)
            assertEquals("$key: double power (and the terrain's own boost)", want.second, R17C7.ratio(torch, SLOW, "terrainpulse") { it.terrain = key }, 0.12)
        }
        assertEquals(Type.NORMAL, R17C7.hit(battle(), torch, "terrainpulse").type)
        // a Flying user is not on the ground: nothing changes
        val glider = R17C7.mon(GLIDER, 100, "terrainpulse")
        val b = battle(); terrain(b, "electric")
        assertEquals(Type.NORMAL, R17C7.hit(b, glider, "terrainpulse").type)
        assertEquals(1.0, R17C7.ratio(glider, SLOW, "terrainpulse") { it.terrain = "electric" }, 0.0)
    }

    @Test fun expandingForceRisingVoltageAndMistyExplosionNeedTheirTerrain() {
        val m = main()
        // Expanding Force is Psychic: x1.5 and the terrain's x1.3
        assertEquals(1.95, R17C7.ratio(m, SLOW, "expandingforce") { it.terrain = "psychic" }, 0.1)
        assertEquals(1.0, R17C7.ratio(m, SLOW, "expandingforce") { it.terrain = "electric" }, 0.0)
        assertEquals(1.0, R17C7.ratio(R17C7.mon(GLIDER, 100), SLOW, "expandingforce") { it.terrain = "psychic" }, 0.0)
        // Rising Voltage is Electric: x2 against a grounded target and the terrain's x1.3 for a grounded user
        assertEquals(2.6, R17C7.ratio(m, SLOW, "risingvoltage") { it.terrain = "electric" }, 0.1)
        assertEquals("a Flying target is not doubled", 1.3, R17C7.ratio(m, GLIDER, "risingvoltage") { it.terrain = "electric" }, 0.05)
        assertEquals(1.0, R17C7.ratio(m, SLOW, "risingvoltage") { it.terrain = "grassy" }, 0.0)
        // Misty Explosion is Fairy: x1.5 in Misty Terrain
        assertEquals(1.5, R17C7.ratio(m, SLOW, "mistyexplosion") { it.terrain = "misty" }, 0.05)
        assertEquals(1.0, R17C7.ratio(m, SLOW, "mistyexplosion") { it.terrain = "psychic" }, 0.0)
    }

    @Test fun grassyGlideGoesFirstInGrassyTerrain() {
        fun firstMover(key: String?, mainSp: Int): Boolean {
            val glide = R17C7.mon(mainSp, 100, "grassyglide")
            val b = R17C7.battle(R17C7.mon(713, 100, "tackle")) // the wild one is much faster
            terrain(b, key)
            return R17C7.play(glide, b, listOf("grassyglide"), listOf("tackle")).single().attacks().first().byPlayer
        }
        assertFalse("without a terrain the faster wild one is first", firstMover(null, SLOW))
        assertTrue("in Grassy Terrain the Glide goes first", firstMover("grassy", SLOW))
        assertFalse("another terrain", firstMover("electric", SLOW))
        assertFalse("a Flying user is not on the ground", firstMover("grassy", GLIDER))
    }

    @Test fun steelRollerFailsWithoutATerrainAndEndsOneAfterTheHit() {
        val none = battle(); val e = R17C7.play(main("steelroller"), none, listOf("steelroller")).single()
        assertTrue(e.attacks().single().failed)
        assertEquals(listOf("But it failed!"), e.lines())
        assertEquals(R17C7.maxHp(none.wild), none.wild.hp)

        val b = battle(); terrain(b, "grassy")
        val e2 = R17C7.play(main("steelroller"), b, listOf("steelroller")).single()
        assertTrue(e2.attacks().single().damage > 0)
        assertNull(b.field.terrain)
        assertEquals(listOf("The grass disappeared from the battlefield."), e2.fields().map { it.text })

        // a miss leaves the terrain alone
        val miss = battle(); terrain(miss, "grassy"); val m = main("steelroller"); miss.player.boosts[5] = -6
        val e3 = R17C7.play(m, miss, listOf("steelroller"), rng = R17C7.high).single()
        assertTrue(e3.attacks().single().missed)
        assertEquals("grassy", miss.field.terrain)
    }

    @Test fun iceSpinnerEndsTheTerrainAfterTheHitAndWorksWithoutOne() {
        val b = battle(); terrain(b, "psychic")
        val e = R17C7.play(main("icespinner"), b, listOf("icespinner")).single()
        assertTrue(e.attacks().single().damage > 0)
        assertNull(b.field.terrain)
        assertEquals(listOf("The weirdness disappeared from the battlefield."), e.fields().map { it.text })
        val none = battle(); val e2 = R17C7.play(main("icespinner"), none, listOf("icespinner")).single()
        assertTrue(e2.attacks().single().damage > 0)
        assertTrue(e2.fields().isEmpty())
    }

    // ---------------------------------------------------------------- the move choice and the texts

    @Test fun theSmartChoiceKeepsTerrainMovesForWhenTheyChangeSomething() {
        val m = main("grassyterrain"); val b = battle()
        val v = SimpleView.of(data, b, m)
        for ((move, key, _) in starts) {
            assertTrue(move, MoveChooser.statusUseful(MoveChoice.SMART, data.move(move)!!, v, true))
            terrain(b, key)
            assertFalse("$move while it is already so", MoveChooser.statusUseful(MoveChoice.SMART, data.move(move)!!, v, true))
            terrain(b, null)
        }
        terrain(b, "misty")
        assertTrue("another terrain replaces it", MoveChooser.statusUseful(MoveChoice.SMART, data.move("grassyterrain")!!, v, true))
    }

    @Test fun theSmartChoiceLeavesOutMovesThatCannotWorkInTheTerrain() {
        // Steel Roller needs a terrain; Quick Attack is stopped by Psychic Terrain on the ground
        val m = R17C7.mon(700, 100, "steelroller", "tackle"); val b = battle()
        repeat(20) { assertEquals("tackle", MoveChooser.pick(MoveChoice.SMART, SimpleView.of(data, b, m), true, kotlin.random.Random(it)).id) }
        terrain(b, "grassy")
        val picks = (0 until 40).map { MoveChooser.pick(MoveChoice.SMART, SimpleView.of(data, b, m), true, kotlin.random.Random(it)).id }.toSet()
        assertEquals(setOf("steelroller", "tackle"), picks)

        val q = R17C7.mon(700, 100, "quickattack", "tackle"); val pb = battle(); terrain(pb, "psychic")
        repeat(20) { assertEquals("tackle", MoveChooser.pick(MoveChoice.SMART, SimpleView.of(data, pb, q), true, kotlin.random.Random(it)).id) }
        val fb = battle(GLIDER); terrain(fb, "psychic")
        val seen = (0 until 40).map { MoveChooser.pick(MoveChoice.SMART, SimpleView.of(data, fb, q), true, kotlin.random.Random(it)).id }.toSet()
        assertEquals(setOf("quickattack", "tackle"), seen)
    }

    @Test fun theMoveTextsSayWhatHappens() {
        fun say(id: String) = MoveText.describe(data.move(id)!!)
        for ((move, _, _) in starts) { assertFalse(move, say(move).contains("Has no effect")); assertTrue(move, say(move).contains("5 turns")) }
        assertTrue(say("electricterrain").contains("can't fall asleep"))
        assertTrue(say("grassyterrain").contains("a sixteenth of their max HP"))
        assertTrue(say("mistyterrain").contains("can't get a status or become confused"))
        assertTrue(say("psychicterrain").contains("can't be hit by moves that go first"))
        assertTrue(say("steelroller").contains("Fails unless there is a terrain, and ends the terrain."))
        assertTrue(say("icespinner").contains("Ends the terrain."))
        assertTrue(say("grassyglide").contains("Goes first in Grassy Terrain"))
        assertTrue(say("earthquake").contains("half damage"))
        assertTrue(say("terrainpulse").contains("double power"))
        for (id in listOf("electricterrain", "grassyterrain", "mistyterrain", "psychicterrain", "steelroller", "icespinner", "terrainpulse", "risingvoltage", "expandingforce", "mistyexplosion")) {
            assertTrue("$id ends with a full stop", say(id).endsWith("."))
        }
    }
}
