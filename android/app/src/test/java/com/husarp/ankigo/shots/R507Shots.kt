package com.husarp.ankigo.shots

import android.content.Context
import android.widget.RemoteViews
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.WidgetRender
import com.husarp.ankigo.ui.WidgetSnapshot
import org.junit.Rule
import org.junit.Test

/* R5-07: the 1x1 Study now widget (64I) at 70x70 and 100x100 dp, light and dark: 87 due, nothing due, 1234 due, unreadable. */
class R507Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val frame = WidgetShotsFrame()

    private fun view(ctx: Context, due: Int?, s: Int): RemoteViews {
        val k = WidgetRender.Kind.STUDY_NOW
        val snap = WidgetSnapshot(null, false, 0, 0, 0, false, 0, 0, emptyList(), due = due)
        val rv = RemoteViews(ctx.packageName, k.layout)
        rv.setImageViewBitmap(k.imgLight, WidgetRender.render(ctx, k, snap, s.toFloat(), s.toFloat(), night = false))
        rv.setImageViewBitmap(k.imgDark, WidgetRender.render(ctx, k, snap, s.toFloat(), s.toFloat(), night = true))
        return rv
    }

    private fun all(name: String, due: Int?) {
        for (s in listOf(70, 100)) frame.shoot(paparazzi, name, s, s) { view(it, due, s) }
    }

    @Test fun due87() = all("r507-due87", 87)
    @Test fun done() = all("r507-done", 0)
    @Test fun due1234() = all("r507-due1234", 1234)
    @Test fun unknown() = all("r507-unknown", null)
}
