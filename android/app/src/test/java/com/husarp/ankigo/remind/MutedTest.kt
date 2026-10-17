package com.husarp.ankigo.remind

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MutedTest {
    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L

    @Test fun snoozeComesBackAfterAnHour() {
        val m = Muted().snooze(ReminderKind.CARDS_DUE, now)
        assertFalse(m.snoozeReady(ReminderKind.CARDS_DUE, now + hour - 1))
        assertTrue(m.snoozeReady(ReminderKind.CARDS_DUE, now + hour))
        assertTrue(m.snoozeReady(ReminderKind.CARDS_DUE, now + hour + ReminderRules.CATCH_UP_MS))
        assertFalse(m.snoozeReady(ReminderKind.CARDS_DUE, now + hour + ReminderRules.CATCH_UP_MS + 1)) // too late: not made up for
        assertFalse(m.snoozeReady(ReminderKind.OWN_GOAL, now + hour)) // only that kind
        assertEquals(now + hour, m.nextSnooze(now))
        assertNull(m.nextSnooze(now + hour))
        assertEquals(now + 2 * hour, m.snooze(ReminderKind.CARDS_DUE, now + hour).snoozed[ReminderKind.CARDS_DUE]) // a second press moves it
    }

    @Test fun aSnoozeIsHoldingUntilItsTimeAndNotAfter() {
        val m = Muted().snooze(ReminderKind.CARDS_DUE, now)
        assertTrue(m.snoozing(ReminderKind.CARDS_DUE, now))
        assertTrue(m.snoozing(ReminderKind.CARDS_DUE, now + hour - 1))
        assertFalse(m.snoozing(ReminderKind.CARDS_DUE, now + hour)) // its time has come: it is "ready" instead
        assertFalse(m.snoozing(ReminderKind.OWN_GOAL, now))
        assertFalse(Muted().snoozing(ReminderKind.CARDS_DUE, now))
    }

    @Test fun consumeOnlyForgetsTheSnoozesThatWereSeen() {
        val seen = Muted().snooze(ReminderKind.CARDS_DUE, now).snooze(ReminderKind.OWN_GOAL, now)
        assertTrue(seen.consume(listOf(ReminderKind.CARDS_DUE, ReminderKind.OWN_GOAL), seen).snoozed.isEmpty())
        val moved = seen.snooze(ReminderKind.CARDS_DUE, now + hour) // pressed again meanwhile: the time moves to now + 2 h
        assertEquals(setOf(ReminderKind.CARDS_DUE), moved.consume(listOf(ReminderKind.CARDS_DUE, ReminderKind.OWN_GOAL), seen).snoozed.keys)
        assertEquals(now + 2 * hour, moved.consume(listOf(ReminderKind.CARDS_DUE), seen).snoozed[ReminderKind.CARDS_DUE])
        assertSame(moved, moved.consume(emptyList(), seen))
        assertTrue(Muted().consume(listOf(ReminderKind.CARDS_DUE), seen).snoozed.isEmpty()) // gone meanwhile (Not today): stays gone
    }

    @Test fun notTodayCoversTheWholeTopicForThatDayOnly() {
        val m = Muted().snooze(ReminderKind.OWN_GOAL, now).notToday(ReminderKind.CARDS_DUE, "2026-10-14")
        assertTrue(m.silenced(ReminderKind.CARDS_DUE, "2026-10-14"))
        assertTrue(m.silenced(ReminderKind.OWN_GOAL, "2026-10-14")) // the other study reminder too
        assertFalse(m.silenced(ReminderKind.CARDS_DUE, "2026-10-15")) // tomorrow they are back
        assertFalse(m.silenced(ReminderKind.STREAK, "2026-10-14")) // other topics go on
        assertFalse(m.silenced(ReminderKind.SHOP, "2026-10-14"))
        assertTrue(m.snoozed.isEmpty()) // a snooze of the topic is forgotten
        val g = Muted().notToday(ReminderKind.STREAK, "2026-10-14")
        assertTrue(g.silenced(ReminderKind.DAILY_GOAL, "2026-10-14"))
        assertFalse(g.silenced(ReminderKind.CARDS_DUE, "2026-10-14"))
    }

    @Test fun usedSnoozesAndOldMarksAreTidiedAway() {
        val m = Muted().snooze(ReminderKind.CARDS_DUE, now).snooze(ReminderKind.OWN_GOAL, now).notToday(ReminderKind.STREAK, "2026-10-13")
        assertEquals(setOf(ReminderKind.OWN_GOAL), m.without(listOf(ReminderKind.CARDS_DUE)).snoozed.keys)
        assertSame(m, m.without(emptyList()))
        val later = m.tidy(now + hour + ReminderRules.CATCH_UP_MS + 1, "2026-10-14")
        assertTrue(later.snoozed.isEmpty())
        assertTrue(later.offDay.isEmpty()) // yesterday's "Not today"
        assertEquals(m.snoozed, m.tidy(now + 2 * hour, "2026-10-13").snoozed)
        assertTrue(m.tidy(now, "2026-10-13").silenced(ReminderKind.STREAK, "2026-10-13"))
    }

    private fun assertSame(a: Any, b: Any) = assertTrue(a === b)

    @Test fun roundTripAndBadInput() {
        val m = Muted().snooze(ReminderKind.CARDS_DUE, now).notToday(ReminderKind.STREAK, "2026-10-14")
        assertEquals(m, Muted.decode(m.encode()))
        assertEquals(Muted(), Muted.decode(null))
        assertEquals(Muted(), Muted.decode(""))
        assertEquals(Muted(), Muted.decode("garbage;S:NOPE:5;S:CARDS_DUE:abc;O:STREAK:;X:SHOP:1;S:SHOP"))
        assertEquals(1, Muted.decode("S:SHOP:12;nonsense").snoozed.size)
    }
}
