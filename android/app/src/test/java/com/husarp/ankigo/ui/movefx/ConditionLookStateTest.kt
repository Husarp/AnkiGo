package com.husarp.ankigo.ui.movefx

import org.junit.Assert.*
import org.junit.Test

class ConditionLookStateTest {
    @Test fun frozenPersistsAcrossTurnsUntilAnyCure() {
        val look = ConditionLookState()
        look.set("frz", 10)
        look.advance(1_000_000)
        assertEquals("frz", look.status)
        look.set("frz", 1_000_010)
        assertEquals(10L, look.since)
        look.set(null, 1_000_020)
        assertTrue(look.clearing)
        look.advance(1_001_019)
        assertTrue(look.visible)
        look.advance(1_001_020)
        assertFalse(look.visible)
    }

    @Test fun wakingShakesBrieflyThenStops() {
        val look = ConditionLookState()
        look.set("slp", 0)
        look.advance(50_000)
        assertEquals("slp", look.status)
        look.set(null, 50_000)
        look.advance(50_419)
        assertTrue(look.clearing)
        look.advance(50_420)
        assertFalse(look.visible)
    }

    @Test fun repeatedClearsDoNotRestartTheExit() {
        for (status in listOf("frz", "slp", "psn", "tox", "attract")) {
            val look = ConditionLookState()
            look.set(status, 0)
            look.set(null, 100)
            look.set(null, 300)
            assertEquals(100L, look.since)
            look.advance(100 + look.duration())
            assertFalse(look.visible)
        }
    }

    @Test fun departureAndDisabledAnimationsStopWithoutAnExit() {
        for (status in listOf("frz", "slp", "psn", "tox", "attract")) {
            val look = ConditionLookState()
            look.set(status, 0)
            look.set(null, 100, immediate = true)
            assertFalse(look.visible)
            assertFalse(look.clearing)
        }
    }

    @Test fun reappliedStatusCancelsAnExit() {
        val look = ConditionLookState()
        look.set("frz", 0)
        look.set(null, 100)
        look.set("frz", 200)
        assertFalse(look.clearing)
        look.advance(2000)
        assertEquals("frz", look.status)
    }

    @Test fun changingConditionsReplacesThePreviousLook() {
        val look = ConditionLookState()
        look.set("frz", 0)
        look.set("slp", 100)
        assertEquals("slp", look.status)
        assertEquals(100L, look.since)
        assertFalse(look.clearing)
        look.set("brn", 200)
        assertTrue(look.clearing)
        look.advance(620)
        assertFalse(look.visible)
    }

    @Test fun poisonAndLovePersistWithoutRestartingOnTheNextTurn() {
        for (status in listOf("psn", "tox", "attract")) {
            val look = ConditionLookState()
            look.set(status, 10)
            look.advance(50000)
            look.set(status, 50000)
            assertEquals(status, look.status)
            assertEquals(10L, look.since)
        }
    }

    @Test fun otherConditionsDoNotStartALook() {
        for (status in listOf(null, "par", "brn")) {
            val look = ConditionLookState()
            look.set(status, 0)
            assertFalse(look.visible)
        }
    }
}
