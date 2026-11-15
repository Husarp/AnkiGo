package com.husarp.ankigo.study

import com.husarp.ankigo.game.GameEvent
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class R34AnswerIoTest {
    private class Screen : StudyListener {
        val saved = ArrayList<AnswerOutcome>()
        var games = 0
        var warnings = 0
        override fun journalFailed() { warnings++ }
        override fun unresolved(u: AnswerHost.Unresolved?) {}
        override fun outcome(o: AnswerOutcome, info: AnswerHost.Info) { saved += o }
        override fun gameDone(answer: QueuedAnswer, events: List<GameEvent>, absorbMs: Long, gameMs: Long, mine: Boolean) { games++ }
    }

    private class Rig(val failJournal: Boolean = false, val failGame: Boolean = false, val blockProvider: Boolean = false) : AutoCloseable {
        val io = Executors.newSingleThreadExecutor()
        val journalIo = Executors.newSingleThreadExecutor()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val inProvider = CountDownLatch(1)
        val releaseProvider = CountDownLatch(1)
        val main = LinkedBlockingQueue<Runnable>()
        val order = CopyOnWriteArrayList<String>()
        val fake = FakeAnki().apply { add(7, 101); add(7, 102) }
        val memory = MemStore()
        var gameThread: Thread? = null
        var journalThread: Thread? = null
        val provider = object : AnswerProvider by fake {
            override fun answer(key: StudyLoop.Key, ease: Int, ms: Long) {
                order += "provider"
                if (blockProvider && key == fake.queueOf(7).first().key) {
                    inProvider.countDown()
                    check(releaseProvider.await(10, TimeUnit.SECONDS))
                }
                fake.answer(key, ease, ms)
            }
        }
        val store = object : JournalStore by memory {
            override fun write(text: String) {
                journalThread = Thread.currentThread()
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
                if (failJournal) error("storage full")
                memory.write(text)
                order += "journal"
            }
        }
        val env = object : AnswerHost.Env {
            override fun post(r: Runnable) { main += r }
            override fun elapsed() = System.nanoTime() / 1_000_000
            override fun expect(before: Map<String, String?>, pinned: Boolean) {}
            override fun cancel(before: Map<String, String?>) {}
            override fun log(text: String) {}
            override fun gameStep(answer: QueuedAnswer, front: String?, done: (List<GameEvent>, Long) -> Unit) {
                gameThread = Thread.currentThread()
                order += "game"
                if (failGame) error("game failed")
                done(emptyList(), 0)
            }
            override fun restored(answers: List<QueuedAnswer>, done: () -> Unit) = done()
            override fun settled(answer: QueuedAnswer, after: Map<String, String?>, saved: Boolean) {}
            override fun dropped(answer: QueuedAnswer) {}
        }
        val host = AnswerHost(provider, store, env, io, journalIo = journalIo)
        val screen = Screen()
        init { host.start(); host.register(screen) }
        fun entry(index: Int = 0): QueuedAnswer {
            val c = fake.queueOf(7)[index]
            return QueuedAnswer(host.nextSeq(), 7, c.nid, c.ord, c.id, fake.stats(c.key)!!, 3, 100, 1)
        }
        fun pumpUntil(done: () -> Boolean) {
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (!done()) {
                main.poll(10, TimeUnit.MILLISECONDS)?.run()
                check(System.nanoTime() < end) { "timed out: $order" }
            }
        }
        override fun close() { release.countDown(); releaseProvider.countDown(); io.shutdownNow(); journalIo.shutdownNow(); io.awaitTermination(3, TimeUnit.SECONDS); journalIo.awaitTermination(3, TimeUnit.SECONDS) }
    }

    @Test fun blockedStorageDoesNotHoldTheTapAndTheNextLoadCannotOvertakeTheAnswer() {
        Rig().use { r ->
            val caller = Thread.currentThread()
            r.host.enqueueAsync(r.entry(), "front", r.screen)
            assertTrue(r.entered.await(3, TimeUnit.SECONDS))
            assertNotSame(caller, r.journalThread)
            assertTrue(r.host.unresolvedNow()) // does not take the journal lock
            assertEquals(1, r.host.unresolved()!!.count)
            assertEquals(0, r.screen.games)
            assertTrue(r.fake.revlog.isEmpty())
            assertTrue(r.memory.text.isNullOrEmpty())
            val loaded = CountDownLatch(1)
            r.host.submit(null, Runnable { r.order += "load"; loaded.countDown() })
            assertFalse(loaded.await(50, TimeUnit.MILLISECONDS))
            r.release.countDown()
            r.pumpUntil { r.screen.saved.size == 1 && loaded.count == 0L }
            assertSame(caller, r.gameThread)
            assertEquals(listOf("journal", "game", "provider", "load"), r.order.toList())
            assertEquals(1, r.screen.games)
            assertTrue(r.screen.saved.single() is AnswerOutcome.Saved)
            assertFalse(r.host.unresolvedNow())
            assertTrue(AnswerJournal.decode(r.memory.text).answers.isEmpty())
        }
    }

    @Test fun rapidAnswersKeepTheirGameAndProviderOrderAheadOfTheLoad() {
        Rig().use { r ->
            val first = r.entry(0); val second = r.entry(1)
            r.host.enqueueAsync(first, "one", r.screen)
            r.host.enqueueAsync(second, "two", r.screen)
            val loaded = CountDownLatch(1)
            r.host.submit(null, Runnable { r.order += "load"; loaded.countDown() })
            assertEquals(2, r.host.unresolved()!!.count)
            r.release.countDown()
            r.pumpUntil { r.screen.saved.size == 2 && loaded.count == 0L }
            assertTrue(r.order.count { it == "journal" } >= 2)
            assertEquals(2, r.order.count { it == "game" })
            assertEquals(2, r.order.count { it == "provider" })
            assertEquals("load", r.order.last())
            assertEquals(listOf(first.cardId, second.cardId), r.fake.revlog.map { it.cardId })
            assertEquals(2, r.screen.games)
        }
    }

    @Test fun anotherAnswersGameDoesNotWaitForASlowAnkiDroidWrite() {
        Rig(blockProvider = true).use { r ->
            val first = r.entry(0); val second = r.entry(1)
            r.host.enqueueAsync(first, "one", r.screen)
            r.release.countDown()
            r.pumpUntil { r.screen.games == 1 }
            assertTrue(r.inProvider.await(3, TimeUnit.SECONDS))
            r.host.enqueueAsync(second, "two", r.screen)
            r.pumpUntil { r.screen.games == 2 }
            assertTrue(r.fake.revlog.isEmpty())
            assertEquals(listOf(first.seq, second.seq), AnswerJournal.decode(r.memory.text).answers.map { it.seq })
            r.releaseProvider.countDown()
            r.pumpUntil { r.screen.saved.size == 2 }
            assertEquals(2, r.fake.revlog.size)
        }
    }

    @Test fun theDurableJournalCanReplayEvenIfTheGameCallbackHasNotRun() {
        Rig().use { r ->
            val answer = r.entry()
            r.host.enqueueAsync(answer, "front", r.screen)
            r.release.countDown()
            assertNotNull(r.main.poll(3, TimeUnit.SECONDS)) // pretend the process dies before this game callback runs
            val restored = AnswerJournal.decode(r.memory.text).answers.single()
            assertEquals(answer.seq, restored.seq)
            assertEquals(answer.before.filterKeys { it in AnswerState.JOURNAL }, restored.before)
            assertTrue(restored.restored)
            assertTrue(r.fake.revlog.isEmpty())
            assertEquals(0, r.screen.games)
        }
    }

    @Test fun journalFailureWarnsTheCurrentScreenAndStillWritesOnceAfterTheOldScreenLeaves() {
        Rig(failJournal = true).use { r ->
            r.host.enqueueAsync(r.entry(), "front", r.screen)
            assertTrue(r.entered.await(3, TimeUnit.SECONDS))
            r.host.unregister(r.screen)
            // Register before releasing the worker: registration currently invalidates the queue selection.
            r.release.countDown()
            val next = Screen(); r.host.register(next)
            r.pumpUntil { next.saved.isNotEmpty() }
            assertEquals(0, r.screen.warnings)
            assertEquals(1, next.warnings)
            assertEquals(1, next.games)
            assertEquals(1, r.fake.revlog.size)
        }
    }

    @Test fun aFailedGameStepStillReleasesTheWriteAndPendingGameTicket() {
        Rig(failGame = true).use { r ->
            r.host.enqueueAsync(r.entry(), "front", r.screen)
            r.release.countDown()
            r.pumpUntil { r.screen.saved.isNotEmpty() }
            assertEquals(1, r.screen.games)
            assertEquals(1, r.fake.revlog.size)
        }
    }
}
