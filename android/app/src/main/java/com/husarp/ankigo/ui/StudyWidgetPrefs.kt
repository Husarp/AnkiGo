package com.husarp.ankigo.ui

import android.content.SharedPreferences
import com.husarp.ankigo.Anki

/**
 * The study widget keeps its deck and card state per widget id in the "spike" prefs (`w<id>.deck`, `w<id>.deckName`,
 * `w<id>.nid`, `w<id>.ord`, `w<id>.start`, `w<id>.q`). Before this, all study widgets shared one set of global keys
 * (`studyDeck`, `studyDeckName`, `w_nid`, `w_ord`, `w_start`, `w_q`); [migrate] gives them to the existing widgets once.
 * The decision logic is pure (maps in, edits out) so it is unit tested; the SharedPreferences wrappers are thin.
 */
object StudyWidgetPrefs {
    const val DECK = "deck"
    const val DECK_NAME = "deckName"
    const val NID = "nid"
    const val ORD = "ord"
    const val START = "start"
    const val Q = "q"
    val FIELDS = listOf(DECK, DECK_NAME, NID, ORD, START, Q)
    /** Set once the old global keys were handed to the widgets that existed. */
    const val MIGRATED = "w_perWidget"

    private val OLD_KEYS = mapOf("studyDeck" to DECK, "studyDeckName" to DECK_NAME, "w_nid" to NID, "w_ord" to ORD, "w_start" to START, "w_q" to Q)

    fun key(id: Int, field: String) = "w$id.$field"

    /** What to write to / remove from the prefs. */
    class Edit(val put: Map<String, Any>, val remove: Set<String>)

    /** The deck with the most cards due (new + learn + review); never one with nothing due. Ties: the first by name. Null when nothing is due. */
    fun mostDue(decks: List<Anki.Deck>): Anki.Deck? =
        decks.filter { due(it) > 0 }.sortedBy { it.name }.maxByOrNull { due(it) }

    /** maxByOrNull returns the first maximum, so after sorting by name the earliest name wins a tie. */
    fun due(d: Anki.Deck) = d.new.coerceAtLeast(0) + d.learn.coerceAtLeast(0) + d.review.coerceAtLeast(0)

    /** True when any deck has something due (the motivation widget's tap decides Study vs battle with this). */
    fun anyDue(decks: List<Anki.Deck>) = decks.any { due(it) > 0 }

    /**
     * The one-time move of the old global keys onto the existing widget ids. Null = nothing to do (already migrated).
     * A widget without a saved deck gets nothing, so it picks the default deck on its next update.
     */
    fun migrate(all: Map<String, Any?>, ids: List<Int>): Edit? {
        if (all[MIGRATED] == true) return null
        val put = LinkedHashMap<String, Any>()
        put[MIGRATED] = true
        val oldDeck = (all["studyDeck"] as? Long) ?: 0L
        if (oldDeck != 0L) for (id in ids) for ((old, field) in OLD_KEYS) all[old]?.let { put[key(id, field)] = it }
        return Edit(put, OLD_KEYS.keys.filter { it in all }.toSet())
    }

    /** Every key a widget owns (removed when the widget is deleted). */
    fun keysOf(id: Int) = FIELDS.map { key(id, it) }

    /** Only the card state (kept: the deck). */
    fun cardKeysOf(id: Int) = listOf(NID, ORD, START, Q).map { key(id, it) }

    // ---- SharedPreferences wrappers ----

    private fun apply(p: SharedPreferences, e: Edit) {
        val ed = p.edit()
        e.remove.forEach { ed.remove(it) }
        for ((k, v) in e.put) when (v) {
            is Boolean -> ed.putBoolean(k, v)
            is Long -> ed.putLong(k, v)
            is Int -> ed.putInt(k, v)
            is String -> ed.putString(k, v)
        }
        ed.apply()
    }

    /** Runs the migration for the widgets that exist now (cheap when already done). */
    fun prepare(p: SharedPreferences, ids: IntArray) {
        migrate(p.all, ids.toList())?.let { apply(p, it) }
    }

    fun deckId(p: SharedPreferences, id: Int): Long = p.getLong(key(id, DECK), 0L)
    fun deckName(p: SharedPreferences, id: Int): String? = p.getString(key(id, DECK_NAME), null)

    /** Chooses the widget's deck and forgets its current card. */
    fun setDeck(p: SharedPreferences, id: Int, deckId: Long, deckName: String) {
        val ed = p.edit()
        cardKeysOf(id).forEach { ed.remove(it) }
        ed.putLong(key(id, DECK), deckId).putString(key(id, DECK_NAME), deckName).apply()
    }

    fun forget(p: SharedPreferences, ids: IntArray) {
        val ed = p.edit()
        for (id in ids) keysOf(id).forEach { ed.remove(it) }
        ed.apply()
    }
}
