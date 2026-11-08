package com.husarp.ankigo.ui

import com.husarp.ankigo.game.*
import org.junit.Assert.*
import org.junit.Test

class R32BMatchupSectionsTest {
    @Test fun hiddenTypesStayInTheirRealSectionAtEveryDiscoveryStage() {
        for (types in listOf(listOf(Type.WATER), listOf(Type.GHOST, Type.POISON), listOf(Type.WATER, Type.FLYING), listOf(Type.GRASS, Type.STEEL))) {
            for (seen in listOf(emptySet(), setOf(Type.NORMAL, Type.FIRE, Type.ELECTRIC), Type.entries.toSet())) {
                val sections = MatchupGroups.sections(types, TypeMatchups.discovered(types, seen))
                val all = TypeMatchups.of(types)
                val expected = mapOf("weak" to all.weakTo, "resist" to all.resists, "immune" to all.immune, "strong" to all.strongAgainst)
                for ((key, entries) in expected) {
                    val cell = sections.getValue(key)
                    assertEquals(entries.filter { it.type in seen }, cell.entries)
                    assertEquals(entries.filter { it.type !in seen }, cell.hidden)
                }
                val defensive = sections.filterKeys { it != "strong" }.values
                assertEquals(18, defensive.sumOf { it.entries.size + it.unknownCount })
                assertEquals(18 - seen.size, defensive.sumOf { it.unknownCount })
                assertEquals(seen, defensive.flatMap { it.entries }.map { it.type }.toSet())
            }
        }
    }

    @Test fun compareUsesSectionsAndKeepsUnseenNamesOutOfVisibleEntries() {
        val data = TestData.world()
        val state = TestData.state(data)
        val table = CompareTable.build(data, state, listOf(ComparePick.Owned("main")), false)
        assertNull(table.rowOrNull("unseen_matchups"))
        for (key in listOf("weak", "resist", "immune", "normal", "strong")) {
            assertTrue((table.row(key).cells[0] as Cell.Matchups).entries.isEmpty())
        }
    }
}
