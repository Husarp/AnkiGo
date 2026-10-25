package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.EffectKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.ActionBarModel
import com.husarp.ankigo.ui.BarSide
import com.husarp.ankigo.ui.FloatKind
import com.husarp.ankigo.ui.FloatText
import com.husarp.ankigo.ui.FloatTextView
import com.husarp.ankigo.ui.HalfState
import com.husarp.ankigo.ui.MoveBubble
import com.husarp.ankigo.ui.MoveBubbleLook
import com.husarp.ankigo.ui.StatPillView
import com.husarp.ankigo.ui.Ui
import org.junit.Rule
import org.junit.Test

/**
 * R19-C: the stat-change pill (mockup A1-C). PNGs r19c-pills-*: the move bubble (both looks) with +1 Spe, +2 Atk, -1 Def, -3 Acc;
 * the three stage counts of each direction as bare pills; the floating badges up and down on a light and a dark backdrop.
 * Light + dark theme.
 */
class R19CStatPillShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun bubbleOf(): com.husarp.ankigo.ui.BubbleContent {
        val m = ActionBarModel()
        m.observe(GameEvent.Attack(true, "Sparkmouse", "Gnarlpup", "Swords Dance", Type.NORMAL, Category.STATUS, 0, 1, 1.0, false, false, false, 1, 10, 10, 10))
        for ((s, n) in listOf("spe" to 1, "atk" to 2, "def" to -1, "accuracy" to -3)) m.observe(GameEvent.Effect(true, "x", 5, 10, null, EffectKind.STAT_CHANGE, s, n))
        return ActionBarModel.bubble((m.half(BarSide.MINE) as HalfState.Moved).move)
    }

    private fun label(ctx: Context, t: String) = TextView(ctx).apply { text = t; textSize = 11f; setTextColor(Ui.c(ctx, R.color.ag_sub0)); setPadding(0, Ui.dp(ctx, 8), 0, Ui.dp(ctx, 3)) }

    private fun row(ctx: Context, vararg v: View) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        for (x in v) addView(x, LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = Ui.dp(ctx, 6) })
    }

    private fun float(ctx: Context, up: Boolean, stat: String, short: String, n: Int) =
        FloatTextView(ctx, FloatText(true, "$short ${if (up) "up" else "down"} $n", if (up) FloatKind.STAT_UP else FloatKind.STAT_DOWN, stat, n), 0f)

    private fun patch(ctx: Context, top: Int, bot: Int, vararg v: View) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8))
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bot))
        for (x in v) addView(x, LinearLayout.LayoutParams(-2, -2).also { it.setMargins(Ui.dp(ctx, 6), 0, Ui.dp(ctx, 6), 0) })
    }

    private fun page(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(ctx, R.color.ag_base))
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 8))
        }
        for ((name, variant) in listOf("Move details (card look)" to MoveBubbleLook.Variant.E2B, "Move details (dark look)" to MoveBubbleLook.Variant.E2A)) {
            col.addView(label(ctx, name))
            val b = MoveBubble(ctx, MoveBubbleLook.create(ctx, variant)).also { it.bind(bubbleOf()); it.tailX = Ui.dp(ctx, 40).toFloat() }
            col.addView(b, LinearLayout.LayoutParams(-2, -2))
        }
        col.addView(label(ctx, "Pills: 1, 2, 3 stages up and down"))
        col.addView(row(ctx, *(1..3).map { StatPillView(ctx, "spa", it) }.toTypedArray(), *(1..3).map { StatPillView(ctx, "evasion", -it) }.toTypedArray()))
        col.addView(label(ctx, "Floating badges, light sky and night backdrop"))
        val sky = Pair(0xFFDFE6F5.toInt(), 0xFFE9EFE3.toInt())
        val night = Pair(0xFF262A40.toInt(), 0xFF232A2C.toInt())
        for ((bg, ups) in listOf(sky to true, sky to false, night to true, night to false)) {
            val stats = listOf("atk" to "ATK", "spe" to "SPE", "spa" to "SPA")
            col.addView(patch(ctx, bg.first, bg.second, *stats.mapIndexed { i, (k, s) -> float(ctx, ups, s, s, i + 1) }.toTypedArray()),
                LinearLayout.LayoutParams(-1, -2))
        }
        return FrameLayout(ctx).apply { addView(col, FrameLayout.LayoutParams(-1, -1)) }
    }

    @Test fun pills() = paparazzi.shootWith("r19c-pills", listOf(Size.PHONE)) { ctx -> page(ctx) }
}
