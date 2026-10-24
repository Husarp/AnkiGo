package com.husarp.ankigo.game

import com.husarp.ankigo.game.data.Parsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * R17C-4 with the real game data (run with --real; skipped otherwise): every move of the ticket exists, the data carries what the rules
 * read (multi-hit counts, accuracy per hit, self-destruct ...), none of them still gets the flat 60 and every one says its sentence.
 */
class R17C4RealDataTest {
    private fun real(): GameData? {
        val dir = System.getenv("ANKIGO_REAL_DATA")?.takeIf { it.isNotBlank() }?.let { File(it) } ?: return null
        fun text(vararg names: String) = names.map { File(dir, it) }.first { it.isFile }.readText()
        return Parsers.build(Parsers.Sources(
            pokedexJson = text("pokedex.json"), movesJson = text("moves.json"), learnsetsJson = text("learnsets.json"),
            speciesCsv = text("pokemon_species.csv", "species.csv"), pokemonCsv = text("pokemon.csv"), statsCsv = text("pokemon_stats.csv"),
            evolutionCsv = text("pokemon_evolution.csv"), experienceCsv = text("experience.csv"), itemsCsv = text("items.csv"),
        ))
    }

    private val ids: List<String> = R17C4Fixture.MOVES.map { it.id }.filter { it != "shadowball2" }

    @Test fun everyMoveOfTheTicketExistsAndNoneKeepsTheFlatSixty() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        val a = TestData.mon(d, 25, 50); val t = TestData.mon(d, 143, 50)
        for (id in ids) {
            val m = d.move(id)
            assertNotNull("$id is not in the real data", m)
            m!!
            assertTrue("$id is a damaging move", m.damaging)
        }
        for (id in MovePower.VARIABLE_POWER - "spitup") {
            val m = d.move(id)!!
            val h = BattleEngine.newHit(SimpleView(d, a, t), true, a, BattleSide(), t, BattleSide(), m)
            assertTrue("$id has no rule for its power", MovePower.basePower(h) != null || MovePower.fixedDamage(h) != null)
            assertFalse("$id still shows a power number", MoveText.showsPower(m))
        }
        assertNotNull(d.move("spitup")) // R17C-5's move: it only must not show the flat 60
        assertFalse(MoveText.showsPower(d.move("spitup")!!))
    }

    @Test fun theDataHasWhatTheRulesRead() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        for (id in listOf("tripleaxel", "triplekick")) {
            val m = d.move(id)!!
            assertEquals(id, 3..3, m.multihit); assertTrue(id, m.multiAccuracy)
        }
        assertEquals(10..10, d.move("populationbomb")!!.multihit); assertTrue(d.move("populationbomb")!!.multiAccuracy)
        assertTrue(d.move("finalgambit")!!.selfDestruct)
        assertEquals("self", d.move("bide")!!.target)
        assertTrue(d.move("snore")!!.sleepUsable)
        assertEquals(1, d.move("bide")!!.priority); assertEquals(-5, d.move("counter")!!.priority); assertEquals(-3, d.move("focuspunch")!!.priority)
        assertEquals(3, d.move("fakeout")!!.priority); assertEquals(1, d.move("suckerpunch")!!.priority); assertEquals(1, d.move("thunderclap")!!.priority)
        assertEquals(50, d.move("lastrespects")!!.power); assertEquals(70, d.move("retaliate")!!.power)
        // the weights the weight moves read
        assertEquals(460.0, d.sp(143).weightKg!!, 0.0)
        assertEquals(6.0, d.sp(25).weightKg!!, 0.0)
        val a = TestData.mon(d, 25, 50)
        fun lowKick(target: Int): Int {
            val t = TestData.mon(d, target, 50)
            return MovePower.basePower(BattleEngine.newHit(SimpleView(d, a, t), true, a, BattleSide(), t, BattleSide(), d.move("lowkick")!!))!!
        }
        assertEquals(120, lowKick(143)) // Snorlax
        assertEquals(20, lowKick(25))   // Pikachu
    }

    @Test fun everyMoveSaysItsSentence() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        for (id in ids) {
            val m = d.move(id)!!
            val lines = MovePower.describe(m)
            assertEquals(id, 1, lines.size)
            assertTrue(MoveText.describe(m).contains(lines[0]))
        }
    }

    @Test fun theDefaultRuleListPlaysARealFightWithTheRealMoves() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        // with every rule set on, a few of the moves run through a whole round without a crash and with sensible numbers
        for (id in listOf("magnitude", "present", "psywave", "tripleaxel", "beatup", "futuresight", "rollout", "bide", "counter", "facade", "hiddenpower", "freezedry")) {
            val main = TestData.mon(d, 25, 50, listOf(id, "tackle"))
            val wild = TestData.mon(d, 143, 50, listOf("tackle"))
            val b = Battle(wild, Tier.NORMAL)
            repeat(6) { n ->
                val ev = BattleEngine.playRound(d, b, main, 1.0, true, kotlin.random.Random(n + 3))
                assertTrue("$id round $n", ev.any { it is GameEvent.Attack || it is GameEvent.Effect || it is GameEvent.Splash })
                main.hp = StatCalc.maxHp(d, main); wild.hp = b.maxWildHp(d)
            }
        }
    }
}
