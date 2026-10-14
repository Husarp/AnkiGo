package com.husarp.ankigo.ui

import com.husarp.ankigo.game.ankimon.ExportReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// TICKET T53: the export's result and warning texts.
class AnkimonExportTextTest {
    private fun r(fresh: Boolean = false, mons: Int = 312, added: Int = 0, released: Int = 0, untouched: Int = 0, team: Int = 6) =
        ExportReport(fresh, mons, added, 0, untouched, released, team, 12400, 210, 30)

    @Test fun plainSummary() {
        assertEquals("312 Pokémon · team of 6 · ₽ 12,400 · Pokédex 210", AnkimonExportFlow.summary(r()))
    }

    @Test fun summaryMentionsNewReleasedAndKeptOnlyWhenAboveZero() {
        assertEquals("312 Pokémon (5 new from this phone, 2 released, 14 kept as they were) · team of 6 · ₽ 12,400 · Pokédex 210",
            AnkimonExportFlow.summary(r(added = 5, released = 2, untouched = 14)))
        assertEquals("312 Pokémon (2 released) · team of 6 · ₽ 12,400 · Pokédex 210", AnkimonExportFlow.summary(r(released = 2)))
    }

    @Test fun freshExportDoesNotCallEverythingNew() {
        assertEquals("312 Pokémon · no team · ₽ 12,400 · Pokédex 210", AnkimonExportFlow.summary(r(fresh = true, added = 312, team = 0)))
    }

    @Test fun baseLine() {
        assertTrue(AnkimonExportFlow.baseLine(null).startsWith("You haven't imported an Ankimon save"))
        val kept = AnkimonExportFlow.baseLine(java.time.LocalDate.of(2026, 10, 12).atTime(12, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli())
        assertTrue(kept, kept.startsWith("Based on the Ankimon save you imported on 12 Oct 2026, with your AnkiGo Pokémon"))
    }
}
