package com.husarp.ankigo.ui

import com.husarp.ankigo.Anki
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetDueTest {
    private fun deck(id: Long, name: String, new: Int = 0, learn: Int = 0, review: Int = 0) = Anki.Deck(id, name, learn, review, new, false)

    @Test fun emptyListIsZero() = assertEquals(0, WidgetDue.totalDue(emptyList()))

    @Test fun addsNewLearnAndReview() =
        assertEquals(2 + 3 + 5, WidgetDue.totalDue(listOf(deck(1, "Default", new = 2, learn = 3, review = 5))))

    @Test fun topLevelDecksAreAddedUp() =
        assertEquals(10 + 4, WidgetDue.totalDue(listOf(deck(1, "Maths", review = 10), deck(2, "History", new = 4))))

    @Test fun childrenAreNotCountedTwice() {
        // the parent's counts already contain its children
        val decks = listOf(
            deck(1, "Languages", new = 5, review = 20),
            deck(2, "Languages::Spanish", new = 3, review = 12),
            deck(3, "Languages::Spanish::Verbs", review = 7),
            deck(4, "Maths", review = 6),
        )
        assertEquals(25 + 6, WidgetDue.totalDue(decks))
    }

    @Test fun aChildWithoutItsParentInTheListStillCounts() {
        // "Languages" itself is not in the list, so "Languages::Spanish" is the top of what we can see
        assertEquals(15, WidgetDue.totalDue(listOf(deck(2, "Languages::Spanish", review = 15))))
    }

    @Test fun onlyAnAncestorByWholeNameCounts() {
        // "Lang" is not an ancestor of "Languages"
        assertEquals(3 + 9, WidgetDue.totalDue(listOf(deck(1, "Lang", review = 3), deck(2, "Languages", review = 9))))
    }

    @Test fun middleAncestorMissingStillFindsTheGrandParent() {
        val decks = listOf(deck(1, "A", review = 4), deck(2, "A::B::C", review = 9))
        assertEquals(4, WidgetDue.totalDue(decks))
    }

    @Test fun negativeCountsAreZero() {
        assertEquals(5, WidgetDue.totalDue(listOf(deck(1, "X", new = -1, learn = -1, review = 5), deck(2, "Y", new = -1, learn = -1, review = -1))))
    }

    @Test fun textLines() {
        assertEquals("32 / 50", BuddyText.goal(32, 50))
        assertEquals("0 / 50", BuddyText.goal(0, 50))
        assertEquals("62 / 50", BuddyText.goal(62, 50))
        assertEquals("7", BuddyText.goal(7, 0))
        assertEquals("today", BuddyText.UNIT)
        assertEquals("32 done · 48 left", BuddyText.counts(32, 48))
        assertEquals("32 done · 0 left", BuddyText.counts(32, 0))
        assertEquals("32 done", BuddyText.counts(32, null))
    }
}
