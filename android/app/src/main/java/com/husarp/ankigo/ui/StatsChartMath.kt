package com.husarp.ankigo.ui

import com.husarp.ankigo.stats.AnkiAccess
import com.husarp.ankigo.stats.Bar
import com.husarp.ankigo.stats.ChartData
import com.husarp.ankigo.stats.ChartGrain
import com.husarp.ankigo.stats.ChartKind
import com.husarp.ankigo.stats.ChartMetric
import com.husarp.ankigo.stats.ChartPeriod
import com.husarp.ankigo.stats.ChartSeries
import com.husarp.ankigo.stats.StatsText
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

// TICKET R3-V1 (PLAN 26, 66, 104; pick 1): the maths and the texts of the Stats chart, pure Kotlin (no android.*), tested.
// StatsChartView and StatsPage only draw what is decided here: which bar a finger is on, where the axis labels go, how the
// axis is scaled, where the info box sits, what the three key-number tiles and the legend say.

/** One key number: the big [value] over its small [label]. */
data class StatTile(val value: String, val label: String)

/** One entry of the legend of a stacked chart: the part's name, its index (colour) and its share of the whole period. */
data class LegendItem(val name: String, val index: Int, val percent: Int?)

enum class LabelAlign { START, CENTER, END }

/** An axis label: under bar [index], its text left-aligned at the plot start, centred under the bar, or right-aligned at the plot end. */
data class AxisLabel(val index: Int, val align: LabelAlign)

/** The y axis: the top value [max], the gridline values [ticks] (bottom to top) and their [labels]. */
class ChartAxis(val max: Double, val ticks: List<Double>, val labels: List<String>)

object StatsChartMath {
    // ---------------------------------------------------------------- hit testing

    /**
     * The slot (bar or row) under [pos] when [n] equal slots fill [start]..[end]. -1 when [pos] is outside and [clamp] is false
     * (a tap on the axis labels closes the info box); with [clamp] the nearest slot (a drag that leaves the plot keeps its end bar).
     */
    fun indexAt(pos: Float, n: Int, start: Float, end: Float, clamp: Boolean = false): Int {
        if (n <= 0 || end <= start) return -1
        if (!clamp && (pos < start || pos > end)) return -1
        return floor((pos - start) / (end - start) * n).toInt().coerceIn(0, n - 1)
    }

    /** R4-X1 item 1: a tap on the pick that is already open closes it ([none] = nothing picked); any other tap picks it. */
    fun <T> tapPick(open: T, tapped: T, none: T): T = if (tapped == open) none else tapped

    /** The middle of slot [i] of [n] in [start]..[end]. */
    fun slotCenter(i: Int, n: Int, start: Float, end: Float): Float = start + (i + 0.5f) * (end - start) / n

    // ---------------------------------------------------------------- the y axis

    /**
     * The axis for the tallest bar [dataMax] (a stacked bar: its sum). Three gridlines (0, half, top) with whole, round numbers;
     * RETENTION is always 0..100 %; TIME (in minutes) switches to hours from one hour up.
     */
    fun axis(metric: ChartMetric, dataMax: Double): ChartAxis {
        if (metric == ChartMetric.RETENTION) return ChartAxis(100.0, listOf(0.0, 50.0, 100.0), listOf("0", "50", "100"))
        if (metric == ChartMetric.TIME) {
            if (dataMax >= 60.0) {
                val h = niceMax(dataMax / 60.0)
                return ChartAxis(h * 60, listOf(0.0, h * 30, h * 60), listOf("0", hours(h / 2), hours(h)))
            }
            val m = if (dataMax <= 0.0) 60.0 else niceMax(dataMax)
            return ChartAxis(m, listOf(0.0, m / 2, m), listOf("0", minutes(m / 2), minutes(m)))
        }
        val top = niceMax(dataMax)
        return ChartAxis(top, listOf(0.0, top / 2, top), listOf("0", compact(top / 2), compact(top)))
    }

    /** The smallest 2, 4, 6, 8 or 10 times a power of ten that holds [v] (so half of it is round too). */
    fun niceMax(v: Double): Double {
        if (v <= 0.0) return 2.0
        val scale = 10.0.pow(floor(log10(v)))
        val n = v / scale
        return listOf(2.0, 4.0, 6.0, 8.0, 10.0).first { it >= n - 1e-9 } * scale
    }

    /** "850", "1.2k", "12k", "3M"; a fraction keeps one decimal. */
    fun compact(v: Double): String = when {
        v >= 1_000_000 -> trim(v / 1_000_000) + "M"
        v >= 10_000 -> "${(v / 1000).roundToInt()}k"
        v >= 1000 -> trim(v / 1000) + "k"
        else -> trim(v)
    }

    private fun trim(v: Double): String = if (abs(v - Math.rint(v)) < 0.05) Math.rint(v).toLong().toString() else String.format(Locale.US, "%.1f", v)
    private fun minutes(m: Double): String = if (m >= 60.0) hours(m / 60) else "${trim(m)}m"
    private fun hours(h: Double): String = "${trim(h)}h"

    /** The value text at the end of a category row ("41 %", "1.2k"). */
    fun categoryValue(metric: ChartMetric, v: Double): String =
        if (metric == ChartMetric.DEX_PROGRESS) "${Math.round(v)} %" else compact(v)

    // ---------------------------------------------------------------- x axis labels

    /** The text under a bar: a 7 day chart the weekday, a longer day chart "17 Sep", a week "Oct 5", a month "Oct". */
    fun axisText(s: ChartSeries, bar: Bar): String {
        if (s.kind != ChartKind.TIME || s.grain != ChartGrain.DAY || s.period == ChartPeriod.D7) return bar.label
        val d = runCatching { LocalDate.parse(bar.startDay) }.getOrNull() ?: return bar.label
        return "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
    }

    /**
     * Which bars get a label under them: all of them when they fit; else the most that fit without touching (each [gap] apart),
     * always the first (left-aligned at [start]) and the last (right-aligned at [end]), the ones between evenly spread.
     * [width] gives the text width of bar i.
     */
    fun labelPlan(n: Int, start: Float, end: Float, gap: Float, width: (Int) -> Float): List<AxisLabel> {
        if (n <= 0) return emptyList()
        if (n == 1) return listOf(AxisLabel(0, LabelAlign.CENTER))
        fun plan(idx: List<Int>) = idx.map { AxisLabel(it, if (it == 0) LabelAlign.START else if (it == n - 1) LabelAlign.END else LabelAlign.CENTER) }
        for (k in (n - 2) downTo 0) {
            val idx = if (k == n - 2) (0 until n).toList()
            else (0..k + 1).map { Math.round(it * (n - 1).toDouble() / (k + 1)).toInt() }.distinct()
            val p = plan(idx)
            if (fits(p, n, start, end, gap, width)) return p
        }
        return plan(listOf(0))
    }

    private fun span(l: AxisLabel, n: Int, start: Float, end: Float, width: (Int) -> Float): Pair<Float, Float> {
        val w = width(l.index)
        return when (l.align) {
            LabelAlign.START -> start to start + w
            LabelAlign.END -> end - w to end
            LabelAlign.CENTER -> slotCenter(l.index, n, start, end).let { it - w / 2 to it + w / 2 }
        }
    }

    private fun fits(p: List<AxisLabel>, n: Int, start: Float, end: Float, gap: Float, width: (Int) -> Float): Boolean {
        var right = Float.NEGATIVE_INFINITY
        for (l in p) {
            val (a, b) = span(l, n, start, end, width)
            if (a < right + gap) return false
            right = b
        }
        return true
    }

    /** The left edge of an axis label of width [w]. */
    fun labelLeft(l: AxisLabel, n: Int, start: Float, end: Float, w: Float): Float = span(l, n, start, end) { w }.first

    // ---------------------------------------------------------------- info box position

    /**
     * Left edge of an info box [boxW] wide for a bar centred at [anchorX]: to the right of the bar, else to its left, else
     * centred in the [viewW] wide chart; always inside [margin]. [halfGap] = half a bar plus the space beside it.
     */
    fun tooltipLeft(anchorX: Float, halfGap: Float, boxW: Float, viewW: Float, margin: Float): Float {
        val right = anchorX + halfGap
        if (right + boxW <= viewW - margin) return right
        val left = anchorX - halfGap - boxW
        if (left >= margin) return left
        return ((viewW - boxW) / 2).coerceAtLeast(margin)
    }

    /** Top edge of an info box [boxH] tall for the row [rowTop]..[rowBottom]: under the row when it fits in [viewH], else above it, else the top. */
    fun tooltipTop(rowTop: Float, rowBottom: Float, boxH: Float, viewH: Float, margin: Float, gap: Float): Float {
        val below = rowBottom + gap
        if (below + boxH <= viewH - margin) return below
        val above = rowTop - gap - boxH
        if (above >= margin) return above
        return margin
    }

    // ---------------------------------------------------------------- texts

    /** The legend of a stacked chart: each part's share of everything in the period; null percents when nothing was counted. */
    fun legend(s: ChartSeries): List<LegendItem> {
        if (s.kind != ChartKind.TIME || s.seriesNames.size < 2) return emptyList()
        val sums = s.seriesNames.indices.map { i -> s.bars.sumOf { it.values.getOrElse(i) { 0.0 } } }
        val all = sums.sum()
        return s.seriesNames.mapIndexed { i, n -> LegendItem(n, i, if (all > 0.0) Math.round(100.0 * sums[i] / all).toInt() else null) }
    }

    /** What to say instead of a chart (loading, AnkiDroid unreadable, nothing in the period); null when there is something to draw. */
    fun emptyMessage(s: ChartSeries, access: AnkiAccess?, message: String?): String? {
        if (s.loading) return s.source.ifBlank { "Reading AnkiDroid…" }
        if (!s.empty) return null
        val reason = StatsText.reason(access, message)
        return when {
            reason.isNotBlank() && s.source == reason -> reason
            s.kind == ChartKind.CATEGORY -> "Nothing here yet."
            else -> emptyText(s.metric)
        }
    }

    /**
     * "Nothing in the period" for one metric, worded for that metric (R4 fix N2): the metric's noun reads as a unit in the tiles ("HP dealt",
     * "coins earned", "min"), not after "No".
     */
    fun emptyText(m: ChartMetric): String = when (m) {
        ChartMetric.RETENTION -> "No answers counted in this period yet."
        ChartMetric.TIME -> "No study time in this period yet."
        ChartMetric.DUE -> "Nothing due in this period."
        ChartMetric.MONEY -> "No money earned in this period yet."
        ChartMetric.XP -> "No XP earned in this period yet."
        ChartMetric.BUTTONS -> "No answers in this period yet."
        else -> "No ${m.noun} in this period yet." // reviews, new cards, answers, catches, battles won, misses, calendar reviews
    }

    /** The three key numbers above the chart: total, a day on average and the best day (a category chart: its total and its biggest bar). */
    fun tiles(s: ChartSeries): List<StatTile> {
        val m = s.metric
        if (s.kind == ChartKind.CATEGORY) {
            val total = when (m) {
                ChartMetric.DEX_PROGRESS -> StatTile("${Math.round(s.total)} %", "of the Pokédex caught")
                ChartMetric.PER_TYPE -> StatTile(num(s.total), "species caught")
                ChartMetric.SHINIES_RARE -> StatTile(num(s.total), "Pokémon owned")
                ChartMetric.CARD_STATES -> StatTile(num(s.total), "cards counted")
                else -> StatTile(num(s.total), m.noun)
            }
            val best = s.bestLabel?.let { StatTile(categoryValue(m, s.best), (if (m == ChartMetric.DEX_PROGRESS) "furthest: " else "most: ") + it) }
            return listOfNotNull(total, best)
        }
        fun v(x: Double): String = when (m) {
            ChartMetric.TIME -> StatsText.duration(Math.round(x * 60_000.0))
            ChartMetric.RETENTION -> "${Math.round(x)} %"
            else -> num(x)
        }
        val totalLabel = when (m) {
            ChartMetric.TIME -> "studied in total"
            ChartMetric.RETENTION -> "kept (not Again)"
            else -> ChartData.valueText(m, s.total).substringAfter(' ')
        }
        val bestWord = if (m == ChartMetric.DUE) "busiest day" else "best day"
        val best = s.bestDay?.let { StatTile(v(s.best), "$bestWord (${dayShort(it)})") } ?: StatTile("–", bestWord)
        return listOf(StatTile(v(s.total), totalLabel), StatTile(v(s.perDayAvg), "a day on average"), best)
    }

    private fun dayShort(day: String): String =
        runCatching { LocalDate.parse(day) }.getOrNull()?.let { "${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}" } ?: day

    /** Whole numbers with thousands commas, anything else with one decimal. */
    fun num(v: Double): String =
        if (abs(v - Math.rint(v)) < 0.05) String.format(Locale.US, "%,d", Math.rint(v).toLong()) else String.format(Locale.US, "%,.1f", v)

    // ---------------------------------------------------------------- the menus

    /** The periods the chip offers: DUE looks ahead 7 or 30 days only. */
    fun periods(metric: ChartMetric): List<ChartPeriod> =
        if (metric == ChartMetric.DUE) listOf(ChartPeriod.D7, ChartPeriod.D30) else ChartPeriod.values().toList()

    fun periodLabel(metric: ChartMetric, p: ChartPeriod): String {
        val ahead = metric == ChartMetric.DUE
        return when (p) {
            ChartPeriod.D365 -> if (ahead) "Next year" else "Last year"
            else -> "${if (ahead) "Next" else "Last"} ${p.days} days"
        }
    }

    /** The chart's title: "Reviews per day", "Card states". */
    fun title(s: ChartSeries): String =
        if (s.kind == ChartKind.TIME) "${s.metric.label} ${s.grain.label.lowercase(Locale.US)}" else s.metric.label

    /** The small hint on the right of the title (R18 item 234: the calendar has none, its bottom-left line says it). */
    fun hint(s: ChartSeries): String = when (s.kind) {
        ChartKind.HEATMAP -> ""
        ChartKind.CATEGORY -> "tap a bar for details"
        ChartKind.TIME -> "tap a bar for its ${when (s.grain) { ChartGrain.DAY -> "day"; ChartGrain.WEEK -> "week"; ChartGrain.MONTH -> "month" }}"
    }
}
