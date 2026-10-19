package com.husarp.ankigo.stats

import com.husarp.ankigo.game.DayLog
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// TICKET R3-14: the metrics Adam's picked list needs beyond R3-04's chart: groups, money spent, healing, card states, the
// streak calendar, the three collection charts, the bar size ("Per day") and the tap text of every bar. Made-up data only.
class ChartMetricsTest {
    private val today = "2026-10-14" // a Wednesday
    private val t = LocalDate.parse(today)
    private fun date(k: Int) = t.minusDays(k.toLong()).toString()

    private fun log(reviews: Int = 0, xp: Int = 0, cash: Int = 0, spent: Int = 0, heal: Int = 0, buttons: List<Int> = listOf(0, 0, 0, 0, 0)) =
        DayLog(reviews = reviews, xp = xp, cash = cash, buttons = buttons.toIntArray(), spent = spent, healItems = heal)

    private fun anki(perDay: Map<String, Int> = emptyMap(), counts: Counts = Counts(), forecast: List<Int> = List(30) { 0 }) =
        AnkiStats("pkg", 0, today, perDay, emptyMap(), emptyMap(), counts, 0, forecast, 0, 0, 1L)

    private fun build(
        m: ChartMetric, p: ChartPeriod, snap: StatsSnapshot = StatsSnapshot(), history: Map<String, DayLog> = emptyMap(), deck: String? = null,
        grain: ChartGrain? = null, goal: Int = 50, collection: CollectionStats? = null,
    ) = ChartData.build(m, p, deck, snap, history, today, grain, goal, collection)

    // ---------------------------------------------------------------- the metric list

    @Test fun theMetricListIsGroupedLikeTheMockup() {
        fun names(g: ChartGroup) = ChartMetric.menu.first { it.first == g }.second.map { it.label }
        assertEquals(listOf("Study", "Game", "Collection"), ChartMetric.menu.map { it.first.label })
        assertEquals(
            listOf("Reviews", "Time studied", "Retention", "New cards", "Buttons pressed", "Card states", "Due forecast", "Streak calendar"),
            names(ChartGroup.STUDY),
        )
        assertEquals(
            listOf("Money earned", "Catches", "Battles won", "XP earned", "Healing used", "Money spent", "Damage dealt", "Damage taken", "Damage blocked", "Misses"),
            names(ChartGroup.GAME),
        )
        assertEquals(listOf("Pokédex progress", "Pokémon per type", "Shinies & rare"), names(ChartGroup.COLLECTION))
    }

    @Test fun gameGroupKeepsTheMockupOrder() {
        val game = ChartMetric.menu.first { it.first == ChartGroup.GAME }.second
        assertEquals(
            listOf(
                ChartMetric.MONEY, ChartMetric.CATCHES, ChartMetric.WINS, ChartMetric.XP, ChartMetric.HEALING, ChartMetric.MONEY_SPENT,
                ChartMetric.DMG_DEALT, ChartMetric.DMG_TAKEN, ChartMetric.DMG_BLOCKED, ChartMetric.MISSES,
            ),
            game,
        )
    }

    @Test fun everyMetricHasALabelGroupAndIconAndAnswersIsLeftOutOfTheList() {
        for (m in ChartMetric.values()) {
            assertTrue("$m label", m.label.isNotBlank()); assertTrue("$m icon", m.icon.isNotBlank()); assertTrue("$m noun", m.noun.isNotBlank())
            assertTrue("$m series", m.seriesNames.isNotEmpty())
        }
        val listed = ChartMetric.menu.flatMap { it.second }
        assertFalse(ChartMetric.ANSWERS in listed)
        assertEquals(ChartMetric.values().size - 1, listed.size)
        assertEquals(listed.size, listed.toSet().size)
        for ((g, ms) in ChartMetric.menu) assertTrue(ms.all { it.group == g })
        assertEquals(listed.size, listed.map { it.icon }.toSet().size) // one icon each
    }

    @Test fun kindsAndWhichFiltersApply() {
        assertEquals(ChartKind.CATEGORY, ChartMetric.CARD_STATES.kind)
        for (m in listOf(ChartMetric.DEX_PROGRESS, ChartMetric.PER_TYPE, ChartMetric.SHINIES_RARE)) {
            assertEquals(ChartKind.CATEGORY, m.kind); assertFalse(m.usesPeriod); assertFalse(m.fromAnkiDroid)
        }
        assertEquals(ChartKind.HEATMAP, ChartMetric.STREAK_CALENDAR.kind); assertTrue(ChartMetric.STREAK_CALENDAR.usesPeriod)
        assertEquals(ChartKind.TIME, ChartMetric.REVIEWS.kind)
        assertEquals(ChartKind.TIME, ChartMetric.MONEY_SPENT.kind); assertEquals(ChartKind.TIME, ChartMetric.HEALING.kind)
        assertFalse(ChartMetric.CARD_STATES.usesPeriod)
    }

    // ---------------------------------------------------------------- money spent, healing used

    @Test fun moneySpentAndHealingReadTheirDayLogFields() {
        val h = mapOf(date(0) to log(spent = 1200, heal = 2), date(2) to log(spent = 300, heal = 1, cash = 999))
        val spent = build(ChartMetric.MONEY_SPENT, ChartPeriod.D7, history = h, deck = "Some deck")
        assertEquals(1500.0, spent.total, 0.0); assertEquals(1200.0, spent.best, 0.0); assertEquals(today, spent.bestDay)
        assertEquals("₽", spent.unit); assertEquals(listOf("Spent"), spent.seriesNames); assertFalse(spent.deckApplies)
        assertEquals(listOf(1200.0), spent.bars.last().values)
        val heal = build(ChartMetric.HEALING, ChartPeriod.D7, history = h)
        assertEquals(3.0, heal.total, 0.0); assertEquals(2.0, heal.best, 0.0); assertEquals("items", heal.unit)
        assertFalse(heal.empty)
        // the money earned keeps its own field
        assertEquals(999.0, build(ChartMetric.MONEY, ChartPeriod.D7, history = h).total, 0.0)
        assertTrue(build(ChartMetric.MONEY_SPENT, ChartPeriod.D7).empty)
        assertTrue(build(ChartMetric.HEALING, ChartPeriod.D30).empty)
    }

    @Test fun moneySpentAndHealingTapText() {
        val h = mapOf(date(0) to log(spent = 1200, heal = 1), date(1) to log(heal = 3))
        assertEquals(listOf("Wed 14 Oct · 1,200 ₽ spent"), build(ChartMetric.MONEY_SPENT, ChartPeriod.D7, history = h).bars.last().detail)
        assertEquals(listOf("Wed 14 Oct · 1 healing item"), build(ChartMetric.HEALING, ChartPeriod.D7, history = h).bars.last().detail)
        assertEquals(listOf("Tue 13 Oct · 3 healing items"), build(ChartMetric.HEALING, ChartPeriod.D7, history = h).bars[5].detail)
    }

    // ---------------------------------------------------------------- bar size (granularity)

    @Test fun theBarSizesOfEveryPeriod() {
        assertEquals(listOf(ChartGrain.DAY), ChartData.grains(ChartMetric.XP, ChartPeriod.D7))
        assertEquals(listOf(ChartGrain.DAY, ChartGrain.WEEK), ChartData.grains(ChartMetric.XP, ChartPeriod.D30))
        assertEquals(listOf(ChartGrain.DAY, ChartGrain.WEEK, ChartGrain.MONTH), ChartData.grains(ChartMetric.XP, ChartPeriod.D90))
        assertEquals(listOf(ChartGrain.WEEK, ChartGrain.MONTH), ChartData.grains(ChartMetric.XP, ChartPeriod.D365))
        assertEquals(listOf(ChartGrain.DAY, ChartGrain.WEEK), ChartData.grains(ChartMetric.DUE, ChartPeriod.D365)) // DUE only looks 30 days ahead
        for (m in listOf(ChartMetric.CARD_STATES, ChartMetric.STREAK_CALENDAR, ChartMetric.DEX_PROGRESS, ChartMetric.PER_TYPE, ChartMetric.SHINIES_RARE))
            assertTrue("$m", ChartData.grains(m, ChartPeriod.D30).isEmpty())
        assertEquals("Per day", ChartGrain.DAY.label); assertEquals("Per week", ChartGrain.WEEK.label); assertEquals("Per month", ChartGrain.MONTH.label)
    }

    @Test fun noChoiceKeepsTheDefaultsAndInvalidChoicesFallBack() {
        fun g(p: ChartPeriod, want: ChartGrain?) = ChartData.effectiveGrain(ChartMetric.XP, p, want)
        assertEquals(ChartGrain.DAY, g(ChartPeriod.D7, null)); assertEquals(ChartGrain.DAY, g(ChartPeriod.D30, null))
        assertEquals(ChartGrain.WEEK, g(ChartPeriod.D90, null)); assertEquals(ChartGrain.MONTH, g(ChartPeriod.D365, null))
        assertEquals(ChartGrain.DAY, g(ChartPeriod.D7, ChartGrain.MONTH))
        assertEquals(ChartGrain.DAY, g(ChartPeriod.D7, ChartGrain.WEEK))
        assertEquals(ChartGrain.DAY, g(ChartPeriod.D30, ChartGrain.MONTH))
        assertEquals(ChartGrain.MONTH, g(ChartPeriod.D365, ChartGrain.DAY))
        assertEquals(ChartGrain.WEEK, g(ChartPeriod.D30, ChartGrain.WEEK))
        assertEquals(ChartGrain.DAY, g(ChartPeriod.D90, ChartGrain.DAY))
        assertEquals(ChartGrain.WEEK, g(ChartPeriod.D365, ChartGrain.WEEK))
        assertEquals(ChartGrain.DAY, ChartData.effectiveGrain(ChartMetric.DUE, ChartPeriod.D365, ChartGrain.MONTH))
    }

    @Test fun monthForSevenDaysIsStillSevenDayBars() {
        val s = build(ChartMetric.XP, ChartPeriod.D7, grain = ChartGrain.MONTH)
        assertEquals(7, s.bars.size); assertEquals(ChartGrain.DAY, s.grain)
        assertEquals(ChartGrain.DAY, build(ChartMetric.XP, ChartPeriod.D30).grain)
        assertEquals(ChartGrain.WEEK, build(ChartMetric.XP, ChartPeriod.D90).grain)
    }

    @Test fun thirtyDaysPerWeek() {
        val h = mapOf(date(0) to log(xp = 100), date(1) to log(xp = 50), date(2) to log(xp = 30), date(10) to log(xp = 7))
        val s = build(ChartMetric.XP, ChartPeriod.D30, history = h, grain = ChartGrain.WEEK)
        assertEquals(ChartGrain.WEEK, s.grain)
        assertEquals(listOf("Sep 14", "Sep 21", "Sep 28", "Oct 5", "Oct 12"), s.bars.map { it.label })
        assertEquals("2026-09-15", s.bars.first().startDay); assertEquals("2026-09-20", s.bars.first().endDay) // the cut first week
        assertEquals(listOf(180.0), s.bars.last().values)
        assertEquals(5, s.bars.size)
        assertEquals(listOf(7.0), s.bars[2].values)                                // Sun 4 Oct (date 10) is in the week of Mon 28 Sep
        assertEquals(187.0, s.total, 0.0)
        assertEquals(listOf("12 – 14 Oct · 180 XP", "Average 60 XP a day"), s.bars.last().detail)
        assertEquals(listOf("15 – 20 Sep · 0 XP"), s.bars.first().detail)
    }

    @Test fun ninetyDaysPerDayAndPerMonth() {
        val daily = build(ChartMetric.XP, ChartPeriod.D90, grain = ChartGrain.DAY)
        assertEquals(90, daily.bars.size); assertEquals("14", daily.bars.last().label)
        val h = mapOf("2026-08-10" to log(xp = 310), date(0) to log(xp = 28))
        val m = build(ChartMetric.XP, ChartPeriod.D90, history = h, grain = ChartGrain.MONTH)
        assertEquals(listOf("Jul", "Aug", "Sep", "Oct"), m.bars.map { it.label })
        assertEquals(listOf(310.0), m.bars[1].values)
        assertEquals(listOf("August 2026 · 310 XP", "Average 10 XP a day"), m.bars[1].detail)
        assertEquals(listOf("17 – 31 Jul · 0 XP"), m.bars[0].detail)
        assertEquals(listOf("1 – 14 Oct · 28 XP", "Average 2 XP a day"), m.bars.last().detail)
    }

    @Test fun aYearPerWeekHasPartialEndsAndNamesTheOldYear() {
        val h = (0 until 365).associate { date(it) to log(reviews = 2) }
        val s = build(ChartMetric.ANSWERS, ChartPeriod.D365, history = h, grain = ChartGrain.WEEK)
        assertEquals(ChartGrain.WEEK, s.grain)
        assertEquals(53, s.bars.size)
        assertEquals(730.0, s.bars.sumOf { it.values[0] }, 0.0)
        assertTrue(s.bars.first().detail.first().startsWith("15 – 19 Oct 2025 · "))   // Wed 15 Oct 2025 .. Sun 19 Oct 2025
        assertEquals("2026-10-12", s.bars.last().startDay)
    }

    @Test fun monthBarsOfAYearNameTheYearWhenNotThisOne() {
        val h = (0 until 365).associate { date(it) to log(reviews = 2) }
        val s = build(ChartMetric.ANSWERS, ChartPeriod.D365, history = h)
        assertEquals(listOf("15 – 31 Oct 2025 · 34 answers", "Average 2 answers a day"), s.bars.first().detail)
        assertEquals(listOf("November 2025 · 60 answers", "Average 2 answers a day"), s.bars[1].detail)
        assertEquals(listOf("1 – 14 Oct · 28 answers", "Average 2 answers a day"), s.bars.last().detail)
    }

    @Test fun aWeekThatCrossesAMonthAndOneThatCrossesNewYear() {
        val h = mapOf(date(14) to log(xp = 8)) // Wed 30 Sep: in the week Mon 28 Sep .. Sun 4 Oct
        val s = build(ChartMetric.XP, ChartPeriod.D30, history = h, grain = ChartGrain.WEEK)
        assertEquals(listOf("28 Sep – 4 Oct · 8 XP", "Average 1.1 XP a day"), s.bars[2].detail)
        val ny = ChartData.build(ChartMetric.XP, ChartPeriod.D30, null, StatsSnapshot(), mapOf("2025-12-30" to log(xp = 70)), "2026-01-05", ChartGrain.WEEK)
        assertEquals(listOf("29 Dec 2025 – 4 Jan 2026 · 70 XP", "Average 10 XP a day"), ny.bars.first { it.startDay <= "2025-12-30" && it.endDay >= "2025-12-30" }.detail)
    }

    // ---------------------------------------------------------------- tap text of time bars

    @Test fun aDayBarSaysTheDayAndTheValue() {
        val s = build(ChartMetric.REVIEWS, ChartPeriod.D7, StatsSnapshot(stats = anki(mapOf(date(0) to 43, date(1) to 1)), access = AnkiAccess.OK))
        assertEquals(listOf("Wed 14 Oct · 43 reviews"), s.bars.last().detail)
        assertEquals(listOf("Tue 13 Oct · 1 review"), s.bars[5].detail)
        assertEquals(listOf("Thu 8 Oct · 0 reviews"), s.bars.first().detail)
        assertEquals(s.bars.last().detail, s.detailFor(today))
        assertEquals(s.bars[5].detail, s.detailFor(date(1)))
        assertTrue(s.detailFor("2020-01-01").isEmpty())
    }

    @Test fun stackedButtonBarsListEachPartWithItsPercent() {
        val h = mapOf(date(0) to log(buttons = listOf(9, 1, 2, 3, 4)))
        val s = build(ChartMetric.BUTTONS, ChartPeriod.D7, history = h)
        assertEquals(
            listOf("Wed 14 Oct · 10 answers", "Again · 1 · 10 %", "Hard · 2 · 20 %", "Good · 3 · 30 %", "Easy · 4 · 40 %"),
            s.bars.last().detail,
        )
        assertEquals(listOf("Tue 13 Oct · 0 answers"), s.bars[5].detail) // no part lines for an empty bar
    }

    @Test fun timeBarsUseMinutesAndHoursAndSplitInAppFromLive() {
        val snap = StatsSnapshot(timeByDay = mapOf(date(0) to 600_000L, date(1) to 4_500_000L), liveTimeByDay = mapOf(date(0) to 300_000L))
        val s = build(ChartMetric.TIME, ChartPeriod.D7, snap)
        assertEquals(listOf("Wed 14 Oct · 15 min", "In AnkiGo · 10 min · 67 %", "Live · 5 min · 33 %"), s.bars.last().detail)
        assertEquals(listOf("Tue 13 Oct · 1h 15m", "In AnkiGo · 1h 15m · 100 %", "Live · 0 min · 0 %"), s.bars[5].detail)
        assertEquals(listOf("Mon 12 Oct · 0 min"), s.bars[4].detail)
    }

    @Test fun retentionBarsSayHowManyAnswersTheyAreMadeOf() {
        val h = mapOf(date(0) to log(buttons = listOf(0, 1, 0, 1, 1)))
        val s = build(ChartMetric.RETENTION, ChartPeriod.D7, history = h)
        assertEquals(listOf("Wed 14 Oct · 67 % kept", "2 of 3 answers weren't Again"), s.bars.last().detail)
        assertEquals(listOf("Thu 8 Oct · no answers counted"), s.bars.first().detail)
    }

    @Test fun dueBarsLookAhead() {
        val s = build(ChartMetric.DUE, ChartPeriod.D7, StatsSnapshot(stats = anki(forecast = List(30) { it + 1 }), access = AnkiAccess.OK))
        assertEquals(listOf("Thu 15 Oct · 1 card due"), s.bars.first().detail)
        assertEquals(listOf("Wed 21 Oct · 7 cards due"), s.bars.last().detail)
        val weeks = build(ChartMetric.DUE, ChartPeriod.D30, StatsSnapshot(stats = anki(forecast = List(30) { 1 }), access = AnkiAccess.OK), grain = ChartGrain.WEEK)
        assertEquals(ChartGrain.WEEK, weeks.grain)
        assertEquals(listOf("15 – 18 Oct · 4 cards due", "Average 1 card due a day"), weeks.bars.first().detail)
    }

    @Test fun barsStillLoadingHaveNoTapText() {
        val s = build(ChartMetric.NEW_CARDS, ChartPeriod.D7)
        assertTrue(s.loading); assertTrue(s.bars.all { it.detail.isEmpty() })
    }

    @Test fun moneyShowsThousandsAndDecimals() {
        val h = mapOf(date(0) to log(cash = 12_345), date(1) to log(cash = 10))
        val s = build(ChartMetric.MONEY, ChartPeriod.D7, history = h)
        assertEquals(listOf("Wed 14 Oct · 12,345 ₽ earned"), s.bars.last().detail)
        val w = build(ChartMetric.MONEY, ChartPeriod.D30, history = h, grain = ChartGrain.WEEK)
        assertEquals(listOf("12 – 14 Oct · 12,355 ₽ earned", "Average 4,118.3 ₽ earned a day"), w.bars.last().detail) // 3 days in the last week
    }

    // ---------------------------------------------------------------- card states

    private val counts = Counts(new = 120, learning = 5, relearning = 3, young = 1000, mature = 2000, suspended = 40, buried = 1)

    @Test fun cardStatesAllDecksUseTheRefreshCounts() {
        val s = build(ChartMetric.CARD_STATES, ChartPeriod.D30, StatsSnapshot(stats = anki(counts = counts), access = AnkiAccess.OK))
        assertEquals(ChartKind.CATEGORY, s.kind)
        assertEquals(listOf("New", "Learning", "Relearning", "Young", "Mature", "Suspended", "Buried"), s.bars.map { it.label })
        assertEquals(listOf(120.0, 5.0, 3.0, 1000.0, 2000.0, 40.0, 1.0), s.bars.map { it.values.single() })
        assertEquals(3169.0, s.total, 0.0); assertEquals(2000.0, s.best, 0.0); assertEquals("Mature", s.bestLabel); assertNull(s.bestDay)
        assertEquals("cards", s.unit); assertEquals("AnkiDroid", s.source); assertTrue(s.deckApplies)
        assertFalse(s.empty); assertFalse(s.loading); assertTrue(s.bars.all { it.startDay.isEmpty() && it.endDay.isEmpty() })
        assertEquals(listOf("Young · 1,000 cards", "32 % of the 3,169 cards counted"), s.bars[3].detail)
        assertEquals(listOf("Buried · 1 card", "0 % of the 3,169 cards counted"), s.bars[6].detail)
        assertTrue(ChartData.seriesKeys(ChartMetric.CARD_STATES, ChartPeriod.D30, null, today).isEmpty())
    }

    @Test fun cardStatesOfOneDeckComeFromTheSeriesCache() {
        val key = SeriesKey("Spanish", ChartMetric.CARD_STATES, 0, today)
        assertEquals(listOf(key), ChartData.seriesKeys(ChartMetric.CARD_STATES, ChartPeriod.D365, "Spanish", today))
        val pending = build(ChartMetric.CARD_STATES, ChartPeriod.D30, StatsSnapshot(stats = anki(counts = counts)), deck = "Spanish")
        assertTrue(pending.loading); assertTrue(pending.empty); assertEquals(7, pending.bars.size); assertEquals("Spanish", pending.deck)
        assertEquals("Reading AnkiDroid…", pending.source); assertTrue(pending.bars.all { it.detail.isEmpty() })
        val data = SeriesData(mapOf("NEW" to 5, "YOUNG" to 15, "MATURE" to 30, "SUSPENDED" to 0), 0)
        val done = build(ChartMetric.CARD_STATES, ChartPeriod.D30, StatsSnapshot(stats = anki(counts = counts), series = mapOf(key to data)), deck = "Spanish")
        assertFalse(done.loading); assertEquals(50.0, done.total, 0.0)
        assertEquals(listOf(5.0, 0.0, 0.0, 15.0, 30.0, 0.0, 0.0), done.bars.map { it.values.single() })
        assertEquals(listOf("Mature · 30 cards", "60 % of the 50 cards counted"), done.bars[4].detail)
        val failed = build(ChartMetric.CARD_STATES, ChartPeriod.D30, StatsSnapshot(access = AnkiAccess.NO_PERMISSION), deck = "Spanish")
        assertFalse(failed.loading); assertTrue(failed.empty); assertEquals(StatsText.reason(AnkiAccess.NO_PERMISSION), failed.source)
    }

    @Test fun cardStatesWithoutAnkiDroidSayWhy() {
        val loading = build(ChartMetric.CARD_STATES, ChartPeriod.D30)
        assertTrue(loading.loading); assertEquals("Reading AnkiDroid…", loading.source)
        val off = build(ChartMetric.CARD_STATES, ChartPeriod.D30, StatsSnapshot(access = AnkiAccess.NOT_INSTALLED))
        assertFalse(off.loading); assertTrue(off.empty); assertEquals(StatsText.reason(AnkiAccess.NOT_INSTALLED), off.source)
    }

    @Test fun cardStatesReadSevenSearchesWithTheDeckPrefix() {
        val asked = ArrayList<String>()
        val d = AnkiSeriesReader { q -> asked += q; if (q.contains("is:new")) 7 else 1 }.read(SeriesKey("A::B", ChartMetric.CARD_STATES, 0, today), 3L)
        assertEquals(7, asked.size)
        assertEquals("deck:\"A::B\" is:new -is:suspended -is:buried", asked[0])
        assertEquals("deck:\"A::B\" is:suspended", asked[5]); assertEquals("deck:\"A::B\" is:buried -is:suspended", asked[6])
        assertEquals(CardState.values().map { it.name }.toSet(), d.perDay.keys)
        assertEquals(7, d.perDay["NEW"]); assertEquals(1, d.perDay["BURIED"])
        // all decks (no prefix): the plain AnkiDroid searches
        val plain = ArrayList<String>()
        AnkiSeriesReader { q -> plain += q; 0 }.read(SeriesKey(null, ChartMetric.CARD_STATES, 0, today), 0)
        assertEquals(listOf(AnkiQueries.NEW, AnkiQueries.LEARNING, AnkiQueries.RELEARNING, AnkiQueries.YOUNG, AnkiQueries.MATURE, AnkiQueries.SUSPENDED, AnkiQueries.BURIED), plain)
        assertEquals(120, CardState.NEW.of(counts)); assertEquals(1, CardState.BURIED.of(counts)); assertEquals(1000, CardState.YOUNG.of(counts))
    }

    // ---------------------------------------------------------------- streak calendar

    @Test fun theStreakCalendarIsTheHeatmapGridAndOneBarPerCell() {
        val h = mapOf(date(0) to log(reviews = 60), date(1) to log(reviews = 20), date(20) to log(reviews = 5))
        val s = build(ChartMetric.STREAK_CALENDAR, ChartPeriod.D30, history = h, goal = 50)
        assertEquals(ChartKind.HEATMAP, s.kind)
        val grid = s.heatmap!!
        assertEquals(6, grid.columns.size)                                  // 6 weeks, ending with the current one
        assertEquals(30, s.bars.size)                                       // the 30 requested days: Tue 15 Sep .. Wed 14 Oct
        assertEquals("2026-09-15", s.bars.first().startDay); assertEquals(today, s.bars.last().startDay)
        val last = grid.columns.last()
        assertEquals(4, last[2]!!.level); assertEquals(60, last[2]!!.reviews)   // Wed 14 Oct: goal reached
        assertEquals(2, last[1]!!.level)                                        // Tue 13 Oct: 40 %
        assertNull(last[3])                                                     // Thursday is after today
        assertEquals(85.0, s.total, 0.0); assertEquals(60.0, s.best, 0.0); assertEquals(today, s.bestDay)
        assertEquals(85.0 / 30, s.perDayAvg, 1e-9)
        assertFalse(s.empty); assertFalse(s.deckApplies)
        assertEquals(grid.total, 85)
        assertEquals("AnkiGo's own count (AnkiDroid isn't available)", s.source)
        assertEquals(listOf("Wed 14 Oct · 60 reviews", "Daily goal reached (50)"), s.detailFor(today))
        assertEquals(listOf("Tue 13 Oct · 20 reviews", "40 % of the daily goal (50)"), s.detailFor(date(1)))
        assertEquals(listOf("Mon 12 Oct · 0 reviews"), s.detailFor(date(2)))
        assertEquals(s.bars.last().detail, s.detailFor(today))
        assertEquals(s.bars.size, s.bars.map { it.startDay }.toSet().size)
    }

    @Test fun theStreakCalendarPrefersAnkiDroidsNumbersAndHasWeeksPerPeriod() {
        val snap = StatsSnapshot(stats = anki(mapOf(date(0) to 10, date(3) to 100)), access = AnkiAccess.OK)
        val s = build(ChartMetric.STREAK_CALENDAR, ChartPeriod.D7, snap, mapOf(date(0) to log(reviews = 999)), goal = 100)
        assertEquals(4, s.heatmap!!.columns.size)
        assertEquals(110.0, s.total, 0.0)
        assertTrue(s.source.startsWith("AnkiDroid"))
        assertEquals(listOf("Sun 11 Oct · 100 reviews", "Daily goal reached (100)"), s.detailFor(date(3)))
        assertEquals(listOf(4, 6, 13, 53), ChartPeriod.values().map { ChartData.heatmapWeeks(it) })
        assertEquals(13, build(ChartMetric.STREAK_CALENDAR, ChartPeriod.D90).heatmap!!.columns.size)
        assertEquals(53, build(ChartMetric.STREAK_CALENDAR, ChartPeriod.D365).heatmap!!.columns.size)
        assertTrue(build(ChartMetric.STREAK_CALENDAR, ChartPeriod.D90).empty)
        assertTrue(ChartData.seriesKeys(ChartMetric.STREAK_CALENDAR, ChartPeriod.D90, "x", today).isEmpty())
    }

    @Test fun theStreakCalendarCountsOnlyTheRequestedPeriod() {
        val h = mapOf(date(0) to log(reviews = 10), date(6) to log(reviews = 20), date(7) to log(reviews = 500), date(14) to log(reviews = 900))
        val s = build(ChartMetric.STREAK_CALENDAR, ChartPeriod.D7, history = h, goal = 50)
        assertEquals(4, s.heatmap!!.columns.size)                // whole-week geometry stays
        assertEquals(7, s.bars.size)
        assertEquals(30.0, s.total, 0.0); assertEquals(20.0, s.best, 0.0); assertEquals(30.0 / 7, s.perDayAvg, 1e-9)
        assertEquals(30, s.heatmap!!.total)
        assertEquals(30, s.heatmap!!.columns.flatten().filterNotNull().sumOf { it.reviews })
        assertNull(s.heatmap!!.columns.flatten().firstOrNull { it != null && it.day == date(7) })   // outside the period: empty cell
    }

    @Test fun retentionCountsUnknownButtonAnswersAsPassed() {
        val unknown = DayLog().also { it.reviews = 4; it.buttons[0] = 4 }
        val s = build(ChartMetric.RETENTION, ChartPeriod.D7, history = mapOf(date(0) to unknown))
        assertFalse(s.empty)
        assertEquals(100.0, s.bars.last().values[0], 0.0)
        val mixed = DayLog().also { it.buttons[0] = 2; it.buttons[1] = 2 }
        assertEquals(50.0, build(ChartMetric.RETENTION, ChartPeriod.D7, history = mapOf(date(0) to mixed)).bars.last().values[0], 0.0)
    }

    @Test fun oneReviewReadsInTheSingular() {
        val s = build(ChartMetric.STREAK_CALENDAR, ChartPeriod.D7, history = mapOf(date(0) to log(reviews = 1)), goal = 50)
        assertEquals(listOf("Wed 14 Oct · 1 review", "2 % of the daily goal (50)"), s.detailFor(today))
    }

    // ---------------------------------------------------------------- collection charts

    private val data = TestData.data(
        listOf(
            TestData.species(1, types = listOf(Type.GRASS, Type.POISON)),
            TestData.species(2, types = listOf(Type.GRASS)),
            TestData.species(3, types = listOf(Type.FIRE)),
            TestData.species(152, types = listOf(Type.WATER), isLegendary = true),
            TestData.species(400, types = listOf(Type.WATER, Type.FLYING), isMythical = true),
            TestData.species(793, "Ultramon", listOf(Type.POISON)),
            TestData.species(10001, "Regionalmon", listOf(Type.FIRE), gen = 7).copy(form = "Alola", baseNum = 1),
        ),
    )

    private fun collection(): CollectionStats {
        val st = GameState()
        fun mon(sp: Int, uid: String, shiny: Boolean = false) = TestData.mon(data, sp, 10, uid = uid).also { it.shiny = shiny }
        st.mons += listOf(mon(1, "a", true), mon(1, "b"), mon(152, "c"), mon(400, "d", true), mon(10001, "e"), mon(793, "f"))
        st.seen += setOf(1, 2, 152, 400, 793); st.caught += setOf(1, 152, 793)
        return CollectionStats.of(data, st, 0L)
    }

    @Test fun ultraBeastsAreCountedFromTheSpeciesNumber() {
        val c = collection()
        assertEquals(1, c.ultraBeasts); assertEquals(2, c.shinies); assertEquals(1, c.legendaries); assertEquals(1, c.mythicals); assertEquals(1, c.regionalForms)
        assertEquals(0, CollectionStats.of(data, GameState(), 0L).ultraBeasts)
    }

    @Test fun dexProgressIsCaughtPercentPerGeneration() {
        val s = build(ChartMetric.DEX_PROGRESS, ChartPeriod.D30, collection = collection())
        assertEquals(ChartKind.CATEGORY, s.kind)
        assertEquals(listOf("Gen 1", "Gen 2", "Gen 4", "Gen 7"), s.bars.map { it.label })
        assertEquals(listOf(33.0, 100.0, 0.0, 100.0), s.bars.map { it.values.single() })
        assertEquals(50.0, s.total, 0.0)                                  // 3 of the 6 base species
        assertEquals(100.0, s.best, 0.0); assertEquals("Gen 2", s.bestLabel)
        assertEquals("%", s.unit); assertFalse(s.empty); assertFalse(s.deckApplies)
        assertEquals(listOf("Gen 1 · 1 of 3 caught (33 %)", "Seen 2 (67 %)"), s.bars[0].detail)
        assertEquals(listOf("Gen 4 · 0 of 1 caught (0 %)", "Seen 1 (100 %)"), s.bars[2].detail)
    }

    @Test fun pokemonPerTypeCountsCaughtSpeciesPerType() {
        val s = build(ChartMetric.PER_TYPE, ChartPeriod.D30, collection = collection())
        assertEquals(listOf("Fire", "Water", "Grass", "Poison", "Flying"), s.bars.map { it.label })
        assertEquals(listOf(0.0, 1.0, 1.0, 2.0, 0.0), s.bars.map { it.values.single() })
        assertEquals(3.0, s.total, 0.0)                                   // the 3 caught species; a dual type counts in two bars
        assertEquals(2.0, s.best, 0.0); assertEquals("Poison", s.bestLabel)
        assertEquals("species", s.unit)
        assertEquals(listOf("Poison · 2 of 2 caught (100 %)", "Seen 2 (100 %)"), s.bars[3].detail)
        assertEquals(listOf("Fire · 0 of 1 caught (0 %)", "Seen 0 (0 %)"), s.bars[0].detail)
    }

    @Test fun shiniesAndRareAreCountsOfOwnedPokemon() {
        val s = build(ChartMetric.SHINIES_RARE, ChartPeriod.D30, collection = collection())
        assertEquals(listOf("Shinies", "Legendaries", "Mythicals", "Ultra Beasts", "Regional forms"), s.bars.map { it.label })
        assertEquals(listOf(2.0, 1.0, 1.0, 1.0, 1.0), s.bars.map { it.values.single() })
        assertEquals(6.0, s.total, 0.0)                                   // the Pokémon owned
        assertEquals(2.0, s.best, 0.0); assertEquals("Shinies", s.bestLabel)
        assertEquals(listOf("Shinies · 2", "33 % of your 6 Pokémon"), s.bars[0].detail)
        assertEquals(listOf("Ultra Beasts · 1", "17 % of your 6 Pokémon"), s.bars[3].detail)
        assertEquals("Pokémon", s.unit)
    }

    @Test fun noRareOnesIsAnEmptyChartWithZeroBars() {
        val st = GameState(); st.mons += TestData.mon(data, 2, 5, uid = "x")
        val s = build(ChartMetric.SHINIES_RARE, ChartPeriod.D30, collection = CollectionStats.of(data, st, 0L))
        assertTrue(s.empty); assertEquals(5, s.bars.size); assertNull(s.bestLabel)
        assertEquals(listOf("Shinies · 0"), s.bars[0].detail)
    }

    @Test fun collectionChartsWithoutACollectionAreEmptyNotACrash() {
        for (m in listOf(ChartMetric.DEX_PROGRESS, ChartMetric.PER_TYPE, ChartMetric.SHINIES_RARE)) {
            val s = build(m, ChartPeriod.D30)
            assertTrue("$m", s.empty); assertEquals("$m", 0.0, s.total, 0.0); assertEquals("No collection yet", s.source)
        }
        val fresh = build(ChartMetric.DEX_PROGRESS, ChartPeriod.D30, collection = CollectionStats.of(data, GameState(), 0L))
        assertTrue(fresh.empty); assertEquals(4, fresh.bars.size); assertEquals(0.0, fresh.total, 0.0)
    }

    // ---------------------------------------------------------------- everything together

    @Test fun everyMetricPeriodAndGrainBuildsWithoutCrashing() {
        val snap = StatsSnapshot(stats = anki(mapOf(date(0) to 3), counts = counts), timeByDay = mapOf(date(0) to 1L), access = AnkiAccess.OK)
        val h = mapOf(date(0) to log(reviews = 1, spent = 5, heal = 1, buttons = listOf(0, 1, 1, 1, 1)))
        val c = collection()
        for (m in ChartMetric.values()) for (p in ChartPeriod.values()) for (g in listOf(null) + ChartGrain.values().toList()) {
            val s = build(m, p, snap, h, grain = g, collection = c)
            assertTrue("$m $p $g", s.bars.isNotEmpty())
            assertEquals("$m $p $g", m.seriesNames.size, s.bars.first().values.size)
            assertEquals(m.kind, s.kind)
            if (m.kind == ChartKind.TIME) {
                assertTrue("$m $p $g", s.grain in ChartData.effectivePeriod(m, p).grains)
                if (!s.loading) assertEquals("$m $p $g", s.bars.size, s.bars.count { it.detail.isNotEmpty() })
            }
            assertNotNull(s.bars.first().detail)
        }
    }

    @Test fun aBadTodayStillGivesAnEmptySeriesForEveryKind() {
        for (m in ChartMetric.values()) {
            val s = ChartData.build(m, ChartPeriod.D7, null, StatsSnapshot(), emptyMap(), "nope")
            assertTrue("$m", s.empty); assertTrue("$m", s.bars.isEmpty())
        }
    }
}
