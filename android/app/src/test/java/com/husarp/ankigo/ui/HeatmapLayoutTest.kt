package com.husarp.ankigo.ui

import com.husarp.ankigo.game.StudyStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

// TICKET R9-W2-D5: the geometry of the Stats calendar (strip for 7 days, rows with a new row per month for 30, week columns for 90 / 365).
class HeatmapLayoutTest {
    private val W = 330f

    /** The days the Stats page hands the view: [periodDays] ending today, inside a [weeks]-week grid. */
    private fun days(today: String, periodDays: Int, weeks: Int): List<HeatmapLayout.Day> {
        val t = LocalDate.parse(today)
        val gridFirst = t.with(DayOfWeek.MONDAY).minusWeeks((weeks - 1).toLong())
        val periodFirst = t.minusDays((periodDays - 1).toLong())
        val first = if (periodFirst.isAfter(gridFirst)) periodFirst else gridFirst
        val counts = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(t) }.associate { it.toString() to (it.dayOfMonth % 5) * 10 }
        val h = StudyStats.heatmapFromCounts(counts, 40, today, weeks, first.toString())
        return h.columns.flatten().filterNotNull().map { HeatmapLayout.Day(it.day, it.level) }
    }

    private fun layout(mode: HeatmapLayout.Mode, today: String, periodDays: Int, weeks: Int, w: Float = W) =
        HeatmapLayout.layout(mode, days(today, periodDays, weeks), today, w)

    private fun HeatmapLayout.Result.cell(day: String) = cells.first { it.day == day }
    private fun HeatmapLayout.Result.rowCount() = cells.map { it.y }.distinct().size

    // ---------------------------------------------------------------- 7 days: the strip

    @Test fun stripAndRowsIgnoreDaysBeyondTheirPeriod() {
        val six = days("2026-10-18", 42, 7)
        assertTrue(six.size >= 42)
        assertEquals(7, HeatmapLayout.layout(HeatmapLayout.Mode.STRIP, six, "2026-10-18", W).cells.size)
        assertEquals(30, HeatmapLayout.layout(HeatmapLayout.Mode.ROWS, six, "2026-10-18", W).cells.size)
    }

    @Test fun stripIsOneRowEndingWithToday() {
        val r = layout(HeatmapLayout.Mode.STRIP, "2026-10-18", 7, 4)
        assertEquals(7, r.cells.size)
        assertEquals(1, r.rowCount())
        assertEquals((12..18).map { it.toString() }, r.cells.map { it.number })
        assertTrue(r.cells.last().today)
        assertEquals(1, r.cells.count { it.today })
        assertEquals("2026-10-18", r.cells.last().day)
        assertEquals(W, r.cells.last().x + r.cells.last().w, 0.01f)       // uses the whole width
        assertEquals(0f, r.cells.first().x, 0.001f)
        assertTrue(r.cells.zipWithNext().all { (a, b) -> a.x < b.x })       // oldest left
        assertEquals(r.cells.first().y + r.cells.first().h + 20f, r.height, 0.01f)
    }

    @Test fun stripHasAllSevenLettersUnderTheCells() {
        val r = layout(HeatmapLayout.Mode.STRIP, "2026-10-18", 7, 4)    // Mon 12 .. Sun 18
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), r.letters.map { it.text })
        for ((i, day) in (12..18).map { "2026-10-$it" }.withIndex()) {
            val c = r.cell(day)
            val l = r.letters[i]
            assertEquals(c.x + c.w / 2, l.x, 0.01f)
            assertTrue(l.centered)
            assertTrue(l.cy > c.y + c.h)                                      // under the cell
        }
    }

    @Test fun stripNamesTheMonthAboveTheFirstCellAndAboveADayOne() {
        val plain = layout(HeatmapLayout.Mode.STRIP, "2026-10-18", 7, 4)
        assertEquals(listOf("Oct"), plain.months.map { it.text })
        assertEquals(0f, plain.months[0].x, 0.001f)
        assertTrue(plain.months[0].cy < plain.cells[0].y)
        val change = layout(HeatmapLayout.Mode.STRIP, "2026-10-03", 7, 4)  // Sep 27 .. Oct 3
        assertEquals(listOf("Sep", "Oct"), change.months.map { it.text })
        assertEquals(change.cell("2026-10-01").x, change.months[1].x, 0.001f)
    }

    // ---------------------------------------------------------------- 30 days: rows ending today

    private fun rowsOf(r: HeatmapLayout.Result): List<List<HeatmapLayout.Cell>> =
        r.cells.groupBy { it.y }.toSortedMap().values.map { row -> row.sortedBy { it.x } }

    private fun assertTodayBottomRight(r: HeatmapLayout.Result) {
        val today = r.cells.single { it.today }
        assertEquals(r.cells.maxOf { it.x }, today.x, 0.001f)
        assertEquals(r.cells.maxOf { it.y }, today.y, 0.001f)
        assertEquals(r.cells.last().day, today.day)
    }

    @Test fun rowsEndWithTodayBottomRightOnASunday() {
        val r = layout(HeatmapLayout.Mode.ROWS, "2026-10-18", 30, 6)     // Sat Sep 19 .. Sun Oct 18
        assertEquals(30, r.cells.size)
        assertTodayBottomRight(r)
        val cw = (W - 24f) / 7
        assertEquals(6 * (cw + 4f), r.cells.last().x, 0.01f)
        assertEquals(cw, r.cells[0].w, 0.01f)
        assertEquals(34f, r.cells[0].h, 0.001f)                           // min(cell, 34)
        val rows = rowsOf(r)
        // Sep 19-20 | Sep 21-27 | Sep 28-30 | Oct 1-4 | Oct 5-11 | Oct 12-18: the month change starts a new row
        assertEquals(listOf(2, 7, 3, 4, 7, 7), rows.map { it.size })
        assertEquals(r.height, 18f + rows.size * 38f, 0.01f)
    }

    @Test fun aNewMonthStartsANewRowInItsWeekdaysColumn() {
        val r = layout(HeatmapLayout.Mode.ROWS, "2026-10-18", 30, 6)
        val rows = rowsOf(r)
        val oct1 = r.cell("2026-10-01")                                    // a Thursday: column 3 when Monday is column 0
        val sep30 = r.cell("2026-09-30")
        assertTrue(oct1.y > sep30.y)
        val step = (W - 24f) / 7 + 4f
        assertEquals(3 * step, oct1.x, 0.01f)
        assertEquals(oct1.x, rows[3][0].x, 0.001f)                         // first in its row
        // the previous row stays short: Sep 28-30 then empty
        assertEquals("2026-09-30", rows[2].last().day)
        assertEquals(2 * step, rows[2].last().x, 0.01f)
    }

    @Test fun rowsWhenTodayIsAWednesday() {
        val r = layout(HeatmapLayout.Mode.ROWS, "2026-10-14", 30, 6)      // Tue Sep 15 .. Wed Oct 14
        assertTodayBottomRight(r)
        val step = (W - 24f) / 7 + 4f
        // columns are turned: the last one is a Wednesday, the first a Thursday
        assertEquals(0f, r.cell("2026-10-01").x, 0.01f)                    // a Thursday = the first column
        assertEquals(6 * step, r.cell("2026-09-30").x, 0.01f)             // the Wednesday before ends its row
        assertTrue(r.cell("2026-10-01").y > r.cell("2026-09-30").y)
    }

    @Test fun rowLettersFollowTheTurnedColumns() {
        val r = layout(HeatmapLayout.Mode.ROWS, "2026-10-14", 30, 6)      // last column Wednesday
        // columns, left to right: Thu Fri Sat Sun Mon Tue Wed
        assertEquals(listOf("T", "F", "S", "S", "M", "T", "W"), r.letters.map { it.text })
        val step = (W - 24f) / 7 + 4f
        val cw = (W - 24f) / 7
        assertEquals((0..6).map { it * step + cw / 2 }, r.letters.map { it.x })
        assertTrue(r.letters.all { it.centered && it.cy < 14f })           // above the grid
        val sunday = layout(HeatmapLayout.Mode.ROWS, "2026-10-18", 30, 6)
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), sunday.letters.map { it.text })
    }

    @Test fun rowsMonthNameOnlyWithEmptyCellsBeforeTheFirst() {
        // today Wed Oct 14: Oct 1 sits in the first column -> no "Oct"; the first row (Tue Sep 15 in column 5) has leading space -> "Sep"
        val r = layout(HeatmapLayout.Mode.ROWS, "2026-10-14", 30, 6)
        assertEquals(listOf("Sep"), r.months.map { it.text })
        val m = r.months[0]
        val first = r.cells[0]
        assertEquals(0f, m.x, 0.001f)
        assertFalse(m.centered)
        assertEquals(first.y + first.h / 2, m.cy, 0.01f)
        assertTrue(m.x < first.x)                                          // in the empty cells before it
        // today Sun Oct 18: Oct 1 is a Thursday (column 3) -> "Oct" in the three empty cells before it
        val sun = layout(HeatmapLayout.Mode.ROWS, "2026-10-18", 30, 6)
        assertEquals(listOf("Sep", "Oct"), sun.months.map { it.text })
        assertEquals(sun.cell("2026-10-01").y + sun.cell("2026-10-01").h / 2, sun.months[1].cy, 0.01f)
    }

    @Test fun rowsWhenTodayIsTheFirstOfTheMonth() {
        val r = layout(HeatmapLayout.Mode.ROWS, "2026-10-01", 30, 6)      // Wed Sep 2 .. Thu Oct 1
        assertEquals(30, r.cells.size)
        assertTodayBottomRight(r)
        val rows = rowsOf(r)
        assertEquals(listOf("2026-10-01"), rows.last().map { it.day })     // alone on its row, in the last column
        assertEquals(6 * ((W - 24f) / 7 + 4f), rows.last()[0].x, 0.01f)
        assertEquals("Oct", r.months.last().text)
        assertEquals(rows.last()[0].y + rows.last()[0].h / 2, r.months.last().cy, 0.01f)
    }

    @Test fun rowsWhenAMonthStartsOnAMondayHasNoMonthNameForIt() {
        val r = layout(HeatmapLayout.Mode.ROWS, "2026-06-14", 30, 6)      // Sat May 16 .. Sun Jun 14, Jun 1 is a Monday
        assertEquals(30, r.cells.size)
        assertTodayBottomRight(r)
        assertEquals(0f, r.cell("2026-06-01").x, 0.001f)
        assertTrue(r.cell("2026-06-01").y > r.cell("2026-05-31").y)
        assertEquals(listOf("May"), r.months.map { it.text })              // only the first row (column 5) has leading space
        assertEquals(listOf(2, 7, 7, 7, 7), rowsOf(r).map { it.size })
    }

    @Test fun rowsShrinkTheCellHeightOnANarrowCard() {
        val r = layout(HeatmapLayout.Mode.ROWS, "2026-10-18", 30, 6, 200f)
        val cw = (200f - 24f) / 7
        assertEquals(cw, r.cells[0].w, 0.01f)
        assertEquals(cw, r.cells[0].h, 0.01f)                             // below 34 dp the cell stays square
    }

    @Test fun rowsAndStripShowTheDateInsideAndLongOnesDoNot() {
        assertTrue(layout(HeatmapLayout.Mode.ROWS, "2026-10-18", 30, 6).cells.all { it.number != null })
        assertTrue(layout(HeatmapLayout.Mode.WEEKS, "2026-10-18", 90, 13).cells.all { it.number == null })
    }

    // ---------------------------------------------------------------- 90 / 365 days: week columns

    @Test fun weeksOf90DaysHave13ColumnsMondayOnTopAndTodayInItsWeekdayRow() {
        val r = layout(HeatmapLayout.Mode.WEEKS, "2026-10-14", 90, 13)    // Wednesday
        assertEquals(13, r.cells.map { it.x }.distinct().size)
        val top = 16f
        // every Monday is in the top row
        for (c in r.cells) {
            val day = LocalDate.parse(c.day)
            if (day.dayOfWeek == DayOfWeek.MONDAY) assertEquals(top, c.y, 0.001f)
        }
        val today = r.cell("2026-10-14")
        assertTrue(today.today)
        assertEquals(r.cells.maxOf { it.x }, today.x, 0.001f)              // rightmost column
        assertEquals(top + 2 * (today.h + 3f), today.y, 0.01f)           // Wednesday = row 2
        // nothing after today
        assertTrue(r.cells.all { it.day <= "2026-10-14" })
        assertEquals(0, r.cells.count { it.x == today.x && it.y > today.y })
        assertEquals(3, r.cells.count { it.x == today.x })                  // Mon, Tue, Wed of this week
    }

    @Test fun weeksCellSizeFollowsTheWidth() {
        val r = layout(HeatmapLayout.Mode.WEEKS, "2026-10-14", 90, 13)
        val cw = (W - 22f - 3f * 12) / 13
        assertEquals(cw, r.cells[0].w, 0.01f)
        assertEquals(cw, r.cells[0].h, 0.01f)
        assertEquals(10f, r.labelSp, 0f)
        assertEquals(16f + 7 * cw + 6 * 3f, r.height, 0.01f)
        val wide = layout(HeatmapLayout.Mode.WEEKS, "2026-10-14", 90, 13, 1000f)
        assertEquals(40f, wide.cells[0].w, 0.001f)                          // at most 40 dp
    }

    @Test fun aYearHas53ColumnsTinyCellsAtLeast7DpTallAndASmallerGap() {
        val r = layout(HeatmapLayout.Mode.WEEKS, "2026-10-14", 365, 53)
        assertEquals(53, r.cells.map { it.x }.distinct().size)
        val cw = (W - 22f - 2f * 52) / 53
        assertEquals(cw, r.cells[0].w, 0.01f)
        assertEquals(7f, r.cells[0].h, 0.001f)                              // height >= 7 dp
        assertEquals(8.5f, r.labelSp, 0f)                                   // small cells: smaller labels
        assertEquals(16f + 7 * 7f + 6 * 2f, r.height, 0.01f)
        assertEquals(2f, r.cell("2026-10-14").y - r.cell("2026-10-13").y - 7f, 0.001f)
        assertTrue(r.cells.last().today)
        assertTrue(r.cells.all { it.day <= "2026-10-14" })
    }

    @Test fun weekLettersAreOnTheLeftAtTheirRows() {
        val r = layout(HeatmapLayout.Mode.WEEKS, "2026-10-14", 90, 13)
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), r.letters.map { it.text })
        val ch = r.cells[0].h
        val rowsAt = (0..6).map { 16f + it * (ch + 3f) + ch / 2 }
        assertEquals(rowsAt, r.letters.map { it.cy })
        assertTrue(r.letters.all { it.x == 0f && !it.centered })
        assertTrue(r.cells.all { it.x >= 22f })
    }

    @Test fun weekMonthNamesAreOnTopAndNeverCloserThan24Dp() {
        for (weeks in listOf(13 to 90, 53 to 365)) {
            val r = layout(HeatmapLayout.Mode.WEEKS, "2026-10-14", weeks.second, weeks.first)
            assertTrue(r.months.isNotEmpty())
            assertTrue(r.months.all { it.cy < 16f && !it.centered })
            assertEquals(22f, r.months.first().x, 0.001f)                 // the first column is always named
            assertTrue(r.months.zipWithNext().all { (a, b) -> b.x - a.x > 24f })
        }
        val year = layout(HeatmapLayout.Mode.WEEKS, "2026-10-14", 365, 53)
        assertTrue(year.months.size >= 8)
        for (w in listOf(320f, 280f, 412f)) {                                // no name runs past the right edge
            val narrow = layout(HeatmapLayout.Mode.WEEKS, "2026-10-14", 365, 53, w)
            assertTrue(narrow.months.all { it.x + 20f <= w })
        }
    }

    // ---------------------------------------------------------------- tapping

    @Test fun hitTestFindsTheDayAndNullBesideTheDays() {
        for ((mode, p) in listOf(HeatmapLayout.Mode.STRIP to (7 to 4), HeatmapLayout.Mode.ROWS to (30 to 6), HeatmapLayout.Mode.WEEKS to (90 to 13))) {
            val r = layout(mode, "2026-10-14", p.first, p.second)
            for (c in r.cells) assertEquals(c.day, r.hit(c.x + c.w / 2, c.y + c.h / 2))
            assertNull(r.hit(-5f, 20f))
            assertNull(r.hit(W + 20f, 20f))
            assertNull(r.hit(10f, r.height + 30f))
            assertNull(r.hit(10f, -5f))
        }
    }

    @Test fun hitTestIsNullOnEmptyCellsAndAfterToday() {
        val rows = layout(HeatmapLayout.Mode.ROWS, "2026-10-18", 30, 6)
        val oct1 = rows.cell("2026-10-01")
        assertNull(rows.hit(oct1.x - 2 * (oct1.w + 4f) + oct1.w / 2, oct1.y + oct1.h / 2))   // an empty cell before the 1st
        val sep30 = rows.cell("2026-09-30")
        assertNull(rows.hit(sep30.x + 2 * (sep30.w + 4f) + 3f, sep30.y + sep30.h / 2))      // trailing empty cell of the Sep row
        val weeks = layout(HeatmapLayout.Mode.WEEKS, "2026-10-14", 90, 13)
        val today = weeks.cell("2026-10-14")
        assertNull(weeks.hit(today.x + today.w / 2, today.y + 2 * (today.h + 3f) + today.h / 2))          // Friday of this week: not drawn
        assertNotNull(weeks.hit(today.x + today.w / 2, today.y + today.h / 2))
    }

    @Test fun hitTestGivesTheGapToTheNearerCell() {
        val r = layout(HeatmapLayout.Mode.STRIP, "2026-10-18", 7, 4)
        val a = r.cells[2]
        val b = r.cells[3]
        assertEquals(a.day, r.hit(a.x + a.w + 1f, a.y + 5f))                  // just right of cell a
        assertEquals(b.day, r.hit(b.x - 1f, b.y + 5f))                        // just left of cell b
    }

    @Test fun emptyOrBadInputGivesAnEmptyLayout() {
        assertTrue(HeatmapLayout.layout(HeatmapLayout.Mode.ROWS, emptyList(), "2026-10-14", W).cells.isEmpty())
        assertTrue(HeatmapLayout.layout(HeatmapLayout.Mode.ROWS, listOf(HeatmapLayout.Day("nope", 0)), "2026-10-14", W).cells.isEmpty())
        assertTrue(HeatmapLayout.layout(HeatmapLayout.Mode.WEEKS, days("2026-10-14", 90, 13), "2026-10-14", 0f).cells.isEmpty())
    }
}
