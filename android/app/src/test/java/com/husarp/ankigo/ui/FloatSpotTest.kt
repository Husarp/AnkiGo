package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatSpotTest {
    @Test fun roomyArenaKeepsTheSpotAndFullRise() {
        val s = FloatSpot.place(100, 80, 400, 300, 40, 24, 68)
        assertEquals(FloatSpot.Spot(100, 80, 68), s)
    }

    @Test fun noRoomAboveStartsLowerAndEndsInside() {
        val s = FloatSpot.place(100, 5, 400, 300, 40, 24, 68)
        assertEquals(68, s.rise)
        assertEquals(68, s.top)   // start at 68 so start - rise = 0
    }

    @Test fun riseShrinksInAShortArena() {
        val s = FloatSpot.place(10, 0, 300, 50, 40, 24, 68)
        assertEquals(26, s.rise)
        assertEquals(26, s.top)
        assertTrue(s.top - s.rise >= 0 && s.top + 24 <= 50)
    }

    @Test fun arenaShorterThanTheNumberNeverRises() {
        val s = FloatSpot.place(10, 30, 300, 20, 40, 24, 68)
        assertEquals(0, s.rise)
        assertEquals(0, s.top)
    }

    @Test fun leftClampedBothSides() {
        assertEquals(0, FloatSpot.place(-30, 100, 300, 300, 40, 24, 68).left)
        assertEquals(260, FloatSpot.place(290, 100, 300, 300, 40, 24, 68).left)
        assertEquals(0, FloatSpot.place(5, 100, 30, 300, 40, 24, 68).left)
    }

    @Test fun bottomClampedAboveTheEdge() {
        val s = FloatSpot.place(50, 400, 300, 200, 40, 24, 68)
        assertEquals(176, s.top)
    }
}
