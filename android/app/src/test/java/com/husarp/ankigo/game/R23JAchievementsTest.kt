package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime
import java.time.ZoneId

class R23JAchievementsTest {
    private val data = TestData.world()
    private fun state() = TestData.state(data, 25, 20, listOf("tackle"))
    private fun has(s: GameState, m: Metric) = (s.counters[m.name] ?: 0L) > 0
    private fun answer(button: Int = 4, combo: Int = 1) = GameEvent.Counted(button, 20, 2, combo, false)
    private fun attack(damage: Int = 100, hp: Int = 0) = GameEvent.Attack(true, "main", "wild", "move", Type.NORMAL,
        Category.PHYSICAL, damage, 1, 1.0, false, false, false, hp, 100, 10, 100)
    private fun count(s: GameState, vararg events: GameEvent) = SecretAchievements.count(s, 0L, events.toList())

    @Test fun finalTableAndHiddenSecrets() {
        val s = state()
        assertEquals(48, AchievementTable.ROWS.size)
        assertEquals(117, AchievementTable.ROWS.sumOf { it.tiers.size })
        for (id in listOf("damage-dealt", "damage-taken", "damage-blocked", "misses", "super-effective", "dex-caught", "dex-seen", "balls-thrown", "money-spent", "items-used")) assertNull(AchievementTable.row(id))
        for (id in listOf("ball-catches", "tms", "items-found")) assertEquals(3, AchievementTable.row(id)!!.tiers.size)
        val secrets = Achievements.view(s, data).filter { it.group == "Secret" }
        assertEquals(11, secrets.size)
        assertTrue(secrets.all { it.title == "???" && it.desc == "???" })
        Achievements.bump(s, Metric.ONE_SHOT)
        assertEquals("One-shot", Achievements.view(s, data).first { it.id == "one-shot" }.title)
        assertEquals(listOf(60L, 600L, 3000L, 12000L, 30000L), AchievementTable.row("study-time")!!.tiers)
        assertEquals("0.50 h", AchPageMath.fmt(Metric.STUDY_MINUTES, 30))
    }

    @Test fun oneShotRequiresFullHpInOneMove() {
        val s = state(); count(s, attack(99), attack(50, 50)); assertFalse(has(s, Metric.ONE_SHOT))
        count(s, attack()); assertTrue(has(s, Metric.ONE_SHOT))
    }
    @Test fun lastBreathAndLevelGapUsePreRewardWinner() {
        val s = state(); val main = s.main!!
        val wild = state().main!!.also { it.uid = "wild" }
        val b = Battle(wild, Tier.NORMAL)
        main.hp = 2; wild.level = main.level + 19
        SecretAchievements.win(s, b, main)
        assertFalse(has(s, Metric.LAST_BREATH)); assertFalse(has(s, Metric.DAVID_GOLIATH))
        main.hp = 1; wild.level++
        SecretAchievements.win(s, b, main)
        assertTrue(has(s, Metric.LAST_BREATH)); assertTrue(has(s, Metric.DAVID_GOLIATH))
    }
    @Test fun twentyCombo() {
        val s = state(); count(s, answer(combo = 19)); assertFalse(has(s, Metric.COMBO_20))
        count(s, answer(combo = 20)); assertTrue(has(s, Metric.COMBO_20))
    }
    @Test fun sixSwitchesInSameEncounterSurviveSave() {
        var s = state(); val b = Battle(TestData.state(data, 25, 20, listOf("tackle")).main!!, Tier.NORMAL)
        repeat(5) { SecretAchievements.switch(s, b) }; assertFalse(has(s, Metric.REVOLVING_DOOR))
        s = SaveCodec.decode(SaveCodec.encode(s)); SecretAchievements.switch(s, b)
        assertTrue(has(s, Metric.REVOLVING_DOOR))
        s = state(); repeat(5) { SecretAchievements.switch(s, b) }
        val other = Battle(state().main!!.also { it.uid = "other" }, Tier.NORMAL); SecretAchievements.switch(s, other)
        assertFalse(has(s, Metric.REVOLVING_DOOR))
    }
    @Test fun nightOwlOnlyAtThree() {
        val s = state(); val zone = ZoneId.systemDefault()
        fun at(hour: Int) = ZonedDateTime.of(2026, 10, 30, hour, 30, 0, 0, zone).toInstant().toEpochMilli()
        SecretAchievements.count(s, at(2), listOf(answer())); assertFalse(has(s, Metric.NIGHT_OWL))
        SecretAchievements.count(s, at(3), listOf(GameEvent.Info("ignored"))); assertFalse(has(s, Metric.NIGHT_OWL))
        SecretAchievements.count(s, at(3), listOf(answer())); assertTrue(has(s, Metric.NIGHT_OWL))
    }
    @Test fun nightOwlUsesAnswerTimeEvenWhenDeliveredLater() {
        val s = state(); val at = ZonedDateTime.of(2026, 10, 30, 3, 30, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
        val game = Game(data, s, TestData.rng()) { at + 12 * 3600000L }
        game.onAnswer(Answer(1L, 3, at, catchUp = true))
        assertTrue(has(s, Metric.NIGHT_OWL))
    }
    @Test fun sevenEasyConsecutiveAcrossSave() {
        var s = state(); repeat(6) { count(s, answer()) }; count(s, answer(3)); repeat(6) { count(s, answer()) }
        assertFalse(has(s, Metric.EASY_STREET)); s = SaveCodec.decode(SaveCodec.encode(s)); count(s, answer())
        assertTrue(has(s, Metric.EASY_STREET))
    }
    @Test fun fiveActualCatchesOfSameSpeciesAcrossSave() {
        var s = state()
        fun caught(species: Int) = GameEvent.Caught("uid", species, "wild", 10, false, null, false, false)
        repeat(4) { count(s, caught(25)) }; count(s, caught(1)); assertFalse(has(s, Metric.DEJA_VU))
        s = SaveCodec.decode(SaveCodec.encode(s)); count(s, caught(25)); assertTrue(has(s, Metric.DEJA_VU))
    }
    @Test fun perfectThrowChecksBallHpFirstThrowAndSuccess() {
        fun run(ball: String, hurt: Boolean = false, throws: Int = 0, success: Boolean = true): GameState {
            val s = state(); val wild = state().main!!.also { it.uid = "wild" }
            if (hurt) wild.hp--
            s.battle = Battle(wild, Tier.NORMAL, failedThrows = throws)
            s.bag[ball] = 1
            val rng = object : kotlin.random.Random() {
                override fun nextBits(bitCount: Int) = 0
                override fun nextInt(until: Int) = if (success) 0 else until - 1
            }
            Game(data, s, rng) { 1000L }.throwBall(ball)
            return s
        }
        assertTrue(has(run("poke-ball"), Metric.PERFECT_THROW))
        assertFalse(has(run("great-ball"), Metric.PERFECT_THROW))
        assertFalse(has(run("poke-ball", hurt = true), Metric.PERFECT_THROW))
        assertFalse(has(run("poke-ball", throws = 1), Metric.PERFECT_THROW))
        assertFalse(has(run("poke-ball", success = false), Metric.PERFECT_THROW))
    }
    @Test fun evolutionRequiresAnActiveBattle() {
        val s = state(); val evo = Evolution(133, EvoKind.LEVEL)
        Progression.evolve(data, s, s.main!!, evo, TestData.rng(), mutableListOf())
        assertFalse(has(s, Metric.GLOW_UP))
        s.battle = Battle(state().main!!, Tier.NORMAL)
        Progression.evolve(data, s, s.main!!, Evolution(25, EvoKind.LEVEL), TestData.rng(), mutableListOf())
        assertTrue(has(s, Metric.GLOW_UP))
    }
    @Test fun realSwitchesAwardOnceAndSecretCelebratesAsOneOff() {
        val s = state(); val second = TestData.mon(data, 19, 20, uid = "second")
        s.mons += second; s.team += second.uid
        s.battle = Battle(TestData.mon(data, 19, 20, listOf("splash"), "wild"), Tier.NORMAL)
        Achievements.onReady(s, data, 1000L)
        val game = Game(data, s, TestData.rng()) { 2000L }
        val events = mutableListOf<GameEvent>()
        repeat(6) { events += game.setMain(if (it % 2 == 0) "second" else "main").events }
        assertEquals(1, events.filterIsInstance<GameEvent.AchievementReached>().count { it.id == "revolving-door" })
        val celebration = AchPageMath.celebrations(events).first { it.rowId == "revolving-door" }
        assertEquals(-1, celebration.tier); assertEquals("Achievement!", celebration.word)
        assertTrue(game.setMain("second").events.none { it is GameEvent.AchievementReached && it.id == "revolving-door" })
    }
    @Test fun oldRemovedAndHigherTiersLoadWithoutTakingRewards() {
        val s = state(); s.achLadder = 3; s.cash = 12345
        s.achTiers["damage-dealt"] = mutableListOf(1, 2, 3, 4, 5)
        s.achTiers["future-id"] = mutableListOf(1)
        for (id in listOf("ball-catches", "tms", "items-found")) s.achTiers[id] = mutableListOf(1, 2, 3, 4, 5)
        s.bag["master-ball"] = 1
        s.rewardsClaimed += "streak-days/4"
        val back = SaveCodec.decode(SaveCodec.encode(s)); Achievements.onReady(back, data, 1000)
        assertEquals(12345, back.cash); assertEquals(1, back.count("master-ball"))
        assertTrue("streak-days/4" in back.rewardsClaimed)
        val views = Achievements.view(back, data)
        assertFalse(views.any { it.id == "damage-dealt" || it.id == "future-id" })
        for (id in listOf("ball-catches", "tms", "items-found")) assertEquals(2, views.first { it.id == id }.tier)
    }
}
