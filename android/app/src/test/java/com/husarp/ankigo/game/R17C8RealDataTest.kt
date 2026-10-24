package com.husarp.ankigo.game

import com.husarp.ankigo.game.data.Parsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * R17C-8 with the real game data (run with --real; skipped otherwise): every move the rules name exists and says something, the flags the rules
 * read are in the data, Metronome's pool is the data's `metronome` flag minus what cannot be called, and a real fight with each of the moves plays
 * through without a crash.
 */
class R17C8RealDataTest {
    private fun real(): GameData? {
        val dir = System.getenv("ANKIGO_REAL_DATA")?.takeIf { it.isNotBlank() }?.let { File(it) } ?: return null
        fun text(vararg names: String) = names.map { File(dir, it) }.first { it.isFile }.readText()
        return Parsers.build(Parsers.Sources(
            pokedexJson = text("pokedex.json"), movesJson = text("moves.json"), learnsetsJson = text("learnsets.json"),
            speciesCsv = text("pokemon_species.csv", "species.csv"), pokemonCsv = text("pokemon.csv"), statsCsv = text("pokemon_stats.csv"),
            evolutionCsv = text("pokemon_evolution.csv"), experienceCsv = text("experience.csv"), itemsCsv = text("items.csv"),
        ))
    }

    private val damagingToo = listOf("throatchop", "burnup", "doubleshock", "smackdown", "thousandarrows")

    @Test fun everyMoveTheRulesNameExistsAndSaysSomething() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        assertEquals(55, MoveControl.handles.size) // 53 + Parting Shot and Powder (R17C FIX-A)
        for (id in MoveControl.handles) {
            val m = d.move(id) ?: error("$id is not in the real data")
            assertFalse("$id is a status move", m.damaging)
            assertFalse("$id is never picked", id in CoreRules.NEVER_PICKED)
            val text = MoveText.describe(m)
            assertTrue("$id: $text", text.endsWith(".") && "Has no effect" !in text && "→" !in text)
        }
        for (id in damagingToo) {
            val m = d.move(id) ?: error("$id is not in the real data")
            assertTrue("$id says its extra", MoveControl.describe(m).isNotEmpty())
        }
    }

    @Test fun theDataCarriesTheFlagsAndFieldsTheRulesRead() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        fun flags(id: String) = d.move(id)!!.flags
        assertTrue("reflectable" in flags("taunt") && "reflectable" in flags("toxic") && "reflectable" !in flags("heartswap"))
        assertTrue("snatch" in flags("swordsdance") && "snatch" in flags("recover"))
        assertTrue("heal" in flags("recover") && "heal" in flags("roost") && "heal" in flags("gigadrain"))
        assertTrue("sound" in flags("hypervoice") && "sound" in flags("growl"))
        assertTrue("mirror" in flags("flamethrower") && "mirror" in flags("ember"))
        assertTrue("failencore" in flags("encore") && "failmimic" in flags("metronome") && "failcopycat" in flags("copycat") && "nosketch" in flags("sketch"))
        assertTrue("nosleeptalk" in flags("metronome") && "failmefirst" in flags("mefirst"))
        assertTrue("gravity" in flags("magnetrise") && "gravity" in flags("telekinesis"))
        assertTrue(d.move("sleeptalk")!!.sleepUsable)
        assertTrue(d.move("thousandarrows")!!.ignoreImmunity)
        assertEquals("smackdown", d.move("smackdown")!!.volatileStatus); assertEquals("smackdown", d.move("thousandarrows")!!.volatileStatus)
        assertEquals("roost", d.move("roost")!!.selfVolatile)
        assertEquals(4, d.move("snatch")!!.priority); assertEquals(4, d.move("magiccoat")!!.priority)
        assertEquals(mapOf("spd" to 1), d.move("charge")!!.boosts); assertEquals(mapOf("def" to 1), d.move("defensecurl")!!.boosts)
        assertEquals(mapOf("evasion" to 2), d.move("minimize")!!.boosts); assertEquals(mapOf("spe" to -1), d.move("tarshot")!!.boosts)
        assertNotNull(d.move("throatchop")!!.secondary)
    }

    @Test fun metronomeCallsTheFlaggedMovesThatCanBeCalled() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        val flagged = d.moves.values.count { "metronome" in it.flags }
        val pool = MoveControl.metronomePool(d)
        assertTrue("flagged $flagged", flagged > 500)
        assertTrue("pool ${pool.size}", pool.size in 400..flagged)
        val ids = pool.map { it.id }.toSet()
        assertTrue(ids.none { it in CoreRules.NEVER_PICKED })
        assertTrue(pool.none { CoreRules.isCharge(it) || CoreRules.isRecharge(it) })
        for (id in listOf("metronome", "sleeptalk", "copycat", "mirrormove", "mefirst", "naturepower", "assist", "transform", "mimic", "sketch", "struggle", "splash", "fly", "hyperbeam"))
            assertFalse("$id can't be called", id in ids)
        assertTrue("tackle" in ids && "flamethrower" in ids && "taunt" in ids)
    }

    @Test fun aRealFightWithEveryMoveOfTheSetPlaysThrough() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        fun fighter(species: Int, level: Int, vararg ids: String): Mon = TestData.mon(d, species, level, ids.toList())
        var played = 0
        for (id in MoveControl.handles + damagingToo) {
            for (seed in 1..3) {
                val main = fighter(25, 50, "thunderbolt", "quickattack", "surf")
                val wild = fighter(143, 50, "bodyslam", "earthquake", "hypervoice")
                val b = Battle(wild, Tier.NORMAL)
                if (seed == 2) { main.hp = main.hp / 2; if (id == "sleeptalk") b.player.status = "slp" } // hurt (heal paths), asleep for Sleep Talk
                if (seed == 3) { b.enemy.status = "psn"; b.enemy.boosts[0] = 2; b.field.terrain = "grassy"; b.field.terrainTurns = 4 }
                val events = R17C8.withRules(R17C8.forcing(id)) {
                    (1..2).flatMap { BattleEngine.playRound(d, b, main, 1.0, true, Random(seed * 31 + it), enemyPower = 0.2, moveChoice = MoveChoice.SMART) }
                }
                assertTrue("$id seed $seed", events.any { it is GameEvent.Attack && it.byPlayer && it.moveId == id })
                played++
            }
        }
        assertTrue(played > 150)
    }

    @Test fun aRealDittoTurnsIntoItsOpponent() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        val ditto = TestData.mon(d, 132, 30, listOf("transform"))
        val wild = TestData.mon(d, 25, 40, listOf("thunderbolt", "quickattack"))
        val b = Battle(wild, Tier.NORMAL)
        val events = BattleEngine.playRound(d, b, ditto, 1.0, false, Random(2))
        assertTrue(events.any { it is GameEvent.Attack && it.byPlayer && it.moveId == "transform" && !it.failed })
        val v = SimpleView.of(d, b, ditto)
        assertEquals(listOf(Type.ELECTRIC), v.types(true))
        assertEquals(listOf("thunderbolt", "quickattack"), v.moves(true).map { it.id })
        assertEquals(v.stats(false).atk, v.stats(true).atk)
        assertEquals(d.sp(25).weightKg, MoveControl.transformedWeight(v, ditto))
    }

    @Test fun aRealSmeargleSketchesAndThenFights() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        val smeargle = TestData.mon(d, 235, 30, listOf("sketch"))
        val wild = TestData.mon(d, 25, 40, listOf("thunderbolt"))
        val b = Battle(wild, Tier.NORMAL)
        b.enemy.lastMove = "thunderbolt"
        val first = BattleEngine.playRound(d, b, smeargle, 1.0, false, Random(2))
        assertTrue(first.any { it is GameEvent.Attack && it.byPlayer && it.moveId == "sketch" && !it.failed })
        val second = BattleEngine.playRound(d, b, smeargle, 1.0, false, Random(3))
        assertTrue(second.any { it is GameEvent.Attack && it.byPlayer && it.moveId == "thunderbolt" })
        assertEquals(listOf("sketch"), smeargle.moves)
    }
}
