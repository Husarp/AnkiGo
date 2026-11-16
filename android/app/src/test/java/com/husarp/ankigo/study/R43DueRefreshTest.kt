package com.husarp.ankigo.study

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class R43DueRefreshTest {
    private fun prepared(anki: FakeAnki, key: StudyLoop.Key) = Prepared(key, anki.stats(key)!!, key,
        PredictRule.LearnRead(anki.clock.ms, emptyList()))

    @Test fun idleLearningCardMovesAheadOfThePreparedNextButNeverReplacesTheDisplay() {
        val anki = FakeAnki()
        val shown = anki.add(1, 11)
        val next = anki.add(1, 12)
        val learning = anki.add(1, 13).apply { queue = 1 }
        val nc = NextCard<StudyLoop.Key>(1, { 1L }, { anki.clock.ms }, maxAhead = NextCard.AHEAD)
        nc.reload()
        nc.loaded(1, prepared(anki, shown.key), anki.top(1, 5))
        nc.prepared(shown.key, 1, prepared(anki, next.key))
        val before = nc.slot
        anki.clock.ms += 15_000
        anki.moveToTop(learning)
        val commands = nc.refreshQueue(shown.key, 1, anki.top(1, 5))
        assertSame(before, nc.slot)
        assertTrue(commands.all { it is Cmd.Prepare })
        assertEquals(Cmd.Prepare(learning.key, shown.key, 1), commands.first())
        nc.prepared(shown.key, 1, prepared(anki, learning.key))
        val answer = nc.tapAnswer(3, 250)
        assertEquals(shown.id, answer.filterIsInstance<Cmd.Enqueue>().single().answer.cardId)
        assertEquals(learning.key, answer.filterIsInstance<Cmd.Show<StudyLoop.Key>>().single().card.key)
        assertEquals(0, anki.count("answer"))
    }

    @Test fun unchangedQueueKeepsThePreparedNextAndAnEmptyQueueOnlyDropsWhatFollows() {
        val anki = FakeAnki(); val shown = anki.add(1, 11); val next = anki.add(1, 12)
        val nc = NextCard<StudyLoop.Key>(1, { 1L }, { anki.clock.ms })
        nc.reload(); nc.loaded(1, prepared(anki, shown.key), anki.top(1, 5))
        nc.prepared(shown.key, 1, prepared(anki, next.key))
        assertTrue(nc.refreshQueue(shown.key, 1, anki.top(1, 5)).isEmpty())
        val slot = nc.slot
        assertTrue(nc.refreshQueue(shown.key, 1, emptyList()).isEmpty())
        assertSame(slot, nc.slot)
        assertEquals(Cmd.ShowWaiting, nc.tapAnswer(3, 200)[1])
    }

    @Test fun unchangedIdleQueueKeepsTheNextTapReadyWithoutWaitingForPreparation() {
        val anki = FakeAnki(); val shown = anki.add(1, 11); val next = anki.add(1, 12)
        val nc = NextCard<StudyLoop.Key>(1, { 1L }, { anki.clock.ms })
        nc.reload(); nc.loaded(1, prepared(anki, shown.key), anki.top(1, 5))
        nc.prepared(shown.key, 1, prepared(anki, next.key))
        repeat(3) { assertTrue(nc.refreshQueue(shown.key, 1, anki.top(1, 5)).isEmpty()) }
        assertEquals(next.key, nc.tapAnswer(3, 100).filterIsInstance<Cmd.Show<StudyLoop.Key>>().single().card.key)
    }

    @Test fun unchangedIdleQueueRetainsAnAlreadyPreparedLearningFallback() {
        val anki = FakeAnki().apply { clock.ms = 100_000 }
        val shown = anki.add(1, 11); val next = anki.add(1, 12)
        val learning = anki.add(1, 13).apply { queue = 1; due = 110 }
        val read = PredictRule.LearnRead(anki.clock.ms, listOf(PredictRule.Due(learning.id, learning.due, learning.key)))
        val nc = NextCard<StudyLoop.Key>(1, { 1L }, { anki.clock.ms }, maxAhead = NextCard.AHEAD, check = PredictRule::check)
        nc.reload(); nc.loaded(1, prepared(anki, shown.key), anki.top(1, 5))
        nc.prepared(shown.key, 1, prepared(anki, next.key).copy(learning = read))
        nc.prepared(next.key, 1, prepared(anki, learning.key).copy(learning = read))
        nc.preparedLearning(shown.key, 1, prepared(anki, learning.key))
        anki.clock.ms += 15_000
        nc.refreshQueue(shown.key, 1, anki.top(1, 5))
        assertEquals(learning.key, nc.tapAnswer(3, 100).filterIsInstance<Cmd.Show<StudyLoop.Key>>().single().card.key)
        assertEquals(1, nc.predictedLearning)
    }

    @Test fun staleQueueResultsCannotChangePredictionsOrAHeldTap() {
        val anki = FakeAnki(); val a = anki.add(1, 11); val b = anki.add(1, 12)
        val nc = NextCard<StudyLoop.Key>(1, { 1L }, { anki.clock.ms })
        nc.reload(); nc.loaded(1, prepared(anki, a.key), anki.top(1, 5))
        nc.prepared(a.key, 1, prepared(anki, b.key))
        assertTrue(nc.refreshQueue(a.key, 0, emptyList()).isEmpty())
        assertTrue(nc.refreshQueue(b.key, 1, emptyList()).isEmpty())
        nc.tapAnswer(3, 100); nc.tapAnswer(2, 120)
        val held = nc.slot
        assertTrue(nc.refreshQueue(a.key, 1, emptyList()).isEmpty())
        assertSame(held, nc.slot)
        assertNotNull((nc.slot as NextCard.Slot.Predicted).held)
    }

    @Test fun screenPollsAtFifteenSecondsOrEarlierLearningDeadlineAndRetiresPausedReads() {
        val clock = FakeClock()
        var wake: Pair<Long, () -> Unit>? = null
        var reply: ((Int?, Long?) -> Unit)? = null
        val shown = mutableListOf<Int>()
        val live = LiveRefresh<Int>({ clock.ms }, { ms, f -> wake = clock.ms + ms to f }, { wake = null },
            read = { reply = it }, show = { shown += it }, maxDelay = 15_000)
        fun run() { val (at, f) = wake!!; wake = null; clock.ms = at; f() }
        live.start(); run(); reply!!(1, null)
        assertEquals(15_000L, wake!!.first)
        run(); reply!!(2, 20_000)
        assertEquals(20_000L, wake!!.first)
        run(); live.invalidate(); reply!!(99, null)
        assertEquals(listOf(1, 2), shown)
        run(); live.stop(); reply!!(88, null)
        assertNull(wake); assertEquals(listOf(1, 2), shown)
        clock.ms = 50_000; live.start(); run(); reply!!(3, null)
        assertEquals(listOf(1, 2, 3), shown)
        assertEquals(65_000L, wake!!.first)
        live.stop()
    }

    @Test fun queuedAnswersDelayRefreshAndSlowRefreshCannotHoldATapOrPublishAfterIt() {
        val anki = FakeAnki(); val a = anki.add(1, 11); val b = anki.add(1, 12)
        val nc = NextCard<StudyLoop.Key>(1, { 1L }, { anki.clock.ms })
        nc.reload(); nc.loaded(1, prepared(anki, a.key), anki.top(1, 5))
        nc.prepared(a.key, 1, prepared(anki, b.key))
        var blocked = true
        var wake: (() -> Unit)? = null
        var reply: (() -> Unit)? = null
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val io = Executors.newSingleThreadExecutor()
        val live = LiveRefresh<List<StudyLoop.Key>>({ anki.clock.ms }, { _, f -> wake = f }, { wake = null }, { blocked },
            read = { done -> io.execute {
                entered.countDown(); check(release.await(5, TimeUnit.SECONDS))
                val queue = anki.top(1, 5)
                reply = { done(queue, null) }
            } }, show = { fail("the answer must retire this result") }, maxDelay = 15_000)
        try {
            live.start(); wake!!()
            assertEquals(1L, entered.count)
            blocked = false; wake!!()
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            val commands = nc.tapAnswer(3, 100)
            live.invalidate()
            assertEquals(b.key, commands.filterIsInstance<Cmd.Show<StudyLoop.Key>>().single().card.key)
            assertEquals(1L, release.count)
            release.countDown()
            io.submit {}.get(3, TimeUnit.SECONDS)
            reply!!()
        } finally { release.countDown(); live.stop(); io.shutdownNow(); io.awaitTermination(3, TimeUnit.SECONDS) }
    }

    @Test fun refreshingTheSameLoadAlsoRefreshesItsLearningRead() {
        var reads = 0
        val cache = LearnCache { PredictRule.LearnRead((++reads).toLong(), emptyList()) }
        assertEquals(1L, cache.get(1).at)
        assertEquals(1L, cache.get(1).at)
        assertEquals(2L, cache.get(1, 1).at)
        assertEquals(2L, cache.get(1, 1).at)
        assertEquals(3L, cache.get(2, 1).at)
    }
}
