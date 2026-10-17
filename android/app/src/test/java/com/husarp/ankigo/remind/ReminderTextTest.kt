package com.husarp.ankigo.remind

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTextTest {
    @Test fun compactNumbersStayShort() {
        val cases = mapOf(
            0 to "0", 7 to "7", 999 to "999", 1000 to "1k", 1049 to "1k", 1200 to "1.2k", 1999 to "1.9k", 2000 to "2k",
            9999 to "9.9k", 10_000 to "10k", 12_345 to "12k", 150_000 to "150k", 999_999 to "999k",
            1_000_000 to "1M", 1_500_000 to "1.5M", 25_000_000 to "25M",
        )
        for ((n, want) in cases) assertEquals("compact($n)", want, ReminderText.compact(n))
        assertEquals("0", ReminderText.compact(-5))
    }

    @Test fun noCountIsEverWiderThanFourCharacters() {
        for (n in (0..120_000 step 7) + listOf(999_999, 1_000_000, 9_999_999, 99_000_000)) assertTrue("$n", ReminderText.compact(n).length <= 4)
    }

    @Test fun titleIsTheCount() {
        assertEquals("42 cards waiting", ReminderText.cardsTitle(42))
        assertEquals("1 card waiting", ReminderText.cardsTitle(1))
        assertEquals("2k cards waiting", ReminderText.cardsTitle(2000))
        assertEquals("12k cards waiting", ReminderText.cardsTitle(12_345))
    }

    @Test fun oneShortLinePerDeckAtMostThree() {
        assertEquals("Japanese Core 2k — 30", ReminderText.deckLine("Japanese Core 2k", 30))
        assertEquals("Kanji — 1.2k", ReminderText.deckLine("Japanese::Kanji", 1200))
        val five = listOf("A" to 9, "B" to 8, "C" to 7, "D" to 6, "E" to 5)
        assertEquals(listOf("A — 9", "B — 8", "C — 7", "and 2 more decks"), ReminderText.deckLines(five))
        assertEquals(listOf("A — 9", "B — 8", "C — 7", "and 1 more deck"), ReminderText.deckLines(five.take(4)))
        assertEquals(listOf("A — 9", "B — 8", "C — 7"), ReminderText.deckLines(five.take(3)))
        assertEquals(emptyList<String>(), ReminderText.deckLines(emptyList()))
    }

    @Test fun aLongDeckNameIsCut() {
        val long = "Medicine::Anatomy and physiology of the whole human body, part two"
        val line = ReminderText.deckLine(long, 12)
        assertTrue(line, line.endsWith("… — 12"))
        assertTrue(ReminderText.deckName(long).length <= ReminderText.DECK_NAME_MAX)
        assertEquals("Exactly twenty-four chr", ReminderText.deckName("Exactly twenty-four chr")) // 23 characters: untouched
        assertEquals("X", ReminderText.deckName("X"))
        assertEquals("Odd::", ReminderText.deckName("Odd::")) // an empty leaf shows the whole name
    }

    @Test fun theWorstCaseNotificationStaysSmall() {
        // five long decks with huge counts: still title + three lines + "and 2 more" + the goal line
        val decks = (1..5).map { "Deck number $it with a very very long name indeed" to 1_000_000 * it }
        val lines = ReminderText.deckLines(decks) + ReminderText.goalLine(123_456, 999_999, 100)
        assertEquals(5, lines.size)
        assertTrue(lines.all { it.length <= 60 })
    }

    @Test fun goalAndStreakLines() {
        assertEquals("Daily goal 64 / 100 · 6-day streak", ReminderText.goalLine(64, 100, 6))
        assertEquals("Daily goal 64 / 100", ReminderText.goalLine(64, 100, 0))
        assertEquals("Daily goal reached · 3-day streak", ReminderText.goalLine(100, 100, 3))
        assertEquals("Daily goal 1.2k / 2k", ReminderText.goalLine(1200, 2000, 0))
        assertEquals("Your 6-day streak ends tonight", ReminderText.streakTitle(6))
        assertEquals("10 reviews keep it going.", ReminderText.streakLine(10))
        assertEquals("1 review keeps it going.", ReminderText.streakLine(1))
    }

    @Test fun ownGoalAndDailyGoalAndShop() {
        assertEquals("36 reviews to your goal", ReminderText.ownGoalTitle(36, false))
        assertEquals("1 new card to your goal", ReminderText.ownGoalTitle(1, true))
        assertEquals("64 / 100 reviews today", ReminderText.ownGoalLine(64, 100, false))
        assertEquals("12 / 20 new cards today", ReminderText.ownGoalLine(12, 20, true))
        assertEquals("40 reviews to today's goal", ReminderText.dailyGoalTitle(40))
        assertEquals("1 review to today's goal", ReminderText.dailyGoalTitle(1))
        assertEquals("Poké Ball, Great Ball", ReminderText.shopLine(listOf("Poké Ball", "Great Ball")))
        assertEquals("A, B, C and 2 more", ReminderText.shopLine(listOf("A", "B", "C", "D", "E")))
    }

    @Test fun settingsRowSummaries() {
        val p = ReminderPrefs()
        for (k in ReminderKind.entries) assertEquals("Off", ReminderText.summary(k, p))
        assertEquals("Off", ReminderText.quietSummary(p.copy(quietOn = false)))
        assertEquals("22:00 – 08:00", ReminderText.quietSummary(p))
        assertEquals("Off", ReminderText.quietSummary(p.copy(quietFrom = 480, quietTo = 480))) // empty range
        assertEquals("20 cards · 18:00", ReminderText.summary(ReminderKind.CARDS_DUE, p.copy(dueOn = true)))
        assertEquals("50 cards · App picks · 2 decks · 2 a day",
            ReminderText.summary(ReminderKind.CARDS_DUE, p.copy(dueOn = true, dueMin = 50, dueMode = DueMode.APP_PICKS, allDecks = false, deckIds = setOf(1, 2), dailyCap = 2)))
        assertEquals("1.2k cards · Every 4 h · 1 deck",
            ReminderText.summary(ReminderKind.CARDS_DUE, p.copy(dueOn = true, dueMin = 1200, dueMode = DueMode.EVERY_HOURS, dueEveryHours = 4, allDecks = false, deckIds = setOf(7))))
        assertEquals("20 cards · 18:00 · No decks", ReminderText.summary(ReminderKind.CARDS_DUE, p.copy(dueOn = true, allDecks = false)))
        assertEquals("100 reviews · Every 2 h", ReminderText.summary(ReminderKind.OWN_GOAL, p.copy(goalOn = true)))
        assertEquals("20 new cards · Every 2 h", ReminderText.summary(ReminderKind.OWN_GOAL, p.copy(goalOn = true, goalKind = GoalKind.NEW_CARDS)))
        assertEquals("At 20:00", ReminderText.summary(ReminderKind.STREAK, p.copy(streakOn = true)))
        assertEquals("At 19:00", ReminderText.summary(ReminderKind.DAILY_GOAL, p.copy(dailyGoalOn = true)))
        assertEquals("Balls and evolution items", ReminderText.summary(ReminderKind.SHOP, p.copy(shopOn = true)))
        assertEquals("07:05", ReminderText.timeText(7 * 60 + 5))
        assertEquals("23:59", ReminderText.timeText(99_999)) // out of range: clamped
    }
}
