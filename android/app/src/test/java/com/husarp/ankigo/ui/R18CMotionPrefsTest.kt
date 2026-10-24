package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R18-C (PLAN 247): the rule of "Pokémon movement" (pure). */
class R18CMotionPrefsTest {
    @Test fun fiveStepsInOrder() {
        assertEquals(listOf("Always", "Often", "Now and then", "Rarely", "Never"), MotionPrefs.LABELS)
        assertEquals(4, MotionPrefs.LAST)
        assertEquals("Now and then", MotionPrefs.label(MotionPrefs.NOW_AND_THEN))
        assertEquals(0, MotionPrefs.clamp(-3)); assertEquals(4, MotionPrefs.clamp(99))
    }

    @Test fun thePauseGrowsAlongTheSlider() {
        assertEquals(0L, MotionPrefs.pauseMs(MotionPrefs.ALWAYS))
        val p = (MotionPrefs.OFTEN..MotionPrefs.RARELY).map { MotionPrefs.pauseMs(it) }
        assertEquals(listOf(2_000L, 5_000L, 12_000L), p)
        assertEquals(-1L, MotionPrefs.pauseMs(MotionPrefs.NEVER))
    }

    @Test fun theJitterStaysWithinThirtyPercent() {
        for (step in MotionPrefs.OFTEN..MotionPrefs.RARELY) {
            val base = MotionPrefs.pauseMs(step)
            var r = 0f
            while (r <= 1f) {
                val v = MotionPrefs.jitteredPauseMs(step, r)
                assertTrue("$step $r $v", v >= (base * 0.7).toLong() - 1 && v <= (base * 1.3).toLong() + 1)
                r += 0.05f
            }
            assertEquals((base * 0.7), MotionPrefs.jitteredPauseMs(step, 0f).toDouble(), 1.0)
            assertEquals((base * 1.3), MotionPrefs.jitteredPauseMs(step, 1f).toDouble(), 1.0)
            assertEquals(base, MotionPrefs.jitteredPauseMs(step, 0.5f))
            assertTrue("the two sides differ", MotionPrefs.jitteredPauseMs(step, 0.2f) != MotionPrefs.jitteredPauseMs(step, 0.8f))
        }
        assertEquals(0L, MotionPrefs.jitteredPauseMs(MotionPrefs.ALWAYS, 0.9f))
        assertEquals(-1L, MotionPrefs.jitteredPauseMs(MotionPrefs.NEVER, 0.9f))
    }

    @Test fun neverMeansNoTicksAndAlwaysMeansNoPauses() {
        assertFalse(MotionPrefs.moves(MotionPrefs.NEVER))
        for (s in MotionPrefs.ALWAYS..MotionPrefs.RARELY) assertTrue(MotionPrefs.moves(s))
        assertFalse(MotionPrefs.pauses(MotionPrefs.ALWAYS)); assertFalse(MotionPrefs.pauses(MotionPrefs.NEVER))
        for (s in MotionPrefs.OFTEN..MotionPrefs.RARELY) assertTrue(MotionPrefs.pauses(s))
    }

    @Test fun reducedMotionStartsAtNeverUntilTheSliderIsMoved() {
        assertEquals(MotionPrefs.ALWAYS, MotionPrefs.defaultStep(false))
        assertEquals(MotionPrefs.NEVER, MotionPrefs.defaultStep(true))
        assertEquals(MotionPrefs.NEVER, MotionPrefs.resolve(null, true))
        assertEquals(MotionPrefs.ALWAYS, MotionPrefs.resolve(null, false))
        assertEquals(MotionPrefs.OFTEN, MotionPrefs.resolve(MotionPrefs.OFTEN, true))    // a chosen step wins
        assertEquals(MotionPrefs.ALWAYS, MotionPrefs.resolve(MotionPrefs.ALWAYS, true))
        assertEquals(MotionPrefs.NEVER, MotionPrefs.resolve(42, false))                  // an out-of-range saved value is clamped
    }

    @Test fun theSettingIsInTheCatalogUnderBattlePanel() {
        val e = SettingsCatalog.byTitle("Pokémon movement")!!
        assertEquals(CatalogSection.BATTLE, e.section)
        assertEquals("Battle panel", e.sub)
        assertTrue(SettingsSearch.match("movement").any { it.info.id == e.id })
        assertTrue(SettingsSearch.match("still").isNotEmpty() || SettingsSearch.match("idle").any { it.info.id == e.id })
    }
}
