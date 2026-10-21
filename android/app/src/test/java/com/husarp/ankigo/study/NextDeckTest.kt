package com.husarp.ankigo.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextDeckTest {
    private var seq = 0L
    private fun d(name: String, total: Int = 5) = NextDeck.D(++seq, name, total)
    private fun pick(decks: List<NextDeck.D>, current: String) =
        NextDeck.pick(decks, decks.firstOrNull { it.name == current }?.id ?: -1, current)?.name

    @Test fun aSiblingBeforeTheParent() {
        val decks = listOf(d("Japanese", 20), d("Japanese::Kanji", 0), d("Japanese::Vocab", 12), d("Spanish", 9))
        assertEquals("Japanese::Vocab", pick(decks, "Japanese::Kanji"))
    }

    @Test fun theNextSiblingAfterTheCurrentWrapsRound() {
        val decks = listOf(d("J"), d("J::A"), d("J::B", 0), d("J::C"), d("J::D"))
        assertEquals("J::C", pick(decks, "J::B"))
        assertEquals("J::A", pick(decks, "J::D"))
    }

    @Test fun emptySiblingsAreSkippedThenOneLevelUp() {
        val decks = listOf(d("L", 30), d("L::J", 10), d("L::J::K", 0), d("L::J::V", 0), d("L::S", 7), d("L::S::Verbs", 7))
        assertEquals("L::S::Verbs", pick(decks, "L::J::K"))   // never L::J or L (ancestors); a leaf before its parent deck
    }

    @Test fun aSubdeckWithoutChildrenComesFirst() {
        val decks = listOf(d("P"), d("P::A", 0), d("P::B", 8), d("P::B::X", 8), d("P::C", 3))
        assertEquals("P::B::X", pick(decks, "P::A"))   // the first after A in tree order is B's own leaf, not B
    }

    @Test fun aTopLevelDeckGoesToAnyOther() {
        val decks = listOf(d("Alpha", 0), d("Beta", 4), d("Gamma", 2), d("Gamma::One", 2))
        assertEquals("Beta", pick(decks, "Alpha"))
        assertEquals("Gamma::One", pick(listOf(d("Zed", 0), d("Gamma", 2), d("Gamma::One", 2)), "Zed"))
    }

    @Test fun deepTreeFallsBackToAnyDeck() {
        val decks = listOf(d("A", 0), d("A::B", 0), d("A::B::C", 0), d("A::B::C::D", 0), d("Other", 3))
        assertEquals("Other", pick(decks, "A::B::C::D"))
    }

    @Test fun nothingLeftGivesNull() {
        val decks = listOf(d("A", 9), d("A::B", 9), d("C", 0))
        assertNull(pick(decks, "A::B"))   // only the ancestor has cards
    }

    @Test fun subPathOfTheCardsDeck() {
        assertEquals("Kanji", NextDeck.subPath("Japanese", "Japanese::Kanji"))
        assertEquals("Kanji › N5", NextDeck.subPath("Japanese", "Japanese::Kanji::N5"))
        assertNull(NextDeck.subPath("Japanese", "Japanese"))
        assertNull(NextDeck.subPath("Japanese::Kanji", "Japanese"))
        assertNull(NextDeck.subPath("Japanese", null))
        assertNull(NextDeck.subPath("Jap", "Japanese::Kanji"))
    }
}
