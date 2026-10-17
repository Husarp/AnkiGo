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

    // Bigger float (27 sp outlined ~ 90x46 px at 2.6x density) at the small sizes: stays inside, rise shrinks.
    @Test fun bigFloatInsideAtPip() {
        val s = FloatSpot.place(150, 90, 240, 136, 90, 46, 88)
        assertTrue(s.left >= 0 && s.left + 90 <= 240)
        assertTrue(s.top - s.rise >= 0 && s.top + 46 <= 136)
    }

    @Test fun bigFloatInsideAtFloatBattle() {
        val s = FloatSpot.place(260, 10, 320, 300, 90, 46, 88)
        assertTrue(s.left >= 0 && s.left + 90 <= 320)
        assertTrue(s.top - s.rise >= 0 && s.top + 46 <= 300)
    }

    private val box = { l: Int, t: Int, r: Int, b: Int -> FloatSpot.Rect(l, t, r, b) }

    @Test fun clearOfNothingIsPlainPlace() {
        assertEquals(FloatSpot.place(100, 80, 400, 300, 40, 24, 34), FloatSpot.placeClear(100, 80, 400, 300, 40, 24, 34, emptyList(), emptyList(), 2))
    }

    @Test fun aSecondTextSitsUnderTheFirstInsteadOfOnIt() {
        val first = FloatSpot.placeClear(100, 80, 400, 300, 40, 24, 34, emptyList(), emptyList(), 2)
        val taken = listOf(box(first.left, first.top, first.left + 40, first.top + 24))
        val second = FloatSpot.placeClear(100, 80, 400, 300, 40, 24, 34, taken, emptyList(), 2)
        assertEquals(first.left, second.left)
        assertEquals(first.top + 26, second.top)
        assertTrue(!box(second.left, second.top, second.left + 40, second.top + 24).hits(taken[0]))
    }

    @Test fun threeTextsNeverOverlap() {
        val taken = ArrayList<FloatSpot.Rect>()
        repeat(4) {
            val s = FloatSpot.placeClear(100, 150, 400, 300, 60, 24, 34, taken, emptyList(), 2)
            val r = box(s.left, s.top, s.left + 60, s.top + 24)
            assertTrue(taken.none { it.hits(r) })
            assertTrue(s.top >= 0 && s.top + 24 <= 300)
            taken += r
        }
    }

    @Test fun aTextSlidesSidewaysWhenACardIsInTheWay() {
        val card = box(80, 40, 180, 120)   // right over the sprite's spot
        val s = FloatSpot.placeClear(100, 80, 400, 300, 40, 24, 34, emptyList(), listOf(card), 2)
        assertTrue(!box(s.left, s.top - s.rise, s.left + 40, s.top + 24).hits(card))
    }

    @Test fun noFreeSpotStillGivesAnInsideSpot() {
        val wall = box(0, 0, 400, 300)
        val s = FloatSpot.placeClear(100, 80, 400, 300, 40, 24, 34, emptyList(), listOf(wall), 2)
        assertTrue(s.left >= 0 && s.left + 40 <= 400 && s.top - s.rise >= 0 && s.top + 24 <= 300)
    }

    @Test fun crowdedPicksTheLeastOverlap() {
        // a 30 high arena holds one text; the second one has to share, but it must not pick a spot worse than the first try
        val taken = listOf(box(100, 3, 140, 27))
        val s = FloatSpot.placeClear(100, 3, 400, 30, 40, 24, 34, taken, emptyList(), 2)
        assertTrue(s.left + 40 <= 100 || s.left >= 140 || s.top + 24 <= 3 || s.top >= 27) // moved off sideways: no overlap at all
    }
}
