package com.husarp.ankigo.shots

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.StatTypography
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R29IStatTests {
    @get:Rule val paparazzi = Shots.rule()

    @Test fun onlyNumericValueArrowsAreReplacedAndExistingColoursSurviveRebinding() {
        val raw = "58 → 104 · 57 → 38 · Next → Back · HP 10/20 · ×2/3"
        val source = SpannableStringBuilder(raw)
        val colour = ForegroundColorSpan(0xff123456.toInt())
        source.setSpan(colour, 0, 8, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val formatted = StatTypography.format(StatTypography.format(source)) as Spanned
        assertEquals(raw, formatted.toString())
        assertEquals(0, formatted.getSpanStart(colour))
        assertEquals(8, formatted.getSpanEnd(colour))
        val arrows = formatted.getSpans(0, formatted.length, ReplacementSpan::class.java)
        assertEquals(2, arrows.size)
        assertEquals(listOf(raw.indexOf('→'), raw.indexOf('→', raw.indexOf('→') + 1)), arrows.map { formatted.getSpanStart(it) })
        for (arrow in arrows) assertEquals("→", formatted.subSequence(formatted.getSpanStart(arrow), formatted.getSpanEnd(arrow)).toString())
        assertEquals("Next → Back", StatTypography.format("Next → Back"))
        assertEquals(0, source.getSpans(0, source.length, ReplacementSpan::class.java).size)
    }

    @Test fun compactStatCardOmitsOwnerAndClosesOnTapEvenWhenItMustScroll() {
        val ctx = paparazzi.context
        val mark = com.husarp.ankigo.ui.ConditionMarks.Mark("def", com.husarp.ankigo.ui.ConditionMarks.Slot.OURS,
            com.husarp.ankigo.ui.ConditionMarks.def("stat.def")!!, 1, 1L)
        val sections = listOf(com.husarp.ankigo.ui.ConditionSections.Section("Your Pikachu", listOf(mark)))
        for (short in listOf(false, true)) {
            var closes = 0
            val card = com.husarp.ankigo.ui.ConditionList(ctx, 9f)
            card.buildStat(sections, Ui.dp(ctx, 320), Ui.dp(ctx, if (short) 60 else 400)) { closes++ }
            card.layout(0, 0, card.measuredWidth, card.measuredHeight)
            assertEquals(short, card.scrolls)
            assertFalse(card.spoken.contains("Pikachu"))
            assertTrue(card.spoken.contains("Defense"))
            if (!short) assertTrue("no 48 dp owner row remains", card.measuredHeight < Ui.dp(ctx, 160))
            val t = android.os.SystemClock.uptimeMillis()
            card.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, card.width / 2f, card.height / 2f, 0))
            card.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 40, android.view.MotionEvent.ACTION_UP, card.width / 2f, card.height / 2f, 0))
            assertEquals("one tap closes the card", 1, closes)
        }
    }

    @Test fun everyStageIsItsOwnChipAndTapTarget() {
        val ctx = paparazzi.context
        for (slot in com.husarp.ankigo.ui.ConditionMarks.Slot.entries) {
            val stages = com.husarp.ankigo.ui.ConditionMarks.STAT_KEYS.mapIndexed { i, key ->
                com.husarp.ankigo.ui.ConditionMarks.Mark(key, slot, com.husarp.ankigo.ui.ConditionMarks.def("stat.$key")!!, if (i % 2 == 0) 1 else -6, i.toLong())
            }
            val m = com.husarp.ankigo.ui.ChipMetrics(ctx, 10.5f)
            var x = 0f
            val rects = stages.map { mark ->
                val w = m.width(mark)
                com.husarp.ankigo.ui.ConditionSpot.R(x, 0f, x + w, m.height).also { x += w + m.gap }
            }
            val view = com.husarp.ankigo.ui.ConditionMarksView(ctx)
            view.setFlow(slot, rects, stages, m)
            view.layout(0, 0, x.toInt() + 1, m.height.toInt() + 1)
            assertEquals(7, view.chipRects().size)
            val tapped = mutableListOf<String>()
            view.onMarkTap = { tapped += it.key }
            val t = android.os.SystemClock.uptimeMillis()
            for (r in view.chipRects()) for (action in listOf(0, 1)) {
                val e = android.view.MotionEvent.obtain(t, t + action * 40L, action, r.centerX(), r.centerY(), 0)
                view.onTouchEvent(e); e.recycle()
            }
            assertEquals(stages.map { it.key }, tapped)
        }
    }

    @Test fun arrowUsesRoundThinStrokesDigitCentreAndCurrentTextColourWithoutChangingPaint() {
        val text = StatTypography.format("58 → 104") as Spanned
        val arrow = text.getSpans(0, text.length, ReplacementSpan::class.java).single()
        val start = text.getSpanStart(arrow)
        for (size in listOf(13f, 26f, 39f)) for (colour in listOf(R.color.ag_green, R.color.ag_red)) {
            val paint = TextPaint().apply {
                textSize = size
                typeface = Ui.font(paparazzi.context, 900)
                color = paparazzi.context.getColor(colour)
                alpha = 191
            }
            val original = Paint(paint)
            val fm = paint.fontMetricsInt
            val ascent = fm.ascent
            val descent = fm.descent
            assertEquals((12f * size / 13f).toInt(), arrow.getSize(paint, text, start, start + 1, fm))
            assertEquals(ascent, fm.ascent)
            assertEquals(descent, fm.descent)
            var draws = 0
            val canvas = object : Canvas() {
                override fun drawPath(path: Path, stroke: Paint) {
                    draws++
                    assertEquals(Paint.Style.STROKE, stroke.style)
                    assertEquals(Paint.Cap.ROUND, stroke.strokeCap)
                    assertEquals(Paint.Join.ROUND, stroke.strokeJoin)
                    assertEquals(1.5f * size / 13f, stroke.strokeWidth, 0.001f)
                    assertEquals(paint.color, stroke.color)
                    assertEquals(paint.alpha, stroke.alpha)
                    val digits = Rect().also { paint.getTextBounds("0123456789", 0, 10, it) }
                    val bounds = RectF().also { path.computeBounds(it, true) }
                    assertEquals(40f + (digits.top + digits.bottom) / 2f, bounds.centerY(), 0.001f)
                    assertEquals(7f * size / 13f, bounds.height(), 0.001f)
                    assertEquals(10f + size / 13f, bounds.left, 0.001f)
                    assertEquals(10f + 10f * size / 13f, bounds.right, 0.001f)
                }
            }
            arrow.draw(canvas, text, start, start + 1, 10f, 0, 40, 70, paint)
            assertEquals(1, draws)
            assertEquals(original.style, paint.style)
            assertEquals(original.strokeWidth, paint.strokeWidth, 0f)
            assertEquals(original.color, paint.color)
            assertEquals(original.strokeCap, paint.strokeCap)
        }
    }
}
