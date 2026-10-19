package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.DayLog
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
 * R9-W2-D5: the Stats streak calendar per period (7 days strip, 30 days rows with a row per month, 90 days and a year as week
 * columns). PNGs: r9cal-d7, r9cal-d30, r9cal-d90, r9cal-d365 (412 dp wide) and r9cal-narrow-d7 / -d30 / -d90 / -d365 (320 dp),
 * one with a selected day each. Light + dark.
 */
class R9CalendarShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Before fun storeOff() { AnkiStatsStore.enabled = false; AnkiStatsStore.snapshot = StatsSnapshot() }
    @After fun storeOn() { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }

    private val wide = Variant("w412", 412, 1150)
    private val narrow = Variant("w320", 320, 1150)

    private fun prepare(): LocalDate {
        val g = GameRepo.game!!
        val s = GameRepo.state!!
        val today = LocalDate.parse(g.dayKey())
        s.day = today.toString(); s.reviewsToday = 86
        val rnd = Random(9)
        s.history.clear()
        for (i in 0 until 400) {
            val reviews = when {
                i == 0 -> 86
                i in 20..24 || i in 47..52 -> 0
                rnd.nextInt(10) < 2 -> 0
                else -> 8 + rnd.nextInt(110)
            }
            if (reviews == 0) continue
            s.history[today.minusDays(i.toLong()).toString()] = DayLog(reviews = reviews, won = reviews / 9, xp = reviews * 14, cash = reviews * 6)
        }
        return today
    }

    private fun shoot(name: String, period: ChartPeriod, v: Variant, pickBack: Int? = null) {
        paparazzi.shootPage(name, listOf(v)) { ctx: Context ->
            val today = prepare()
            AnkiStatsStore.snapshot = StatsSnapshot()
            val page = StatsPage(ctx, NoHost)
            page.preset(metric = ChartMetric.STREAK_CALENDAR, period = period, heatDay = pickBack?.let { today.minusDays(it.toLong()).toString() })
            pageScreen(ctx, page) as View
        }
    }

    @Test fun days7() = shoot("r9cal-d7", ChartPeriod.D7, wide, 2)
    @Test fun days30() = shoot("r9cal-d30", ChartPeriod.D30, wide)
    @Test fun days90() = shoot("r9cal-d90", ChartPeriod.D90, wide, 5)
    @Test fun days365() = shoot("r9cal-d365", ChartPeriod.D365, wide)
    @Test fun narrow7() = shoot("r9cal-narrow-d7", ChartPeriod.D7, narrow)
    @Test fun narrow30() = shoot("r9cal-narrow-d30", ChartPeriod.D30, narrow, 3)
    @Test fun narrow90() = shoot("r9cal-narrow-d90", ChartPeriod.D90, narrow)
    @Test fun narrow365() = shoot("r9cal-narrow-d365", ChartPeriod.D365, narrow, 100)
}
