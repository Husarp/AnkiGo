package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.ArenaView
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

/** R9-W6-6: "+3 HP" / "−128 HP" floats. PNGs: r9w6-hp-kinds (static gallery), r9w6-hp-arena (floats in the narrow float-battle arena, font scale 2.0). */
class R9W6HpUnitsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private fun gallery(ctx: Context): View {
        fun tv(k: FloatKind, text: String) = FloatTextView(ctx, FloatText(false, text, k), 0f)
        val rows = listOf(
            listOf(tv(FloatKind.HEAL, FloatTexts.heal(3)), tv(FloatKind.DAMAGE, FloatTexts.loss(12))),
            listOf(tv(FloatKind.BIG_DAMAGE, FloatTexts.loss(128)), tv(FloatKind.HEAL, FloatTexts.heal(128))),
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

    @Test fun kinds() = paparazzi.shootWith("r9w6-hp-kinds", listOf(Size.PHONE, Size.PIP)) { ctx -> gallery(ctx) }

    @Test fun floatsStayInsideTheNarrowArenaAtFontScale2() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.FLOATBATTLE, theme).copy(fontScale = 2.0f))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
            val p = BattlePanelView(ctx)
            p.setCompact(true); p.renderAll()
            p.setLog(LogKind.GOOD, "Good", "Sparkmouse used {{Thunderbolt}}!", false)
            var done = false
            var floats = emptyList<FloatTextView>()
            p.arena.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                if (!done && v.width > 0 && v.height > 0) {
                    done = true
                    p.arena.floatText(FloatText(false, FloatTexts.loss(128), FloatKind.BIG_DAMAGE))
                    p.arena.floatText(FloatText(true, FloatTexts.heal(3), FloatKind.HEAL))
                    p.arena.floatText(FloatText(true, FloatTexts.loss(12), FloatKind.DAMAGE))
                    p.arena.measure(View.MeasureSpec.makeMeasureSpec(p.arena.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(p.arena.height, View.MeasureSpec.EXACTLY))
                    p.arena.layout(p.arena.left, p.arena.top, p.arena.right, p.arena.bottom)
                    floats = (0 until p.arena.childCount).map { p.arena.getChildAt(it) }.filterIsInstance<FloatTextView>()
                }
            }
            paparazzi.snapshot(p, "r9w6-hp-arena-${theme.name.lowercase()}", 850)
            assertTrue("floats were fired", done)
            val arena: ArenaView = p.arena
            assertEquals(3, floats.size)
            for (f in floats) {
                assertTrue("${f.ft.text} inside the arena ($theme): ${f.left},${f.top},${f.right},${f.bottom} of ${arena.width}x${arena.height}",
                    f.left >= 0 && f.top >= 0 && f.right <= arena.width && f.bottom <= arena.height)
            }
        }
    }
}
