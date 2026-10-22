package com.husarp.ankigo.ui

import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DexFilterTest {
    private val all = listOf(
        TestData.species(1, "Bulba", listOf(Type.GRASS, Type.POISON)),
        TestData.species(4, "Char", listOf(Type.FIRE)),
        TestData.species(25, "Pika", listOf(Type.ELECTRIC)),
        TestData.species(125, "Electro", listOf(Type.ELECTRIC)),
        TestData.species(152, "Chiko", listOf(Type.GRASS)),
        TestData.species(252, "Tree", listOf(Type.GRASS)),
    )
    // owned: Bulba (caught at 100), Pika (caught at 300), Chiko (caught at 200)
    private val last = mapOf(1 to 100L, 25 to 300L, 152 to 200L)
    private val wish = setOf(25, 4)
    private val gens = setOf(1, 2, 3)

    private fun run(f: DexFilter, enabled: Set<Int> = gens) = f.apply(all, last, wish, enabled).map { it.num }

    @Test fun defaultsShowEverythingInNumberOrder() {
        assertEquals(listOf(1, 4, 25, 125, 152, 252), run(DexFilter()))
    }

    @Test fun ownedAndNotOwned() {
        assertEquals(listOf(1, 25, 152), run(DexFilter(owned = DexOwned.OWNED)))
        assertEquals(listOf(4, 125, 252), run(DexFilter(owned = DexOwned.NOT_OWNED)))
    }

    @Test fun disabledGensNeverShow() {
        assertEquals(listOf(1, 4, 25, 125), run(DexFilter(), enabled = setOf(1)))
        assertEquals(emptyList<Int>(), run(DexFilter(gens = setOf(2)), enabled = setOf(1)))
    }

    @Test fun genChipsAreMultiSelect() {
        assertEquals(listOf(152), run(DexFilter(gens = setOf(2))))
        assertEquals(listOf(152, 252), run(DexFilter(gens = setOf(2, 3))))
    }

    @Test fun typeMatchesAnyOfTheSpeciesTypes() {
        assertEquals(listOf(1, 152, 252), run(DexFilter(types = setOf(Type.GRASS))))
        assertEquals(listOf(1), run(DexFilter(types = setOf(Type.POISON))))
        assertEquals(listOf(1, 4, 152, 252), run(DexFilter(types = setOf(Type.POISON, Type.FIRE, Type.GRASS))))
    }

    @Test fun wishlist() {
        assertEquals(listOf(4, 25), run(DexFilter(wishlistOnly = true)))
    }

    @Test fun searchByNameIsCaseInsensitiveAndContains() {
        assertEquals(listOf(25), run(DexFilter(query = "PIK")))
        assertEquals(listOf(125, 252), run(DexFilter(query = "e")))
        assertEquals(listOf(1, 4, 25, 125, 152, 252), run(DexFilter(query = "  ")))
    }

    @Test fun searchByNumberIsAPrefixAndHandlesHashAndPadding() {
        assertEquals(listOf(25, 252), run(DexFilter(query = "25")))
        assertEquals(listOf(25), run(DexFilter(query = "#025")))
        assertEquals(listOf(1), run(DexFilter(query = "001")))
        assertEquals(listOf(1, 125, 152), run(DexFilter(query = "1")))
        assertEquals(emptyList<Int>(), run(DexFilter(query = "000")))
    }

    @Test fun everythingCombinesWithAnd() {
        val f = DexFilter(owned = DexOwned.OWNED, gens = setOf(1, 2), types = setOf(Type.GRASS), wishlistOnly = false, query = "b")
        assertEquals(listOf(1), run(f))
        assertEquals(listOf(25), run(DexFilter(owned = DexOwned.OWNED, wishlistOnly = true, types = setOf(Type.ELECTRIC), gens = setOf(1), query = "pi")))
        assertEquals(emptyList<Int>(), run(DexFilter(owned = DexOwned.NOT_OWNED, wishlistOnly = true, types = setOf(Type.ELECTRIC))))
    }

    @Test fun sortByName() {
        assertEquals(listOf(1, 4, 152, 125, 25, 252), run(DexFilter(sort = DexSort.NAME)))
    }

    @Test fun sortByLastCaughtPutsOwnedNewestFirstThenTheRestByNumber() {
        assertEquals(listOf(25, 152, 1, 4, 125, 252), run(DexFilter(sort = DexSort.LAST_CAUGHT)))
        assertEquals(listOf(25, 152, 1), run(DexFilter(sort = DexSort.LAST_CAUGHT, owned = DexOwned.OWNED)))
    }

    // ---- regional forms: the number shown is the base species' number ----

    private val withForms = all + listOf(
        TestData.species(19, "Ratty"),
        TestData.species(10091, "Alolan Ratty", gen = 7).copy(form = "Alola", baseNum = 19),
        TestData.species(10250, "Paldean Hornbull", gen = 9).copy(form = "Paldea-Combat", baseNum = 128),
        TestData.species(128, "Hornbull"),
    )
    private val allGens = (1..9).toSet()

    private fun runForms(f: DexFilter) = f.apply(withForms, last, wish, allGens).map { it.num }

    @Test fun sortByNumberPutsAFormRightAfterItsBaseSpecies() {
        assertEquals(listOf(1, 4, 19, 10091, 25, 125, 128, 10250, 152, 252), runForms(DexFilter()))
    }

    @Test fun searchByNumberMatchesTheBaseNumberOfAForm() {
        assertEquals(listOf(19, 10091), runForms(DexFilter(query = "19")))
        assertEquals(listOf(19, 10091), runForms(DexFilter(query = "#019")))
        assertEquals(listOf(128, 10250), runForms(DexFilter(query = "128")))
        assertEquals(emptyList<Int>(), runForms(DexFilter(query = "10091"))) // the PokeAPI id is not a Pokédex number
    }

    @Test fun searchByNameFindsTheForm() {
        assertEquals(listOf(10091), runForms(DexFilter(query = "alolan")))
        assertEquals(listOf(10091, 10250), runForms(DexFilter(query = "an"))) // Alolan Ratty, Paldean Hornbull (+ no others)
    }

    @Test fun theGenFilterUsesTheFormsOwnGeneration() {
        assertEquals(listOf(10091), runForms(DexFilter(gens = setOf(7))))
        assertEquals(listOf(10250), runForms(DexFilter(gens = setOf(9))))
        // gen 7 switched off: the Alolan form disappears even though its base species (gen 1) is shown
        val noSeven = DexFilter().apply(withForms, last, wish, allGens - 7).map { it.num }
        assertTrue(19 in noSeven)
        assertFalse(10091 in noSeven)
    }

    @Test fun sortByLastCaughtKeepsAFormAfterItsBaseWhenBothAreNotOwned() {
        val lastForms = mapOf(10250 to 900L)
        val out = DexFilter(sort = DexSort.LAST_CAUGHT).apply(withForms, lastForms, wish, allGens).map { it.num }
        assertEquals(listOf(10250, 1, 4, 19, 10091, 25, 125, 128, 152, 252), out)
    }

    @Test fun activeFlag() {
        assertEquals(false, DexFilter(sort = DexSort.NAME).active)
        assertEquals(true, DexFilter(query = "x").active)
    }

    // ---- PLAN 192: Any of / All of for the picked types ----

    private val typed = listOf(
        TestData.species(7, "Squirt", listOf(Type.WATER)),
        TestData.species(130, "Gyara", listOf(Type.WATER, Type.FLYING)),
        TestData.species(16, "Birdie", listOf(Type.NORMAL, Type.FLYING)),
        TestData.species(6, "Zard", listOf(Type.FIRE, Type.FLYING)),
        TestData.species(9, "Blast", listOf(Type.WATER)),
        TestData.species(95, "Onix", listOf(Type.ROCK, Type.GROUND)),
    )

    private fun runTyped(f: DexFilter) = f.apply(typed, last, wish, gens).map { it.num }

    @Test fun anyOfIsTheDefaultAndUnchanged() {
        assertEquals(TypeMatch.ANY, DexFilter().typeMatch)
        assertEquals(listOf(6, 7, 9, 16, 130), runTyped(DexFilter(types = setOf(Type.WATER, Type.FLYING))))
        assertEquals(listOf(7, 9, 130), runTyped(DexFilter(types = setOf(Type.WATER))))
    }

    @Test fun allOfOneTypeIsTheSameAsAnyOfOneType() {
        assertEquals(listOf(7, 9, 130), runTyped(DexFilter(types = setOf(Type.WATER), typeMatch = TypeMatch.ALL)))
        assertEquals(listOf(6, 16, 130), runTyped(DexFilter(types = setOf(Type.FLYING), typeMatch = TypeMatch.ALL)))
    }

    @Test fun allOfTwoTypesFindsOnlyThoseWithBoth() {
        assertEquals(listOf(130), runTyped(DexFilter(types = setOf(Type.WATER, Type.FLYING), typeMatch = TypeMatch.ALL)))
        assertEquals(listOf(95), runTyped(DexFilter(types = setOf(Type.GROUND, Type.ROCK), typeMatch = TypeMatch.ALL)))
        assertEquals(emptyList<Int>(), runTyped(DexFilter(types = setOf(Type.WATER, Type.FIRE), typeMatch = TypeMatch.ALL)))
    }

    @Test fun noPickedTypesShowsEverythingInBothModes() {
        assertEquals(6, runTyped(DexFilter(typeMatch = TypeMatch.ALL)).size)
        assertEquals(false, DexFilter(typeMatch = TypeMatch.ALL).active)
        assertEquals(true, DexFilter(types = setOf(Type.FIRE), typeMatch = TypeMatch.ALL).active)
    }

    // ---- R16-2-E: the third mode, and the filter's type part ----

    @Test fun onlyKeepsNoTypeOutsideThePicks() {
        // Water: pure Water only (Gyara is Water/Flying); Water + Flying: pure Water, pure Flying and Water/Flying
        assertEquals(listOf(7, 9), runTyped(DexFilter(types = setOf(Type.WATER), typeMatch = TypeMatch.ONLY)))
        assertEquals(listOf(7, 9, 130), runTyped(DexFilter(types = setOf(Type.WATER, Type.FLYING), typeMatch = TypeMatch.ONLY)))
        assertEquals(emptyList<Int>(), runTyped(DexFilter(types = setOf(Type.FLYING), typeMatch = TypeMatch.ONLY))) // nobody here is pure Flying
        assertEquals(6, runTyped(DexFilter(typeMatch = TypeMatch.ONLY)).size)
    }

    @Test fun theTypePartIsATypeFilterAndRoundTrips() {
        val f = DexFilter(types = setOf(Type.WATER, Type.ICE), typeMatch = TypeMatch.ALL)
        assertEquals(TypeFilter(setOf(Type.WATER, Type.ICE), TypeMatch.ALL), f.typeFilter)
        val g = DexFilter(query = "x", wishlistOnly = true).withTypeFilter(f.typeFilter)
        assertEquals(f.types, g.types); assertEquals(TypeMatch.ALL, g.typeMatch)
        assertEquals("x", g.query); assertTrue(g.wishlistOnly)
        assertEquals(TypeFilter(), g.withTypeFilter(TypeFilter()).typeFilter)
    }
}
