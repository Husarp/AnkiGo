package com.husarp.ankigo.ui

import com.husarp.ankigo.Anki
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** R9-B9b: which deck the battle card's Continue button studies (the pure rule). */
class ContinueCardTest {
    private fun deck(id: Long, name: String = "Deck $id", learn: Int = 0, review: Int = 0, new: Int = 0) = Anki.Deck(id, name, learn, review, new, false)

    @Test fun lastDeckWithCardsDueShows() {
        val d = deck(7, review = 5)
        assertEquals(d, ContinueBar.forCard(7, listOf(deck(1, new = 3), d)))
    }

    @Test fun noLastDeckHides() {
        assertNull(ContinueBar.forCard(0, listOf(deck(0, review = 5), deck(1, review = 5))))
    }

    @Test fun deckNotInTheListHides() {
        assertNull(ContinueBar.forCard(9, listOf(deck(1, review = 5))))
        assertNull(ContinueBar.forCard(9, emptyList()))
    }

    @Test fun nothingDueHides() {
        assertNull(ContinueBar.forCard(7, listOf(deck(7))))
    }

    @Test fun anyOfLearnReviewNewCountsAsDue() {
        assertEquals(3, ContinueBar.forCard(7, listOf(deck(7, learn = 3)))?.total)
        assertEquals(2, ContinueBar.forCard(7, listOf(deck(7, new = 2)))?.total)
    }

    @Test fun theBarsHiddenTodayRuleDoesNotApplyHere() {
        // ContinueBar.show hides the bar for a hidden day; the card's button has no such input at all
        assertEquals(false, ContinueBar.show(7, 5, "2026-10-18", "2026-10-18"))
        assertEquals(7L, ContinueBar.forCard(7, listOf(deck(7, review = 5)))?.id)
    }

    @Test fun descriptionNamesTheDeckAndDue() {
        assertEquals("Continue Verbs, 4 due", ContinueBar.description(deck(7, "Spanish::Verbs", review = 4)))
    }
}
