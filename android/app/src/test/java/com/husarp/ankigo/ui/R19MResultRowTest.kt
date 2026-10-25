package com.husarp.ankigo.ui

import com.husarp.ankigo.ui.CatchTilesPlan.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R19 M (PLAN 174B): the result row has no "Auto" tile; a real throw stays; the compact words are shorter; TalkBack has the whole fact. */
class R19MResultRowTest {
    private fun xp(n: Int) = listOf(CatchSummary.XpLine("a", "A", n, 5, false))
    private fun sum(caught: Boolean = true, isNew: Boolean = true, thrown: Boolean = false, coins: Int = 10) = CatchSummary(
        caught, "Pidgey", if (caught) CatchSummary.PokedexLine(isNew, 25) else null,
        if (thrown) CatchSummary.ThrowLine("poke-ball", 2, 45) else null, xp(120), coins, CatchSummary.NEXT_TEXT)

    @Test fun autoCatchHasNoAutoTile() {
        assertEquals(listOf(Kind.DEX, Kind.XP, Kind.COINS), CatchTilesPlan.rowTiles(sum()).map { it.kind })
        assertEquals(listOf("New #25", "+120 XP", "+10"), CatchTilesPlan.rowTiles(sum()).map { it.value })
        assertTrue(Kind.values().none { it.name == "AUTO" })
    }

    @Test fun realThrowStaysAndCompactIsShorter() {
        val full = CatchTilesPlan.rowTiles(sum(thrown = true))
        val small = CatchTilesPlan.rowTiles(sum(thrown = true), compact = true)
        assertEquals("Throw 2 · 45 %", full[1].value)
        assertEquals("Throw 2", small[1].value)
        assertEquals("poke-ball", small[1].ball)
        assertEquals("Caught", CatchTilesPlan.rowTiles(sum(isNew = false), compact = true)[0].value)
        assertEquals("Caught before", CatchTilesPlan.rowTiles(sum(isNew = false))[0].value)
        for ((f, c) in full.zip(small)) assertTrue(c.value.length <= f.value.length)
    }

    @Test fun talkBackTextsAreWholeFacts() {
        val d = CatchTilesPlan.rowTiles(sum(thrown = true)).map { it.desc }
        assertEquals(listOf("Caught, new, number 25", "Throw 2, 45 percent chance", "120 XP", "10 coins"), d)
        assertEquals(listOf("Defeated", "120 XP"), CatchTilesPlan.rowTiles(sum(caught = false, coins = 0)).map { it.desc.ifEmpty { it.value } })
    }
}
