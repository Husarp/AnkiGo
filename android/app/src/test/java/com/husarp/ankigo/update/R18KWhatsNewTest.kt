package com.husarp.ankigo.update

import com.husarp.ankigo.R
import com.husarp.ankigo.ui.MatchupBoxes
import com.husarp.ankigo.ui.MatchupGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** R18-K (PLAN 201): the bundled release notes, the "installed" card's show-once rule, and the matchup icons (176). */
class R18KWhatsNewTest {
    private val sample = """
        # comment
        ## 0.22.0
        - **Bold** one.
        - Setting line. @ Settings → Look → Size
        ## 0.24.0
        - Ahead of the bump.
        ## 0.23.0
        - First.
        - Second.
        ## 0.21.0
        stray line
    """.trimIndent()

    @Test fun parsesBlocksNewestFirst() {
        val v = WhatsNew.parse(sample)
        assertEquals(listOf("0.24.0", "0.23.0", "0.22.0"), v.map { it.version })   // 0.21.0 has no change lines: dropped
        assertEquals(WhatsNew.Item("Bold one."), v[2].items[0])
        assertEquals(WhatsNew.Item("Setting line.", "Settings → Look → Size"), v[2].items[1])
        assertEquals(WhatsNew.Item("Second."), v[1].items[1])
    }

    @Test fun fixesGoUnderTheirOwnHeading() {
        val v = WhatsNew.parse("## 0.24.0\n- New thing.\n### Fixes\n- Bug gone.\n## 0.23.0\n### Fixes\n- Only a fix.\n")
        assertEquals(listOf(WhatsNew.Item("New thing.")), v[0].items)
        assertEquals(listOf(WhatsNew.Item("Bug gone.")), v[0].fixes)
        assertEquals("0.23.0", v[1].version)   // a block with only fixes is kept
        assertTrue(v[1].items.isEmpty())
    }

    @Test fun versionsAreComparedAsNumbers() {
        val v = WhatsNew.parse("## 0.9.0\n- a\n## 0.10.0\n- b\n")
        assertEquals(listOf("0.10.0", "0.9.0"), v.map { it.version })
    }

    @Test fun upToHidesNotesWrittenAheadOfTheVersion() {
        val v = WhatsNew.parse(sample)
        assertEquals(listOf("0.23.0", "0.22.0"), WhatsNew.upTo(v, "0.23.0").map { it.version })
        assertTrue(WhatsNew.upTo(v, "0.21.0").isEmpty())
        assertTrue(WhatsNew.upTo(v, "?").isEmpty())
    }

    @Test fun unseenIsNewerThanLastSeenUpToCurrent() {
        val v = WhatsNew.parse(sample)
        assertEquals(listOf("0.23.0"), WhatsNew.unseen(v, "0.22.0", "0.23.0").map { it.version })
        assertEquals(listOf("0.23.0", "0.22.0"), WhatsNew.unseen(v, null, "0.23.0").map { it.version })
        assertTrue(WhatsNew.unseen(v, "0.23.0", "0.23.0").isEmpty())
    }

    @Test fun cardShowsOnceAfterAnUpdate() {
        // first install: no card, the version is stored
        assertEquals(WhatsNew.Action.MARK_SEEN, WhatsNew.decide(null, "0.24.0", wasUpdated = false))
        // updated from a version that never stored one (0.23.0 -> 0.24.0): the card shows
        assertEquals(WhatsNew.Action.SHOW, WhatsNew.decide(null, "0.24.0", wasUpdated = true))
        // stored older version: shows; after a tap the stored version is the current one: never again
        assertEquals(WhatsNew.Action.SHOW, WhatsNew.decide("0.23.0", "0.24.0", wasUpdated = true))
        assertEquals(WhatsNew.Action.SHOW, WhatsNew.decide("0.9.0", "0.10.0", wasUpdated = true))
        assertEquals(WhatsNew.Action.NONE, WhatsNew.decide("0.24.0", "0.24.0", wasUpdated = true))
        assertEquals(WhatsNew.Action.NONE, WhatsNew.decide("0.25.0", "0.24.0", wasUpdated = true))   // a downgrade tells nothing
    }

    @Test fun changeCountReadsNaturally() {
        assertEquals("1 change", WhatsNew.changes(1))
        assertEquals("4 changes", WhatsNew.changes(4))
    }

    @Test fun bundledNotesParse() {
        val f = File("src/main/res/raw/whats_new.txt")
        assertTrue("whats_new.txt not found from ${File(".").absolutePath}", f.exists())
        val v = WhatsNew.parse(f.readText())
        assertTrue(v.size >= 7)
        assertEquals("0.24.0", v.first().version)
        for (b in v) for (i in b.items) {
            assertTrue("${b.version}: ${i.text}", i.text.endsWith(".") || i.text.endsWith("…)") || i.text.endsWith(")."))
            assertFalse("no arrow glyph in a text (${b.version})", i.text.contains("→"))
            assertFalse("no rouble sign (${b.version})", (i.text + (i.path ?: "")).contains("₽"))
            i.path?.let { assertTrue(it.startsWith("Settings → ")) }
        }
        assertNull(v.firstOrNull { it.version == "0.19.0" })
    }

    @Test fun everyGroupHasItsOwnIcon() {
        val icons = MatchupGroup.entries.map { MatchupBoxes.iconRes(it) }
        assertEquals(6, icons.toSet().size)
        assertEquals(R.drawable.ic_mu_burst, MatchupBoxes.iconRes(MatchupGroup.STRONG_AGAINST))   // not the sword
    }
}
