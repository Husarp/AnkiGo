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
 * R17C-5 with the real game data (run with --real; skipped otherwise): every move of [SideMoves] exists and is a status move, the data
 * carries what the rules read (priorities, flags, targets, boosts), every one says its own sentence, and every one can be used in a real
 * round of two real Pokémon without a crash and without "But nothing happened.".
 */
class R17C5RealDataTest {
    private fun real(): GameData? {
        val dir = System.getenv("ANKIGO_REAL_DATA")?.takeIf { it.isNotBlank() }?.let { File(it) } ?: return null
        fun text(vararg names: String) = names.map { File(dir, it) }.first { it.isFile }.readText()
        return Parsers.build(Parsers.Sources(
            pokedexJson = text("pokedex.json"), movesJson = text("moves.json"), learnsetsJson = text("learnsets.json"),
            speciesCsv = text("pokemon_species.csv", "species.csv"), pokemonCsv = text("pokemon.csv"), statsCsv = text("pokemon_stats.csv"),
            evolutionCsv = text("pokemon_evolution.csv"), experienceCsv = text("experience.csv"), itemsCsv = text("items.csv"),
        ))
    }

    private val damagingToo = listOf("spitup", "brickbreak", "psychicfangs", "ragingbull", "rapidspin", "mortalspin", "feint", "phantomforce", "shadowforce", "hyperspacefury", "hyperspacehole")

    @Test fun everyHandledMoveExistsAndIsAStatusMove() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        assertEquals(47, SideMoves.handles.size)
        for (id in SideMoves.handles) {
            val m = d.move(id)
            assertNotNull("$id is not in the real data", m)
            assertEquals("$id is a status move", Category.STATUS, m!!.category)
        }
        for (id in damagingToo) assertNotNull("$id is not in the real data", d.move(id))
    }

    @Test fun theDataHasWhatTheRulesRead() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        for (id in listOf("protect", "detect", "kingsshield", "spikyshield", "banefulbunker", "burningbulwark", "silktrap", "obstruct", "endure")) {
            assertEquals(id, 4, d.move(id)!!.priority); assertEquals(id, "self", d.move(id)!!.target)
        }
        for (id in listOf("quickguard", "wideguard", "craftyshield")) { assertEquals(id, 3, d.move(id)!!.priority); assertEquals("allySide", d.move(id)!!.target) }
        assertEquals(0, d.move("matblock")!!.priority)
        for (id in listOf("reflect", "lightscreen", "auroraveil", "safeguard", "mist", "tailwind")) assertEquals(id, "allySide", d.move(id)!!.target)
        assertEquals("allyTeam", d.move("healbell")!!.target); assertEquals("allyTeam", d.move("aromatherapy")!!.target)
        assertEquals("allies", d.move("junglehealing")!!.target); assertEquals("allies", d.move("lunarblessing")!!.target)
        assertEquals("normal", d.move("curse")!!.target); assertEquals("all", d.move("haze")!!.target)
        for (id in listOf("curse", "haze", "healbell", "destinybond")) assertTrue("$id bypasses a substitute", "bypasssub" in d.move(id)!!.flags)
        assertTrue("sound" in d.move("healbell")!!.flags)
        for (id in listOf("feint", "phantomforce", "shadowforce", "hyperspacefury", "hyperspacehole")) assertTrue("$id breaks protection", d.move(id)!!.breaksProtect)
        assertEquals(mapOf("atk" to 2, "spa" to 2, "spe" to 2), d.move("filletaway")!!.boosts)
        assertEquals(mapOf("atk" to 1, "def" to 1, "spa" to 1, "spd" to 1, "spe" to 1), d.move("clangoroussoul")!!.boosts)
        assertEquals(setOf("protect", "contact"), d.move("tackle")!!.flags.intersect(setOf("protect", "contact")))
        assertTrue("protect" in d.move("leechseed")!!.flags && "protect" in d.move("thunderwave")!!.flags)
        assertEquals("earthquake hits all adjacent", "allAdjacent", d.move("earthquake")!!.target)
        assertTrue("sound" in d.move("hypervoice")!!.flags && "bypasssub" in d.move("hypervoice")!!.flags)
        for (id in listOf("brickbreak", "psychicfangs", "ragingbull", "rapidspin", "mortalspin")) assertTrue(id, d.move(id)!!.damaging)
    }

    @Test fun everyMoveSaysItsSentence() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        for (id in SideMoves.handles + damagingToo) {
            val m = d.move(id)!!
            val own = SideMoves.describe(m)
            val text = MoveText.describe(m)
            assertTrue("$id has a sentence of its own", own.isNotEmpty())
            assertTrue("$id: $text", text.endsWith(".") && text != "Has no effect in AnkiGo battles.")
            assertFalse("$id: $text", "  " in text)
        }
        assertTrue(MoveText.describe(d.move("phantomforce")!!).contains("Goes through Protect and similar moves, and ends them."))
    }

    @Test fun everyMoveCanBeUsedInARealRoundWithoutNothingHappening() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        val tackle = listOf("tackle")
        for (id in SideMoves.handles + damagingToo) {
            // a Ghost Main so Curse is the real one and Ghost-immune moves have a reason to say so; the wild one is a Normal Snorlax with 'tackle' and 'rest'-free moves
            for (main in listOf(25, 94)) {
                val m = TestData.mon(d, main, 50, listOf(id)); val w = TestData.mon(d, 143, 50, tackle)
                m.hp = m.hp / 2
                val b = Battle(w, Tier.NORMAL)
                b.field.weather = "hail" // Aurora Veil needs it
                val script = object : BattleRules {
                    override fun forcedMove(v: BattleView, p: Boolean): Move? = if (p) v.data.move(id) else v.data.move("tackle")
                }
                val events = BattleRules.using(BattleRules.ALL + script) { BattleEngine.playRound(d, b, m, 1.0, true, Random(3)) }
                val lines = events.filterIsInstance<GameEvent.Effect>().map { it.text }
                assertFalse("$id (species $main) did nothing: $lines", "But nothing happened." in lines)
                val charged = events.filterIsInstance<GameEvent.Effect>().any { it.kind == EffectKind.CHARGE } // Phantom Force / Shadow Force only charge on the first turn
                assertTrue("$id was used", charged || events.filterIsInstance<GameEvent.Attack>().any { it.byPlayer })
            }
        }
    }
}
