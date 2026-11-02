package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.DayLog
import com.husarp.ankigo.stats.*
import com.husarp.ankigo.ui.StatsPage
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate

class R28LStatsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @Before fun off() { AnkiStatsStore.enabled = false }
    @After fun on() { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }
    private fun texts(v: View): List<String> = (if (v is TextView) listOf(v.text.toString()) else emptyList()) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList()
    private fun shot(metric: ChartMetric, sheet: Boolean = false) {
        paparazzi.shootPage("l-${metric.name.lowercase()}${if(sheet) "-picker" else ""}", listOf(Variant("360",360,2100))) { ctx ->
            val s = GameRepo.state!!
            val today = LocalDate.parse(GameRepo.game!!.dayKey())
            s.day = today.toString(); s.history.clear()
            for (i in 0..364) s.history[today.minusDays(i.toLong()).toString()] = DayLog(reviews = if (metric == ChartMetric.STREAK_CALENDAR && i % 3 == 0) 0 else 40 + i % 80, dailyGoal = 50,
                buttons = intArrayOf(0, 5, 10, 60, 15))
            val a = AnkiStats("fake", 0, today.toString(), s.history.mapValues { it.value.reviews }, emptyMap(), emptyMap(),
                Counts(new = 100, learning = 10, young = 200, mature = 500), 42, List(30) { 10 + it }, 0, 0, 1L)
            AnkiStatsStore.snapshot = StatsSnapshot(stats = a, access = AnkiAccess.OK,
                timeByDay = s.history.mapValues { 60000L }, liveTimeByDay = s.history.mapValues { 120000L },
                timedAnswersByDay = s.history.mapValues { 30L },
                series = mapOf(SeriesKey(null, ChartMetric.NEW_CARDS, 30, today.toString()) to
                    SeriesData(perDay = s.history.mapValues { 5 }, fetchedAt = 1L)))
            val page = StatsPage(ctx, NoHost)
            page.preset(metric = metric, sheet = sheet, period = ChartPeriod.D30)
            val screen = pageScreen(ctx, page)
            StatsPage::class.java.getDeclaredMethod("render").apply { isAccessible = true }.invoke(page)
            val t = texts(screen)
            if (metric == ChartMetric.STREAK_CALENDAR) {
                assertTrue(t.any { it.contains("20 study days") })
                assertTrue(t.any { it.startsWith("Last 30 days ·") && it.endsWith("per study day") })
            }
            assertTrue(t.contains("Cards today")); assertTrue(t.contains("Due now"))
            assertTrue(t.contains("GAME · ALL TIME")); assertTrue(t.contains("Oldest 9 Oct 2025" ) || t.any { it.startsWith("Oldest ") })
            for (removed in listOf("Damage dealt", "Duplicates", "Retention (est.)", "Most used ball", "Evolutions", "Reviews"))
                assertFalse(removed, t.contains(removed))
            screen
        }
    }
    @Test fun wholeScreen() = shot(ChartMetric.REVIEWS)
    @Test fun todayAnswers() = shot(ChartMetric.BUTTONS)
    @Test fun combinedTime() = shot(ChartMetric.TIME)
    @Test fun dueForecast() = shot(ChartMetric.DUE)
    @Test fun calendar() = shot(ChartMetric.STREAK_CALENDAR)
    @Test fun cardStates() = shot(ChartMetric.CARD_STATES)
    @Test fun newCards() = shot(ChartMetric.NEW_CARDS)
    @Test fun sevenChoices() = shot(ChartMetric.REVIEWS, true)
}
