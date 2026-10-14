package com.husarp.ankigo.ui

import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
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

    @Test fun activeFlag() {
        assertEquals(false, DexFilter(sort = DexSort.NAME).active)
        assertEquals(true, DexFilter(query = "x").active)
    }
}
