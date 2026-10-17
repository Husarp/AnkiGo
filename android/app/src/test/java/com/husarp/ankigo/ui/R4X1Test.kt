package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.Matchup
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R4-X1: the pure rules of the 0.12 feedback round (chart pick toggle, Compare scale / zoom / drag / labels, downloads). */
class R4X1Test {
    private val world = TestData.world()
    private val data = GameData(world.species, world.moves, world.growth, world.itemPrices)
    private val sp = data.species.values.first()

    private fun table(vararg levels: Int?): CompareData {
        val cols = levels.indices.map { i -> CompareColumn(ComparePick.Dex(i + 1), null, sp, sp, "c$i", false, null) }
        return CompareData(cols, listOf(CompareRow("level", "Level", levels.map { Cell.Text(it?.toString() ?: DASH, value = it) }, numeric = true)))
    }

    // ---- item 1: a tap on the open pick closes it ----
    @Test fun tapOnTheOpenPickClosesIt() {
        assertEquals(-1, StatsChartMath.tapPick(3, 3, -1))
        assertEquals(4, StatsChartMath.tapPick(3, 4, -1))
        assertEquals(2, StatsChartMath.tapPick(-1, 2, -1))
        assertNull(StatsChartMath.tapPick<String?>("2026-01-02", "2026-01-02", null))
        assertEquals("2026-01-03", StatsChartMath.tapPick<String?>("2026-01-02", "2026-01-03", null))
        assertNull(StatsChartMath.tapPick<String?>(null, null, null)) // a tap beside the days with nothing open
    }

    // ---- item 10: red to green by value ----
    @Test fun scaleFollowsTheValueNotTheRank() {
        // ranks would be 0,1,2; the scale keeps 99 and 100 together
        assertEquals(listOf(0f, 0.5f, 1f), CompareModes.scale(listOf(10, 55, 100)))
        val s = CompareModes.scale(listOf(10, 99, 100))
        assertEquals(0f, s[0]!!, 1e-6f); assertEquals(1f, s[2]!!, 1e-6f)
        assertTrue(s[2]!! - s[1]!! < 0.02f) // near-equal values, near-equal colours
    }

    @Test fun scaleSkipsMissingValuesAndNothingToCompare() {
        assertEquals(listOf(0f, null, 1f), CompareModes.scale(listOf(3, null, 9)))
        assertEquals(listOf<Float?>(null, null), CompareModes.scale(listOf(5, 5)))      // all equal
        assertEquals(listOf<Float?>(null, null, null), CompareModes.scale(listOf(5, null, null))) // one value
        assertEquals(listOf<Float?>(), CompareModes.scale(emptyList()))
    }

    @Test fun scaleRowsOnlyForNumericRows() {
        val t = table(5, 9, 7)
        val m = CompareModes.scaleRows(t)
        assertEquals(listOf(0f, 1f, 0.5f), m["level"])
        assertEquals(setOf("level"), m.keys)
    }

    // ---- item 11: an equal value shows nothing ----
    @Test fun equalValueHasNoLabel() {
        val t = table(7, 7, 9)
        val d = CompareModes.diffs(t, 0)["level"]!!
        assertEquals(listOf(null, 0, 2), d)
        assertEquals("", CompareModes.diffLabel(d[1]))
        assertEquals("+2", CompareModes.diffLabel(d[2]))
    }

    // ---- item 12: zoom ----
    @Test fun zoomChoiceIsKeptOnlyWhenValid() {
        assertEquals(CompareFit.AUTO, CompareFit.valid(0))
        for (o in listOf(3, 4, 5, 6)) assertEquals(o, CompareFit.valid(o))
        assertEquals(CompareFit.AUTO, CompareFit.valid(2)); assertEquals(CompareFit.AUTO, CompareFit.valid(7)); assertEquals(CompareFit.AUTO, CompareFit.valid(-1))
        assertEquals("Auto", CompareFit.label(0)); assertEquals("4", CompareFit.label(4))
    }

    @Test fun columnWidthFollowsTheZoom() {
        // phone: 412 - 12 = 400 dp viewport, 56 dp label column
        val auto9 = CompareSizing.colDp(400, 56, 9, CompareFit.AUTO)
        assertEquals((400 - 56) / 5.4f, auto9, 0.01f) // as before: 5 fit, a sliver of the 6th
        assertEquals((400 - 56) / 2f, CompareSizing.colDp(400, 56, 2, CompareFit.AUTO), 0.01f) // few columns fill the width
        val w3 = CompareSizing.colDp(400, 56, 9, 3); val w4 = CompareSizing.colDp(400, 56, 9, 4)
        val w5 = CompareSizing.colDp(400, 56, 9, 5); val w6 = CompareSizing.colDp(400, 56, 9, 6)
        assertTrue(w3 > w4 && w4 > w5 && w5 > w6)
        assertEquals((400 - 56) / 3.4f, w3, 0.01f)
        // a chosen zoom is honoured even with fewer columns
        assertEquals((400 - 56) / 4f, CompareSizing.colDp(400, 56, 2, 4), 0.01f)
    }

    @Test fun zoomScalesEverythingWithinLimits() {
        assertEquals(1f, CompareSizing.zoom(80f, CompareFit.AUTO), 0f) // automatic keeps today's sizes
        assertTrue(CompareSizing.zoom(CompareSizing.colDp(400, 56, 9, 3), 3) > CompareSizing.zoom(CompareSizing.colDp(400, 56, 9, 6), 6))
        assertEquals(1.3f, CompareSizing.zoom(300f, 3), 0f)
        assertEquals(0.8f, CompareSizing.zoom(20f, 6), 0f)
        assertEquals(1f, CompareSizing.zoom(72f, 4), 0.001f)
    }

    // ---- item 8: labels never break mid-word ----
    @Test fun labelShrinksUntilItsLongestWordFits() {
        assertEquals(11f, CompareLabelFit.sp(11f, 8f, 30f, 40f), 0f)        // fits: unchanged
        assertEquals(11f * 40f / 50f, CompareLabelFit.sp(11f, 8f, 50f, 40f), 0.001f) // shrinks in proportion
        assertEquals(8f, CompareLabelFit.sp(11f, 8f, 100f, 40f), 0f)         // never below the floor
        assertEquals(11f, CompareLabelFit.sp(11f, 8f, 0f, 40f), 0f)
        assertEquals("Resists", CompareLabelFit.longestWord("Resists"))
        assertEquals("Weak", CompareLabelFit.longestWord("Weak to"))
        assertEquals("Total", CompareLabelFit.longestWord("Total\n↓"))
        assertEquals("", CompareLabelFit.longestWord(""))
    }

    // ---- item 7: drag ----
    @Test fun dragTargetIsTheNearestSlotInsideTheMovablePlaces() {
        // label 56 px, columns 100 px, 5 columns, reference (pos 0) pinned: movable from 1
        assertEquals(1, CompareDrag.target(56f + 100f, 56f, 100f, 5, 1))
        assertEquals(2, CompareDrag.target(56f + 240f, 56f, 100f, 5, 1))
        assertEquals(1, CompareDrag.target(56f - 300f, 56f, 100f, 5, 1)) // left of everything: first movable
        assertEquals(4, CompareDrag.target(56f + 900f, 56f, 100f, 5, 1)) // right of everything: last
        assertEquals(0, CompareDrag.target(56f, 56f, 100f, 5, 0))        // best of all: slot 0 is free
        assertEquals(0, CompareDrag.target(10f, 56f, 100f, 0, 0))
    }

    @Test fun otherColumnsStepAsideWhileDragging() {
        // column 1 dragged right to 3: columns 2 and 3 step left
        assertEquals(listOf(0, 0, -1, -1, 0), (0..4).map { CompareDrag.shift(it, 1, 3) })
        // column 3 dragged left to 1: columns 1 and 2 step right
        assertEquals(listOf(0, 1, 1, 0, 0), (0..4).map { CompareDrag.shift(it, 3, 1) })
        assertEquals(listOf(0, 0, 0), (0..2).map { CompareDrag.shift(it, 1, 1) })
    }

    @Test fun edgeAutoScrollOnlyNearTheEdges() {
        assertEquals(0f, CompareDrag.autoScroll(200f, 400f, 56f, 48f, 14f), 0f)
        assertTrue(CompareDrag.autoScroll(390f, 400f, 56f, 48f, 14f) > 0f)
        assertEquals(14f, CompareDrag.autoScroll(400f, 400f, 56f, 48f, 14f), 0.001f)
        assertTrue(CompareDrag.autoScroll(60f, 400f, 56f, 48f, 14f) < 0f)
    }

    @Test fun dropMovesTheColumnInTheState() {
        val t = table(5, 9, 7, 1)
        val s = CompareState(CompareMode.BEST_OF_ALL)
        s.moveTo(t, 0, 3)
        assertEquals(listOf(1, 2, 3, 0), s.display(t))
        s.moveTo(t, 0, 0)
        assertEquals(listOf(0, 1, 2, 3), s.display(t))
        s.moveTo(t, 2, 99) // out of range is pulled in
        assertEquals(listOf(0, 1, 3, 2), s.display(t))
    }

    @Test fun dropKeepsTheReferenceInVsModeAndSpendsTheSort() {
        val t = table(5, 9, 7, 1)
        val s = CompareState()
        s.tapRow("level")             // display 1 2 0 3: the default reference stays column 0 -> shown first
        val shown = s.display(t)
        assertEquals(0, shown[0])
        val last = shown.last()
        s.moveTo(t, last, 1)          // to the first movable place
        assertEquals(shown[0], s.display(t)[0])
        assertEquals(last, s.display(t)[1])
        assertNull(s.sortKey)
        s.moveTo(t, shown[0], 3)      // the reference itself cannot be dragged away
        assertEquals(shown[0], s.display(t)[0])
    }

    // ---- item 9: what a tap says ----
    @Test fun everyRowHasAnExplanation() {
        for (k in listOf("level", "hp", "base_atk", "total", "ivs", "nature", "types", "moves", "weak", "resist", "immune", "evo", "becomes")) {
            val i = CompareInfo.forRow(k, "x")
            assertNotNull(k, i); assertTrue(k, i!!.text.length > 10)
        }
        assertEquals("Resists", CompareInfo.forRow("resist", "Resists")!!.title)
        assertEquals("Odd", CompareInfo.forRow("unknown", "Odd")!!.title) // an unknown row still names itself
        assertNull(CompareInfo.forRow("unknown", ""))
    }

    @Test fun typeAndMatchupTexts() {
        assertEquals("Fire type", CompareInfo.typeText(Type.FIRE))
        assertEquals("Water ×2: takes double damage", CompareInfo.matchupText(Matchup(Type.WATER, 2.0)))
        assertEquals("Grass ×¼: takes a quarter of the damage", CompareInfo.matchupText(Matchup(Type.GRASS, 0.25)))
        assertEquals("Ground ×0: takes no damage", CompareInfo.matchupText(Matchup(Type.GROUND, 0.0)))
        assertEquals("Ice ×4: takes four times the damage", CompareInfo.matchupText(Matchup(Type.ICE, 4.0)))
    }

    // ---- item 2: no limit on ticks ----
    @Test fun anyNumberCanBeTicked() {
        val p = PickSet()
        for (i in 1..40) assertEquals(PickSet.Result.ADDED, p.toggle(ComparePick.Dex(i)))
        assertEquals(40, p.size)
        assertEquals(PickSet.Result.REMOVED, p.toggle(ComparePick.Dex(7)))
        assertFalse(p.has(ComparePick.Dex(7)))
    }

    // ---- item 5: downloads ----
    @Test fun downloadCompleteOnlyWhenEveryWantedGenerationIsDone() {
        assertTrue(DownloadsDone.complete(setOf(1, 2, 3), setOf(1, 2)))
        assertTrue(DownloadsDone.complete(setOf(1, 2), setOf(1, 2)))
        assertFalse(DownloadsDone.complete(setOf(1), setOf(1, 2)))   // a generation was switched on later
        assertFalse(DownloadsDone.complete(emptySet(), setOf(1)))
        assertFalse(DownloadsDone.complete(setOf(1), emptySet()))     // nothing wanted: no green state
    }

    @Test fun downloadFlagRoundTrips() {
        assertEquals("1,2,5", DownloadsDone.encode(setOf(5, 1, 2)))
        assertEquals(setOf(1, 2, 5), DownloadsDone.decode("1,2,5"))
        assertEquals(emptySet<Int>(), DownloadsDone.decode(null))
        assertEquals(setOf(3), DownloadsDone.decode("x, 3,,"))
    }
}
