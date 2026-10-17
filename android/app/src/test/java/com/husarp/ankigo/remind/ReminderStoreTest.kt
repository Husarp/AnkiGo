package com.husarp.ankigo.remind

import com.husarp.ankigo.shots.Shots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderStoreTest {
    @Test fun everyReminderStartsOffWithThePrefilledValues() {
        val d = ReminderPrefs()
        assertFalse(d.dueOn); assertFalse(d.goalOn); assertFalse(d.streakOn); assertFalse(d.dailyGoalOn); assertFalse(d.shopOn)
        assertFalse(d.anyOn)
        assertEquals(20, d.dueMin)
        assertEquals(DueMode.FIXED, d.dueMode)
        assertEquals(18 * 60, d.dueTime)
        assertEquals(2, d.dueEveryHours)
        assertEquals(3, d.minGapHours)
        assertEquals(1, d.dailyCap)
        assertTrue(d.allDecks)
        assertEquals(GoalKind.REVIEWS, d.goalKind)
        assertEquals(100, d.goalCount)
        assertEquals(20, d.copy(goalKind = GoalKind.NEW_CARDS).goalCount)
        assertEquals(20 * 60, d.streakTime)
        assertEquals(19 * 60, d.dailyGoalTime)
        assertTrue(d.quietOn)
        assertEquals(22 * 60, d.quietFrom)
        assertEquals(8 * 60, d.quietTo)
    }

    @Test fun anythingOnMakesTheAlarmNeeded() {
        assertTrue(ReminderPrefs(dueOn = true).anyOn)
        assertTrue(ReminderPrefs(goalOn = true).anyOn)
        assertTrue(ReminderPrefs(streakOn = true).anyOn)
        assertTrue(ReminderPrefs(dailyGoalOn = true).anyOn)
        assertTrue(ReminderPrefs(shopOn = true).anyOn)
        assertFalse(ReminderPrefs(quietOn = false, dueMin = 5).anyOn)
    }

    @Test fun anEmptyFileLoadsTheDefaults() {
        assertEquals(ReminderPrefs(), ReminderStore.load(Shots.MemPrefs()))
    }

    @Test fun roundTrip() {
        val custom = ReminderPrefs(
            dueOn = true, dueMin = 35, dueMode = DueMode.EVERY_HOURS, dueTime = 7 * 60 + 30, dueEveryHours = 4, minGapHours = 5, dailyCap = 2,
            allDecks = false, deckIds = setOf(11L, 4_000_000_000_001L),
            goalOn = true, goalKind = GoalKind.NEW_CARDS, goalReviews = 250, goalNewCards = 45,
            streakOn = true, streakTime = 21 * 60 + 15, dailyGoalOn = true, dailyGoalTime = 17 * 60, shopOn = true,
            quietOn = false, quietFrom = 23 * 60, quietTo = 6 * 60 + 45,
        )
        val p = Shots.MemPrefs()
        ReminderStore.save(p, custom)
        assertEquals(custom, ReminderStore.load(p))
        ReminderStore.save(p, ReminderPrefs())
        assertEquals(ReminderPrefs(), ReminderStore.load(p))
    }

    @Test fun withGoalCountChangesTheCountOfTheChosenKind() {
        val r = ReminderPrefs().withGoalCount(150)
        assertEquals(150, r.goalCount); assertEquals(20, r.copy(goalKind = GoalKind.NEW_CARDS).goalCount)
        val n = ReminderPrefs(goalKind = GoalKind.NEW_CARDS).withGoalCount(30)
        assertEquals(30, n.goalCount); assertEquals(100, n.copy(goalKind = GoalKind.REVIEWS).goalCount)
    }

    @Test fun badValuesAreBroughtIntoRange() {
        val p = Shots.MemPrefs()
        p.edit().putInt("dueMin", -4).putInt("dueTime", 5000).putInt("dueEveryHours", 0).putInt("minGapHours", 99).putInt("dailyCap", 7)
            .putInt("goalReviews", 0).putInt("quietFrom", -1).putString("dueMode", "NOPE").putString("goalKind", null)
            .putStringSet("deckIds", setOf("5", "x", "")).apply()
        val l = ReminderStore.load(p)
        assertEquals(1, l.dueMin); assertEquals(1439, l.dueTime); assertEquals(1, l.dueEveryHours); assertEquals(24, l.minGapHours)
        assertEquals(2, l.dailyCap); assertEquals(1, l.goalReviews); assertEquals(0, l.quietFrom)
        assertEquals(DueMode.FIXED, l.dueMode); assertEquals(GoalKind.REVIEWS, l.goalKind)
        assertEquals(setOf(5L), l.deckIds)
    }

    // ---- the sent log ----

    @Test fun sentLogRoundTripAndPruning() {
        val day = 24L * 3_600_000
        val log = SentLog().with(ReminderKind.CARDS_DUE, 10 * day).with(ReminderKind.SHOP, 12 * day).with(ReminderKind.STREAK, 14 * day)
        assertEquals(listOf(ReminderKind.SHOP, ReminderKind.STREAK), log.entries.map { it.kind }) // the one from 4 days before was dropped
        assertEquals(log, SentLog.decode(log.encode()))
        assertEquals(14 * day, log.last(ReminderKind.STREAK))
        assertNull(log.last(ReminderKind.CARDS_DUE))
        assertNull(log.lastStudy())
        assertEquals(15 * day, log.with(ReminderKind.OWN_GOAL, 15 * day).lastStudy())
    }

    @Test fun sentLogSkipsBadEntries() {
        val l = SentLog.decode("CARDS_DUE:100;;JUNK:5;SHOP:x;STREAK;SHOP:300")
        assertEquals(listOf(ReminderKind.CARDS_DUE to 100L, ReminderKind.SHOP to 300L), l.entries.map { it.kind to it.at })
        assertEquals(SentLog(), SentLog.decode(null))
    }

    @Test fun sentLogIgnoresTheFuture() {
        val l = SentLog().with(ReminderKind.SHOP, 500).with(ReminderKind.STREAK, 900)
        assertEquals(listOf(ReminderKind.SHOP), l.upTo(600).entries.map { it.kind })
        assertTrue(l.upTo(1000) === l || l.upTo(1000) == l)
    }

    // ---- facts file, answer times, permission ----

    @Test fun factsRoundTrip() {
        val f = ReminderFacts(
            day = "2026-10-14", dayStartHour = 5, reviewsToday = 33, dailyGoal = 120, streakDays = 8, lastGoalDay = "2026-10-13",
            shopToday = listOf("poke-ball", "thunder-stone"), shopTomorrow = listOf("great-ball"),
        )
        val p = Shots.MemPrefs()
        ReminderFacts.save(p, f)
        assertEquals(f, ReminderFacts.load(p))
        assertFalse(ReminderFacts.load(Shots.MemPrefs()).valid)
        ReminderFacts.save(p, ReminderFacts.NONE)
        assertEquals(ReminderFacts.NONE, ReminderFacts.load(p))
    }

    @Test fun answerTimesTakeColumnZeroAndSkipJunk() {
        val lines = sequenceOf("1000,12,0,3,5000,app", "2000", "abc,1,2", "", "  3000 ,x", "99999999,1,1", "-5,1,1", "1500,1")
        assertEquals(listOf(1000L, 2000L, 3000L, 1500L), AnswerTimes.parse(lines, 0, 5000))
    }

    @Test fun permissionIsOnlyAskedOnAndroid13Plus() {
        assertFalse(ReminderPermission.mustAsk(32, false))
        assertFalse(ReminderPermission.mustAsk(26, false))
        assertTrue(ReminderPermission.mustAsk(33, false))
        assertTrue(ReminderPermission.mustAsk(35, false))
        assertFalse(ReminderPermission.mustAsk(34, true))
    }
}
