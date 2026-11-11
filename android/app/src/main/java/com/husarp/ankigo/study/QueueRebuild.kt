package com.husarp.ankigo.study

/** Anki's same-deck selection keeps the old learning cutoff. Restore our deck even if the other selection fails. */
object QueueRebuild {
    fun run(deckId: Long, decks: List<Long>, select: (Long) -> Unit): Boolean {
        val other = decks.firstOrNull { it != deckId } ?: return false
        try { select(other) } finally { select(deckId) }
        return true
    }
}
