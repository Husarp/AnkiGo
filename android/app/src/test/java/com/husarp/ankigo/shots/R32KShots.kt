package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.DayLog
import com.husarp.ankigo.stats.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R32KShots {
    @get:Rule val paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun rollingPeriodsAt320dp() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("p320", 320, 1100).device(theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.scenario = FakeGame.Scenario.MID_GAME
            FakeGame.install(ctx)
            AnkiStatsStore.enabled = false
            try {
                val state = GameRepo.state!!
                val today = java.time.LocalDate.parse(GameRepo.game!!.dayKey())
                state.day = today.toString()
                state.history.clear()
                for (i in 0..299) state.history[today.minusDays(i.toLong()).toString()] =
                    DayLog(buttons = intArrayOf(0, 2 + i % 3, 3, 8 + i % 5, 4))
                for (period in ChartPrefs.periodsFor(ChartMetric.BUTTONS)) {
                    val page = StatsPage(ctx, NoHost)
                    page.preset(metric = ChartMetric.BUTTONS, period = period)
                    val screen = pageScreen(ctx, page)
                    call(page, "render")
                    paparazzi.snapshot(screen, "k-buttons-${period.days}-${theme.name.lowercase()}", 1000)
                    val chip = all(screen).filterIsInstance<TextView>().first { it.text.toString() == "Last ${period.days} days" }
                    assertEquals(0, chip.layout.getEllipsisCount(0))
                    assertTrue(chip.right <= (chip.parent as View).width)
                }
            } finally { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }
        }
    }
}
