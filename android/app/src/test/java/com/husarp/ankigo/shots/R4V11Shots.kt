package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.DayLog
import com.husarp.ankigo.game.Metric
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.ChartMetric
import com.husarp.ankigo.stats.ChartPeriod
import com.husarp.ankigo.stats.StatsSnapshot
import com.husarp.ankigo.ui.StatsPage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/*
 * R4-V11: the four battle charts (damage dealt / taken / blocked, misses) and the All time card with the battle tiles.
 * PNGs: r4v11-dealt, r4v11-taken, r4v11-blocked, r4v11-misses (30 days, one bar tapped), r4v11-dealt-week (90 days),
 * r4v11-sheet (the metric list with the new chips), r4v11-alltime (the whole page). Light + dark.
 */
class R4V11Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Before fun storeOff() { AnkiStatsStore.enabled = false; AnkiStatsStore.snapshot = StatsSnapshot() }
    @After fun storeOn() { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }

    /** 60 days of made-up study; the battle numbers only exist for the last 45 days (the update came in between). */
    private fun prepare() {
        val g = GameRepo.game!!
        val s = GameRepo.state!!
        val today = LocalDate.parse(g.dayKey())
        s.day = today.toString(); s.reviewsToday = 86; s.streakDays = 12; s.bestStreakDays = 20
        s.lastGoalDay = today.minusDays(1).toString()
        val rnd = Random(9)
        s.history.clear()
        for (i in 0 until 60) {
            val reviews = if (i % 7 == 3) 0 else 20 + rnd.nextInt(90)
            if (reviews == 0) continue
            val battle = i < 45
            s.history[today.minusDays(i.toLong()).toString()] = DayLog(
                reviews = reviews, won = reviews / 9, xp = reviews * 14, cash = reviews * 6,
                dmgDealt = if (battle) reviews * 11 + rnd.nextInt(80) else 0, dmgTaken = if (battle) reviews * 6 + rnd.nextInt(40) else 0,
                dmgBlocked = if (battle) reviews * 2 + rnd.nextInt(20) else 0, misses = if (battle) reviews / 8 else 0,
            )
        }
        val h = s.history.values
        s.counters[Metric.DAMAGE_DEALT.name] = h.sumOf { it.dmgDealt.toLong() }
        s.counters[Metric.DAMAGE_TAKEN.name] = h.sumOf { it.dmgTaken.toLong() }
        s.counters[Metric.DAMAGE_BLOCKED.name] = h.sumOf { it.dmgBlocked.toLong() }
        s.counters[Metric.MISSES.name] = h.sumOf { it.misses.toLong() }
    }

    private fun shoot(name: String, variants: List<Variant> = listOf(Variants.PHONE), setup: (StatsPage) -> Unit) {
        paparazzi.shootPage(name, variants) { ctx: Context ->
            prepare()
            val page = StatsPage(ctx, NoHost)
            setup(page)
            pageScreen(ctx, page) as View
        }
    }

    @Test fun dealt() = shoot("r4v11-dealt") { it.preset(metric = ChartMetric.DMG_DEALT, period = ChartPeriod.D30, bar = 26) }
    @Test fun taken() = shoot("r4v11-taken") { it.preset(metric = ChartMetric.DMG_TAKEN, period = ChartPeriod.D30, bar = 26) }
    @Test fun blocked() = shoot("r4v11-blocked") { it.preset(metric = ChartMetric.DMG_BLOCKED, period = ChartPeriod.D30, bar = 26) }
    @Test fun misses() = shoot("r4v11-misses") { it.preset(metric = ChartMetric.MISSES, period = ChartPeriod.D30, bar = 26) }
    @Test fun dealtThreeMonths() = shoot("r4v11-dealt-week") { it.preset(metric = ChartMetric.DMG_DEALT, period = ChartPeriod.D90, bar = 5) }
    @Test fun metricSheet() = shoot("r4v11-sheet") { it.preset(sheet = true) }
    @Test fun wholePage() = shoot("r4v11-alltime", listOf(Variants.TALL)) { it.preset(metric = ChartMetric.DMG_TAKEN, period = ChartPeriod.D30) }
}
