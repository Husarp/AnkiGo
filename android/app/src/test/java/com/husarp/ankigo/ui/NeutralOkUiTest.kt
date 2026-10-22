package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.debug.GameEventText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PLAN 155 (R14-B2-F): how the presentation treats the neutral OK of a new card (log row, charge dots, floats, debug text, the log file). */
class NeutralOkUiTest {
    private var t = 1000L
    private fun builder() = BattleLogBuilder(5, { t++ })
    private fun encounter() = GameEvent.Encounter(1, "Wildling", 7, false, Tier.NORMAL, 30, 30, true, false)
    private fun counted(button: Int, neutral: Boolean = false) = GameEvent.Counted(button, if (button == 1) 0 else 10, 1, 1, false, neutral)

    @Test fun theLogShowsOkNotAgainAndTheRoundKeepsCollecting() {
        val b = builder()
        b.feed(encounter())
        b.answer("study", 11, 1, "A new card")
        b.feed(listOf(counted(1, neutral = true)))
        b.answer("study", 12, 3, "Next card")
        b.feed(listOf(counted(3), GameEvent.Charge(10, 1, 2)))
        val round = b.battles.single().rounds.single()
        assertEquals(listOf(BattleLogBuilder.OK, 3), round.answers.map { it.button })
        assertEquals(listOf("A new card", "Next card"), round.answers.map { it.card })
        assertEquals("OK", BattleLogBuilder.buttonLabel(round.answers[0].button))
        assertEquals("Again", BattleLogBuilder.buttonLabel(1))
        assertTrue("the round has no multiplier yet", round.multiplier == null)
    }

    @Test fun anOkAnswerKeepsItsCardAndIsNotMixedUpWithARealAgain() {
        val b = builder()
        b.feed(encounter())
        b.answer("study", 11, 1, "New one")
        b.answer("study", 12, 1, "Real again")
        b.feed(listOf(counted(1, neutral = true)))
        b.feed(listOf(counted(1)))
        val answers = b.battles.single().rounds.flatMap { it.answers }
        assertEquals(listOf(BattleLogBuilder.OK, 1), answers.map { it.button })
        assertEquals(listOf("New one", "Real again"), answers.map { it.card })
    }

    @Test fun theLogFileKeepsTheOkRow() {
        val b = builder()
        b.feed(encounter())
        b.answer("study", 11, 1, "A new card")
        b.feed(listOf(counted(1, neutral = true)))
        val back = BattleLogCodec.decode(BattleLogCodec.encode(b.battles))
        assertEquals(BattleLogBuilder.OK, back.single().rounds.single().answers.single().button)
    }

    @Test fun theChargeDotsStayAsTheyAre() {
        val c = StudyCharge()
        c.observe(counted(3), 2); c.observe(GameEvent.Charge(10, 1, 2), 2)
        assertEquals(listOf(3), c.filled)
        c.observe(counted(1, neutral = true), 2)
        assertEquals("the OK fills no pip and clears nothing", listOf(3), c.filled)
        c.observe(counted(4), 2); c.observe(GameEvent.RoundStart(1.5, 30, false), 2)
        assertEquals(listOf(3, 4), c.filled)
        c.observe(counted(1, neutral = true), 2)
        assertEquals("not even after a finished round", listOf(3, 4), c.filled)
    }

    @Test fun noHesitatedFloatForAnOkButStillForARealAgain() {
        assertTrue(FloatTexts.of(counted(1, neutral = true)).isEmpty())
        assertEquals(listOf(FloatKind.HESITATE), FloatTexts.of(counted(1)).map { it.kind })
    }

    @Test fun theDebugLogSaysNeutral() {
        assertTrue(GameEventText.event(counted(1, neutral = true)).endsWith(" neutral"))
        assertFalse(GameEventText.event(counted(1)).contains("neutral"))
    }
}
