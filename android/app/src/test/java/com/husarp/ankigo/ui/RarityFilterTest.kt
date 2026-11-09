package com.husarp.ankigo.ui

import com.husarp.ankigo.game.*
import org.junit.Assert.*
import org.junit.Test

class RarityFilterTest {
    private val data = TestData.data(listOf(
        TestData.species(19, "Plain"),
        TestData.species(20, "Below", base = Stats(99, 80, 80, 80, 80, 80)),
        TestData.species(21, "Strong", base = Stats(100, 80, 80, 80, 80, 80)),
        TestData.species(1, "Starter"),
        TestData.species(172, "Baby", isBaby = true),
        TestData.species(793, "Ultra"),
        TestData.species(150, "Legend", isLegendary = true),
        TestData.species(151, "Myth", isMythical = true),
    ))
    private val mons = data.species.values.map { TestData.mon(data, it.num, 5, uid = "m${it.num}").apply { tier = Encounters.tierOf(it) ?: Tier.NORMAL } }
    private fun dex(f: RarityFilter, types: Set<Type> = emptySet()) = DexFilter(rarities = f, types = types)
        .apply(data.species.values.toList(), emptyMap(), emptySet(), (1..9).toSet()).map { it.num }.toSet()

    @Test fun everyMarkUsesTheSameLadderForSpeciesAndCopies() {
        val expected = mapOf(PokemonRarity.BABY to setOf(172),
            PokemonRarity.ULTRA to setOf(793), PokemonRarity.LEGENDARY to setOf(150), PokemonRarity.MYTHICAL to setOf(151))
        for ((r, nums) in expected) {
            val filter = RarityFilter(setOf(r))
            assertEquals(nums, dex(filter))
            assertEquals(nums, filter.apply(data, mons).map { it.species }.toSet())
        }
        assertEquals(data.species.keys, dex(RarityFilter()))
        assertEquals(mons, RarityFilter().apply(data, mons))
    }

    @Test fun selectedMarksCombineWithOrAndOtherFiltersWithAnd() {
        val f = RarityFilter(setOf(PokemonRarity.BABY, PokemonRarity.MYTHICAL))
        assertEquals(setOf(172, 151), dex(f))
        assertTrue(dex(f, setOf(Type.WATER)).isEmpty())
        assertEquals(setOf(PokemonRarity.MYTHICAL), f.toggle(PokemonRarity.BABY).picked)
        assertFalse(f.toggle(PokemonRarity.BABY).toggle(PokemonRarity.MYTHICAL).active)
        val owned = DexFilter(owned = DexOwned.OWNED, rarities = f).apply(data.species.values.toList(), mapOf(172 to 1L), emptySet(), (1..9).toSet())
        assertEquals(listOf(172), owned.map { it.num })
    }

    @Test fun retiredDotIsRemovedFromSavedBoxAndDexFilters() {
        assertEquals(listOf(PokemonRarity.BABY, PokemonRarity.ULTRA, PokemonRarity.LEGENDARY, PokemonRarity.MYTHICAL), RarityFilter.options)
        assertFalse(RarityFilter.decode("COMMON").active)
        assertEquals(data.species.keys, dex(RarityFilter.decode("COMMON")))
        assertEquals(mons, RarityFilter.decode("COMMON").apply(data, mons))
        assertEquals(setOf(PokemonRarity.BABY), RarityFilter.decode("COMMON,BABY").picked)
        val migrated = SortPrefs.decodeDex("OWNED;1,7;1;0;COMMON,MYTHICAL")
        assertEquals(setOf(PokemonRarity.MYTHICAL), migrated.rarities.picked)
        assertEquals(DexOwned.OWNED, migrated.owned)
        assertTrue(migrated.wishlistOnly)
        assertFalse(SortPrefs.decodeDex("ALL;;0;0;COMMON").rarities.active)
    }

    @Test fun savedSelectionsAreCompatibleWithOldAndUnknownValues() {
        val filter = RarityFilter(RarityFilter.options.toSet())
        assertEquals(filter, RarityFilter.decode(filter.encode()))
        assertEquals(RarityFilter(), RarityFilter.decode(null))
        assertEquals(RarityFilter(), RarityFilter.decode("PLAIN,garbage"))
        assertEquals(RarityFilter(setOf(PokemonRarity.BABY)), RarityFilter.decode("BABY,unknown,BABY"))
        val f = DexFilter(rarities = filter, gens = setOf(1, 7), wishlistOnly = true)
        assertEquals(f, SortPrefs.decodeDex(SortPrefs.encodeDex(f)))
        assertFalse(SortPrefs.decodeDex("ALL;;0;0").rarities.active)
        assertTrue(DexFilter(rarities = filter).active)
        assertEquals(1, DexFilter(rarities = filter).sheetActive)
        assertTrue(FilterRowText.emptyDex(DexFilter(rarities = filter)).contains("Baby"))
        assertEquals("Baby", FilterRowText.plain(FilterRowText.withRarity(emptyList(), RarityFilter(setOf(PokemonRarity.BABY)))))
    }
}
