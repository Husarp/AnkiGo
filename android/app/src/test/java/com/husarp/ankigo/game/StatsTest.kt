package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.random.Random

// TICKET T40: per-day history, best streak, heatmap and summary.
class StatsTest {
    private val data = TestData.world()
    private var now = ZonedDateTime.of(2026, 10, 13, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    private var nextCard = 1L
    private lateinit var savedZone: TimeZone

    @Before fun utcZone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreZone() = TimeZone.setDefault(savedZone)

    private fun game(): Game {
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        s.settings.cardsPerRound = 1
        return Game(data, s, Random(1)) { now }
    }

    private fun Game.fight(species: Int, shiny: Boolean = false, hp: Int = 1) {
        val w = TestData.mon(data, species, 12, listOf("tackle"), uid = "wild")
        w.hp = hp; w.shiny = shiny
        state.battle = Battle(w, Tier.NORMAL, startedAt = now)
    }

    private fun Game.answer(button: Int = 3, card: Long = nextCard++) = onAnswer(Answer(card, button, now))

    private fun counted() = GameEvent.Counted(3, 10, 1, 1, false)
    private fun caught(shiny: Boolean) = GameEvent.Caught("u", 54, "Wavebug", 12, shiny, null, true, true)

    // ---- record ----

    @Test fun recordCountsEventsIntoTodaysLog() {
        val s = GameState(day = "2026-10-13")
        StudyStats.record(s, listOf(counted(), counted(), GameEvent.Defeated(19, "Rat", 30), caught(false), caught(true), GameEvent.Info("x")))
        val d = s.history.getValue("2026-10-13")
        assertEquals(2, d.reviews)
        assertEquals(3, d.won)       // one defeat + two catches
        assertEquals(2, d.caught)
        assertEquals(1, d.shinies)
        StudyStats.record(s, listOf(counted()))
        assertEquals(3, s.history.getValue("2026-10-13").reviews)
    }

    @Test fun eventsWithNothingToCountMakeNoEntry() {
        val s = GameState(day = "2026-10-13")
        StudyStats.record(s, listOf(GameEvent.Ignored("undo"), GameEvent.Info("x"), GameEvent.BallThrow("poke-ball", 8, false, 1)))
        assertTrue(s.history.isEmpty())
        StudyStats.record(GameState(day = ""), listOf(counted()))
    }

    @Test fun answersAreRecordedUnderTheGameDay() {
        val g = game()
        g.answer(); g.answer(1)
        assertEquals(2, g.state.history.getValue("2026-10-13").reviews)
        now += 24 * 3_600_000L
        g.answer()
        assertEquals(1, g.state.history.getValue("2026-10-14").reviews)
        assertEquals(2, g.state.history.getValue("2026-10-13").reviews)
        // before the day start hour (04:00) it still belongs to the day before
        now = ZonedDateTime.of(2026, 10, 15, 2, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        g.answer()
        assertEquals(2, g.state.history.getValue("2026-10-14").reviews)
    }

    @Test fun defeatAndCatchThroughGameAnswers() {
        val g = game()
        g.state.caught += 19
        g.fight(19)
        g.answer()
        var d = g.state.history.getValue("2026-10-13")
        assertEquals(1, d.reviews); assertEquals(1, d.won); assertEquals(0, d.caught)
        g.fight(54)
        g.answer()
        d = g.state.history.getValue("2026-10-13")
        assertEquals(2, d.reviews); assertEquals(2, d.won); assertEquals(1, d.caught); assertEquals(0, d.shinies)
        g.fight(63, shiny = true)
        g.answer()
        d = g.state.history.getValue("2026-10-13")
        assertEquals(3, d.won); assertEquals(2, d.caught); assertEquals(1, d.shinies)
    }

    @Test fun throwBallCatchCountsButAMissAndAnUndoDoNot() {
        val g = game()
        Economy.give(g.state, "master-ball", 2)
        g.fight(54, shiny = true, hp = 20)
        g.throwBall("master-ball")
        val d = g.state.history.getValue("2026-10-13")
        assertEquals(0, d.reviews); assertEquals(1, d.won); assertEquals(1, d.caught); assertEquals(1, d.shinies)
        // an answer repeated after an undo is ignored, so it adds nothing
        g.answer(card = 77)
        assertEquals(1, g.state.history.getValue("2026-10-13").reviews)
        g.onUndo(77, 1)
        g.answer(card = 77)
        assertEquals(1, g.state.history.getValue("2026-10-13").reviews)
    }

    @Test fun historyOlderThan400DaysIsPruned() {
        val s = GameState(day = "2026-10-13")
        val today = LocalDate.parse("2026-10-13")
        s.history[today.minusDays(401).toString()] = DayLog(reviews = 5)
        s.history[today.minusDays(400).toString()] = DayLog(reviews = 6)
        s.history[today.minusDays(10).toString()] = DayLog(reviews = 7)
        StudyStats.record(s, listOf(counted()))
        assertNull(s.history[today.minusDays(401).toString()])
        assertEquals(6, s.history.getValue(today.minusDays(400).toString()).reviews)
        assertEquals(3, s.history.size) // the 400-day, the 10-day and today
    }

    // ---- best streak ----

    @Test fun bestStreakGrowsWithTheStreakAndNeverDrops() {
        val g = game()
        g.state.settings.dailyAverage = 2
        fun studyDay() { repeat(3) { g.answer() } }
        studyDay(); assertEquals(1, g.state.bestStreakDays)
        now += 24 * 3_600_000L; studyDay(); assertEquals(2, g.state.bestStreakDays)
        now += 24 * 3_600_000L; studyDay(); assertEquals(3, g.state.bestStreakDays)
        now += 3 * 24 * 3_600_000L; studyDay()
        assertEquals(1, g.state.streakDays); assertEquals(3, g.state.bestStreakDays)
    }

    @Test fun currentStreakIsZeroWhenTheGoalWasNotReachedTodayOrYesterday() {
        val s = GameState(day = "2026-10-14")
        s.streakDays = 12; s.bestStreakDays = 9; s.lastGoalDay = "2026-10-14"
        assertEquals(12, StudyStats.streak(s, "2026-10-14").current)
        assertEquals(12, StudyStats.streak(s, "2026-10-14").best) // best never below the current one
        s.lastGoalDay = "2026-10-13"
        assertEquals(12, StudyStats.streak(s, "2026-10-14").current)
        s.lastGoalDay = "2026-10-12"
        val gone = StudyStats.streak(s, "2026-10-14")
        assertEquals(0, gone.current); assertEquals(9, gone.best)
        s.lastGoalDay = null
        assertEquals(0, StudyStats.streak(s, "2026-10-14").current)
    }

    // ---- heatmap ----

    private fun mapState(goal: Int = 100): GameState = GameState(day = "2026-10-14").also { it.settings.dailyAverage = goal }

    @Test fun heatmapIsMondayFirstWithTheCurrentWeekLast() {
        val s = mapState()
        val h = StudyStats.heatmap(s, "2026-10-14", 18) // a Wednesday
        assertEquals(18, h.columns.size)
        assertTrue(h.columns.all { it.size == 7 })
        val last = h.columns.last()
        assertEquals("2026-10-12", last[0]!!.day) // Monday on the top row
        assertEquals("2026-10-13", last[1]!!.day)
        assertEquals("2026-10-14", last[2]!!.day) // today
        assertNull(last[3]); assertNull(last[4]); assertNull(last[5]); assertNull(last[6]) // the future is not drawn
        assertEquals("2026-06-15", h.columns.first()[0]!!.day)
        assertEquals("2026-06-21", h.columns.first()[6]!!.day)
        assertTrue(h.columns.dropLast(1).all { week -> week.all { it != null } })
    }

    @Test fun heatmapOnASundayFillsTheWholeLastColumn() {
        val h = StudyStats.heatmap(mapState(), "2026-10-18", 4)
        assertEquals("2026-10-18", h.columns.last()[6]!!.day)
        assertTrue(h.columns.last().all { it != null })
        val monday = StudyStats.heatmap(mapState(), "2026-10-12", 4)
        assertEquals("2026-10-12", monday.columns.last()[0]!!.day)
        assertTrue(monday.columns.last().drop(1).all { it == null })
    }

    @Test fun eachHeatmapCellUsesItsOwnGameDaysGoal() {
        // 2026-10-12 is a Monday (goal 500), 2026-10-13 a Tuesday (goal 20), 2026-10-14 (today) a Wednesday (goal 100)
        val s = mapState(100)
        s.settings.goalByWeekday = listOf(500, 20, 100, 100, 100, 100, 100)
        s.history["2026-10-12"] = DayLog(reviews = 20)
        s.history["2026-10-13"] = DayLog(reviews = 20)
        val h = StudyStats.heatmap(s, "2026-10-14", 2)
        assertEquals("a Monday goal of 500: 20 reviews is a pale cell", 1, h.columns.last()[0]!!.level)
        assertEquals("a Tuesday goal of 20: the same 20 reviews reach it", 4, h.columns.last()[1]!!.level)
    }

    @Test fun heatmapLevelsFollowTheDailyGoal() {
        assertEquals(0, StudyStats.level(0, 100))
        assertEquals(1, StudyStats.level(1, 100)); assertEquals(1, StudyStats.level(24, 100))
        assertEquals(2, StudyStats.level(25, 100)); assertEquals(2, StudyStats.level(49, 100))
        assertEquals(3, StudyStats.level(50, 100)); assertEquals(3, StudyStats.level(99, 100))
        assertEquals(4, StudyStats.level(100, 100)); assertEquals(4, StudyStats.level(900, 100))
        assertEquals(4, StudyStats.level(1, 0)) // a goal of 0 acts like 1
        val s = mapState(100)
        s.history["2026-10-13"] = DayLog(reviews = 60)
        fun levelOf() = StudyStats.heatmap(s, "2026-10-14", 2).columns.last()[1]!!.level
        assertEquals(3, levelOf())
        s.settings.dailyAverage = 40 // the goal was lowered: the same day now counts as a goal day
        assertEquals(4, levelOf())
        s.settings.dailyAverage = 400
        assertEquals(1, levelOf())
    }

    @Test fun heatmapTotalAndTodaysCellUseTheLiveCount() {
        val s = mapState()
        s.history["2026-10-13"] = DayLog(reviews = 30)
        s.history["2026-01-01"] = DayLog(reviews = 999) // outside the 18 weeks
        s.reviewsToday = 50
        val sum = StudyStats.summary(s, "2026-10-14", 18)
        assertEquals(50, sum.reviewsToday)
        assertEquals(80, sum.heatmap.total)
        assertEquals(50, sum.heatmap.columns.last()[2]!!.reviews)
    }

    @Test fun heatmapFromAnyDayCountMap() {
        val counts = mapOf("2026-10-14" to 100, "2026-10-13" to 60, "2026-10-12" to 24, "2026-06-15" to 7, "2026-06-14" to 999, "2026-10-15" to 50)
        val h = StudyStats.heatmapFromCounts(counts, 100, "2026-10-14", 18)
        val last = h.columns.last()
        assertEquals(4, last[2]!!.level); assertEquals(100, last[2]!!.reviews)   // today reaches the goal
        assertEquals(3, last[1]!!.level)                                          // 60 of 100
        assertEquals(1, last[0]!!.level)                                          // 24 of 100
        assertEquals(7, h.columns.first()[0]!!.reviews)                           // Monday of the first week
        assertNull(last[3])                                                       // a count for a future day is not drawn
        assertEquals(100 + 60 + 24 + 7, h.total)                                  // 06-14 is before the first column, 10-15 is after today
        assertEquals(1, StudyStats.heatmapFromCounts(counts, 400, "2026-10-14", 18).columns.last()[1]!!.level)
        assertEquals(0, StudyStats.heatmapFromCounts(emptyMap(), 100, "2026-10-14", 4).total)
        assertTrue(StudyStats.heatmapFromCounts(counts, 100, "bad", 18).columns.isEmpty())
    }

    @Test fun theStateHeatmapIsTheMapHeatmapOfItsHistory() {
        val s = mapState(80)
        s.history["2026-10-13"] = DayLog(reviews = 60); s.history["2026-09-01"] = DayLog(reviews = 15)
        val viaState = StudyStats.heatmap(s, "2026-10-14", 18, todayReviews = 33)
        val viaMap = StudyStats.heatmapFromCounts(mapOf("2026-10-13" to 60, "2026-09-01" to 15, "2026-10-14" to 33), 80, "2026-10-14", 18)
        assertEquals(viaMap.total, viaState.total)
        assertEquals(viaMap.monthLabels, viaState.monthLabels)
        assertEquals(
            viaMap.columns.flatten().map { it?.let { c -> Triple(c.day, c.reviews, c.level) } },
            viaState.columns.flatten().map { it?.let { c -> Triple(c.day, c.reviews, c.level) } },
        )
    }

    @Test fun dayTotalsFromCounts() {
        val t = StudyStats.dayTotals(listOf(100, 0, 50, 0, 7))
        assertEquals(3, t.daysStudied); assertEquals(52, t.avgPerDay); assertEquals(100, t.bestDay) // 157 / 3 = 52.3
        val none = StudyStats.dayTotals(listOf(0, 0))
        assertEquals(0, none.daysStudied); assertEquals(0, none.avgPerDay); assertEquals(0, none.bestDay)
        assertEquals(0, StudyStats.dayTotals(emptyList()).daysStudied)
    }

    @Test fun monthLabelsSitOnTheColumnThatStartsAMonth() {
        val h = StudyStats.heatmap(mapState(), "2026-10-14", 18)
        assertEquals(listOf(0 to "Jun", 3 to "Jul", 7 to "Aug", 12 to "Sep", 16 to "Oct"), h.monthLabels)
        // when the second column starts a month too, the first label is dropped so they don't overprint
        val short = StudyStats.heatmap(mapState(), "2026-10-14", 2) // Oct 5 and Oct 12
        assertEquals(listOf(0 to "Oct"), short.monthLabels)
        val edge = StudyStats.heatmap(mapState(), "2026-11-04", 2) // Oct 26 and Nov 2
        assertEquals(listOf(1 to "Nov"), edge.monthLabels)
    }

    // ---- summary ----

    @Test fun battlesWonCountsACatchOnce() {
        val s = mapState(100)
        s.defeats = 64; s.catches = 21; s.catchWins = 5 // 5 of the catches are already inside defeats
        assertEquals(80, StudyStats.summary(s, "2026-10-14").battlesWon)
    }

    @Test fun summaryTotalsComeFromTheSaveAndTheHistory() {
        val s = mapState(100)
        s.totalReviews = 4120; s.defeats = 64; s.catches = 21; s.evolutions = 3; s.bestCardStreak = 21
        for (i in 0 until 3) s.mons += TestData.mon(data, 25, 10, uid = "m$i").also { it.shiny = i < 2 }
        s.history["2026-10-12"] = DayLog(reviews = 100, won = 10)
        s.history["2026-10-13"] = DayLog(reviews = 50)
        s.history["2026-10-01"] = DayLog() // an empty day is no study day
        s.reviewsToday = 0
        s.streakDays = 3; s.bestStreakDays = 8; s.lastGoalDay = "2026-10-13"
        val sum = StudyStats.summary(s, "2026-10-14")
        assertEquals(4120L, sum.reviews)
        assertEquals(85, sum.battlesWon) // defeats + catches
        assertEquals(21, sum.catches)
        assertEquals(2, sum.shinies)     // shiny Pokémon owned now
        assertEquals(3, sum.evolutions); assertEquals(21, sum.bestCombo)
        assertEquals(2, sum.daysStudied); assertEquals(75, sum.avgPerDay); assertEquals(100, sum.bestDay)
        assertEquals(3, sum.streak.current); assertEquals(8, sum.streak.best)
        assertEquals(100, sum.goal)
    }

    @Test fun aNewPlayerHasAnEmptySummary() {
        val sum = StudyStats.summary(GameState(day = "2026-10-14"), "2026-10-14")
        assertEquals(0, sum.daysStudied); assertEquals(0, sum.avgPerDay); assertEquals(0, sum.bestDay)
        assertEquals(0, sum.heatmap.total)
        assertTrue(sum.heatmap.columns.flatten().filterNotNull().all { it.level == 0 })
    }

    // ---- save ----

    @Test fun historyAndBestStreakRoundTrip() {
        val s = GameState(day = "2026-10-14", schema = GameState.SCHEMA)
        s.mons += TestData.mon(data, 25, 5, uid = "a"); s.team += "a"
        s.history["2026-10-12"] = DayLog(100, 7, 2, 1)
        s.history["2026-10-13"] = DayLog(5, 0, 0, 0)
        s.history["2026-09-01"] = DayLog(1, 1, 1, 1)
        s.bestStreakDays = 20; s.streakDays = 12; s.reviewsToday = 40
        val json = SaveCodec.encode(s)
        val b = SaveCodec.decode(json)
        assertEquals(json, SaveCodec.encode(b))
        assertEquals(20, b.bestStreakDays)
        assertEquals(3, b.history.size)
        val d = b.history.getValue("2026-10-12")
        assertEquals(listOf(100, 7, 2, 1), listOf(d.reviews, d.won, d.caught, d.shinies))
        assertNull(b.history["2026-10-14"]) // history present: nothing is seeded
        assertEquals("[100,7,2,1,0,0,0,0,0,0,0,0,0,0,0,0,0]", JSONObject(json).getJSONObject("history").getJSONArray("2026-10-12").toString())
    }

    @Test fun anOldSaveWithoutHistorySeedsTodayAndTheBestStreak() {
        val s = GameState(day = "2026-10-14")
        s.mons += TestData.mon(data, 25, 5, uid = "a"); s.team += "a"
        s.reviewsToday = 86; s.streakDays = 12; s.bestStreakDays = 99
        val old = JSONObject(SaveCodec.encode(s)).apply { remove("history"); remove("bestStreakDays") }.toString()
        val b = SaveCodec.decode(old)
        assertEquals(1, b.history.size)
        assertEquals(86, b.history.getValue("2026-10-14").reviews)
        assertEquals(0, b.history.getValue("2026-10-14").won)
        assertEquals(12, b.bestStreakDays) // current streak is the best known
        assertNotNull(StudyStats.summary(b, "2026-10-14").heatmap.columns.last()[2])
    }

    @Test fun anOldSaveWithNoReviewsTodayStaysEmpty() {
        val s = GameState(day = "2026-10-14")
        s.mons += TestData.mon(data, 25, 5, uid = "a"); s.team += "a"
        val old = JSONObject(SaveCodec.encode(s)).apply { remove("history") }.toString()
        assertTrue(SaveCodec.decode(old).history.isEmpty())
    }
}
