package com.husarp.ankigo.update

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class R42IUpdatesTest {
    @Test fun checkTimestampDistinguishesAutoManualAndNever() {
        assertEquals("Last checked: never", UpdateLogic.lastChecked(0, false) { error("Never has no date") })
        assertEquals("Last checked: 09:15 (auto)", UpdateLogic.lastChecked(42, false) { "09:15" })
        assertEquals("Last checked: 09:15 (manual)", UpdateLogic.lastChecked(42, true) { "09:15" })
    }
    @Test fun showsOnlyThreeMostRecentInstalledVersionsInNumericOrder() {
        val notes = WhatsNew.parse("## 1.7.0\n- a\n## 1.5.0\n- b\n## 1.9.0\n- c\n## 1.6.0\n- d\n## 1.4.0\n- e\n")
        assertEquals(listOf("1.7.0", "1.6.0", "1.5.0"), WhatsNew.recent(notes, "1.7.0").map { it.version })
        assertEquals(listOf("1.5.0", "1.4.0"), WhatsNew.recent(notes, "1.5.0").map { it.version })
        assertEquals(4, WhatsNew.unseen(notes, null, "1.7.0").size) // the post-install notice still covers every unseen version
    }
    @Test fun everyBundledVersionHasItsActualPublishedDate() {
        val notes = WhatsNew.parse(File("src/main/res/raw/whats_new.txt").readText())
        val dates = WhatsNew.releaseDates(File("src/main/res/raw/release_dates.txt").readText())
        for (v in notes) assertNotNull(v.version, dates[v.version])
        assertEquals("2026-11-11", dates["1.7.0"])
        assertEquals("11 Nov 2026", UpdateLogic.releaseDate(dates["1.7.0"]))
        assertEquals("Release date unavailable", UpdateLogic.releaseDate(null))
        assertEquals("Release date unavailable", UpdateLogic.releaseDate("nonsense"))
    }
}
