package com.husarp.ankigo.game.data

import com.husarp.ankigo.game.EvoKind
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Dev check against the real downloaded files. Skipped unless env ANKIGO_REAL_DATA points at a folder with the 9 source files
 * (pokedex.json, moves.json, learnsets.json, pokemon_species.csv or species.csv, pokemon.csv, pokemon_stats.csv,
 * pokemon_evolution.csv, experience.csv, items.csv). The files never go into the repo.
 */
class RealDataCheck {
    private fun sources(): Parsers.Sources? {
        val dir = System.getenv("ANKIGO_REAL_DATA")?.takeIf { it.isNotBlank() }?.let { File(it) } ?: return null
        fun text(vararg names: String) = names.map { File(dir, it) }.first { it.isFile }.readText()
        return Parsers.Sources(
            pokedexJson = text("pokedex.json"), movesJson = text("moves.json"), learnsetsJson = text("learnsets.json"),
            speciesCsv = text("pokemon_species.csv", "species.csv"), pokemonCsv = text("pokemon.csv"), statsCsv = text("pokemon_stats.csv"),
            evolutionCsv = text("pokemon_evolution.csv"), experienceCsv = text("experience.csv"), itemsCsv = text("items.csv"),
        )
    }

    @Test fun realFilesBuildAndKnownSpeciesLookRight() {
        val src = sources()
        assumeTrue("ANKIGO_REAL_DATA not set", src != null)
        val t0 = System.currentTimeMillis()
        val d = Parsers.build(src!!)
        val ms = System.currentTimeMillis() - t0
        assertEquals(1025, d.species.size)
        assertTrue(d.moves.size > 800)

        // Pikachu: prevo Pichu (172), Thunder Stone -> Raichu (26); Raichu-Alola is dropped
        val pikachu = d.sp(25)
        assertEquals(172, pikachu.prevo)
        val raichu = pikachu.evolutions.single()
        assertEquals(26, raichu.to); assertEquals(EvoKind.ITEM, raichu.kind); assertEquals("thunder-stone", raichu.item)
        assertTrue(pikachu.levelMoves.isNotEmpty())

        // Eevee: 8 evolutions incl. friendship day/night and Sylveon's Fairy move
        val eevee = d.sp(133).evolutions
        assertEquals(8, eevee.size)
        assertTrue(eevee.any { it.kind == EvoKind.FRIENDSHIP && it.time == "day" })
        assertTrue(eevee.any { it.kind == EvoKind.FRIENDSHIP && it.time == "night" })
        assertTrue(eevee.single { it.to == 700 }.let { it.kind == EvoKind.FRIENDSHIP && it.moveType == Type.FAIRY })

        // Kirlia -> Gallade: Dawn Stone, males only
        val gallade = d.sp(281).evolutions.single { it.to == 475 }
        assertEquals(EvoKind.ITEM, gallade.kind); assertEquals("dawn-stone", gallade.item); assertEquals('M', gallade.gender)

        // Kadabra -> Alakazam: trade without item; Onix -> Steelix: trade with Metal Coat
        val alakazam = d.sp(64).evolutions.single { it.to == 65 }
        assertEquals(EvoKind.TRADE, alakazam.kind); assertNull(alakazam.item)
        val steelix = d.sp(95).evolutions.single { it.to == 208 }
        assertEquals(EvoKind.TRADE, steelix.kind); assertEquals("metal-coat", steelix.item)

        // Tyrogue: three level-20 evolutions with conditions
        val tyrogue = d.sp(236).evolutions
        assertEquals(3, tyrogue.size)
        assertTrue(tyrogue.all { it.kind == EvoKind.LEVEL && it.level == 20 && it.condition != null })

        // Mr. Mime's prevo is Mime Jr.
        assertEquals(439, d.sp(122).prevo)

        // Growth table "medium fast" (rate 2): 1,000,000 XP at level 100
        assertEquals(1_000_000, d.growth.getValue(2)[100])

        // Every evolution item is a known PokeAPI item id; report the ones that aren't
        val unknownItems = d.species.values.flatMap { s -> s.evolutions.mapNotNull { it.item } }.distinct().filter { it !in d.itemPrices }
        val kinds = d.species.values.flatMap { it.evolutions }.groupingBy { it.kind }.eachCount()
        val withMoves = d.species.values.count { it.levelMoves.isNotEmpty() }
        val cache = Parsers.encodeCache(d)
        val back = Parsers.decodeCache(cache)
        assertEquals(d.species, back.species)
        assertEquals(d.moves, back.moves)

        println("REAL-DATA species=${d.species.size} moves=${d.moves.size} growthRates=${d.growth.size} items=${d.itemPrices.size}")
        println("REAL-DATA evolution kinds=$kinds speciesWithLevelMoves=$withMoves pikachuLevelMoves=${pikachu.levelMoves.size} pikachuTms=${pikachu.tmMoves.size}")
        println("REAL-DATA evolution items not in items.csv: $unknownItems")
        println("REAL-DATA cache=${cache.length} bytes buildMs=$ms")
        assertNotNull(d.move("tackle"))
    }
}
