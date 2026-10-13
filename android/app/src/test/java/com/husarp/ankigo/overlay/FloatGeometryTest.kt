package com.husarp.ankigo.overlay

import com.husarp.ankigo.overlay.FloatGeometry.Box
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatGeometryTest {
    private val sw = 1080
    private val sh = 2200
    private val minW = 300
    private val minH = 200

    private fun clamp(b: Box, w: Int = sw, h: Int = sh) = FloatGeometry.clamp(b, w, h, minW, minH)

    @Test fun insideStaysUnchanged() {
        val b = Box(100, 200, 500, 600)
        assertEquals(b, clamp(b))
    }

    @Test fun offScreenIsPulledBack() {
        assertEquals(Box(0, 0, 500, 600), clamp(Box(-400, -50, 500, 600)))
        assertEquals(Box(580, 1600, 500, 600), clamp(Box(900, 2100, 500, 600)))
    }

    @Test fun tooBigShrinksToScreen() {
        assertEquals(Box(0, 0, sw, sh), clamp(Box(50, 50, 5000, 9000)))
    }

    @Test fun tooSmallGrowsToMinimum() {
        assertEquals(Box(10, 20, minW, minH), clamp(Box(10, 20, 10, 10)))
    }

    @Test fun rotationToLandscapeKeepsItOnScreen() {
        // saved in portrait (1080x2200), now landscape (2200x1080): too tall and below the new bottom edge
        val b = clamp(Box(300, 1500, 700, 900), 2200, 1080)
        assertEquals(Box(300, 180, 700, 900), b)
    }

    @Test fun screenSmallerThanMinimumWins() {
        // split screen smaller than min size: the window can't be bigger than the display
        assertEquals(Box(0, 0, 250, 180), FloatGeometry.clamp(Box(40, 40, 300, 200), 250, 180, minW, minH))
    }

    @Test fun defaultIsCentredInLowerThird() {
        val b = FloatGeometry.default(sw, sh, 600, 800, minW, minH)
        assertEquals(240, b.x)
        assertEquals(sh * 2 / 3 - 400, b.y)
        assertEquals(600, b.w)
        assertEquals(800, b.h)
    }

    @Test fun defaultTallWindowIsClamped() {
        val b = FloatGeometry.default(sw, sh, 600, 2000, minW, minH)
        assertEquals(200, b.y)
    }

    @Test fun moveClampsAtEdges() {
        val s = Box(100, 100, 500, 600)
        assertEquals(Box(150, 130, 500, 600), FloatGeometry.move(s, 50, 30, sw, sh, minW, minH))
        assertEquals(Box(0, 0, 500, 600), FloatGeometry.move(s, -999, -999, sw, sh, minW, minH))
        assertEquals(Box(580, 1600, 500, 600), FloatGeometry.move(s, 9999, 9999, sw, sh, minW, minH))
    }

    @Test fun resizeGrowsShrinksAndStopsAtMinAndScreen() {
        val s = Box(100, 100, 500, 600)
        assertEquals(Box(100, 100, 600, 650), FloatGeometry.resize(s, 100, 50, sw, sh, minW, minH))
        assertEquals(Box(100, 100, minW, minH), FloatGeometry.resize(s, -999, -999, sw, sh, minW, minH))
        assertEquals(Box(100, 100, sw - 100, sh - 100), FloatGeometry.resize(s, 9999, 9999, sw, sh, minW, minH))
    }

    @Test fun resizeNearEdgeWithMinimumShiftsOntoScreen() {
        // only 100 px of room right of x=980, but min width is 300: the window moves left rather than leave the screen
        val b = FloatGeometry.resize(Box(980, 100, 100, 300), 0, 0, sw, sh, minW, minH)
        assertEquals(Box(780, 100, 300, 300), b)
    }
}
