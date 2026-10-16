package com.husarp.ankigo.ui

import com.husarp.ankigo.game.TestData
import org.junit.Assert.assertEquals
import org.junit.Test

class DexCopiesTest {
    private val data = TestData.world()

    @Test fun copiesOfOneSpeciesSortedByLevelDescShinyFirstOnTie() {
        val a = TestData.mon(data, 25, 10, uid = "a")
        val b = TestData.mon(data, 25, 30, uid = "b")
        val c = TestData.mon(data, 25, 10, uid = "c").also { it.shiny = true }
        val other = TestData.mon(data, 26, 99, uid = "o")
        assertEquals(listOf("b", "c", "a"), DexDetail.copies(listOf(a, other, b, c), 25).map { it.uid })
        assertEquals(emptyList<String>(), DexDetail.copies(listOf(a, b), 7).map { it.uid })
    }

    @Test fun copyNote() {
        val m = TestData.mon(data, 25, 10)
        assertEquals("In box", DexDetail.copyNote(m, false))
        assertEquals("In team", DexDetail.copyNote(m, true))
        m.nickname = "Zappy"
        assertEquals("“Zappy” · In team", DexDetail.copyNote(m, true))
        m.nickname = " "
        assertEquals("In box", DexDetail.copyNote(m, false))
    }
}
