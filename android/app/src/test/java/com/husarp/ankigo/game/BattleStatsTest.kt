package com.husarp.ankigo.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone
import kotlin.random.Random

/**
 * R4-V11: damage dealt / taken / blocked and misses per day (DayLog), counted by the same rules as the all-time achievement
 * counters (Achievements.attackNumbers). Made-up species only.
 */
class BattleStatsTest {
    private val data = TestData.world()
    private val NOW = 1_791_892_800_000L // 2026-10-13 12:00 UTC: the game day of TestData.state
    private lateinit var savedZone: TimeZone

    @Before fun utcZone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreZone() = TimeZone.setDefault(savedZone)

    private fun atk(
        byPlayer: Boolean, damage: Int = 10, eff: Double = 1.0, crit: Boolean = false, missed: Boolean = false,
        failed: Boolean = false, cat: Category = Category.PHYSICAL,
    ) = GameEvent.Attack(
        byPlayer, if (byPlayer) "Main" else "Wild", if (byPlayer) "Wild" else "Main", "Tackle", Type.NORMAL, cat, damage, 1, eff, crit,
        missed, failed, targetHp = 30, targetMaxHp = 40, userHp = 30, userMaxHp = 40,
    )

    /** One scripted round: super-effective crit, plain hit, a miss each side, a status move, a failed move, a resisted hit, an immune hit, a hit against you. */
    private fun round() = listOf(
        atk(true, damage = 30, eff = 2.0, crit = true), // dealt 30
        atk(true, damage = 12),                         // dealt 12
        atk(true, damage = 0, missed = true),           // a miss (yours)
        atk(true, damage = 0, cat = Category.STATUS),   // a status move: nothing
        atk(true, damage = 5, failed = true),           // a failed move: nothing
        atk(false, damage = 8, eff = 0.5),              // taken 8; a neutral hit would have done 16, so 8 blocked
        atk(false, damage = 0, eff = 0.0),              // no effect: nothing taken, nothing blocked (its full damage is unknown)
        atk(false, damage = 0, missed = true),          // the wild one missed: also a miss
        atk(false, damage = 0, cat = Category.STATUS, eff = 0.5),
        atk(false, damage = 20, eff = 2.0),             // taken 20
    )

    private fun state() = TestData.state(data, 25, 20, listOf("tackle"))

    private fun counter(s: GameState, m: Metric) = s.counters[m.name] ?: 0L

    // ---- the pure rule ----

    @Test fun attackNumbersOfEachKindOfAttack() {
        fun n(e: GameEvent.Attack) = Achievements.attackNumbers(e).let { listOf(it.dealt, it.taken, it.blocked, it.misses.toLong()) }
        assertEquals(listOf(30L, 0L, 0L, 0L), n(atk(true, damage = 30, eff = 2.0, crit = true)))
        assertEquals(listOf(0L, 8L, 8L, 0L), n(atk(false, damage = 8, eff = 0.5)))
        assertEquals(listOf(0L, 10L, 30L, 0L), n(atk(false, damage = 10, eff = 0.25))) // 40 - 10
        assertEquals(listOf(0L, 9L, 0L, 0L), n(atk(false, damage = 9)))
        assertEquals(listOf(0L, 0L, 0L, 1L), n(atk(true, damage = 0, missed = true)))
        assertEquals(listOf(0L, 0L, 0L, 1L), n(atk(false, damage = 0, missed = true)))
        assertEquals(listOf(0L, 0L, 0L, 0L), n(atk(false, damage = 0, eff = 0.0)))
        assertEquals(listOf(0L, 0L, 0L, 0L), n(atk(true, damage = 7, cat = Category.STATUS)))
        assertEquals(listOf(0L, 0L, 0L, 0L), n(atk(false, damage = 7, failed = true, eff = 0.5)))
    }

    // ---- the day log ----

    @Test fun recordSumsTheFourBattleNumbersFromAScriptedBattle() {
        val s = GameState(day = "2026-10-13")
        StudyStats.record(s, round())
        val d = s.history.getValue("2026-10-13")
        assertEquals(42, d.dmgDealt)
        assertEquals(28, d.dmgTaken)
        assertEquals(8, d.dmgBlocked)
        assertEquals(2, d.misses)
    }

    @Test fun theDayLogEqualsTheAchievementCountersForTheSameEvents() {
        val events = round() + round().take(3)
        val s = GameState(day = "2026-10-13")
        StudyStats.record(s, events)
        Achievements.count(s, events)
        val d = s.history.getValue("2026-10-13")
        assertEquals(counter(s, Metric.DAMAGE_DEALT), d.dmgDealt.toLong())
        assertEquals(counter(s, Metric.DAMAGE_TAKEN), d.dmgTaken.toLong())
        assertEquals(counter(s, Metric.DAMAGE_BLOCKED), d.dmgBlocked.toLong())
        assertEquals(counter(s, Metric.MISSES), d.misses.toLong())
        assertEquals(listOf(84, 28, 8, 3), listOf(d.dmgDealt, d.dmgTaken, d.dmgBlocked, d.misses)) // two rounds, the second cut after the miss
    }

    @Test fun aDayWithOnlyAnAttackIsStillRecordedAndAnIgnoredAnswerAddsNothing() {
        val s = GameState(day = "2026-10-13")
        StudyStats.record(s, listOf(GameEvent.Ignored("Answer repeated after an undo")))
        assertNull(s.history["2026-10-13"])
        StudyStats.record(s, listOf(atk(true, damage = 15)))
        assertEquals(15, s.history.getValue("2026-10-13").dmgDealt)
        StudyStats.record(s, listOf(atk(false, damage = 0, missed = true)))
        assertEquals(1, s.history.getValue("2026-10-13").misses)
        assertEquals(0, s.history.getValue("2026-10-13").reviews)
    }

    @Test fun noGameDayRecordsNothing() {
        val s = GameState()
        StudyStats.record(s, round())
        assertTrue(s.history.isEmpty())
    }

    @Test fun numbersAddUpAcrossCallsAndStayOnTheirDay() {
        val s = GameState(day = "2026-10-13")
        StudyStats.record(s, round()); StudyStats.record(s, round())
        s.day = "2026-10-14"
        StudyStats.record(s, round().take(2))
        assertEquals(84, s.history.getValue("2026-10-13").dmgDealt)
        assertEquals(56, s.history.getValue("2026-10-13").dmgTaken)
        assertEquals(16, s.history.getValue("2026-10-13").dmgBlocked)
        assertEquals(4, s.history.getValue("2026-10-13").misses)
        assertEquals(42, s.history.getValue("2026-10-14").dmgDealt)
        assertEquals(0, s.history.getValue("2026-10-14").dmgTaken)
    }

    @Test fun otherNumbersOfTheDayAreKept() {
        val s = GameState(day = "2026-10-13")
        s.history["2026-10-13"] = DayLog(reviews = 12, xp = 30, cash = 40, spent = 5, healItems = 2)
        StudyStats.record(s, round())
        val d = s.history.getValue("2026-10-13")
        assertEquals(12, d.reviews); assertEquals(30, d.xp); assertEquals(40, d.cash); assertEquals(5, d.spent); assertEquals(2, d.healItems)
        assertEquals(42, d.dmgDealt)
    }

    // ---- through the game ----

    private var time = NOW
    private fun game(s: GameState = state(), seed: Int = 1) = Game(data, s, Random(seed)) { time }
    private fun Game.answer(button: Int, card: Long) = onAnswer(Answer(card, button, time))

    @Test fun throughTheGameTheDayLogAndTheCountersAgreeAndARedoDoesNotCountTwice() {
        val g = game()
        g.roundAnswers = 1
        g.state.battle = Battle(TestData.mon(data, 19, 15, listOf("tackle"), uid = "wild"), Tier.NORMAL, startedAt = time)
        val events = g.answer(3, 1) + g.answer(3, 2)
        val attacks = events.filterIsInstance<GameEvent.Attack>()
        assertTrue(attacks.isNotEmpty())
        val d = g.state.history.getValue(g.state.day)
        assertEquals(attacks.sumOf { Achievements.attackNumbers(it).dealt.toInt() }, d.dmgDealt)
        assertEquals(attacks.sumOf { Achievements.attackNumbers(it).taken.toInt() }, d.dmgTaken)
        assertEquals(attacks.sumOf { Achievements.attackNumbers(it).blocked.toInt() }, d.dmgBlocked)
        assertEquals(attacks.count { it.missed }, d.misses)
        assertEquals(counter(g.state, Metric.DAMAGE_DEALT), d.dmgDealt.toLong())
        assertEquals(counter(g.state, Metric.DAMAGE_TAKEN), d.dmgTaken.toLong())
        assertEquals(counter(g.state, Metric.DAMAGE_BLOCKED), d.dmgBlocked.toLong())
        assertEquals(counter(g.state, Metric.MISSES), d.misses.toLong())

        g.onUndo(2, 1)                       // AnkiDroid undid card 2; the same answer comes again and is ignored
        val redo = g.answer(3, 2)
        assertTrue(redo.any { it is GameEvent.Ignored })
        assertEquals(d.dmgDealt, g.state.history.getValue(g.state.day).dmgDealt)
        assertEquals(d.misses, g.state.history.getValue(g.state.day).misses)
        assertEquals(counter(g.state, Metric.DAMAGE_DEALT), g.state.history.getValue(g.state.day).dmgDealt.toLong())
    }

    // ---- all-time numbers ----

    @Test fun theSummaryReadsTheAllTimeCountersAndZerosWithoutThem() {
        val s = state()
        val none = StudyStats.summary(s, s.day)
        assertEquals(listOf(0L, 0L, 0L, 0L), listOf(none.damageDealt, none.damageTaken, none.damageBlocked, none.misses))
        Achievements.count(s, round())
        val sum = StudyStats.summary(s, s.day)
        assertEquals(42L, sum.damageDealt); assertEquals(28L, sum.damageTaken); assertEquals(8L, sum.damageBlocked); assertEquals(2L, sum.misses)
    }
}
