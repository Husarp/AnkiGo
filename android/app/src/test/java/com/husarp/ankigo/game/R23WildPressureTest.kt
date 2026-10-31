package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R23WildPressureTest {
    private val data = TestData.world()
    private fun mon(vararg moves: String) = TestData.mon(data, 150, 40, moves.toList())
    private fun battle() = Battle(mon("tackle", "bodyslam", "growl"), Tier.NORMAL)

    @Test fun againStreakCrossesRoundsButBoostsOnlyOnceUntilANonAgain() {
        val b = battle()
        assertFalse(RoundRule.answerBoost(b, 1))
        assertTrue(RoundRule.answerBoost(b, 1))
        repeat(8) { assertFalse(RoundRule.answerBoost(b, 1)) }
        assertFalse(RoundRule.answerBoost(b, 2))
        assertFalse(RoundRule.answerBoost(b, 1))
        assertTrue(RoundRule.answerBoost(b, 1))
        RoundRule.answerBoost(b, 3)
        assertFalse(RoundRule.answerBoost(b, 1))
        RoundRule.answerBoost(b, 4)
        assertFalse(RoundRule.answerBoost(b, 1))
    }

    @Test fun okNeitherCountsAsAgainNorBreaksItsStreak() {
        val b = battle()
        repeat(4) { assertFalse(RoundRule.answerBoost(b, Tuning.BUTTON_OK)) }
        assertFalse(RoundRule.answerBoost(b, 1))
        assertFalse(RoundRule.answerBoost(b, Tuning.BUTTON_OK))
        assertTrue(RoundRule.answerBoost(b, 1))
        assertFalse(RoundRule.answerBoost(b, Tuning.BUTTON_OK))
        assertFalse(RoundRule.answerBoost(b, 1))
    }

    @Test fun normalBoostPathProducesStageAndLogAndHonorsLimit() {
        val b = battle(); val main = mon("tackle")
        val ev = BattleEngine.weaknessBoost(data, b, main, Random(1), MoveChoice.SMART)
        assertEquals(1, b.enemy.boosts[0])
        val stage = ev.filterIsInstance<GameEvent.Effect>().single { it.kind == EffectKind.STAT_CHANGE }
        assertFalse(stage.onPlayer)
        assertEquals("atk", stage.stat)
        assertEquals(1, stage.stages)
        assertTrue(ev.filterIsInstance<GameEvent.Effect>().any { it.text.contains("Attack rose") })
        assertTrue(ev.filterIsInstance<GameEvent.Effect>().any { it.text.contains("smells weakness") })
        repeat(10) { BattleEngine.weaknessBoost(data, b, main, Random(1), MoveChoice.SMART) }
        assertEquals(6, b.enemy.boosts[0])
    }

    @Test fun hardPowerIsSeventyFivePercentAndOkHasNoPower() {
        assertEquals(0.75, RoundRule.plan(listOf(2, Tuning.BUTTON_OK)).enemyPower, 0.0)
        assertEquals(1.5, RoundRule.plan(listOf(2, 2)).enemyPower, 0.0)
    }

    @Test fun thirdBadRoundForcesOnceAndGoodEasyOrUnknownReset() {
        val b = battle()
        repeat(2) {
            assertFalse(RoundRule.strongestRound(b, listOf(1, 2)))
            assertFalse(RoundRule.strongestRound(b, listOf(2, 2)))
            assertTrue(RoundRule.strongestRound(b, listOf(1, 1)))
        }
        for (good in listOf(0, 3, 4)) {
            assertFalse(RoundRule.strongestRound(b, listOf(2)))
            assertFalse(RoundRule.strongestRound(b, listOf(2, good)))
            assertEquals(0, b.pressuredRounds)
        }
    }

    @Test fun okOnlyRoundPausesCountAndMixedOkRoundCounts() {
        val b = battle()
        assertFalse(RoundRule.strongestRound(b, listOf(1, Tuning.BUTTON_OK)))
        repeat(4) { assertFalse(RoundRule.strongestRound(b, listOf(Tuning.BUTTON_OK, Tuning.BUTTON_OK))) }
        assertEquals(1, b.pressuredRounds)
        assertFalse(RoundRule.strongestRound(b, listOf(2, Tuning.BUTTON_OK)))
        assertTrue(RoundRule.strongestRound(b, listOf(2, Tuning.BUTTON_OK)))
    }

    @Test fun strongestIsAlwaysTopExpectedDamageAndAnnouncedInEveryChoiceMode() {
        for (choice in MoveChoice.entries) repeat(10) { seed ->
            val b = battle(); val main = mon("tackle")
            val view = SimpleView.of(data, b, main, mainActs = false)
            val best = view.moves(false).filter { it.damaging }.maxByOrNull { MoveChooser.expectedDamage(view, false, it) }!!
            val ev = BattleEngine.playRound(data, b, main, 0.0, true, Random(seed), mainActs = false, moveChoice = choice, forceStrongest = true)
            assertEquals(best.id, ev.filterIsInstance<GameEvent.Attack>().single().moveId)
            assertTrue(ev.filterIsInstance<GameEvent.Effect>().any { it.text.contains("strongest move: ${best.name}") })
        }
    }

    @Test fun strongestRanksAccuracyAndTypeInsteadOfBasePower() {
        val inaccurate = Move("bigmiss", "Big Miss", Type.NORMAL, Category.PHYSICAL, 200, 10)
        val exactData = TestData.data(data.species.values.toList(), TestData.MOVES + (inaccurate.id to inaccurate))
        val wild = TestData.mon(exactData, 150, 40, listOf("bigmiss", "bodyslam"))
        val main = TestData.mon(exactData, 150, 40, listOf("tackle"))
        assertEquals("bodyslam", MoveChooser.pickStrongest(SimpleView.of(exactData, Battle(wild, Tier.NORMAL), main), false, Random(1)).id)
        val ghost = TestData.mon(data, 92, 40, listOf("tackle"))
        val typed = Battle(mon("bodyslam", "shadowball"), Tier.NORMAL)
        assertEquals("shadowball", MoveChooser.pickStrongest(SimpleView.of(data, typed, ghost), false, Random(1)).id)
    }

    @Test fun gameWiresAnswerBoostBeforeRoundAndForcesThirdBadRound() {
        val state = TestData.state(data, 150, 100, listOf("growl"))
        val now = 1793361600000L
        val game = Game(data, state, Random(17)) { now }
        game.tick()
        val b = battle(); b.startedAt = now; state.battle = b
        var card = 100L
        fun answer(button: Int, neutral: Boolean = false) = game.onAnswer(Answer(card++, button, now, neutral = neutral))
        assertFalse(answer(1).filterIsInstance<GameEvent.Effect>().any { it.text.contains("Attack rose") })
        assertTrue(answer(1).filterIsInstance<GameEvent.Effect>().any { it.text.contains("Attack rose") })
        assertEquals(1, b.enemy.boosts[0])
        answer(1, neutral = true); answer(1, neutral = true)
        assertEquals(1, b.pressuredRounds)
        answer(2); answer(2)
        answer(2)
        val ev = answer(2)
        assertTrue(ev.filterIsInstance<GameEvent.Effect>().any { it.text.contains("strongest move") })
        assertEquals(0, b.pressuredRounds)
        assertSame(b, state.battle)
    }

    @Test fun forcedMoveRulesAndNoDamagingMoveRemainUsable() {
        val b = battle(); val main = mon("tackle")
        b.enemy.charging = "tackle"
        assertEquals("tackle", MoveChooser.pickStrongest(SimpleView.of(data, b, main), false, Random(1)).id)
        val statusOnly = Battle(mon("growl"), Tier.NORMAL)
        assertEquals("growl", MoveChooser.pickStrongest(SimpleView.of(data, statusOnly, main), false, Random(1)).id)
    }

    @Test fun oldSaveDefaultsCountersToZero() {
        val state = GameState(); state.battle = battle()
        val json = JSONObject(SaveCodec.encode(state))
        json.getJSONObject("battle").remove("againStreak")
        json.getJSONObject("battle").remove("pressuredRounds")
        val back = SaveCodec.decode(json.toString()).battle!!
        assertEquals(0, back.againStreak)
        assertEquals(0, back.pressuredRounds)
    }

    @Test fun countersSurviveSaveAndNewBattleStartsClean() {
        val b = battle(); b.againStreak = 1; b.pressuredRounds = 2
        val state = GameState(); state.battle = b
        val back = SaveCodec.decode(SaveCodec.encode(state)).battle!!
        assertTrue(RoundRule.answerBoost(back, 1))
        assertTrue(RoundRule.strongestRound(back, listOf(2)))
        assertEquals(0, battle().againStreak)
        assertEquals(0, battle().pressuredRounds)
    }
}
