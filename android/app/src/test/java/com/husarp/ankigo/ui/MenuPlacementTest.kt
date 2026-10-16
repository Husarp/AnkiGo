package com.husarp.ankigo.ui

import com.husarp.ankigo.ui.MenuPlacement.Box
import com.husarp.ankigo.ui.MenuPlacement.Spot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The popup placement maths (PLAN 49): a menu opened near any edge of any window size stays fully inside it. */
class MenuPlacementTest {
    private val d = 3  // px per dp (only to make the numbers look like a phone)
    private val inset = 10 * d
    private val minUseful = 140 * d

    // Window sizes in dp, from Shots.kt: PHONE 412x869, SPLIT 412x400, LAND 869x412, FLOAT 340x480, PIP 240x136
    private val sizes = mapOf("PHONE" to (412 to 869), "SPLIT" to (412 to 400), "LAND" to (869 to 412), "FLOAT" to (340 to 480), "PIP" to (240 to 136))

    private fun inside(s: Spot, b: Box) {
        assertTrue("x $s in $b", s.x >= b.left && s.x + s.width <= b.right)
        assertTrue("y $s in $b", s.y >= b.top && s.y + s.height <= b.bottom)
        assertTrue("size $s", s.width > 0 && s.height > 0)
    }

    private fun place(a: Box, w: Int, h: Int, b: Box) = MenuPlacement.place(a, w, h, b, inset, minUseful)

    @Test fun opensBelowRightAlignedWhenThereIsRoom() {
        val b = Box(0, 0, 412 * d, 869 * d)
        val a = Box(300 * d, 40 * d, 340 * d, 80 * d)
        val s = place(a, 220 * d, 200 * d, b)
        assertEquals(Spot(340 * d + inset - 220 * d, 80 * d - inset / 2, 220 * d, 200 * d), s)
    }

    @Test fun flipsAboveNearTheBottom() {
        val b = Box(0, 0, 412 * d, 869 * d)
        val a = Box(300 * d, 800 * d, 340 * d, 840 * d)
        val s = place(a, 220 * d, 250 * d, b)
        assertEquals(800 * d + inset / 2 - 250 * d, s.y)
        assertEquals(250 * d, s.height)
        inside(s, b)
    }

    @Test fun slidesRightWhenAnchoredAtTheLeftEdge() {
        val b = Box(0, 0, 412 * d, 869 * d)
        val s = place(Box(0, 100 * d, 40 * d, 140 * d), 220 * d, 200 * d, b)
        assertEquals(0, s.x)
        inside(s, b)
    }

    @Test fun slidesLeftAtTheRightEdge() {
        val b = Box(0, 0, 412 * d, 869 * d)
        val s = place(Box(380 * d, 100 * d, 440 * d, 140 * d), 220 * d, 200 * d, b)
        assertEquals(412 * d - 220 * d, s.x)
        inside(s, b)
    }

    @Test fun tallerThanEitherSideScrollsOnTheRoomierSide() {
        val b = Box(0, 0, 412 * d, 400 * d)           // split screen
        val s = place(Box(300 * d, 300 * d, 340 * d, 340 * d), 220 * d, 380 * d, b)   // 6+ rows, anchor low
        assertTrue("shrunk to scroll", s.height < 380 * d)
        assertEquals(300 * d + inset / 2 - s.height, s.y)  // opens upward, as its bottom edge meets the anchor
        inside(s, b)
    }

    @Test fun anchorInTheMiddleOfATinyWindowCoversItAndScrolls() {
        val b = Box(0, 0, 240 * d, 136 * d)           // picture in picture
        val s = place(Box(100 * d, 50 * d, 140 * d, 90 * d), 220 * d, 300 * d, b)
        assertEquals(b.height, s.height)
        inside(s, b)
    }

    @Test fun menuWiderThanTheWindowShrinks() {
        val b = Box(0, 0, 200 * d, 400 * d)
        val s = place(Box(150 * d, 10 * d, 190 * d, 50 * d), 320 * d, 100 * d, b)
        assertEquals(200 * d, s.width)
        assertEquals(0, s.x)
    }

    @Test fun anchorPartlyOffScreenStillGivesAnInsideMenu() {
        val b = Box(0, 0, 412 * d, 869 * d)
        inside(place(Box(100 * d, -300 * d, 400 * d, 20 * d), 220 * d, 250 * d, b), b)       // long row half scrolled out at the top
        inside(place(Box(100 * d, 800 * d, 400 * d, 1500 * d), 220 * d, 250 * d, b), b)      // ... and at the bottom
        inside(place(Box(-50 * d, 100 * d, 20 * d, 140 * d), 220 * d, 250 * d, b), b)        // anchor poking out at the left
    }

    @Test fun windowWithOffsetOriginStaysInside() {
        // split screen bottom half: the frame starts at y = 434 dp (same space as the anchor)
        val b = Box(0, 434 * d, 412 * d, 869 * d)
        for (y in 434 until 869 step 29) for (x in 0 until 412 step 37) {
            inside(place(Box(x * d, y * d, (x + 40) * d, (y + 40) * d), 220 * d, 330 * d, b), b)
        }
    }

    @Test fun everySizeEveryAnchorPositionAndMenuHeightStaysInside() {
        for ((name, wh) in sizes) {
            val b = Box(0, 0, wh.first * d, wh.second * d)
            for (h in listOf(100, 250, 340, 700)) for (mw in listOf(200, 320)) {
                for (ay in -20..wh.second + 20 step 17) for (ax in -20..wh.first + 20 step 23) {
                    val s = place(Box(ax * d, ay * d, (ax + 40) * d, (ay + 40) * d), mw * d, h * d, b)
                    try { inside(s, b) } catch (e: AssertionError) { throw AssertionError("$name menu ${mw}x$h anchor $ax,$ay: ${e.message}") }
                }
            }
        }
    }
}
