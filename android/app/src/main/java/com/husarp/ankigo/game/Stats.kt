package com.husarp.ankigo.game

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

// TICKET T40: AnkiGo's own per-day log (GameState.history) and what the Stats screen shows. Pure Kotlin, no android.*.
// (Named StudyStats because Model.kt already has the Pokémon `Stats` class.)

/** Records study into [GameState.history] and turns it into the numbers and heatmap of the Stats screen. */
object StudyStats {
    /** History older than this many days is dropped when recording. */
    const val KEEP_DAYS = 400

    /**
     * Adds what [events] say to today's [DayLog]: Counted -> reviews; Defeated -> won; Caught -> caught (+ shinies when
     * shiny) and won (a catch is a won battle). Ignored answers never produce Counted, so they are never counted.
     * Called by Game.onAnswer and Game.throwBall only.
     */
    fun record(state: GameState, events: List<GameEvent>) {
        var reviews = 0; var won = 0; var caught = 0; var shinies = 0
        for (e in events) when (e) {
            is GameEvent.Counted -> reviews++
            is GameEvent.Defeated -> won++
            is GameEvent.Caught -> { won++; caught++; if (e.shiny) shinies++ }
            else -> Unit
        }
        if (reviews + won + caught + shinies == 0 || state.day.isEmpty()) return
        val log = state.history.getOrPut(state.day) { DayLog() }
        log.reviews += reviews; log.won += won; log.caught += caught; log.shinies += shinies
        prune(state)
    }

    /** Drops days older than [KEEP_DAYS] before the game day. */
    fun prune(state: GameState) {
        val day = parse(state.day) ?: return
        val cutoff = day.minusDays(KEEP_DAYS.toLong()).toString()
        state.history.keys.removeAll { it < cutoff }
    }

    /** Heat level of a day: 0 none, 1 under 25 % of the daily [goal], 2 under 50 %, 3 under 100 %, 4 goal reached. */
    fun level(reviews: Int, goal: Int): Int {
        if (reviews <= 0) return 0
        val f = reviews.toDouble() / max(goal, 1)
        return when { f < 0.25 -> 1; f < 0.5 -> 2; f < 1.0 -> 3; else -> 4 }
    }

    class Cell(val day: String, val reviews: Int, val level: Int)

    /** [columns] are weeks, oldest first, the last one is the current week; each has 7 cells, Monday first; a day after today is null. */
    class Heatmap(val columns: List<List<Cell?>>, val monthLabels: List<Pair<Int, String>>, val total: Int)

    class Streak(val current: Int, val best: Int)

    class Summary(
        val today: String,
        val reviewsToday: Int,
        val goal: Int,
        val streak: Streak,
        val heatmap: Heatmap,
        val reviews: Long,
        val battlesWon: Int,
        val catches: Int,
        val shinies: Int,
        val evolutions: Int,
        val bestCombo: Int,
        val daysStudied: Int,
        val avgPerDay: Int,
        val bestDay: Int,
    )

    fun summary(state: GameState, today: String, weeks: Int = 18): Summary {
        val goal = max(state.settings.dailyAverage, 1)
        val live = state.day == today
        val todayReviews = if (live) max(state.reviewsToday, state.history[today]?.reviews ?: 0) else state.history[today]?.reviews ?: 0
        val streak = streak(state, today)
        val totals = dayTotals(state.history.values.map { it.reviews })
        return Summary(
            today = today, reviewsToday = todayReviews, goal = goal, streak = streak,
            heatmap = heatmap(state, today, weeks, todayReviews),
            reviews = state.totalReviews, battlesWon = state.defeats + state.catches, catches = state.catches,
            shinies = state.mons.count { it.shiny }, evolutions = state.evolutions, bestCombo = state.bestCardStreak,
            daysStudied = totals.daysStudied, avgPerDay = totals.avgPerDay, bestDay = totals.bestDay,
        )
    }

    class DayTotals(val daysStudied: Int, val avgPerDay: Int, val bestDay: Int)

    /** Days with any study, the average per study day (rounded) and the best day, from per-day counts (AnkiGo's own or AnkiDroid's). */
    fun dayTotals(counts: Collection<Int>): DayTotals {
        val studied = counts.filter { it > 0 }
        val sum = studied.sumOf { it.toLong() }
        return DayTotals(
            studied.size,
            if (studied.isEmpty()) 0 else ((sum + studied.size / 2) / studied.size).toInt(),
            studied.maxOrNull() ?: 0,
        )
    }

    /** Current streak: the stored one while the goal was reached today or yesterday, else 0. Best never drops below it. */
    fun streak(state: GameState, today: String): Streak {
        val t = parse(today)
        val last = state.lastGoalDay
        val alive = t != null && last != null && (last == today || last == t.minusDays(1).toString())
        val current = if (alive) state.streakDays else 0
        return Streak(current, max(state.bestStreakDays, current))
    }

    /** The heatmap of the last [weeks] weeks ending with the week of [today]. [todayReviews] overrides today's cell. */
    fun heatmap(state: GameState, today: String, weeks: Int, todayReviews: Int? = null): Heatmap {
        val counts = state.history.mapValues { it.value.reviews }.toMutableMap()
        if (todayReviews != null) counts[today] = todayReviews
        return heatmapFromCounts(counts, state.settings.dailyAverage, today, weeks)
    }

    /** The same heatmap from any per-day count map (date "yyyy-MM-dd" -> count, e.g. the cards AnkiDroid says were studied); heat levels are measured against [goal]. */
    fun heatmapFromCounts(counts: Map<String, Int>, goal: Int, today: String, weeks: Int): Heatmap {
        val t = parse(today) ?: return Heatmap(emptyList(), emptyList(), 0)
        val first = t.with(DayOfWeek.MONDAY).minusWeeks((weeks - 1).toLong())
        var total = 0
        val columns = (0 until weeks).map { w ->
            (0 until 7).map { d ->
                val date = first.plusDays((w * 7 + d).toLong())
                if (date.isAfter(t)) null else {
                    val key = date.toString()
                    val r = counts[key] ?: 0
                    total += r
                    Cell(key, r, level(r, goal))
                }
            }
        }
        val labels = ArrayList<Pair<Int, String>>()
        for (w in 0 until weeks) {
            val monday = first.plusWeeks(w.toLong())
            if (w == 0 || monday.month != monday.minusWeeks(1).month) {
                labels += w to monday.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            }
        }
        // A first column that is followed at once by another label would overprint it: keep the later one.
        if (labels.size > 1 && labels[1].first == 1) labels.removeAt(0)
        return Heatmap(columns, labels, total)
    }

    private fun parse(day: String): LocalDate? =
        try { LocalDate.parse(day) } catch (e: DateTimeParseException) { null }
}
