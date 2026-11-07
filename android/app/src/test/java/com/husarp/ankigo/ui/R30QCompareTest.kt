package com.husarp.ankigo.ui

import com.husarp.ankigo.game.*
import org.junit.Assert.*
import org.junit.Test

class R30QCompareTest {
    private val data = TestData.world()
    private val picks = listOf(ComparePick.Dex(54), ComparePick.Dex(92))

    @Test fun unknownAndUnseenEntriesOnlyShowTypesAndNames() {
        val state = TestData.state(data).also { it.seen += 54 }
        for (evolved in listOf(false, true)) {
            val table = CompareTable.build(data, state, picks, evolved)
            assertEquals(listOf("types"), table.rows.map { it.key })
            assertEquals("Duckling", table.columns[0].name)
            assertEquals("???", table.columns[1].name)
            assertTrue(table.columns[1].hidden)
            assertTrue(table.columns.all { it.note == null })
        }
    }

    @Test fun mixedKnownAndUnknownCellsDoNotLeakValuesOrBestMarks() {
        val state = TestData.state(data).also { it.defeatedSpecies += 92; it.seen += 54 }
        val table = CompareTable.build(data, state, picks, false)
        for (row in table.rows.filter { it.key != "types" }) {
            val hidden = row.cells[0] as Cell.Text
            assertTrue(hidden.empty)
            assertNull(hidden.value)
            assertFalse(hidden.best)
            if (row.numeric) assertFalse((row.cells[1] as Cell.Text).best)
        }
        assertTrue(table.row("weak").cells[1] is Cell.Matchups)
        val unknownOnly = CompareModes.view(table, listOf(0))
        assertEquals(listOf("types"), unknownOnly.rows.map { it.key })
    }

    @Test fun onlyMovesUsedInCurrentBattleAppearInUnknownComparison() {
        val state = TestData.state(data).also { it.seen += 54 }
        state.battle = Battle(TestData.mon(data, 54, 10, listOf("watergun", "tackle")), Tier.NORMAL)
        val table = CompareTable.build(data, state, picks, false, usedWildMoves = setOf("Water Gun"))
        assertEquals(listOf("types", "moves"), table.rows.map { it.key })
        assertEquals(listOf("watergun"), (table.row("moves").cells[0] as Cell.Moves).moves.map { it.id })
        assertTrue((table.row("moves").cells[1] as Cell.Text).empty)
        val mixed = CompareTable.build(data, state, listOf(ComparePick.Owned("main"), picks[0]), false, usedWildMoves = setOf("Water Gun"))
        assertEquals(listOf("watergun"), (mixed.row("moves").cells[1] as Cell.Moves).moves.map { it.id })
        assertTrue((mixed.row("weak").cells[1] as Cell.Text).empty)
        state.battle = null
        assertNull(CompareTable.build(data, state, picks, false, usedWildMoves = setOf("Water Gun")).rowOrNull("moves"))
    }

    @Test fun catchOrDefeatRestoresSpeciesComparison() {
        for (caught in listOf(true, false)) {
            val state = GameState()
            if (caught) state.caught += listOf(54, 92) else state.defeatedSpecies += listOf(54, 92)
            val table = CompareTable.build(data, state, picks, false)
            assertNotNull(table.rowOrNull("base_hp"))
            assertTrue(table.row("weak").cells.all { it is Cell.Matchups })
        }
    }
}
