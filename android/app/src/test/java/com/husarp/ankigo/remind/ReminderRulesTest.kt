package com.husarp.ankigo.remind

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderRulesTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private fun t(s: String) = LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()

    /** Cards-due reminder on, fixed 18:00, 20 cards, no quiet hours unless a test asks. */
    private val dueOn = ReminderPrefs(dueOn = true, quietOn = false)
    private fun deck(id: Long, name: String, learn: Int = 0, review: Int = 0) = DueDeck(id, name, learn, review)
    private fun snap(vararg d: DueDeck, reviewed: Int? = null, newToday: Int? = null) = DueSnapshot(d.toList(), reviewed, newToday)
    private val thirty = snap(deck(1, "Spanish", learn = 5, review = 25))

    private fun decide(
        now: String, prefs: ReminderPrefs, due: DueSnapshot? = thirty, log: SentLog = SentLog(),
        facts: ReminderFacts = ReminderFacts(), pick: Int = ReminderRules.DEFAULT_PICK_MINUTE,
    ) = ReminderRules.decide(t(now), zone, prefs, facts, { due }, log, pick)

    private fun kinds(l: List<Notice>) = l.map { it.kind }

    // ---- everything off ----

    @Test fun everythingOffSendsNothingAndNeverAsksAnkiDroid() {
        var asked = 0
        val out = ReminderRules.decide(t("2026-10-14T18:30:00"), zone, ReminderPrefs(), ReminderFacts(), { asked++; thirty }, SentLog())
        assertTrue(out.isEmpty())
        assertEquals(0, asked)
        assertFalse(ReminderPrefs().anyOn)
    }

    // ---- cards due: fixed time ----

    @Test fun fixedFiresOnceTheTimeHasPassed() {
        assertTrue(decide("2026-10-14T17:59:00", dueOn).isEmpty())
        val out = decide("2026-10-14T18:00:00", dueOn)
        assertEquals(listOf(ReminderKind.CARDS_DUE), kinds(out))
        assertEquals(30, out[0].dueCount)
        assertEquals("30 cards waiting", out[0].title)
        assertEquals(listOf("Spanish — 30"), out[0].lines)
        assertEquals(listOf("Spanish"), out[0].deckNames)
        assertEquals(1, decide("2026-10-14T19:40:00", dueOn).size) // a late tick still counts
        assertTrue(decide("2026-10-14T21:01:00", dueOn).isEmpty()) // more than 3 hours late: skipped
    }

    @Test fun fixedNeedsNothingSentToday() {
        val log = SentLog().with(ReminderKind.CARDS_DUE, t("2026-10-14T18:05:00"))
        assertTrue(decide("2026-10-14T18:30:00", dueOn.copy(minGapHours = 0, dailyCap = 2), log = log).isEmpty())
        assertEquals(1, decide("2026-10-15T18:00:00", dueOn, log = log).size) // the next day it fires again
    }

    @Test fun belowTheMinimumNothingIsSentAndItIsCheckedAgainLater() {
        val few = snap(deck(1, "Spanish", review = 19))
        assertTrue(decide("2026-10-14T18:00:00", dueOn, due = few).isEmpty())
        assertEquals(1, decide("2026-10-14T18:30:00", dueOn, due = thirty).size)
        assertEquals(1, decide("2026-10-14T18:30:00", dueOn, due = snap(deck(1, "Spanish", review = 20))).size) // exactly N counts
    }

    @Test fun ankiDroidNotReachableSkipsQuietly() {
        assertTrue(decide("2026-10-14T18:00:00", dueOn, due = null).isEmpty())
    }

    // ---- cards due: app picks and every X hours ----

    @Test fun appPicksUsesThePickedMinute() {
        val p = dueOn.copy(dueMode = DueMode.APP_PICKS)
        assertTrue(decide("2026-10-14T18:00:00", p, pick = 21 * 60).isEmpty())
        assertEquals(1, decide("2026-10-14T21:00:00", p, pick = 21 * 60).size)
        assertEquals(1, decide("2026-10-14T18:00:00", p).size) // default pick 18:00
    }

    @Test fun everyHoursChecksWheneverTheIntervalPassed() {
        val p = dueOn.copy(dueMode = DueMode.EVERY_HOURS, dueEveryHours = 2, minGapHours = 0, dailyCap = 2)
        assertEquals(1, decide("2026-10-14T09:00:00", p).size) // the first check of the day
        val log = SentLog().with(ReminderKind.CARDS_DUE, t("2026-10-14T09:00:00"))
        assertTrue(decide("2026-10-14T10:59:00", p, log = log).isEmpty())
        assertEquals(1, decide("2026-10-14T11:00:00", p, log = log).size)
        assertTrue(decide("2026-10-14T11:00:00", p, due = snap(deck(1, "S", review = 3)), log = log).isEmpty()) // fewer than N left
    }

    // ---- min gap and daily cap ----

    @Test fun minimumGapBetweenStudyReminders() {
        val p = dueOn.copy(dueMode = DueMode.EVERY_HOURS, dueEveryHours = 1, minGapHours = 3, dailyCap = 2)
        val log = SentLog().with(ReminderKind.CARDS_DUE, t("2026-10-14T09:00:00"))
        assertTrue(decide("2026-10-14T11:59:00", p, log = log).isEmpty())
        assertEquals(1, decide("2026-10-14T12:00:00", p, log = log).size)
    }

    @Test fun dailyCapOneAndTwo() {
        val base = dueOn.copy(dueMode = DueMode.EVERY_HOURS, dueEveryHours = 1, minGapHours = 0)
        val one = SentLog().with(ReminderKind.CARDS_DUE, t("2026-10-14T09:00:00"))
        assertTrue(decide("2026-10-14T12:00:00", base.copy(dailyCap = 1), log = one).isEmpty())
        assertEquals(1, decide("2026-10-14T12:00:00", base.copy(dailyCap = 2), log = one).size)
        val two = one.with(ReminderKind.CARDS_DUE, t("2026-10-14T12:00:00"))
        assertTrue(decide("2026-10-14T15:00:00", base.copy(dailyCap = 2), log = two).isEmpty())
        assertEquals(1, decide("2026-10-15T09:00:00", base.copy(dailyCap = 2), log = two).size) // a new game day
    }

    @Test fun theCapCountsTheGameDayNotTheCalendarDay() {
        val base = dueOn.copy(dueMode = DueMode.EVERY_HOURS, dueEveryHours = 1, minGapHours = 0, dailyCap = 1)
        // 01:00 on the 15th is still game day the 14th (day starts at 4)
        val log = SentLog().with(ReminderKind.CARDS_DUE, t("2026-10-14T23:30:00"))
        assertTrue(decide("2026-10-15T03:30:00", base, log = log).isEmpty())
        assertEquals(1, decide("2026-10-15T04:30:00", base, log = log).size)
    }

    @Test fun cardsDueAndOwnGoalShareTheGapAndTheCap() {
        val p = dueOn.copy(goalOn = true, dueMode = DueMode.EVERY_HOURS, dueEveryHours = 1, minGapHours = 3, dailyCap = 2)
        val both = snap(deck(1, "Spanish", review = 30), reviewed = 10)
        // one tick: only one of the two (the gap between them would be 0)
        assertEquals(listOf(ReminderKind.CARDS_DUE), kinds(decide("2026-10-14T09:00:00", p, due = both)))
        // the own goal alone is held back by the cards-due reminder sent two hours ago
        val log = SentLog().with(ReminderKind.CARDS_DUE, t("2026-10-14T09:00:00"))
        val goalOnly = p.copy(dueOn = false)
        assertTrue(decide("2026-10-14T11:00:00", goalOnly, due = both, log = log).isEmpty())
        assertEquals(listOf(ReminderKind.OWN_GOAL), kinds(decide("2026-10-14T12:00:00", goalOnly, due = both, log = log)))
        // and the cap of 1 is used up by whichever came first
        assertTrue(decide("2026-10-14T12:00:00", goalOnly.copy(dailyCap = 1), due = both, log = log).isEmpty())
    }

    // ---- decks ----

    private val tree = listOf(
        deck(1, "Spanish", learn = 5, review = 25),          // 30, includes the two below
        deck(2, "Spanish::Verbs", review = 10),
        deck(3, "Spanish::Verbs::Irregular", review = 4),
        deck(4, "Medicine", review = 8, learn = 2),           // 10
        deck(5, "Default"),
    )

    @Test fun allDecksCountsTopLevelDecksOnly() {
        val counted = ReminderRules.countedDecks(ReminderPrefs(), tree)
        assertEquals(listOf(1L, 4L, 5L), counted.map { it.id })
        assertEquals(40, counted.sumOf { it.due })
    }

    @Test fun chosenDecksAreNotCountedTwice() {
        fun sum(vararg ids: Long) = ReminderRules.countedDecks(ReminderPrefs(allDecks = false, deckIds = ids.toSet()), tree).sumOf { it.due }
        assertEquals(30, sum(1))
        assertEquals(30, sum(1, 2, 3)) // the children are inside Spanish
        assertEquals(10, sum(2)) // a chosen child alone: its own count (includes its child)
        assertEquals(10 + 10, sum(2, 4))
        assertEquals(0, sum())
        assertEquals(0, sum(99))
    }

    @Test fun aDeckWhoseParentIsMissingCountsAsTopLevel() {
        val lone = listOf(deck(7, "Gone::Child", review = 6))
        assertEquals(6, ReminderRules.countedDecks(ReminderPrefs(), lone).sumOf { it.due })
    }

    @Test fun newCardsAreNotDueAndNegativeCountsAreZero() {
        assertEquals(0, deck(1, "A", learn = -1, review = -1).due)
        val out = decide("2026-10-14T18:00:00", dueOn, due = snap(deck(1, "A", learn = 12, review = 9)))
        assertEquals(21, out[0].dueCount)
    }

    @Test fun noticeNamesTheBiggestDecksFirst() {
        val many = snap(deck(1, "A", review = 5), deck(2, "B::Sub", review = 9), deck(3, "B", review = 9), deck(4, "C", review = 7), deck(5, "D", review = 1))
        val n = decide("2026-10-14T18:00:00", dueOn, due = many)[0]
        assertEquals(listOf("B", "C", "A", "D"), n.deckNames)
        assertEquals(listOf("B — 9", "C — 7", "A — 5", "and 1 more deck"), n.lines)
    }

    // ---- own goal ----

    @Test fun ownGoalKeepsRemindingUntilReached() {
        val p = ReminderPrefs(goalOn = true, quietOn = false, dueEveryHours = 2, minGapHours = 0, dailyCap = 2)
        val out = decide("2026-10-14T09:00:00", p, due = snap(reviewed = 37))
        assertEquals(listOf(ReminderKind.OWN_GOAL), kinds(out))
        assertEquals("63 reviews to your goal", out[0].title)
        assertEquals(listOf("37 / 100 reviews today"), out[0].lines)
        assertTrue(out[0].showBar)
        assertEquals(37, out[0].goalDone); assertEquals(100, out[0].goalTarget)
        val log = SentLog().with(ReminderKind.OWN_GOAL, t("2026-10-14T09:00:00"))
        assertTrue(decide("2026-10-14T10:30:00", p, due = snap(reviewed = 50), log = log).isEmpty()) // 2 h not over
        assertEquals(1, decide("2026-10-14T11:00:00", p, due = snap(reviewed = 50), log = log).size) // keeps going
        assertTrue(decide("2026-10-14T11:00:00", p, due = snap(reviewed = 100), log = log).isEmpty()) // reached: stops
        assertTrue(decide("2026-10-14T11:00:00", p, due = snap(reviewed = 140), log = log).isEmpty())
    }

    @Test fun ownGoalCanCountNewCards() {
        val p = ReminderPrefs(goalOn = true, goalKind = GoalKind.NEW_CARDS, quietOn = false)
        val out = decide("2026-10-14T09:00:00", p, due = snap(reviewed = 500, newToday = 12))
        assertEquals("8 new cards to your goal", out[0].title)
        assertEquals(listOf("12 / 20 new cards today"), out[0].lines)
        assertTrue(out[0].newCards)
        assertTrue(decide("2026-10-14T09:00:00", p, due = snap(reviewed = 0, newToday = 20)).isEmpty())
        assertTrue(decide("2026-10-14T09:00:00", p, due = snap(reviewed = 5, newToday = null)).isEmpty()) // could not count
    }

    // ---- streak ----

    private val streakFacts = ReminderFacts(day = "2026-10-14", reviewsToday = 10, dailyGoal = 50, streakDays = 6, lastGoalDay = "2026-10-13")
    private val streakOn = ReminderPrefs(streakOn = true, quietOn = false)

    @Test fun streakWarnsAtItsTimeWhenTheGoalWasReachedYesterdayButNotToday() {
        assertTrue(decide("2026-10-14T19:59:00", streakOn, facts = streakFacts).isEmpty())
        val out = decide("2026-10-14T20:00:00", streakOn, facts = streakFacts)
        assertEquals(listOf(ReminderKind.STREAK), kinds(out))
        assertEquals("Your 6-day streak ends tonight", out[0].title)
        assertEquals(listOf("40 reviews keep it going."), out[0].lines)
        assertFalse(out[0].showBar)
        assertEquals(6, out[0].streakDays)
        assertEquals(10, out[0].goalDone); assertEquals(50, out[0].goalTarget)
    }

    @Test fun streakOnceADayAndOnlyWhileItCanStillBeSaved() {
        val log = SentLog().with(ReminderKind.STREAK, t("2026-10-14T20:00:00"))
        assertTrue(decide("2026-10-14T20:30:00", streakOn, facts = streakFacts, log = log).isEmpty())
        assertTrue(decide("2026-10-14T20:00:00", streakOn, facts = streakFacts.copy(lastGoalDay = "2026-10-14")).isEmpty()) // reached today
        assertTrue(decide("2026-10-14T20:00:00", streakOn, facts = streakFacts.copy(reviewsToday = 50)).isEmpty())
        assertTrue(decide("2026-10-14T20:00:00", streakOn, facts = streakFacts.copy(streakDays = 0)).isEmpty())
        assertTrue(decide("2026-10-14T20:00:00", streakOn, facts = streakFacts.copy(lastGoalDay = "2026-10-12")).isEmpty()) // already broken
        assertTrue(decide("2026-10-14T20:00:00", streakOn, facts = ReminderFacts()).isEmpty()) // no game facts yet
    }

    @Test fun streakUsesTheGameDayWithItsStartHour() {
        val p = streakOn.copy(streakTime = 2 * 60) // 02:00 belongs to the game day that began the evening before
        // 02:10 on the 15th is still game day the 14th: yesterday (13th) reached, 14th is today
        assertEquals(1, decide("2026-10-15T02:10:00", p, facts = streakFacts).size)
        // the facts are from the 14th; at 05:00 on the 15th it is game day 15, nothing counted yet there, goal reached on the 14th = yesterday
        val stale = streakFacts.copy(lastGoalDay = "2026-10-14", reviewsToday = 70)
        val out = decide("2026-10-15T20:00:00", streakOn, facts = stale)
        assertEquals(1, out.size)
        assertEquals(0, out[0].goalDone) // today's count restarted
        // a different start hour moves the day: start 0 makes 02:10 on the 15th the 15th
        assertTrue(decide("2026-10-15T02:10:00", p, facts = streakFacts.copy(dayStartHour = 0)).isEmpty())
    }

    // ---- daily goal ----

    @Test fun dailyGoalReminderAtItsTimeOncePerDay() {
        val p = ReminderPrefs(dailyGoalOn = true, quietOn = false)
        assertTrue(decide("2026-10-14T18:59:00", p, facts = streakFacts).isEmpty())
        val out = decide("2026-10-14T19:00:00", p, facts = streakFacts)
        assertEquals(listOf(ReminderKind.DAILY_GOAL), kinds(out))
        assertEquals("40 reviews to today's goal", out[0].title)
        assertEquals(listOf("Daily goal 10 / 50 · 6-day streak"), out[0].lines)
        assertTrue(out[0].showBar)
        val log = SentLog().with(ReminderKind.DAILY_GOAL, t("2026-10-14T19:00:00"))
        assertTrue(decide("2026-10-14T19:30:00", p, facts = streakFacts, log = log).isEmpty())
        assertTrue(decide("2026-10-14T19:00:00", p, facts = streakFacts.copy(reviewsToday = 50)).isEmpty())
        assertTrue(decide("2026-10-14T19:00:00", p, facts = streakFacts.copy(lastGoalDay = "2026-10-14", reviewsToday = 10)).isEmpty())
        assertEquals(1, decide("2026-10-15T19:00:00", p, facts = streakFacts.copy(day = "2026-10-15"), log = log).size)
    }

    // ---- shop ----

    private val shopFacts = ReminderFacts(day = "2026-10-14", shopToday = listOf("poke-ball", "thunder-stone"), shopTomorrow = listOf("great-ball"))
    private val shopOn = ReminderPrefs(shopOn = true, quietOn = false)

    @Test fun shopNewsOncePerGameDayWhenSomethingIsWorthHaving() {
        val out = decide("2026-10-14T09:00:00", shopOn, facts = shopFacts)
        assertEquals(listOf(ReminderKind.SHOP), kinds(out))
        assertEquals(listOf("Poké Ball", "Thunder Stone"), out[0].itemNames)
        assertEquals("New in the shop", out[0].title)
        assertEquals(listOf("Poké Ball, Thunder Stone"), out[0].lines)
        val log = SentLog().with(ReminderKind.SHOP, t("2026-10-14T09:00:00"))
        assertTrue(decide("2026-10-14T15:00:00", shopOn, facts = shopFacts, log = log).isEmpty())
        assertTrue(decide("2026-10-14T09:00:00", shopOn.copy(shopOn = false), facts = shopFacts).isEmpty()) // off
        assertTrue(decide("2026-10-14T09:00:00", shopOn, facts = shopFacts.copy(shopToday = emptyList())).isEmpty())
    }

    @Test fun shopUsesTomorrowsStockWhenTheAppWasNotOpenedSinceYesterday() {
        val out = decide("2026-10-15T09:00:00", shopOn, facts = shopFacts)
        assertEquals(listOf("Great Ball"), out[0].itemNames)
        assertTrue(decide("2026-10-16T09:00:00", shopOn, facts = shopFacts).isEmpty()) // two days old: unknown
        assertTrue(decide("2026-10-14T09:00:00", shopOn, facts = ReminderFacts()).isEmpty())
    }

    @Test fun shopWaitsForTheQuietHoursToEnd() {
        val p = shopOn.copy(quietOn = true)
        assertTrue(decide("2026-10-15T05:00:00", p, facts = shopFacts).isEmpty()) // game day 15 began at 04:00, quiet until 08:00
        assertEquals(1, decide("2026-10-15T08:00:00", p, facts = shopFacts).size)
    }

    // ---- quiet hours ----

    @Test fun quietHoursWrapOverMidnight() {
        val p = ReminderPrefs(quietOn = true, quietFrom = 22 * 60, quietTo = 8 * 60)
        fun q(s: String) = ReminderRules.inQuiet(p, t(s), zone)
        assertTrue(q("2026-10-14T22:00:00")); assertTrue(q("2026-10-14T23:59:00")); assertTrue(q("2026-10-15T00:00:00"))
        assertTrue(q("2026-10-15T07:59:00"))
        assertFalse(q("2026-10-15T08:00:00")); assertFalse(q("2026-10-14T21:59:00")); assertFalse(q("2026-10-14T12:00:00"))
        assertFalse(ReminderRules.inQuiet(p.copy(quietOn = false), t("2026-10-14T23:00:00"), zone))
        assertFalse(ReminderRules.inQuiet(p.copy(quietFrom = 600, quietTo = 600), t("2026-10-14T10:00:00"), zone)) // empty range
    }

    @Test fun quietHoursWithoutWrap() {
        val p = ReminderPrefs(quietOn = true, quietFrom = 13 * 60, quietTo = 15 * 60)
        assertTrue(ReminderRules.inQuiet(p, t("2026-10-14T13:00:00"), zone))
        assertTrue(ReminderRules.inQuiet(p, t("2026-10-14T14:59:00"), zone))
        assertFalse(ReminderRules.inQuiet(p, t("2026-10-14T15:00:00"), zone))
        assertFalse(ReminderRules.inQuiet(p, t("2026-10-14T12:59:00"), zone))
    }

    @Test fun nothingIsPostedInsideTheQuietHours() {
        val p = ReminderPrefs(dueOn = true, dueTime = 23 * 60, quietOn = true) // the reminder time itself is in the quiet hours
        assertTrue(decide("2026-10-14T23:05:00", p).isEmpty())
        // skipped, not queued: when the quiet hours end the reminder is not made up for (the next morning is a new game day, and 23:00 is not yet there)
        assertTrue(decide("2026-10-15T08:00:00", p).isEmpty())
        val late = ReminderPrefs(dueOn = true, dueTime = 7 * 60, quietOn = true) // 07:00 in the quiet hours; at 08:00 it must not fire late
        assertTrue(decide("2026-10-15T07:00:00", late).isEmpty())
        assertTrue(decide("2026-10-15T08:00:00", late).isEmpty())
    }

    @Test fun aTimeJustBeforeTheQuietHoursIsKeptWhileItIsStillOpen() {
        val p = ReminderPrefs(dueOn = true, dueTime = 21 * 60 + 50, quietOn = true)
        assertEquals(1, decide("2026-10-14T21:55:00", p).size)
        assertTrue(decide("2026-10-14T22:10:00", p).isEmpty()) // it came due, but now it is quiet: not queued
    }

    @Test fun neverTwoNoticesOfTheSameKindInOneTick() {
        val all = ReminderPrefs(
            dueOn = true, goalOn = true, streakOn = true, dailyGoalOn = true, shopOn = true, quietOn = false,
            dueTime = 12 * 60, streakTime = 12 * 60, dailyGoalTime = 12 * 60, dailyCap = 2, minGapHours = 0,
        )
        val d = snap(deck(1, "Spanish", review = 40), reviewed = 3)
        val f = streakFacts.copy(shopToday = listOf("poke-ball"))
        val out = decide("2026-10-14T12:00:00", all, due = d, facts = f)
        assertEquals(out.map { it.kind }.toSet().size, out.size)
        assertEquals(
            listOf(ReminderKind.CARDS_DUE, ReminderKind.OWN_GOAL, ReminderKind.STREAK, ReminderKind.DAILY_GOAL, ReminderKind.SHOP), kinds(out),
        )
    }

    // ---- AnkiDroid is only asked when needed ----

    @Test fun ankiDroidIsAskedOnlyWhenAStudyReminderIsReadyAndOnlyOnce() {
        var asked = 0
        val p = dueOn.copy(goalOn = true, dueEveryHours = 1, minGapHours = 0, dailyCap = 2)
        // fixed time not reached yet; the own goal is ready though (no earlier send): one question for both
        ReminderRules.decide(t("2026-10-14T10:00:00"), zone, p, ReminderFacts(), { asked++; thirty }, SentLog())
        assertEquals(1, asked)
        asked = 0
        // nothing ready: the own goal waited for 1 h cadence, cards due is before its time
        val log = SentLog().with(ReminderKind.OWN_GOAL, t("2026-10-14T09:45:00"))
        ReminderRules.decide(t("2026-10-14T10:00:00"), zone, p, ReminderFacts(), { asked++; thirty }, log)
        assertEquals(0, asked)
        // study cap used up: not asked even when the time is right
        asked = 0
        val used = SentLog().with(ReminderKind.OWN_GOAL, t("2026-10-14T08:00:00")).with(ReminderKind.CARDS_DUE, t("2026-10-14T08:05:00"))
        ReminderRules.decide(t("2026-10-14T18:00:00"), zone, p, ReminderFacts(), { asked++; thirty }, used)
        assertEquals(0, asked)
    }

    @Test fun aSentLogFromTheFutureBlocksNothing() {
        val future = SentLog().with(ReminderKind.CARDS_DUE, t("2026-10-14T19:00:00")) // the clock was set back
        assertEquals(1, decide("2026-10-14T18:00:00", dueOn, log = future).size)
    }

    // ---- days and times ----

    @Test fun timeInGameDayPutsEarlyTimesOnTheNextCalendarDay() {
        val day = LocalDate.parse("2026-10-14")
        assertEquals(t("2026-10-14T18:00:00"), ReminderRules.timeInGameDay(day, 18 * 60, 4, zone))
        assertEquals(t("2026-10-14T04:00:00"), ReminderRules.timeInGameDay(day, 4 * 60, 4, zone))
        assertEquals(t("2026-10-15T02:30:00"), ReminderRules.timeInGameDay(day, 2 * 60 + 30, 4, zone))
        assertEquals(LocalDate.parse("2026-10-14"), ReminderRules.gameDate(t("2026-10-15T03:59:00"), zone, 4))
        assertEquals(LocalDate.parse("2026-10-15"), ReminderRules.gameDate(t("2026-10-15T04:00:00"), zone, 4))
    }

    // ---- app picks ----

    private fun answers(hour: Int, n: Int, day: String = "2026-10-10") = (0 until n).map { t("${day}T%02d:%02d:00".format(hour, it % 60)) }

    @Test fun appPicksTheBusiestHour() {
        val now = t("2026-10-14T12:00:00")
        assertEquals(21 * 60, ReminderRules.appPickedMinute(answers(21, 12) + answers(8, 5) + answers(13, 3), now, zone))
    }

    @Test fun appPicksTheDefaultWithTooFewAnswers() {
        val now = t("2026-10-14T12:00:00")
        assertEquals(ReminderRules.DEFAULT_PICK_MINUTE, ReminderRules.appPickedMinute(answers(21, 4), now, zone))
        assertEquals(21 * 60, ReminderRules.appPickedMinute(answers(21, 5), now, zone))
        assertEquals(ReminderRules.DEFAULT_PICK_MINUTE, ReminderRules.appPickedMinute(emptyList(), now, zone))
    }

    @Test fun appPicksIgnoresOldAndFutureAnswersAndBreaksTiesToTheEarlierHour() {
        val now = t("2026-10-14T12:00:00")
        val old = answers(7, 30, day = "2026-09-20") // older than 14 days
        val future = answers(23, 30, day = "2026-10-20")
        assertEquals(ReminderRules.DEFAULT_PICK_MINUTE, ReminderRules.appPickedMinute(old + future, now, zone))
        assertEquals(9 * 60, ReminderRules.appPickedMinute(old + answers(9, 6) + answers(20, 6), now, zone))
    }

    // ---- the alarm ----

    @Test fun noAlarmWhenNothingIsOn() {
        assertNull(ReminderRules.nextDelayMs(t("2026-10-14T12:00:00"), zone, ReminderPrefs()))
    }

    @Test fun alarmIsAtMostFifteenMinutesAway() {
        val tick = ReminderRules.TICK_MS
        assertEquals(tick, ReminderRules.nextDelayMs(t("2026-10-14T12:00:00"), zone, ReminderPrefs(shopOn = true)))
        assertEquals(tick, ReminderRules.nextDelayMs(t("2026-10-14T12:00:00"), zone, ReminderPrefs(dueOn = true, dueMode = DueMode.EVERY_HOURS)))
    }

    @Test fun alarmComesEarlierForTheNextFixedTime() {
        val p = ReminderPrefs(dueOn = true) // fixed 18:00
        assertEquals(10 * 60_000L, ReminderRules.nextDelayMs(t("2026-10-14T17:50:00"), zone, p))
        assertEquals(30_000L, ReminderRules.nextDelayMs(t("2026-10-14T17:59:50"), zone, p)) // never under 30 s
        assertEquals(ReminderRules.TICK_MS, ReminderRules.nextDelayMs(t("2026-10-14T18:00:10"), zone, p)) // next is tomorrow
        assertEquals(5 * 60_000L, ReminderRules.nextDelayMs(t("2026-10-14T19:55:00"), zone, ReminderPrefs(streakOn = true))) // 20:00
        assertEquals(2 * 60_000L, ReminderRules.nextDelayMs(t("2026-10-14T18:58:00"), zone, ReminderPrefs(dailyGoalOn = true))) // 19:00
        assertEquals(3 * 60_000L, ReminderRules.nextDelayMs(t("2026-10-14T20:57:00"), zone, ReminderPrefs(dueOn = true, dueMode = DueMode.APP_PICKS), 21 * 60))
    }

    @Test fun alarmReachesTomorrowsTimeAcrossMidnight() {
        val p = ReminderPrefs(dueOn = true, dueTime = 0) // 00:00
        assertEquals(5 * 60_000L, ReminderRules.nextDelayMs(t("2026-10-14T23:55:00"), zone, p))
    }

    // ---- the notification buttons: In 1 hour, Not today ----

    private fun decideMuted(now: String, prefs: ReminderPrefs, muted: Muted, due: DueSnapshot? = thirty, log: SentLog = SentLog(), facts: ReminderFacts = ReminderFacts()) =
        ReminderRules.decide(t(now), zone, prefs, facts, { due }, log, ReminderRules.DEFAULT_PICK_MINUTE, muted)

    @Test fun aSnoozedStudyReminderComesBackAnHourLaterWhateverTheTimeOrTheCap() {
        val sentAt = "2026-10-14T18:00:00"
        val log = SentLog().with(ReminderKind.CARDS_DUE, t(sentAt))
        val muted = Muted().snooze(ReminderKind.CARDS_DUE, t("2026-10-14T18:05:00")) // pressed "In 1 hour" at 18:05
        // fixed time already used today, cap 1: without the snooze nothing would come
        assertTrue(decideMuted("2026-10-14T19:06:00", dueOn, Muted(), log = log).isEmpty())
        assertTrue(decideMuted("2026-10-14T19:04:00", dueOn, muted, log = log).isEmpty()) // not yet
        val out = decideMuted("2026-10-14T19:05:00", dueOn, muted, log = log)
        assertEquals(listOf(ReminderKind.CARDS_DUE), kinds(out))
        assertEquals(1, decideMuted("2026-10-14T21:30:00", dueOn, muted, log = log).size) // a late tick (Doze) still counts
        assertTrue(decideMuted("2026-10-14T22:30:00", dueOn.copy(quietOn = false), muted, log = log).isEmpty()) // over 3 h late: dropped
    }

    @Test fun aSnoozedReminderStillNeedsEnoughCardsAndRespectsTheQuietHours() {
        val muted = Muted().snooze(ReminderKind.CARDS_DUE, t("2026-10-14T17:00:00"))
        val few = snap(deck(1, "Spanish", review = 5))
        assertTrue(decideMuted("2026-10-14T18:00:00", dueOn, muted, due = few).isEmpty()) // fewer than the minimum now
        assertTrue(decideMuted("2026-10-14T18:00:00", dueOn.copy(quietOn = true, quietFrom = 17 * 60 + 30, quietTo = 19 * 60), muted).isEmpty())
        assertEquals(1, decideMuted("2026-10-14T18:00:00", dueOn, muted).size)
    }

    @Test fun aSnoozedOwnGoalComesBackAndKeepsTheCountRule() {
        val p = ReminderPrefs(goalOn = true, quietOn = false, dueEveryHours = 6)
        val log = SentLog().with(ReminderKind.OWN_GOAL, t("2026-10-14T10:00:00"))
        val muted = Muted().snooze(ReminderKind.OWN_GOAL, t("2026-10-14T10:05:00"))
        assertTrue(decideMuted("2026-10-14T11:00:00", p, Muted(), due = snap(reviewed = 30), log = log).isEmpty()) // 6 h cadence
        assertEquals(listOf(ReminderKind.OWN_GOAL), kinds(decideMuted("2026-10-14T11:05:00", p, muted, due = snap(reviewed = 30), log = log)))
        assertTrue(decideMuted("2026-10-14T11:05:00", p, muted, due = snap(reviewed = 100), log = log).isEmpty()) // goal reached meanwhile
    }

    @Test fun notTodayStopsTheStudyRemindersForThatGameDayOnly() {
        val every = dueOn.copy(goalOn = true, dueMode = DueMode.EVERY_HOURS, dueEveryHours = 1, minGapHours = 0, dailyCap = 2)
        val d = snap(deck(1, "Spanish", review = 30), reviewed = 3)
        val muted = Muted().notToday(ReminderKind.CARDS_DUE, "2026-10-14")
        assertTrue(decideMuted("2026-10-14T12:00:00", every, muted, due = d).isEmpty()) // both study reminders silent
        assertEquals(2, decideMuted("2026-10-15T12:00:00", every, muted, due = d).size) // next game day: back
        // 02:00 on the 15th is still game day the 14th
        assertTrue(decideMuted("2026-10-15T02:00:00", every.copy(quietOn = false), muted, due = d).isEmpty())
        // a snooze set before is overridden by "Not today"
        val both = Muted().snooze(ReminderKind.CARDS_DUE, t("2026-10-14T10:00:00")).notToday(ReminderKind.CARDS_DUE, "2026-10-14")
        assertTrue(decideMuted("2026-10-14T12:00:00", dueOn, both).isEmpty())
    }

    @Test fun notTodayOnTheStreakNoticeSilencesTheGoalNoticesToo() {
        val p = ReminderPrefs(streakOn = true, dailyGoalOn = true, quietOn = false, streakTime = 19 * 60)
        val muted = Muted().notToday(ReminderKind.STREAK, "2026-10-14")
        assertTrue(decideMuted("2026-10-14T19:00:00", p, muted, facts = streakFacts).isEmpty())
        assertEquals(2, decideMuted("2026-10-14T19:00:00", p, Muted(), facts = streakFacts).size)
        // the study reminders are another topic
        assertEquals(1, decideMuted("2026-10-14T18:00:00", dueOn, muted).size)
    }

    @Test fun aKindThatIsSnoozedCannotFireTheNormalWayBeforeItsHour() {
        // "every 1 h", gap 0: without a snooze this kind is due again right away
        val every = dueOn.copy(dueMode = DueMode.EVERY_HOURS, dueEveryHours = 1, minGapHours = 0, dailyCap = 9, goalOn = true)
        val d = snap(deck(1, "Spanish", review = 30), reviewed = 3)
        val log = SentLog().with(ReminderKind.CARDS_DUE, t("2026-10-14T08:00:00")).with(ReminderKind.OWN_GOAL, t("2026-10-14T08:00:00"))
        assertEquals(setOf(ReminderKind.CARDS_DUE, ReminderKind.OWN_GOAL), kinds(decideMuted("2026-10-14T12:00:00", every, Muted(), due = d, log = log)).toSet())
        // "In 1 hour" pressed at 11:50 on an older notification: both ordinary cadences are over, but the deadline (12:50) is still ahead
        val muted = Muted().snooze(ReminderKind.CARDS_DUE, t("2026-10-14T11:50:00"))
        assertEquals(listOf(ReminderKind.OWN_GOAL), kinds(decideMuted("2026-10-14T12:00:00", every, muted, due = d, log = log))) // only the snoozed kind waits
        val own = Muted().snooze(ReminderKind.OWN_GOAL, t("2026-10-14T11:50:00"))
        assertEquals(listOf(ReminderKind.CARDS_DUE), kinds(decideMuted("2026-10-14T12:00:00", every, own, due = d, log = log)))
        // at the deadline it comes back (the snoozed path), and a fixed-time reminder can't slip through before it either
        assertEquals(listOf(ReminderKind.CARDS_DUE, ReminderKind.OWN_GOAL), kinds(decideMuted("2026-10-14T12:50:00", every, muted, due = d, log = log)))
        assertTrue(decideMuted("2026-10-14T18:30:00", dueOn, Muted().snooze(ReminderKind.CARDS_DUE, t("2026-10-14T18:10:00"))).isEmpty())
        assertEquals(1, decideMuted("2026-10-14T18:30:00", dueOn, Muted().snooze(ReminderKind.CARDS_DUE, t("2026-10-14T17:10:00"))).size) // 18:10: hour is over
    }

    @Test fun aButtonPressedWhileTheTickWasReadingAnkiDroidWins() {
        val decidedWith = Muted() // what the tick saw before it asked AnkiDroid
        val out = decideMuted("2026-10-14T18:00:00", dueOn, decidedWith)
        assertEquals(1, out.size)
        val now = t("2026-10-14T18:00:00")
        val day = "2026-10-14"
        assertEquals(out, ReminderRules.stillWanted(out, now, day, decidedWith)) // nothing happened meanwhile
        // "Not today" pressed during the read: nothing is posted
        assertTrue(ReminderRules.stillWanted(out, now, day, decidedWith.notToday(ReminderKind.OWN_GOAL, day)).isEmpty()) // same topic
        assertEquals(out, ReminderRules.stillWanted(out, now, day, decidedWith.notToday(ReminderKind.SHOP, day))) // another topic does not matter
        assertEquals(out, ReminderRules.stillWanted(out, now, "2026-10-15", decidedWith.notToday(ReminderKind.CARDS_DUE, day))) // yesterday's mark
        // "In 1 hour" pressed during the read: the new snooze is ahead, so nothing is posted now
        assertTrue(ReminderRules.stillWanted(out, now, day, decidedWith.snooze(ReminderKind.CARDS_DUE, now + 60_000)).isEmpty())
        // the snooze this tick itself acted on (its hour has come) does not stop it
        val seen = Muted().snooze(ReminderKind.CARDS_DUE, now - 3_600_000)
        assertEquals(out, ReminderRules.stillWanted(out, now, day, seen))
    }

    @Test fun theTickOnlyUsesUpTheSnoozeItSaw() {
        val now = t("2026-10-14T18:00:00")
        val seen = Muted().snooze(ReminderKind.CARDS_DUE, now - 3_600_000) // ready: this tick acts on it
        val used = ReminderRules.snoozesUsed(now, zone, dueOn, seen)
        assertEquals(setOf(ReminderKind.CARDS_DUE), used)
        // nothing pressed meanwhile: used up
        assertTrue(seen.consume(used, seen).snoozed.isEmpty())
        // "In 1 hour" pressed again during the tick moved the time: that new snooze stays
        val pressed = seen.snooze(ReminderKind.CARDS_DUE, now + 5_000)
        assertEquals(pressed.snoozed, pressed.consume(used, seen).snoozed)
        // "Not today" pressed during the tick: the snooze is gone already, the mark stays through the clean-up
        val off = seen.notToday(ReminderKind.CARDS_DUE, "2026-10-14")
        val after = off.consume(used, seen).tidy(now, "2026-10-14")
        assertTrue(after.silenced(ReminderKind.CARDS_DUE, "2026-10-14"))
        assertTrue(after.silenced(ReminderKind.OWN_GOAL, "2026-10-14"))
        assertTrue(after.snoozed.isEmpty())
    }

    @Test fun snoozesAreUsedUpOnceTheTickLookedAtThem() {
        val muted = Muted().snooze(ReminderKind.CARDS_DUE, t("2026-10-14T17:00:00")).snooze(ReminderKind.OWN_GOAL, t("2026-10-14T18:30:00"))
        val now = t("2026-10-14T18:10:00")
        assertEquals(setOf(ReminderKind.CARDS_DUE), ReminderRules.snoozesUsed(now, zone, dueOn, muted)) // OWN_GOAL's hour is still to come
        assertTrue(ReminderRules.snoozesUsed(now, zone, ReminderPrefs(), muted).isEmpty()) // nothing on: no tick work
        val quiet = dueOn.copy(quietOn = true, quietFrom = 18 * 60, quietTo = 19 * 60)
        assertTrue(ReminderRules.snoozesUsed(now, zone, quiet, muted).isEmpty()) // quiet: they wait
    }

    @Test fun theAlarmWakesForASnoozedReminder() {
        val now = t("2026-10-14T12:00:00")
        val p = ReminderPrefs(dueOn = true, dueMode = DueMode.EVERY_HOURS)
        val muted = Muted().snooze(ReminderKind.CARDS_DUE, now - 55 * 60_000) // due in 5 minutes
        assertEquals(5 * 60_000L, ReminderRules.nextDelayMs(now, zone, p, ReminderRules.DEFAULT_PICK_MINUTE, muted))
        assertEquals(ReminderRules.TICK_MS, ReminderRules.nextDelayMs(now, zone, p, ReminderRules.DEFAULT_PICK_MINUTE, Muted().snooze(ReminderKind.CARDS_DUE, now))) // an hour away
        assertEquals(30_000L, ReminderRules.nextDelayMs(now, zone, p, ReminderRules.DEFAULT_PICK_MINUTE, Muted().snooze(ReminderKind.CARDS_DUE, now - 59 * 60_000 - 50_000)))
    }

    // ---- the goal line on the cards-due notice ----

    @Test fun cardsDueNoticeCarriesTheDailyGoalAndTheStreak() {
        val f = streakFacts // 10 of 50 today, 6-day streak (goal reached yesterday)
        val n = decide("2026-10-14T18:00:00", dueOn, due = snap(deck(1, "Japanese Core 2k", review = 30), deck(2, "Kanji", review = 12)), facts = f)[0]
        assertEquals("42 cards waiting", n.title)
        assertEquals(listOf("Japanese Core 2k — 30", "Kanji — 12", "Daily goal 10 / 50 · 6-day streak"), n.lines)
        assertTrue(n.showBar); assertEquals(10, n.goalDone); assertEquals(50, n.goalTarget)
        // a facts snapshot from yesterday counts for yesterday: today starts at 0; a broken streak is not shown
        val stale = f.copy(day = "2026-10-13", reviewsToday = 70, lastGoalDay = "2026-10-12")
        val s = decide("2026-10-14T18:00:00", dueOn, due = thirty, facts = stale)[0]
        assertEquals(listOf("Spanish — 30", "Daily goal 0 / 50"), s.lines)
        // no facts or no goal: no goal line, no bar
        val none = decide("2026-10-14T18:00:00", dueOn, due = thirty)[0]
        assertEquals(listOf("Spanish — 30"), none.lines)
        assertFalse(none.showBar)
        // the goal is reached: "Daily goal reached", the bar full
        val done = decide("2026-10-14T18:00:00", dueOn, due = thirty, facts = f.copy(reviewsToday = 60, lastGoalDay = "2026-10-14"))[0]
        assertEquals("Daily goal reached · 6-day streak", done.lines.last())
    }

    @Test fun noDeckWithCardsStillGivesALine() {
        val n = decide("2026-10-14T18:00:00", dueOn.copy(dueMin = 0), due = snap(deck(1, "Empty")))[0]
        assertEquals(listOf("Time for a study session."), n.lines)
    }

    @Test fun bigNumbersKeepTheNoticeSmall() {
        val decks = (1..6).map { deck(it.toLong(), "Deck number $it with a rather long name that goes on", review = 5_000 * it) }
        val n = decide("2026-10-14T18:00:00", dueOn, due = snap(*decks.toTypedArray()), facts = streakFacts.copy(reviewsToday = 12_345, dailyGoal = 20_000))[0]
        assertEquals("105k cards waiting", n.title)
        assertEquals(5, n.lines.size) // three decks, "and 3 more decks", the goal line
        assertEquals("and 3 more decks", n.lines[3])
        assertTrue(n.lines.all { it.length <= 50 })
    }
}

