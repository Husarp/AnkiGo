package com.husarp.ankigo.deck

import com.husarp.ankigo.stats.AnkiQueries
import com.husarp.ankigo.stats.AnkiSeriesQueries
import kotlin.math.roundToInt

/** Read-only card searches; no review-log access or inferred test scores. */
object DeckStatus {
    data class Snapshot(val states: List<Int>, val suspended: Int, val buried: Int,
                        val due: Int, val tomorrow: Int, val introduced: Int,
                        val streak: Int, val todayIncluded: Boolean) {
        val active get() = states.sum()
        val known: Int? get() = if (active == 0) null else (100.0 * states[3] / active).roundToInt()
    }

    fun scope(name: String, includeSubdecks: Boolean): String {
        val escaped = AnkiSeriesQueries.escape(name)
        return "deck:\"$escaped\"" + if (includeSubdecks) "" else " -deck:\"$escaped::*\""
    }

    fun read(name: String, includeSubdecks: Boolean, count: (String) -> Int): Snapshot {
        val scope = scope(name, includeSubdecks)
        fun n(query: String) = count("$scope $query").coerceAtLeast(0)
        val states = listOf(n(AnkiQueries.NEW), n(AnkiQueries.LEARNING) + n(AnkiQueries.RELEARNING),
            n(AnkiQueries.YOUNG), n(AnkiQueries.MATURE))
        val live = " -is:suspended -is:buried"
        val suspended = n(AnkiQueries.SUSPENDED)
        val buried = n(AnkiQueries.BURIED)
        val due = n(AnkiQueries.DUE_NOW + live)
        val tomorrow = n(AnkiQueries.due(1) + live)
        val introduced = n(AnkiQueries.introduced(7) + live)
        val today = n(AnkiQueries.ratedOn(0)) > 0
        var streak = if (today) 1 else 0
        for (day in 1 until 365) {
            if (n(AnkiQueries.ratedOn(day)) == 0) break
            streak++
        }
        return Snapshot(states, suspended, buried, due, tomorrow, introduced, streak, today)
    }
}
