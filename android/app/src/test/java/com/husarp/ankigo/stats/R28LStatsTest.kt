package com.husarp.ankigo.stats

import com.husarp.ankigo.game.DayLog
import com.husarp.ankigo.ui.StatsChartMath
import org.junit.Assert.*
import org.junit.Test

class R28LStatsTest {
    private val today = "2026-11-01"
    @Test fun answerSplitUsesTodayCalendarWeekAndCalendarMonth() {
        val today = "2026-11-07"
        val history = mapOf(today to DayLog(buttons = intArrayOf(40, 1, 2, 3, 4)),
            "2026-11-02" to DayLog(buttons = intArrayOf(80, 10, 20, 30, 40)),
            "2026-11-01" to DayLog(buttons = intArrayOf(0, 100, 200, 300, 400)),
            "2026-10-31" to DayLog(buttons = intArrayOf(0, 1000, 2000, 3000, 4000)),
            "2026-11-08" to DayLog(buttons = intArrayOf(0, 1000, 2000, 3000, 4000)))
        for ((p, total, first) in listOf(Triple(ChartPeriod.D1, 10.0, today),
            Triple(ChartPeriod.D7, 110.0, "2026-11-02"), Triple(ChartPeriod.D30, 1110.0, "2026-11-01"))) {
            val s = ChartData.build(ChartMetric.BUTTONS, p, "Old deck", StatsSnapshot(), history, today, ChartGrain.MONTH)
            assertEquals(total, s.total, 0.0)
            assertEquals(first, s.bars.first().startDay)
            assertEquals(today, s.bars.last().endDay)
            assertEquals(total / s.bars.size, s.perDayAvg, 0.0)
            assertFalse(s.deckApplies)
            assertEquals(listOf(10, 20, 30, 40), StatsChartMath.legend(s).map { it.percent })
        }
        assertTrue(ChartMetric.BUTTONS.usesPeriod)
        assertEquals(listOf("Today", "This week", "This month"), StatsChartMath.periods(ChartMetric.BUTTONS).map { StatsChartMath.periodLabel(ChartMetric.BUTTONS, it) })
        assertTrue(ChartData.grains(ChartMetric.BUTTONS, ChartPeriod.D365).isEmpty())
    }
    @Test fun yesterdayDoesNotFillEmptyToday() {
        val s = ChartData.build(ChartMetric.BUTTONS, ChartPeriod.D1, null, StatsSnapshot(),
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
