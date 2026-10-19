package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import com.husarp.ankigo.R
import com.husarp.ankigo.game.StudyStats

/**
 * The Stats heatmap (TICKET T40, R9-W2-D5): the calendar of the chosen period. The geometry is [HeatmapLayout] (a strip for 7
 * days, rows of 7 with a new row per month for 30 days, week columns for 90 days and a year); this view only draws it and maps
 * a tap to a day. Cells are rounded, today has a mauve ring. Level colours come from [levelColor] (also used by the legend).
 */
class HeatmapView(ctx: Context) : View(ctx) {
    private var days: List<HeatmapLayout.Day> = emptyList()
    private var mode = HeatmapLayout.Mode.WEEKS
    private var todayKey: String? = null
    private var layout: HeatmapLayout.Result? = null
    private var layoutW = -1f

    private val density = ctx.resources.displayMetrics.density
    private val fontScale = ctx.resources.configuration.fontScale

    // the canvas is scaled by the density while drawing, so every size below is in dp
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Ui.c(ctx, R.color.ag_mauve)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Ui.font(ctx, 700)
        color = Ui.c(ctx, R.color.ag_sub0)
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Ui.font(ctx, 800)
        textSize = 10f * fontScale
        textAlign = Paint.Align.CENTER
    }
    private val pick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Ui.c(ctx, R.color.ag_text)
    }
    private val numberOnDark = Ui.c(ctx, R.color.ag_card)
    private val numberOnLight = Ui.c(ctx, R.color.ag_text)
    private val rect = RectF()

    /** TICKET R3-V1: the day whose info is open (outlined), and what a tap on a day does (null = tapped beside the days). */
    var selectedDay: String? = null
        set(v) { if (v != field) { field = v; invalidate() } }
    var onDayTap: ((String?) -> Unit)? = null

    fun set(heatmap: StudyStats.Heatmap, today: String, noun: String = "Reviews", mode: HeatmapLayout.Mode = HeatmapLayout.Mode.WEEKS) {
        days = heatmap.columns.flatten().filterNotNull().map { HeatmapLayout.Day(it.day, it.level) }
        this.mode = mode
        todayKey = today
        layout = null
        contentDescription = "$noun in the last ${days.size} days: ${heatmap.total}"
        requestLayout()
        invalidate()
    }

    private fun layoutFor(widthPx: Int): HeatmapLayout.Result {
        val w = widthPx / density
        val l = layout
        if (l != null && w == layoutW) return l
        return HeatmapLayout.layout(mode, days, todayKey ?: days.lastOrNull()?.key ?: "", w).also { layout = it; layoutW = w }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = resolveSize(Ui.dp(context, 240), widthMeasureSpec)
        setMeasuredDimension(w, (layoutFor(w).height * density).toInt() + 1)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layout = null
    }

    override fun onDraw(canvas: Canvas) {
        val l = layoutFor(width)
        canvas.save()
        canvas.scale(density, density)
        label.textSize = l.labelSp * fontScale
        val mid = -(label.descent() + label.ascent()) / 2
        for (t in l.letters) {
            label.textAlign = if (t.centered) Paint.Align.CENTER else Paint.Align.LEFT
            canvas.drawText(t.text, t.x, t.cy + mid, label)
        }
        for (t in l.months) {
            label.textAlign = if (t.centered) Paint.Align.CENTER else Paint.Align.LEFT
            canvas.drawText(t.text, t.x, t.cy + mid, label)
        }
        val numMid = -(numberPaint.descent() + numberPaint.ascent()) / 2
        for (c in l.cells) {
            rect.set(c.x, c.y, c.x + c.w, c.y + c.h)
            cellPaint.color = levelColor(context, c.level)
            canvas.drawRoundRect(rect, c.radius, c.radius, cellPaint)
            if (c.today) {
                val h = ring.strokeWidth / 2
                rect.inset(h, h)
                canvas.drawRoundRect(rect, c.radius, c.radius, ring)
            }
            c.number?.let {
                numberPaint.color = if (c.level >= 3) numberOnDark else numberOnLight
                canvas.drawText(it, c.x + c.w / 2, c.y + c.h / 2 + numMid, numberPaint)
            }
            if (c.day == selectedDay) {
                rect.set(c.x, c.y, c.x + c.w, c.y + c.h)
                rect.inset(pick.strokeWidth / 2, pick.strokeWidth / 2)
                canvas.drawRoundRect(rect, c.radius, c.radius, pick)
            }
        }
        canvas.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val tap = onDayTap ?: return super.onTouchEvent(e)
        if (e.actionMasked == MotionEvent.ACTION_DOWN) return true
        if (e.actionMasked != MotionEvent.ACTION_UP) return true
        tap(layoutFor(width).hit(e.x / density, e.y / density))
        performClick()
        return true
    }

    override fun performClick(): Boolean = super.performClick()

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
