package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XpCardSpotTest {
    private fun box(l: Int, t: Int, r: Int, b: Int) = FloatSpot.Rect(l, t, r, b)

    @Test fun emptyColumnStacksUpToMaxCentred() {
        val tops = XpCardSpot.slots(300, 8, 170, 40, 6, 4, emptyList())
        assertEquals(3, tops.size)
        assertEquals(listOf(tops[0], tops[0] + 44, tops[0] + 88), tops)
        assertEquals(300 - 6 - (tops[0] + 128), tops[0] - 6) // centred
    }

    @Test fun usesTheGapBetweenTheWildCardAndOurSprite() {
        val wildCard = box(10, 10, 176, 80)
        val ourSprite = box(22, 140, 160, 260)
        val tops = XpCardSpot.slots(280, 8, 170, 40, 6, 4, listOf(wildCard, ourSprite))
        assertEquals(1, tops.size)
        assertTrue(tops[0] >= 80 && tops[0] + 40 <= 140)
    }

    @Test fun twoCardsFitAGapOf84() {
        val tops = XpCardSpot.slots(300, 8, 170, 40, 0, 4, listOf(box(0, 0, 200, 100), box(0, 184, 200, 300)))
        assertEquals(listOf(100, 144), tops)
    }

    @Test fun noGapMeansNoRoom() {
        assertEquals(emptyList<Int>(), XpCardSpot.slots(120, 8, 170, 40, 6, 4, listOf(box(0, 0, 200, 60), box(0, 70, 200, 120))))
    }

    @Test fun obstaclesOutsideTheColumnAreIgnored() {
        val tops = XpCardSpot.slots(120, 8, 100, 40, 6, 4, listOf(box(200, 0, 300, 120)))
        assertTrue(tops.isNotEmpty())
    }

    @Test fun biggestGapWins() {
        val tops = XpCardSpot.slots(300, 8, 100, 40, 0, 4, listOf(box(0, 50, 200, 70), box(0, 80, 200, 90)))
        // gaps: 0..50 (1 card), 70..80 (none), 90..300 (3 cards)
        assertEquals(3, tops.size)
        assertTrue(tops.all { it >= 90 })
    }

    // ---- anywhere: small windows without a free strip at the left edge

    @Test fun anywherePrefersTheTopEdge() {
        val spots = XpCardSpot.anywhere(400, 140, 160, 30, 4, 4, listOf(box(0, 60, 150, 140)), max = 1)
        assertEquals(listOf(XpCardSpot.Pos(4, 4)), spots)
    }

    @Test fun anywhereGoesBesideACardWhenTheEdgesAreBlocked() {
        val obstacles = listOf(box(0, 0, 150, 40), box(0, 100, 150, 140), box(250, 0, 400, 140))
        val spots = XpCardSpot.anywhere(400, 140, 90, 30, 4, 4, obstacles, max = 1)
        assertEquals(1, spots.size)
        val r = FloatSpot.Rect(spots[0].x, spots[0].y, spots[0].x + 90, spots[0].y + 30)
        assertTrue(obstacles.none { it.hits(r) })
    }

    @Test fun anywhereSpotsNeverOverlapEachOtherOrObstacles() {
        val obstacles = listOf(box(0, 50, 120, 140), box(280, 50, 400, 140))
        val spots = XpCardSpot.anywhere(400, 140, 160, 30, 4, 4, obstacles, max = 2)
        assertEquals(2, spots.size)
        val rects = spots.map { FloatSpot.Rect(it.x, it.y, it.x + 160, it.y + 30) }
        assertTrue(!rects[0].hits(rects[1]))
        assertTrue(rects.all { r -> obstacles.none { it.hits(r) } })
    }

    @Test fun anywhereNoRoomIsEmpty() {
        assertEquals(emptyList<XpCardSpot.Pos>(), XpCardSpot.anywhere(200, 100, 160, 30, 4, 4, listOf(box(0, 0, 200, 100))))
        assertEquals(emptyList<XpCardSpot.Pos>(), XpCardSpot.anywhere(100, 100, 160, 30, 4, 4, emptyList()))
    }
}
