package com.husarp.ankigo.shots

import android.content.Context
import android.widget.RemoteViews
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.StudyNowBg
import com.husarp.ankigo.ui.WidgetRender
import com.husarp.ankigo.ui.WidgetSnapshot
import org.junit.Rule
import org.junit.Test

/* R5-07 + R6-08: the 1x1 Study now widget at 70x70 and 100x100 dp, light and dark: 87 due, nothing due, 1234 due, unreadable; each background (System / White / Black) and goal progress 0 % / 50 % / done. */
class R507Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val frame = WidgetShotsFrame()

    private fun view(ctx: Context, due: Int?, s: Int, today: Int = 0, bg: StudyNowBg = StudyNowBg.SYSTEM): RemoteViews {
        val k = WidgetRender.Kind.STUDY_NOW
        val snap = WidgetSnapshot(null, false, today, 50, 0, false, 0, 0, emptyList(), due = due)
        val rv = RemoteViews(ctx.packageName, k.layout)
        rv.setImageViewBitmap(k.imgLight, WidgetRender.render(ctx, k, snap, s.toFloat(), s.toFloat(), night = false, studyBg = bg))
        rv.setImageViewBitmap(k.imgDark, WidgetRender.render(ctx, k, snap, s.toFloat(), s.toFloat(), night = true, studyBg = bg))
        return rv
    }

    private fun all(name: String, due: Int?) {
        for (s in listOf(70, 100)) frame.shoot(paparazzi, name, s, s) { view(it, due, s) }
    }

    private fun goalShots(name: String, today: Int, due: Int?) {
        for (bg in StudyNowBg.values()) frame.shoot(paparazzi, "$name-${bg.key}", 70, 70) { view(it, due, 70, today, bg) }
    }

    @Test fun goal0() = goalShots("r608-goal0", 0, 87)
    @Test fun goal50() = goalShots("r608-goal50", 25, 87)
    @Test fun goalDone() = goalShots("r608-gdone", 50, 87)
    @Test fun goalDoneAllDone() = goalShots("r608-gdone-nodue", 50, 0)
    @Test fun goal50Big() = frame.shoot(paparazzi, "r608-goal50-big", 100, 100) { view(it, 1234, 100, 25, StudyNowBg.BLACK) }

    @Test fun due87() = all("r507-due87", 87)
    @Test fun done() = all("r507-done", 0)
    @Test fun due1234() = all("r507-due1234", 1234)
    @Test fun unknown() = all("r507-unknown", null)
}
