package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BarGhostTest {
    /** The first drop: the fill is still at the old value (1.0) while it heads for 0.6; the ghost must start at the old value. */
    @Test fun firstDropStartsAtTheOldValue() {
        assertEquals(1f, BarView.ghostTop(1f, 0.6f), 0f)
        assertEquals(0.9f, BarView.ghostTop(0.9f, 0.5f), 0f)
    }

    @Test fun noDropNoGhost() {
        assertEquals(0f, BarView.ghostTop(0.5f, 0.5f), 0f)
        assertEquals(0f, BarView.ghostTop(0.4f, 0.5f), 0f)
    }

    @Test fun outOfRangeIsClamped() {
        assertEquals(1f, BarView.ghostTop(1.7f, 0.2f), 0f)
        assertEquals(0f, BarView.ghostTop(-1f, 0f), 0f)
    }
}
