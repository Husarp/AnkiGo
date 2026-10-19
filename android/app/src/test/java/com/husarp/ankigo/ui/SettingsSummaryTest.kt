package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSummaryTest {
    @Test fun lookLine() {
        assertEquals("System · Mauve · 100 %", SettingsSummary.look(ThemeMode.SYSTEM, Accent.MAUVE, 100))
        assertEquals("Dark · Teal · 85 %", SettingsSummary.look(ThemeMode.DARK, Accent.TEAL, 85))
    }

    @Test fun generations() {
        assertEquals("Gens 1–8", SettingsSummary.gens(1..8))
        assertEquals("Gen 5", SettingsSummary.gens(listOf(5)))
        assertEquals("Gens 1, 3, 9", SettingsSummary.gens(setOf(9, 1, 3)))
        assertEquals("Gens 2–3", SettingsSummary.gens(listOf(3, 2)))
    }

    @Test fun gameLine() {
        assertEquals("Gens 1–8 · goal 100", SettingsSummary.game(1..8, 100))
    }

    @Test fun leaveAndBattle() {
        assertEquals("On leaving: Nothing", SettingsSummary.battle(LeaveMode.OFF, true, false))
        assertEquals("On leaving: Picture-in-picture · compact cards · no stats bar when small",
            SettingsSummary.battle(LeaveMode.PIP, false, true))
        assertEquals("On leaving: Nothing · classic cards", SettingsSummary.battle(LeaveMode.OFF, true, false, false))
    }

    @Test fun soundLine() {
        assertEquals("Off", SettingsSummary.sound(null, null, null))
        assertEquals("Buttons 40% · battle 70% · cries 70%", SettingsSummary.sound(40, 70, 70))
        assertEquals("Battle 30%", SettingsSummary.sound(null, 30, null))
        assertEquals("Buttons 0% · cries 50%", SettingsSummary.sound(0, null, 50))
    }

    @Test fun downloadsAndSizes() {
        assertEquals("Downloading…", SettingsSummary.downloads(0, 0, true))
        assertEquals("Data 4.3 MB · pictures 820 KB", SettingsSummary.downloads(4_300_000, 820_000, false))
        assertEquals("0 B", SettingsSummary.bytes(0))
    }

    @Test fun saveLine() {
        assertEquals("Not saved yet", SettingsSummary.save(null, null) { "x" })
        assertEquals("Not saved yet", SettingsSummary.save(0L, 3L) { "x" })
        assertEquals("Saved today (#7)", SettingsSummary.save(5L, 7L) { "today" })
    }

    @Test fun studyLine() {
        assertEquals("Studies inside AnkiGo · text 100%", SettingsSummary.study(false, false, 100))
        assertEquals("Decks open AnkiDroid · swipes · text 120%", SettingsSummary.study(true, true, 120))
    }

    @Test fun everySectionIsOnTheHomeListOnce() {
        val listed = SettingsSection.GROUPS.flatten()
        assertEquals(SettingsSection.entries.toSet(), listed.toSet())
        assertEquals(listed.size, listed.toSet().size)
    }

    @Test fun linkSectionsDoNotOpenInPlace() {
        assertFalse(SettingsSection.UPDATES.opens)
        assertFalse(SettingsSection.DIAGNOSTICS.opens)
        assertTrue(SettingsSection.LOOK.opens)
        assertEquals(SettingsSection.LOOK, SettingsSection.byName("LOOK"))
        assertNull(SettingsSection.byName("nope"))
    }
}
