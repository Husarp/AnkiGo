package com.husarp.ankigo.ui

import com.husarp.ankigo.remind.ReminderPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    private fun titles(q: String) = SettingsSearch.match(q).map { it.info.title }

    @Test fun blankFindsNothing() {
        assertTrue(SettingsSearch.match("").isEmpty())
        assertTrue(SettingsSearch.match("   ").isEmpty())
    }

    @Test fun findsByTitleExplanationAndSectionWords() {
        assertTrue("Answer timer" in titles("timer"))
        assertTrue("Faster start" in titles("lead"))            // only in its explanation
        assertTrue("Quiet from" in titles("reminders quiet"))   // section word + title word
    }

    @Test fun everyWordMustMatch() {
        assertTrue("Answer timer" in titles("answer timer"))
        assertTrue(titles("timer zzzz").isEmpty())
    }

    @Test fun ignoresCaseAndAccents() {
        assertEquals(titles("pokemon cries"), titles("POKÉMON CRIES"))
        assertTrue("Pokémon cries" in titles("pokemon"))
    }

    @Test fun titleMatchesComeFirst() {
        val t = titles("catch")
        val firstExplainOnly = t.indexOfFirst { SettingsCatalog.byTitle(it)!!.title.lowercase().contains("catch").not() }
        val lastTitle = t.indexOfLast { SettingsCatalog.byTitle(it)!!.title.lowercase().contains("catch") }
        assertTrue(firstExplainOnly == -1 || lastTitle < firstExplainOnly)
        assertTrue(t.first().lowercase().contains("catch"))
    }

    @Test fun hitSaysWhereItLives() {
        val hit = SettingsSearch.match("always catch").first()
        assertEquals("Game · When you beat a wild Pokémon", hit.where)
        assertEquals("Look", SettingsSearch.match("interface size").first().where)
    }

    @Test fun everyCatalogEntryHasASectionThatOpensOrStartsAScreen() {
        for (e in SettingsCatalog.all) {
            val s = SettingsSearch.sectionOf(e)
            assertTrue("${e.id} -> ${s.name} not on the home list", s in SettingsSection.GROUPS.flatten())
        }
    }

    @Test fun dataEntriesGoToTheirOwnSection() {
        assertEquals(SettingsSection.DOWNLOADS, SettingsSearch.sectionOf(SettingsCatalog.byId("gameDataSize")!!))
        assertEquals(SettingsSection.SAVE, SettingsSearch.sectionOf(SettingsCatalog.byId("exportSave")!!))
        assertEquals(SettingsSection.ANKIMON, SettingsSearch.sectionOf(SettingsCatalog.byId("ankimonImport")!!))
        assertEquals(SettingsSection.SOUND, SettingsSearch.sectionOf(SettingsCatalog.byId("volumeButtons")!!))
        assertEquals(SettingsSection.REMINDERS, SettingsSearch.sectionOf(SettingsCatalog.byId("dueOn")!!))
    }

    @Test fun groupsKeepOrderAndPutNamedOnesFirst() {
        val g = SettingsSearch.groups(listOf("Pokémon", "Difficulty", "Battle", "Pokémon", "Battle"), listOf("Battle", "Difficulty", "Nope"))
        assertEquals(listOf("Battle", "Difficulty", "Pokémon"), g.map { it.title })
        assertEquals(listOf(listOf(2, 4), listOf(1), listOf(0, 3)), g.map { it.rows })
        assertEquals(listOf("A", "B"), SettingsSearch.groups(listOf("A", "B", "A")).map { it.title })
    }

    @Test fun remindersSummary() {
        assertEquals("Off", SettingsSummary.reminders(ReminderPrefs()))
        assertEquals("1 on · cards due", SettingsSummary.reminders(ReminderPrefs(dueOn = true)))
        assertEquals("2 on · cards due, streak", SettingsSummary.reminders(ReminderPrefs(dueOn = true, streakOn = true)))
        assertEquals("4 on · cards due, own goal +2",
            SettingsSummary.reminders(ReminderPrefs(dueOn = true, goalOn = true, streakOn = true, shopOn = true)))
    }

    @Test fun helpAndRemindersAreOnTheHome() {
        val all = SettingsSection.GROUPS.flatten()
        assertTrue(SettingsSection.REMINDERS in all && SettingsSection.HELP in all)
        assertTrue(!SettingsSection.HELP.opens && SettingsSection.REMINDERS.opens)
    }
}
