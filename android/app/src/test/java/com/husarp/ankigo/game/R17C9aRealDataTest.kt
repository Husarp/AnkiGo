package com.husarp.ankigo.game

import com.husarp.ankigo.game.data.Parsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * R17C-9a with the real game data (run with --real; skipped otherwise): every ability the game builds is a real Showdown ability with the same name,
 * the lists of Mold Breaker and Trace name real abilities, the built ones cover about two thirds of the ability slots of the species (printed), and a
 * real Pokémon of every built ability plays a few rounds against a real Main without a crash or an impossible HP.
 */
class R17C9aRealDataTest {
    private fun dir(): File? = System.getenv("ANKIGO_REAL_DATA")?.takeIf { it.isNotBlank() }?.let { File(it) }

    private fun real(): GameData? {
        val dir = dir() ?: return null
        fun text(vararg names: String) = names.map { File(dir, it) }.first { it.isFile }.readText()
        return Parsers.build(Parsers.Sources(
            pokedexJson = text("pokedex.json"), movesJson = text("moves.json"), learnsetsJson = text("learnsets.json"),
            speciesCsv = text("pokemon_species.csv", "species.csv"), pokemonCsv = text("pokemon.csv"), statsCsv = text("pokemon_stats.csv"),
            evolutionCsv = text("pokemon_evolution.csv"), experienceCsv = text("experience.csv"), itemsCsv = text("items.csv"),
        ))
    }

    /** Showdown's abilities.js: id -> name. */
    private fun showdown(): Map<String, String>? {
        val f = dir()?.let { File(it, "abilities.js") }?.takeIf { it.isFile } ?: return null
        return Regex("""([a-z0-9]+):\{(?:[^{}]|\{[^{}]*\})*?name:"([^"]+)"""").findAll(f.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    }

    @Test fun everyBuiltAbilityIsARealShowdownAbilityWithTheSameName() {
        val names = showdown(); assumeTrue("ANKIGO_REAL_DATA (abilities.js) not set", names != null)
        names!!
        assertTrue("the list was read: ${names.size}", names.size > 250)
        for (id in Abilities.BUILT) {
            assertTrue("$id is a Showdown ability", id in names)
            assertEquals("the name of $id", names[id], AbilityText.name(id))
        }
        for (id in Abilities.BREAKABLE + Abilities.MOLD_BREAKERS) assertTrue("$id (a list entry) is a Showdown ability", id in names)
    }

    @Test fun theBuiltAbilitiesCoverTwoThirdsOfTheSpeciesSlots() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        var slots = 0; var covered = 0; var species = 0
        val perAbility = HashMap<String, Int>()
        for (sp in d.species.values) {
            if (sp.abilities.isEmpty()) continue
            species++
            for (a in sp.abilities) {
                slots++
                val id = BattleView.normalizeAbility(a)
                if (id in Abilities.BUILT) { covered++; perAbility.merge(id, 1, Int::plus) }
            }
        }
        val pct = 100.0 * covered / slots
        println("R17C-9a coverage: $covered of $slots ability slots on $species species/forms = ${"%.1f".format(pct)} % with ${Abilities.BUILT.size} abilities")
        println("R17C-9a top: " + perAbility.entries.sortedByDescending { it.value }.take(12).joinToString { "${it.key} ${it.value}" })
        println("R17C-9a built but on no species slot: " + (Abilities.BUILT - perAbility.keys).sorted())
        assertTrue("about two thirds of the slots (audit: ~68 %): $pct", pct >= 66.0)
    }

    @Test fun aRealPokemonOfEveryBuiltAbilityPlaysWithoutTrouble() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        fun moves(num: Int): List<String> = d.sp(num).levelMoves.filter { it.level <= 50 && d.move(it.move) != null }.map { it.move }.distinct().takeLast(4).ifEmpty { listOf("tackle") }
        val rng = Random(17)
        // a real Main with a few real moves, and for each ability the first real species that has it (as the wild one, and as the Main)
        val bySpecies = d.species.values.filter { it.num <= 1025 }
        var fights = 0
        for (id in Abilities.BUILT) {
            val holder = bySpecies.firstOrNull { sp -> sp.abilities.any { BattleView.normalizeAbility(it) == id } } ?: continue
            for (asMain in listOf(false, true)) for (choice in listOf(MoveChoice.RANDOM, MoveChoice.SMARTEST)) {
                val opponent = bySpecies[rng.nextInt(bySpecies.size)]
                val holderMon = TestData.mon(d, holder.num, 50, moves(holder.num)).also { it.ability = holder.abilities.first { a -> BattleView.normalizeAbility(a) == id } }
                val otherMon = TestData.mon(d, opponent.num, 50, moves(opponent.num))
                val (main, wild) = if (asMain) holderMon to otherMon else otherMon to holderMon
                val b = Battle(wild, Tier.NORMAL)
                repeat(5) {
                    if (main.hp > 0 && wild.hp > 0) BattleEngine.playRound(d, b, main, 1.0, true, rng, moveChoice = choice)
                }
                fights++
                assertTrue("$id (${holder.name}) Main hp ${main.hp}", main.hp in 0..StatCalc.maxHp(d, main))
                assertTrue("$id (${holder.name}) wild hp ${wild.hp}", wild.hp in 0..b.maxWildHp(d))
                for (v in listOf(b.player, b.enemy)) assertTrue("$id: stages stay in range", v.boosts.all { it in -6..6 })
            }
        }
        println("R17C-9a played $fights fights with the ${Abilities.BUILT.size} abilities")
        assertTrue(fights >= 4 * 90)
    }
}
