package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextUtils
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.husarp.ankigo.R
import com.husarp.ankigo.stats.ChartKind
import com.husarp.ankigo.stats.ChartMetric
import com.husarp.ankigo.stats.ChartSeries
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// TICKET R3-V1 (pick 1, look 1B): the Stats chart, drawn by hand. A TIME chart is bars over days / weeks / months (stacked when
// the metric has parts), a CATEGORY chart is a list of labelled bars (rows). Tap a bar: it lights up and a small box says what
// that exact day / period holds ([com.husarp.ankigo.stats.Bar.detail]); drag across to scrub (time charts); tap beside the
// bars to close it. The streak calendar is the [HeatmapView]. All geometry and text decisions are in [StatsChartMath].

class StatsChartView(ctx: Context) : View(ctx) {
    private var series: ChartSeries? = null
    private var tones: List<Int> = emptyList()

    /** The bar whose info box is open, -1 = none. */
    var selected: Int = -1
        set(v) {
            val n = series?.bars?.size ?: 0
            val next = if (v in 0 until n) v else -1
            if (next != field) { field = next; invalidate() }
        }

    /** Called with the new [selected] (-1 = closed) after a tap or a drag. */
    var onSelect: ((Int) -> Unit)? = null

    private fun dp(v: Float) = Ui.dp(context, v)
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
    private fun color(res: Int) = Ui.c(context, res)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grid = Paint().apply { color = color(R.color.ag_s0); strokeWidth = dp(1f) }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font(context, 700); textSize = sp(10f); color = color(R.color.ag_sub0) }
    private val rowLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font(context, 800); textSize = sp(11f); color = color(R.color.ag_sub1) }
    private val rowValue = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font(context, 900); textSize = sp(11f); color = color(R.color.ag_text) }
    private val tipTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font(context, 900); textSize = sp(12f); color = color(R.color.ag_text) }
    private val tipLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font(context, 700); textSize = sp(11f); color = color(R.color.ag_sub1) }
    private val tipBox = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = color(R.color.ag_mantle) }
    private val tipEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1f); color = color(R.color.ag_s1) }
    private val rect = RectF()
    private val path = Path()

    private val plotHeight = dp(200f)
    private val rowHeight = dp(28f)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    fun set(s: ChartSeries, tones: List<Int>, selected: Int = -1) {
        series = s
        this.tones = tones.ifEmpty { listOf(color(R.color.ag_mauve)) }
        this.selected = selected
        contentDescription = "${StatsChartMath.title(s)}: ${s.bars.size} bars"
        requestLayout()
        invalidate()
    }

    private val rows: Boolean get() = series?.kind == ChartKind.CATEGORY

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = resolveSize(dp(240f).toInt(), widthMeasureSpec)
        val h = if (rows) (series?.bars?.size ?: 0) * rowHeight + dp(4f) else plotHeight
        setMeasuredDimension(w, h.toInt())
    }

    // ---------------------------------------------------------------- drawing

    override fun onDraw(canvas: Canvas) {
        val s = series ?: return
        if (s.bars.isEmpty()) return
        if (rows) drawRows(canvas, s) else drawBars(canvas, s)
    }

    /** The bar colours of bar [i]'s part [part]. */
    private fun tone(part: Int) = tones[part % tones.size]

    private fun peak(s: ChartSeries): Double =
        s.bars.filter { it.hasData }.maxOfOrNull { b -> if (s.metric == ChartMetric.RETENTION) b.values.firstOrNull() ?: 0.0 else b.values.sum() } ?: 0.0

    // plot box of a TIME chart
    private var plotL = 0f
    private var plotR = 0f
    private var plotT = 0f
    private var plotB = 0f

    private fun drawBars(canvas: Canvas, s: ChartSeries) {
        val n = s.bars.size
        val axis = StatsChartMath.axis(s.metric, peak(s))
        plotL = axis.labels.maxOf { label.measureText(it) } + dp(10f)
        plotR = width - dp(2f)
        plotT = dp(8f)
        plotB = height - dp(22f)
        val ph = plotB - plotT

        // gridlines + y labels
        label.textAlign = Paint.Align.RIGHT
        val lh = (label.descent() + label.ascent()) / 2
        for ((i, tick) in axis.ticks.withIndex()) {
            val y = plotB - (tick / axis.max).toFloat() * ph
            canvas.drawLine(plotL, y, plotR, y, grid)
            canvas.drawText(axis.labels[i], plotL - dp(6f), y - lh, label)
        }

        val slot = (plotR - plotL) / n
        val barW = max(dp(1.5f), min(slot * 0.74f, dp(26f)))
        val radius = min(dp(3f), barW / 2)
        val dim = selected >= 0
        if (dim) { // a soft column behind the chosen bar
            val cx = StatsChartMath.slotCenter(selected, n, plotL, plotR)
            fill.color = tone(0); fill.alpha = 38
            rect.set(cx - slot / 2, plotT - dp(4f), cx + slot / 2, plotB)
            canvas.drawRoundRect(rect, radius, radius, fill)
        }
        for (i in 0 until n) {
            val bar = s.bars[i]
            if (!bar.hasData) continue
            val cx = StatsChartMath.slotCenter(i, n, plotL, plotR)
            val alpha = if (dim && i != selected) 105 else 255
            val total = if (s.metric == ChartMetric.RETENTION) bar.values.firstOrNull() ?: 0.0 else bar.values.sum()
            val top = plotB - (total / axis.max).toFloat().coerceIn(0f, 1f) * ph
            if (total <= 0.0) { // a quiet day: a thin stub on the baseline
                fill.color = color(R.color.ag_s0); fill.alpha = alpha
                rect.set(cx - barW / 2, plotB - dp(2f), cx + barW / 2, plotB)
                canvas.drawRoundRect(rect, dp(1f), dp(1f), fill)
                continue
            }
            canvas.save()
            rect.set(cx - barW / 2, top, cx + barW / 2, plotB + radius)
            path.reset(); path.addRoundRect(rect, radius, radius, Path.Direction.CW)
            canvas.clipPath(path)
            var y = plotB
            for ((part, v) in bar.values.withIndex()) {
                if (v <= 0.0) continue
                val h = (v / total).toFloat() * (plotB - top)
                fill.color = tone(part); fill.alpha = alpha
                canvas.drawRect(cx - barW / 2, y - h, cx + barW / 2, y, fill)
                y -= h
            }
            canvas.restore()
        }

        // x labels
        val texts = s.bars.map { StatsChartMath.axisText(s, it) }
        val plan = StatsChartMath.labelPlan(n, plotL, plotR, dp(8f)) { label.measureText(texts[it]) }
        label.textAlign = Paint.Align.LEFT
        for (l in plan) {
            val w = label.measureText(texts[l.index])
            canvas.drawText(texts[l.index], StatsChartMath.labelLeft(l, n, plotL, plotR, w), height - dp(6f), label)
        }

        if (selected >= 0) {
            val cx = StatsChartMath.slotCenter(selected, n, plotL, plotR)
            drawTip(canvas, s, s.bars[selected].detail, tipLeft = { w -> StatsChartMath.tooltipLeft(cx, barW / 2 + dp(8f), w, width.toFloat(), dp(6f)) }, tipTop = { plotT })
        }
    }

    // label column + track of a CATEGORY chart
    private fun drawRows(canvas: Canvas, s: ChartSeries) {
        val n = s.bars.size
        val labelW = min(s.bars.maxOf { rowLabel.measureText(it.label) } + dp(4f), width * 0.36f)
        val valueW = s.bars.maxOf { rowValue.measureText(StatsChartMath.categoryValue(s.metric, it.values.firstOrNull() ?: 0.0)) } + dp(2f)
        val trackL = labelW + dp(10f)
        val trackR = width - valueW - dp(10f)
        val full = if (s.metric == ChartMetric.DEX_PROGRESS) 100.0 else max(1.0, s.bars.maxOf { it.values.firstOrNull() ?: 0.0 })
        val trackH = dp(10f)
        for (i in 0 until n) {
            val bar = s.bars[i]
            val top = i * rowHeight + dp(2f)
            val cy = top + rowHeight / 2
            if (i == selected) {
                fill.color = tone(i); fill.alpha = 34
                rect.set(0f, top, width.toFloat(), top + rowHeight)
                canvas.drawRoundRect(rect, dp(8f), dp(8f), fill)
            }
            val alpha = if (selected >= 0 && i != selected) 120 else 255
            rowLabel.alpha = alpha; rowValue.alpha = alpha
            rowLabel.textAlign = Paint.Align.LEFT
            canvas.drawText(TextUtils.ellipsize(bar.label, android.text.TextPaint(rowLabel), labelW, TextUtils.TruncateAt.END).toString(), dp(4f), cy - (rowLabel.descent() + rowLabel.ascent()) / 2, rowLabel)
            fill.color = color(R.color.ag_s0); fill.alpha = alpha
            rect.set(trackL, cy - trackH / 2, trackR, cy + trackH / 2)
            canvas.drawRoundRect(rect, trackH / 2, trackH / 2, fill)
            val v = bar.values.firstOrNull() ?: 0.0
            if (v > 0.0) {
                fill.color = tone(i); fill.alpha = alpha
                rect.set(trackL, cy - trackH / 2, trackL + max(trackH, ((trackR - trackL) * (v / full).toFloat().coerceIn(0f, 1f))), cy + trackH / 2)
                canvas.drawRoundRect(rect, trackH / 2, trackH / 2, fill)
            }
            rowValue.textAlign = Paint.Align.RIGHT
            canvas.drawText(StatsChartMath.categoryValue(s.metric, v), width - dp(4f), cy - (rowValue.descent() + rowValue.ascent()) / 2, rowValue)
        }
        rowLabel.alpha = 255; rowValue.alpha = 255
        if (selected >= 0) {
            val rowTop = selected * rowHeight + dp(2f)
            drawTip(canvas, s, s.bars[selected].detail,
                tipLeft = { w -> (width - w - dp(4f)).coerceAtLeast(dp(4f)) },
                tipTopFor = { h -> StatsChartMath.tooltipTop(rowTop, rowTop + rowHeight, h, height.toFloat(), dp(2f), dp(2f)) })
        }
    }

    /** The info box: the first line in bold, the others small; a stacked bar's part lines get their colour as a dot. */
    private fun drawTip(
        canvas: Canvas, s: ChartSeries, lines: List<String>, tipLeft: (Float) -> Float,
        tipTop: (() -> Float)? = null, tipTopFor: ((Float) -> Float)? = null,
    ) {
        if (lines.isEmpty()) return
        val pad = dp(10f)
        val dotW = dp(14f)
        val parts = if (s.kind == ChartKind.TIME && s.seriesNames.size > 1 && lines.size > s.seriesNames.size) s.seriesNames.size else 0
        val maxW = width - dp(12f)
        val widest = lines.withIndex().maxOf { (i, t) -> (if (i == 0) tipTitle else tipLine).measureText(t) + if (i in 1..parts) dotW else 0f }
        val w = min(widest + 2 * pad, maxW)
        val lineH = dp(17f)
        val h = pad * 0.8f * 2 + lines.size * lineH
        val x = tipLeft(w)
        val y = tipTopFor?.invoke(h) ?: tipTop?.invoke() ?: dp(6f)
        rect.set(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, dp(10f), dp(10f), tipBox)
        canvas.drawRoundRect(rect, dp(10f), dp(10f), tipEdge)
        for ((i, t) in lines.withIndex()) {
            val p = if (i == 0) tipTitle else tipLine
            val base = y + pad * 0.8f + i * lineH + lineH / 2 - (p.descent() + p.ascent()) / 2
            var tx = x + pad
            if (i in 1..parts) {
                fill.color = tone(i - 1); fill.alpha = 255
                canvas.drawCircle(tx + dp(3.5f), y + pad * 0.8f + i * lineH + lineH / 2, dp(3.5f), fill)
                tx += dotW
            }
            canvas.drawText(TextUtils.ellipsize(t, android.text.TextPaint(p), w - 2 * pad - (tx - x - pad), TextUtils.TruncateAt.END).toString(), tx, base, p)
        }
    }

    // ---------------------------------------------------------------- touch

    private var downX = 0f
    private var downY = 0f
    private var scrubbing = false

    private fun hit(e: MotionEvent, clamp: Boolean): Int {
        val s = series ?: return -1
        val n = s.bars.size
        return if (rows) StatsChartMath.indexAt(e.y - dp(2f), n, 0f, n * rowHeight, clamp)
        else StatsChartMath.indexAt(e.x, n, plotL, plotR, clamp)
    }

    private fun choose(i: Int) {
        if (i == selected) return
        selected = i
        onSelect?.invoke(selected)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (series == null || series?.bars.isNullOrEmpty()) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; scrubbing = false; choose(hit(e, false)); return true }
            MotionEvent.ACTION_MOVE -> {
                if (!rows) {
                    if (!scrubbing && abs(e.x - downX) > slop && abs(e.x - downX) > abs(e.y - downY)) {
                        scrubbing = true
                        parent?.requestDisallowInterceptTouchEvent(true) // a sideways drag belongs to the chart, not the page scroll
                    }
                    if (scrubbing) choose(hit(e, true))
                }
                return true
            }
            MotionEvent.ACTION_UP -> { performClick(); return true }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
