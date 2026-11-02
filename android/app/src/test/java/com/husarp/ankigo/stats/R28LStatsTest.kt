package com.husarp.ankigo.stats

import com.husarp.ankigo.game.DayLog
import com.husarp.ankigo.ui.StatsChartMath
import org.junit.Assert.*
import org.junit.Test

class R28LStatsTest {
    private val today = "2026-11-01"
    @Test fun answerSplitIsOnlyTodayEvenWithOldPeriodAndGrain() {
        val history = mapOf(today to DayLog(buttons = intArrayOf(40, 1, 2, 3, 4)),
            "2026-10-31" to DayLog(buttons = intArrayOf(0, 100, 200, 300, 400)))
        for (p in ChartPeriod.entries) {
            val s = ChartData.build(ChartMetric.BUTTONS, p, "Old deck", StatsSnapshot(), history, today, ChartGrain.MONTH)
            assertEquals(10.0, s.total, 0.0)
            assertEquals(listOf(1.0, 2.0, 3.0, 4.0), s.bars.single().values)
            assertEquals(today, s.bars.single().startDay)
            assertEquals(10.0, s.perDayAvg, 0.0)
            assertFalse(s.deckApplies)
            assertEquals(listOf(10, 20, 30, 40), StatsChartMath.legend(s).map { it.percent })
        }
        assertFalse(ChartMetric.BUTTONS.usesPeriod)
        assertTrue(ChartData.grains(ChartMetric.BUTTONS, ChartPeriod.D365).isEmpty())
    }
    @Test fun yesterdayDoesNotFillEmptyToday() {
        val s = ChartData.build(ChartMetric.BUTTONS, ChartPeriod.D30, null, StatsSnapshot(),
            mapOf("2026-10-31" to DayLog(buttons = intArrayOf(0, 2, 3, 4, 5))), today)
        assertTrue(s.empty)
        assertEquals(0.0, s.total, 0.0)
    }
    @Test fun timeHasOneCombinedSeriesAndCoverageNote() {
        val s = ChartData.build(ChartMetric.TIME, ChartPeriod.D7, null,
            StatsSnapshot(timeByDay = mapOf(today to 60000L), liveTimeByDay = mapOf(today to 120000L)), emptyMap(), today)
        assertEquals(3.0, s.total, 0.0)
        assertEquals(listOf(3.0), s.bars.last().values)
        assertEquals(1, s.seriesNames.size)
        assertEquals(StatsText.TIMED_SOURCE, s.source)
    }
}
