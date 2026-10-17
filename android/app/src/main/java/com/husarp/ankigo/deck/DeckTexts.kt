package com.husarp.ankigo.deck

/** The sentences of the deck and card management dialogs (pure, JVM-tested). Results of the operations come from [DeckOps] itself. */
object DeckTexts {
    /** A dialog text with one bold part: [before] + [bold] + [after]. */
    data class Parts(val before: String, val bold: String, val after: String) {
        override fun toString() = before + bold + after
    }

    /** What the name field of "New subdeck" becomes: the parent, "::", then what was typed (trimmed). */
    fun subdeckName(parent: String, typed: String) = parent + DeckOps.SEP + typed.trim()

    /** Why [typed] can't be used for a new deck (or null). For a subdeck the field holds only the part after the parent. */
    fun nameProblem(typed: String): String? = DeckOps.nameProblem(typed)

    /** "Delete note?": it removes the WHOLE note with all its cards. [cards] null = the count could not be read. */
    fun deleteBody(cards: Int?, deck: String): Parts {
        val count = when (cards) {
            null -> "all its cards"
            1 -> "1 card"
            else -> "$cards cards"
        }
        return Parts("This deletes the whole note: ", count, " in $deck. It can't be undone.")
    }

    /** The confirm for "Move all cards": [count] cards sit directly in [from]; [hasSubdecks] adds that the subdecks stay as they are. */
    fun moveAllBody(count: Int, from: String, to: String, hasSubdecks: Boolean): Parts {
        val tail = (if (hasSubdecks) " The cards of its subdecks stay where they are." else "") +
            " AnkiGo can't undo this; to reverse it, move the cards back."
        return Parts("Move ", if (count == 1) "1 card" else "$count cards", " that sit directly in \"$from\" to \"$to\"?$tail")
    }

    /** "There is nothing to move": no card sits directly in [from]. */
    fun nothingToMove(from: String, hasSubdecks: Boolean) =
        "There are no cards directly in \"$from\"." + if (hasSubdecks) " The cards of its subdecks are not moved." else ""

    /** The line over the progress bar of a running move. */
    fun progress(done: Int, total: Int) = if (total <= 0) "Looking for the cards…" else "Moved $done of $total cards"

    /** Under the editor: what a save changes. [cards] null = the count could not be read. */
    fun editWarning(cards: Int?): String = when {
        cards == null || cards <= 1 -> "Saved straight to AnkiDroid, then read back to check."
        else -> "Changes all $cards cards of this note. Saved straight to AnkiDroid, then read back to check."
    }

    /** The line under "Edit / Preview": the note type and the deck the card is in. */
    fun editInfo(noteType: String, deck: String?) = if (deck.isNullOrBlank()) noteType else "$noteType · $deck"
}
