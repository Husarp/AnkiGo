package com.husarp.ankigo.stats

import com.husarp.ankigo.game.*
import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.*
import com.husarp.ankigo.ui.HeatmapLayout
import org.junit.Test

class R22PStatsTest {
    @Test fun goalIsCapturedOnFirstReviewAndSurvivesSettingsAndSave() {
        val state = GameState(day = "2026-10-30")
        state.settings.dailyAverage = 50
        StudyStats.record(state, listOf(GameEvent.Counted(3, 10, 1, 1, false)))
        state.settings.dailyAverage = 100
        StudyStats.record(state, listOf(GameEvent.Counted(3, 10, 1, 1, false)))
        val loaded = SaveCodec.decode(SaveCodec.encode(state))
        assertEquals(50, loaded.history.getValue(state.day).dailyGoal)
        val series = ChartData.build(ChartMetric.REVIEWS, ChartPeriod.D7, null, StatsSnapshot(), loaded.history, state.day, goal = 100)
        assertEquals(50, series.bars.last().dailyGoal)
        assertEquals(100, series.bars.first().dailyGoal)
        assertEquals(100, DailyGoal.forDay(state, state.day)) // game rules stay as before
    }
    @Test fun wholeLeapYearIncludesJanuaryAndDecemberAndNoOtherYear() {
        val history = mapOf("2024-01-01" to DayLog(reviews = 12), "2024-12-31" to DayLog(reviews = 7))
        val s = ChartData.build(ChartMetric.STREAK_CALENDAR, ChartPeriod.D365, null, StatsSnapshot(), history, "2026-10-30", calendarYear = 2024)
        assertEquals(366, s.bars.size)
        assertEquals("2024-01-01", s.bars.first().startDay)
        assertEquals("2024-12-31", s.bars.last().startDay)
        assertEquals(19.0, s.total, 0.0)
        val cells = s.heatmap!!.columns.flatten().filterNotNull()
        assertEquals(366, cells.size)
        assertTrue(cells.all { it.day.startsWith("2024-") })
        val layout = HeatmapLayout.layout(HeatmapLayout.Mode.WEEKS, cells.map { HeatmapLayout.Day(it.day, it.level) }, "2026-10-30", 260f)
        assertEquals(12, layout.months.size)
        assertEquals("Jan", layout.months.first().text)
        assertEquals("Dec", layout.months.last().text)
    }
    @Test fun weekAndMonthReviewBarsDoNotCompareWithASingleDayGoal() {
        val s = ChartData.build(ChartMetric.REVIEWS, ChartPeriod.D365, null, StatsSnapshot(), emptyMap(), "2026-10-30", grain = ChartGrain.MONTH)
        assertTrue(s.bars.all { it.dailyGoal == null })
    }
}
