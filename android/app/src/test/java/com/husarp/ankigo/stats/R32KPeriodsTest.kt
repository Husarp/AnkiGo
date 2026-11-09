package com.husarp.ankigo.stats

import com.husarp.ankigo.game.DayLog
import com.husarp.ankigo.ui.StatsChartMath
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class R32KPeriodsTest {
    @Test fun rollingRangesIncludeExactlyTheRequestedStudyDays() {
        for (today in listOf("2026-01-01", "2026-03-01", "2028-03-01", "2026-11-04")) {
            val end = LocalDate.parse(today)
            for (period in ChartPrefs.periodsFor(ChartMetric.BUTTONS)) {
                val first = end.minusDays(period.days - 1L)
                val history = listOf(first.minusDays(1), first, end, end.plusDays(1)).associate {
                    it.toString() to DayLog(buttons = intArrayOf(0, 1, 0, 0, 0))
                }
                for (metric in listOf(ChartMetric.BUTTONS, ChartMetric.REVIEWS, ChartMetric.TIME, ChartMetric.NEW_CARDS, ChartMetric.STREAK_CALENDAR)) {
                    val series = ChartData.build(metric, period, null, StatsSnapshot(), history, today)
                    assertEquals("$metric/$period/$today", first.toString(), series.bars.first().startDay)
                    assertEquals(today, series.bars.last().endDay)
                    if (metric == ChartMetric.BUTTONS) {
                        assertEquals(period.days, series.bars.size)
                        assertEquals(2.0, series.total, 0.0)
                    }
                }
            }
        }
    }

    @Test fun studyDayBoundaryControlsTheLastIncludedDay() {
        for ((hour, expected) in listOf(3 to "2026-12-31", 4 to "2027-01-01")) {
            val instant = java.time.Instant.parse("2027-01-01T0${hour}:00:00Z").toEpochMilli()
            val today = AnkiGoTime.dayKey(instant, 4, java.time.ZoneOffset.UTC)
            assertEquals(expected, today)
            val series = ChartData.build(ChartMetric.BUTTONS, ChartPeriod.D7, null, StatsSnapshot(), emptyMap(), today)
            assertEquals(expected, series.bars.last().endDay)
            assertEquals(LocalDate.parse(expected).minusDays(6).toString(), series.bars.first().startDay)
        }
    }

    @Test fun allHistoricalMenusOfferTheSameRollingWindows() {
        for (metric in ChartMetric.menu.flatMap { it.second }.filter { it.usesPeriod && it != ChartMetric.DUE }) {
            assertEquals(listOf(7, 30, 100, 300), StatsChartMath.periods(metric).map { it.days })
            assertEquals(listOf("Last 7 days", "Last 30 days", "Last 100 days", "Last 300 days"),
                StatsChartMath.periods(metric).map { StatsChartMath.periodLabel(metric, it) })
        }
        val due = ChartData.build(ChartMetric.DUE, ChartPeriod.D7, null, StatsSnapshot(), emptyMap(), "2026-01-01")
        assertEquals("2026-01-02", due.bars.first().startDay)
        assertEquals("2026-01-08", due.bars.last().endDay)
    }
}
