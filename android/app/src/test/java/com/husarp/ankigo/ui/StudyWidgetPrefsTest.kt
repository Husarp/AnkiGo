package com.husarp.ankigo.ui

import com.husarp.ankigo.Anki
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyWidgetPrefsTest {
    private fun deck(id: Long, name: String, new: Int = 0, learn: Int = 0, review: Int = 0) = Anki.Deck(id, name, learn, review, new, false)

    @Test fun mostDueDeckWins() {
        val d = listOf(deck(1, "A", review = 3), deck(2, "B", new = 5, learn = 2, review = 4), deck(3, "C", review = 10))
        assertEquals(2L, StudyWidgetPrefs.mostDue(d)?.id) // 11 beats 10 and 3: new + learn + review
    }

    @Test fun tieGoesToTheFirstByName() {
        val d = listOf(deck(1, "Zebra", review = 7), deck(2, "Alpha", review = 7), deck(3, "Mid", review = 7))
        assertEquals(2L, StudyWidgetPrefs.mostDue(d)?.id)
    }

    @Test fun neverAnEmptyDeck() {
        assertNull(StudyWidgetPrefs.mostDue(listOf(deck(1, "A"), deck(2, "B"))))
        assertNull(StudyWidgetPrefs.mostDue(emptyList()))
        // AnkiDroid reports -1 when it has no counts: that is not "due"
        assertNull(StudyWidgetPrefs.mostDue(listOf(Anki.Deck(1, "A", -1, -1, -1, false))))
        assertEquals(2L, StudyWidgetPrefs.mostDue(listOf(deck(1, "A"), deck(2, "B", new = 1)))?.id)
    }

    @Test fun anyDueSaysIfStudyHasWork() {
        assertTrue(StudyWidgetPrefs.anyDue(listOf(deck(1, "A"), deck(2, "B", learn = 1))))
        assertFalse(StudyWidgetPrefs.anyDue(listOf(deck(1, "A"), deck(2, "B"))))
        assertFalse(StudyWidgetPrefs.anyDue(emptyList()))
    }

    @Test fun keysArePerWidgetId() {
        assertEquals("w12.deck", StudyWidgetPrefs.key(12, StudyWidgetPrefs.DECK))
        assertTrue(StudyWidgetPrefs.keysOf(12).all { it.startsWith("w12.") })
        assertEquals(6, StudyWidgetPrefs.keysOf(12).toSet().size)
        assertTrue(StudyWidgetPrefs.key(1, "deck") != StudyWidgetPrefs.key(2, "deck"))
        // card keys are the state minus the deck
        assertEquals(setOf("w5.nid", "w5.ord", "w5.start", "w5.q"), StudyWidgetPrefs.cardKeysOf(5).toSet())
    }

    private val old = mapOf<String, Any?>(
        "studyDeck" to 42L, "studyDeckName" to "Geo::Capitals", "w_nid" to 7L, "w_ord" to 1, "w_start" to 1000L, "w_q" to "Capital of Peru?",
        "pkg" to "com.ichi2.anki", "unrelated" to 3,
    )

    @Test fun migrationGivesEveryExistingWidgetTheOldDeckAndCard() {
        val e = StudyWidgetPrefs.migrate(old, listOf(11, 12))!!
        for (id in listOf(11, 12)) {
            assertEquals(42L, e.put["w$id.deck"])
            assertEquals("Geo::Capitals", e.put["w$id.deckName"])
            assertEquals(7L, e.put["w$id.nid"])
            assertEquals(1, e.put["w$id.ord"])
            assertEquals(1000L, e.put["w$id.start"])
            assertEquals("Capital of Peru?", e.put["w$id.q"])
        }
        assertEquals(true, e.put[StudyWidgetPrefs.MIGRATED])
        // old global keys go; keys that are not ours stay
        assertEquals(setOf("studyDeck", "studyDeckName", "w_nid", "w_ord", "w_start", "w_q"), e.remove)
        assertFalse(e.put.containsKey("w11.pkg"))
    }

    @Test fun migrationRunsOnlyOnce() {
        assertNull(StudyWidgetPrefs.migrate(old + (StudyWidgetPrefs.MIGRATED to true), listOf(11)))
    }

    @Test fun noOldDeckMeansNothingToCopyButStillMarkedDone() {
        val e = StudyWidgetPrefs.migrate(mapOf("pkg" to "x"), listOf(11))!!
        assertEquals(mapOf<String, Any>(StudyWidgetPrefs.MIGRATED to true), e.put)
        assertTrue(e.remove.isEmpty())
        val zero = StudyWidgetPrefs.migrate(mapOf("studyDeck" to 0L, "w_nid" to 5L), listOf(11))!!
        assertEquals(1, zero.put.size)
        assertEquals(setOf("studyDeck", "w_nid"), zero.remove)
    }

    @Test fun noWidgetsYetJustClearsTheOldKeys() {
        val e = StudyWidgetPrefs.migrate(old, emptyList())!!
        assertEquals(mapOf<String, Any>(StudyWidgetPrefs.MIGRATED to true), e.put)
        assertEquals(6, e.remove.size)
    }
}
