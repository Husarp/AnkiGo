package com.husarp.ankigo.deck

import org.junit.Assert.*
import org.junit.Test

class DeckTextsTest {
    @Test fun subdeckNameJoinsParentAndTypedPart() {
        assertEquals("Geography::Capitals", DeckTexts.subdeckName("Geography", "  Capitals "))
        assertEquals("A::B::C", DeckTexts.subdeckName("A", "B::C"))
    }

    @Test fun nameProblemsAreTheOpsOwn() {
        assertEquals(DeckOps.nameProblem(""), DeckTexts.nameProblem(""))
        assertNotNull(DeckTexts.nameProblem("   "))
        assertNotNull(DeckTexts.nameProblem("::x"))
        assertNull(DeckTexts.nameProblem("Rivers"))
    }

    @Test fun deleteSaysWholeNoteAndCount() {
        val p = DeckTexts.deleteBody(2, "Geography::Capitals")
        assertEquals("This deletes the whole note: 2 cards in Geography::Capitals. It can't be undone.", p.toString())
        assertEquals("2 cards", p.bold)
        assertEquals("1 card", DeckTexts.deleteBody(1, "X").bold)
        assertEquals("all its cards", DeckTexts.deleteBody(null, "X").bold)
        assertTrue(DeckTexts.deleteBody(null, "X").toString().contains("whole note"))
    }

    @Test fun moveAllConfirmSaysWhatStaysAndThatThereIsNoUndo() {
        val withSub = DeckTexts.moveAllBody(120, "Geography", "Archive", true).toString()
        assertTrue(withSub.startsWith("Move 120 cards that sit directly in \"Geography\" to \"Archive\"?"))
        assertTrue("subdecks" in withSub); assertTrue("can't undo" in withSub)
        val plain = DeckTexts.moveAllBody(1, "A", "B", false)
        assertEquals("1 card", plain.bold); assertFalse("subdecks" in plain.toString())
    }

    @Test fun nothingToMove() {
        assertEquals("There are no cards directly in \"A\".", DeckTexts.nothingToMove("A", false))
        assertTrue("subdecks" in DeckTexts.nothingToMove("A", true))
    }

    @Test fun progressLine() {
        assertEquals("Moved 12 of 120 cards", DeckTexts.progress(12, 120))
        assertEquals("Looking for the cards…", DeckTexts.progress(0, 0))
    }

    @Test fun editTexts() {
        assertTrue(DeckTexts.editWarning(2).startsWith("Changes all 2 cards of this note."))
        assertTrue(DeckTexts.editWarning(1).startsWith("Saved straight"))
        assertTrue(DeckTexts.editWarning(null).startsWith("Saved straight"))
        assertEquals("Basic · Geography::Capitals", DeckTexts.editInfo("Basic", "Geography::Capitals"))
        assertEquals("Basic", DeckTexts.editInfo("Basic", null))
        assertEquals("Basic", DeckTexts.editInfo("Basic", " "))
    }
}
