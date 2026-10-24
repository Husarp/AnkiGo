package com.husarp.ankigo.study

import org.junit.Assert.*
import org.junit.Test

/**
 * R18-A (PLAN 246): card actions must not flip the card back to the question. The rules the study screen uses: what happens when a card
 * action's write is over ([StudyRules.afterAction]), the flag the screen shows right after a flag ([StudyRules.withFlag]), and the card
 * that comes back after an edit, a move or a resume still showing its answer ([StudyRules.AnswerKeep.same]).
 */
class R18ACardActionsTest {
    /** The row the screen keeps of the card on show (CardState.COLS + ord + original_due + flags). */
    private val row = mapOf(
        "_id" to "7", "note_id" to "70", "ord" to "0", "deck_id" to "2", "original_deck_id" to "0", "reps" to "3", "lapses" to "0", "type" to "2", "queue" to "2",
        "due" to "100", "interval" to "10", "sm2_factor" to "2500", "left" to "0", "last_review_time_secs" to "55", "original_due" to "0", "flags" to "0",
    )
    private val key = StudyLoop.Key(70, 0)
    private val keep = StudyRules.AnswerKeep(key, row, "typed", 1234)

    // ---- flag

    @Test fun aFlagKeepsTheCardAndOnlyBuryAndSuspendTakeItAway() {
        assertEquals(StudyRules.AfterAction.KEEP_CARD, StudyRules.afterAction("flag", true))
        assertEquals(StudyRules.AfterAction.NEXT_CARD, StudyRules.afterAction("bury", true))
        assertEquals(StudyRules.AfterAction.NEXT_CARD, StudyRules.afterAction("suspend", true))
        for (a in listOf("flag", "bury", "suspend")) assertEquals("a failed write is an error: $a", StudyRules.AfterAction.ERROR, StudyRules.afterAction(a, false))
    }

    @Test fun theFlagTheScreenShowsFollowsAtOnce() {
        assertEquals("3", StudyRules.withFlag(row, 3)["flags"])
        assertEquals("flag removed", "0", StudyRules.withFlag(row + ("flags" to "5"), 0)["flags"])
        assertEquals("only AnkiDroid's 7 flags", "7", StudyRules.withFlag(row, 9)["flags"])
        assertEquals("nothing else of the row moves", row - "flags", StudyRules.withFlag(row, 3) - "flags")
    }

    @Test fun aFlaggedCardIsStillTheSameUnansweredCardOnTheAnswerSide() {
        assertTrue(keep.same(key, StudyRules.withFlag(row, 3)))
        assertTrue(keep.same(key, StudyRules.withFlag(row + ("flags" to "2"), 0)))
    }

    // ---- edit note and move: the screen reloads the card and the answer side comes back

    @Test fun anEditedNoteComesBackOnTheAnswerSide() {
        // the editor changed fields and the card's modification time; the scheduling did not move
        assertTrue(keep.same(key, row + ("mod" to "99999") + ("usn" to "-1")))
    }

    @Test fun aCardMovedToAnotherDeckIsStillTheSameCard() {
        assertTrue(keep.same(key, row + ("deck_id" to "9")))
        assertTrue("into a filtered deck", keep.same(key, row + ("deck_id" to "9") + ("original_deck_id" to "2") + ("original_due" to "100")))
    }

    // ---- what still shows the question

    @Test fun aCardAnsweredBuriedSuspendedOrRescheduledElsewhereShowsItsQuestion() {
        assertFalse("answered", keep.same(key, row + ("reps" to "4") + ("last_review_time_secs" to "99")))
        assertFalse("buried", keep.same(key, row + ("queue" to "-2")))
        assertFalse("suspended", keep.same(key, row + ("queue" to "-1")))
        assertFalse("rescheduled", keep.same(key, row + ("due" to "250")))
        assertFalse("another card", keep.same(StudyLoop.Key(71, 0), row))
        assertFalse("a card with no id", keep.same(key, row - "_id"))
    }
}
