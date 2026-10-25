package com.husarp.ankigo.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.random.Random

/**
 * PLAN 155 (R14-B2-F): the OK of a new card is a neutral answer: counted like an Again press, no combo change.
 * R19-T (PLAN 284): it is also one of the round's answers, with no power (the round rules are in R19TTurnsTest).
 */
class NeutralAnswerTest {
    private val data = TestData.world()
    private val now = ZonedDateTime.of(2026, 10, 13, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    private var nextCard = 1L
    private lateinit var savedZone: TimeZone

    @Before fun utcZone() { savedZone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }
    @After fun restoreZone() = TimeZone.setDefault(savedZone)

    private fun game(seed: Int = 1) = Game(data, TestData.state(data, 25, 20, listOf("tackle")), Random(seed)) { now }

    private fun Game.fight(hp: Int = 36): Mon {
        val w = TestData.mon(data, 19, 15, listOf("tackle"), uid = "wild")
        w.hp = hp
        state.battle = Battle(w, Tier.NORMAL, startedAt = now)
        return w
    }

    private fun Game.answer(button: Int, neutral: Boolean = false) = onAnswer(Answer(nextCard++, button, now, false, neutral))

    private inline fun <reified T : GameEvent> List<GameEvent>.of() = filterIsInstance<T>()

    @Test fun anOkIsCountedLikeAnAgainPress() {
        val g = game(); g.fight()
        val ev = g.answer(1, neutral = true)
        assertEquals(GameEvent.Counted(1, 0, 1, 0, false, true), ev.of<GameEvent.Counted>().single())
        assertTrue(ev.of<GameEvent.Counted>().single().neutral)
        assertEquals(1, g.state.reviewsToday); assertEquals(1L, g.state.totalReviews)
        assertEquals(1, g.state.buttonsToday[1])
    }

    @Test fun anOkIsOneOfTheRoundsAnswersWithNoPowerAndNobodyAttacksYet() {   // R19-T (PLAN 284): it used to add no charge at all
        val g = game(); val wild = g.fight(hp = 36)
        val mainHp = g.state.main!!.hp
        val ev = g.answer(1, neutral = true)
        assertEquals(listOf(GameEvent.Charge(0, 1, 2)), ev.of<GameEvent.Charge>())
        assertTrue(ev.of<GameEvent.RoundStart>().isEmpty())
        assertTrue(ev.of<GameEvent.Attack>().isEmpty())
        assertTrue(ev.of<GameEvent.Splash>().isEmpty())
        val b = g.state.battle!!
        assertEquals(1, b.answers); assertEquals(0, b.tally); assertEquals(listOf(Tuning.BUTTON_OK), b.buttons)
        assertEquals(36, wild.hp); assertEquals(mainHp, g.state.main!!.hp)
    }

    @Test fun anOkKeepsTheCombo() {
        val g = game(); g.fight()
        g.answer(3); g.answer(4); g.answer(3)   // one full round and one answer in
        assertEquals(3, g.state.cardStreak)
        val ev = g.answer(1, neutral = true)
        assertEquals(3, g.state.cardStreak)
        assertEquals(3, ev.of<GameEvent.Counted>().single().cardStreak)
        assertEquals(3, g.state.bestCardStreak)
        g.answer(3)
        assertEquals(4, g.state.cardStreak)   // not 5: the OK did not count as a step either
    }

    @Test fun aRealAgainBreaksTheComboButAnOkDoesNot() {
        val a = game(); a.fight(); a.answer(3); a.answer(3); a.answer(1)
        assertEquals(0, a.state.cardStreak)
        val b = game(); b.fight(); b.answer(3); b.answer(3); b.answer(1, neutral = true)
        assertEquals(2, b.state.cardStreak)
    }

    @Test fun moneyAndTheItemCountdownRunLikeForAnyAnswer() {
        val twinA = game(); twinA.fight(); val plain = twinA.answer(1)
        val twinB = game(); twinB.fight(); val ok = twinB.answer(1, neutral = true)
        assertEquals(twinA.state.cash, twinB.state.cash)
        assertEquals(twinA.state.cashToday, twinB.state.cashToday)
        assertEquals(twinA.state.itemCountdown, twinB.state.itemCountdown)
        assertEquals(plain.of<GameEvent.Cash>(), ok.of<GameEvent.Cash>())
        assertEquals(plain.of<GameEvent.ItemFound>(), ok.of<GameEvent.ItemFound>())
    }

    @Test fun anOkCanReachTheDailyGoal() {
        val g = game(); g.fight()
        g.state.reviewsToday = DailyGoal.today(g.state) - 1
        val ev = g.answer(1, neutral = true)
        assertTrue(ev.of<GameEvent.Info>().any { it.text == "Daily goal reached!" })
        assertEquals(g.state.day, g.state.lastGoalDay)
    }

    @Test fun aNormalAnswerAfterAnOkCompletesTheRoundWithThatAnswersPowerOnly() {   // R19-T (PLAN 284)
        val with = game(); with.fight(); with.answer(1, neutral = true)
        assertEquals(1, with.state.battle!!.answers)   // the OK filled the first slot
        val evWith = with.answer(3)
        val start = evWith.of<GameEvent.RoundStart>().single()
        assertEquals(RoundRule.plan(listOf(3)).multiplier, start.multiplier, 0.0)   // Good's strength alone, not Good + Good
        assertEquals(10, start.points)
        assertEquals(0, with.state.battle!!.answers); assertEquals(0, with.state.battle!!.tally); assertTrue(with.state.battle!!.buttons.isEmpty())
    }

    @Test fun anOkFirstThingInTheDayStillStartsTheFightAndFillsADot() {
        val g = game(); g.state.battle = null
        val ev = g.answer(1, neutral = true)
        assertEquals(1, ev.of<GameEvent.Encounter>().size)
        assertEquals(1, ev.of<GameEvent.Charge>().size)   // R19-T (PLAN 284): the first answer of the fight, in grey
        val b = g.state.battle!!
        assertEquals(1, b.answers); assertEquals(listOf(Tuning.BUTTON_OK), b.buttons)
    }

    @Test fun offIsExactlyToday() {
        assertFalse(Answer(1, 3, 5).neutral)
        val a = game(); a.fight(); val x = a.answer(1)
        assertFalse(x.of<GameEvent.Counted>().single().neutral)
        assertEquals(1, x.of<GameEvent.Charge>().size)
        assertEquals(1, a.state.battle!!.answers)
        assertEquals(0, a.state.cardStreak)
    }
}
