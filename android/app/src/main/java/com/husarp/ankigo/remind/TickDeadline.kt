package com.husarp.ankigo.remind

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * The time box of one alarm tick. A receiver using goAsync() may only stay open a short while, and an AnkiDroid query can block for
 * as long as AnkiDroid likes. When the deadline passes the guard is [expired]: the tick's [whileLive] steps no longer start, so
 * nothing is posted or saved late, while the receiver is finished without waiting for the stuck query.
 */
class TickGuard {
    @Volatile var expired = false
        private set
    private val lock = Any()

    /**
     * Runs [block] unless the deadline has passed (then null). The deadline waits for a block that is already running: such a block only
     * posts and saves our own state, it never asks AnkiDroid.
     */
    fun <T : Any> whileLive(block: () -> T): T? = synchronized(lock) { if (expired) null else block() }

    internal fun expire(): Boolean = synchronized(lock) {
        if (expired) false else { expired = true; true }
    }

    companion object {
        /** For callers without a deadline (tests, settings screens): never expires. */
        val NONE = TickGuard()
    }
}

/** Runs a tick's work with a hard deadline (pure JVM: threads only). */
object TickDeadline {
    /** The longest the receiver stays open: well inside the broadcast limit. */
    const val LIMIT_MS = 8_000L

    /**
     * Runs [work] on a worker thread and calls [onEnd] exactly once, from whichever thread gets there first: with `false` when the
     * work returned (or threw, [onError] is called first), with `true` when [limitMs] passed before. After a timeout the worker may
     * still be inside a slow query; the guard it was given is expired, so it posts nothing when it comes back.
     */
    fun run(limitMs: Long, work: (TickGuard) -> Unit, onError: (Throwable) -> Unit, onEnd: (timedOut: Boolean) -> Unit): TickGuard {
        val guard = TickGuard()
        val ended = AtomicBoolean(false)
        fun end(timedOut: Boolean) { if (ended.compareAndSet(false, true)) onEnd(timedOut) }
        val done = CountDownLatch(1)
        thread(name = "ankigo-remind", isDaemon = true) {
            try {
                work(guard)
            } catch (e: Throwable) {
                onError(e)
            } finally {
                done.countDown()
                end(false)
            }
        }
        thread(name = "ankigo-remind-deadline", isDaemon = true) {
            if (!done.await(limitMs, TimeUnit.MILLISECONDS) && guard.expire()) end(true)
        }
        return guard
    }
}
