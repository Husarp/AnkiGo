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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/*
 * R18-E: the "Show" sheet without the six removed charts (item 233) and the streak calendar with all seven weekday letters, a
 * bit more space under them and no hint at the top right (item 234). PNGs: r18e-sheet, r18e-cal-d7 / -d30 / -d90 / -d365 (412 dp)
 * and r18e-cal-narrow-d7 / -d30 / -d90 / -d365 (320 dp). Light + dark.
 */
class R18EStatsShots {
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

    private fun cal(name: String, period: ChartPeriod, v: Variant) {
        paparazzi.shootPage(name, listOf(v)) { ctx: Context ->
            prepare()
            AnkiStatsStore.snapshot = StatsSnapshot()
            val page = StatsPage(ctx, NoHost)
            page.preset(metric = ChartMetric.STREAK_CALENDAR, period = period)
            pageScreen(ctx, page) as View
        }
    }

    @Test fun sheet() {
        paparazzi.shootPage("r18e-sheet", listOf(Variants.PHONE)) { ctx: Context ->
            prepare()
            val page = StatsPage(ctx, NoHost)
            page.preset(sheet = true)
            pageScreen(ctx, page) as View
        }
    }

    /** Item 222: a tab tapped again scrolls to the top and must not reset the chart's choice. */
    @Test fun reselectKeepsTheChoice() {
        paparazzi.shootPage("r18e-reselect", listOf(Variants.PHONE)) { ctx: Context ->
            prepare()
            val page = StatsPage(ctx, NoHost)
            page.preset(metric = ChartMetric.XP, period = ChartPeriod.D90)
            val screen = pageScreen(ctx, page) as View
            val before = page.choice()
            page.onTabReselected()
            assertEquals(before, page.choice())
            screen
        }
    }

    @Test fun days7() = cal("r18e-cal-d7", ChartPeriod.D7, wide)
    @Test fun days30() = cal("r18e-cal-d30", ChartPeriod.D30, wide)
    @Test fun days90() = cal("r18e-cal-d90", ChartPeriod.D90, wide)
    @Test fun days365() = cal("r18e-cal-d365", ChartPeriod.D365, wide)
    @Test fun narrow7() = cal("r18e-cal-narrow-d7", ChartPeriod.D7, narrow)
    @Test fun narrow30() = cal("r18e-cal-narrow-d30", ChartPeriod.D30, narrow)
    @Test fun narrow90() = cal("r18e-cal-narrow-d90", ChartPeriod.D90, narrow)
    @Test fun narrow365() = cal("r18e-cal-narrow-d365", ChartPeriod.D365, narrow)
}
