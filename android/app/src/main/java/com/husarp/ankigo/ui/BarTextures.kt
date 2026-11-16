package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
import android.widget.LinearLayout
import com.husarp.ankigo.R

/** Optional fills; the classic choices preserve the existing progress-bar colours. */
enum class GoalBarFill(val label: String) { CLASSIC("Classic"), RIBBON("Ribbon") }
enum class XpBarFill(val label: String, val art: Int = 0) {
    CLASSIC("Classic"),
    RIPPLES("Ripples", R.drawable.bar_xpfill_a_1),
    WAVES("Waves", R.drawable.bar_xpfill_a_2),
    PEARL("Pearl", R.drawable.bar_xpfill_b_1),
    SHIMMER("Shimmer", R.drawable.bar_xpfill_b_2),
    TIDE("Tide", R.drawable.bar_xpfill_b_3),
}

object BarTextures {
    const val GOAL_KEY = "goalBarFill"
    const val XP_KEY = "xpBarFill"
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("ankigo_ui", 0)
    fun goal(ctx: Context) = GoalBarFill.entries.firstOrNull { it.name == prefs(ctx).getString(GOAL_KEY, null) } ?: GoalBarFill.CLASSIC
    fun xp(ctx: Context) = XpBarFill.entries.firstOrNull { it.name == prefs(ctx).getString(XP_KEY, null) } ?: XpBarFill.CLASSIC
    fun putGoal(ctx: Context, value: GoalBarFill) = AppPrefs.putLook(ctx, GOAL_KEY, value.name)
    fun putXp(ctx: Context, value: XpBarFill) = AppPrefs.putLook(ctx, XP_KEY, value.name)
    fun art(ctx: Context, meaning: BarMeaning?, fraction: Float): Int = when (meaning) {
        BarMeaning.XP -> xp(ctx).art
        BarMeaning.GOAL -> if (goal(ctx) == GoalBarFill.RIBBON) when {
            fraction >= 1f -> R.drawable.bar_goalfill_b_3
            fraction >= .45f -> R.drawable.bar_goalfill_b_2
            else -> R.drawable.bar_goalfill_b_1
        } else 0
        else -> 0
    }

    /** A vertical picker keeps names readable at large text sizes, with a real fill preview per choice. */
    fun xpPicker(ctx: Context, picked: () -> Unit): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        for (choice in XpBarFill.entries) {
            val preview = BarView(ctx).apply {
                textureOverride = choice.art
                setMeaning(BarMeaning.XP, .72f, false)
            }
            addView(Form.row(ctx, choice.label + if (xp(ctx) == choice) " · Selected" else "", null, null) {
                putXp(ctx, choice); picked()
            })
            addView(preview, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 7)).also {
                it.setMargins(Ui.dp(ctx, 16), 0, Ui.dp(ctx, 16), Ui.dp(ctx, 10))
            })
        }
    }
}

/** Shader and transform are retained: a moving fill never decodes or allocates per frame. */
internal class BarTexture(ctx: Context, art: Int) {
    private val bitmap = BitmapFactory.decodeResource(ctx.resources, art)
    val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP)
    private val matrix = Matrix()
    fun fit(height: Float) {
        val scale = height / bitmap.height
        matrix.setScale(scale, scale)
        shader.setLocalMatrix(matrix)
    }
}
