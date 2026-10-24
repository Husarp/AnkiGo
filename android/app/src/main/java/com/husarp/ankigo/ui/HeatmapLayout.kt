package com.husarp.ankigo.ui

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * TICKET R9-W2-D5: the geometry of the Stats calendar, pure (no android.*), all numbers in dp. [HeatmapView] draws what this
 * returns. Three looks (docs/design/round9: D5-B, D5-C with month rows, D5-L1):
 *  - [Mode.STRIP] (7 days): one row of tall cells, oldest left, today at the right, the date inside, M W F S under the cells,
 *    the month name above the first cell and above a day 1.
 *  - [Mode.ROWS] (30 days): 7 columns turned so the LAST one is today's weekday, one row per week, dates inside, M W F S on top;
 *    a new month always starts a new row (in its weekday's column), and the month name sits in the empty cells before the 1st.
 *  - [Mode.WEEKS] (90 / 365 days): weeks as columns, Monday on top, cells sized to the width, M W F S on the left, month names on top.
 */
object HeatmapLayout {
    enum class Mode { STRIP, ROWS, WEEKS }

    /** One day of the period: its game date "yyyy-MM-dd" and heat level 0..4. */
    class Day(val key: String, val level: Int)

    /** A drawn day: its box, and the date number to print inside ([number] null in [Mode.WEEKS]). */
    class Cell(val day: String, val level: Int, val x: Float, val y: Float, val w: Float, val h: Float, val number: String?, val today: Boolean) {
        val radius: Float get() = min(3f, min(w / 4f, h / 4f))
    }

    /** A text: [cy] is the vertical middle of the line; [x] is the left edge, or the middle when [centered]. */
    class Label(val text: String, val x: Float, val cy: Float, val centered: Boolean)

    class Result(
        val cells: List<Cell>,
        val letters: List<Label>,
        val months: List<Label>,
        val height: Float,
        /** Text size (sp) of the letters and month names (8.5 when the cells are below 10 dp). */
        val labelSp: Float,
        private val slop: Float,
    ) {
        /** The day under ([x], [y]) dp (the gaps between cells count for the nearest cell); null beside the days, in empty cells and on the labels. */
        fun hit(x: Float, y: Float): String? {
            for (c in cells) {
                if (x >= c.x - slop && x < c.x + c.w + slop && y >= c.y - slop && y < c.y + c.h + slop) return c.day
            }
            return null
        }
    }

    private val EMPTY = Result(emptyList(), emptyList(), emptyList(), 0f, 10f, 0f)
    /** R18 item 234: all seven weekday letters (Monday = 0). */
    private val LET = mapOf(0 to "M", 1 to "T", 2 to "W", 3 to "T", 4 to "F", 5 to "S", 6 to "S")
    /** R18 item 234: the extra space between the letters and the grid, dp. */
    private const val LETTER_GAP = 4f

    /** [days]: consecutive game days, oldest first, the last one is [today]; [widthDp] the width of the view. */
    fun layout(mode: Mode, allDays: List<Day>, today: String, widthDp: Float): Result {
        // STRIP shows only the last 7 days and ROWS the last 30, whatever the list holds
        val days = when (mode) { Mode.STRIP -> allDays.takeLast(7); Mode.ROWS -> allDays.takeLast(30); Mode.WEEKS -> allDays }
        if (days.isEmpty() || widthDp <= 0f) return EMPTY
        val dates = days.map { parse(it.key) ?: return EMPTY }
        val todayDate = parse(today) ?: dates.last()
        return when (mode) {
            Mode.STRIP -> strip(days, dates, today, widthDp)
            Mode.ROWS -> rows(days, dates, today, todayDate, widthDp)
            Mode.WEEKS -> weeks(days, dates, today, widthDp)
        }
    }

    private fun parse(key: String): LocalDate? = try { LocalDate.parse(key) } catch (e: Exception) { null }
    private fun dow(d: LocalDate): Int = d.dayOfWeek.value - 1 // 0 = Monday
    private fun month(d: LocalDate): String = d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    private fun strip(days: List<Day>, dates: List<LocalDate>, today: String, w: Float): Result {
        val n = days.size
        val gap = if (n > 14) 2f else 4f
        val cw = (w - gap * (n - 1)) / n
        val ch = if (n > 14) 30f else 36f
        val top = 14f
        val cells = ArrayList<Cell>(n)
        val letters = ArrayList<Label>()
        val months = ArrayList<Label>()
        for (i in 0 until n) {
            val x = i * (cw + gap)
            val d = days[i]
            cells += Cell(d.key, d.level, x, top, cw, ch, if (n <= 14) dates[i].dayOfMonth.toString() else null, d.key == today)
            LET[dow(dates[i])]?.let { letters += Label(it, x + cw / 2, top + ch + 12f - 3.5f + LETTER_GAP, true) }
            if (i == 0 || dates[i].dayOfMonth == 1) months += Label(month(dates[i]), x, 9f - 3.5f, false)
        }
        return Result(cells, letters, months, top + ch + 16f + LETTER_GAP, if (cw < 10f) 8.5f else 10f, gap / 2)
    }

    private fun rows(days: List<Day>, dates: List<LocalDate>, today: String, todayDate: LocalDate, w: Float): Result {
        val gap = 4f
        val cw = (w - gap * 6) / 7
        val ch = min(cw, 34f)
        val top = 14f + LETTER_GAP
        val colOf = { d: LocalDate -> ((dow(d) - dow(todayDate) + 6) % 7 + 7) % 7 } // today's weekday = column 6
        val cells = ArrayList<Cell>(days.size)
        val months = ArrayList<Label>()
        var row = -1
        for (i in days.indices) {
            val c = colOf(dates[i])
            // a new row at the first day, at the first column, and whenever the month changes
            if (i == 0 || c == 0 || dates[i].month != dates[i - 1].month) {
                row++
                if (c > 0) months += Label(month(dates[i]), 0f, top + row * (ch + gap) + ch / 2, false)
            }
            cells += Cell(days[i].key, days[i].level, c * (cw + gap), top + row * (ch + gap), cw, ch, dates[i].dayOfMonth.toString(), days[i].key == today)
        }
        val letters = ArrayList<Label>()
        for (c in 0..6) {
            val wd = (((dow(todayDate) - (6 - c)) % 7) + 7) % 7
            LET[wd]?.let { letters += Label(it, c * (cw + gap) + cw / 2, 10f - 3.5f, true) }
        }
        return Result(cells, letters, months, top + (row + 1) * (ch + gap), if (cw < 10f) 8.5f else 10f, gap / 2)
    }

    private fun weeks(days: List<Day>, dates: List<LocalDate>, today: String, w: Float): Result {
        val lead = dow(dates.first())
        val cols = ceil((lead + days.size) / 7.0).toInt()
        val leftW = 18f + LETTER_GAP
        val topH = 16f
        val gap = if (cols > 30) 2f else 3f
        val cw = min(40f, (w - leftW - gap * (cols - 1)) / cols)
        val chh = max(cw, 7f)
        val fs = if (chh < 10f) 8.5f else 10f
        val cells = ArrayList<Cell>(days.size)
        for (i in days.indices) {
            val slot = lead + i
            cells += Cell(days[i].key, days[i].level, leftW + (slot / 7) * (cw + gap), topH + (slot % 7) * (chh + gap), cw, chh, null, days[i].key == today)
        }
        val letters = ArrayList<Label>()
        for (r in 0..6) LET[r]?.let { letters += Label(it, 0f, topH + r * (chh + gap) + chh / 2, false) }
        val months = ArrayList<Label>()
        val firstMonday = dates.first().minusDays(lead.toLong())
        var lastMonth = -1
        var lastX = -99f
        for (c in 0 until cols) {
            val start = firstMonday.plusWeeks(c.toLong())
            val x = leftW + c * (cw + gap)
            // a name needs ~20 dp: one that would run past the right edge is left out
            if (start.monthValue != lastMonth) {
                if ((x - lastX > 24f && x + 20f <= w) || c == 0) { months += Label(month(start), x, topH - 5f - fs * 0.35f, false); lastX = x }
                lastMonth = start.monthValue
            }
        }
        return Result(cells, letters, months, topH + 7 * chh + 6 * gap, fs, gap / 2)
    }
}
