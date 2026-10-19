package com.husarp.ankigo.study

import org.junit.Assert.*
import org.junit.Test

/** The rule that tells a verified write (the write queue itself is tested in AnswerQueueTest / StudySimTest). */
class StudyLoopTest {
    private val before = mapOf("_id" to "7", "reps" to "3", "due" to "1", "queue" to "2", "last_review_time_secs" to "100")

    @Test fun verifiesActualRowsAfterWrite() {
        assertTrue(StudyLoop.saved(before, before + mapOf("reps" to "4", "due" to "2")))
        assertTrue("the queue change alone is enough next to the rep", StudyLoop.saved(before, before + mapOf("reps" to "4", "queue" to "1")))
        assertTrue(StudyLoop.saved(before, before + mapOf("reps" to "4", "last_review_time_secs" to "200")))
    }
    @Test fun repChangeAloneCannotConfirmWrite() {
        assertFalse(StudyLoop.saved(before, before + ("reps" to "4")))
        assertFalse(StudyLoop.saved(before, before + ("due" to "2")))
    }
    @Test fun unreadableOrOtherCardsAreNeverSaved() {
        assertFalse(StudyLoop.saved(before, null))
        assertFalse(StudyLoop.saved(before, before + mapOf("_id" to "8", "reps" to "4", "due" to "2")))
        assertFalse("two reps at once is not one answer", StudyLoop.saved(before, before + mapOf("reps" to "5", "due" to "2")))
        assertFalse(StudyLoop.saved(before - "reps", before + mapOf("reps" to "4", "due" to "2")))
        assertFalse(StudyLoop.saved(before - "_id", before + mapOf("reps" to "4", "due" to "2")))
    }
    @Test fun anUnchangedCardIsNotSaved() {
        assertFalse(StudyLoop.saved(before, before))
    }
}
