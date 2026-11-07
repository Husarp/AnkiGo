package com.husarp.ankigo.study

import org.junit.Assert.*
import org.junit.Test

/** PLAN 432: reproduce the device's missing learning row, wrong top and disabled preparation. */
class R27StudyRecoveryTest {
    private fun key(n: Long) = StudyLoop.Key(n, 0)
    private fun due(n: Long) = mapOf("note_id" to "$n", "ord" to "0", "queue" to "1")
    private fun card(n: Long) = Prepared(key(n), mapOf("_id" to "$n", "reps" to "1", "due" to "5", "queue" to "2"), "card$n")

    @Test fun learningSearchMissingFromSchedulerRecoversWithoutThrowing() {
        var reads = 0
        val result = QueueRead.read(
            query = { DueOrder.prioritize(emptyList<StudyLoop.Key>(), listOf(due(9)), { if (++reads == 2) listOf(key(9)) else emptyList() }) { it } },
            dueCount = { 1 }, pause = {},
        )
        assertEquals(listOf(key(9)), result.items)
        assertEquals(1, result.retries)
    }

    @Test fun realScheduledDueLearningPrecedesNewCards() {
        val actual = listOf(key(1), key(9))
        assertEquals(listOf(key(9), key(1)), DueOrder.prioritize(actual, listOf(due(9)), { error("no expansion needed") }) { it })
    }

    @Test fun missingLearningSearchResultDoesNotBlockTheRealSchedulerTop() {
        val scheduled = listOf(key(1))
        assertEquals(scheduled, DueOrder.prioritize(scheduled, listOf(due(9)), { scheduled }) { it })
    }

    @Test fun permanentlyMissingLearningCardDoesNotCrashOrClaimFinished() {
        val result = QueueRead.read(
            query = { DueOrder.prioritize(emptyList<StudyLoop.Key>(), listOf(due(9)), { emptyList() }) { it } },
            dueCount = { 1 }, pause = {},
        )
        assertTrue(result.items.isEmpty())
        assertTrue(result.dueButEmpty)
    }

    @Test fun freshSchedulerModeStillPreparesContentButNeverShowsAGuess() {
        var seq = 0L
        val nc = NextCard<String>(1, { ++seq }, maxAhead = 2, freshNext = true)
        nc.reload()
        val initial = nc.loaded(1, card(1), listOf(key(1), key(2)))
        assertEquals(listOf(Cmd.Prepare(key(2), key(1), 1)), initial.filterIsInstance<Cmd.Prepare>())
        nc.prepared(key(1), 1, card(2))
        val tap = nc.tapAnswer(3, 100)
        assertTrue(tap.none { it is Cmd.Show })
        assertTrue(tap.contains(Cmd.ShowWaiting))
        assertEquals(Cmd.Load(2, null), tap.last())
    }
}
