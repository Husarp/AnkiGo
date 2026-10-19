package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.view.View
import android.widget.ImageView
import com.husarp.ankigo.R

/** The "Best" mark on the Stats streak card (R10-W2-D, D5-A): no box; a trophy and "New best!" while the streak is the record. */
object StreakBest {
    /** A record: the streak runs (> 0) and has reached the best. */
    fun isRecord(current: Int, best: Int): Boolean = current > 0 && current >= best

    fun description(current: Int, best: Int): String =
        if (isRecord(current, best)) "New best streak: $best ${days(best)}" else "Best streak: $best ${days(best)}"

    private fun days(n: Int) = if (n == 1) "day" else "days"

    fun view(ctx: Context, current: Int, best: Int): IconLabel {
        val record = isRecord(current, best)
        val icon = ImageView(ctx).apply {
            setImageResource(if (record) R.drawable.ic_ach_trophy else R.drawable.ic_star)
            setColorFilter(Ui.c(ctx, R.color.ag_yellow), PorterDuff.Mode.SRC_IN)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val label = if (record) Ui.text(ctx, 12f, 900, R.color.ag_yellow).apply { text = "New best!" }
        else Ui.text(ctx, 12f, 800, R.color.ag_sub0).apply {
            text = SpannableStringBuilder("Best ").also { sb ->
                val at = sb.length
                sb.append(best.toString())
                sb.setSpan(android.text.style.ForegroundColorSpan(Ui.c(ctx, R.color.ag_text)), at, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(FontSpan(Ui.font(ctx, 900)), at, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        label.maxLines = 1
        return IconLabel(ctx, icon, label, 14, 4).also {
            it.contentDescription = description(current, best)
            it.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
    }
}

/** A part of a text in another weight of the app font (TypefaceSpan(Typeface) needs API 28; the app starts at 26). */
private class FontSpan(private val face: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) { tp.typeface = face }
    override fun updateMeasureState(tp: TextPaint) { tp.typeface = face }
}
