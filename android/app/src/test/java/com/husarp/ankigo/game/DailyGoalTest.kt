package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import com.husarp.ankigo.remind.ReminderFacts
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.random.Random

// R6-04: the daily goal per weekday. 2026-10-12 is a Monday.
class DailyGoalTest {
    private val data = TestData.world()
    private val week = listOf(10, 20, 30, 40, 50, 60, 70) // Mon .. Sun
    private lateinit var savedZone: TimeZone

    @Before fun utcZone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreZone() = TimeZone.setDefault(savedZone)

    private fun at(day: Int, hour: Int, min: Int = 0) =
        ZonedDateTime.of(2026, 10, day, hour, min, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test fun noTableMeansTheSameGoalEveryDay() {
        val s = GameState(day = "2026-10-14")
        s.settings.dailyAverage = 120
        assertEquals(120, DailyGoal.today(s))
        assertEquals(120, DailyGoal.forDay(s, "2026-10-18"))
        assertEquals(120, DailyGoal.forDay(s, "garbage"))
        assertEquals(120, DailyGoal.today(GameState(day = "").also { it.settings.dailyAverage = 120 }))
    }

    @Test fun theTableIsMondayFirst() {
        val s = GameState(day = "2026-10-12")
        s.settings.goalByWeekday = week
        assertEquals(10, DailyGoal.today(s)) // Monday
        for (i in 0..6) assertEquals(week[i], DailyGoal.forDay(s, "2026-10-${12 + i}"))
        assertEquals(10, DailyGoal.forDay(s, "2026-10-19")) // next Monday
    }

    @Test fun anUnreadableDayOrABadTableFallsBackToTheBaseGoal() {
        val s = GameState(day = "")
        s.settings.dailyAverage = 77
        s.settings.goalByWeekday = week
        assertEquals(77, DailyGoal.today(s))
        s.settings.goalByWeekday = listOf(1, 2, 3)
        assertEquals(77, DailyGoal.forDay(s, "2026-10-12"))
        assertNull(DailyGoal.cleanTable(listOf(1, 2, 3)))
        assertEquals(listOf(1, 1, 1, 1, 1, 1, 500), DailyGoal.cleanTable(listOf(0, -4, 1, 1, 1, 1, 9999)))
    }

    @Test fun theLegacyBaseGoalIsNotCappedAtTheTableMax() {
        val s = GameState(day = "2026-10-14")
        s.settings.dailyAverage = 1000 // an imported Ankimon goal
        assertEquals(1000, DailyGoal.today(s))
        assertEquals(1000, DailyGoal.forDay(s, "2026-10-12"))
        assertEquals(1000, DailyGoal.forDay(s, "garbage"))
        s.settings.dailyAverage = 0
        assertEquals(1, DailyGoal.today(s))
        // the weekday-table entries keep the picker bounds
        s.settings.goalByWeekday = listOf(9999, 0, 30, 40, 50, 60, 70)
        assertEquals(500, DailyGoal.forDay(s, "2026-10-12")); assertEquals(1, DailyGoal.forDay(s, "2026-10-13"))
    }

    @Test fun theGameDayDecidesTheWeekdayNotTheClock() {
        // day starts at 4: Tuesday 2 a.m. still belongs to Monday's goal
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        s.settings.cardsPerRound = 1
        s.settings.dayStartHour = 4
        s.settings.goalByWeekday = week
        var now = at(13, 2)
        val g = Game(data, s, Random(1)) { now }
        g.tick()
        assertEquals("2026-10-12", s.day)
        assertEquals(10, DailyGoal.today(s))
        now = at(13, 5)
        g.tick()
        assertEquals("2026-10-13", s.day)
        assertEquals(20, DailyGoal.today(s))
    }

    @Test fun theStreakAndTheBonusUseThatDaysGoal() {
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        s.settings.cardsPerRound = 1
        s.settings.goalByWeekday = listOf(2, 3, 1000, 1000, 1000, 1000, 1000) // Mon 2, Tue 3
        var now = at(12, 12)
        val g = Game(data, s, Random(1)) { now }
        var card = 1L
        fun answers(n: Int) = (1..n).sumOf { g.onAnswer(Answer(card++, 3, now)).count { it == GameEvent.Info("Daily goal reached!") } }
        assertEquals(0, answers(1)); assertNull(s.lastGoalDay)
        assertEquals(1, answers(1)); assertEquals("2026-10-12", s.lastGoalDay) // Monday's goal is 2
        assertEquals(0, answers(3)) // once a day
        now = at(13, 12)
        assertEquals(0, answers(2)); assertEquals("2026-10-12", s.lastGoalDay) // Tuesday needs 3
        assertEquals(1, answers(1))
        assertEquals(2, s.streakDays); assertEquals("2026-10-13", s.lastGoalDay)
    }

    @Test fun lowerAndRaisedGoalsMidDayAndAMetDayStaysMet() {
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        s.settings.cardsPerRound = 1
        s.settings.goalByWeekday = listOf(1000, 5, 1000, 1000, 1000, 1000, 1000)
        var now = at(13, 12) // Tuesday
        val g = Game(data, s, Random(1)) { now }
        var card = 1L
        fun answer() = g.onAnswer(Answer(card++, 3, now)).any { it == GameEvent.Info("Daily goal reached!") }
        repeat(3) { assertEquals(false, answer()) }
        s.settings.goalByWeekday = listOf(1000, 4, 1000, 1000, 1000, 1000, 1000) // lowered mid-day
        assertEquals(true, answer())
        s.settings.goalByWeekday = listOf(1000, 400, 1000, 1000, 1000, 1000, 1000) // raised afterwards
        assertEquals("2026-10-13", s.lastGoalDay)
        assertEquals(1, s.streakDays)
        assertEquals(false, answer()) // never a second time
        assertEquals(1, s.streakDays)
    }

    @Test fun statsSummaryUsesTheGoalOfThePassedDay() {
        val s = GameState(day = "2026-10-13")
        s.settings.goalByWeekday = week
        assertEquals(20, StudyStats.summary(s, "2026-10-13").goal) // Tuesday
        assertEquals(30, StudyStats.summary(s, "2026-10-14").goal)
    }

    @Test fun saveRoundTripKeepsTheTableAndAnOldSaveLoadsWithoutOne() {
        val s = GameState(day = "2026-10-13", starterChosen = true)
        s.settings.goalByWeekday = week
        s.settings.dailyAverage = 90
        val back = SaveCodec.decode(SaveCodec.encode(s))
        assertEquals(week, back.settings.goalByWeekday); assertEquals(90, back.settings.dailyAverage)

        val same = GameState(day = "2026-10-13", starterChosen = true)
        val json = SaveCodec.encode(same)
        assertEquals(false, json.contains("goalByWeekday")) // nothing written when the goal is the same every day
        assertNull(SaveCodec.decode(json).settings.goalByWeekday)

        val o = JSONObject(SaveCodec.encode(s))
        o.getJSONObject("settings").put("goalByWeekday", org.json.JSONArray(listOf(1, 2))) // damaged: ignored
        assertNull(SaveCodec.decode(o.toString()).settings.goalByWeekday)
    }

    @Test fun reminderFactsForAnOlderDayKnowTheGoalOfTheRealDay() {
        val s = TestData.state(data) // day 2026-10-13, a Tuesday
        s.settings.dailyAverage = 100
        assertEquals(100, ReminderFacts.from(data, s).goalFor("2026-10-16"))
        s.settings.goalByWeekday = week
        val f = ReminderFacts.from(data, s)
        assertEquals(20, f.dailyGoal)
        assertEquals(20, f.goalFor("2026-10-13"))
        assertEquals(50, f.goalFor("2026-10-16")) // Friday, the app was not opened since Tuesday
        assertEquals(0, ReminderFacts.NONE.goalFor("2026-10-16"))
    }
}
