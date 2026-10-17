package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GoalToneTest {
    private val p = GoalPalette(red = 0xFFFF0000.toInt(), orange = 0xFFFF8000.toInt(), green = 0xFF00FF00.toInt())

    @Test fun redAtZero() = assertEquals(p.red, GoalTone.color(0f, p))

    @Test fun orangeInTheMiddle() = assertEquals(p.orange, GoalTone.color(0.5f, p))

    @Test fun greenAtFullAndStaysGreenBeyond() {
        assertEquals(p.green, GoalTone.color(1f, p))
        assertEquals(p.green, GoalTone.color(1.4f, p))
    }

    @Test fun blendsSmoothlyBetweenTheStops() {
        assertEquals(0xFFFF4000.toInt(), GoalTone.color(0.25f, p))   // halfway red -> orange
        assertEquals(0xFF80C000.toInt(), GoalTone.color(0.75f, p))   // halfway orange -> green
    }

    @Test fun outOfRangeInputsAreClamped() {
        assertEquals(p.red, GoalTone.color(-1f, p))
        assertEquals(p.red, GoalTone.color(Float.NaN, p))
    }

    @Test fun flameLevels() {
        assertEquals(0, GoalTone.flameLevel(0f))
        assertEquals(0, GoalTone.flameLevel(0.99f))
        assertEquals(1, GoalTone.flameLevel(1f))
        assertEquals(1, GoalTone.flameLevel(1.4f))
        assertEquals(2, GoalTone.flameLevel(1.5f))
        assertEquals(3, GoalTone.flameLevel(2f))
        assertEquals(3, GoalTone.flameLevel(9f))
        assertEquals(0, GoalTone.flameLevel(Float.NaN))
    }
}
