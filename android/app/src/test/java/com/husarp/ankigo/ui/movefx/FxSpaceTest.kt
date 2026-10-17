package com.husarp.ankigo.ui.movefx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The mockup stage mapped onto the arena, both directions. */
class FxSpaceTest {
    private val eps = 1e-3f

    // the mockup layout at 2 px per unit: the player (attacker) bottom left, the wild one (target) top right
    private val unit = 2f
    private val forward = FxSpace(FxSpace.AX * unit, FxSpace.AY * unit, FxSpace.DX * unit, FxSpace.DY * unit, unit)
    // the wild one attacks us: the same two spots swapped
    private val reverse = FxSpace(FxSpace.DX * unit, FxSpace.DY * unit, FxSpace.AX * unit, FxSpace.AY * unit, unit)

    @Test fun directionFollowsTheTargetSide() {
        assertEquals(1f, forward.dir, 0f)
        assertEquals(-1f, reverse.dir, 0f)
        assertEquals(1f, FxSpace(10f, 10f, 10f, 50f, 1f).dir, 0f)
    }

    @Test fun attackerRelativePointsMirrorOnlyLeftRight() {
        val f = forward.fromAttacker(55f, -28f)
        assertEquals(140f * 2 + 55f * 2, f.x, eps); assertEquals(190f * 2 - 28f * 2, f.y, eps)
        val r = reverse.fromAttacker(55f, -28f)
        assertEquals(350f * 2 - 55f * 2, r.x, eps)      // toward the target = to the left now
        assertEquals(105f * 2 - 28f * 2, r.y, eps)      // up stays up
    }

    @Test fun targetRelativePointsMirrorOnlyLeftRight() {
        val f = forward.fromTarget(-12f, 4f)
        assertEquals(350f * 2 - 12f * 2, f.x, eps); assertEquals(105f * 2 + 4f * 2, f.y, eps)
        val r = reverse.fromTarget(-12f, 4f)
        assertEquals(140f * 2 + 12f * 2, r.x, eps)      // "in front of the target" is toward the attacker: to the right
        assertEquals(190f * 2 + 4f * 2, r.y, eps)
    }

    @Test fun betweenInterpolatesTheMappedCentres() {
        val m = forward.between(.5f, 0f, 0f)
        assertEquals((140f + 350f) / 2 * 2, m.x, eps); assertEquals((190f + 105f) / 2 * 2, m.y, eps)
        val r = reverse.between(.25f, 10f, 6f)
        assertEquals(700f + (280f - 700f) * .25f - 10f * 2, r.x, eps)
        assertEquals(210f + (380f - 210f) * .25f + 6f * 2, r.y, eps)
        assertEquals(forward.betweenX(.3f, 7f), forward.between(.3f, 7f, 1f).x, 0f)
        assertEquals(forward.betweenY(.3f, 1f), forward.between(.3f, 7f, 1f).y, 0f)
    }

    @Test fun offsetsAndLengths() {
        assertEquals(30f, forward.x(15f), eps); assertEquals(-30f, reverse.x(15f), eps)
        assertEquals(30f, forward.y(15f), eps); assertEquals(30f, reverse.y(15f), eps)
        assertEquals(30f, reverse.len(15f), eps)
    }

    @Test fun towardVectorIsTheMockupsInBothDirections() {
        assertEquals(210f, forward.towardX, eps); assertEquals(-85f, forward.towardY, eps)
        assertEquals(210f, reverse.towardX, eps); assertEquals(85f, reverse.towardY, eps)
        assertEquals(kotlin.math.hypot(210f, 85f), forward.pathLength, eps)
    }

    @Test fun pathAngleIsInScreenAxes() {
        assertEquals(Math.atan2(-85.0, 210.0).toFloat(), forward.pathAngle, 1e-4f)
        assertEquals(Math.atan2(85.0, -210.0).toFloat(), reverse.pathAngle, 1e-4f)
    }

    @Test fun artPixelsAreWholeAndAtLeastOne() {
        assertEquals(2, FxSpace(0f, 0f, 1f, 1f, 1f).artPixels())          // 1.5 px rounds to 2
        assertEquals(1, FxSpace(0f, 0f, 1f, 1f, .4f).artPixels())         // 0.6 px: one device pixel, not two
        assertEquals(1, FxSpace(0f, 0f, 1f, 1f, .3f).artPixels())        // 0.45 px: never below 1
        assertEquals(3, FxSpace(0f, 0f, 1f, 1f, 2.2f).artPixels())       // 3.3
        assertEquals(5, FxSpace(0f, 0f, 1f, 1f, 2.2f).artPixels(1.5f))   // 4.95
        assertTrue(abs(forward.artPixels(1.2f) - 1.5f * unit * 1.2f) <= 1f)
    }

    @Test fun artPixelsFollowTheUnitWithinOneDevicePixel() {
        // a small arena (study strip), a phone, a tablet: the art cell stays within one device pixel of the intended 1.5 x unit x k
        for (u in floatArrayOf(.3f, .6f, 1f, 1.8f, 2.5f, 4f)) for (k in floatArrayOf(.8f, 1f, 1.6f)) {
            val cell = FxSpace(0f, 0f, 1f, 1f, u).artPixels(k)
            assertTrue("unit $u k $k cell $cell", abs(cell - FxPixelArt.ART_PX * u * k) <= 1f)
        }
        // and a flame (22 art pixels wide) is as wide as its cells: it shrinks with the unit instead of stopping at 44 px
        val small = FxSpace(0f, 0f, 1f, 1f, .6f).artPixels() * 22
        val big = FxSpace(0f, 0f, 1f, 1f, 2.5f).artPixels() * 22
        assertTrue("$small vs $big", small < big / 3)
    }
}
