package com.husarp.ankigo.deck

import com.husarp.ankigo.Anki

/**
 * The rows of the "Move to deck" tree (pure, JVM-tested): every deck under its parent, indented, filtered decks and the deck the
 * cards are in now marked and not pickable. The screen draws the rows ([DeckPickerSheet]); what a pick does is [DeckOps]' job.
 */
object DeckPick {
    /** [CARD] = move one card (the deck it is in now is shown with "now"); [ALL] = move every card of a deck ("from"). */
    enum class Mode { CARD, ALL }

    /**
     * One line. [tag] = the small grey word at the right ("now", "from", "filtered"), [pickable] = it can be the target,
     * [reason] = one short sentence for a tap on a row that is not pickable.
     */
    data class Row(val deck: Anki.Deck, val depth: Int, val label: String, val tag: String?, val pickable: Boolean, val reason: String?)

    fun rows(decks: List<Anki.Deck>, currentId: Long, mode: Mode): List<Row> =
        decks.sortedWith(ORDER).map { d ->
            val parts = d.name.split(DeckOps.SEP)
            when {
                d.dyn -> Row(d, parts.size - 1, parts.last(), "filtered", false, DeckOps.FILTERED_TARGET)
                d.id == currentId -> Row(
                    d, parts.size - 1, parts.last(), if (mode == Mode.CARD) "now" else "from", false,
                    if (mode == Mode.CARD) "The card is in this deck already." else "These cards are in this deck already. Pick another deck.",
                )
                else -> Row(d, parts.size - 1, parts.last(), null, true, null)
            }
        }

    /** A deck by its path ("Geography" before "Geography::Capitals" before "Geography 2"), capital letters ignored. */
    private val ORDER = Comparator<Anki.Deck> { a, b ->
        val x = a.name.split(DeckOps.SEP)
        val y = b.name.split(DeckOps.SEP)
        for (i in 0 until minOf(x.size, y.size)) {
            val c = x[i].compareTo(y[i], ignoreCase = true)
            if (c != 0) return@Comparator c
        }
        x.size.compareTo(y.size)
    }
}
