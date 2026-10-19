package com.husarp.ankigo.gfx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** ROUND 10 B20: the Pokémon size in the sprite's draw scale ([SpriteView.sized]): k times what a holder 1 / k as big gets, never past the view. */
class SpriteSizedScaleTest {
    @Test fun aSizeIsKTimesTheScaleOfTheHolderAt100() {
        // plain layouts: a 96 px picture in a 220 x 200 holder draws at 2x; the holder grown by k draws at 2k
        for (k in listOf(0.8f, 0.9f, 1.05f, 1.1f, 1.3f)) {
            val vw = Math.round(220 * k); val vh = Math.round(200 * k)
            assertEquals("k=$k", 2f * k, SpriteView.sized(k, 96, 96, vw, vh, false, 4), 0.02f)
        }
        // a smaller picture is capped by the wanted scale first (4x): the same, times k
        assertEquals(4f * 1.3f, SpriteView.sized(1.3f, 50, 46, 286, 260, false, 4), 0.02f)
        // picture mode: a 300 px frame wants 3.125 -> 3x; at 130 % the frame is 390 px and the picture draws 3.9x; at 80 % 2.4x
        assertEquals(3.9f, SpriteView.sized(1.3f, 96, 96, 390, 390, true, 1), 0.001f)
        assertEquals(2.4f, SpriteView.sized(0.8f, 96, 96, 240, 240, true, 1), 0.001f)
        assertEquals(3.3f, SpriteView.sized(1.1f, 96, 96, 330, 330, true, 1), 0.001f)
    }

    @Test fun theSteps5PercentApartAreRealDifferencesNotWholePixelJumps() {
        // today a 300 px frame draws 3x for every size from 100 up to a frame of 383 px; the size multiplies that, smoothly
        var last = 0f
        for (pct in listOf(80, 85, 90, 95, 100, 105, 110, 115, 120, 125, 130)) {
            val k = pct / 100f
            val s = SpriteView.sized(k, 96, 96, Math.round(300 * k), Math.round(300 * k), true, 1)
            assertTrue("$pct %: $s must be bigger than $last", s > last)
            assertEquals("$pct %", 3f * k, s, 0.02f)
            last = s
        }
    }

    @Test fun neverPastWhatTheViewAllows() {
        // a picture taller than wide: the holder is the limit
        val s = SpriteView.sized(1.3f, 40, 120, 100, 150, false, 8)
        assertTrue("$s", s <= Math.min(100f / 40, 150f / 120) + 0.0001f)
        assertEquals(0f, SpriteView.sized(1.3f, 96, 96, 0, 100, false, 4), 0f)
        assertEquals(0f, SpriteView.sized(0f, 96, 96, 100, 100, false, 4), 0f)
    }

    @Test fun theArtBoxOfASizedFrameIsTheSameShareOfItsFrame() {
        // uniform scaling about the feet: the art box is the same share of a frame k times as big
        val bounds = intArrayOf(10, 20, 40, 70)
        for (k in listOf(0.8f, 1.1f, 1.3f)) {
            val side = Math.round(300 * k)
            val a = SpriteView.artShare(96, 96, bounds, false, side, k)!!
            val b = SpriteView.artShare(96, 96, bounds, false, 300, 1f)!!
            assertEquals("k=$k x", b.x, a.x, 0.01f); assertEquals("k=$k y", b.y, a.y, 0.01f)
            assertEquals("k=$k w", b.w, a.w, 0.01f); assertEquals("k=$k h", b.h, a.h, 0.01f)
        }
    }

    @Test fun aFactorOfOneIsTheScaleBefore() {
        val a = SpriteView.artShare(96, 96, intArrayOf(10, 20, 40, 70), false, 300)!!
        val b = SpriteView.artShare(96, 96, intArrayOf(10, 20, 40, 70), false, 300, 1f)!!
        assertEquals(a.x, b.x, 0f); assertEquals(a.w, b.w, 0f); assertEquals(a.h, b.h, 0f)
    }
}
