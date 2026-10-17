package com.husarp.ankigo.ui

import android.content.Context
import com.husarp.ankigo.Anki
import kotlin.math.max

// R5-06: the "cards waiting in AnkiDroid" number the widgets show, and the Buddy widget's two text lines. Pure except [read].

/** Total cards due in AnkiDroid, for the widgets (the Buddy widget's "left"; the 1x1 Study now widget reuses it). */
object WidgetDue {
    /**
     * new + learn + review over the top-level decks only: a parent's counts already contain its children, so a deck with
     * an ancestor in the list (names split on "::") is left out. Negative counts (AnkiDroid's "unknown") count as 0.
     */
    fun totalDue(decks: List<Anki.Deck>): Int {
        val names = decks.mapTo(HashSet()) { it.name }
        return decks.filter { !hasAncestor(it.name, names) }
            .sumOf { max(0, it.new) + max(0, it.learn) + max(0, it.review) }
    }

    private fun hasAncestor(name: String, names: Set<String>): Boolean {
        val parts = name.split("::")
        var prefix = ""
        for (i in 0 until parts.size - 1) {
            prefix = if (i == 0) parts[0] else prefix + "::" + parts[i]
            if (prefix in names) return true
        }
        return false
    }

    /** The total due now, or null when it can't be read (no permission, AnkiDroid missing or not answering). Blocking: background thread. */
    fun read(ctx: Context): Int? = runCatching {
        Anki.load(ctx)
        if (!Anki.hasPermission(ctx)) return@runCatching null
        // no deck at all = the provider is not there (AnkiDroid always has a Default deck)
        Anki.decks(ctx).takeIf { it.isNotEmpty() }?.let { totalDue(it) }
    }.getOrNull()
}

/** The Buddy widget's two lines under the ring. */
object BuddyText {
    /** "today" in "32 / 50 today" is drawn small next to this. */
    const val UNIT = "today"

    /** "32 / 50"; without a goal just the count. */
    fun goal(today: Int, goal: Int) = if (goal > 0) "$today / $goal" else "$today"

    /** "32 done · 48 left"; "32 done" when AnkiDroid can't be read ([due] null). */
    fun counts(today: Int, due: Int?) = if (due == null) "$today done" else "$today done · $due left"

    /** Spoken text of the Buddy widget: "Name, 32 of 50 today, 48 cards left, 12-day streak"; unknown parts are left out. */
    fun description(name: String?, today: Int, goal: Int, due: Int?, streak: Int): String = listOfNotNull(
        name?.takeIf { it.isNotBlank() } ?: "Buddy",
        if (goal > 0) "$today of $goal today" else "$today today",
        due?.let { "$it ${if (it == 1) "card" else "cards"} left" },
        streak.takeIf { it > 0 }?.let { "$it-day streak" },
    ).joinToString(", ")
}
