package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** R10-D (B3): the count the Pokédex sort chip adds (" · N") for the filters that now live in the sort sheet. */
class R10SortFilterTest {
    @Test fun nothingOnCountsZero() = assertEquals(0, DexFilter().sheetActive)
    @Test fun wishlistCountsOne() = assertEquals(1, DexFilter(wishlistOnly = true).sheetActive)
    @Test fun anyNumberOfGensCountsOne() {
        assertEquals(1, DexFilter(gens = setOf(1)).sheetActive)
        assertEquals(1, DexFilter(gens = setOf(1, 2, 3)).sheetActive)
    }
    @Test fun wishlistAndGensCountTwo() = assertEquals(2, DexFilter(wishlistOnly = true, gens = setOf(4, 5)).sheetActive)
    @Test fun typesSearchAndOwnedAreNotInTheSheet() =
        assertEquals(0, DexFilter(types = setOf(com.husarp.ankigo.game.Type.FIRE), query = "x", owned = DexOwned.OWNED).sheetActive)
}
