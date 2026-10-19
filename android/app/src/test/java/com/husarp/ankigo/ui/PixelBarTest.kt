package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 10 W8: the pixel look's maths (unit, 5 % steps, cut corners). */
class PixelBarTest {
    @Test fun theUnitIsAHalfDpGridOfTheHeightOverSeven() {
        // density 1: dp = px. 7 dp -> 1, 14 dp -> 2, 3 dp -> 0.5 dp = 0.5 px -> min 1 px
        assertEquals(1, PixelBar.unitPx(7, 1f))
        assertEquals(2, PixelBar.unitPx(14, 1f))
        assertEquals(1, PixelBar.unitPx(3, 1f))
        assertEquals(1, PixelBar.unitPx(1, 1f))
        assertEquals(3, PixelBar.unitPx(21, 1f))
        // density 2: 7 dp (14 px) -> 1 dp = 2 px; 3 dp (6 px) -> 0.5 dp = 1 px; 14 dp (28 px) -> 4 px; 10 dp (20 px) -> round(2.86)/2 = 1.5 dp = 3 px
        assertEquals(2, PixelBar.unitPx(14, 2f))
        assertEquals(1, PixelBar.unitPx(6, 2f))
        assertEquals(4, PixelBar.unitPx(28, 2f))
        assertEquals(3, PixelBar.unitPx(20, 2f))
    }

    @Test fun theUnitIsAtLeastOnePixelAtEveryHeightAndDensity() {
        for (d in listOf(1f, 1.5f, 2f, 2.625f, 3f, 3.5f)) for (h in 1..200) assertTrue("h=$h d=$d", PixelBar.unitPx(h, d) >= 1)
    }

    @Test fun theOuterHeightIsTheInnerOnePlusAnOutlineUnitOnEachSide() {
        assertEquals(9, PixelBar.outerPx(7, 1f))
        assertEquals(18, PixelBar.outerPx(14, 2f))
        assertEquals(8, PixelBar.outerPx(6, 2f))
    }

    @Test fun theOuterHeightGivesItsUnitBack() {
        for (d in listOf(1f, 1.5f, 2f, 2.625f, 3f, 3.5f)) for (inner in 2..120) {
            val u = PixelBar.unitPx(inner, d)
            assertEquals("inner=$inner d=$d", u, PixelBar.unitOfOuter(PixelBar.outerPx(inner, d), d))
        }
        assertEquals(0, PixelBar.unitOfOuter(2, 2f))
        assertEquals(0, PixelBar.unitOfOuter(0, 2f))
        assertEquals(1, PixelBar.unitOfOuter(3, 2f))
    }

    @Test fun theFillMovesInFivePercentSteps() {
        assertEquals(0f, PixelBar.step(0f), 0f)
        assertEquals(1f, PixelBar.step(1f), 0f)
        assertEquals(0.85f, PixelBar.step(0.86f), 1e-6f)
        assertEquals(0.40f, PixelBar.step(0.42f), 1e-6f)
        assertEquals(0.15f, PixelBar.step(0.14f), 1e-6f)
        assertEquals(0.65f, PixelBar.step(0.64f), 1e-6f)
        assertEquals(1f, PixelBar.step(0.98f), 1e-6f)
        assertEquals(0f, PixelBar.step(-1f), 0f)
        assertEquals(1f, PixelBar.step(7f), 0f)
        for (i in 0..1000) {
            val s = PixelBar.step(i / 1000f)
            assertEquals("i=$i", Math.round(s * 20) / 20f, s, 1e-6f)
        }
    }

    @Test fun aBarThatIsNotEmptyNeverLooksEmpty() {
        assertEquals(0.05f, PixelBar.step(0.001f), 1e-6f)
        assertEquals(0.05f, PixelBar.step(0.02f), 1e-6f)
        assertEquals(0f, PixelBar.step(0f), 0f)
        assertTrue(PixelBar.fillPx(100, 0.003f) > 0)
    }

    @Test fun theFillIsWholePixelsAndNeverWiderThanTheTrack() {
        assertEquals(0, PixelBar.fillPx(100, 0f))
        assertEquals(100, PixelBar.fillPx(100, 1f))
        assertEquals(85, PixelBar.fillPx(100, 0.86f))
        assertEquals(60, PixelBar.fillPx(120, 0.5f))
        for (w in listOf(0, 1, 7, 100, 333)) for (i in 0..100) assertTrue(PixelBar.fillPx(w, i / 100f) in 0..w)
    }

    @Test fun theHighlightIsAtLeastOnePixelAndAboutOnePointTwoUnits() {
        assertEquals(1, PixelBar.highlightPx(1))
        assertEquals(2, PixelBar.highlightPx(2))
        assertEquals(4, PixelBar.highlightPx(3))
        assertEquals(5, PixelBar.highlightPx(4))
    }

    @Test fun theOutlineLeavesTheFourCornerSquaresOut() {
        val r = IntArray(16)
        val w = 40; val h = 12; val u = 2
        PixelBar.frame(w, h, u, r)
        fun inFrame(x: Int, y: Int): Boolean {
            for (i in 0 until 16 step 4) if (x >= r[i] && x < r[i + 2] && y >= r[i + 1] && y < r[i + 3]) return true
            return false
        }
        for (cx in listOf(0, w - u)) for (cy in listOf(0, h - u)) for (x in cx until cx + u) for (y in cy until cy + u) assertFalse("corner ($x,$y)", inFrame(x, y))
        // everything else in the outer ring is outline; the inside is not
        for (x in 0 until w) for (y in 0 until h) {
            val corner = (x < u || x >= w - u) && (y < u || y >= h - u)
            val ring = x < u || x >= w - u || y < u || y >= h - u
            assertEquals("($x,$y)", ring && !corner, inFrame(x, y))
        }
    }
}
