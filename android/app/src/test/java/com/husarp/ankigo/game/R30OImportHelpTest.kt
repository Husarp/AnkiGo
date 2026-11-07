package com.husarp.ankigo.game

import com.husarp.ankigo.game.ankimon.AnkimonMerge
import com.husarp.ankigo.help.HelpContent
import com.husarp.ankigo.ui.SettingsCatalog
import org.junit.Assert.*
import org.junit.Test

class R30OImportHelpTest {
    @Test fun settingsAndHelpKeepIncomingImportOnly() {
        assertNotNull(SettingsCatalog.byId("ankimonImport"))
        assertNull(SettingsCatalog.byId("ankimonExport"))
        val page = HelpContent.pages.single { it.id == HelpContent.ANKIMON_IMPORT }
        assertEquals("Ankimon: import", page.title)
        assertFalse(page.toString().contains("Export for Ankimon"))
    }

    @Test fun incomingAnkimonItemsCannotCreateTheAppOnlyFreeze() {
        assertNull(AnkimonMerge.itemKey("Streak Freeze"))
        assertNull(AnkimonMerge.itemKey("streakfreeze"))
        assertEquals("poke-ball", AnkimonMerge.itemKey("Poké Ball"))
    }
}
