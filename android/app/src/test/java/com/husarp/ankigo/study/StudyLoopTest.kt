package com.husarp.ankigo.study

import org.junit.Assert.*
import org.junit.Test

class StudyLoopTest {
    private val before = mapOf("_id" to "7", "reps" to "3", "due" to "1", "queue" to "2", "last_review_time_secs" to "100")
    private class Fake(val initial: Map<String, String?>, var top: StudyLoop.Key? = StudyLoop.Key(1, 0)) : StudyLoop.Provider {
        var writes = 0; var selections = 0; var reads = 0; var save = true
        var row = initial
        override fun select() { selections++ }
        override fun top(): StudyLoop.Key? { reads++; return top }
        override fun stats(key: StudyLoop.Key) = row
        override fun answer(key: StudyLoop.Key, ease: Int, ms: Long) { writes++; if (save) row = row + mapOf("reps" to "4", "due" to "2") }
    }
    @Test fun verifiesActualRowsAfterWrite() {
        val p = Fake(before)
        val result = StudyLoop(p).answer(StudyLoop.Key(1, 0), before, 3, 400)
        assertEquals(StudyLoop.Status.SAVED, result.status)
        assertEquals(1, p.writes); assertTrue(p.selections > 0); assertTrue(p.reads > 0)
    }
    @Test fun refusesStaleCard() {
        val p = Fake(before, StudyLoop.Key(2, 0))
        assertEquals(StudyLoop.Status.MOVED, StudyLoop(p).answer(StudyLoop.Key(1, 0), before, 3, 0).status)
        assertEquals(0, p.writes)
    }
    @Test fun silentFailureIsRetriedOnceAndNeverSaved() {
        val p = Fake(before).apply { save = false }
        assertEquals(StudyLoop.Status.FAILED, StudyLoop(p).answer(StudyLoop.Key(1, 0), before, 3, 0).status)
        assertEquals(2, p.writes)
    }
    @Test fun repChangeAloneCannotConfirmWrite() {
        assertFalse(StudyLoop.saved(before, before + ("reps" to "4")))
        assertFalse(StudyLoop.saved(before, before + ("due" to "2")))
    }
    @Test fun alreadyChangedStateDoesNotWriteAgain() {
        val p = Fake(before).apply { row = before + mapOf("reps" to "4", "due" to "2") }
        assertEquals(StudyLoop.Status.MOVED, StudyLoop(p).answer(StudyLoop.Key(1, 0), before, 3, 0).status)
        assertEquals(0, p.writes)
    }
}
