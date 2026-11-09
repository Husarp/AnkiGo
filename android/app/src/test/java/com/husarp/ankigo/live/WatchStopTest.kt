package com.husarp.ankigo.live

import org.junit.Assert.*
import org.junit.Test

class WatchStopTest {
    private class Clock {
        var now = 0L
        val tasks = LinkedHashMap<Runnable, Long>()
        fun advance(ms: Long) {
            now += ms
            val due = tasks.filterValues { it <= now }.keys.toList()
            due.forEach { tasks.remove(it); it.run() }
        }
    }

    @Test fun halfSecondTabHandoffsReuseTheRunningProviderBaseline() {
        val clock = Clock()
        var baselines = 1; var stops = 0; var running = true
        val stop = WatchStop({ r, ms -> clock.tasks[r] = clock.now + ms }, { clock.tasks.remove(it) }) { stops++; running = false }
        repeat(20) {
            stop.schedule() // last battle holder left
            clock.advance(500)
            stop.cancel() // next battle holder arrived
            if (!running) { running = true; baselines++ }
        }
        assertEquals(1, baselines); assertEquals(0, stops); assertTrue(clock.tasks.isEmpty())
        stop.schedule(); clock.advance(1000)
        assertFalse(running); assertEquals(1, stops)
        stop.cancel()
        if (!running) baselines++
        assertEquals("a later visit still starts a fresh catch-up baseline", 2, baselines)
    }

    @Test fun duplicateReleasesDoNotExtendTheStopAndCancelledWorkCannotStopANewHolder() {
        val clock = Clock(); var stops = 0
        val stop = WatchStop({ r, ms -> clock.tasks[r] = clock.now + ms }, { clock.tasks.remove(it) }) { stops++ }
        stop.schedule(); val stale = clock.tasks.keys.single()
        clock.advance(500); stop.schedule(); clock.advance(500)
        assertEquals(1, stops)
        stop.schedule(); stop.cancel(); stale.run()
        assertEquals(1, stops)
    }
}
