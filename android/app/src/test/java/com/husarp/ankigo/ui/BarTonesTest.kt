package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarTonesTest {
    private val p = BarPalette(sky = 0x11, peach = 0x22, teal = 0x33, accent = 0x44, pink = 0x55)

    @Test fun byMeaningGivesEachMeaningItsOwnColour() {
        assertEquals(BarFill(0x11), BarTones.resolve(BarMeaning.XP, BarStyle.MEANING, p))
        assertEquals(BarFill(0x22), BarTones.resolve(BarMeaning.GOAL, BarStyle.MEANING, p))
        assertEquals(BarFill(0x33), BarTones.resolve(BarMeaning.COLLECTION, BarStyle.MEANING, p))
        assertEquals(BarFill(0x44), BarTones.resolve(BarMeaning.PROGRESS, BarStyle.MEANING, p))
    }

    @Test fun accentIsOneColourForEveryMeaning() {
        for (m in BarMeaning.entries) assertEquals(BarFill(0x44), BarTones.resolve(m, BarStyle.ACCENT, p))
    }

    @Test fun gradientIsAccentToPinkForEveryMeaning() {
        for (m in BarMeaning.entries) assertEquals(BarFill(0x44, 0x55), BarTones.resolve(m, BarStyle.GRADIENT, p))
    }

    @Test fun plainFillsHaveNoGradient() {
        for (m in BarMeaning.entries) for (s in listOf(BarStyle.MEANING, BarStyle.ACCENT)) assertNull(BarTones.resolve(m, s, p).gradientTo)
    }

    @Test fun meaningsAreDistinctOnlyByMeaning() {
        val colours = listOf(BarMeaning.XP, BarMeaning.GOAL, BarMeaning.COLLECTION).map { BarTones.resolve(it, BarStyle.MEANING, p).color }
        assertEquals(3, colours.toSet().size)
        assertNotEquals(colours[0], BarTones.resolve(BarMeaning.PROGRESS, BarStyle.MEANING, p).color)
    }

    private val gp = BarPalette(0x11, 0x22, 0x33, 0x44, 0x55, GoalPalette(0xFFFF0000.toInt(), 0xFFFF8000.toInt(), 0xFF00FF00.toInt()))

    @Test fun goalFollowsGoalToneByFractionInMeaningStyle() {
        for (f in listOf(0f, 0.25f, 0.5f, 0.9f, 1f, 1.6f))
            assertEquals(BarFill(GoalTone.color(f, gp.goal!!)), BarTones.resolve(BarMeaning.GOAL, BarStyle.MEANING, gp, f))
        assertEquals(0xFFFF0000.toInt(), BarTones.resolve(BarMeaning.GOAL, BarStyle.MEANING, gp, 0f).color)
        assertEquals(0xFF00FF00.toInt(), BarTones.resolve(BarMeaning.GOAL, BarStyle.MEANING, gp, 1.6f).color)
    }

    @Test fun goalKeepsStyleColourInAccentAndGradient() {
        assertEquals(BarFill(0x44), BarTones.resolve(BarMeaning.GOAL, BarStyle.ACCENT, gp, 0.3f))
        assertEquals(BarFill(0x44, 0x55), BarTones.resolve(BarMeaning.GOAL, BarStyle.GRADIENT, gp, 0.3f))
    }

    @Test fun otherMeaningsIgnoreFraction() {
        assertEquals(BarFill(0x11), BarTones.resolve(BarMeaning.XP, BarStyle.MEANING, gp, 0.3f))
    }
}
