package com.husarp.ankigo.deck

import com.husarp.ankigo.Anki
import org.junit.Assert.*
import org.junit.Test

/** The rows of the "Move to deck" tree: order, indent, marks. Made-up decks only. */
class DeckPickTest {
    private fun deck(id: Long, name: String, dyn: Boolean = false) = Anki.Deck(id, name, 0, 0, 0, dyn)

    private val decks = listOf(
        deck(5, "Spanish verbs"), deck(2, "Geography"), deck(4, "Geography::Rivers"), deck(3, "Geography::Capitals"),
        deck(1, "Japanese Core 2k"), deck(6, "Today's review", dyn = true), deck(7, "Geography 2"),
    )

    @Test fun childrenSitUnderTheirParent() {
        val names = DeckPick.rows(decks, 3, DeckPick.Mode.CARD).map { it.deck.name }
        assertEquals(listOf("Geography", "Geography::Capitals", "Geography::Rivers", "Geography 2", "Japanese Core 2k", "Spanish verbs", "Today's review"), names)
    }

    @Test fun depthAndLabelCome_fromTheName() {
        val r = DeckPick.rows(decks + deck(8, "Geography::Capitals::Asia"), 99, DeckPick.Mode.CARD)
        val asia = r.first { it.deck.id == 8L }
        assertEquals(2, asia.depth); assertEquals("Asia", asia.label)
        assertEquals(0, r.first { it.deck.id == 2L }.depth)
    }

    @Test fun orderIgnoresCapitals() {
        val r = DeckPick.rows(listOf(deck(1, "beta"), deck(2, "Alpha"), deck(3, "alpha::x")), 0, DeckPick.Mode.CARD)
        assertEquals(listOf("Alpha", "alpha::x", "beta"), r.map { it.deck.name })
    }

    @Test fun currentDeckIsMarkedNowAndNotPickable() {
        val r = DeckPick.rows(decks, 3, DeckPick.Mode.CARD).first { it.deck.id == 3L }
        assertEquals("now", r.tag); assertFalse(r.pickable); assertNotNull(r.reason)
    }

    @Test fun moveAllMarksTheSourceFrom() {
        val r = DeckPick.rows(decks, 2, DeckPick.Mode.ALL).first { it.deck.id == 2L }
        assertEquals("from", r.tag); assertFalse(r.pickable)
    }

    @Test fun filteredDecksAreGreyedWithTheApiReason() {
        val r = DeckPick.rows(decks, 3, DeckPick.Mode.CARD).first { it.deck.id == 6L }
        assertEquals("filtered", r.tag); assertFalse(r.pickable); assertEquals(DeckOps.FILTERED_TARGET, r.reason)
    }

    @Test fun everyOtherDeckIsPickable() {
        val rows = DeckPick.rows(decks, 3, DeckPick.Mode.CARD)
        assertEquals(setOf(2L, 4L, 7L, 1L, 5L), rows.filter { it.pickable }.map { it.deck.id }.toSet())
        assertTrue(rows.filter { it.pickable }.all { it.tag == null && it.reason == null })
    }

    @Test fun aFilteredCurrentDeckStaysFiltered() {
        // a card that sits in a filtered deck right now: the screens refuse before the tree opens, but the rows never offer it as a target
        val r = DeckPick.rows(decks, 6, DeckPick.Mode.CARD).first { it.deck.id == 6L }
        assertFalse(r.pickable); assertEquals("filtered", r.tag)
    }

    @Test fun noDecksNoRows() = assertTrue(DeckPick.rows(emptyList(), 1, DeckPick.Mode.ALL).isEmpty())
}
