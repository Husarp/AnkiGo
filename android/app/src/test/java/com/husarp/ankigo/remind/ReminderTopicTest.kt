package com.husarp.ankigo.remind

import com.husarp.ankigo.ui.CatalogSection
import com.husarp.ankigo.ui.SettingsCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** B8: a Settings search result for a reminder setting opens the sheet of its reminder. */
class ReminderTopicTest {
    private val entries = SettingsCatalog.all.filter { it.section == CatalogSection.REMINDERS }

    @Test fun everyReminderSettingOfTheCatalogHasASheetOrARow() {
        assertEquals(emptyList<String>(), entries.filter { ReminderTopic.forSetting(it.id) == null }.map { it.id })
    }

    @Test fun theCatalogGroupAndTheTopicAgree() {
        for (e in entries) {
            val topic = ReminderTopic.forSetting(e.id)
            when (e.sub) {
                "Cards due" -> assertEquals(e.id, ReminderTopic.DUE, topic)
                "Own goal" -> assertEquals(e.id, ReminderTopic.GOAL, topic)
                "Quiet hours" -> assertEquals(e.id, ReminderTopic.QUIET, topic)
                "Game" -> assertEquals(e.id, when {
                    e.id.startsWith("streak") -> ReminderTopic.STREAK
                    e.id.startsWith("dailyGoal") -> ReminderTopic.DAILY
                    else -> ReminderTopic.SHOP
                }, topic)
                else -> throw AssertionError("unmapped catalog group ${e.sub} for ${e.id}")
            }
        }
    }

    @Test fun specificOptionsOpenTheirOwnReminder() {
        assertEquals(ReminderTopic.DUE, ReminderTopic.forSetting("minGapHours"))
        assertEquals(ReminderTopic.QUIET, ReminderTopic.forSetting("quietFrom"))
        assertEquals(ReminderTopic.GOAL, ReminderTopic.forSetting("goalCount"))
        assertNull(ReminderTopic.forSetting("theme"))
        assertNull(ReminderTopic.forSetting(""))
    }
}
