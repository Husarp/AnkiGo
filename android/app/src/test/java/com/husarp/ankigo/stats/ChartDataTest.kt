package com.husarp.ankigo.stats

import com.husarp.ankigo.game.DayLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// TICKET R3-04: the chart model for every metric and period with made-up data.
class ChartDataTest {
    private val today = "2026-10-14" // a Wednesday
    private val t = LocalDate.parse(today)
    private fun date(k: Int) = t.minusDays(k.toLong()).toString()

    private fun log(reviews: Int = 0, won: Int = 0, caught: Int = 0, xp: Int = 0, cash: Int = 0, buttons: List<Int> = listOf(0, 0, 0, 0, 0)) =
        DayLog(reviews = reviews, won = won, caught = caught, xp = xp, cash = cash, buttons = buttons.toIntArray())

    private fun anki(perDay: Map<String, Int> = emptyMap(), forecast: List<Int> = List(30) { 0 }, answered: Int = 0, again: Int = 0, checked: Long = 1L) =
        AnkiStats("pkg", 0, today, perDay, emptyMap(), emptyMap(), Counts(), 0, forecast, answered, again, checked)

    private fun build(m: ChartMetric, p: ChartPeriod, snap: StatsSnapshot = StatsSnapshot(), history: Map<String, DayLog> = emptyMap(), deck: String? = null) =
        ChartData.build(m, p, deck, snap, history, today)

    // ---- buckets ----

    @Test fun sevenDaysAreOneBarPerDayWithWeekdayLabels() {
        val s = build(ChartMetric.ANSWERS, ChartPeriod.D7, history = mapOf(date(0) to log(reviews = 4)))
        assertEquals(7, s.bars.size)
        assertEquals(listOf("Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed"), s.bars.map { it.label })
        assertEquals("2026-10-08", s.bars.first().startDay); assertEquals("2026-10-08", s.bars.first().endDay)
        assertEquals(today, s.bars.last().endDay)
        assertEquals(listOf(4.0), s.bars.last().values)
    }

    @Test fun thirtyDaysAreOneBarPerDayLabelledWithTheDayOfTheMonth() {
        val s = build(ChartMetric.ANSWERS, ChartPeriod.D30)
        assertEquals(30, s.bars.size)
        assertEquals("2026-09-15", s.bars.first().startDay)
        assertEquals("15", s.bars.first().label); assertEquals("14", s.bars.last().label)
    }

    @Test fun ninetyDaysAreCalendarWeeksWithPartialEnds() {
        val h = (0 until 90).associate { date(it) to log(reviews = 1) }
        val s = build(ChartMetric.ANSWERS, ChartPeriod.D90, history = h)
        assertEquals(14, s.bars.size)
        val first = s.bars.first(); val last = s.bars.last()
        assertEquals("Jul 13", first.label)                  // the Monday of the first (cut) week
        assertEquals("2026-07-17", first.startDay); assertEquals("2026-07-19", first.endDay)
        assertEquals(listOf(3.0), first.values)
        assertEquals("Oct 12", last.label)
        assertEquals("2026-10-12", last.startDay); assertEquals(today, last.endDay)
        assertEquals(listOf(3.0), last.values)
        assertEquals(listOf(7.0), s.bars[1].values)          // a whole week
        assertEquals(90.0, s.bars.sumOf { it.values[0] }, 0.0)
        assertEquals(90.0, s.total, 0.0)
    }

    @Test fun aYearIsCalendarMonths() {
        val h = (0 until 365).associate { date(it) to log(reviews = 2) }
        val s = build(ChartMetric.ANSWERS, ChartPeriod.D365, history = h)
        assertEquals(13, s.bars.size)
        assertEquals("Oct", s.bars.first().label); assertEquals("2025-10-15", s.bars.first().startDay); assertEquals("2025-10-31", s.bars.first().endDay)
        assertEquals(listOf(34.0), s.bars.first().values)    // 17 days x 2
        assertEquals("Oct", s.bars.last().label); assertEquals("2026-10-01", s.bars.last().startDay); assertEquals(today, s.bars.last().endDay)
        assertEquals(listOf(28.0), s.bars.last().values)
        assertEquals(listOf(60.0), s.bars[1].values)         // November: 30 days
        assertEquals(730.0, s.total, 0.0)
        assertEquals(2.0, s.perDayAvg, 1e-9)
    }

    // ---- totals, average, best, empty ----

    @Test fun totalAverageBestAndEmpty() {
        val h = mapOf(date(0) to log(xp = 100), date(3) to log(xp = 250), date(40) to log(xp = 999))
        val s = build(ChartMetric.XP, ChartPeriod.D30, history = h)
        assertEquals(350.0, s.total, 0.0)                    // the day 40 days ago is outside
        assertEquals(350.0 / 30, s.perDayAvg, 1e-9)
        assertEquals(250.0, s.best, 0.0); assertEquals(date(3), s.bestDay)
        assertFalse(s.empty); assertEquals("XP", s.unit)
        val e = build(ChartMetric.XP, ChartPeriod.D30)
        assertTrue(e.empty); assertEquals(0.0, e.total, 0.0); assertEquals(0.0, e.best, 0.0); assertNull(e.bestDay)
        assertEquals(30, e.bars.size)
    }

    @Test fun badTodayGivesAnEmptySeriesNotACrash() {
        val s = ChartData.build(ChartMetric.XP, ChartPeriod.D7, null, StatsSnapshot(), emptyMap(), "nope")
        assertTrue(s.empty); assertTrue(s.bars.isEmpty())
    }

    // ---- game metrics ----

    @Test fun gameMetricsReadTheirOwnDayLogFieldsAndIgnoreTheDeck() {
        val h = mapOf(date(1) to log(reviews = 10, won = 3, caught = 2, xp = 40, cash = 70), date(0) to log(reviews = 5, won = 1, caught = 0, xp = 5, cash = 8))
        fun total(m: ChartMetric) = build(m, ChartPeriod.D7, history = h, deck = "Some deck").also { assertFalse(it.deckApplies) }.total
        assertEquals(15.0, total(ChartMetric.ANSWERS), 0.0)
        assertEquals(45.0, total(ChartMetric.XP), 0.0)
        assertEquals(2.0, total(ChartMetric.CATCHES), 0.0)
        assertEquals(4.0, total(ChartMetric.WINS), 0.0)
        assertEquals(78.0, total(ChartMetric.MONEY), 0.0)
        assertEquals("₽", build(ChartMetric.MONEY, ChartPeriod.D7).unit)
    }

    @Test fun buttonsAreStackedAgainHardGoodEasy() {
        val h = mapOf(date(0) to log(buttons = listOf(9, 1, 2, 3, 4)), date(1) to log(buttons = listOf(0, 4, 0, 0, 1)))
        val s = build(ChartMetric.BUTTONS, ChartPeriod.D7, history = h)
        assertEquals(listOf("Again", "Hard", "Good", "Easy"), s.seriesNames)
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0), s.bars.last().values)       // the unknown button (index 0) is left out
        assertEquals(listOf(4.0, 0.0, 0.0, 1.0), s.bars[5].values)
        assertEquals(15.0, s.total, 0.0)
        assertEquals(10.0, s.best, 0.0); assertEquals(today, s.bestDay)
    }

    // ---- retention ----

    @Test fun retentionPerBarIsRoundedAndZeroAnswerDaysHaveNoData() {
        // today: 2 of 3 passed = 66.67 -> 67; yesterday 1 of 1 failed -> 0 with data; the rest no answers
        val h = mapOf(date(0) to log(buttons = listOf(0, 1, 0, 1, 1)), date(1) to log(buttons = listOf(0, 1, 0, 0, 0)))
        val s = build(ChartMetric.RETENTION, ChartPeriod.D7, history = h)
        assertEquals(listOf(67.0), s.bars.last().values); assertTrue(s.bars.last().hasData)
        assertEquals(listOf(0.0), s.bars[5].values); assertTrue(s.bars[5].hasData)
        assertFalse(s.bars[0].hasData); assertEquals(listOf(0.0), s.bars[0].values)
        assertEquals(50.0, s.total, 0.0)                    // 2 passed of 4 answers, no AnkiDroid number for 7 days yet
        assertEquals(33.5, s.perDayAvg, 1e-9)               // mean of the days with answers: (67 + 0) / 2
        assertEquals(67.0, s.best, 0.0); assertEquals(today, s.bestDay)
        assertFalse(s.empty); assertEquals("%", s.unit)
    }

    @Test fun retentionWithoutAnyAnswerIsEmpty() {
        val s = build(ChartMetric.RETENTION, ChartPeriod.D30)
        assertTrue(s.empty); assertEquals(0.0, s.total, 0.0); assertNull(s.bestDay)
        assertTrue(s.bars.none { it.hasData })
    }

    @Test fun retentionUsesAnkiDroidForTheTotalOf7And30And365Days() {
        val h = mapOf(date(0) to log(buttons = listOf(0, 0, 0, 1, 0)))        // the game says 100 %
        // 30 days: from the AnkiDroid refresh itself (answered 200, again 30 -> 85 %)
        val s30 = build(ChartMetric.RETENTION, ChartPeriod.D30, StatsSnapshot(stats = anki(answered = 200, again = 30)), h)
        assertEquals(85.0, s30.total, 0.0); assertEquals(listOf(100.0), s30.bars.last().values)
        // 7 and 365 days: from the on-demand window read
        val series = mapOf(
            SeriesKey(null, ChartMetric.RETENTION, 7, today) to SeriesData(mapOf(AnkiSeriesReader.ANSWERED to 3, AnkiSeriesReader.AGAIN to 1), 0),
            SeriesKey(null, ChartMetric.RETENTION, 365, today) to SeriesData(mapOf(AnkiSeriesReader.ANSWERED to 1000, AnkiSeriesReader.AGAIN to 123), 0),
        )
        val snap = StatsSnapshot(stats = anki(), series = series)
        assertEquals(67.0, build(ChartMetric.RETENTION, ChartPeriod.D7, snap, h).total, 0.0)
        assertEquals(88.0, build(ChartMetric.RETENTION, ChartPeriod.D365, snap, h).total, 0.0) // 87.7 -> 88
        // 90 days: AnkiDroid has no such window: the game's number
        assertEquals(100.0, build(ChartMetric.RETENTION, ChartPeriod.D90, snap, h).total, 0.0)
        // AnkiDroid knows it but the game counted nothing: not empty, no bar has data
        val onlyAnki = build(ChartMetric.RETENTION, ChartPeriod.D7, snap)
        assertFalse(onlyAnki.empty); assertEquals(67.0, onlyAnki.total, 0.0); assertTrue(onlyAnki.bars.none { it.hasData })
    }

    @Test fun retentionWindowsAskedFor() {
        assertEquals(listOf(SeriesKey(null, ChartMetric.RETENTION, 7, today)), ChartData.seriesKeys(ChartMetric.RETENTION, ChartPeriod.D7, null, today))
        assertEquals(listOf(SeriesKey(null, ChartMetric.RETENTION, 365, today)), ChartData.seriesKeys(ChartMetric.RETENTION, ChartPeriod.D365, "x", today))
        assertTrue(ChartData.seriesKeys(ChartMetric.RETENTION, ChartPeriod.D30, null, today).isEmpty())
        assertTrue(ChartData.seriesKeys(ChartMetric.RETENTION, ChartPeriod.D90, null, today).isEmpty())
    }

    // ---- AnkiDroid metrics ----

    @Test fun reviewsFromAnkiDroidAllDecks() {
        val snap = StatsSnapshot(stats = anki(mapOf(date(0) to 30, date(2) to 12)), access = AnkiAccess.OK)
        val s = build(ChartMetric.REVIEWS, ChartPeriod.D7, snap, history = mapOf(date(0) to log(reviews = 999)))
        assertEquals(42.0, s.total, 0.0); assertEquals("AnkiDroid", s.source); assertTrue(s.deckApplies); assertEquals("cards", s.unit)
        assertEquals(30.0, s.best, 0.0)
    }

    @Test fun reviewsFallBackToTheGameCountAndSayso() {
        val s = build(ChartMetric.REVIEWS, ChartPeriod.D7, StatsSnapshot(access = AnkiAccess.NOT_INSTALLED), mapOf(date(0) to log(reviews = 7)))
        assertEquals(7.0, s.total, 0.0)
        assertTrue(s.source.startsWith("AnkiGo's own count")); assertFalse(s.deckApplies)
    }

    @Test fun reviewsNoteWhenTheHistoryIsStillLoading() {
        val loading = anki(mapOf(date(0) to 1), checked = 0L)
        assertEquals("AnkiDroid · history still loading", build(ChartMetric.REVIEWS, ChartPeriod.D30, StatsSnapshot(stats = loading)).source)
        assertEquals("AnkiDroid", build(ChartMetric.REVIEWS, ChartPeriod.D7, StatsSnapshot(stats = loading)).source) // the last 7 days are always read
    }

    @Test fun deckReviewsComeFromTheSeriesCacheOrShowLoading() {
        val key = SeriesKey("Spanish", ChartMetric.REVIEWS, 7, today)
        assertEquals(listOf(key), ChartData.seriesKeys(ChartMetric.REVIEWS, ChartPeriod.D7, "Spanish", today))
        assertTrue(ChartData.seriesKeys(ChartMetric.REVIEWS, ChartPeriod.D7, null, today).isEmpty())
        val pending = build(ChartMetric.REVIEWS, ChartPeriod.D7, StatsSnapshot(stats = anki(mapOf(date(0) to 99))), deck = "Spanish")
        assertTrue(pending.loading); assertTrue(pending.empty); assertEquals("Reading AnkiDroid…", pending.source); assertEquals("Spanish", pending.deck)
        val done = build(ChartMetric.REVIEWS, ChartPeriod.D7, StatsSnapshot(stats = anki(), series = mapOf(key to SeriesData(mapOf(date(0) to 8, date(1) to 2), 0))), deck = "Spanish")
        assertFalse(done.loading); assertEquals(10.0, done.total, 0.0); assertTrue(done.deckApplies)
        val failed = build(ChartMetric.REVIEWS, ChartPeriod.D7, StatsSnapshot(access = AnkiAccess.NO_PERMISSION), deck = "Spanish")
        assertFalse(failed.loading); assertEquals(StatsText.reason(AnkiAccess.NO_PERMISSION), failed.source)
    }

    @Test fun newCardsAllDecksAndPerDeck() {
        assertEquals(listOf(SeriesKey(null, ChartMetric.NEW_CARDS, 30, today)), ChartData.seriesKeys(ChartMetric.NEW_CARDS, ChartPeriod.D30, null, today))
        assertEquals(listOf(SeriesKey("D", ChartMetric.NEW_CARDS, 30, today)), ChartData.seriesKeys(ChartMetric.NEW_CARDS, ChartPeriod.D30, "D", today))
        val snap = StatsSnapshot(series = mapOf(SeriesKey(null, ChartMetric.NEW_CARDS, 7, today) to SeriesData(mapOf(date(0) to 5, date(6) to 3), 0)))
        val s = build(ChartMetric.NEW_CARDS, ChartPeriod.D7, snap)
        assertEquals(8.0, s.total, 0.0); assertEquals(5.0, s.best, 0.0)
        assertTrue(build(ChartMetric.NEW_CARDS, ChartPeriod.D7).loading)
    }

    @Test fun dueLooksAheadOnlyAndNeverBeyond30Days() {
        val forecast = List(30) { it + 1 } // 1 due tomorrow, 2 the day after ...
        val snap = StatsSnapshot(stats = anki(forecast = forecast))
        val s7 = build(ChartMetric.DUE, ChartPeriod.D7, snap)
        assertEquals(7, s7.bars.size)
        assertEquals("2026-10-15", s7.bars.first().startDay); assertEquals("2026-10-21", s7.bars.last().startDay)
        assertEquals(listOf("Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed"), s7.bars.map { it.label })
        assertEquals(28.0, s7.total, 0.0); assertEquals(4.0, s7.perDayAvg, 1e-9); assertEquals(7.0, s7.best, 0.0)
        val s365 = build(ChartMetric.DUE, ChartPeriod.D365, snap)
        assertEquals(ChartPeriod.D30, s365.period); assertEquals(30, s365.bars.size); assertEquals(465.0, s365.total, 0.0)
        assertEquals(ChartPeriod.D30, ChartData.effectivePeriod(ChartMetric.DUE, ChartPeriod.D90))
        assertEquals(ChartPeriod.D90, ChartData.effectivePeriod(ChartMetric.REVIEWS, ChartPeriod.D90))
        assertEquals(listOf(SeriesKey("D", ChartMetric.DUE, 30, today)), ChartData.seriesKeys(ChartMetric.DUE, ChartPeriod.D365, "D", today))
    }

    @Test fun dueWithoutAnkiDroidSaysWhy() {
        val s = build(ChartMetric.DUE, ChartPeriod.D7, StatsSnapshot(access = AnkiAccess.ANKIDROID_TOO_OLD))
        assertTrue(s.empty); assertEquals(StatsText.reason(AnkiAccess.ANKIDROID_TOO_OLD), s.source); assertFalse(s.loading)
    }

    // ---- time ----

    @Test fun timeAddsInAppAndLiveInMinutes() {
        val snap = StatsSnapshot(timeByDay = mapOf(date(0) to 600_000L, date(1) to 120_000L), liveTimeByDay = mapOf(date(0) to 300_000L, date(2) to 60_000L))
        val s = build(ChartMetric.TIME, ChartPeriod.D7, snap)
        assertEquals(listOf("In AnkiGo", "Live"), s.seriesNames)
        assertEquals(listOf(10.0, 5.0), s.bars.last().values)
        assertEquals(listOf(0.0, 1.0), s.bars[4].values)
        assertEquals(18.0, s.total, 1e-9); assertEquals(15.0, s.best, 1e-9); assertEquals("min", s.unit); assertFalse(s.deckApplies)
    }

    @Test fun everyMetricAndPeriodBuildsWithoutCrashing() {
        val snap = StatsSnapshot(stats = anki(mapOf(date(0) to 3)), timeByDay = mapOf(date(0) to 1L))
        for (m in ChartMetric.values()) for (p in ChartPeriod.values()) {
            val s = build(m, p, snap, mapOf(date(0) to log(reviews = 1, buttons = listOf(0, 1, 1, 1, 1))))
            // R3-14: the collection charts read a CollectionStats this test doesn't give (ChartMetricsTest does): no bars then
            if (m.group == ChartGroup.COLLECTION) { assertTrue("$m $p", s.empty); continue }
            assertTrue("$m $p", s.bars.isNotEmpty())
            assertEquals(m.seriesNames.size, s.bars.first().values.size)
        }
    }
}
