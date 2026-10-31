package com.husarp.ankigo.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PracticeRewardsTest {
    private val data = TestData.world()
    private fun game() = Game(data, TestData.state(data, 25, 20, listOf("tackle")), Random(7)) { 1_790_000_000_000L }
    @Test fun practiceAnswersKeepBattlesButNeverPayAnswerMoney() {
        val g = game(); val before = g.state.cash
        val events = g.onPracticeAnswer(Answer(42, 3, g.clock()))
        assertTrue(events.any { it is GameEvent.Counted })
        assertNotNull(g.state.battle)
        assertEquals(before, g.state.cash)
        assertFalse(events.any { it is GameEvent.Cash })
        assertFalse(g.state.practiceRewards)
        g.onAnswer(Answer(43, 3, g.clock()))
        assertTrue(g.state.cash > before)
    }
    @Test fun practiceWinPaysHalfPokemonAndTrainerXpAndNoWinMoney() {
        fun run(practice: Boolean): Pair<Game, List<GameEvent>> {
            val g = game()
            g.roundAnswers = 1
            val wild = TestData.mon(data, 19, 15, listOf("tackle"), uid = "wild").apply { hp = 1 }
            g.state.battle = Battle(wild, Tier.NORMAL, startedAt = g.clock())
            g.state.settings.autoMode = 2
            return g to if (practice) g.onPracticeAnswer(Answer(42, 4, g.clock())) else g.onAnswer(Answer(42, 4, g.clock()))
        }
        val (normal, normalEvents) = run(false)
        val (practice, practiceEvents) = run(true)
        assertTrue(normalEvents.any { it is GameEvent.Defeated })
        assertTrue(practiceEvents.any { it is GameEvent.Defeated })
        assertEquals(normalEvents.filterIsInstance<GameEvent.XpGained>().map { it.xp / 2 }, practiceEvents.filterIsInstance<GameEvent.XpGained>().map { it.xp })
        assertEquals(normal.state.trainerXp / 2, practice.state.trainerXp)
        assertEquals(normalEvents.filterIsInstance<GameEvent.Defeated>().single().xp / 2, practiceEvents.filterIsInstance<GameEvent.Defeated>().single().xp)
        assertFalse(practiceEvents.any { it is GameEvent.Cash })
        assertFalse(practice.state.practiceRewards)
    }
    @Test fun practicePreservesTheExistingNeutralNewCardRule() {
        val g = game(); g.state.cardStreak = 7
        val events = g.onPracticeAnswer(Answer(42, 1, g.clock(), neutral = true))
        assertEquals(7, g.state.cardStreak)
        assertTrue(events.filterIsInstance<GameEvent.Counted>().single().neutral)
        assertEquals(listOf(Tuning.BUTTON_OK), g.state.battle!!.buttons)
        assertFalse(events.any { it is GameEvent.Cash })
    }
    @Test fun localPracticeDoesNotConsumeAnkiUndoRedoMarkers() {
        val g = game(); g.tick()
        g.state.redo[42] = 1
        val events = g.onPracticeAnswer(Answer(42, 3, g.clock()))
        assertTrue(events.any { it is GameEvent.Counted })
        assertEquals(1, g.state.redo[42])
        assertTrue(g.onAnswer(Answer(42, 3, g.clock())).any { it is GameEvent.Ignored })
    }
    @Test fun catchRewardsAndExpShareAlsoUseHalfXp() {
        fun rewards(practice: Boolean): List<Int> {
            val g = game()
            val holder = TestData.mon(data, 19, 15, listOf("tackle"), uid = "holder")
            g.state.mons += holder
            g.state.settings.xpShareUid = holder.uid
            g.state.practiceRewards = practice
            val events = mutableListOf<GameEvent>()
            Progression.rewardCatch(data, g.state, g.state.main!!, TestData.mon(data, 19, 15, listOf("tackle"), uid = "wild"), g.clock(), Random(3), events)
            return events.filterIsInstance<GameEvent.XpGained>().map { it.xp }
        }
        val normal = rewards(false)
        assertEquals(2, normal.size)
        assertEquals(normal.map { it / 2 }, rewards(true))
    }
    @Test fun halfXpRoundsDownAndNormalRewardsAreUnchanged() {
        assertEquals(2, Tuning.studyXp(5, true)); assertEquals(5, Tuning.studyXp(5, false))
    }
}
