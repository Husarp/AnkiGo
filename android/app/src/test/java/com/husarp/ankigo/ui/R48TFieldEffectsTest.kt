package com.husarp.ankigo.ui

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class R48TFieldEffectsTest {
    @Test fun suppressionLeavesIndependentGroundConditionsVisible() {
        val state = FieldEffectsState()
        state.set("rain", true, true, true)
        assertNull(state.weather)
        assertTrue(state.psychic && state.waterSport && state.active)
        state.set("rain", true, true, false)
        assertEquals("rain", state.weather)
        state.set(null, false, false, false)
        assertFalse(state.active)
    }

    @Test fun unselectedConditionsHaveNoArt() {
        val state = FieldEffectsState()
        for (key in listOf("electric", "grassy", "misty", "trickroom", "gravity", "wonderroom", "magicroom", "mudsport", "iondeluge")) {
            state.set(key, false, false, false)
            assertFalse(key, state.active)
        }
    }

    @Test fun sandHasSmoothIrregularLullsAndDenseBursts() {
        val samples = (0..2400).map { FieldEffectsState.sandDensity(it / 10f) }
        assertTrue(samples.min() < .25f)
        assertTrue(samples.max() > .9f)
        assertTrue(samples.zipWithNext().all { abs(it.first - it.second) < .05f })
        assertTrue((0..50).any { abs(FieldEffectsState.sandDensity(it.toFloat()) - FieldEffectsState.sandDensity(it + 3.1f)) > .2f })
        assertTrue(samples.all { it in .18f..1f })
    }
}
