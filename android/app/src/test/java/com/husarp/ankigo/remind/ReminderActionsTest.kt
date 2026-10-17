package com.husarp.ankigo.remind

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderActionsTest {
    @Test fun studyNoticesHaveStudyNowInOneHourNotToday() {
        for (k in listOf(ReminderKind.CARDS_DUE, ReminderKind.OWN_GOAL)) {
            assertEquals(listOf(NoticeAction.STUDY_NOW, NoticeAction.SNOOZE, NoticeAction.NOT_TODAY), ReminderActions.forKind(k))
        }
        assertEquals(listOf("Study now", "In 1 hour", "Not today"), ReminderActions.forKind(ReminderKind.CARDS_DUE).map { it.label })
    }

    @Test fun streakAndDailyGoalHaveStudyNowAndNotToday() {
        for (k in listOf(ReminderKind.STREAK, ReminderKind.DAILY_GOAL)) {
            assertEquals(listOf(NoticeAction.STUDY_NOW, NoticeAction.NOT_TODAY), ReminderActions.forKind(k))
        }
        assertTrue(ReminderActions.forKind(ReminderKind.SHOP).isEmpty())
    }

    @Test fun buttonsThatNeedTheReceiverHaveTheirOwnActionStrings() {
        val actions = NoticeAction.entries.mapNotNull { it.intentAction }
        assertEquals(actions.size, actions.toSet().size)
        assertNull(NoticeAction.STUDY_NOW.intentAction) // it opens an Activity, not the receiver
        assertTrue(actions.all { it.startsWith("com.husarp.ankigo.remind.") })
    }

    @Test fun onlyTheShopNewsOpensTheShop() {
        assertTrue(ReminderActions.opensShop(ReminderKind.SHOP))
        assertTrue(ReminderKind.entries.filter { it != ReminderKind.SHOP }.none { ReminderActions.opensShop(it) })
    }

    @Test fun theKindComesBackFromItsName() {
        for (k in ReminderKind.entries) assertEquals(k, ReminderActions.kindOf(k.name))
        assertNull(ReminderActions.kindOf("NOPE"))
        assertNull(ReminderActions.kindOf(null))
        assertFalse(ReminderActions.kindOf("cards_due") != null)
    }
}
