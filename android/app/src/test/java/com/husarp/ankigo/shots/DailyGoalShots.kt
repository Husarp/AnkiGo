package com.husarp.ankigo.shots

import android.content.Context
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.DailyGoalPicker
import com.husarp.ankigo.ui.Pickers
import org.junit.Rule
import org.junit.Test

/* R6-04: the Settings daily-goal picker, "Same every day" and "Per weekday", light + dark. Names: dailygoal-<mode>-<size>-<theme>. */
class DailyGoalShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun dialog(name: String, perWeekday: Boolean) {
        paparazzi.shootPage(name) { ctx: Context ->
            val s = com.husarp.ankigo.game.Settings().also {
                it.dailyAverage = 100
                if (perWeekday) it.goalByWeekday = listOf(80, 100, 100, 120, 150, 40, 20)
            }
            val d = AppDialogShots.capture { DailyGoalPicker.show(ShotCtx(ctx), s, "2026-10-14", { f -> f(s) }) {} }
                ?: error("no dialog")
            AppDialogShots.scene(ctx, d)
        }
    }

    @Test fun same() = dialog("dailygoal-same", false)
    @Test fun perWeekday() = dialog("dailygoal-weekday", true)
}
