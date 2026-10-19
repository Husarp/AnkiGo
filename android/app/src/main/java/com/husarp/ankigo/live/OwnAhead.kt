package com.husarp.ankigo.live

import com.husarp.ankigo.CardState

// Pure Kotlin (no android.*): unit-tested on the JVM.

/**
 * R10-B23: AnkiGo's own answers count in the game at the TAP (the button and the card as shown are all the game needs); the write
 * to AnkiDroid follows in the background and may take seconds. Until it is settled, each such answer is kept here as the card as
 * it will be after it (the state as shown, one rep more). LiveSession (main thread) keeps the rules:
 * - every card map saved with the game holds these rows ([into]), so the save itself says "the game counted this answer": a
 *   restart never counts it again (catch-up), and a journal replay can tell whether its game step is in the save ([counted]);
 * - a detector that starts gets them as known (its catch-up skips them) and expects their rep change, pinned: it never counts
 *   the write itself;
 * - a row leaves when the write is settled (the detector then knows the real state) or ends NOT written (the game keeps what it
 *   counted; a battle is never undone).
 */
class OwnAhead {
    private val rows = ArrayList<CardState>()

    /** The rows, oldest first (a copy). */
    val pending: List<CardState> get() = rows.toList()

    fun isEmpty() = rows.isEmpty()

    /** The answer made on [before] counts now: returns its row (also when it was already there). */
    fun add(before: CardState): CardState {
        val row = predicted(before)
        if (rows.none { it.id == row.id && it.reps == row.reps }) rows += row
        return row
    }

    /** The answer made on [before] is settled or not written: its row leaves. Returns the row, null when it was not there. */
    fun remove(before: CardState): CardState? {
        val i = rows.indexOfFirst { it.id == before.id && it.reps == before.reps + 1 }
        return if (i < 0) null else rows.removeAt(i)
    }

    /** [map] (the detector's card map, or the saved one while no detector exists) with every row in it: what is saved with the game. */
    fun into(map: String?): String? = withPendingOwn(map, rows)

    companion object {
        /** The card as it will be after the answer made on [before]: one rep more (the rest is not known before AnkiDroid has it). */
        fun predicted(before: CardState) = before.copy(reps = before.reps + 1)

        /**
         * The game already counted the answer made on [before]: one of [maps] (the card map saved with the game, the detector's
         * map as handed to the game) holds its card at the rep that answer makes, or later. Only own answers counted at the tap
         * (their row) or answers the detector counted put a card there; an unreadable map says nothing.
         */
        fun counted(before: CardState, vararg maps: String?): Boolean = maps.any { m ->
            m != null && ((try { decode(m) } catch (e: Exception) { null })?.get(before.id)?.reps ?: -1) >= before.reps + 1
        }
    }
}
