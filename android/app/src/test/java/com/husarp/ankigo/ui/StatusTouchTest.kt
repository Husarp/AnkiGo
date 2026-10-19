package com.husarp.ankigo.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The touch area of a status badge ([StatusTouch.area]): at least the minimum both ways, centred, kept inside the arena. */
class StatusTouchTest {
    private val min = 120   // 48 dp at 2.5 px per dp

    @Test fun aSmallBadgeGetsTheMinimumCentredOnIt() {
        val a = StatusTouch.area(300, 100, 340, 140, min, 1000, 600)   // a 40 px badge centred at (320, 120)
        assertArrayEquals(intArrayOf(260, 60, 380, 180), a)
    }

    @Test fun aBigBadgeKeepsItsOwnSize() {
        val a = StatusTouch.area(300, 100, 460, 260, min, 1000, 600)
        assertArrayEquals(intArrayOf(300, 100, 460, 260), a)
    }

    @Test fun nearAnEdgeTheAreaMovesInsideKeepingItsSize() {
        val a = StatusTouch.area(10, 5, 50, 45, min, 1000, 600)
        assertArrayEquals(intArrayOf(0, 0, 120, 120), a)
        val b = StatusTouch.area(960, 560, 1000, 600, min, 1000, 600)
        assertArrayEquals(intArrayOf(880, 480, 1000, 600), b)
    }

    @Test fun anArenaSmallerThanTheMinimumCutsTheArea() {
        val a = StatusTouch.area(10, 10, 40, 40, min, 100, 90)
        assertArrayEquals(intArrayOf(0, 0, 100, 90), a)
    }

    @Test fun theAreaAlwaysHoldsTheBadgeWhenTheArenaHoldsIt() {
        for (l in listOf(0, 7, 400, 960)) for (t in listOf(0, 3, 300, 560)) {
            val a = StatusTouch.area(l, t, l + 40, t + 40, min, 1000, 600)
            assertTrue("$l,$t -> ${a.toList()}", a[0] <= l && a[1] <= t && a[2] >= l + 40 && a[3] >= t + 40)
            assertEquals(min, a[2] - a[0]); assertEquals(min, a[3] - a[1])
        }
    }
}
