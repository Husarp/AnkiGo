package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.TestData
import org.junit.Assert.*
import org.junit.Test

class BoxTilesTest {
    private val base = listOf(TestData.species(1), TestData.species(25))
    private val data = TestData.data(base + TestData.species(25).copy(num = 10100, id = "testmon25x", baseNum = 25))

    private var n = 0
    private fun mon(sp: Int, lv: Int, shiny: Boolean = false, at: Long = 0, fav: Boolean = false) =
        TestData.mon(data, sp, lv, uid = "u${n++}").also { it.shiny = shiny; it.caughtAt = at; it.favourite = fav }

    @Test fun groupedTilesKeepShiniesInAndSeparateRegionalForms() {
        val ms = listOf(mon(25, 5), mon(25, 6, shiny = true), mon(10100, 20), mon(1, 3), mon(25, 12))
        val t = BoxTiles.build(data, ms, grouped = true)
        assertEquals(listOf(1, 25, 10100), t.map { it.best.species })
        assertEquals(listOf(1, 3, 1), t.map { it.count })
        assertTrue(t[1].isGroup); assertFalse(t[0].isGroup)
        assertEquals(12, t[1].best.level)
        assertTrue(t[1].anyShiny())
    }

    @Test fun ungroupedIsOneTilePerMonBySpeciesThenLevel() {
        val ms = listOf(mon(25, 12), mon(1, 9), mon(25, 5))
        val t = BoxTiles.build(data, ms, grouped = false)
        assertEquals(3, t.size); assertTrue(t.none { it.isGroup })
        assertEquals(listOf(1 to 9, 25 to 5, 25 to 12), t.map { it.best.species to it.best.level })
    }

    @Test fun favouritesFilterThenGroup() {
        val a = mon(25, 5, fav = true); val b = mon(25, 6); val c = mon(1, 9, fav = true)
        assertEquals(listOf(a, c), BoxTiles.filter(listOf(a, b, c), BoxFilter.FAVOURITES))
        assertEquals(listOf(a, b, c), BoxTiles.filter(listOf(a, b, c), BoxFilter.ALL))
        val t = BoxTiles.build(data, BoxTiles.filter(listOf(a, b, c), BoxFilter.FAVOURITES), grouped = true)
        assertEquals(listOf(1, 1), t.map { it.count })
        assertTrue(t.all { it.anyFavourite() })
    }

    @Test fun tileMarksSeeEveryCopy() {
        val a = mon(25, 5); val b = mon(25, 9)
        val t = BoxTiles.build(data, listOf(a, b), grouped = true).single()
        assertTrue(t.anyInTeam(listOf(a.uid))); assertFalse(t.anyInTeam(listOf("x")))
        assertFalse(t.anyFavourite()); assertFalse(t.anyShiny())
    }

    @Test fun copiesSortByLevelOrRecent() {
        val a = mon(25, 24, at = 100); val b = mon(25, 17, at = 300); val c = mon(25, 9, at = 200)
        assertEquals(listOf(a, b, c), BoxTiles.order(listOf(c, a, b), CopySort.LEVEL))
        assertEquals(listOf(b, c, a), BoxTiles.order(listOf(c, a, b), CopySort.RECENT))
    }

    @Test fun sortTiesAreStable() {
        val a = mon(25, 10, at = 5); val b = mon(25, 10, at = 9); val s = mon(25, 10, shiny = true, at = 1)
        assertEquals(listOf(s, b, a), BoxTiles.order(listOf(a, b, s), CopySort.LEVEL))
        val x = mon(25, 4, at = 7); val y = mon(25, 30, at = 7)
        assertEquals(listOf(y, x), BoxTiles.order(listOf(x, y), CopySort.RECENT))
    }

    @Test fun emptyAndSingle() {
        assertTrue(BoxTiles.build(data, emptyList<Mon>(), true).isEmpty())
        assertTrue(BoxTiles.build(data, emptyList<Mon>(), false).isEmpty())
        assertFalse(BoxTiles.build(data, listOf(mon(1, 2)), true).single().isGroup)
    }
}
