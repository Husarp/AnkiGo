package com.husarp.ankigo.ui.movefx

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The CSS filter matrices vs the W3C Filter Effects formulas, and their composition in CSS order. */
class FxColorTest {
    private val eps = 1e-5f

    /** Applies a 4 x 5 matrix to an RGBA vector (0..1 scale; no offsets in these filters). */
    private fun apply(m: FloatArray, v: FloatArray): FloatArray =
        FloatArray(4) { r -> m[r * 5] * v[0] + m[r * 5 + 1] * v[1] + m[r * 5 + 2] * v[2] + m[r * 5 + 3] * v[3] + m[r * 5 + 4] }

    private fun assertMatrix(expected: FloatArray, actual: FloatArray) = assertArrayEquals(expected, actual, eps)

    @Test fun identityAndNeutralValues() {
        assertMatrix(FxColor.identity(), FxColor.brightness(1f))
        assertMatrix(FxColor.identity(), FxColor.saturate(1f))
        assertMatrix(FxColor.identity(), FxColor.sepia(0f))
        assertMatrix(FxColor.identity(), FxColor.hueRotate(0f))
        assertMatrix(FxColor.identity(), FxColor.hueRotate(360f))
    }

    @Test fun brightnessScalesColourNotAlpha() {
        val m = FxColor.brightness(1.5f)
        assertMatrix(floatArrayOf(1.5f, 0f, 0f, 0f, 0f, 0f, 1.5f, 0f, 0f, 0f, 0f, 0f, 1.5f, 0f, 0f, 0f, 0f, 0f, 1f, 0f), m)
        assertMatrix(floatArrayOf(.3f, .6f, .9f, .4f), apply(m, floatArrayOf(.2f, .4f, .6f, .4f)))
    }

    @Test fun saturateZeroIsLuminanceGreyAndKeepsAlpha() {
        val m = FxColor.saturate(0f)
        for (row in 0..2) {
            assertEquals(.213f, m[row * 5], eps); assertEquals(.715f, m[row * 5 + 1], eps); assertEquals(.072f, m[row * 5 + 2], eps)
        }
        assertEquals(1f, m[18], eps)
    }

    @Test fun saturateSpecNumbers() {
        val m = FxColor.saturate(2.2f)
        assertEquals(.213f + .787f * 2.2f, m[0], eps)
        assertEquals(.715f - .715f * 2.2f, m[1], eps)
        assertEquals(.072f - .072f * 2.2f, m[2], eps)
        assertEquals(.213f - .213f * 2.2f, m[5], eps)
        assertEquals(.715f + .285f * 2.2f, m[6], eps)
        assertEquals(.072f + .928f * 2.2f, m[12], eps)
    }

    @Test fun sepiaFullIsTheSpecMatrix() {
        val m = FxColor.sepia(1f)
        assertMatrix(floatArrayOf(.393f, .769f, .189f, 0f, 0f, .349f, .686f, .168f, 0f, 0f, .272f, .534f, .131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f), m)
        // white through sepia(1) is (1.351, 1.203, 0.937) before the final clamp
        val w = apply(m, floatArrayOf(1f, 1f, 1f, 1f))
        assertEquals(1.351f, w[0], 1e-4f); assertEquals(1.203f, w[1], 1e-4f); assertEquals(.937f, w[2], 1e-4f)
    }

    @Test fun sepiaAmountIsClampedAndBlends() {
        assertMatrix(FxColor.sepia(1f), FxColor.sepia(7f))
        assertMatrix(FxColor.identity(), FxColor.sepia(-3f))
        val half = FxColor.sepia(.5f)
        assertEquals(.393f + .607f * .5f, half[0], eps)
        assertEquals(.769f - .769f * .5f, half[1], eps)
        assertEquals(.131f + .869f * .5f, half[12], eps)
    }

    @Test fun hueRotateSpecNumbers() {
        val q = FxColor.hueRotate(90f)       // cos 0, sin 1
        assertEquals(.213f - .213f, q[0], eps); assertEquals(.715f - .715f, q[1], eps); assertEquals(.072f + .928f, q[2], eps)
        assertEquals(.213f + .143f, q[5], eps); assertEquals(.715f + .140f, q[6], eps); assertEquals(.072f - .283f, q[7], eps)
        assertEquals(.213f - .787f, q[10], eps); assertEquals(.715f + .715f, q[11], eps); assertEquals(.072f + .072f, q[12], eps)
        val h = FxColor.hueRotate(180f)      // cos -1, sin 0
        assertEquals(.213f - .787f, h[0], eps); assertEquals(.715f + .715f, h[1], eps); assertEquals(.072f + .072f, h[2], eps)
        assertEquals(.213f + .213f, h[5], eps); assertEquals(.715f - .285f, h[6], eps); assertEquals(.072f + .072f, h[7], eps)
    }

    @Test fun hueRotateKeepsGrey() {
        val g = apply(FxColor.hueRotate(-8f), floatArrayOf(.5f, .5f, .5f, 1f))
        assertEquals(.5f, g[0], 1e-3f); assertEquals(.5f, g[1], 1e-3f); assertEquals(.5f, g[2], 1e-3f)
    }

    @Test fun chainAppliesFirstFilterFirst() {
        val v = floatArrayOf(.8f, .4f, .2f, 1f)
        val a = FxColor.sepia(.55f)
        val b = FxColor.saturate(2.2f)
        val c = FxColor.brightness(1.2f)
        val step = apply(c, apply(b, apply(a, v)))
        val one = apply(FxColor.chain(a, b, c), v)
        for (i in 0..3) assertEquals(step[i], one[i], 1e-4f)
        // and the order matters (brightness alone commutes, a hue turn and a sepia do not)
        val h = FxColor.hueRotate(90f)
        val s1 = apply(FxColor.chain(a, h), v)
        val s2 = apply(FxColor.chain(h, a), v)
        assertTrue(kotlin.math.abs(s1[0] - s2[0]) > 1e-3f || kotlin.math.abs(s1[2] - s2[2]) > 1e-3f)
    }

    @Test fun thenIsTheProductSecondTimesFirst() {
        val a = FxColor.hueRotate(40f)
        val b = FxColor.saturate(.5f)
        val v = floatArrayOf(.9f, .1f, .5f, .7f)
        val direct = apply(b, apply(a, v))
        val composed = apply(FxColor.then(a, b), v)
        for (i in 0..3) assertEquals(direct[i], composed[i], 1e-5f)
    }

    @Test fun chainBuilderMatchesChainFunctionAndResets() {
        val ch = FxColor.Chain()
        val m1 = ch.sepia(.55f).saturate(2.2f).brightness(1.1f).matrix.copyOf()
        assertMatrix(FxColor.chain(FxColor.sepia(.55f), FxColor.saturate(2.2f), FxColor.brightness(1.1f)), m1)
        val m2 = ch.reset().brightness(.5f).matrix.copyOf()
        assertMatrix(FxColor.brightness(.5f), m2)
        assertMatrix(FxColor.identity(), ch.reset().matrix)
    }

    @Test fun offsetsPropagateThroughComposition() {
        // a matrix with an offset (not one of ours) still composes: first adds .1 to red, second doubles red
        val first = FxColor.identity().also { it[4] = .1f }
        val second = FxColor.brightness(2f)
        val out = apply(FxColor.then(first, second), floatArrayOf(.2f, 0f, 0f, 1f))
        assertEquals(.6f, out[0], 1e-5f)
    }

    // ---- R7-FIX 7: no array per operation ----
    @Test fun intoVariantsFillTheGivenArrayCompletelyAndReturnIt() {
        val out = FloatArray(FxColor.SIZE)
        fun dirty() = out.fill(Float.NaN)
        dirty(); assertSame(out, FxColor.brightnessInto(1.3f, out)); assertMatrix(FxColor.brightness(1.3f), out)
        dirty(); assertSame(out, FxColor.saturateInto(2.2f, out)); assertMatrix(FxColor.saturate(2.2f), out)
        dirty(); assertSame(out, FxColor.sepiaInto(.55f, out)); assertMatrix(FxColor.sepia(.55f), out)
        dirty(); assertSame(out, FxColor.hueRotateInto(-8f, out)); assertMatrix(FxColor.hueRotate(-8f), out)
    }

    @Test fun chainKeepsItsArraysAndGivesTheSameResultFrameAfterFrame() {
        val ch = FxColor.Chain()
        val keep = ch.matrix
        for (i in 0 until 5) {
            val b = 1.08f + .22f * kotlin.math.sin(i * 40f / 5f)
            val m = ch.reset().sepia(.55f).saturate(2.2f).brightness(b).hueRotate(12f * i).matrix
            assertSame("the running result is always the same array", keep, m)
            assertMatrix(FxColor.chain(FxColor.sepia(.55f), FxColor.saturate(2.2f), FxColor.brightness(b), FxColor.hueRotate(12f * i)), m)
        }
    }
}
