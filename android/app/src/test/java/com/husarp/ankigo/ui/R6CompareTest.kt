package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R6-05: Compare's "Stats | Base stats" switch (item 7), the sort fallback and the text of a head without tags (item 8). */
class R6CompareTest {
    private val world = TestData.world()
    private val data = GameData(world.species, world.moves, world.growth, world.itemPrices)

    private fun state(vararg uids: String): GameState {
        val s = GameState(createdAt = 1L, starterChosen = true)
        uids.forEach { s.mons += TestData.mon(data, 25, 20, listOf("tackle"), it) }
        return s
    }

    private val owned = CompareTable.build(data, state("a", "b"), listOf(ComparePick.Owned("a"), ComparePick.Owned("b")), false)
    private val dexOnly = CompareTable.build(data, state(), listOf(ComparePick.Dex(25), ComparePick.Dex(26)), false)
    private val G = CompareGroup.entries

    @Test fun rowKeysBelongToOneGroup() {
        for (k in listOf("hp", "atk", "def", "spa", "spd", "spe", "total")) assertEquals(k, CompareGroup.STATS, CompareGroups.of(k))
        for (k in listOf("base_hp", "base_spe", "base_total")) assertEquals(k, CompareGroup.BASE, CompareGroups.of(k))
        for (k in listOf("level", "types", "nature", "ivs", "moves", "weak", "evo", "becomes")) assertNull(k, CompareGroups.of(k))
    }

    @Test fun everyTableRowIsInTheRightGroup() {
        assertEquals(7, owned.rows.count { CompareGroups.of(it.key) == CompareGroup.STATS })
        assertEquals(7, owned.rows.count { CompareGroups.of(it.key) == CompareGroup.BASE })
    }

    @Test fun viewShowsOneGroupAtATime() {
        val order = listOf(0, 1)
        val stats = CompareModes.view(owned, order, RowChoice(), CompareGroup.STATS).rows.map { it.key }
        val base = CompareModes.view(owned, order, RowChoice(), CompareGroup.BASE).rows.map { it.key }
        assertTrue("hp" in stats && "total" in stats); assertFalse(stats.any { it.startsWith("base_") })
        assertTrue("base_hp" in base && "base_total" in base); assertFalse("hp" in base || "total" in base)
        for (k in listOf("level", "types", "nature", "moves", "weak")) { assertTrue(k in stats); assertTrue(k in base) }
        // no group = everything (as before)
        assertEquals(owned.rows.size, CompareModes.view(owned, order).rows.size)
    }

    @Test fun sectionHeadingsFollowTheShownGroup() {
        val base = CompareModes.view(owned, listOf(0, 1), RowChoice(), CompareGroup.BASE)
        assertEquals("Base stats", base.row("base_hp").section)
        val stats = CompareModes.view(owned, listOf(0, 1), RowChoice(), CompareGroup.STATS)
        assertEquals("Stats", stats.row("hp").section)
        assertEquals("Build", stats.row("nature").section)
    }

    @Test fun hiddenRowsStayHiddenInTheShownGroup() {
        val v = CompareModes.view(owned, listOf(0, 1), RowChoice(setOf("atk")), CompareGroup.STATS)
        assertNull(v.rowOrNull("atk")); assertEquals("Stats", v.row("hp").section)
    }

    @Test fun switchOnlyWhenBothGroupsHaveRows() {
        assertTrue(CompareGroups.switchable(owned))
        assertFalse(CompareGroups.switchable(dexOnly))
    }

    @Test fun pokedexOnlyFallsBackToBaseStats() {
        for (chosen in G) assertEquals(CompareGroup.BASE, CompareGroups.effective(dexOnly, chosen))
        for (chosen in G) assertEquals(chosen, CompareGroups.effective(owned, chosen))
    }

    @Test fun sortMovesToTheSameRowOfTheShownGroup() {
        assertEquals("base_atk", CompareGroups.sortKeyFor("atk", CompareGroup.BASE))
        assertEquals("base_total", CompareGroups.sortKeyFor("total", CompareGroup.BASE))
        assertEquals("atk", CompareGroups.sortKeyFor("base_atk", CompareGroup.STATS))
        assertEquals("spe", CompareGroups.sortKeyFor("base_spe", CompareGroup.STATS))
        // already in the shown group, a non-stat row and no sort stay as they are
        assertEquals("atk", CompareGroups.sortKeyFor("atk", CompareGroup.STATS))
        assertEquals("base_hp", CompareGroups.sortKeyFor("base_hp", CompareGroup.BASE))
        assertEquals("level", CompareGroups.sortKeyFor("level", CompareGroup.BASE))
        assertEquals("ivs", CompareGroups.sortKeyFor("ivs", CompareGroup.STATS))
        assertNull(CompareGroups.sortKeyFor(null, CompareGroup.BASE))
    }

    @Test fun mappedSortKeysAreRealRows() {
        for (r in owned.rows) {
            val g = CompareGroups.of(r.key) ?: continue
            val mapped = CompareGroups.sortKeyFor(r.key, CompareGroups.other(g))!!
            assertTrue(mapped, owned.rowOrNull(mapped)?.numeric == true)
            assertEquals(r.key, CompareGroups.sortKeyFor(mapped, g))
        }
    }

    @Test fun sortStillWorksAfterTheMapping() {
        val st = CompareState(CompareMode.BEST_OF_ALL)
        st.setSort(CompareGroups.sortKeyFor("hp", CompareGroup.BASE), true)
        assertEquals("base_hp", st.sortKey)
        assertEquals(listOf(0, 1), st.display(dexOnly).sorted())
    }

    @Test fun headInfoSaysWhatTheTagsSaid() {
        assertEquals("Pokédex entry, not one of your Pokémon", CompareInfo.headInfo(true, emptyList(), null))
        assertEquals("Main · Exp. Share", CompareInfo.headInfo(false, listOf("Main", "Exp. Share"), null))
        assertEquals("Main · from Sparkmouse", CompareInfo.headInfo(false, listOf("Main"), "from Sparkmouse"))
        assertEquals("Pokédex entry, not one of your Pokémon · already final", CompareInfo.headInfo(true, emptyList(), "already final"))
        assertNull(CompareInfo.headInfo(false, emptyList(), null))
        assertNull(CompareInfo.headInfo(false, listOf(" "), ""))
    }
}
