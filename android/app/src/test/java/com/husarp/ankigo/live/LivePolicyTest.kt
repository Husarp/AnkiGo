package com.husarp.ankigo.live

import com.husarp.ankigo.game.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePolicyTest {
    private val recheck = Tuning.LIVE_RECHECK_MS

    @Test fun fullReadEveryTenthTickWhenRecent() {
        val full = (1..30).filter { LivePolicy.isFull(it, 1_000L * it, 1_000L * it - 500) }
        assertEquals(listOf(Tuning.LIVE_FULL_EVERY, 2 * Tuning.LIVE_FULL_EVERY, 3 * Tuning.LIVE_FULL_EVERY), full)
    }

    @Test fun fullReadWhen30sPassedEvenAtTickThree() {
        assertTrue(LivePolicy.isFull(3, recheck, 0))
        assertTrue(LivePolicy.isFull(3, recheck + 5_000, 0))
        assertFalse(LivePolicy.isFull(3, recheck - 1, 0))
    }

    @Test fun lastFullReadResetsTheClock() {
        assertFalse(LivePolicy.isFull(7, 100_000, 100_000 - recheck + 1))
        assertTrue(LivePolicy.isFull(7, 100_000, 100_000 - recheck))
    }

    @Test fun slowPollsStillTriggerTheTimeRule() {
        // Ticks that each take ~9 s never reach tick 10 within 30 s, the time rule still fires.
        var last = 0L; var fulls = 0
        for (tick in 1..9) { val now = tick * 9_000L; if (LivePolicy.isFull(tick, now, last)) { fulls++; last = now } }
        assertTrue(fulls >= 2)
    }

    @Test fun delayNeverBelow100ms() {
        assertEquals(Tuning.LIVE_POLL_MS, LivePolicy.nextDelay(0))
        assertEquals(Tuning.LIVE_POLL_MS - 300, LivePolicy.nextDelay(300))
        assertEquals(100L, LivePolicy.nextDelay(Tuning.LIVE_POLL_MS))
        assertEquals(100L, LivePolicy.nextDelay(5_000))
        assertEquals(100L, LivePolicy.nextDelay(Long.MAX_VALUE / 2))
        assertTrue(LivePolicy.nextDelay(950) >= LivePolicy.MIN_DELAY_MS)
    }

    @Test fun tuningValues() {
        assertEquals(1_000L, Tuning.LIVE_POLL_MS); assertEquals(10, Tuning.LIVE_FULL_EVERY); assertEquals(30_000L, Tuning.LIVE_RECHECK_MS)
        assertEquals(5_000L, Tuning.LIVE_RETRY_MS); assertEquals(30_000L, Tuning.LIVE_RETRY_SLOW_MS)
    }

    @Test fun gateCoalescesRequestsOfOneGeneration() {
        val g = RecheckGate()
        assertTrue(g.tryQueue(1))
        assertFalse(g.tryQueue(1))
        assertFalse(g.tryQueue(1))
        g.done()
        assertTrue(g.tryQueue(1)) // after it ran, a new one may queue
        assertFalse(g.tryQueue(1))
    }

    @Test fun gateNewGenerationCanQueueEvenIfOldRequestWasDropped() {
        val g = RecheckGate()
        assertTrue(g.tryQueue(1)) // queued, then stop()/start() cleared the worker queue: done() never ran
        assertTrue(g.tryQueue(2))
        assertFalse(g.tryQueue(2))
    }

    // ---- the study screen pauses the polls while it is in front (LiveSession.hush)
    @Test fun hushStartsWithTheFirstOwnerAndEndsWithTheLast() {
        val h = HushGate(); val a = Any(); val b = Any()
        assertFalse(h.active)
        assertTrue("first owner pauses", h.hold(a)); assertTrue(h.active)
        assertFalse("a second owner changes nothing", h.hold(b))
        assertFalse("one owner left: still paused", h.release(a)); assertTrue(h.active)
        assertTrue("the last owner resumes", h.release(b)); assertFalse(h.active)
    }

    @Test fun hushCountsTheSameOwnerOnceAndIgnoresStrangers() {
        val h = HushGate(); val screen = Any()
        assertTrue(h.hold(screen)); assertFalse(h.hold(screen)) // onResume twice
        assertFalse("never held", h.release(Any())); assertTrue(h.active)
        assertTrue(h.release(screen)); assertFalse(h.active)
        assertFalse("released twice", h.release(screen))
    }

    @Test fun hushIsByIdentityNotEquality() {
        val h = HushGate()
        val a = "screen"; val b = String("screen".toCharArray()) // equal, but two screens
        h.hold(a); h.hold(b)
        assertFalse(h.release(a)); assertTrue(h.active)
        assertTrue(h.release(b))
    }

    @Test fun theFirstPollAfterALongPauseIsAFullRead() {
        // polls paused (hush) for a minute after a full read: the tick that follows reads everything, whatever its number
        assertTrue(LivePolicy.isFull(tickNo = 3, nowMs = 61_000, lastFullMs = 1_000))
        assertFalse("a short pause: the light poll as usual", LivePolicy.isFull(tickNo = 3, nowMs = 6_000, lastFullMs = 1_000))
    }
}
