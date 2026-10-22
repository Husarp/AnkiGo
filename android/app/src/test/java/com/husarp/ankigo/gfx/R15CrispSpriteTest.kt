package com.husarp.ankigo.gfx

import com.husarp.ankigo.ui.ViewerMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PLAN 213 (R15-G): every sprite is drawn crisp; a picture bigger than pixel art takes the whole-number scale when it keeps 85 % of its room. */
class R15CrispSpriteTest {
    private val eps = 0.0001f

    @Test fun neverSmoothed() {
        assertFalse(SpriteView.SMOOTH)
    }

    @Test fun crispScaleTakesTheWholeNumberOnlyWhenItKeeps85Percent() {
        assertEquals(2f, SpriteView.crispScale(2.2f), 0f)      // 2 >= 1.87
        assertEquals(2.5f, SpriteView.crispScale(2.5f), 0f)    // 2 < 2.125
        assertEquals(3f, SpriteView.crispScale(3f), 0f)
        assertEquals(1f, SpriteView.crispScale(1.1f), 0f)
        assertEquals(1.2f, SpriteView.crispScale(1.2f), 0f)    // 1 < 1.02: stays
        assertEquals(0.7f, SpriteView.crispScale(0.7f), 0f)    // below 1: the exact fit
        assertEquals(0f, SpriteView.crispScale(0f), 0f)
    }

    @Test fun aBigPictureInASquareFrameTakesTheWholeNumberOrTheExactRoom() {
        // 120 px picture (not pixel art), frame 220 wants 2.29 and the view fits more: 2 (>= 1.95)
        assertFalse(SpriteView.pixelArt(120, 110))
        assertEquals(2f, SpriteView.frameScale(120, 110, 400, 400, 220f), eps)
        // frame 240 wants 2.5, the view fits 3.33: 2 < 2.125, the exact 2.5 stays (nearest neighbour, fractional)
        assertEquals(2.5f, SpriteView.frameScale(120, 110, 400, 400, 240f), eps)
        // the view limits it (fit 2.2 on a 264 box): 2
        assertEquals(2f, SpriteView.frameScale(120, 110, 264, 264, 300f), eps)
        // room below 1: exact
        assertEquals(0.5f, SpriteView.frameScale(200, 162, 100, 300, 100f), eps)
    }

    @Test fun aBigPictureInTheWantedScaleRuleToo() {
        assertEquals(2f, SpriteView.scale(120, 110, 264, 264, 4), eps)        // fit 2.2 -> 2
        assertEquals(2.5f, SpriteView.scale(120, 110, 300, 300, 4), eps)      // fit 2.5 stays
        assertEquals(2f, SpriteView.scale(120, 110, 1000, 1000, 2), eps)      // capped by wanted: whole already
        assertEquals(3f, SpriteView.scale(120, 110, 1000, 1000, 3), eps)
    }

    @Test fun pixelArtKeepsToday() {
        assertEquals(2f, SpriteView.scale(96, 96, 264, 264, 4), eps)
        assertEquals(2f, SpriteView.scale(96, 96, 220, 200, 4), eps)          // 2.08: 2
        assertEquals(4f, SpriteView.scale(50, 46, 220, 200, 4), eps)
        assertEquals(0.5f, SpriteView.scale(96, 96, 48, 60, 4), eps)
        assertEquals(3f, SpriteView.frameScale(96, 96, 300, 300, 300f), eps)  // 3.125
        assertEquals(5f, SpriteView.frameScale(70, 60, 500, 500, 500f), eps)
        assertEquals(0.5f, SpriteView.frameScale(96, 96, 48, 48, 48f), eps)
    }

    @Test fun sizedAndArtShareFollowTheCrispRule() {
        // k = 1.3: a 390 frame is the 300 holder times 1.3; the holder fits 2.5 (2 < 2.125, so 2.5 stays)
        val base = SpriteView.frameScale(120, 110, 300, 300, 300f)
        assertEquals(2.5f, base, eps)                                          // fit 2.5, 2 < 2.125
        assertEquals(base * 1.3f, SpriteView.sized(1.3f, 120, 110, 390, 390, true, 1), 0.01f)
        // a holder where the whole number wins: 264 box, fit 2.2 -> 2; times k
        assertEquals(2f * 1.25f, SpriteView.sized(1.25f, 120, 110, 330, 330, true, 1), 0.01f)
        // artShare draws a 120 x 110 picture in a 264 frame at 2: 240 x 220 of 264
        val a = SpriteView.artShare(120, 110, intArrayOf(0, 0, 120, 110), false, 264)!!
        assertEquals(240f / 264f, a.w, eps)
        assertEquals(220f / 264f, a.h, eps)
    }

    @Test fun viewerFitScaleUsesTheWholeNumberRuleForEveryPicture() {
        assertEquals(3f, ViewerMath.fitScale(120, 110, 368, 1000), eps)           // 3.07 -> 3
        assertEquals(468f / 120f, ViewerMath.fitScale(120, 110, 468, 1000), eps)  // 3.9: 3 < 3.315, the exact fit
        assertEquals(0.5f, ViewerMath.fitScale(300, 200, 150, 1000), eps)         // below 1: exact
        assertEquals(0f, ViewerMath.fitScale(120, 0, 368, 1000), eps)
    }
}
