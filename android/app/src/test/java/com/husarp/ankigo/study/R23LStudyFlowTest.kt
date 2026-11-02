package com.husarp.ankigo.study

import org.junit.Assert.*
import org.junit.Test

class R23LStudyFlowTest {
    private fun key(n: Long) = StudyLoop.Key(n, 0)
    private fun card(n: Long) = Prepared(key(n), mapOf("_id" to "$n", "note_id" to "$n", "deck_id" to "1", "reps" to "3", "due" to "5", "queue" to "2", "last_review_time_secs" to "9"), "card$n")

    @Test fun fastPrefetchCannotReplaceShownCardOrRetargetItsAnswer() {
        var seq = 0L
        val nc = NextCard<String>(1, { ++seq }, maxAhead = 2, freshNext = true)
        nc.reload()
        nc.loaded(1, card(1), listOf(key(1), key(2)))
        nc.prepared(key(1), 1, card(2))
        assertTrue(nc.loaded(1, card(3), listOf(key(3))).isEmpty())
        assertEquals(key(1), nc.actionCard()!!.key)
        val answer = nc.tapAnswer(3, 20)
        assertEquals(1L, answer.filterIsInstance<Cmd.Enqueue>().single().answer.cardId)
        assertTrue(answer.none { it is Cmd.Show })
        assertEquals(Cmd.Load(2, null), answer.last())
        assertTrue(nc.loaded(1, card(2), listOf(key(2))).isEmpty())
        nc.loaded(2, card(3), listOf(key(3)))
        nc.prepared(key(1), 1, card(2))
        assertEquals(key(3), nc.actionCard()!!.key)
        assertEquals(3L, nc.tapAnswer(2, 30).filterIsInstance<Cmd.Enqueue>().single().answer.cardId)
    }

    private fun row(n: Int, queue: Int) = mapOf("note_id" to "$n", "ord" to "0", "queue" to "$queue")

    @Test fun dueLearningAndReviewPrecedeNewAndInvalidRows() {
        assertEquals(row(3, 1), DueOrder.first(listOf(row(1, 0), row(2, 2), row(3, 1))))
        assertEquals(row(2, 2), DueOrder.first(listOf(row(1, 0), row(2, 2))))
        assertNull(DueOrder.first(listOf(row(1, 0), row(2, -1))))
    }

    @Test fun schedulerOrderIsKeptWhenSearchSuggestsADueReview() {
        val queue = listOf(key(1), key(2), key(3))
        val next = DueOrder.prioritize(queue, listOf(row(2, 2)), { error("unnecessary expanded read") }) { it }
        assertEquals(queue, next)
    }

    @Test fun emptySchedulerCanBeReadAgainWhenSearchReportsDueCards() {
        var expanded = 0
        val next = DueOrder.prioritize(emptyList<StudyLoop.Key>(), listOf(row(9, 1)), { expanded++; listOf(key(9), key(1)) }) { it }
        assertEquals(key(9), next.first())
        assertEquals(1, expanded)
    }

    @Test fun reviewOutsideDailyLimitDoesNotBecomeAnExtraStudyCard() {
        for (queue in listOf(2, 3)) {
            assertEquals(listOf(key(1)), DueOrder.prioritize(listOf(key(1)), listOf(row(9, queue)), { listOf(key(1)) }) { it })
        }
    }

    @Test fun freshReadFindsLearningThatBecameDueAfterEmptyRead() {
        var rows = emptyList<Map<String, String?>>()
        val query = { DueOrder.prioritize(emptyList<StudyLoop.Key>(), rows, { listOf(key(9)) }) { it } }
        assertTrue(query().isEmpty()) // first empty load, before the battle/Finish deck wait
        rows = listOf(row(9, 1))
        assertEquals(listOf(key(9)), query()) // last-moment check must resume study
        assertEquals("deck:\"A\\_B\" is:due -is:new", DueOrder.query("A_B"))
    }

    @Test fun dueLearningMissingFromSchedulerLeavesRecoveryToTheCaller() {
        assertTrue(DueOrder.prioritize(emptyList<StudyLoop.Key>(), listOf(row(9, 1)), { emptyList() }) { it }.isEmpty())
    }
}
