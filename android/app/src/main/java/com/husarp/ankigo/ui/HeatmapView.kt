package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import androidx.core.graphics.ColorUtils
import com.husarp.ankigo.R
import com.husarp.ankigo.game.StudyStats
import kotlin.math.min

/**
 * The Stats heatmap (TICKET T40): weeks as columns (oldest left, current week right), Monday on the top row, month names
 * on top and M / W / F on the left. Rounded 3 dp cells, 3 dp apart, as big as the width allows (at most 18 dp). A day
 * after today is not drawn. Level colours come from [levelColor] (also used by the legend).
 */
class HeatmapView(ctx: Context) : View(ctx) {
    private var map: StudyStats.Heatmap? = null
    private var todayKey: String? = null

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(ctx, 1.5f)
        color = Ui.c(ctx, R.color.ag_mauve)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Ui.font(ctx, 700)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, ctx.resources.displayMetrics)
        color = Ui.c(ctx, R.color.ag_sub0)
    }
    private val rect = RectF()
    private val gap = Ui.dp(ctx, 3f)
    private val radius = Ui.dp(ctx, 3f)
    private val maxCell = Ui.dp(ctx, 18f)
    private val leftW = Ui.dp(ctx, 18f)
    private val topH = Ui.dp(ctx, 16f)

    fun set(heatmap: StudyStats.Heatmap, today: String, noun: String = "Reviews") {
        map = heatmap
        todayKey = today
        contentDescription = "$noun in the last ${heatmap.columns.size} weeks: ${heatmap.total}"
        requestLayout()
        invalidate()
    }

    private fun cellFor(width: Int): Float {
        val cols = map?.columns?.size?.takeIf { it > 0 } ?: return maxCell
        return min(maxCell, (width - leftW - gap * (cols - 1)) / cols)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = resolveSize(Ui.dp(context, 240), widthMeasureSpec)
        val h = topH + 7 * cellFor(w) + 6 * gap
        setMeasuredDimension(w, h.toInt() + 1)
    }

    override fun onDraw(canvas: Canvas) {
        val m = map ?: return
        val cell = cellFor(width)
        // M / W / F on the left, vertically centred on their rows
        label.textAlign = Paint.Align.LEFT
        for ((row, text) in listOf(0 to "M", 2 to "W", 4 to "F")) {
            val cy = topH + row * (cell + gap) + cell / 2
            canvas.drawText(text, 0f, cy - (label.descent() + label.ascent()) / 2, label)
        }
        for ((col, name) in m.monthLabels) {
            canvas.drawText(name, leftW + col * (cell + gap), topH - Ui.dp(context, 5f), label)
        }
        for ((c, column) in m.columns.withIndex()) {
            val x = leftW + c * (cell + gap)
            for ((r, d) in column.withIndex()) {
                if (d == null) continue
                val y = topH + r * (cell + gap)
                rect.set(x, y, x + cell, y + cell)
                cellPaint.color = levelColor(context, d.level)
                canvas.drawRoundRect(rect, radius, radius, cellPaint)
                if (d.day == todayKey) {
                    val h = ring.strokeWidth / 2
                    rect.inset(h, h)
                    canvas.drawRoundRect(rect, radius, radius, ring)
                }
            }
        }
    }

    companion object {
        /** Level 0 = the quiet empty tone, 1..4 = `ag_green` at rising strength over the card colour. */
        fun levelColor(ctx: Context, level: Int): Int {
            val card = Ui.c(ctx, R.color.ag_card)
            if (level <= 0) {
                val dark = ColorUtils.calculateLuminance(card) < 0.5
                return Ui.c(ctx, if (dark) R.color.ag_s0 else R.color.ag_mantle)
            }
            val a = floatArrayOf(0f, 0.35f, 0.55f, 0.78f, 1f)[level.coerceIn(1, 4)]
            return ColorUtils.blendARGB(card, Ui.c(ctx, R.color.ag_green), a)
        }
    }
}
