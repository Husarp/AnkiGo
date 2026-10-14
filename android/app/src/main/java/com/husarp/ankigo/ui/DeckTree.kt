package com.husarp.ankigo.ui

import com.husarp.ankigo.Anki

/**
 * Which decks of the Study tab are shown. Rules: every parent deck starts collapsed; the user's expanded parents are kept
 * as a set of deck ids (decks are matched to their parents by "Parent::Child" names, as AnkiDroid does).
 */
object DeckTree {
    /** One visible row: [depth] = number of ancestors, [hasChildren] = shows an arrow, [expanded] = its children are shown. */
    data class Row(val deck: Anki.Deck, val depth: Int, val hasChildren: Boolean, val expanded: Boolean)

    /** The rows to draw, in the given deck order. A deck is hidden when any of its (existing) ancestors is not expanded. */
    fun rows(decks: List<Anki.Deck>, expanded: Set<Long>): List<Row> {
        val byName = decks.associateBy { it.name }
        return decks.mapNotNull { d ->
            val ancestors = d.name.split("::").dropLast(1).runningReduce { a, b -> "$a::$b" }
            if (ancestors.any { a -> byName[a]?.let { it.id !in expanded } == true }) return@mapNotNull null
            val has = decks.any { it.name.startsWith(d.name + "::") }
            Row(d, ancestors.size, has, has && d.id in expanded)
        }
    }

    /** Flips one deck between collapsed and expanded. */
    fun toggle(expanded: Set<Long>, id: Long): Set<Long> = if (id in expanded) expanded - id else expanded + id

    /**
     * Forgets deleted decks. Only a successful, non-empty deck read may prune: an empty list (error, AnkiDroid not
     * ready) leaves the stored set untouched.
     */
    fun prune(expanded: Set<Long>, decks: List<Anki.Deck>): Set<Long> {
        if (decks.isEmpty()) return expanded
        val ids = decks.map { it.id }.toSet()
        return expanded.filterTo(HashSet()) { it in ids }
    }

    /** Stored form (a string set, the only set type SharedPreferences has) and back; bad entries are ignored. */
    fun encode(expanded: Set<Long>): Set<String> = expanded.mapTo(HashSet()) { it.toString() }
    fun decode(stored: Set<String>?): Set<Long> = stored.orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
}
