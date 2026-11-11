package com.husarp.ankigo.study

import org.junit.Assert.*
import org.junit.Test

/** Model Anki's cached cutoff: newly due learning is after the main queue until an answer/rebuild. */
class R33ReturnLearningTest {
    private fun key(id: Long) = StudyLoop.Key(id, 0)
    private fun row(id: Long, queue: Int, due: Long = 100) = mapOf("note_id" to "$id", "ord" to "0", "queue" to "$queue", "due" to "$due")

    @Test fun backgroundDwellThenRestartShowsDueLearningBeforePreparedNewAndReview() {
        for (learningQueue in listOf(1, 4)) {
            var clock = 90_000L
            val nc = NextCard<String>(1, { 1L }, now = { clock }, maxAhead = NextCard.AHEAD)
            val new = Prepared(key(1), mapOf("queue" to "0"), "prepared new")
            val load = nc.reload().filterIsInstance<Cmd.Load>().single()
            nc.loaded(load.token, new, listOf(new.key))
            val original = nc.slot
            // App backgrounds; the provider's cached cutoff stays at 90s while learning becomes due at 100s.
            clock = 120_000
            val ticket = nc.returnCheck(false)!!
            val due = listOf(row(2, 2), row(500, learningQueue))
            val cached = (1L..499L).map(::key) + key(500)
            val reads = mutableListOf<Int>()
            fun queue() = DueOrder.prioritize(cached.take(6), due,
                { DueOrder.expandLearning(due, { limit -> reads += limit; cached.take(limit) }) { it } }) { it }
            val read = SchedulerCardRead.confirmed(queue(), ::queue, { it }, { k ->
                assertEquals(original, nc.slot)
                Prepared(k, mapOf("queue" to if (k.nid == 500L) "$learningQueue" else "0"), "c${k.nid}")
            }, { p, _ -> p })
            val shown = nc.returned(ticket, read.card, read.queue, false).filterIsInstance<Cmd.Show<String>>().single()
            assertEquals(key(500), shown.card.key)
            assertEquals(listOf(100, 1000, 100, 1000), reads)
            assertEquals("prepared new", new.view)
            // A cold deck restart also uses this order, without needing an existing Activity's return ticket.
            val restart = NextCard<String>(1, { 1L })
            val token = restart.reload().filterIsInstance<Cmd.Load>().single().token
            assertEquals(key(500), restart.loaded(token, read.card!!, read.queue).filterIsInstance<Cmd.Show<String>>().single().card.key)
        }
    }

    @Test fun learningUsesTheSchedulersOrderIncludingItsPreferenceForPreviouslyAttemptedCards() {
        val due = listOf(row(8, 1, 200), row(9, 1, 100))
        assertEquals(listOf(key(8), key(1)),
            DueOrder.prioritize(listOf(key(1), key(8)), due, { error("a real due learning card is already ready") }) { it })
        assertEquals(listOf(key(8), key(9), key(1)),
            DueOrder.prioritize(listOf(key(1), key(8), key(9)), due, { error("already ready") }) { it })
    }

    @Test fun dwellBeyondOldLearnAheadWindowRebuildsSchedulerBeforeShowingDueCard() {
        val due = listOf(row(9, 1))
        var stale = true
        val selections = mutableListOf<Long>()
        val result = DueOrder.expandLearning(due, { if (stale) listOf(key(1)) else listOf(key(9), key(1)) }, rebuild = {
            QueueRebuild.run(1, listOf(1, 2)) { selections += it; if (it == 2L) stale = false }
        }) { it }
        assertEquals(key(9), result.first())
        assertEquals(listOf(2L, 1L), selections)
    }

    @Test fun rebuildRestoresSelectedDeckOnFailureAndNeverSelectsANonexistentDeck() {
        val selected = mutableListOf<Long>()
        assertFalse(QueueRebuild.run(1, listOf(1)) { error("must not select a nonexistent deck") })
        try {
            QueueRebuild.run(1, listOf(1, 2)) { selected += it; if (it == 2L) error("provider failed") }
            fail("failure must reach normal queue recovery")
        } catch (_: IllegalStateException) {}
        assertEquals(listOf(2L, 1L), selected)
    }

    @Test fun rebuildAvoidsScanningThousandsOfMainQueueCardsOnReturn() {
        val due = listOf(row(9000, 1))
        var stale = true
        val reads = mutableListOf<Int>()
        val result = DueOrder.expandLearning(due, { limit ->
            reads += limit
            if (stale) (1L..limit.toLong()).map(::key) else listOf(key(9000), key(1))
        }, rebuild = { stale = false; true }) { it }
        assertEquals(key(9000), result.first())
        assertEquals(listOf(100, 100), reads)
    }

    @Test fun alreadyPresentLearningNeedsNoExpandedProviderReadAndUnavailableSearchRowsNeverInventMetadata() {
        val due = listOf(row(9, 1))
        assertEquals(listOf(key(9), key(1)), DueOrder.prioritize(listOf(key(1), key(9)), due, { error("already ready") }) { it })
        assertEquals(listOf(key(1)), DueOrder.prioritize(listOf(key(1)), due,
            { DueOrder.expandLearning(due, { listOf(key(1)) }) { it } }) { it })
    }
}
