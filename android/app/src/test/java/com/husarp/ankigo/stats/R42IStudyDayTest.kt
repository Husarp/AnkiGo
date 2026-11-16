package com.husarp.ankigo.stats

import com.husarp.ankigo.game.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone
import kotlin.random.Random

class R42IStudyDayTest {
    private val zone = ZoneId.of("UTC")
    private fun at(time: String) = Instant.parse(time).toEpochMilli()

    @Test fun threeAmBoundaryIsSharedByGameTimeAndChartsIncludingUndoAndLearningRepeats() {
        val old = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val data = TestData.world()
            val state = TestData.state(data)
            state.settings.dayStartHour = 3
            var now = at("2026-11-16T02:59:59Z")
            val game = Game(data, state, Random(1)) { now }
            state.battle = Battle(TestData.mon(data, 1, 10, listOf("splash"), "wild"), Tier.NORMAL, awaitingContinue = true)
            game.onAnswer(Answer(1, 1, now))
            game.onAnswer(Answer(1, 3, now)) // the same learning card can count twice in AnkiGo
            game.onUndo(1, 1)
            game.onAnswer(Answer(1, 3, now)) // redo gives no second reward
            assertEquals(2, state.reviewsToday)
            assertEquals("2026-11-15", game.dayKey())
            assertEquals(game.dayKey(), AnkiGoTime.dayKey(now, 3, zone))
            now = at("2026-11-16T03:00:00Z")
            game.onAnswer(Answer(2, 3, now))
            assertEquals("2026-11-16", game.dayKey())
            assertEquals(1, state.reviewsToday)
            for (metric in listOf(ChartMetric.REVIEWS, ChartMetric.BUTTONS, ChartMetric.STREAK_CALENDAR)) {
                val chart = ChartData.build(metric, ChartPeriod.D7, null, StatsSnapshot(), state.history, game.dayKey())
                assertEquals(metric.name, 2.0, chart.bars.first { it.startDay == "2026-11-15" }.values.sum(), 0.0)
                assertEquals(metric.name, 1.0, chart.bars.last().values.sum(), 0.0)
            }
            val time = timeInAnkiGo(sequenceOf("${now - 1},1,0,3,1000,study", "$now,2,0,3,2000,study")) { AnkiGoTime.dayKey(it, 3, zone) }
            assertEquals(mapOf("2026-11-15" to 1000L, "2026-11-16" to 2000L), time)
        } finally { TimeZone.setDefault(old) }
    }

    @Test fun localThreeAmResetStaysAtThreeAcrossDaylightSavingChanges() {
        val berlin = ZoneId.of("Europe/Berlin")
        for ((before, reset, day) in listOf(
            Triple("2026-03-29T00:59:59Z", "2026-03-29T01:00:00Z", "2026-03-29"),
            Triple("2026-10-25T01:59:59Z", "2026-10-25T02:00:00Z", "2026-10-25"))) {
            assertNotEquals(day, StudyDay.key(at(before), 3, berlin))
            assertEquals(day, StudyDay.key(at(reset), 3, berlin))
            assertEquals(at(reset), StudyDay.start(day, 3, berlin))
        }
        assertEquals(23 * 3600000L, StudyDay.start("2026-03-29", 3, berlin) - StudyDay.start("2026-03-28", 3, berlin))
        assertEquals(25 * 3600000L, StudyDay.start("2026-10-25", 3, berlin) - StudyDay.start("2026-10-24", 3, berlin))
    }

    @Test fun timestampQueriesUseHalfOpenThreeAmBoundsAndDistinctCards() {
        val q = StudyDayQueries.cards("2026-11-16", 3, zone, listOf(12, 34))
        assertTrue(q, "COUNT(DISTINCT r.cid)" in q)
        assertTrue(q, "r.id >= ${at("2026-11-16T03:00:00Z")}" in q)
        assertTrue(q, "r.id < ${at("2026-11-17T03:00:00Z")}" in q)
        assertTrue(q, "c.odid IN (12,34)" in q)
        assertTrue(q, "r.ease BETWEEN 1 AND 4" in q)
        val fresh = StudyDayQueries.cards("2026-11-16", 3, zone, introduced = true)
        assertTrue(fresh, "old.id < r.id" in fresh)
        assertTrue(fresh, "r.type = 0" in fresh)
    }

    @Test fun exactDailyReaderNeverUsesAnkidroidsRelativeDaySearches() {
        val requested = mutableListOf<String>()
        val reader = AnkiStatsReader(studiedOn = { day -> requested += day; if (day == "2026-11-16") 170 else 0 }) { q ->
            assertFalse(q, q.startsWith("prop:rated=")); 0
        }
        val stats = reader.quick(null, "test", "2026-11-16", 100)
        assertEquals(170, stats.studied("2026-11-16"))
        assertEquals(7, requested.size)
        val history = mapOf("2026-11-16" to DayLog(reviews = 300))
        val chart = ChartData.build(ChartMetric.REVIEWS, ChartPeriod.D7, null, StatsSnapshot(stats = stats), history, "2026-11-16")
        assertEquals(170.0, chart.bars.last().values.single(), 0.0)
        assertEquals(300, StudyStats.dayTotals(history.values.map { it.reviews }).bestDay)
        reader.history(stats, 100)
        assertEquals(365, requested.size)
    }

    @Test fun newCardAndDeckSeriesUseExactDayReaderAndResetScopedCache() {
        for (metric in listOf(ChartMetric.NEW_CARDS, ChartMetric.REVIEWS)) {
            val key = SeriesKey("Parent", metric, 7, "2026-11-16", 3)
            val days = mutableListOf<String>()
            val result = AnkiSeriesReader(cardsOn = { k, day, introduced ->
                assertEquals(key, k); assertEquals(metric == ChartMetric.NEW_CARDS, introduced)
                days += day; 9
            }) { error("Relative-day lookup is forbidden") }.read(key, 20)
            assertEquals(7, days.size)
            assertEquals(9, result.perDay["2026-11-16"])
            val cache = AnkiSeriesCache.put(mapOf(key.copy(dayStartHour = 4) to result), key, result)
            assertEquals(setOf(key), cache.keys)
        }
        assertEquals(3, ChartData.seriesKeys(ChartMetric.NEW_CARDS, ChartPeriod.D7, null, "2026-11-16", 3).single().dayStartHour)
    }

    @Test fun unavailableExactCardCountsFallBackWithoutHidingOtherAnkidroidNumbers() {
        val stats = AnkiStatsReader { 0 }.quick(null, "test", "2026-11-16", 100)
            .copy(dailyCountsAvailable = false, perDay = emptyMap(), dueNow = 42)
        val snap = StatsSnapshot(stats = stats)
        for (metric in listOf(ChartMetric.REVIEWS, ChartMetric.STREAK_CALENDAR)) {
            val chart = ChartData.build(metric, ChartPeriod.D7, null, snap, mapOf("2026-11-16" to DayLog(reviews = 300)), "2026-11-16")
            assertEquals(300.0, chart.bars.last().values.single(), 0.0)
            assertTrue(chart.source.startsWith("AnkiGo"))
        }
        assertEquals(42, snap.stats!!.dueNow)
        val key = SeriesKey("Deck", ChartMetric.REVIEWS, 7, "2026-11-16", 3)
        val filtered = ChartData.build(ChartMetric.REVIEWS, ChartPeriod.D7, "Deck", snap.copy(dayStartHour = 3,
            series = mapOf(key to SeriesData(mapOf("2026-11-16" to 17), 100))), emptyMap(), "2026-11-16")
        assertEquals(17.0, filtered.bars.last().values.single(), 0.0)
        assertEquals("cards", filtered.unit)
    }

    @Test fun cacheKeepsItsResetHourAndTimezone() {
        val s = AnkiStatsReader { 0 }.quick(null, "test", "2026-11-16", 100).copy(dayStartHour = 3, zoneId = "Europe/Berlin")
        assertEquals(s, AnkiStatsCodec.decode(AnkiStatsCodec.encode(s)))
        assertNull(AnkiStatsCodec.decode(AnkiStatsCodec.encode(s).replace("\"v\":2", "\"v\":1")))
    }
}
