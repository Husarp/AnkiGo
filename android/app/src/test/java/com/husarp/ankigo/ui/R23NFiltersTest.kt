package com.husarp.ankigo.ui

import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Type
import org.junit.Assert.*
import org.junit.Test

class R23NFiltersTest {
    @Test fun favouritesInTeamHoldingAnItemMustBeTheSamePokemon() {
        val data = TestData.data(listOf(TestData.species(1, types = listOf(Type.GRASS))))
        val both = TestData.mon(data, 1, 10, uid = "both").also { it.favourite = true; it.heldItem = "leftovers" }
        val noItem = TestData.mon(data, 1, 10, uid = "noItem").also { it.favourite = true }
        val boxed = TestData.mon(data, 1, 10, uid = "boxed").also { it.favourite = true; it.heldItem = "leftovers" }
        val mons = listOf(both, noItem, boxed)
        val filters = setOf(BoxFilter.FAVOURITES, BoxFilter.IN_TEAM, BoxFilter.HOLDS_ITEM)
        assertEquals(listOf(both), BoxTiles.filter(mons, filters, listOf("both", "noItem")))
        assertEquals(mons, BoxTiles.filter(mons, emptySet()))
        assertTrue(BoxTiles.filter(mons, filters, listOf("both"), types = TypeFilter(setOf(Type.FIRE)), typesOf = { data.sp(it.species).types }).isEmpty())
        assertEquals(1, BoxTiles.build(data, BoxTiles.filter(mons, filters, listOf("both", "noItem")), true).single().count)
    }

    @Test fun savedShowsMigrateSingleChoiceAndIgnoreUnknownsAndAll() {
        assertEquals(setOf(BoxFilter.SHINY), SortPrefs.decodeShows("SHINY"))
        assertEquals(setOf(BoxFilter.FAVOURITES, BoxFilter.IN_TEAM, BoxFilter.HOLDS_ITEM), SortPrefs.decodeShows("FAVOURITES,IN_TEAM,HOLDS_ITEM"))
        assertEquals(emptySet<BoxFilter>(), SortPrefs.decodeShows(null))
        assertEquals(emptySet<BoxFilter>(), SortPrefs.decodeShows("ALL,bogus"))
    }

    @Test fun combinedFiltersAreNamedInTheEmptyStateAndSheetOnlyChip() {
        val shows = setOf(BoxFilter.FAVOURITES, BoxFilter.HOLDS_ITEM, BoxFilter.CAN_EVOLVE)
        assertEquals("Holds an item · Can evolve now", FilterRowText.plain(FilterRowText.activePieces(TypeFilter(), shows)))
        val empty = FilterRowText.emptyBox(shows, TypeFilter())
        shows.forEach { assertTrue(empty.contains(it.label)) }
    }
}
