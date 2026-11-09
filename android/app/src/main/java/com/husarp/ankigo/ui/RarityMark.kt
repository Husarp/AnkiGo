package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.view.Gravity
import android.widget.LinearLayout
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.PokemonRarity

/** PLAN 436: achievement tier shapes beside neutral Pokémon names; presentation only. */
object RarityMark {
    const val KEY = "pokemonRarityMark"
    fun enabled(ctx: Context) = ctx.applicationContext.getSharedPreferences("ankigo_ui", 0).getBoolean(KEY, true)
    fun setEnabled(ctx: Context, on: Boolean) = ctx.applicationContext.getSharedPreferences("ankigo_ui", 0).edit().putBoolean(KEY, on).apply()
    fun tier(tier: Tier?, baseTotal: Int): Int? = PokemonRarity.of(tier, baseTotal).mark
    fun name(ctx: Context, text: CharSequence, tier: Tier?, baseTotal: Int): CharSequence {
        val index = tier(tier, baseTotal) ?: return text
        if (!enabled(ctx)) return text
        return label(ctx, text, index)
    }
    fun label(ctx: Context, text: CharSequence, index: Int): CharSequence =
        SpannableStringBuilder("\uFFFC ").apply {
            setSpan(Mark(index, AchStyle.tierColor(ctx, index)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(text)
        }
    /** Explicit rarity fact stays visible when name marks are switched off. Unknown species must not call this. */
    fun detail(parent: LinearLayout, tier: Tier?, baseTotal: Int, centered: Boolean = false) {
        val rarity = PokemonRarity.of(tier, baseTotal)
        val index = rarity.mark ?: return
        val ctx = parent.context
        parent.addView(Ui.text(ctx, 12f, 800, R.color.ag_sub0).apply {
            text = label(ctx, RarityFilter.label(rarity), index)
            if (centered) gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))
    }
    /** Uses the current paint so the mark follows font scaling and FitName's smaller text. */
    class Mark(private val tier: Int, private val color: Int) : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int =
            (paint.textSize * .8f).toInt()
        override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            val side = (paint.textSize * .8f).toInt().coerceAtLeast(1)
            val cy = y + (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2
            val save = canvas.save()
            canvas.translate(x, cy - side / 2f)
            AchStyle.shape(tier, color).apply { setBounds(0, 0, side, side); draw(canvas) }
            canvas.restoreToCount(save)
        }
    }
}
