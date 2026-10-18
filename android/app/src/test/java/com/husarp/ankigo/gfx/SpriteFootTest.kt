package com.husarp.ankigo.gfx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R8-04: where a sprite's feet are (lowest visible row over all frames), on plain arrays. */
class SpriteFootTest {
    /** A frame of [w] x [h] pixels, [rows] given as the lowest and highest visible row (alpha 255 on both). */
    private fun frame(w: Int, h: Int, top: Int, bottom: Int, alpha: Int = 255): IntArray {
        val px = IntArray(w * h)
        for (y in top..bottom) for (x in 2 until w - 2) px[y * w + x] = (alpha shl 24) or 0x336699
        return px
    }

    private fun lowest(frames: List<IntArray>, w: Int, h: Int) = SpriteFoot.lowestRow(frames.size, h) { f, y -> SpriteFoot.anyVisible(frames[f], y * w, w) }

    @Test fun anImageWithEmptyRowsUnderTheFeetHasItsLowestRowAbove() {
        // 96 x 96 frame with the body on rows 20..87 (8 empty rows under the feet)
        val f = frame(96, 96, 20, 87)
        assertEquals(87, lowest(listOf(f), 96, 96))
        assertEquals(8, SpriteFoot.rowsBelow(96, 87))
    }

    @Test fun anImageThatFillsItsFrameHasNone() {
        val f = frame(40, 40, 0, 39)
        assertEquals(39, lowest(listOf(f), 40, 40))
        assertEquals(0, SpriteFoot.rowsBelow(40, 39))
    }

    @Test fun animatedFramesWithDifferentLowestRowsUseTheLowestOfAll() {
        // the Pokémon hops: the feet are on row 80 in one frame, 88 in another, 84 in a third
        val frames = listOf(frame(50, 96, 10, 80), frame(50, 96, 10, 88), frame(50, 96, 10, 84))
        assertEquals(88, lowest(frames, 50, 96))
        assertEquals("order does not matter", 88, lowest(frames.reversed(), 50, 96))
    }

    @Test fun almostTransparentPixelsAreNotTheFeet() {
        // a faint shadow at alpha 16 (not above) on row 90, the body ends at 70; alpha 17 counts
        val shadow = frame(30, 96, 90, 90, alpha = SpriteFoot.ALPHA)
        val body = frame(30, 96, 10, 70)
        val both = IntArray(30 * 96) { if (shadow[it] != 0) shadow[it] else body[it] }
        assertEquals(70, lowest(listOf(both), 30, 96))
        val seen = IntArray(30 * 96) { if (shadow[it] != 0) (17 shl 24) or 0x336699 else body[it] }
        assertEquals(90, lowest(listOf(seen), 30, 96))
    }

    @Test fun anEmptyImageHasNoFeetAndNoOffset() {
        val empty = IntArray(20 * 20)
        assertEquals(-1, lowest(listOf(empty), 20, 20))
        assertEquals(0, SpriteFoot.rowsBelow(20, -1))
        assertFalse(SpriteFoot.anyVisible(empty, 0, empty.size))
        assertTrue(SpriteFoot.anyVisible(frame(20, 20, 5, 5), 0, 20 * 20))
    }

    @Test fun eachFrameIsOnlyScannedDownToTheBestRowSoFar() {
        // the scan visits the rows from the bottom up; the second frame stops at the first frame's row
        val seen = ArrayList<Pair<Int, Int>>()
        val r = SpriteFoot.lowestRow(2, 10) { f, y -> seen += f to y; (f == 0 && y <= 6) || (f == 1 && y <= 3) }
        assertEquals(6, r)
        assertEquals(listOf(0 to 9, 0 to 8, 0 to 7, 0 to 6, 1 to 9, 1 to 8, 1 to 7), seen)
    }
}
