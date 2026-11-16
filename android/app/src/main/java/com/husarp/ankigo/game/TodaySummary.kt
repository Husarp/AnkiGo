package com.husarp.ankigo.game

/** All decks in one game day. Earnings come from history, never the wallet or current bag. */
class TodaySummary(state: GameState, val day: String, val studyMillis: Long) {
    val log = state.history[day] ?: DayLog()
    val reviews = if (state.day == day) maxOf(state.reviewsToday, log.reviews) else log.reviews
    val goal = DailyGoal.forDay(state, day).coerceAtLeast(1)
    val answered = log.buttons.drop(1).sum()
    val correct = if (answered > 0) "${(answered - log.buttons[1]) * 100 / answered}%" else "—"
    val defeated = (log.won - log.caught).coerceAtLeast(0)
    val items = log.foundItems.toSortedMap()
}
