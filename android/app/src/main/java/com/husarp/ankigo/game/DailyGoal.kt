package com.husarp.ankigo.game

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * AnkiGo's own daily goal (AnkiDroid has none). One number for every day ([Settings.dailyAverage], "Same every day") or
 * one per weekday ([Settings.goalByWeekday], Monday first). Everything that shows or checks the goal asks here for the
 * goal of a GAME day (see Game.dayKey: a day starts at the player's day-start hour, so a 2 a.m. answer before it belongs
 * to yesterday's weekday). Pure, no android.*.
 */
object DailyGoal {
    const val MIN = 20          // the goal picker's range (Settings -> Game -> Daily goal)
    const val MAX = 500         // the picker's top, and the cap of each weekday-table entry (not of the "same every day" base)
    const val STEP = 10

    /** The goal of game day [day] ("yyyy-MM-dd"); a missing / unreadable day, or no weekday table, gives the base goal. */
    fun forSettings(s: Settings, day: String?): Int = pick(s.dailyAverage, s.goalByWeekday, day)

    /** The same from the two stored parts: the [base] goal and the optional 7-value [table] (Monday first). */
    fun pick(base: Int, table: List<Int>?, day: String?): Int {
        val date = if (table == null || table.size != 7 || day == null) null else parse(day)
        // the weekday table keeps the picker's bounds; the legacy base value (an imported goal of 1000, say) is only kept >= 1
        return if (date == null) base.coerceAtLeast(1) else table!![date.dayOfWeek.value - 1].coerceIn(1, MAX)
    }

    /** The goal of game day [day]. */
    fun forDay(state: GameState, day: String): Int = forSettings(state.settings, day)

    /** Today's goal: the one for the current game day ([GameState.day]). */
    fun today(state: GameState): Int = forSettings(state.settings, state.day.ifEmpty { null })

    /** A saved table made safe: exactly 7 values (else null = same every day), each 1..[MAX]. */
    fun cleanTable(t: List<Int>?): List<Int>? = if (t == null || t.size != 7) null else t.map { it.coerceIn(1, MAX) }

    private fun parse(day: String): LocalDate? = try { LocalDate.parse(day) } catch (e: DateTimeParseException) { null }
}
