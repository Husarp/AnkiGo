package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.ActionBarModel
import com.husarp.ankigo.ui.FloatKind
import com.husarp.ankigo.ui.FloatText
import com.husarp.ankigo.ui.FloatTextView
import com.husarp.ankigo.ui.Ui
import org.junit.Rule
import org.junit.Test

/** R14-I (PLAN 172): the three "can't move" floats with their status badge, next to a damage number for size. PNG: r14i-cantmove. */
class R14CantMoveShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun gallery(ctx: Context): View {
        fun tv(k: FloatKind, t: String, st: String? = null) = FloatTextView(ctx, FloatText(true, t, k, st), 0f)
        val rows = listOf(
            listOf(tv(FloatKind.DAMAGE, "−12 HP"), tv(FloatKind.STATUS, "Burned")),
            listOf(tv(FloatKind.STATUS, "Fast asleep", "slp")),
            listOf(tv(FloatKind.STATUS, "Fully paralyzed", "par")),
            listOf(tv(FloatKind.STATUS, "Frozen solid", "frz")),
        )
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Ui.c(ctx, R.color.ag_arena_top), Ui.c(ctx, R.color.ag_arena_bot)))
        }
        for (r in rows) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            for (v in r) row.addView(v, LinearLayout.LayoutParams(-2, -2).also { it.setMargins(Ui.dp(ctx, 5), Ui.dp(ctx, 6), Ui.dp(ctx, 5), Ui.dp(ctx, 6)) })
            col.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        return col
    }

    @Test fun cantMove() = paparazzi.shootWith("r14i-cantmove", listOf(Size.PHONE)) { ctx -> gallery(ctx) }
}
