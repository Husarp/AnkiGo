package com.husarp.ankigo.debug

import org.junit.Assert.assertEquals
import org.junit.Test

class DelayedFlushTest {
    private class Fake {
        val tasks = ArrayList<() -> Unit>()
        val delays = ArrayList<Long>()
        fun schedule(ms: Long, t: () -> Unit) { delays += ms; tasks += t }
        fun runDue() { val t = tasks.toList(); tasks.clear(); t.forEach { it() } }
    }

    @Test fun manyRequestsInOneWindowRunOnce() {
        val f = Fake(); var runs = 0
        val d = DelayedFlush(2000, f::schedule) { runs++ }
        repeat(10) { d.request() }
        assertEquals(1, f.tasks.size); assertEquals(listOf(2000L), f.delays)
        f.runDue()
        assertEquals(1, runs)
    }

    @Test fun aLaterRequestSchedulesAgain() {
        val f = Fake(); var runs = 0
        val d = DelayedFlush(2000, f::schedule) { runs++ }
        d.request(); f.runDue(); d.request(); f.runDue()
        assertEquals(2, runs)
    }
}
