package com.husarp.ankigo.ui

import org.junit.Assert.*
import org.junit.Test

class R49XFieldEffectsTest {
    @Test fun hailIsWeatherAndSuppressionLeavesTerrainAlone() {
        val state = FieldEffectsState()
        state.set("hail", false, false, false, "grassy")
        assertEquals("hail", state.weather)
        assertEquals("grassy", state.terrain)
        assertTrue(state.active && state.animated)
        state.set("hail", false, false, true, "grassy")
        assertNull(state.weather)
        assertEquals("grassy", state.terrain)
        assertTrue(state.active)
        assertFalse(state.animated)
        state.set(null, false, false, false)
        assertFalse(state.active || state.animated)
    }

    @Test fun replacingTerrainKeepsOnlyTheSelectedTexture() {
        val state = FieldEffectsState()
        for (key in listOf("electric", "grassy", "misty")) {
            state.set(null, false, false, false, key)
            assertEquals(key, state.terrain)
            assertTrue(state.active)
            assertEquals(key != "grassy", state.animated)
        }
        state.set(null, true, true, false, "psychic")
        assertNull(state.terrain)
        assertTrue(state.psychic && state.waterSport && state.animated)
        state.set(null, false, false, false, "trickroom")
        assertFalse(state.active)
    }

    @Test fun electricHasTransparentBriefFlashesWithIrregularGaps() {
        val samples = (0..36000).map { FieldEffectsState.electricAlpha(it / 100f) }
        assertTrue(samples.all { it in 0f.. .32f })
        assertTrue(samples.max() > .31f)
        assertTrue(samples.count { it == 0f } > samples.size * .89f)
        val starts = samples.indices.drop(1).filter { samples[it] > 0f && samples[it - 1] == 0f }
        val gaps = starts.zipWithNext { a, b -> (b - a) / 100f }
        assertTrue(gaps.all { it in 3.3f..8.7f })
        assertTrue(gaps.max() - gaps.min() > 2f)
        for (start in starts) {
            val duration = samples.drop(start).takeWhile { it > 0f }.size / 100f
            assertTrue(duration in .35f.. .61f)
        }
    }

    @Test fun hailIsMuchFasterThanPreviewWithOneOrTwoFrameSplashes() {
        val speeds = (0 until 34).map { FieldEffectsState.hailSpeed(it) }
        // Preview fell at 48..96 scene pixels/second; even the slowest new bead is over twice as fast.
        assertTrue(speeds.all { it in 220f..320f })
        assertTrue(speeds.max() - speeds.min() > 60f)
        assertTrue(FieldEffectsState.HAIL_SPLASH_SECONDS in .063f.. .126f)
    }
}
