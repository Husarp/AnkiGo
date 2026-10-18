package com.husarp.ankigo.gfx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// ROUND 8 R8-FIX M1: in picture mode a Pokémon is drawn at (its frame / 96) device px per sprite pixel - a small species small, a big one big.
class SpriteFrameScaleTest {
    @Test fun pixelArtTakesTheWholeNumberBelowFrameOver96() {
        assertEquals(3f, SpriteView.frameScale(43, 49, 300, 300, 300f), 0f)    // 3.125: 3, not the 6 the holder would allow
        assertEquals(3f, SpriteView.frameScale(96, 96, 300, 300, 300f), 0f)    // a frame-filling one: the same 3
        assertEquals(2f, SpriteView.frameScale(43, 49, 200, 200, 200f), 0f)    // 2.08
        assertEquals(5f, SpriteView.frameScale(70, 60, 500, 500, 500f), 0f)    // 5.2
    }

    @Test fun aHolderAHairUnder96sMultipleStillCountsAsTheMultiple() {
        assertEquals(2f, SpriteView.frameScale(43, 49, 191, 191, 191f), 0f)    // 1.99: the rounding of the holder to pixels
        assertEquals(1f, SpriteView.frameScale(43, 49, 95, 95, 95f), 0f)
        assertEquals(3f, SpriteView.frameScale(43, 49, 287, 287, 287f), 0f)
    }

    @Test fun pixelArtIsAtLeastOneAndFractionalOnlyWhenEvenOneDoesNotFit() {
        assertEquals(1f, SpriteView.frameScale(43, 49, 60, 60, 60f), 0f)       // frame 60 wants .625: 1 (it fits)
        assertEquals(0.5f, SpriteView.frameScale(96, 96, 48, 48, 48f), 0f)     // 1 does not fit 48 px: the fit
        assertEquals(0f, SpriteView.frameScale(96, 96, 0, 48, 48f), 0f)
        assertEquals(0f, SpriteView.frameScale(0, 96, 48, 48, 48f), 0f)
    }

    @Test fun neverMoreThanTheHolderAllows() {
        // a holder narrower than its frame (the frame says 300 / 96 = 3.125, the room is 100 wide): the fit wins
        assertEquals(2f, SpriteView.frameScale(43, 49, 100, 300, 300f), 0f)    // fit 2.33 -> 2
        assertEquals(1f, SpriteView.frameScale(96, 96, 100, 300, 300f), 0f)
    }

    @Test fun aBigThreeDAnimationIsSmoothAndFractionalCappedByTheFit() {
        assertEquals(1.5f, SpriteView.frameScale(200, 180, 300, 300, 300f), 0.0001f)    // 300/96 = 3.125, the holder fits 1.5
        assertEquals(300f / 96f, SpriteView.frameScale(100, 100, 400, 400, 300f), 0.0001f)   // room to spare: exactly frame / 96
        assertEquals(2.5f, SpriteView.frameScale(120, 100, 300, 300, 300f), 0.0001f)
        assertEquals(0.5f, SpriteView.frameScale(200, 162, 100, 300, 100f), 0.0001f)
    }

    @Test fun theSizeFollowsTheSpeciesInTheSameFrame() {
        val small = 43 * SpriteView.frameScale(43, 49, 300, 300, 300f)
        val big = 96 * SpriteView.frameScale(96, 96, 300, 300, 300f)
        assertTrue("$small < $big", small < big)
        assertEquals(129f, small, 0f); assertEquals(288f, big, 0f)
    }

    @Test fun artShareIsWhatIsDrawnOfTheFrameWithoutTheEmptyRowsUnderTheFeet() {
        // 43 x 49 in a 300-px frame: scale 3 = 129 x 147 px; 5 empty rows under the feet are not part of the picture
        val a = SpriteView.artShare(43, 49, 5, 300)
        assertNotNull(a)
        assertEquals(129f / 300f, a!!.w, 0.0001f)
        assertEquals(44f * 3f / 300f, a.h, 0.0001f)
        val full = SpriteView.artShare(96, 96, 0, 300)!!
        assertEquals(288f / 300f, full.w, 0.0001f); assertEquals(288f / 300f, full.h, 0.0001f)
        // a 3D one at 1.5: 300 x 270 of 300 x 300
        val big = SpriteView.artShare(200, 180, 0, 300)!!
        assertEquals(1f, big.w, 0.0001f); assertEquals(0.9f, big.h, 0.0001f)
        // a smaller species always takes a smaller share than a bigger one
        assertTrue(a.w < full.w && a.h < full.h)
    }

    @Test fun artShareOfNothingIsNull() {
        assertNull(SpriteView.artShare(43, 49, 0, 0))
        assertNull(SpriteView.artShare(0, 49, 0, 300))
    }
}
