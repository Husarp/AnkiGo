package com.husarp.ankigo.ui.movefx

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R7-FIX 7: a gradient that stays the same is recognised (the ice cube keeps its shader), one that changes is not. */
class FxGradientKeyTest {
    private val colors = intArrayOf(0x75D2F4FF, 0x4D87CDFA, 0x70B4E8FF)
    private val stops = floatArrayOf(0f, .5f, 1f)

    @Test fun nothingRememberedMatchesNothing() {
        assertFalse(GradientKey().matches(0f, 0f, 1f, 1f, colors, stops))
    }

    @Test fun theSameGradientMatchesFrameAfterFrame() {
        val k = GradientKey()
        k.remember(-50f, -48f, 50f, 48f, colors, stops)
        repeat(5) { assertTrue(k.matches(-50f, -48f, 50f, 48f, colors, stops)) }
    }

    @Test fun anyChangedPartDoesNotMatch() {
        val k = GradientKey()
        k.remember(-50f, -48f, 50f, 48f, colors, stops)
        assertFalse(k.matches(-51f, -48f, 50f, 48f, colors, stops))
        assertFalse(k.matches(-50f, -47f, 50f, 48f, colors, stops))
        assertFalse(k.matches(-50f, -48f, 49f, 48f, colors, stops))
        assertFalse(k.matches(-50f, -48f, 50f, 49f, colors, stops))
        assertFalse(k.matches(-50f, -48f, 50f, 48f, intArrayOf(0x75D2F4FF, 0x4D87CDFA, 0x70B4E8FE), stops))
        assertFalse(k.matches(-50f, -48f, 50f, 48f, colors, floatArrayOf(0f, .4f, 1f)))
    }

    @Test fun itKeepsACopyOfTheArrays() {
        val k = GradientKey()
        val c = colors.copyOf(); val s = stops.copyOf()
        k.remember(0f, 0f, 1f, 1f, c, s)
        c[0] = 0; s[1] = .9f                                       // the caller reuses its arrays
        assertFalse(k.matches(0f, 0f, 1f, 1f, c, s))
        assertTrue(k.matches(0f, 0f, 1f, 1f, colors, stops))
    }
}
