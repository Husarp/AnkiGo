package com.husarp.ankigo.study

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Deliberately slow provider lives only in tests. No production provider adds latency. */
class R30LagQueueTest {
    private fun key(n: Long) = StudyLoop.Key(n, 0)
    private fun card(n: Long) = Prepared(key(n), mapOf("_id" to "$n", "note_id" to "$n", "deck_id" to "1",
        "reps" to "3", "due" to "5", "queue" to "2", "last_review_time_secs" to "9"), "card$n",
        PredictRule.LearnRead(1000, emptyList()))
    private fun nc(): NextCard<String> {
        var seq = 0L
        return NextCard(1, { ++seq }, { 1000 }, maxAhead = NextCard.AHEAD, check = PredictRule::check)
    }
    private fun ready(nc: NextCard<String>, count: Long = 4) {
        nc.reload(); nc.loaded(1, card(1), (1..count).map(::key))
        for (n in 2..count) nc.prepared(key(n - 1), 1, card(n))
    }
    private inner class SlowAnkiDroid : AnswerProvider {
        val entered = CountDownLatch(1)
        val written = CountDownLatch(1)
        var order = mutableListOf(key(1), key(2), key(3), key(4))
        val states = (1L..10L).associate { key(it) to card(it).before }.toMutableMap()
        override fun select(deckId: Long) {}
        override fun top(deckId: Long, limit: Int): List<StudyLoop.Key> {
            Thread.sleep(500)
            return order.take(limit)
        }
        override fun stats(key: StudyLoop.Key) = states[key]
        override fun answer(key: StudyLoop.Key, ease: Int, ms: Long) {
            entered.countDown()
            Thread.sleep(1000)
            if (order.firstOrNull() == key) {
                states[key] = states.getValue(key) + ("reps" to "4") + ("due" to "6")
                order.removeAt(0)
            }
            written.countDown()
        }
    }
    private class MemoryJournal : JournalStore {
        var text: String? = null
        override fun read() = text
        override fun write(text: String) { this.text = text }
        override fun clear() { text = null }
    }
    private fun queue(provider: AnswerProvider, outcomes: MutableList<AnswerOutcome> = mutableListOf(), journal: JournalStore = MemoryJournal()) =
        AnswerQueue(provider, journal, object : AnswerHooks {
            override fun expect(before: Map<String, String?>) {}
            override fun cancel(before: Map<String, String?>) {}
            override fun outcome(o: AnswerOutcome) { outcomes += o }
        }, object : Waiter {
            override fun clear() {}
            override fun wake() {}
            override fun await(ms: Long): Boolean = error("unexpected retry")
        }, tryNotTop = { true }, wallNow = { 1000 })

    @Test fun preparedNextCardShowsWhileSlowWriteAndReadAreStillOutstanding() {
        val nc = nc(); ready(nc)
        val provider = SlowAnkiDroid(); val outcomes = mutableListOf<AnswerOutcome>(); val answers = queue(provider, outcomes)
        val io = Executors.newSingleThreadExecutor()
        try {
            val start = System.nanoTime()
            val commands = nc.tapAnswer(3, 50)
            val answer = commands.filterIsInstance<Cmd.Enqueue>().single().answer
            answers.enqueue(answer)
            val write = io.submit { answers.drain() }
            assertEquals(card(2), commands.filterIsInstance<Cmd.Show<String>>().single().card)
            assertTrue("display is immediate, not behind a 500 ms read + 1000 ms write", (System.nanoTime() - start) / 1_000_000 < 400)
            assertTrue(provider.entered.await(3, TimeUnit.SECONDS))
            assertFalse("the next card is already displayed while the answer writes", write.isDone)
            assertEquals(1L, provider.written.count)
            write.get(4, TimeUnit.SECONDS)
            assertTrue(outcomes.single() is AnswerOutcome.Saved)
            assertEquals("4", provider.states.getValue(key(1))["reps"])
        } finally { io.shutdownNow(); io.awaitTermination(3, TimeUnit.SECONDS) }
    }

    @Test fun lateMismatchKeepsShownCardAndItsHeldTapGoesThrough195WaitingJournal() {
        val nc = nc(); ready(nc, 2)
        nc.tapAnswer(3, 50) // card 2 displayed
        nc.tapAnswer(4, 75) // held: nothing prepared after it
        val commands = nc.swapped(2, card(7), listOf(key(7), key(2), key(8)))
        assertEquals(Cmd.Confirm(card(2), onTop = false), commands.first())
        val answer = commands.filterIsInstance<Cmd.Enqueue>().single().answer
        assertEquals(2L, answer.cardId); assertEquals(4, answer.ease); assertEquals(75L, answer.tookMs)
        assertEquals(0, nc.heldDropped)
        val provider = SlowAnkiDroid().apply { order = mutableListOf(key(7), key(2)) }
        val journal = MemoryJournal(); val outcomes = mutableListOf<AnswerOutcome>()
        val answers = queue(provider, outcomes, journal)
        answers.enqueue(answer); answers.drain()
        assertTrue(outcomes.single() is AnswerOutcome.Waiting)
        assertEquals(answer.cardId, answers.waitingAnswers().single().cardId)
        assertTrue(AnswerJournal.decode(journal.text).answers.single().waiting)
        provider.order = mutableListOf(key(2))
        assertTrue(answers.writeWaiting(1, key(2)))
        assertTrue(outcomes.last() is AnswerOutcome.Saved)
        assertTrue(answers.waitingAnswers().isEmpty()); assertNull(journal.text)
    }

    @Test fun lateMismatchWithoutTapOnlyChangesWhatFollowsAndLatePreparationCannotReplaceIt() {
        val nc = nc(); ready(nc)
        nc.tapAnswer(3, 50)
        val commands = nc.swapped(2, card(7), listOf(key(7), key(8), key(2)))
        assertTrue(commands.none { it is Cmd.Show })
        assertEquals(NextCard.Slot.Confirmed(card(2), 2, onTop = false), nc.slot)
        assertNull(nc.actionCard())
        nc.prepared(key(1), 1, card(9)) // stale
        val tap = nc.tapAnswer(2, 123)
        assertEquals(2L, tap.filterIsInstance<Cmd.Enqueue>().single().answer.cardId)
        assertEquals(card(7), tap.filterIsInstance<Cmd.Show<String>>().single().card)
        assertTrue(nc.confirmed(2, key(2), listOf(key(2))).isEmpty())
    }

    @Test fun emptyOrFailedLateReadKeepsTheDisplayAndQueuesItsHeldAnswer() {
        for (failed in listOf(false, true)) {
            val nc = nc(); ready(nc, 2); nc.tapAnswer(3, 1); nc.tapAnswer(2, 99)
            val commands = if (failed) nc.failed(2, "offline") else nc.finished(2)
            assertTrue(commands.none { it is Cmd.Show || it is Cmd.ShowError || it == Cmd.ShowFinished })
            assertEquals(2L, commands.filterIsInstance<Cmd.Enqueue>().single().answer.cardId)
            assertEquals(0, nc.heldDropped)
        }
    }

    @Test fun nextReadAddsNewProviderCardsAndUsesOnlyRealDueEntriesBeforeNew() {
        val nc = nc(); ready(nc, 2); nc.tapAnswer(3, 1)
        val commands = nc.confirmed(2, key(2), listOf(key(2), key(9), key(10)))
        assertTrue(commands.contains(Cmd.Prepare(key(9), key(2), 2)))
        nc.prepared(key(2), 2, card(9))
        assertEquals(card(9), nc.tapAnswer(3, 1).filterIsInstance<Cmd.Show<String>>().single().card)
        val due = listOf(mapOf("note_id" to "7", "ord" to "0", "queue" to "2"))
        assertEquals(listOf(key(7), key(1)), DueOrder.prioritize(listOf(key(1), key(7)), due, { error("already present") }) { it })
        assertEquals(listOf(key(1)), DueOrder.prioritize(listOf(key(1)), due, { listOf(key(1)) }) { it })
    }
    @Test fun leavingPersistsAHeldTapOnceBeforeInvalidatingItsConfirmation() {
        val nc = nc(); ready(nc, 2); nc.tapAnswer(3, 1); nc.tapAnswer(4, 99)
        val answer = nc.releaseHeld().filterIsInstance<Cmd.Enqueue>().single().answer
        assertEquals(2L, answer.cardId); assertEquals(4, answer.ease); assertEquals(99L, answer.tookMs)
        assertTrue(nc.releaseHeld().isEmpty())
        nc.leave()
        assertTrue(nc.confirmed(2, key(2), listOf(key(2))).isEmpty())
        assertEquals(0, nc.heldDropped)
    }

    @Test fun disablingWaitingAnswersGivesAnExplicitWaitInsteadOfAnUnsafePrediction() {
        val nc = NextCard<String>(1, { 1L }, { 1000 }, maxAhead = NextCard.AHEAD,
            check = { _, _, _ -> PredictRule.Why.WAITING_DISABLED })
        ready(nc, 2)
        val commands = nc.tapAnswer(3, 1)
        assertEquals(Cmd.ShowWaiting, commands[1])
        assertEquals("waiting answers disabled", nc.lastWait)
        assertTrue(commands.none { it is Cmd.Show })
    }

}
