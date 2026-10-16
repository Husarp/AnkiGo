package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.TestData
import org.junit.Assert.*
import org.junit.Test

class BoxGroupsTest {
    private val base = listOf(TestData.species(1), TestData.species(25))
    private val data = TestData.data(base + TestData.species(25).copy(num = 10100, id = "testmon25x", baseNum = 25))

    private var n = 0
    private fun mon(sp: Int, lv: Int, shiny: Boolean = false, at: Long = 0) =
        TestData.mon(data, sp, lv, uid = "u${n++}").also { it.shiny = shiny; it.caughtAt = at }

    @Test fun duplicatesGroupBestFirstAndSingleIsPlain() {
        val g = BoxGroups.group(data, listOf(mon(25, 5), mon(1, 9), mon(25, 12), mon(25, 8)), false, false)
        assertEquals(listOf(1, 25), g.map { it.num })
        assertTrue(g[0].isSingle)
        assertEquals(3, g[1].count); assertEquals(listOf(12, 8, 5), g[1].members.map { it.level })
        assertEquals(12, g[1].best.level)
    }

    @Test fun tieBreakShinyThenLatest() {
        val a = mon(25, 10, at = 5); val b = mon(25, 10, at = 9); val c = mon(25, 10, shiny = true, at = 1)
        assertSame(c, BoxGroups.group(data, listOf(a, b, c), false, false)[0].best)
        assertSame(b, BoxGroups.group(data, listOf(a, b), false, false)[0].best)
    }

    @Test fun shinySeparate() {
        val ms = listOf(mon(25, 5), mon(25, 6, shiny = true), mon(25, 7))
        assertEquals(1, BoxGroups.group(data, ms, false, false).size)
        val g = BoxGroups.group(data, ms, true, false)
        assertEquals(listOf("25", "25s"), g.map { it.key }); assertEquals(listOf(2, 1), g.map { it.count })
    }

    @Test fun formsOnAndOff() {
        val ms = listOf(mon(25, 5), mon(10100, 20), mon(1, 3))
        val merged = BoxGroups.group(data, ms, false, false)
        assertEquals(listOf("1", "25"), merged.map { it.key }); assertEquals(2, merged[1].count)
        val sep = BoxGroups.group(data, ms, false, true)
        assertEquals(listOf("1", "25", "10100"), sep.map { it.key }); assertTrue(sep.all { it.isSingle })
    }

    @Test fun emptyBox() { assertTrue(BoxGroups.group(data, emptyList<Mon>(), true, true).isEmpty()) }
}
