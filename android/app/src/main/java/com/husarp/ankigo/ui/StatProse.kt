package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Stat
import kotlin.math.ceil

/** Inline stat names keep their readable text for copying and accessibility. */
object StatProse {
    private val names = Regex("\\b(Special Attack|Special Defense|Attack(?! Order)|Defense(?! Curl| Order)|Speed(?! Swap)|HP|Accuracy|Evasion|accuracy|evasion|evasiveness)\\b")

    internal fun icon(stat: Stat): Int = when (stat) {
        Stat.HP -> R.drawable.ic_stat_heart
        Stat.ATK -> R.drawable.ic_stat_swords
        Stat.DEF -> R.drawable.ic_stat_shield
        Stat.SPA -> R.drawable.ic_stat_sparkles
        Stat.SPD -> R.drawable.ic_stat_shield_plus
        Stat.SPE -> R.drawable.ic_stat_gauge
    }

    internal class NameSpan(private val icon: Drawable, val color: Int, private val face: Typeface) : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int =
            ceil(Paint(paint).apply { typeface = face }.measureText(text, start, end) + paint.textSize * 1.15f).toInt()

        override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float,
                          top: Int, y: Int, bottom: Int, paint: Paint) {
            val size = paint.textSize * 0.95f
            val center = y + (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
            canvas.save()
            canvas.translate(x, center - size / 2f)
            icon.setBounds(0, 0, size.toInt(), size.toInt())
            icon.draw(canvas)
            canvas.restore()
            canvas.drawText(text, start, end, x + paint.textSize * 1.15f, y.toFloat(), Paint(paint).apply { this.color = this@NameSpan.color; typeface = face })
        }
    }

    fun format(ctx: Context, text: CharSequence): CharSequence {
        if (!names.containsMatchIn(text)) return text
        val out = SpannableStringBuilder(text)
        out.getSpans(0, out.length, NameSpan::class.java).forEach(out::removeSpan)
        for (m in names.findAll(text)) {
            val stat = when (m.value) {
                "HP" -> Stat.HP; "Attack" -> Stat.ATK; "Defense" -> Stat.DEF
                "Special Attack" -> Stat.SPA; "Special Defense" -> Stat.SPD; "Speed" -> Stat.SPE
                else -> null
            }
            val color = Ui.c(ctx, stat?.let(DetailMath::statColor) ?: R.color.ag_lav)
            val drawable = ctx.getDrawable(stat?.let(::icon) ?: R.drawable.ic_ach_eye)!!.mutate().apply { setTint(color) }
            out.setSpan(NameSpan(drawable, color, Ui.font(ctx, 600)), m.range.first, m.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }
}
