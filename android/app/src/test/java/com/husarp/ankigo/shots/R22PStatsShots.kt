package com.husarp.ankigo.shots

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.DayLog
import com.husarp.ankigo.stats.*
import com.husarp.ankigo.ui.StatsPage
import org.junit.*
import java.time.LocalDate

class R22PStatsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @Before fun off() { AnkiStatsStore.enabled = false; AnkiStatsStore.snapshot = StatsSnapshot() }
    @After fun on() { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }
    private fun shot(year: Boolean) {
        paparazzi.shootPage(if (year) "r22p-year" else "r22p-reviews", listOf(Variant("w320", 320, 1150))) { ctx ->
            val s = GameRepo.state!!
            val today = LocalDate.parse(GameRepo.game!!.dayKey())
            s.day = today.toString(); s.history.clear()
            for (i in 0..390) s.history[today.minusDays(i.toLong()).toString()] = DayLog(reviews = (i * 17) % 101, dailyGoal = 50)
            val page = StatsPage(ctx, NoHost)
            page.preset(metric = if (year) ChartMetric.STREAK_CALENDAR else ChartMetric.REVIEWS, period = if (year) ChartPeriod.D365 else ChartPeriod.D30, calendarYear = if (year) today.year else null)
            pageScreen(ctx, page)
        }
    }
    @Test fun goalAboveAndBelowBars() = shot(false)
    @Test fun yearChipsAndWholeYear() = shot(true)
}
