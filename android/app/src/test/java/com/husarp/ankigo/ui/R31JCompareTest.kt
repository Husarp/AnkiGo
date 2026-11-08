package com.husarp.ankigo.ui

import com.husarp.ankigo.game.*
import org.junit.Assert.*
import org.junit.Test

class R31JCompareTest {
    private val data = TestData.world()
    private val picks = listOf(ComparePick.Owned("main"), ComparePick.Dex(54))
    private fun entries(table: CompareData, column: Int) = table.rows.filter { it.key != "strong" }.flatMap { (it.cells[column] as? Cell.Matchups)?.entries.orEmpty() }

    private fun unknown(table: CompareData, column: Int) = table.rows.filter { it.key != "strong" }.sumOf { (it.cells[column] as? Cell.Matchups)?.unknownCount ?: 0 }

    @Test fun owningOrCatchingDoesNotShowAnyUnseenMatchup() {
        val state = TestData.state(data).also { it.caught += 54 }
        val table = CompareTable.build(data, state, picks, false)
        assertTrue(entries(table, 0).isEmpty())
        assertTrue(entries(table, 1).isEmpty())
        assertEquals(18, unknown(table, 0))
        assertEquals(18, unknown(table, 1))
        assertNotNull(table.rowOrNull("base_hp"))
    }

    @Test fun observedWildTypesAppearEvenWhileStatsAreUnknownAndWaitEachEncounter() {
        val state = TestData.state(data)
        state.battle = Battle(TestData.mon(data, 54, 20), Tier.NORMAL)
        state.seenMatchups[54] = mutableSetOf(Type.GRASS, Type.NORMAL)
        var table = CompareTable.build(data, state, picks, false)
        assertTrue(table.rows.filter { it.key != "types" }.all { (it.cells[1] as? Cell.Text)?.empty == true })
        state.battle!!.playerAttacked = true
        table = CompareTable.build(data, state, picks, false)
        assertEquals(listOf(Matchup(Type.GRASS, 2.0), Matchup(Type.NORMAL, 1.0)), entries(table, 1))
        assertEquals(16, unknown(table, 1))
        assertTrue((table.row("base_hp").cells[1] as Cell.Text).empty)
        assertEquals(16, unknown(CompareModes.view(table, listOf(1)), 0))
    }

    @Test fun fullyEvolvedComparisonUsesDiscoveryOfTheShownSpecies() {
        val state = TestData.state(data, 129, 20)
        state.seenMatchups[129] = mutableSetOf(Type.ELECTRIC)
        val evolved = CompareTable.build(data, state, listOf(ComparePick.Owned("main")), true)
        assertTrue(entries(evolved, 0).isEmpty())
        assertEquals(18, unknown(evolved, 0))
        state.seenMatchups[130] = mutableSetOf(Type.ELECTRIC)
        val learned = CompareTable.build(data, state, listOf(ComparePick.Owned("main")), true)
        assertEquals(listOf(Matchup(Type.ELECTRIC, 4.0)), entries(learned, 0))
    }
}
