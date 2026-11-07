package com.husarp.ankigo.game

import com.husarp.ankigo.game.data.Parsers
import com.husarp.ankigo.ui.RarityMark
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class PokemonRarityTest {
    private fun check(sp: Species) {
        val tier = Encounters.tierOf(sp)
        val expected = when (tier) {
            Tier.BABY -> 1 to "great-ball"
            Tier.ULTRA -> 2 to "ultra-ball"
            Tier.LEGENDARY -> 3 to "ultra-ball"
            Tier.MYTHICAL -> 4 to "master-ball"
            Tier.STARTER -> 0 to "great-ball"
            else -> if (sp.base.total >= 500) 0 to "great-ball" else null to "poke-ball"
        }
        assertEquals("${sp.name} mark", expected.first, RarityMark.tier(tier, sp.base.total))
        assertEquals("${sp.name} ball", expected.second, CatchBall.forAuto(sp))
    }

    @Test fun everyFixtureSpeciesFollowsTheLadder() = TestData.world().species.values.forEach(::check)

    @Test fun allTiersAtTheBaseTotalBoundary() {
        for (tier in Tier.entries + null) for (total in listOf(0, 499, 500, 501, 800)) {
            val expectedMark = when (tier) {
                Tier.BABY -> 1
                Tier.ULTRA -> 2
                Tier.LEGENDARY -> 3
                Tier.MYTHICAL -> 4
                Tier.STARTER -> 0
                else -> if (total >= 500) 0 else null
            }
            val expectedBall = when (expectedMark) {
                null -> "poke-ball"
                0, 1 -> "great-ball"
                2, 3 -> "ultra-ball"
                else -> "master-ball"
            }
            assertEquals("$tier / $total", expectedMark, RarityMark.tier(tier, total))
            assertEquals("$tier / $total", expectedBall, PokemonRarity.of(tier, total).ball)
        }
    }

    /** Production species data is downloaded, not bundled; run with ANKIGO_REAL_DATA, like the existing real-data checks. */
    @Test fun everyProductionSpeciesFollowsTheLadder() {
        val dir = System.getenv("ANKIGO_REAL_DATA")?.let(::File)
        assumeTrue("ANKIGO_REAL_DATA not set", dir != null)
        fun text(name: String) = File(dir!!, name).readText()
        val data = Parsers.build(Parsers.Sources(
            pokedexJson = text("pokedex.json"), movesJson = text("moves.json"), learnsetsJson = text("learnsets.json"),
            speciesCsv = text("pokemon_species.csv"), pokemonCsv = text("pokemon.csv"), statsCsv = text("pokemon_stats.csv"),
            evolutionCsv = text("pokemon_evolution.csv"), experienceCsv = text("experience.csv"), itemsCsv = text("items.csv"),
        ))
        assertTrue("Full species data loaded", data.species.size >= 1025)
        data.species.values.forEach(::check)
        println("R30-c ladder checked for ${data.species.size} species/forms")
    }
}
