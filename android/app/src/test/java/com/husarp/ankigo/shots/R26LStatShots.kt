package com.husarp.ankigo.shots

import android.view.Gravity
import android.widget.LinearLayout
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Stat
import com.husarp.ankigo.ui.*
import org.junit.Rule
import org.junit.Test

class R26LStatShots {
    @get:Rule val paparazzi = Shots.rule()
    @Test fun typographyPreservesTextAndDoesNotAccumulateSizing() {
        val raw = "48 → 32 · ×2/3 · 2/3× · HP 10/20"
        val once = StatTypography.format(raw)
        val twice = StatTypography.format(once) as android.text.Spanned
        org.junit.Assert.assertEquals(raw, twice.toString())
        val sizes = twice.getSpans(0, twice.length, android.text.style.ReplacementSpan::class.java)
        org.junit.Assert.assertEquals(1, sizes.size)
        org.junit.Assert.assertEquals(raw.indexOf('→'), twice.getSpanStart(sizes.single()))
        val fractions = twice.getSpans(0, twice.length, android.text.style.MetricAffectingSpan::class.java).filter { it !is android.text.style.ReplacementSpan }
        org.junit.Assert.assertEquals(2, fractions.size)
        for (span in fractions) org.junit.Assert.assertEquals("2/3", twice.subSequence(twice.getSpanStart(span), twice.getSpanEnd(span)).toString())
    }
    @Test fun labelsStayNonEditableAndNumericTextFormatsOnEveryBind() {
        val label = Ui.text(paparazzi.context, 13f, 800, R.color.ag_text)
        label.text = "Ordinary label"
        org.junit.Assert.assertEquals("Ordinary label", label.text)
        org.junit.Assert.assertFalse(label.text is android.text.Editable)
        label.text = "48 → 32 · ×2/3"
        val styled = label.text as android.text.Spanned
        org.junit.Assert.assertEquals(2, styled.getSpans(0, styled.length, android.text.style.MetricAffectingSpan::class.java).size)
        org.junit.Assert.assertFalse(styled is android.text.Editable)
        label.text = "Another label"
        org.junit.Assert.assertEquals("Another label", label.text)
        org.junit.Assert.assertFalse(label.text is android.text.Editable)
    }
    @Test fun stagesAndFractions() = paparazzi.shootWith("r26l-stats", listOf(Size.PHONE, Size.SMALL)) { ctx ->
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Ui.c(ctx, R.color.ag_base))
            addView(Ui.text(ctx, 13f, 900, R.color.ag_text).apply { text = "Resists ×½   Strongly resists ×¼" })
            addView(Ui.text(ctx, 13f, 900, R.color.ag_text).apply { text = "×2/3  ×1/2  ×2/5  ×1/3  ×2/7  ×1/4" })
            addView(Ui.text(ctx, 13f, 900, R.color.ag_text).apply { text = "×4/3  ×5/3  ×7/3  ×8/3  ×3/4  ×3/5  ×3/7  ×3/8" })
            for (sign in listOf(1, -1)) for (start in listOf(1, 4)) addView(LinearLayout(ctx).apply {
                gravity = Gravity.CENTER_VERTICAL
                for (n in start..start+2) addView(StatPillView(ctx, "atk", n * sign), LinearLayout.LayoutParams(-2, -2).apply { setMargins(4, 8, 4, 8) })
            })
            for (sign in listOf(1, -1)) for (start in listOf(1, 4)) {
                val metrics = ChipMetrics(ctx, 11f)
                val marks = (start..start+2).map { n -> ConditionMarks.Mark("$n", ConditionMarks.Slot.OURS, ConditionMarks.def("stat.atk")!!, sign * n, n.toLong()) }
                val w = metrics.summaryWidth(marks)
                addView(ConditionMarksView(ctx).apply {
                    setRow(ConditionMarks.Slot.OURS, ConditionSpot.Placed(ConditionSpot.R(0f, 0f, w, metrics.height), ConditionRow.Fit(1, 0, w), true), marks, metrics)
                }, LinearLayout.LayoutParams(-1, (metrics.height + 8).toInt()))
            }
            addView(FloatTextView(ctx, FloatText(true, "Attack up 6", FloatKind.STAT_UP, "ATK", 6), 0f))
            addView(DetailParts.statRow(ctx, Stat.ATK, 48, 0.5f, 0, 60, false, stage = -1))
        }
    }
}
