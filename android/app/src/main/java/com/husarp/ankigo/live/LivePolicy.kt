package com.husarp.ankigo.live

import com.husarp.ankigo.game.Tuning

/**
 * The scheduling decisions of [LiveDetector], pure so they can be tested without a phone. The numbers are in [Tuning]
 * (LIVE_POLL_MS, LIVE_FULL_EVERY, LIVE_RECHECK_MS). Nothing here reads or counts answers.
 */
object LivePolicy {
    /** A tick never waits less than this, however long the last poll took. */
    const val MIN_DELAY_MS = 100L

    /**
     * Should this tick read all of today's rated cards (instead of the light poll)? Every [Tuning.LIVE_FULL_EVERY]th
     * tick, and whenever [Tuning.LIVE_RECHECK_MS] has passed since the last full read ([lastFullMs], same clock as
     * [nowMs]: elapsedRealtime), whatever the tick count.
     */
    fun isFull(tickNo: Int, nowMs: Long, lastFullMs: Long): Boolean =
        tickNo % Tuning.LIVE_FULL_EVERY == 0 || nowMs - lastFullMs >= Tuning.LIVE_RECHECK_MS

    /** Wait before the next tick, given how long this one took. */
    fun nextDelay(tookMs: Long): Long = (Tuning.LIVE_POLL_MS - tookMs).coerceAtLeast(MIN_DELAY_MS)
}

/**
 * Coalesces "full read now" requests: while one is queued for a loop generation, more requests for the same
 * generation are dropped. A new generation (the detector was stopped/started, its queue was cleared) can always queue.
 */
class RecheckGate {
    private var queuedGen = -1

    /** True = post the request; false = one is already queued for [gen]. */
    @Synchronized fun tryQueue(gen: Int): Boolean {
        if (queuedGen == gen) return false
        queuedGen = gen
        return true
    }

    /** The queued request started running: the next request may queue again. */
    @Synchronized fun done() { queuedGen = -1 }
}

/**
 * Who wants the detector's regular polls paused (the in-app study screen while it is in front, see LiveSession.hush).
 * An identity set: the same owner twice is one. [hold] / [release] say whether the paused state changed.
 */
class HushGate {
    private val owners: MutableSet<Any> = java.util.Collections.newSetFromMap(java.util.IdentityHashMap())

    val active: Boolean get() = owners.isNotEmpty()

    /** True when this started the pause (nobody held it before). */
    fun hold(owner: Any): Boolean { val was = active; owners += owner; return active != was }

    /** True when this ended the pause (the last owner let go). */
    fun release(owner: Any): Boolean { val was = active; owners -= owner; return active != was }
}
