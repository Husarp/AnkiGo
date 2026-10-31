package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.*
import org.junit.Test

class R23VBullyTest {
    private val data = TestData.world()
    private fun state() = TestData.state(data, 25, 40, listOf("tackle"))
    private fun fight(s: GameState, level: Int) {
        s.battle = Battle(TestData.mon(data, 19, level, listOf("splash"), "wild").also { it.hp = 1 }, Tier.NORMAL, override = Outcome.DEFEAT)
    }
    private fun earned(s: GameState) = (s.counters[Metric.BULLY.name] ?: 0L) > 0

    @Test fun thresholdAndDirection() {
        for (gap in listOf(-20, 0, 19, 20, 21)) {
            val s = state()
            fight(s, s.main!!.level - gap)
            SecretAchievements.win(s, s.battle!!, s.main!!)
            assertEquals("gap $gap", gap >= 20, earned(s))
        }
    }

    @Test fun actualDefeatRevealsOnceAndSurvivesSave() {
        val s = state()
        Achievements.onReady(s, data, 1000L)
        val hidden = Achievements.view(s, data).single { it.id == "bully" }
        assertEquals("???", hidden.title)
        assertEquals("???", hidden.desc)
        assertTrue(hidden.oneOff)
        val game = Game(data, s, TestData.rng()) { 2000L }
        fight(s, 20)
        assertFalse(earned(s))
        game.onAnswer(Answer(1L, 4, 2000L))
        val events = game.onAnswer(Answer(2L, 4, 2000L))
        assertEquals(1, events.filterIsInstance<GameEvent.AchievementReached>().count { it.id == "bully" })
        val back = SaveCodec.decode(SaveCodec.encode(s))
        assertTrue(earned(back))
        assertEquals("Bully", Achievements.view(back, data).single { it.id == "bully" }.title)
        fight(back, 20)
        val resumed = Game(data, back, TestData.rng()) { 3000L }
        resumed.onAnswer(Answer(3L, 4, 3000L))
        val again = resumed.onAnswer(Answer(4L, 4, 3000L))
        assertTrue(again.none { it is GameEvent.AchievementReached && it.id == "bully" })
        assertEquals(1, back.achTiers["bully"]!!.size)
    }

    @Test fun successfulCatchUsesExistingWinRuleButFailedThrowDoesNot() {
        for (success in listOf(false, true)) {
            val s = state()
            fight(s, 20)
            s.bag["poke-ball"] = 1
            val rng = object : kotlin.random.Random() {
                override fun nextBits(bitCount: Int) = 0
                override fun nextInt(until: Int) = if (success) 0 else until - 1
            }
            Game(data, s, rng) { 2000L }.throwBall("poke-ball")
            assertEquals(success, earned(s))
        }
    }
}
