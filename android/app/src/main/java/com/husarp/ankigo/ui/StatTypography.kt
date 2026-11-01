package com.husarp.ankigo.ui

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan

/** Shape only multiplier fractions: other slashes (HP, dates, stages) keep their meaning and size. */
internal object StatTypography {
    private val fraction = Regex("(?<=×)[1-8]/[2-8](?![0-9])|(?<![0-9])[1-8]/[2-8](?=×)")
    private val valueArrow = Regex("(?<=\\d) → (?=\\d)")
    private class Fraction : MetricAffectingSpan() {
        override fun updateMeasureState(p: TextPaint) { p.fontFeatureSettings = "'frac' 1" }
        override fun updateDrawState(p: TextPaint) { p.fontFeatureSettings = "'frac' 1" }
    }
    private class ValueArrow : RelativeSizeSpan(0.75f)
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
