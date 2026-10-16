package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateGateTest {
    @Test fun firstUpdateGoesAtOnce() {
        assertEquals(0L, UpdateGate(100).request(now = 5_000, urgent = false))
    }

    @Test fun onlyOneUpdateWaitsAtATime() {
        val g = UpdateGate(100)
        assertEquals(0L, g.request(1_000, false))
        for (i in 1..500) assertEquals(-1L, g.request(1_000L + i, false)) // a flood of progress ticks adds nothing
        assertEquals(-1L, g.request(1_100, true)) // even an urgent one: the waiting update reads the latest state
    }

    @Test fun progressIsSpacedByTheGap() {
        val g = UpdateGate(100)
        assertEquals(0L, g.request(1_000, false))
        g.fired(1_000)
        assertEquals(70L, g.request(1_030, false)) // 30 ms after the last update: wait the other 70
        g.fired(1_100)
        assertEquals(0L, g.request(1_250, false)) // long enough ago: no wait
    }

    @Test fun urgentChangesSkipTheGap() {
        val g = UpdateGate(100)
        g.request(1_000, false); g.fired(1_000)
        assertEquals(0L, g.request(1_010, true))
    }

    @Test fun clockGoingBackwardsNeverWaitsLongerThanTheGap() {
        val g = UpdateGate(100)
        g.request(10_000, false); g.fired(10_000)
        assertEquals(100L, g.request(500, false))
    }
}
