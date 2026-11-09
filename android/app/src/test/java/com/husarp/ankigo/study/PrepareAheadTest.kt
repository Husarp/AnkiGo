package com.husarp.ankigo.study

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PrepareAheadTest {
    private fun key(n: Long) = StudyLoop.Key(n, 0)
    private fun card(n: Long) = Prepared(key(n), mapOf("_id" to "$n", "reps" to "2"), "question$n")

    @Test fun contentFinishesWhileTheOrderedAnswerThreadIsBlocked() {
        val writeIo = Executors.newSingleThreadExecutor()
        val contentIo = Executors.newSingleThreadExecutor()
        val ahead = PrepareAhead<String>(contentIo, { 0 })
        val writing = CountDownLatch(1); val releaseWrite = CountDownLatch(1)
        try {
            val write = writeIo.submit { writing.countDown(); releaseWrite.await(5, TimeUnit.SECONDS) }
            assertTrue(writing.await(5, TimeUnit.SECONDS))
            ahead.start(key(2)) { card(2) }
            contentIo.submit {}.get(5, TimeUnit.SECONDS)
            assertFalse("the answer is still being written", write.isDone)
            assertEquals(card(2), ahead.take(key(2), card(2).before))
        } finally { releaseWrite.countDown(); ahead.close(); writeIo.shutdownNow() }
    }

    @Test fun unfinishedPreparationNeverBlocksACardLoad() {
        val io = Executors.newSingleThreadExecutor()
        val ahead = PrepareAhead<String>(io, { 0 })
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        try {
            ahead.start(key(2)) { started.countDown(); release.await(5, TimeUnit.SECONDS); card(2) }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertNull(ahead.take(key(2), card(2).before))
            release.countDown()
        } finally { release.countDown(); ahead.close() }
    }

    @Test fun changedStateExpiredContentAndExplicitReloadCannotReuseAPreparation() {
        val io = Executors.newSingleThreadExecutor()
        var now = 0L
        val ahead = PrepareAhead<String>(io, { now })
        fun prepare() { ahead.start(key(2)) { card(2) }; io.submit {}.get(5, TimeUnit.SECONDS) }
        try {
            prepare()
            assertNull(ahead.take(key(2), card(2).before + ("reps" to "3")))
            prepare(); now = 5_001
            assertNull(ahead.take(key(2), card(2).before))
            prepare(); ahead.clear()
            assertNull(ahead.take(key(2), card(2).before))
        } finally { ahead.close() }
    }

    @Test fun slowPreparationDoesNotRenewOldContentWhenItFinallyCompletes() {
        val io = Executors.newSingleThreadExecutor()
        var now = 0L
        val ahead = PrepareAhead<String>(io, { now })
        try {
            ahead.start(key(2)) { now = 6_000; card(2) }
            io.submit {}.get(5, TimeUnit.SECONDS)
            assertNull(ahead.take(key(2), card(2).before))
        } finally { ahead.close() }
    }

    @Test fun failedPreparationFallsBackToANormalRead() {
        val io = Executors.newSingleThreadExecutor()
        val ahead = PrepareAhead<String>(io, { 0 })
        try {
            ahead.start(key(2)) { error("media unavailable") }
            io.submit {}.get(5, TimeUnit.SECONDS)
            assertNull(ahead.take(key(2), card(2).before))
        } finally { ahead.close() }
    }

    @Test fun alreadyPreparedQueueContentIsReusedOnlyWithFreshMatchingState() {
        val io = Executors.newSingleThreadExecutor()
        var now = 0L
        val ahead = PrepareAhead<String>(io, { now })
        try {
            ahead.remember(card(2), 0)
            assertEquals(card(2), ahead.take(key(2), card(2).before))
            assertNull("consumed once", ahead.take(key(2), card(2).before))
            ahead.remember(card(2), 0)
            assertNull(ahead.take(key(2), card(2).before + ("reps" to "3")))
            ahead.remember(card(2), 0); now = 5001
            assertNull("a slow earlier read must not acquire a new lifetime", ahead.take(key(2), card(2).before))
        } finally { ahead.close() }
    }

    @Test fun rememberedContentStaysBoundedAndReloadClearsIt() {
        val io = Executors.newSingleThreadExecutor()
        val ahead = PrepareAhead<String>(io, { 0 })
        try {
            for (n in 1L..4) ahead.remember(card(n), 0)
            assertNull(ahead.take(key(1), card(1).before))
            assertEquals(card(2), ahead.take(key(2), card(2).before))
            ahead.clear()
            assertNull(ahead.take(key(4), card(4).before))
        } finally { ahead.close() }
    }
}
