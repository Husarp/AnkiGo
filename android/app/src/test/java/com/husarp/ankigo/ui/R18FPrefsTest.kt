package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R18-F (PLAN 249): the Pokédex filters and the Box Show choice are kept; encode / decode round trips, unknown or old values give the default. */
class R18FPrefsTest {
    private fun roundTrip(f: DexFilter) = SortPrefs.decodeDex(SortPrefs.encodeDex(f))

    @Test fun dexFiltersRoundTrip() {
        val f = DexFilter(owned = DexOwned.NOT_OWNED, gens = setOf(3, 1, 2), wishlistOnly = true, favouritesOnly = true)
        val r = roundTrip(f)
        assertEquals(DexOwned.NOT_OWNED, r.owned)
        assertEquals(setOf(1, 2, 3), r.gens)
        assertTrue(r.wishlistOnly); assertTrue(r.favouritesOnly)
        assertEquals("NOT_OWNED;1,2,3;1;1", SortPrefs.encodeDex(f))
    }

    @Test fun defaultsRoundTripToDefaults() {
        val r = roundTrip(DexFilter())
        assertEquals(DexFilter(), r)
        assertEquals("ALL;;0;0", SortPrefs.encodeDex(DexFilter()))
    }

    @Test fun theSearchTextIsNeverKept() {
        val r = roundTrip(DexFilter(query = "pika", owned = DexOwned.OWNED))
        assertEquals("", r.query)
        assertEquals(DexOwned.OWNED, r.owned)
    }

    @Test fun unknownOrGarbledValuesGiveDefaults() {
        assertEquals(DexFilter(), SortPrefs.decodeDex(null))
        assertEquals(DexFilter(), SortPrefs.decodeDex(""))
        assertEquals(DexFilter(), SortPrefs.decodeDex("nonsense"))
        assertEquals(DexFilter(), SortPrefs.decodeDex("MARS;x,y;7;?"))
        // a part that is fine stays, a broken part falls back alone
        val r = SortPrefs.decodeDex("OWNED;1,x;1;0")
        assertEquals(DexOwned.OWNED, r.owned); assertTrue(r.gens.isEmpty()); assertTrue(r.wishlistOnly); assertFalse(r.favouritesOnly)
        // an old save with only the first parts still loads
        assertEquals(setOf(2), SortPrefs.decodeDex("ALL;2").gens)
        assertTrue(SortPrefs.decodeDex("ALL;0,1").gens.isEmpty())
    }

    @Test fun decodeKeepsTheSortAndTypesOfTheBase() {
        val base = DexFilter(types = setOf(com.husarp.ankigo.game.Type.FIRE), sort = DexSort.NAME, query = "q")
        val r = SortPrefs.decodeDex("OWNED;2;0;1", base)
        assertEquals(base.types, r.types); assertEquals(DexSort.NAME, r.sort); assertEquals("q", r.query)
        assertEquals(DexOwned.OWNED, r.owned)
    }

    @Test fun boxShowRoundTripsAndUnknownIsAll() {
        for (f in BoxFilter.entries) assertEquals(f, SortPrefs.decodeShow(f.name))
        assertEquals(BoxFilter.ALL, SortPrefs.decodeShow(null))
        assertEquals(BoxFilter.ALL, SortPrefs.decodeShow("OLD_VALUE"))
        assertEquals(BoxFilter.ALL, SortPrefs.decodeShow(""))
    }

    @Test fun onlyTheSheetOnlyShowChoicesHaveNoQuickChip() {
        assertEquals(setOf(BoxFilter.FAVOURITES, BoxFilter.SHINY, BoxFilter.IN_TEAM), BoxFilter.entries.filter { it.hasQuickChip }.toSet())
    }
}
