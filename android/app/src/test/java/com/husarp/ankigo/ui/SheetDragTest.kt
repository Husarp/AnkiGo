package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R6-07 item 18: when a dragged bottom sheet closes and when it springs back. */
class SheetDragTest {
    private val h = 600f
    private val d = 2f // density

    @Test fun theSheetNeverGoesAboveItsRestingPlace() {
        assertEquals(0f, SheetDrag.offset(-40f), 0f)
        assertEquals(25f, SheetDrag.offset(25f), 0f)
    }

    @Test fun releasedPastThirtyPercentCloses() {
        assertTrue(SheetDrag.shouldClose(h * 0.30f, h, 0f, d))
        assertTrue(SheetDrag.shouldClose(h * 0.8f, h, 0f, d))
    }

    @Test fun releasedBeforeThirtyPercentSpringsBack() {
        assertFalse(SheetDrag.shouldClose(h * 0.29f, h, 0f, d))
        assertFalse(SheetDrag.shouldClose(h * 0.05f, h, 100f, d))
    }

    @Test fun aFlingDownClosesAtAnyDistance() {
        val fast = SheetDrag.FLING_DP_PER_S * d
        assertTrue(SheetDrag.shouldClose(h * 0.05f, h, fast, d))
        assertFalse(SheetDrag.shouldClose(h * 0.05f, h, fast - 1f, d))
    }

    @Test fun aFlingUpSpringsBackEvenFarDown() {
        assertFalse(SheetDrag.shouldClose(h * 0.6f, h, -SheetDrag.FLING_DP_PER_S * d, d))
        assertTrue(SheetDrag.shouldClose(h * 0.6f, h, -SheetDrag.FLING_DP_PER_S * d + 1f, d)) // a slow pull back up does not cancel
    }

    @Test fun nothingMovedOrNoHeightNeverCloses() {
        assertFalse(SheetDrag.shouldClose(0f, h, 5000f, d))
        assertFalse(SheetDrag.shouldClose(50f, 0f, 5000f, d))
    }

    @Test fun scrimFadesWithTheSheet() {
        assertEquals(1f, SheetDrag.scrimAlpha(0f, h), 0f)
        assertEquals(0.5f, SheetDrag.scrimAlpha(h / 2, h), 0.0001f)
        assertEquals(0f, SheetDrag.scrimAlpha(h * 2, h), 0f)
        assertEquals(1f, SheetDrag.scrimAlpha(10f, 0f), 0f)
    }

    @Test fun slideIsShorterWhenLittleIsLeft() {
        assertEquals(220L, SheetDrag.slideMs(0f, h))
        assertEquals(120L, SheetDrag.slideMs(h, h))
        assertTrue(SheetDrag.slideMs(h * 0.5f, h) in 121L..219L)
    }
}
