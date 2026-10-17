package com.husarp.ankigo.ui

import com.husarp.ankigo.game.DayLog
import com.husarp.ankigo.stats.AnkiAccess
import com.husarp.ankigo.stats.ChartData
import com.husarp.ankigo.stats.ChartMetric
import com.husarp.ankigo.stats.ChartPeriod
import com.husarp.ankigo.stats.StatsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// TICKET R3-V1: hit testing, axis scale, label placement, info box position and the texts of the Stats chart.
class StatsChartMathTest {
    private val today = "2026-10-14"
    private val t = LocalDate.parse(today)
    private fun date(k: Int) = t.minusDays(k.toLong()).toString()
    private fun log(reviews: Int = 0, buttons: List<Int> = listOf(0, 0, 0, 0, 0)) = DayLog(reviews = reviews, buttons = buttons.toIntArray())
    private fun build(m: ChartMetric, p: ChartPeriod, history: Map<String, DayLog>, snap: StatsSnapshot = StatsSnapshot()) =
        ChartData.build(m, p, null, snap, history, today)

    // ---- hit testing ----

    @Test fun xMapsToTheBarUnderIt() {
        // 4 bars in 100..300: slots 100-150, 150-200, 200-250, 250-300
        assertEquals(0, StatsChartMath.indexAt(100f, 4, 100f, 300f))
        assertEquals(0, StatsChartMath.indexAt(149.9f, 4, 100f, 300f))
        assertEquals(1, StatsChartMath.indexAt(150f, 4, 100f, 300f))
        assertEquals(3, StatsChartMath.indexAt(300f, 4, 100f, 300f))
    }

    @Test fun outsideThePlotIsNoBarUnlessClamped() {
        assertEquals(-1, StatsChartMath.indexAt(99f, 4, 100f, 300f))
        assertEquals(-1, StatsChartMath.indexAt(301f, 4, 100f, 300f))
        assertEquals(0, StatsChartMath.indexAt(-50f, 4, 100f, 300f, clamp = true))
        assertEquals(3, StatsChartMath.indexAt(999f, 4, 100f, 300f, clamp = true))
        assertEquals(-1, StatsChartMath.indexAt(150f, 0, 100f, 300f))
        assertEquals(-1, StatsChartMath.indexAt(150f, 3, 100f, 100f))
    }

    @Test fun slotCentreIsInTheMiddleOfItsSlot() {
        assertEquals(125f, StatsChartMath.slotCenter(0, 4, 100f, 300f), 0.001f)
        assertEquals(275f, StatsChartMath.slotCenter(3, 4, 100f, 300f), 0.001f)
        for (i in 0 until 7) assertEquals(i, StatsChartMath.indexAt(StatsChartMath.slotCenter(i, 7, 30f, 380f), 7, 30f, 380f))
    }

    @Test fun heatmapCellUnderAFinger() {
        // grid at (20, 16), cells 10 wide, gap 2: columns at 20, 32, 44 ...; rows at 16, 28 ...
        assertEquals(0 to 0, StatsChartMath.gridCell(25f, 20f, 20f, 16f, 10f, 2f, 5))
        assertEquals(2 to 3, StatsChartMath.gridCell(50f, 55f, 20f, 16f, 10f, 2f, 5))
        assertNull(StatsChartMath.gridCell(10f, 20f, 20f, 16f, 10f, 2f, 5))   // the M / W / F labels
        assertNull(StatsChartMath.gridCell(25f, 5f, 20f, 16f, 10f, 2f, 5))    // the month names
        assertNull(StatsChartMath.gridCell(25f, 16f + 7 * 12 + 1, 20f, 16f, 10f, 2f, 5)) // under the 7th row
        assertNull(StatsChartMath.gridCell(20f + 5 * 12 + 1, 20f, 20f, 16f, 10f, 2f, 5)) // right of the last column
    }

    // ---- axis ----

    @Test fun axisUsesRoundNumbers() {
        val a = StatsChartMath.axis(ChartMetric.REVIEWS, 92.0)
        assertEquals(100.0, a.max, 0.0); assertEquals(listOf("0", "50", "100"), a.labels)
        val b = StatsChartMath.axis(ChartMetric.REVIEWS, 1.0)
        assertEquals(2.0, b.max, 0.0); assertEquals(listOf("0", "1", "2"), b.labels)
        val c = StatsChartMath.axis(ChartMetric.REVIEWS, 1240.0)
        assertEquals(2000.0, c.max, 0.0); assertEquals(listOf("0", "1k", "2k"), c.labels)
        assertEquals(listOf("0", "10", "20"), StatsChartMath.axis(ChartMetric.XP, 11.0).labels)
        assertEquals(2.0, StatsChartMath.axis(ChartMetric.MONEY, 0.0).max, 0.0)
    }

    @Test fun retentionAxisIsAlwaysAPercent() {
        val a = StatsChartMath.axis(ChartMetric.RETENTION, 37.0)
        assertEquals(100.0, a.max, 0.0); assertEquals(listOf(0.0, 50.0, 100.0), a.ticks)
    }

    @Test fun timeAxisSwitchesToHours() {
        assertEquals(listOf("0", "20m", "40m"), StatsChartMath.axis(ChartMetric.TIME, 27.0).labels)
        assertEquals(listOf("0", "30m", "1h"), StatsChartMath.axis(ChartMetric.TIME, 55.0).labels)
        val h = StatsChartMath.axis(ChartMetric.TIME, 75.0)           // 1 h 15 min -> 2 h
        assertEquals(120.0, h.max, 0.0); assertEquals(listOf("0", "1h", "2h"), h.labels)
        assertEquals(60.0, StatsChartMath.axis(ChartMetric.TIME, 0.0).max, 0.0)
    }

    @Test fun axisHoldsTheTallestBarAndHalfIsRound() {
        for (v in listOf(0.3, 1.0, 2.0, 7.0, 23.0, 99.0, 100.0, 101.0, 4999.0, 123456.0)) {
            val max = StatsChartMath.niceMax(v)
            assertTrue("$v fits under $max", max >= v)
            val half = max / 2
            assertEquals("half of $max", Math.rint(half * 100) / 100, half, 1e-9)
        }
    }

    @Test fun compactNumbers() {
        assertEquals("850", StatsChartMath.compact(850.0)); assertEquals("1.2k", StatsChartMath.compact(1200.0))
        assertEquals("12k", StatsChartMath.compact(12_400.0)); assertEquals("3M", StatsChartMath.compact(3_000_000.0))
        assertEquals("0.5", StatsChartMath.compact(0.5))
    }

    // ---- axis labels ----

    private val w = { _: Int -> 40f }

    @Test fun sevenLabelsAllFitOnAWidePlot() {
        val p = StatsChartMath.labelPlan(7, 30f, 380f, 6f, w)
        assertEquals((0..6).toList(), p.map { it.index })
        assertEquals(LabelAlign.START, p.first().align); assertEquals(LabelAlign.END, p.last().align)
    }

    @Test fun thirtyBarsShowTheEndsAndSomeBetweenWithoutTouching() {
        val p = StatsChartMath.labelPlan(30, 30f, 380f, 6f, w)
        assertEquals(0, p.first().index); assertEquals(29, p.last().index)
        assertTrue(p.size in 3..8)
        var right = -1e9f
        for (l in p) {
            val a = StatsChartMath.labelLeft(l, 30, 30f, 380f, 40f)
            assertTrue("no overlap at ${l.index}", a >= right + 6f - 0.001f)
            right = a + 40f
        }
        assertTrue("last label stays inside the plot", right <= 380f + 0.001f)
    }

    @Test fun veryNarrowPlotKeepsAtLeastTheFirstLabel() {
        val p = StatsChartMath.labelPlan(30, 30f, 60f, 6f, w)
        assertEquals(listOf(0), p.map { it.index })
        assertEquals(emptyList<AxisLabel>(), StatsChartMath.labelPlan(0, 0f, 100f, 6f, w))
        assertEquals(listOf(AxisLabel(0, LabelAlign.CENTER)), StatsChartMath.labelPlan(1, 0f, 100f, 6f, w))
    }

    @Test fun axisTextPerGrain() {
        val h = (0 until 90).associate { date(it) to log(reviews = 1) }
        val week = build(ChartMetric.ANSWERS, ChartPeriod.D90, h)
        assertEquals("Oct 12", StatsChartMath.axisText(week, week.bars.last()))
        val day = build(ChartMetric.ANSWERS, ChartPeriod.D30, h)
        assertEquals("15 Sep", StatsChartMath.axisText(day, day.bars.first()))
        val seven = build(ChartMetric.ANSWERS, ChartPeriod.D7, h)
        assertEquals("Wed", StatsChartMath.axisText(seven, seven.bars.last()))
    }

    // ---- info box ----

    @Test fun infoBoxSitsBesideTheBar() {
        assertEquals(110f, StatsChartMath.tooltipLeft(100f, 10f, 120f, 400f, 8f), 0.001f)   // right of the bar
        assertEquals(220f, StatsChartMath.tooltipLeft(350f, 10f, 120f, 400f, 8f), 0.001f)   // no room right: left of the bar
        assertEquals(40f, StatsChartMath.tooltipLeft(200f, 60f, 320f, 400f, 8f), 0.001f)    // neither side: centred
    }

    @Test fun infoBoxNeverLeavesTheChart() {
        for (anchor in listOf(0f, 20f, 100f, 200f, 300f, 390f)) for (boxW in listOf(80f, 160f, 300f)) {
            val x = StatsChartMath.tooltipLeft(anchor, 8f, boxW, 400f, 8f)
            assertTrue("$anchor/$boxW: $x", x >= 8f && x + boxW <= 400f - 8f + 0.001f || boxW > 384f)
        }
    }

    @Test fun infoBoxOfARowGoesBelowOrAbove() {
        assertEquals(60f, StatsChartMath.tooltipTop(20f, 50f, 40f, 300f, 6f, 10f), 0.001f)      // below
        assertEquals(140f, StatsChartMath.tooltipTop(190f, 220f, 40f, 240f, 6f, 10f), 0.001f)   // above
        assertEquals(6f, StatsChartMath.tooltipTop(10f, 30f, 200f, 100f, 6f, 10f), 0.001f)      // fits neither: top
    }

    // ---- texts ----

    @Test fun tilesOfAReviewChart() {
        val h = (0 until 30).associate { date(it) to log(reviews = 10) } + (date(3) to log(reviews = 90))
        val tl = StatsChartMath.tiles(build(ChartMetric.ANSWERS, ChartPeriod.D30, h))
        assertEquals(StatTile("380", "answers"), tl[0])
        assertEquals(StatTile("12.7", "a day on average"), tl[1])
        assertEquals(StatTile("90", "best day (11 Oct)"), tl[2])
    }

    @Test fun tilesOfATimeChartAreDurations() {
        val snap = StatsSnapshot(timeByDay = mapOf(date(0) to 600_000L, date(1) to 3_000_000L), liveTimeByDay = mapOf(date(0) to 120_000L))
        val tl = StatsChartMath.tiles(build(ChartMetric.TIME, ChartPeriod.D7, emptyMap(), snap))
        assertEquals("1h 02m", tl[0].value); assertEquals("studied in total", tl[0].label)
        assertEquals("50 min", tl[2].value); assertEquals("best day (13 Oct)", tl[2].label)
    }

    @Test fun tilesOfRetentionArePercentAndNoBestDayWithoutData() {
        val h = mapOf(date(0) to log(buttons = listOf(0, 1, 1, 6, 2)))
        val tl = StatsChartMath.tiles(build(ChartMetric.RETENTION, ChartPeriod.D7, h))
        assertEquals("90 %", tl[0].value); assertEquals("kept (not Again)", tl[0].label)
        val none = StatsChartMath.tiles(build(ChartMetric.REVIEWS, ChartPeriod.D7, emptyMap()))
        assertEquals(StatTile("–", "best day"), none[2])
    }

    @Test fun legendSharesAddUpAndStayNullWithoutData() {
        val h = mapOf(date(0) to log(buttons = listOf(0, 3, 1, 12, 4)), date(1) to log(buttons = listOf(0, 0, 1, 12, 4)))
        val legend = StatsChartMath.legend(build(ChartMetric.BUTTONS, ChartPeriod.D7, h))
        assertEquals(listOf("Again", "Hard", "Good", "Easy"), legend.map { it.name })
        assertEquals(100, legend.sumOf { it.percent ?: 0 })
        assertTrue(StatsChartMath.legend(build(ChartMetric.BUTTONS, ChartPeriod.D7, emptyMap())).all { it.percent == null })
        assertTrue(StatsChartMath.legend(build(ChartMetric.REVIEWS, ChartPeriod.D7, h)).isEmpty())
    }

    @Test fun emptyAndLoadingMessages() {
        val none = build(ChartMetric.CATCHES, ChartPeriod.D30, emptyMap())
        assertEquals("No catches in this period yet.", StatsChartMath.emptyMessage(none, null, null))
        val withData = build(ChartMetric.CATCHES, ChartPeriod.D30, mapOf(date(0) to DayLog(caught = 2)))
        assertNull(StatsChartMath.emptyMessage(withData, null, null))
        val loading = ChartData.build(ChartMetric.REVIEWS, ChartPeriod.D30, "Spanish", StatsSnapshot(), emptyMap(), today)
        assertEquals("Reading AnkiDroid…", StatsChartMath.emptyMessage(loading, null, null))
        val denied = StatsSnapshot(access = AnkiAccess.NO_PERMISSION)
        val failed = ChartData.build(ChartMetric.REVIEWS, ChartPeriod.D30, "Spanish", denied, emptyMap(), today)
        assertEquals("Allow AnkiGo to read AnkiDroid to see its stats", StatsChartMath.emptyMessage(failed, denied.access, null))
        assertNotNull(StatsChartMath.emptyMessage(build(ChartMetric.RETENTION, ChartPeriod.D30, emptyMap()), null, null))
    }

    @Test fun menusAndTitles() {
        assertEquals(listOf(ChartPeriod.D7, ChartPeriod.D30), StatsChartMath.periods(ChartMetric.DUE))
        assertEquals(4, StatsChartMath.periods(ChartMetric.REVIEWS).size)
        assertEquals("Last 30 days", StatsChartMath.periodLabel(ChartMetric.REVIEWS, ChartPeriod.D30))
        assertEquals("Next 7 days", StatsChartMath.periodLabel(ChartMetric.DUE, ChartPeriod.D7))
        assertEquals("Last year", StatsChartMath.periodLabel(ChartMetric.REVIEWS, ChartPeriod.D365))
        val s = build(ChartMetric.BUTTONS, ChartPeriod.D30, emptyMap())
        assertEquals("Buttons pressed per day", StatsChartMath.title(s)); assertEquals("tap a bar for its day", StatsChartMath.hint(s))
        assertEquals("Card states", StatsChartMath.title(ChartData.build(ChartMetric.CARD_STATES, ChartPeriod.D30, null, StatsSnapshot(), emptyMap(), today)))
    }
}
