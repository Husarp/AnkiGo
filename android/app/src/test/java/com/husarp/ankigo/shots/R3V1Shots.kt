package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.DayLog
import com.husarp.ankigo.stats.AnkiAccess
import com.husarp.ankigo.stats.AnkiStats
import com.husarp.ankigo.stats.ChartGrain
import com.husarp.ankigo.stats.ChartMetric
import com.husarp.ankigo.stats.ChartPeriod
import com.husarp.ankigo.stats.Counts
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.SeriesData
import com.husarp.ankigo.stats.SeriesKey
import com.husarp.ankigo.stats.StatsSnapshot
import com.husarp.ankigo.ui.StatsPage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/*
 * R3-V1 (pick 1): the Stats screen with the metric button, filter chips, key numbers, the interactive chart and the Collection.
 * PNGs: r3v1-default, r3v1-buttons, r3v1-time, r3v1-states, r3v1-types, r3v1-streak, r3v1-tap-*, r3v1-sheet, r3v1-due,
 * r3v1-retention, r3v1-empty, r3v1-denied, r3v1-loading, r3v1-deck. Light + dark, PHONE + SPLIT (+ TALL for the whole page).
 */
class R3V1Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Before fun storeOff() { AnkiStatsStore.enabled = false; AnkiStatsStore.snapshot = StatsSnapshot() }
    @After fun storeOn() { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }

    private val sizes = listOf(Variants.PHONE, Variants.SPLIT)

    /** ~100 days of made-up study with answer buttons, xp, cash and a gap. */
    private fun prepare(): LocalDate {
        val g = GameRepo.game!!
        val s = GameRepo.state!!
        val today = LocalDate.parse(g.dayKey())
        s.day = today.toString(); s.reviewsToday = 86; s.streakDays = 12; s.bestStreakDays = 20
        s.lastGoalDay = today.minusDays(1).toString()
        val rnd = Random(5)
        s.history.clear()
        for (i in 0 until 400) {
            val reviews = when {
                i == 0 -> 86
                i in 1..12 -> 100 + rnd.nextInt(60)
                i in 20..24 || i in 47..52 -> 0
                rnd.nextInt(10) < 2 -> 0
                else -> 8 + rnd.nextInt(90)
            }
            if (reviews == 0) continue
            val again = reviews * 12 / 100; val hard = reviews * 9 / 100; val easy = reviews * 16 / 100
            s.history[today.minusDays(i.toLong()).toString()] = DayLog(
                reviews = reviews, won = reviews / 9, caught = reviews / 35, xp = reviews * 14, cash = reviews * 6,
                buttons = intArrayOf(0, again, hard, reviews - again - hard - easy, easy), spent = if (i % 4 == 0) 300 else 0, healItems = i % 3,
            )
        }
        s.mons.take(3).forEach { it.shiny = true }
        return today
    }

    private fun anki(today: LocalDate): AnkiStats {
        val s = GameRepo.state!!
        val perDay = (0 until 365).associate { k -> today.minusDays(k.toLong()).toString() to (s.history[today.minusDays(k.toLong()).toString()]?.reviews ?: 0) }
        return AnkiStats(
            "com.ichi2.anki", System.currentTimeMillis() - 5 * 60_000, today.toString(), perDay,
            mapOf(30 to listOf(210, 380, 2900, 610)), mapOf(1 to 14, 7 to 96, 30 to 410),
            Counts(new = 1840, learning = 12, relearning = 5, young = 640, mature = 3120, suspended = 41, buried = 3),
            142, List(30) { 150 - 4 * it + (it % 5) * 3 }, 2600, 190, System.currentTimeMillis() - 60_000,
        )
    }

    private fun snap(today: LocalDate, access: AnkiAccess? = AnkiAccess.OK, withAnki: Boolean = true, live: Boolean = true) = StatsSnapshot(
        if (withAnki) anki(today) else null, access,
        timeByDay = (0 until 40).associate { today.minusDays(it.toLong()).toString() to (6 + (it * 7) % 23) * 60_000L },
        liveTimeByDay = if (live) (0 until 40).filter { it % 3 != 1 }.associate { today.minusDays(it.toLong()).toString() to (2 + (it * 5) % 11) * 60_000L } else emptyMap(),
    )

    private fun shoot(
        name: String, variants: List<Variant> = sizes, snapOf: (LocalDate) -> StatsSnapshot = { snap(it) },
        setup: (StatsPage, LocalDate) -> Unit = { _, _ -> },
    ) {
        paparazzi.shootPage(name, variants) { ctx: Context ->
            val today = prepare()
            AnkiStatsStore.snapshot = snapOf(today)
            val page = StatsPage(ctx, NoHost)
            setup(page, today)
            pageScreen(ctx, page) as View
        }
    }

    @Test fun defaultReviews() = shoot("r3v1-default", listOf(Variants.PHONE, Variants.SPLIT, Variants.TALL))
    @Test fun buttonsStacked() = shoot("r3v1-buttons") { p, _ -> p.preset(metric = ChartMetric.BUTTONS) }
    @Test fun timeStacked() = shoot("r3v1-time") { p, _ -> p.preset(metric = ChartMetric.TIME) }
    @Test fun cardStates() = shoot("r3v1-states") { p, _ -> p.preset(metric = ChartMetric.CARD_STATES) }
    @Test fun collectionTypes() = shoot("r3v1-types", listOf(Variants.PHONE, Variants.TALL)) { p, _ -> p.preset(metric = ChartMetric.PER_TYPE) }
    @Test fun collectionShinies() = shoot("r3v1-shinies", listOf(Variants.PHONE)) { p, _ -> p.preset(metric = ChartMetric.SHINIES_RARE, bar = 1) }
    @Test fun streakCalendar() = shoot("r3v1-streak", sizes) { p, t -> p.preset(metric = ChartMetric.STREAK_CALENDAR, period = ChartPeriod.D90, heatDay = t.minusDays(5).toString()) }
    @Test fun dueForecast() = shoot("r3v1-due") { p, _ -> p.preset(metric = ChartMetric.DUE, period = ChartPeriod.D30) }
    @Test fun retentionWithGaps() = shoot("r3v1-retention") { p, _ -> p.preset(metric = ChartMetric.RETENTION, period = ChartPeriod.D30, bar = 28) }
    @Test fun weeksOfAYear() = shoot("r3v1-weeks", listOf(Variants.PHONE)) { p, _ -> p.preset(metric = ChartMetric.XP, period = ChartPeriod.D365, grain = ChartGrain.WEEK, bar = 20) }
    @Test fun gameMoney() = shoot("r3v1-money", listOf(Variants.PHONE)) { p, _ -> p.preset(metric = ChartMetric.MONEY, period = ChartPeriod.D7, bar = 3) }

    // tapped bars with their info boxes
    @Test fun tappedStackedBar() = shoot("r3v1-tap-buttons", sizes) { p, _ -> p.preset(metric = ChartMetric.BUTTONS, bar = 27) }
    @Test fun tappedBarNearTheEdge() = shoot("r3v1-tap-edge", listOf(Variants.PHONE)) { p, _ -> p.preset(metric = ChartMetric.REVIEWS, bar = 29) }
    @Test fun tappedWeek() = shoot("r3v1-tap-week", listOf(Variants.PHONE)) { p, _ -> p.preset(metric = ChartMetric.BUTTONS, period = ChartPeriod.D90, bar = 6) }
    @Test fun tappedCategory() = shoot("r3v1-tap-states", sizes) { p, _ -> p.preset(metric = ChartMetric.CARD_STATES, bar = 4) }

    @Test fun metricSheet() = shoot("r3v1-sheet") { p, _ -> p.preset(sheet = true) }

    // empty / unreadable / loading
    @Test fun empty() = shoot("r3v1-empty", listOf(Variants.PHONE), snapOf = { snap(it, withAnki = false, live = false) }) { p, _ -> p.preset(metric = ChartMetric.CATCHES, period = ChartPeriod.D7) }
    @Test fun noAnkiDroid() = shoot("r3v1-denied", listOf(Variants.PHONE, Variants.SPLIT), snapOf = { snap(it, AnkiAccess.NO_PERMISSION, withAnki = false) }) { p, _ -> p.preset(metric = ChartMetric.DUE) }
    @Test fun deckLoading() = shoot("r3v1-loading", listOf(Variants.PHONE), snapOf = { snap(it) }) { p, _ -> p.preset(metric = ChartMetric.REVIEWS, deck = "Spanish::Verbs") }
    @Test fun deckWithItsNumbers() = shoot("r3v1-deck", listOf(Variants.PHONE), snapOf = { t ->
        val key = SeriesKey("Spanish::Verbs", ChartMetric.REVIEWS, 30, t.toString())
        snap(t).let { it.copy(series = mapOf(key to SeriesData((0 until 30).associate { k -> t.minusDays(k.toLong()).toString() to (k * 7) % 31 }, System.currentTimeMillis()))) }
    }) { p, _ -> p.preset(metric = ChartMetric.REVIEWS, deck = "Spanish::Verbs") }
}
