package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareStateTest {
    private val world = TestData.world()
    private val data = GameData(world.species, world.moves, world.growth, world.itemPrices)
    private val sp = data.species.values.first()

    /** [levels] = one numeric row "level" ("owned" columns carry a mon-less Dex pick, so the filter is tested with [ownedMask]). */
    private fun table(vararg levels: Int?): CompareData {
        val cols = levels.indices.map { i -> CompareColumn(ComparePick.Dex(i + 1), null, sp, sp, "c$i", false, null) }
        return CompareData(cols, listOf(CompareRow("level", "Level", levels.map { Cell.Text(it?.toString() ?: DASH, value = it) }, numeric = true)))
    }

    @Test fun startsInPickOrderWithTheFirstAsReference() {
        val t = table(5, 9, 7)
        val s = CompareState()
        assertEquals(listOf(0, 1, 2), s.display(t))
        assertEquals(0, s.reference(t))
    }

    @Test fun makeReferencePutsItFirstAndKeepsTheRestInOrder() {
        val t = table(5, 9, 7, 1)
        val s = CompareState()
        s.makeReference(t, 2)
        assertEquals(listOf(2, 0, 1, 3), s.display(t))
        // in "best of all" there is no reference: the manual order shows
        s.mode = CompareMode.BEST_OF_ALL
        assertEquals(listOf(0, 1, 2, 3), s.display(t))
        assertNull(s.reference(t))
    }

    @Test fun tappingARowSortsHighToLowThenFlips() {
        val t = table(5, 9, 7, 1)
        val s = CompareState(CompareMode.BEST_OF_ALL)
        s.tapRow("level")
        assertEquals(listOf(1, 2, 0, 3), s.display(t))
        s.tapRow("level")
        assertEquals(listOf(3, 0, 2, 1), s.display(t))
        s.tapRow("level")
        assertEquals(listOf(1, 2, 0, 3), s.display(t))
        s.setSort(null)
        assertEquals(listOf(0, 1, 2, 3), s.display(t)) // the manual order comes back
    }

    @Test fun sortKeepsTheReferenceFirst() {
        val t = table(5, 9, 7, 1)
        val s = CompareState()
        s.makeReference(t, 3)
        s.tapRow("level")
        assertEquals(listOf(3, 1, 2, 0), s.display(t))
    }

    @Test fun moveWorksOnTheShownOrderAndSpendsTheSort() {
        val t = table(5, 9, 7, 1)
        val s = CompareState(CompareMode.BEST_OF_ALL)
        s.tapRow("level") // 1 2 0 3
        assertTrue(s.canMove(t, 0, -1)); assertFalse(s.canMove(t, 1, -1)); assertFalse(s.canMove(t, 3, 1))
        s.move(t, 0, -1)
        assertEquals(listOf(1, 0, 2, 3), s.display(t))
        assertNull(s.sortKey)
    }

    @Test fun theReferenceDoesNotMoveInVsMode() {
        val t = table(5, 9, 7)
        val s = CompareState()
        assertFalse(s.canMove(t, 0, 1))
        assertTrue(s.canMove(t, 1, 1)); assertFalse(s.canMove(t, 1, -1)) // only 1 and 2 can swap
        s.move(t, 1, 1)
        assertEquals(listOf(0, 2, 1), s.display(t))
        assertEquals(0, s.reference(t))
    }

    @Test fun removeDropsTheColumnAndTheReferencePassesOn() {
        val t = table(5, 9, 7)
        val s = CompareState()
        s.makeReference(t, 1)
        s.remove(t, 1)
        assertEquals(2, s.size)
        assertEquals(listOf(0, 2), s.display(t))
        assertEquals(0, s.reference(t))
    }

    @Test fun filterKeepsOwnedOrDexAndFitDropsGoneColumns() {
        val t = table(5, 9)
        val s = CompareState()
        s.filter = ColumnFilter(owner = OwnerFilter.OWNED) // both columns are Pokédex entries here
        assertEquals(emptyList<Int>(), s.display(t))
        s.filter = ColumnFilter(owner = OwnerFilter.DEX)
        assertEquals(listOf(0, 1), s.display(t))
        s.fit(table(5))
        assertEquals(listOf(0), s.display(table(5)))
        assertEquals(1, s.size)
    }

    @Test fun sortingDoesNotChangeTheDefaultReference() {
        val t = table(5, 9, 7, 1)
        val s = CompareState()
        s.tapRow("level")                                   // no explicit reference: column 0 stays it
        assertEquals(0, s.reference(t))
        assertEquals(listOf(0, 1, 2, 3), s.display(t))
        s.tapRow("level")
        assertEquals(0, s.reference(t))
    }

    /** A table holding only [picks] (as when a Pokémon was released: the other columns are gone from the data). */
    private fun tableOf(vararg picks: Int): CompareData {
        val cols = picks.map { n -> CompareColumn(ComparePick.Dex(n), null, sp, sp, "c$n", false, null) }
        return CompareData(cols, listOf(CompareRow("level", "Level", picks.map { Cell.Text("$it", value = it) }, numeric = true)))
    }

    @Test fun removalsAndOrderFollowThePickNotTheIndex() {
        val s = CompareState(CompareMode.BEST_OF_ALL)
        val abc = tableOf(1, 2, 3)
        s.fit(abc)
        s.remove(abc, 1)                                     // remove B
        assertEquals(listOf(0, 2), s.display(abc))
        val bc = tableOf(2, 3)                               // then A is released: the data is [B, C]
        s.fit(bc)
        assertEquals(listOf(1), s.display(bc))               // B stays removed, C stays
        assertEquals(1, s.size)
    }

    @Test fun theReferenceIsKeptByPickWhenColumnsDisappear() {
        val s = CompareState()
        val abc = tableOf(1, 2, 3)
        s.makeReference(abc, 2)                              // C
        val bc = tableOf(2, 3)
        assertEquals(listOf(1, 0), s.display(bc))            // C (now index 1) is still first
        assertEquals(1, s.reference(bc))
    }

    @Test fun resetViewClearsFilterAndSort() {
        val t = table(5, 9)
        val s = CompareState()
        s.filter = ColumnFilter(owner = OwnerFilter.OWNED)
        s.tapRow("level")
        assertTrue(s.display(t).isEmpty())
        s.resetView()
        assertEquals(listOf(0, 1), s.display(t))
        assertNull(s.sortKey)
    }

    @Test fun picksSurviveTheIntent() {
        val picks = listOf(ComparePick.Owned("a:b"), ComparePick.Dex(25), ComparePick.Owned("x"))
        assertEquals(picks, CompareArgs.decodeAll(picks.map(CompareArgs::encode)))
        assertEquals(listOf(ComparePick.Dex(3)), CompareArgs.decodeAll(listOf("d:3", "d:x", "junk", "o:", "")))
        assertEquals(emptyList<ComparePick>(), CompareArgs.decodeAll(null))
    }
}
