package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R8-03: where a battle background's pixels land in the panel (crop, fade sides, the blurred copy). */
class BackdropDrawTest {
    private fun lay(picW: Int, picH: Int, crop: BackdropDraw.Crop, w: Float, h: Float) = BackdropDraw.layout(picW, picH, crop, 96, 64, w, h)

    @Test fun aCoverCropHasThePanelsShapeAndSitsAtTheBottomCentred() {
        val c = BackdropDraw.coverCrop(1264, 848, 400f, 400f) // squarish panel: full height, a middle band of the width
        assertEquals(848f, c.h, 0.01f); assertEquals(848f, c.w, 0.01f)
        assertEquals((1264 - 848) / 2f, c.x, 0.01f); assertEquals(0f, c.y, 0.01f)
        val wide = BackdropDraw.coverCrop(1264, 848, 1000f, 300f) // wide panel: full width, a band at the bottom
        assertEquals(1264f, wide.w, 0.01f)
        assertEquals(1264f * 300 / 1000, wide.h, 0.01f)
        assertEquals(0f, wide.x, 0.01f)
        assertEquals(848f - wide.h, wide.y, 0.01f)
    }

    @Test fun aCoverCropNeverLeavesThePicture() {
        for ((pw, ph) in listOf(316 to 212, 753 to 500, 1264 to 848, 753 to 325)) for ((w, h) in listOf(400f to 800f, 800f to 400f, 300f to 300f, 1000f to 120f, 90f to 600f)) {
            val c = BackdropDraw.coverCrop(pw, ph, w, h)
            assertTrue(c.x >= -0.01f && c.y >= -0.01f && c.x + c.w <= pw + 0.01f && c.y + c.h <= ph + 0.01f)
            assertEquals(w / h, c.w / c.h, 0.002f)
            assertTrue("one side fills the picture", c.w >= pw - 0.01f || c.h >= ph - 0.01f)
        }
    }

    @Test fun aCropInsideThePictureFillsThePanelWithoutFades() {
        val c = BackdropDraw.coverCrop(1264, 848, 300f, 500f)
        val l = lay(1264, 848, c, 300f, 500f)
        assertTrue(l.picL <= 0.01f && l.picT <= 0.01f && l.picR >= 299.99f && l.picB >= 499.99f)
        assertFalse(l.anyFade)
        assertEquals(l.kx, l.ky, 0.001f)
    }

    @Test fun theCropWindowMapsToThePanel() {
        val c = BackdropDraw.Crop(100f, 50f, 400f, 200f)
        val l = lay(1000, 500, c, 800f, 400f) // factor 2
        assertEquals(2f, l.kx, 1e-4f); assertEquals(2f, l.ky, 1e-4f)
        assertEquals(-200f, l.picL, 1e-3f); assertEquals(-100f, l.picT, 1e-3f)
        assertEquals(1800f, l.picR, 1e-3f); assertEquals(900f, l.picB, 1e-3f)
    }

    @Test fun aWiderWindowThanThePictureShrinksItAndFadesLeftAndRight() {
        // a window 1.5 x as wide as the picture, centred: the picture takes the middle two thirds
        val c = BackdropDraw.Crop(-250f, 0f, 1500f, 500f)
        val l = lay(1000, 500, c, 900f, 300f)
        assertEquals(150f, l.picL, 0.01f); assertEquals(600f, l.picR - l.picL, 0.01f)
        assertTrue(l.fadeLeft && l.fadeRight)
        assertFalse(l.fadeTop || l.fadeBottom)
        val v = BackdropDraw.visible(l, 900f, 300f)
        assertEquals(150f, v[0], 0.01f); assertEquals(0f, v[1], 0.01f); assertEquals(750f, v[2], 0.01f); assertEquals(300f, v[3], 0.01f)
    }

    @Test fun theFadingLayerKeepsThePicturePartThatSticksOutOfThePanelUpToTheJoltRoom() {
        // a window 1.5 x as wide as the picture but only 0.6 x as high: the picture sticks out of the panel above, fades left and right
        val c = BackdropDraw.Crop(-250f, 200f, 1500f, 300f)
        val l = lay(1000, 500, c, 900f, 180f)
        assertTrue(l.fadeLeft && l.fadeRight && !l.fadeTop && !l.fadeBottom)
        assertTrue("the picture starts above the panel", l.picT < -BackdropDraw.OVERSCAN * 180f)
        val v = BackdropDraw.visible(l, 900f, 180f)
        assertEquals("not cut at the panel's top: room for a 6 % jolt", -BackdropDraw.OVERSCAN * 180f, v[1], 0.01f)
        assertTrue(-v[1] >= 0.06f * 180f)
        assertEquals(180f, v[3], 0.01f) // the picture ends at the panel's bottom
        assertEquals(l.picL, v[0], 0.01f); assertEquals(l.picR, v[2], 0.01f)
        // a picture that ends inside the panel is cut at its own edge, not at the room
        val small = BackdropDraw.visible(lay(1000, 500, BackdropDraw.Crop(-250f, 0f, 1500f, 500f), 900f, 300f), 900f, 300f)
        assertEquals(300f, small[3], 0.01f); assertEquals(0f, small[1], 0.01f)
    }

    @Test fun aTallerWindowThanThePictureFadesTopAndBottomOnly() {
        val c = BackdropDraw.Crop(0f, -100f, 1000f, 700f)
        val l = lay(1000, 500, c, 400f, 280f)
        assertTrue(l.fadeTop && l.fadeBottom)
        assertFalse(l.fadeLeft || l.fadeRight)
        assertEquals(40f, l.picT, 0.01f)
    }

    @Test fun bothAxesCanShrinkAtOnce() {
        val l = lay(1000, 500, BackdropDraw.Crop(-200f, -100f, 1400f, 700f), 700f, 350f)
        assertTrue(l.fadeLeft && l.fadeRight && l.fadeTop && l.fadeBottom)
    }

    @Test fun aSideAHairFromThePanelEdgeIsNotFaded() {
        val l = lay(1000, 500, BackdropDraw.Crop(-0.2f, 0f, 1000.4f, 500f), 1000f, 500f)
        assertFalse(l.anyFade)
    }

    @Test fun theBlurCoversThePanelPlusTheOverscanAndSitsOnTheBottom() {
        for ((w, h) in listOf(400f to 800f, 800f to 400f, 1000f to 150f, 120f to 700f, 300f to 300f)) {
            val l = lay(1264, 848, BackdropDraw.coverCrop(1264, 848, w, h), w, h)
            assertTrue("$w x $h left", l.blurL <= -w * BackdropDraw.OVERSCAN + 0.01f)
            assertTrue("$w x $h right", l.blurR >= w * (1 + BackdropDraw.OVERSCAN) - 0.01f)
            assertTrue("$w x $h top", l.blurT <= -h * BackdropDraw.OVERSCAN + 0.01f)
            assertEquals("$w x $h bottom", h * (1 + BackdropDraw.OVERSCAN), l.blurB, 0.01f)
            assertEquals("keeps its shape", 96f / 64, (l.blurR - l.blurL) / (l.blurB - l.blurT), 0.001f)
            assertEquals("centred sideways", w / 2f, (l.blurL + l.blurR) / 2f, 0.01f)
        }
    }

    @Test fun theBlurStillCoversWhenEarthquakeMovesItBySixPercent() {
        for ((w, h) in listOf(400f to 800f, 800f to 400f, 1000f to 150f)) {
            val l = lay(1264, 848, BackdropDraw.coverCrop(1264, 848, w, h), w, h)
            for (jolt in listOf(-0.06f * h, 0.06f * h)) {
                assertTrue(l.blurT + jolt <= 0f)
                assertTrue(l.blurB + jolt >= h)
            }
        }
    }

    @Test fun anEmptyPanelOrPictureDoesNotCrash() {
        val c = BackdropDraw.coverCrop(0, 0, 0f, 0f)
        assertTrue(c.w >= 1f && c.h >= 1f)
        val l = lay(100, 100, BackdropDraw.Crop(0f, 0f, 100f, 100f), 10f, 10f)
        assertEquals(0.1f, l.kx, 1e-5f)
    }
}
