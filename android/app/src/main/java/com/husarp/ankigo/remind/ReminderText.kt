package com.husarp.ankigo.remind

/**
 * The words of the reminders (PLAN 85, pick 4): the compact notification text and the one-line summaries of the settings
 * rows. Pure. Everything is built to stay short whatever the numbers are: counts go through [compact] ("2k", "12k"), deck
 * names are cut, at most three decks are listed, so a notification never grows wide or tall.
 */
object ReminderText {
    /** Longest deck name shown in a notification line; longer ones end in "…". */
    const val DECK_NAME_MAX = 24

    /** Decks listed in a notification before "and N more decks". */
    const val DECKS_SHOWN = 3

    /** 999, 1.2k, 12k, 2k, 150k, 1.5M: at most four characters for any count a person can reach (cut down, never rounded up). */
    fun compact(n: Int): String {
        val v = n.coerceAtLeast(0)
        return when {
            v < 1_000 -> v.toString()
            v < 10_000 -> tenths(v, 1_000, "k")
            v < 1_000_000 -> "${v / 1_000}k"
            v < 10_000_000 -> tenths(v, 1_000_000, "M")
            else -> "${v / 1_000_000}M"
        }
    }

    private fun tenths(v: Int, unit: Int, suffix: String): String {
        val t = v / (unit / 10)
        return if (t % 10 == 0) "${t / 10}$suffix" else "${t / 10}.${t % 10}$suffix"
    }

    private fun count(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "${compact(n)} $many"

    // ---- the notification ----

    /** The title of the cards-due notice: "42 cards waiting". */
    fun cardsTitle(total: Int) = count(total, "card waiting", "cards waiting")

    /** The last part of a deck's name ("Japanese::Core 2k" -> "Core 2k"), cut to [DECK_NAME_MAX] characters. */
    fun deckName(name: String): String {
        val leaf = name.substringAfterLast("::").trim().ifEmpty { name }
        return if (leaf.length <= DECK_NAME_MAX) leaf else leaf.take(DECK_NAME_MAX - 1).trimEnd() + "…"
    }

    /** "Japanese Core 2k — 30" */
    fun deckLine(name: String, due: Int) = "${deckName(name)} — ${compact(due)}"

    /** One line per deck (biggest first, as given), at most [DECKS_SHOWN], then "and 2 more decks". */
    fun deckLines(decks: List<Pair<String, Int>>): List<String> {
        val lines = decks.take(DECKS_SHOWN).map { deckLine(it.first, it.second) }
        val more = decks.size - DECKS_SHOWN
        return if (more > 0) lines + "and $more more ${if (more == 1) "deck" else "decks"}" else lines
    }

    /** "Daily goal 64 / 100 · 6-day streak" (the streak only when there is one); "Daily goal reached" once it is. */
    fun goalLine(done: Int, target: Int, streakDays: Int): String {
        val head = if (done >= target) "Daily goal reached" else "Daily goal ${compact(done)} / ${compact(target)}"
        return if (streakDays > 0) "$head · $streakDays-day streak" else head
    }

    fun streakTitle(days: Int) = "Your $days-day streak ends tonight"

    /** "10 reviews keep it going." */
    fun streakLine(toGo: Int) = if (toGo == 1) "1 review keeps it going." else "${compact(toGo)} reviews keep it going."

    /** The own goal: "36 reviews to your goal" / "12 new cards to your goal". */
    fun ownGoalTitle(toGo: Int, newCards: Boolean) =
        if (newCards) count(toGo, "new card", "new cards") + " to your goal" else count(toGo, "review", "reviews") + " to your goal"

    /** "64 / 100 reviews today" */
    fun ownGoalLine(done: Int, target: Int, newCards: Boolean) =
        "${compact(done)} / ${compact(target)} ${if (newCards) "new cards" else "reviews"} today"

    fun dailyGoalTitle(toGo: Int) = count(toGo, "review", "reviews") + " to today's goal"

    fun dailyGoalLine(done: Int, target: Int, streakDays: Int) = goalLine(done, target, streakDays)

    /** "Poké Ball, Great Ball, Thunder Stone and 2 more" */
    fun shopLine(names: List<String>): String {
        val shown = names.take(DECKS_SHOWN).joinToString(", ")
        val more = names.size - DECKS_SHOWN
        return if (more > 0) "$shown and $more more" else shown
    }

    // ---- the settings rows ----

    /** "18:00" (minutes of the day). */
    fun timeText(minute: Int): String {
        val m = minute.coerceIn(0, 1439)
        return "%02d:%02d".format(m / 60, m % 60)
    }

    /** "Off" for a reminder that is off, else a one-line summary ("20 cards · App picks"). */
    fun summary(kind: ReminderKind, p: ReminderPrefs): String = when (kind) {
        ReminderKind.CARDS_DUE -> if (!p.dueOn) "Off" else listOfNotNull(
            "${compact(p.dueMin)} cards",
            when (p.dueMode) {
                DueMode.FIXED -> timeText(p.dueTime)
                DueMode.APP_PICKS -> "App picks"
                DueMode.EVERY_HOURS -> "Every ${p.dueEveryHours} h"
            },
            if (p.allDecks) null else if (p.deckIds.isEmpty()) "No decks" else count(p.deckIds.size, "deck", "decks"),
            if (p.dailyCap == 2) "2 a day" else null,
        ).joinToString(" · ")
        ReminderKind.OWN_GOAL -> if (!p.goalOn) "Off" else
            "${compact(p.goalCount)} ${if (p.goalKind == GoalKind.REVIEWS) "reviews" else "new cards"} · Every ${p.dueEveryHours} h"
        ReminderKind.STREAK -> if (p.streakOn) "At ${timeText(p.streakTime)}" else "Off"
        ReminderKind.DAILY_GOAL -> if (p.dailyGoalOn) "At ${timeText(p.dailyGoalTime)}" else "Off"
        ReminderKind.SHOP -> if (p.shopOn) "Balls and evolution items" else "Off"
    }

    /** "22:00 – 08:00" or "Off". */
    fun quietSummary(p: ReminderPrefs): String =
        if (p.quietOn && p.quietFrom != p.quietTo) "${timeText(p.quietFrom)} – ${timeText(p.quietTo)}" else "Off"
}
