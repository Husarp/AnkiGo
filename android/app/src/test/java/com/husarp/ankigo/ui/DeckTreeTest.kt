package com.husarp.ankigo.ui

import com.husarp.ankigo.Anki
import org.junit.Assert.*
import org.junit.Test

class DeckTreeTest {
    private fun d(id: Long, name: String) = Anki.Deck(id, name, 0, 1, 0, false)
    private val decks = listOf(
        d(1, "Geo"), d(2, "Geo::Capitals"), d(3, "Geo::Capitals::Asia"), d(4, "Geo::Rivers"),
        d(5, "Lang"), d(6, "Lang::Spanish"), d(7, "Default"),
    )

    @Test fun defaultIsCollapsed() {
        val rows = DeckTree.rows(decks, emptySet())
        assertEquals(listOf(1L, 5L, 7L), rows.map { it.deck.id })
        assertEquals(listOf(true, true, false), rows.map { it.hasChildren })
        assertTrue(rows.none { it.expanded })
    }

    @Test fun expandShowsChildrenWithDepth() {
        val rows = DeckTree.rows(decks, setOf(1L))
        assertEquals(listOf(1L, 2L, 4L, 5L, 7L), rows.map { it.deck.id })
        assertEquals(listOf(0, 1, 1, 0, 0), rows.map { it.depth })
        assertTrue(rows[0].expanded); assertFalse(rows[1].expanded); assertTrue(rows[1].hasChildren)
    }

    @Test fun grandChildrenHiddenWhenMiddleParentCollapsed() {
        // Geo and Capitals expanded -> Asia visible; collapse Capitals -> Asia hidden again, but Capitals stays remembered
        val open = setOf(1L, 2L)
        assertTrue(DeckTree.rows(decks, open).any { it.deck.id == 3L && it.depth == 2 })
        val closedMiddle = DeckTree.toggle(open, 2L)
        assertFalse(DeckTree.rows(decks, closedMiddle).any { it.deck.id == 3L })
        // grandchild stays hidden if only the middle one is expanded and the top one is collapsed
        assertEquals(listOf(1L, 5L, 7L), DeckTree.rows(decks, setOf(2L)).map { it.deck.id })
    }

    @Test fun toggleIsReversible() {
        val a = DeckTree.toggle(emptySet(), 5L)
        assertEquals(setOf(5L), a)
        assertEquals(emptySet<Long>(), DeckTree.toggle(a, 5L))
    }

    @Test fun newDeckStartsCollapsed() {
        val withNew = decks + d(8, "Lang::Polish") + d(9, "Lang::Polish::Verbs")
        val rows = DeckTree.rows(withNew, setOf(5L))
        val polish = rows.first { it.deck.id == 8L }
        assertTrue(polish.hasChildren); assertFalse(polish.expanded)
        assertFalse(rows.any { it.deck.id == 9L })
    }

    @Test fun deletedDeckIsForgotten() {
        val left = decks.filter { it.id != 5L && it.id != 6L }
        assertEquals(setOf(1L), DeckTree.prune(setOf(1L, 5L, 99L), left))
    }

    @Test fun emptyReadDoesNotPrune() {
        assertEquals(setOf(1L, 5L), DeckTree.prune(setOf(1L, 5L), emptyList()))
    }

    @Test fun storedFormRoundTripsAndIgnoresJunk() {
        val s = setOf(3L, 1_700_000_000_123L)
        assertEquals(s, DeckTree.decode(DeckTree.encode(s)))
        assertEquals(setOf(4L), DeckTree.decode(setOf("4", "Geo::Capitals", "")))
        assertEquals(emptySet<Long>(), DeckTree.decode(null))
    }

    @Test fun missingMiddleAncestorDoesNotHideChild() {
        // AnkiDroid normally lists every ancestor; if one is missing its child must still show under the existing ones
        val odd = listOf(d(1, "A"), d(2, "A::B::C"))
        assertEquals(listOf(1L), DeckTree.rows(odd, emptySet()).map { it.deck.id })
        assertEquals(listOf(1L, 2L), DeckTree.rows(odd, setOf(1L)).map { it.deck.id })
    }
}
