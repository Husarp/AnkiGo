package com.husarp.ankigo.ui

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.text.style.ReplacementSpan
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import kotlin.math.ceil

/** Shape multiplier fractions and numeric value arrows, preserving the text for accessibility. */
internal object StatTypography {
    private val fraction = Regex("(?<=×)[1-8]/[2-8](?![0-9])|(?<![0-9])[1-8]/[2-8](?=×)")
    private val valueArrow = Regex("(?<=\\d) → (?=\\d)")
    private class Fraction : MetricAffectingSpan() {
        override fun updateMeasureState(p: TextPaint) { p.fontFeatureSettings = "'frac' 1" }
        override fun updateDrawState(p: TextPaint) { p.fontFeatureSettings = "'frac' 1" }
    }
    /** PLAN 459 A: 12 × 10 at 13 sp, centred on digit ink rather than the font's line box. */
    private class ValueArrow : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int,
                             fm: Paint.FontMetricsInt?): Int = ceil(12f * paint.textSize / 13f).toInt()

        override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float,
                          top: Int, y: Int, bottom: Int, paint: Paint) {
            val scale = paint.textSize / 13f
            val digits = Rect()
            paint.getTextBounds("0123456789", 0, 10, digits)
            val center = y + (digits.top + digits.bottom) / 2f
            // Copy the current text paint so theme/stage colours and alpha carry through unchanged.
            val stroke = Paint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * scale
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
                pathEffect = null
            }
            val path = Path().apply {
                moveTo(x + scale, center)
                lineTo(x + 10f * scale, center)
                moveTo(x + 6.5f * scale, center - 3.5f * scale)
                lineTo(x + 10f * scale, center)
                lineTo(x + 6.5f * scale, center + 3.5f * scale)
            }
            canvas.drawPath(path, stroke)
        }
    }
    fun format(value: CharSequence): CharSequence {
        if (!fraction.containsMatchIn(value) && !valueArrow.containsMatchIn(value)) return value
        val out = SpannableStringBuilder(value)
        out.getSpans(0, out.length, Fraction::class.java).forEach(out::removeSpan)
        out.getSpans(0, out.length, ValueArrow::class.java).forEach(out::removeSpan)
        for (m in fraction.findAll(value)) {
            val start = m.range.first
            out.setSpan(Fraction(), start, m.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        for (m in valueArrow.findAll(value)) {
            val i = m.range.first + 1
            out.setSpan(ValueArrow(), i, i + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }
}
