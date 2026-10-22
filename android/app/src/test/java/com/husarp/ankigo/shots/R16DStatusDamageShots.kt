package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.FloatKind
import com.husarp.ankigo.ui.FloatText
import com.husarp.ankigo.ui.FloatTextView
import com.husarp.ankigo.ui.FloatTexts
import com.husarp.ankigo.ui.LogKind
import com.husarp.ankigo.ui.Ui
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * PLAN 217: status damage floats in the status colour with its name ("-4 HP Burning" / "Poisoning").
 * PNGs: r16d-gallery (static, normal hit next to burn / poison / plain), r16d-arena-<size>-<theme> (floats on a real battle panel; the
 * narrow sizes assert that every float stays inside the arena).
 */
class R16DStatusDamageShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private fun gallery(ctx: Context): View {
        fun tv(k: FloatKind, text: String, stat: String? = null) = FloatTextView(ctx, FloatText(false, text, k, stat), 0f)
        val rows = listOf(
            listOf(tv(FloatKind.DAMAGE, FloatTexts.loss(12)), tv(FloatKind.BIG_DAMAGE, FloatTexts.loss(128))),
            listOf(tv(FloatKind.STATUS_DAMAGE, FloatTexts.loss(4) + " Burning", "brn"), tv(FloatKind.STATUS_DAMAGE, FloatTexts.loss(11) + " Poisoning", "psn")),
            listOf(tv(FloatKind.STATUS_DAMAGE, FloatTexts.loss(128) + " Burning", "brn"), tv(FloatKind.DAMAGE, FloatTexts.loss(4))),
        )
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Ui.c(ctx, R.color.ag_arena_top), Ui.c(ctx, R.color.ag_arena_bot)))
        }
        for (r in rows) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            for (v in r) row.addView(v, LinearLayout.LayoutParams(-2, -2).also { it.setMargins(Ui.dp(ctx, 5), Ui.dp(ctx, 3), Ui.dp(ctx, 5), Ui.dp(ctx, 3)) })
            col.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        return col
    }

    @Test fun gallery() = paparazzi.shootWith("r16d-gallery", listOf(Size.PHONE, Size.PIP_11), sprites = false) { ctx -> gallery(ctx) }

    private fun arena(size: Size, compact: Boolean) {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
            val p = BattlePanelView(ctx)
            p.setCompact(compact); p.renderAll()
            p.setLog(LogKind.GOOD, "Good", "Sparkmouse used {{Thunderbolt}}!", false)
            var done = false
            var floats = emptyList<FloatTextView>()
            p.arena.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                if (!done && v.width > 0 && v.height > 0) {
                    done = true
                    p.arena.floatText(FloatText(false, FloatTexts.loss(12), FloatKind.DAMAGE))
                    p.arena.floatText(FloatText(false, FloatTexts.loss(4) + " Burning", FloatKind.STATUS_DAMAGE, "brn"))
                    p.arena.floatText(FloatText(true, FloatTexts.loss(11) + " Poisoning", FloatKind.STATUS_DAMAGE, "psn"))
                    p.arena.measure(View.MeasureSpec.makeMeasureSpec(p.arena.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(p.arena.height, View.MeasureSpec.EXACTLY))
                    p.arena.layout(p.arena.left, p.arena.top, p.arena.right, p.arena.bottom)
                    floats = (0 until p.arena.childCount).map { p.arena.getChildAt(it) }.filterIsInstance<FloatTextView>()
                }
            }
            paparazzi.snapshot(p, "r16d-arena-${size.name.lowercase()}-${theme.name.lowercase()}", 850)
            assertTrue("floats were fired", done)
            assertEquals(3, floats.size)
            for (f in floats) {
                assertTrue("${f.ft.text} inside the arena ($size $theme): ${f.left},${f.top},${f.right},${f.bottom} of ${p.arena.width}x${p.arena.height}",
                    f.left >= 0 && f.top >= 0 && f.right <= p.arena.width && f.bottom <= p.arena.height)
            }
        }
    }

    @Test fun arenaPhone() = arena(Size.PHONE, compact = false)
    @Test fun arenaFloatBattleNarrow() = arena(Size.FLOATB, compact = true)
    @Test fun arenaPip11Narrowest() = arena(Size.PIP_11, compact = true)
}
