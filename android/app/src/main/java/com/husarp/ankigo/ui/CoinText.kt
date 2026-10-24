package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.style.DynamicDrawableSpan
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.husarp.ankigo.R

/**
 * R18 (PLAN 268): text with the coin icon ([R.drawable.ic_money], fixed gold, never tinted) inline before a number, instead of
 * a currency sign: "[coin] +5". The icon is as tall as the text's size and sits on the line like a digit. It needs a TextView
 * (a system toast can't show it: use the word "coins" there); plain sentences use the word ("25 coins") through [word].
 */
object CoinText {
    /** The inline icon's code point in the text; [CharSequence.toString] of a coin text starts with it. */
    const val MARK = '￼'

    /** [text] (for example "+5" or "100") with the coin icon in front, sized to [tv]'s text size. */
    fun of(tv: TextView, text: String): CharSequence = of(tv.context, tv.textSize, text)

    /** [text] with the coin icon in front, the icon [sizePx] tall. */
    fun of(ctx: Context, sizePx: Float, text: String): CharSequence {
        val s = SpannableString("$MARK$text")
        ContextCompat.getDrawable(ctx, R.drawable.ic_money)?.mutate()?.let { s.setSpan(CoinSpan(it, sizePx), 0, 1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE) }
        return s
    }

    /** "25 coins", "1 coin": the word form for sentences and toasts. [n] is already formatted when [shown] is given ("1,500"). */
    fun word(n: Int, shown: String = n.toString()): String = "$shown ${if (n == 1) "coin" else "coins"}"

    /** "+5 coins", "+1 coin": a gain in a sentence. */
    fun plus(n: Int): String = "+" + word(n)

    /** The icon, [size] px square, sitting on the baseline with its middle at the digits' middle, a small gap after it. */
    private class CoinSpan(private val d: Drawable, private val size: Float) : DynamicDrawableSpan(ALIGN_BASELINE) {
        private val side = (size * 0.95f).toInt().coerceAtLeast(1)
        private val gap = (size * 0.25f).toInt()
        override fun getDrawable(): Drawable = d.also { it.setBounds(0, 0, side, side) }
        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int = side + gap
        override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            // digits are about 0.72 em tall: centre the coin on that band above the baseline
            val mid = y - paint.textSize * 0.36f
            canvas.save()
            canvas.translate(x, mid - side / 2f)
            getDrawable().draw(canvas)
            canvas.restore()
        }
    }
}
