package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.DayLog
import com.husarp.ankigo.stats.*
import com.husarp.ankigo.ui.StatsPage
import com.husarp.ankigo.ui.Ui
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate

/** PLAN 367: controls stay beside their chart, after the summary; cards never touch. */
class R24DStatsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @Before fun off() { AnkiStatsStore.enabled = false; AnkiStatsStore.snapshot = StatsSnapshot() }
    @After fun on() { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }

    private fun descendants(v: View): List<View> = listOf(v) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()

    private fun shot(metric: ChartMetric, empty: Boolean = false) {
        paparazzi.shootPage("r24d-${metric.name.lowercase()}${if (empty) "-empty" else ""}",
            listOf(Variants.PHONE, Variant("narrow", 320, 900))) { ctx ->
            val today = LocalDate.parse(GameRepo.game!!.dayKey())
            val state = GameRepo.state!!
            state.day = today.toString(); state.history.clear()
            if (!empty) for (i in 0..6) {
                state.history[today.minusDays(i.toLong()).toString()] = DayLog(reviews = 100).apply {
                    buttons[1] = 3 + i; buttons[3] = 97 - i
                }
            }
            val page = StatsPage(ctx, NoHost)
            page.preset(metric = metric, period = ChartPeriod.D7)
            val screen = pageScreen(ctx, page)
            StatsPage::class.java.getDeclaredMethod("render").apply { isAccessible = true }.invoke(page)
            val picker = descendants(screen).single { it.contentDescription == "${metric.label}, tap to change the chart" }
            val content = picker.parent as LinearLayout
            val index = content.indexOfChild(picker)
            assertEquals("summary precedes controls when available", if (empty) 2 else 3, index)
            assertEquals(Ui.dp(ctx, 14), (picker.layoutParams as LinearLayout.LayoutParams).topMargin)
            if (!empty) assertTrue(descendants(content.getChildAt(index - 1)).any { it is TextView && it.text.contains(if (metric == ChartMetric.RETENTION) "%" else "100") })
            assertTrue("filters follow picker", content.getChildAt(index + 1) is com.husarp.ankigo.ui.StatsSheets.FlowLayout)
            assertEquals("chart follows filters", Ui.dp(ctx, 8), (content.getChildAt(index + 2).layoutParams as LinearLayout.LayoutParams).topMargin)
            screen
        }
    }
    @Test fun retentionSummaryBeforeControls() = shot(ChartMetric.RETENTION)
    @Test fun reviewsSummaryBeforeControls() = shot(ChartMetric.REVIEWS)
    @Test fun emptyRetentionStillHasStreakGap() = shot(ChartMetric.RETENTION, true)
}
