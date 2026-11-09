package com.husarp.ankigo.study

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class R32LogLagTest {
    private fun key(n: Long) = StudyLoop.Key(n, 0)
    private val now = 1_700_000_000_000L
    private fun card(n: Long, read: PredictRule.LearnRead = PredictRule.LearnRead(now, emptyList())) = Prepared(key(n),
        mapOf("_id" to "$n", "note_id" to "$n", "deck_id" to "1", "reps" to "3", "queue" to "2", "due" to "5"), "card$n", read)
    private fun ready(clock: () -> Long = { now }, read: PredictRule.LearnRead = PredictRule.LearnRead(now, emptyList())): NextCard<String> {
        var seq = 0L
        return NextCard<String>(1, { ++seq }, clock, maxAhead = NextCard.AHEAD, check = PredictRule::check, keepReadyOnLearning = true).also { nc ->
            nc.reload(); nc.loaded(1, card(1), (1L..4).map(::key))
            for (n in 2L..4) nc.prepared(key(n - 1), 1, card(n, read))
        }
    }
    private fun shown(commands: List<Cmd<String>>) = commands.filterIsInstance<Cmd.Show<String>>().single().card
    private fun fast(block: () -> Unit) {
        val at = System.nanoTime()
        block()
        assertTrue("next question must not wait for provider IO", (System.nanoTime() - at) / 1_000_000 < 400)
    }

    @Test fun learningCutoffCannotExposeASlowWriteWhenARealSchedulerCardIsReady() {
        val read = PredictRule.LearnRead(now, listOf(PredictRule.Due(9, now / 1000 - 1, key(9))))
        val nc = ready(read = read)
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val io = Executors.newSingleThreadExecutor()
        val states = (1L..9).associate { key(it) to card(it).before }.toMutableMap()
        val provider = object : AnswerProvider {
            override fun select(deckId: Long) {}
            override fun top(deckId: Long, limit: Int) = listOf(key(1))
            override fun stats(key: StudyLoop.Key) = states[key]
            override fun answer(key: StudyLoop.Key, ease: Int, ms: Long) {
                entered.countDown(); check(release.await(10, TimeUnit.SECONDS))
                states[key] = states.getValue(key) + ("reps" to "4")
            }
        }
        val queue = AnswerQueue(provider, object : JournalStore {
            override fun read(): String? = null
            override fun write(text: String) {}
            override fun clear() {}
        }, object : AnswerHooks {
            override fun expect(before: Map<String, String?>) {}
            override fun cancel(before: Map<String, String?>) {}
            override fun outcome(o: AnswerOutcome) {}
        }, LockWaiter(), tryNotTop = { true }, wallNow = { now })
        try {
            val first = nc.tapAnswer(3, 50)
            queue.enqueue(first.filterIsInstance<Cmd.Enqueue>().single().answer)
            val write = io.submit { queue.drain() }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            assertEquals(key(2), shown(first).key)
            fast { assertEquals(key(3), shown(nc.tapAnswer(4, 75)).key) }
            assertFalse(write.isDone)
            assertEquals("", nc.lastWait)
            // A changed cutoff may disagree, but it never replaces the question being read or retargets its answer.
            nc.swapped(3, card(9), listOf(key(9), key(3), key(4)))
            val answer = nc.tapAnswer(2, 100).filterIsInstance<Cmd.Enqueue>().single().answer
            assertEquals(3L, answer.cardId); assertEquals(2, answer.ease); assertEquals(100L, answer.tookMs)
            release.countDown(); write.get(3, TimeUnit.SECONDS)
        } finally { release.countDown(); io.shutdownNow(); io.awaitTermination(3, TimeUnit.SECONDS) }
    }

    @Test fun preparedLearningStillWinsAndUntrustedOrDisabledReadsStillWait() {
        val read = PredictRule.LearnRead(now, listOf(PredictRule.Due(9, now / 1000 - 1, key(9))))
        val nc = ready(read = read)
        val learn = card(9).copy(before = card(9).before + ("queue" to "1") + ("due" to "${now / 1000 - 1}"))
        nc.preparedLearning(key(1), 1, learn)
        assertEquals(learn, shown(nc.tapAnswer(3, 1)))
        for (bad in listOf(PredictRule.LearnRead(now - PredictRule.MAX_AGE_MS - 1, emptyList()), PredictRule.LearnRead(now, null))) {
            assertTrue(ready(read = bad).tapAnswer(3, 1).contains(Cmd.ShowWaiting))
        }
        val off = NextCard<String>(1, { 1L }, { now }, check = { _, _, _ -> PredictRule.Why.WAITING_DISABLED }, keepReadyOnLearning = true)
        off.reload(); off.loaded(1, card(1), listOf(key(1), key(2))); off.prepared(key(1), 1, card(2, read))
        assertTrue(off.tapAnswer(3, 1).contains(Cmd.ShowWaiting))
    }

    @Test fun confirmationReusesMatchingContentWhileTheNoteProviderIsBlocked() {
        val nc = ready(); nc.tapAnswer(3, 1)
        val worker = Executors.newSingleThreadExecutor()
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        try {
            val slowNote = worker.submit { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            fast {
                val loaded = SchedulerCardRead.load<StudyLoop.Key, String>(listOf(key(2), key(3), key(4)), key(2), { it }) {
                    slowNote.get(10, TimeUnit.SECONDS)
                    error("confirmation must not fetch HTML/note again")
                }
                val commands = nc.confirmed(2, key(2), loaded.queue)
                assertEquals(listOf<Cmd<String>>(Cmd.Confirm(card(2))), commands)
                assertEquals(key(3), shown(nc.tapAnswer(3, 1)).key)
            }
            assertFalse(slowNote.isDone)
        } finally { release.countDown(); worker.shutdownNow(); worker.awaitTermination(3, TimeUnit.SECONDS) }
    }

    @Test fun confirmationDropsChangedQueueSuffixAndFetchesAnUnpreparedTop() {
        val nc = ready(); nc.tapAnswer(3, 1)
        val commands = nc.confirmed(2, key(2), listOf(key(2), key(3), key(8)))
        assertEquals(listOf<Cmd<String>>(Cmd.Confirm(card(2)), Cmd.Prepare(key(8), key(3), 2)), commands)
        nc.tapAnswer(3, 1)
        assertEquals(listOf<Cmd<String>>(Cmd.ShowHeld(3)), nc.tapAnswer(3, 1)) // stale card 4 was discarded
        var fetches = 0
        val result = SchedulerCardRead.load(listOf(key(8)), key(2), { it }) {
            fetches++; SchedulerCardRead.Result(card(8), listOf(key(8)), 1)
        }
        assertEquals(1, fetches); assertEquals(key(8), result.card!!.key)
    }

    @Test fun reusedLearningReadDoesNotBringBackAnAlreadyAnsweredDueCard() {
        val read = PredictRule.LearnRead(now, listOf(PredictRule.Due(1, now / 1000 - 1, key(1))))
        val nc = NextCard<String>(1, { 1L }, { now }, maxAhead = NextCard.AHEAD, check = PredictRule::check)
        nc.reload(); nc.loaded(1, card(1), (1L..4).map(::key))
        for (n in 2L..4) nc.prepared(key(n - 1), 1, card(n, read))
        assertEquals(key(2), shown(nc.tapAnswer(3, 1)).key)
        nc.confirmed(2, key(2), listOf(key(2), key(3), key(4)))
        assertEquals(key(3), shown(nc.tapAnswer(3, 1)).key)
        assertEquals(0, nc.learningFallbacks)
    }

    @Test fun idleLearningRefreshPreventsOldReadWaitWithoutReplacingTheShownCard() {
        var clock = now
        val nc = ready(clock = { clock })
        clock += PredictRule.MAX_AGE_MS + 1
        nc.refreshLearning(key(1), 1, PredictRule.LearnRead(clock, emptyList()))
        assertEquals(key(1), (nc.slot as NextCard.Slot.Confirmed).card.key)
        fast { assertEquals(key(2), shown(nc.tapAnswer(3, 1)).key) }
        // A result from the previous card/load cannot renew the next card's read.
        nc.confirmed(2, key(2), listOf(key(2), key(3), key(4)))
        nc.refreshLearning(key(1), 1, PredictRule.LearnRead(clock, emptyList()))
        clock += PredictRule.MAX_AGE_MS + 1
        assertTrue(nc.tapAnswer(3, 1).contains(Cmd.ShowWaiting))
    }

    @Test fun slowDeckTreeNeverBlocksPreparationAndRepeatedRequestsShareTheRead() {
        val io = Executors.newSingleThreadExecutor()
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        var reads = 0
        val names = DeckNames { reads++; entered.countDown(); check(release.await(10, TimeUnit.SECONDS)); mapOf(2L to "Parent::Child") }
        val submit: (() -> Unit) -> Unit = { task -> io.execute(task) }
        try {
            names.request(2, submit)
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            fast {
                repeat(10) { names.request(2, submit); assertNull(names.cached(2)) }
                assertEquals(key(2), shown(ready().tapAnswer(3, 1)).key)
            }
            release.countDown(); io.submit {}.get(3, TimeUnit.SECONDS)
            assertEquals("Parent::Child", names.cached(2)); assertEquals(1, reads)
            names.request(2, submit); io.submit {}.get(3, TimeUnit.SECONDS)
            assertEquals(1, reads)
        } finally { release.countDown(); io.shutdownNow(); io.awaitTermination(3, TimeUnit.SECONDS) }
    }
}
