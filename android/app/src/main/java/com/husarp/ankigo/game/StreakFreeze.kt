package com.husarp.ankigo.game

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

object StreakFreeze {
    const val ITEM = "streak-freeze"
    const val CAP = 3

    fun price(state: GameState): Int = 2000 + state.count(ITEM).coerceIn(0, CAP - 1) * 1000

    /** Resolve only completed game days. An uncovered gap costs nothing and cannot be revived later. */
    fun rollover(state: GameState, today: String): List<GameEvent> {
        if (state.streakDays <= 0) return emptyList()
        val last = runCatching { LocalDate.parse(state.streakThroughDay ?: state.lastGoalDay) }.getOrNull() ?: return emptyList()
        val end = LocalDate.parse(today).minusDays(1)
        val missed = ChronoUnit.DAYS.between(last, end)
        if (missed <= 0) return emptyList()
        if (missed > state.count(ITEM)) {
            state.streakDays = 0
            return emptyList()
        }
        val used = missed.toInt()
        repeat(used) { Economy.take(state, ITEM) }
        state.streakThroughDay = end.toString()
        return listOf(GameEvent.StreakSaved(state.streakDays, used, state.count(ITEM), last.plusDays(1).toString(), end.toString()))
    }

    /** Only real rewarded wins; normal drops remain available when capped or already awarded today. */
    fun battleDrop(state: GameState, rng: Random, events: MutableList<GameEvent>) {
        if (state.practiceRewards || state.day.isEmpty() || state.battleFreezeDay == state.day || state.count(ITEM) >= CAP) return
        if (rng.nextInt(1000) >= 2) return
        Economy.give(state, ITEM)
        state.battleFreezeDay = state.day
        events += GameEvent.ItemFound(ITEM, state.count(ITEM))
    }

    fun title(saved: GameEvent.StreakSaved) = "Streak saved · ${saved.days} days"
    fun detail(saved: GameEvent.StreakSaved): String =
        "Used ${saved.used} ${if (saved.used == 1) "freeze" else "freezes"} · " + if (saved.left == 0) "none left" else "${saved.left} left"
    fun protectedDays(saved: GameEvent.StreakSaved): String {
        val start = LocalDate.parse(saved.from)
        val end = LocalDate.parse(saved.through)
        val format = java.time.format.DateTimeFormatter.ofPattern("EEE", java.util.Locale.US)
        val days = (0..ChronoUnit.DAYS.between(start, end).coerceIn(0, 2).toInt()).map { start.plusDays(it.toLong()).format(format) }
        return (if (days.size == 1) days.single() else days.dropLast(1).joinToString(", ") + " and " + days.last()) + " protected"
    }
}
