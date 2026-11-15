package com.husarp.ankigo.stats

import com.husarp.ankigo.game.DayLog
import com.husarp.ankigo.ui.StatsChartMath
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class R35KChartRangesTest {
    private val today = "2026-11-15"
    private val end = LocalDate.parse(today)
    private fun build(p: ChartPeriod, h: Map<String, DayLog>, g: ChartGrain) =
        ChartData.build(ChartMetric.BUTTONS, p, null, StatsSnapshot(), h, today, g)

    @Test fun rangedMenusAndSnapshotMenus() {
        for (metric in ChartMetric.values().filter { it.usesPeriod })
            assertEquals(metric.name, listOf(7, 30, 100), ChartPrefs.periodsFor(metric).take(3).map { it.days })
        for (m in listOf(ChartMetric.REVIEWS, ChartMetric.TIME, ChartMetric.NEW_CARDS, ChartMetric.BUTTONS)) {
            assertEquals(listOf(7, 30, 100, 365, 1000), ChartPrefs.periodsFor(m).map { it.days })
            for (p in ChartPrefs.periodsFor(m)) for (g in ChartData.grains(m, p))
                assertEquals(ChartPrefs.Choice(m, p, "Deck", g), ChartPrefs.decode(m.name, p.name, "Deck", g.name))
        }
        for (m in ChartMetric.values().filterNot { it.usesPeriod }) assertTrue(ChartPrefs.periodsFor(m).isEmpty())
    }

    @Test fun allFourHistoryChartsIncludeExactly1000Days() {
        val days = (0..999).associate { end.minusDays(it.toLong()).toString() to 12 }
        val h = days.mapValues { DayLog(reviews = it.value, buttons = intArrayOf(0, 1, 2, 7, 2)) }
        val a = AnkiStats("fake", 1, today, emptyMap(), emptyMap(), emptyMap(), Counts(), 0, emptyList(), 0, 0, 1)
        val snap = StatsSnapshot(stats = a, timeByDay = days.mapValues { 12 * 60000L }, series =
            listOf(ChartMetric.REVIEWS, ChartMetric.NEW_CARDS).associate { SeriesKey(null, it, 1000, today) to SeriesData(days, 1) })
        for (m in listOf(ChartMetric.REVIEWS, ChartMetric.TIME, ChartMetric.NEW_CARDS, ChartMetric.BUTTONS)) {
            val s = ChartData.build(m, ChartPeriod.D1000, null, snap, h, today, ChartGrain.DAY)
            assertEquals(1000, s.bars.size)
            assertEquals(end.minusDays(999).toString(), s.bars.first().startDay)
            assertEquals(today, s.bars.last().endDay)
            assertEquals(12000.0, s.total, 0.0)
            assertTrue(s.bars.all { it.hasData && it.values.sum() == 12.0 })
        }
    }

    @Test fun missingDaysStayBlankAndRecordedZeroIsStillData() {
        val h = mapOf(today to DayLog(), end.minusDays(2).toString() to DayLog(buttons = intArrayOf(0, 0, 0, 12, 0)))
        val daily = build(ChartPeriod.D100, h, ChartGrain.DAY)
        assertTrue(daily.bars.last().hasData)
        assertFalse(daily.bars[daily.bars.lastIndex - 1].hasData)
        assertTrue(daily.bars[daily.bars.lastIndex - 1].detail.single().endsWith("no data recorded"))
        val monthly = build(ChartPeriod.D100, h, ChartGrain.MONTH)
        assertEquals(listOf(0.0, 0.0, 6.0, 0.0), monthly.bars.last().values)
        assertTrue(monthly.bars.dropLast(1).all { !it.hasData })
        assertEquals(12.0, monthly.total, 0.0)
        assertEquals(listOf(0, 0, 100, 0), StatsChartMath.legend(monthly).map { it.percent })
    }

    @Test fun calendarKeepsUnknownDaysBlank() {
        val h = mapOf(today to DayLog(reviews = 10), end.minusDays(2).toString() to DayLog())
        val s = ChartData.build(ChartMetric.STREAK_CALENDAR, ChartPeriod.D100, null, StatsSnapshot(), h, today)
        assertEquals(100, s.bars.size)
        assertTrue(s.bars.last().hasData)
        assertFalse(s.bars[s.bars.lastIndex - 1].hasData)
        assertTrue(s.bars[s.bars.lastIndex - 2].hasData)
        assertEquals(10.0, s.total, 0.0)
    }

    @Test fun partialWeeksMonthsAndLeapYearsAverageRecordedDays() {
        val h = (0..999).associate { end.minusDays(it.toLong()).toString() to DayLog(buttons = intArrayOf(0, 2, 4, 12, 2)) }
        for (g in listOf(ChartGrain.WEEK, ChartGrain.MONTH, ChartGrain.YEAR)) {
            val s = build(ChartPeriod.D1000, h, g)
            assertTrue(s.bars.all { it.values == listOf(2.0, 4.0, 12.0, 2.0) })
            assertEquals(end.minusDays(999).toString(), s.bars.first().startDay)
            assertEquals(today, s.bars.last().endDay)
            assertEquals(20000.0, s.total, 0.0)
            assertEquals(20.0, s.perDayAvg, 0.0)
        }
        val leap = ChartData.build(ChartMetric.ANSWERS, ChartPeriod.D365, null, StatsSnapshot(),
            mapOf("2024-02-29" to DayLog(reviews = 8), "2024-02-28" to DayLog(reviews = 4)), "2024-03-01", ChartGrain.MONTH)
        assertEquals(6.0, leap.bars.first { it.startDay == "2024-02-01" }.values.single(), 0.0)
    }

    @Test fun everyLongAxisGrainUsesFullYears() {
        for (g in ChartPeriod.D1000.grains) {
            val s = build(ChartPeriod.D1000, emptyMap(), g)
            for (b in s.bars) {
                val label = StatsChartMath.axisText(s, b)
                assertTrue(label, label.contains(b.startDay.take(4)))
                assertFalse(label, label.contains("'") || label.contains("’"))
            }
        }
    }

    @Test fun legendUsesCountsRatherThanGivingPartialBucketsEqualWeight() {
        val h = (1..15).associate { "2026-11-%02d".format(it) to DayLog(buttons = intArrayOf(0, 0, 0, 1, 0)) } +
            ("2026-10-01" to DayLog(buttons = intArrayOf(0, 9, 0, 0, 0)))
        val s = build(ChartPeriod.D1000, h, ChartGrain.MONTH)
        assertEquals(listOf(38, 0, 63, 0), StatsChartMath.legend(s).map { it.percent })
        assertEquals(24.0, s.total, 0.0)
    }

    @Test fun providerReadersDoNotTruncate1000DayRequests() {
        val queries = mutableListOf<String>()
        val reviews = AnkiSeriesReader { queries += it; 3 }.read(SeriesKey("Deck", ChartMetric.REVIEWS, 1000, today), 1)
        assertEquals(1000, queries.size)
        assertEquals(1000, reviews.perDay.size)
        assertTrue(queries.last().endsWith("prop:rated=-999"))
        assertEquals(3, reviews.perDay[end.minusDays(999).toString()])
        queries.clear()
        val introduced = AnkiSeriesReader { q -> queries += q; q.substringAfterLast(':').toInt() * 2 }
            .read(SeriesKey(null, ChartMetric.NEW_CARDS, 1000, today), 1)
        assertEquals(1000, queries.size)
        assertEquals("introduced:1000", queries.last())
        assertTrue(introduced.perDay.values.all { it == 2 })
    }

    @Test fun completedLongHistoryDoesNotNeedTheSeparateQuickSnapshot() {
        val snap = StatsSnapshot(series = mapOf(SeriesKey(null, ChartMetric.REVIEWS, 1000, today) to SeriesData(mapOf(today to 12), 1)))
        val s = ChartData.build(ChartMetric.REVIEWS, ChartPeriod.D1000, null, snap, emptyMap(), today, ChartGrain.DAY)
        assertEquals("AnkiDroid", s.source)
        assertEquals(12.0, s.total, 0.0)
        assertTrue(s.bars.last().hasData)
        assertFalse(s.bars.first().hasData)
    }

    @Test fun longProviderReadsCoverTheSelectedWindow() {
        for (m in listOf(ChartMetric.REVIEWS, ChartMetric.NEW_CARDS))
            assertEquals(listOf(SeriesKey(null, m, 1000, today)), ChartData.seriesKeys(m, ChartPeriod.D1000, null, today))
        val key = SeriesKey(null, ChartMetric.DUE, 100, today)
        assertEquals(listOf(key), ChartData.seriesKeys(ChartMetric.DUE, ChartPeriod.D100, null, today))
        val queries = mutableListOf<String>()
        val due = AnkiSeriesReader { queries += it; 3 }.read(key, 1)
        assertEquals(100, queries.size)
        assertEquals("prop:due=100", queries.last())
        val snap = StatsSnapshot(series = mapOf(key to due))
        val s = ChartData.build(ChartMetric.DUE, ChartPeriod.D100, null, snap, emptyMap(), today, ChartGrain.DAY)
        assertEquals(100, s.bars.size)
        assertEquals(end.plusDays(100).toString(), s.bars.last().endDay)
        assertEquals(300.0, s.total, 0.0)
    }
}
