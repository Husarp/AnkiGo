package com.husarp.ankigo.game

import com.husarp.ankigo.game.data.Parsers
import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * R17C-2 (PLAN 240) with the real game data (run with --real; skipped otherwise): the EV yields are in the data, and a game that plays
 * answers until several wins, with each catch rule, grows the Main's EVs and friendship.
 */
class R17C2RealDataTest {
    private fun real(): GameData? {
        val dir = System.getenv("ANKIGO_REAL_DATA")?.takeIf { it.isNotBlank() }?.let { File(it) } ?: return null
        fun text(vararg names: String) = names.map { File(dir, it) }.first { it.isFile }.readText()
        return Parsers.build(Parsers.Sources(
            pokedexJson = text("pokedex.json"), movesJson = text("moves.json"), learnsetsJson = text("learnsets.json"),
            speciesCsv = text("pokemon_species.csv", "species.csv"), pokemonCsv = text("pokemon.csv"), statsCsv = text("pokemon_stats.csv"),
            evolutionCsv = text("pokemon_evolution.csv"), experienceCsv = text("experience.csv"), itemsCsv = text("items.csv"),
        ))
    }

    @Test fun theDataHasTheEvYields() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        assertEquals(Stats(0, 0, 0, 0, 0, 2), d.sp(25).evYield) // Pikachu: 2 Speed
        val withYield = d.species.values.count { it.evYield.total > 0 }
        assertTrue("species with a yield: $withYield", withYield > 1000)
        // a regional form has its own yield (it is read from its own row, not copied from the base species)
        val differing = d.species.values.filter { it.form != null }.filter { it.evYield != d.species[it.baseNum]?.evYield }
        assertTrue("forms with a different yield: ${differing.size}", differing.isNotEmpty())
        // a yield that survives the cache the game starts from
        assertEquals(d.sp(25).evYield, Parsers.decodeCache(Parsers.encodeCache(d)).sp(25).evYield)
        assertEquals(4000, d.price("soothe-bell")) // PokeAPI's price: it can be sold
        assertTrue("soothe-bell" in Items.shopPool(d))
    }

    private fun play(d: GameData, autoMode: Int, seed: Int, wins: Int): GameState {
        var now = 1_800_000_000_000L
        val state = GameState(createdAt = 1L)
        Game(d, state, Random(seed)) { now }.also { it.tick(); it.pickStarter(1) }
        state.settings.autoMode = autoMode
        val g = Game(d, state, Random(seed + 7)) { now }
        var card = 1L
        var n = 0
        while (state.defeats < wins && n++ < 6000) {
            now += 4_000
            g.onAnswer(Answer(card++, 3, now))
            if (n % 10 == 0) state.main?.let { it.hp = StatCalc.maxHp(d, it) } // keep the starter alive: the test is about the wins
        }
        assertTrue("mode $autoMode: only ${state.defeats} wins in $n answers", state.defeats >= wins)
        return state
    }

    @Test fun theMainsEvsAndFriendshipGrowWithEveryCatchRule() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        for (mode in 1..3) {
            val s = play(d!!, mode, seed = 10 + mode, wins = 8)
            val m = s.main!!
            println("REAL R17C-2 autoMode=$mode wins=${s.defeats} catchWins=${s.catchWins} main EVs=${m.evs} friendship=${m.friendship}")
            assertTrue("mode $mode: EVs ${m.evs}", m.evs.total > 0)
            assertTrue("mode $mode: friendship ${m.friendship}", m.friendship >= 5 * 8 && m.friendship <= Tuning.FRIENDSHIP_MAX)
            if (mode == 1) assertEquals("always catch: every win is a catch", s.defeats, s.catchWins)
            // the save keeps them
            val back = SaveCodec.decode(SaveCodec.encode(s)).main!!
            assertEquals(m.evs, back.evs); assertEquals(m.friendship, back.friendship)
        }
    }
}
