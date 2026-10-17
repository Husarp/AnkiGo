package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GridMathTest {
    @Test fun columnsByWidth() {
        assertEquals(3, GridMath.columns(0))
        assertEquals(3, GridMath.columns(320))
        assertEquals(3, GridMath.columns(339))
        assertEquals(4, GridMath.columns(340))
        assertEquals(4, GridMath.columns(411))     // a normal phone
        assertEquals(4, GridMath.columns(599))
        assertEquals(5, GridMath.columns(600))
        assertEquals(5, GridMath.columns(839))
        assertEquals(6, GridMath.columns(840))
        assertEquals(6, GridMath.columns(1280))
    }

    @Test fun spriteScaleIsTheLargestWholeNumberThatFits() {
        assertEquals(2, GridMath.spriteScale(cellPx = 200, nativePx = 96))
        assertEquals(3, GridMath.spriteScale(cellPx = 288, nativePx = 96))
        assertEquals(3, GridMath.spriteScale(cellPx = 383, nativePx = 96))
        assertEquals(4, GridMath.spriteScale(cellPx = 384, nativePx = 96))
    }

    @Test fun spriteScaleNeverGoesBelowOne() {
        assertEquals(1, GridMath.spriteScale(cellPx = 50, nativePx = 96))
        assertEquals(1, GridMath.spriteScale(cellPx = 0, nativePx = 96))
        assertEquals(1, GridMath.spriteScale(cellPx = 100, nativePx = 0))
    }
}
