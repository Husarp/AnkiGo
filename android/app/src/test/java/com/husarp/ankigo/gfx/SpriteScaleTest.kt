package com.husarp.ankigo.gfx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Lane D: pixel art keeps whole-number steps; bigger 3D animations take their room instead of staying at 1x.
class SpriteScaleTest {
    @Test fun pixelArtStaysWholeNumbers() {
        assertTrue(SpriteView.pixelArt(96, 96))
        assertEquals(2f, SpriteView.scale(96, 96, 220, 200, 4), 0f)   // fits 2.08x: 2
        assertEquals(4f, SpriteView.scale(50, 46, 220, 200, 4), 0f)   // capped by the wanted scale
        assertEquals(0.5f, SpriteView.scale(96, 96, 48, 60, 4), 0f)   // not even 1x fits: fractional
        assertEquals(0f, SpriteView.scale(96, 96, 0, 60, 4), 0f)
    }

    @Test fun threeDAnimationsFillTheirRoom() {
        assertFalse(SpriteView.pixelArt(200, 162))
        assertEquals(1.1f, SpriteView.scale(200, 162, 220, 200, 4), 0.001f)          // was 1x
        assertEquals(220f / 147f, SpriteView.scale(147, 111, 220, 200, 4), 0.001f)   // was 1x
        assertEquals(2f, SpriteView.scale(146, 81, 1000, 1000, 2), 0f)               // never over the wanted scale
        assertEquals(0.5f, SpriteView.scale(200, 162, 100, 300, 4), 0f)
    }
}
