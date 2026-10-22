package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** R10-D (B3), R16-2-E: how many filters live in the sort sheet and are on (the sort chip's description says so; the types joined the sheet in 192A). */
class R10SortFilterTest {
    @Test fun nothingOnCountsZero() = assertEquals(0, DexFilter().sheetActive)
    @Test fun wishlistCountsOne() = assertEquals(1, DexFilter(wishlistOnly = true).sheetActive)
    @Test fun anyNumberOfGensCountsOne() {
        assertEquals(1, DexFilter(gens = setOf(1)).sheetActive)
        assertEquals(1, DexFilter(gens = setOf(1, 2, 3)).sheetActive)
    }
    @Test fun wishlistAndGensCountTwo() = assertEquals(2, DexFilter(wishlistOnly = true, gens = setOf(4, 5)).sheetActive)
    @Test fun typesCountOneWhateverTheModeAndTheNumber() {
        assertEquals(1, DexFilter(types = setOf(com.husarp.ankigo.game.Type.FIRE)).sheetActive)
        assertEquals(1, DexFilter(types = setOf(com.husarp.ankigo.game.Type.FIRE, com.husarp.ankigo.game.Type.ICE), typeMatch = TypeMatch.ONLY).sheetActive)
        assertEquals(0, DexFilter(typeMatch = TypeMatch.ONLY).sheetActive) // a mode alone filters nothing
    }
    @Test fun searchAndOwnedAreNotInTheSheet() = assertEquals(0, DexFilter(query = "x", owned = DexOwned.OWNED).sheetActive)
}
