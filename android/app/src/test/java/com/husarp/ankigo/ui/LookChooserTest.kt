package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 10 W7 / W8 (ticket F): the pure parts of the look choosers: columns, which chooser a catalog line belongs to, the catalog lines. */
class LookChooserTest {
    @Test fun threeToARowOnAPhoneTwoWhenNarrowOrBig() {
        assertEquals(3, LookChoosers.columns(352f, 1.0f))   // 412 dp phone, Settings card
        assertEquals(3, LookChoosers.columns(376f, 1.3f))   // the sheet
        assertEquals(2, LookChoosers.columns(260f, 1.0f))   // 320 dp wide
        assertEquals(2, LookChoosers.columns(299f, 1.0f))
        assertEquals(3, LookChoosers.columns(300f, 1.0f))
        assertEquals(2, LookChoosers.columns(352f, 2.0f))   // font 2.0
        assertEquals(2, LookChoosers.columns(352f, 1.6f))
        assertEquals(3, LookChoosers.columns(352f, 1.5f))
    }

    @Test fun everyCatalogLineOfTheThreeEntriesLandsOnItsChooser() {
        val infoBox = listOf("infoBoxAbove", "infoBoxInside") + InfoBoxLook.entries.map { "infoBoxLook" + it.name.lowercase().replaceFirstChar(Char::uppercase) }
        for (id in infoBox) {
            assertNotNull("$id is in the catalog", SettingsCatalog.byId(id))
            assertEquals(id, LookChoosers.INFO_BOX, LookChoosers.titleFor(id))
            assertEquals(id, "Info box style", SettingsCatalog.byId(id)!!.sub)
        }
        for (id in HpBarLook.entries.map { "hpBars" + it.name.lowercase().replaceFirstChar(Char::uppercase) }) {
            assertNotNull("$id is in the catalog", SettingsCatalog.byId(id))
            assertEquals(id, LookChoosers.BARS, LookChoosers.titleFor(id))
            assertEquals(id, "HP / XP bars", SettingsCatalog.byId(id)!!.sub)
        }
        val frames = SettingsCatalog.all.filter { it.sub == "Battle frame" }
        assertEquals(BattleFrameStyle.entries.size, frames.size)
        for (e in frames) assertEquals(e.id, LookChoosers.FRAME, LookChoosers.titleFor(e.id))
        assertNull(LookChoosers.titleFor("pokemonSize"))
        assertNull(LookChoosers.titleFor("turnDots"))
        assertNull(LookChoosers.titleFor("fasterStart"))
    }

    @Test fun theCatalogTitlesAreTheNamesOnTheTiles() {
        for (look in InfoBoxLook.entries) {
            val id = "infoBoxLook" + look.name.lowercase().replaceFirstChar(Char::uppercase)
            assertEquals(look.label, SettingsCatalog.byId(id)!!.title)
        }
        for (look in HpBarLook.entries) assertEquals(look.label, SettingsCatalog.byId("hpBars" + look.name.lowercase().replaceFirstChar(Char::uppercase))!!.title)
        for (s in InfoBoxStyle.entries) assertTrue(SettingsCatalog.all.any { it.title == s.label && it.sub == "Info box style" })
        for (s in BattleFrameStyle.entries) assertTrue(SettingsCatalog.all.any { it.title == s.label && it.sub == "Battle frame" })
    }

    @Test fun searchFindsAnEntryByItsOwnWords() {
        fun ids(q: String) = SettingsSearch.match(q).map { it.info.id }
        assertTrue("infoBoxLookGlass" in ids("glass"))
        assertTrue("hpBarsPixel" in ids("pixel"))
        assertTrue("hpBarsToday" in ids("hp / xp bars"))
        assertTrue("infoBoxLookHud" in ids("hud"))
        assertTrue(SettingsSearch.match("dark plate").first().where.endsWith("· Info box style"))
        assertTrue(SettingsSearch.whereOf(SettingsCatalog.byId("hpBarsPixel")!!).endsWith("· HP / XP bars"))
    }

    @Test fun theValueLineNamesTheLookAndTheLayout() {
        assertEquals("Card · number above", LookChoosers.infoBoxValue(InfoBoxLook.TODAY, InfoBoxStyle.ABOVE))
        assertEquals("Glass · number inside", LookChoosers.infoBoxValue(InfoBoxLook.GLASS, InfoBoxStyle.INSIDE))
    }
}
