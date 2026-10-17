package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareModesTest {
    private val world = TestData.world()
    private val extra = listOf(
        TestData.species(900, "Mudfish", listOf(Type.WATER, Type.GROUND), TestData.stats(70, 60, 60, 50, 50, 40)),
    )
    private val data = GameData(world.species + extra.associateBy { it.num }, world.moves, world.growth, world.itemPrices)

    private fun mon(sp: Int, lvl: Int, uid: String): Mon = TestData.mon(data, sp, lvl, listOf("tackle"), uid)
    private fun state(vararg mons: Mon): GameState =
        GameState(createdAt = 1L, starterChosen = true).also { s -> mons.forEach { s.mons += it } }

    private fun table(picks: List<ComparePick>, vararg mons: Mon) = CompareTable.build(data, state(*mons), picks, false)
    private fun owned(vararg uids: String) = uids.map { ComparePick.Owned(it) }

    private fun fakeTable(vararg levels: Int?): CompareData {
        // a hand-made numeric row "level" plus a non-numeric row, so the maths is tested without the game data
        val cols = levels.indices.map { i ->
            CompareColumn(ComparePick.Dex(1), null, data.species.values.first(), data.species.values.first(), "c$i", false, null)
        }
        val cells = levels.map { Cell.Text(it?.toString() ?: DASH, value = it) }
        return CompareData(cols, listOf(
            CompareRow("level", "Level", cells, numeric = true),
            CompareRow("types", "Types", levels.map { Cell.TypeChips(listOf(Type.FIRE)) }),
        ))
    }

    // ---- value / numeric flag ----

    @Test fun numericRowsCarryValuesAndFlag() {
        val a = mon(25, 26, "a"); val b = mon(2, 24, "b")
        val t = table(owned("a", "b"), a, b)
        assertEquals(listOf(26, 24), t.row("level").cells.map { (it as Cell.Text).value })
        assertTrue(t.row("level").numeric)
        assertTrue(t.row("hp").numeric); assertTrue(t.row("total").numeric); assertTrue(t.row("base_spe").numeric)
        assertTrue(t.row("ivs").numeric)
        assertEquals(listOf(a.ivs.total, b.ivs.total), t.row("ivs").cells.map { (it as Cell.Text).value })
        assertFalse(t.row("types").numeric); assertFalse(t.row("nature").numeric); assertFalse(t.row("moves").numeric)
        assertFalse(t.row("becomes").numeric)
        // a Pokédex column has no value in the owned-only rows
        val d = table(listOf(ComparePick.Owned("a"), ComparePick.Dex(3)), a)
        assertNull((d.row("hp").cells[1] as Cell.Text).value)
        assertEquals(525, (d.row("base_total").cells[1] as Cell.Text).value)
    }

    // ---- vs reference ----

    @Test fun diffsAgainstReferenceWithNullsNegativeAndZero() {
        val t = fakeTable(20, 32, 12, null, 20)
        val d = CompareModes.diffs(t, 0)["level"]!!
        assertEquals(listOf(null, 12, -8, null, 0), d)
        assertFalse(CompareModes.diffs(t, 0).containsKey("types"))
        // the reference has no value: nothing to diff against
        assertEquals(listOf(null, null, null, null, null), CompareModes.diffs(t, 3)["level"])
        // reference switch
        assertEquals(listOf(-12, null, -20, null, -12), CompareModes.diffs(t, 1)["level"])
        assertTrue(CompareModes.diffs(t, 9).isEmpty())
        assertTrue(CompareModes.diffs(t, -1).isEmpty())
    }

    @Test fun diffsOnRealTable() {
        val a = mon(25, 26, "a"); val b = mon(2, 24, "b")
        val t = table(owned("a", "b"), a, b)
        assertEquals(listOf(null, -2), CompareModes.diffs(t, 0)["level"])
        assertEquals(listOf(2, null), CompareModes.diffs(t, 1)["level"])
        assertEquals(listOf(null, 405 - 320), CompareModes.diffs(t, 0)["base_total"])
    }

    @Test fun diffLabelsAndSigns() {
        assertEquals("+12", CompareModes.diffLabel(12))
        assertEquals("−8", CompareModes.diffLabel(-8))
        assertEquals("±0", CompareModes.diffLabel(0))
        assertEquals("", CompareModes.diffLabel(null))
        assertEquals(1, CompareModes.diffSign(3)); assertEquals(-1, CompareModes.diffSign(-3))
        assertEquals(0, CompareModes.diffSign(0)); assertEquals(0, CompareModes.diffSign(null))
    }

    // ---- best of all ----

    @Test fun ranksTiersTiesNullsAndSingle() {
        assertEquals(listOf(1, 0, 0, 2), CompareModes.ranks(listOf(5, 9, 9, 3)))
        assertEquals(listOf(null, 0, null, 1), CompareModes.ranks(listOf(null, 8, null, 2)))
        assertEquals(listOf(0, 0), CompareModes.ranks(listOf(4, 4)))
        assertEquals(listOf<Int?>(null, null, null), CompareModes.ranks(listOf(7, null, null)))
        assertEquals(listOf<Int?>(null), CompareModes.ranks(listOf(7)))
        assertEquals(emptyList<Int?>(), CompareModes.ranks(emptyList()))
        // tier 0 equals bestIndexes
        val v = listOf(3, 9, 5, 9, null)
        assertEquals(CompareTable.bestIndexes(v), CompareModes.ranks(v).withIndex().filter { it.value == 0 }.map { it.index }.toSet())
    }

    @Test fun rankRowsCoversNumericRowsOnly() {
        val r = CompareModes.rankRows(fakeTable(5, 9, 9, 3))
        assertEquals(setOf("level"), r.keys)
        assertEquals(listOf(1, 0, 0, 2), r["level"])
    }

    // ---- order ----

    @Test fun moveReferenceAndRemove() {
        val o = CompareModes.identity(5)
        assertEquals(listOf(0, 1, 2, 3, 4), o)
        assertEquals(listOf(1, 2, 0, 3, 4), CompareModes.move(o, 0, 2))
        assertEquals(listOf(0, 1, 4, 2, 3), CompareModes.move(o, 4, 2))
        assertEquals(o, CompareModes.move(o, 2, 2))
        assertEquals(o, CompareModes.move(o, 7, 0)); assertEquals(o, CompareModes.move(o, 0, -1))
        assertEquals(listOf(3, 0, 1, 2, 4), CompareModes.makeReference(o, 3))
        assertEquals(o, CompareModes.makeReference(o, 0))
        assertEquals(listOf(0, 1, 3, 4), CompareModes.remove(o, 2))
        assertEquals(o, CompareModes.remove(o, 5))
        // reference after removing the old one: the next column becomes first
        assertEquals(listOf(1, 2, 3, 4), CompareModes.remove(o, 0))
        assertEquals(emptyList<Int>(), CompareModes.remove(listOf(3), 0))
    }

    @Test fun sortByRowDescAscNullsLastStable() {
        val t = fakeTable(5, null, 9, 5, 9, null)
        val o = CompareModes.identity(6)
        assertEquals(listOf(2, 4, 0, 3, 1, 5), CompareModes.sortBy(t, o, "level", true))
        assertEquals(listOf(0, 3, 2, 4, 1, 5), CompareModes.sortBy(t, o, "level", false))
        // keeps working on an already reordered / reduced order
        assertEquals(listOf(4, 3, 5), CompareModes.sortBy(t, listOf(3, 5, 4), "level", true))
        assertEquals(o, CompareModes.sortBy(t, o, "types"))
        assertEquals(o, CompareModes.sortBy(t, o, "nope"))
    }

    @Test fun filterByOwnershipAndType() {
        val a = mon(25, 26, "a"); val b = mon(2, 24, "b")
        val t = table(listOf(ComparePick.Owned("a"), ComparePick.Dex(3), ComparePick.Owned("b"), ComparePick.Dex(900)), a, b)
        val o = CompareModes.identity(4)
        assertEquals(o, CompareModes.filter(t, o, ColumnFilter()))
        assertEquals(listOf(0, 2), CompareModes.filter(t, o, ColumnFilter(OwnerFilter.OWNED)))
        assertEquals(listOf(1, 3), CompareModes.filter(t, o, ColumnFilter(OwnerFilter.DEX)))
        assertEquals(listOf(3), CompareModes.filter(t, o, ColumnFilter(types = setOf(Type.GROUND))))
        assertEquals(listOf(3), CompareModes.filter(t, o, ColumnFilter(OwnerFilter.DEX, setOf(Type.GROUND, Type.WATER))))
        assertEquals(emptyList<Int>(), CompareModes.filter(t, o, ColumnFilter(OwnerFilter.OWNED, setOf(Type.GROUND))))
        assertEquals(listOf(3, 1), CompareModes.filter(t, listOf(3, 0, 1), ColumnFilter(OwnerFilter.DEX)))
    }

    // ---- row choice ----

    @Test fun rowChoiceRoundTrip() {
        assertEquals(RowChoice(), RowChoice.decode(null))
        assertEquals(RowChoice(), RowChoice.decode(""))
        assertEquals("", RowChoice().encode())
        val c = RowChoice().toggle("hp").toggle("ivs")
        assertFalse(c.visible("hp")); assertTrue(c.visible("atk"))
        assertEquals("hp,ivs", c.encode())
        assertEquals(c, RowChoice.decode(c.encode()))
        assertEquals(c, RowChoice.decode(" ivs , hp ,,"))
        assertEquals(RowChoice().toggle("ivs"), c.toggle("hp"))
        assertEquals(RowChoice(), c.toggle("hp").toggle("ivs"))
    }

    @Test fun viewHidesRowsAndMovesOrDropsSections() {
        val a = mon(25, 26, "a"); val b = mon(2, 24, "b")
        val t = table(owned("a", "b"), a, b)
        val all = CompareModes.view(t, CompareModes.identity(2))
        assertEquals(t.rows.map { it.key }, all.rows.map { it.key })
        assertEquals(t.rows.map { it.section }, all.rows.map { it.section })
        // hiding the row that carries the heading moves the heading to the next visible row of that group
        val noHp = CompareModes.view(t, CompareModes.identity(2), RowChoice().toggle("hp"))
        assertFalse(noHp.rows.any { it.key == "hp" })
        assertEquals("Stats", noHp.row("atk").section)
        // a group with no visible rows disappears with its heading
        val noMoves = CompareModes.view(t, CompareModes.identity(2), RowChoice().toggle("moves"))
        assertFalse(noMoves.rows.any { it.section == "Moves" })
        assertEquals(t.rows.size - 1, noMoves.rows.size)
        val noBase = CompareModes.view(t, CompareModes.identity(2), RowChoice(setOf("base_hp", "base_atk", "base_def", "base_spa", "base_spd", "base_spe", "base_total")))
        assertFalse(noBase.rows.any { it.section == "Base stats" })
        // a hidden heading never leaks onto the next group's first row
        assertEquals("Build", noBase.row("nature").section)
        // hiding everything is fine
        val none = CompareModes.view(t, CompareModes.identity(2), RowChoice(t.rows.map { it.key }.toSet()))
        assertTrue(none.rows.isEmpty())
    }

    @Test fun viewReordersColumnsAndRecomputesBest() {
        val a = mon(25, 20, "a"); val b = mon(2, 30, "b"); val c = mon(25, 25, "c")
        val t = table(owned("a", "b", "c"), a, b, c)
        val v = CompareModes.view(t, listOf(2, 0))
        assertEquals(listOf("c", "a"), v.columns.map { it.pick.let { p -> (p as ComparePick.Owned).uid } })
        assertEquals(listOf(25, 20), v.row("level").cells.map { (it as Cell.Text).value })
        // b (the overall best) was removed: best is recomputed among the shown columns
        assertEquals(listOf(true, false), v.row("level").cells.map { (it as Cell.Text).best })
        // a single remaining column has nothing to compare
        val one = CompareModes.view(t, listOf(1))
        assertEquals(listOf(false), one.row("level").cells.map { (it as Cell.Text).best })
        // non-numeric cells pass through; out-of-range indexes are ignored
        assertEquals(t.row("types").cells[2], v.row("types").cells[0])
        assertEquals(1, CompareModes.view(t, listOf(1, 9)).columns.size)
    }

    // ---- any number of columns ----

    @Test fun buildWorksForOneSevenAndTwentyPicks() {
        for (n in listOf(1, 2, 7, 20)) {
            val mons = (0 until n).map { mon(if (it % 2 == 0) 25 else 2, 10 + it, "m$it") }
            val t = table(mons.map { ComparePick.Owned(it.uid) } , *mons.toTypedArray())
            assertEquals(n, t.columns.size)
            t.rows.forEach { assertEquals("${it.key} @ $n", n, it.cells.size) }
            val bests = t.row("level").cells.count { (it as Cell.Text).best }
            assertEquals(if (n == 1) 0 else 1, bests) // levels are all different: exactly one best, none for a single column
            val ranks = CompareModes.ranks(t.row("level").cells.map { (it as Cell.Text).value })
            if (n >= 2) assertEquals((0 until n).map { n - 1 - it }, ranks)
            // mixed with Pokédex entries
            val mixed = table(mons.map { ComparePick.Owned(it.uid) } + ComparePick.Dex(3), *mons.toTypedArray())
            assertEquals(n + 1, mixed.columns.size)
            mixed.rows.forEach { assertEquals(n + 1, it.cells.size) }
        }
        // 20 columns sort and diff without trouble
        val mons = (0 until 20).map { mon(25, 10 + it, "m$it") }
        val t = table(mons.map { ComparePick.Owned(it.uid) }, *mons.toTypedArray())
        assertEquals((19 downTo 0).toList(), CompareModes.sortBy(t, CompareModes.identity(20), "level", true))
        assertEquals(19, CompareModes.diffs(t, 0)["level"]!!.last())
    }
}
