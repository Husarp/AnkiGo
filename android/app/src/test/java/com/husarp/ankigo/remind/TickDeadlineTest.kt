package com.husarp.ankigo.remind

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TickDeadlineTest {
    private fun await(l: CountDownLatch) = assertTrue("timed out waiting", l.await(5, TimeUnit.SECONDS))

    @Test fun aFinishedTickEndsOnceWithoutTimeout() {
        val ended = CountDownLatch(1)
        val ends = AtomicInteger()
        val timedOut = AtomicReference<Boolean>()
        TickDeadline.run(5_000, work = { }, onError = { }, onEnd = { t -> timedOut.set(t); ends.incrementAndGet(); ended.countDown() })
        await(ended)
        Thread.sleep(50)
        assertEquals(1, ends.get())
        assertEquals(false, timedOut.get())
    }

    @Test fun aThrowingTickReportsTheErrorAndStillEnds() {
        val ended = CountDownLatch(1)
        val error = AtomicReference<Throwable>()
        TickDeadline.run(5_000, work = { throw IllegalStateException("boom") }, onError = { error.set(it) }, onEnd = { ended.countDown() })
        await(ended)
        assertEquals("boom", error.get().message)
    }

    @Test fun aStuckQueryCannotKeepTheReceiverOpenAndItPostsNothingWhenItComesBack() {
        val release = CountDownLatch(1)
        val ended = CountDownLatch(1)
        val workDone = CountDownLatch(1)
        val ends = AtomicInteger()
        val timedOut = AtomicReference<Boolean>()
        val posted = AtomicInteger()
        val guardSeen = AtomicReference<TickGuard>()
        val started = System.nanoTime()
        TickDeadline.run(
            150,
            work = { guard ->
                guardSeen.set(guard)
                release.await() // the slow AnkiDroid query
                guard.whileLive { posted.incrementAndGet() }
                workDone.countDown()
            },
            onError = { },
            onEnd = { t -> timedOut.set(t); ends.incrementAndGet(); ended.countDown() },
        )
        await(ended)
        assertTrue("the deadline did not end the receiver in time", (System.nanoTime() - started) / 1_000_000 < 3_000)
        assertEquals(true, timedOut.get())
        assertTrue(guardSeen.get().expired)
        release.countDown() // the query finally returns
        await(workDone)
        Thread.sleep(50)
        assertEquals(0, posted.get()) // nothing is posted late
        assertEquals(1, ends.get()) // and the receiver is not finished a second time
    }

    @Test fun theDeadlineWaitsForAStepThatIsAlreadyPosting() {
        val inside = CountDownLatch(1)
        val letGo = CountDownLatch(1)
        val ended = CountDownLatch(1)
        val order = StringBuffer()
        TickDeadline.run(
            100,
            work = { guard ->
                guard.whileLive { inside.countDown(); letGo.await(); order.append("step;") }
            },
            onError = { },
            onEnd = { order.append("end;"); ended.countDown() },
        )
        await(inside)
        Thread.sleep(300) // the deadline has come but the step is still running: it is not cut in half
        assertEquals("", order.toString())
        letGo.countDown()
        await(ended)
        assertEquals("step;end;", order.toString())
    }

    @Test fun guardWithoutDeadlineAlwaysRuns() {
        assertFalse(TickGuard.NONE.expired)
        assertEquals(7, TickGuard.NONE.whileLive { 7 })
        val g = TickGuard()
        assertNotNull(g.whileLive { 1 })
        assertTrue(g.expire())
        assertFalse(g.expire())
        assertNull(g.whileLive { 1 })
    }
}
