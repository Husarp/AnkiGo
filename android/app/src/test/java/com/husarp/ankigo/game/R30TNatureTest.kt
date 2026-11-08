package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import com.husarp.ankigo.ui.DetailMath
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R30TNatureTest {
    private val data = TestData.world()
    private fun mon(n: Nature, uid: String = "main") = TestData.mon(data, 25, 20, uid = uid).let {
        Mon(it.uid, it.species, it.name, it.level, 0, it.ivs, it.evs, n, it.gender, false, it.ability, it.moves, it.hp)
    }
    private fun state(m: Mon) = GameState(starterChosen = true).also { it.mons += m; it.team += m.uid; it.battle = Battle(TestData.mon(data, 16, 10), Tier.NORMAL) }
    private fun qualify(s: GameState, m: Mon, rounds: Int = 6) {
        repeat(rounds) { BattleEngine.playRound(data, s.battle!!, m, 0.0, enemyActs = false, rng = Random(1), mainActs = false) }
    }

    @Test fun hardyThresholdCarryAndPracticeScaling() {
        val m = mon(Nature.HARDY); val s = state(m)
        qualify(s, m, 5)
        assertEquals(100, NaturePerks.rewardXp(s, m, 100))
        qualify(s, m, 1)
        assertEquals(103, NaturePerks.rewardXp(s, m, 100))
        assertEquals(103, (1..100).sumOf { NaturePerks.rewardXp(s, m, 1) })
        s.practiceRewards = true
        assertEquals(103, (1..2).sumOf { NaturePerks.rewardXp(s, m, 100) })
        m.level = 100
        val carry = m.natureXpCarry
        assertEquals(50, NaturePerks.rewardXp(s, m, 100))
        assertEquals(carry, m.natureXpCarry)
    }

    @Test fun switchingDoesNotBorrowCompletedRoundsOrCarries() {
        val a = mon(Nature.HARDY); val b = mon(Nature.HARDY, "other"); val s = state(a)
        s.mons += b
        qualify(s, a)
        assertEquals(100, NaturePerks.rewardXp(s, b, 100))
        qualify(s, b, 1)
        assertEquals(100, NaturePerks.rewardXp(s, a, 100))
        assertEquals(100, NaturePerks.rewardXp(s, b, 100))
        qualify(s, b, 5)
        assertEquals(100, NaturePerks.rewardXp(s, b, 100))
        s.battle = Battle(TestData.mon(data, 16, 10), Tier.NORMAL)
        s.team.clear(); s.team += b.uid
        qualify(s, b)
        assertEquals(103, NaturePerks.rewardXp(s, b, 100))
        assertEquals(0, a.natureXpCarry)
    }

    @Test fun catchAndDefeatApplyAfterShareEggAndBondOnlyToMain() {
        for (catch in listOf(false, true)) {
            val m = mon(Nature.HARDY); val s = state(m)
            val holder = mon(Nature.HARDY, "share"); s.mons += holder
            s.settings.xpShareUid = holder.uid; m.heldItem = "lucky-egg"; m.bondPoints = 50
            qualify(s, m)
            val xp = Progression.xpForDefeat(data.sp(16), 10)
            val half = (xp * Tuning.XP_SHARE_PART).toInt()
            val base = Bond.bonusXp((half * Tuning.LUCKY_EGG).toInt(), m.bondPoints)
            val events = mutableListOf<GameEvent>()
            if (catch) Progression.rewardCatch(data, s, m, s.battle!!.wild, 1, Random(1), events)
            else Progression.rewardDefeat(data, s, m, s.battle!!.wild, 1, Random(1), events)
            assertEquals(base + base * 3 / 100, events.filterIsInstance<GameEvent.XpGained>().single { it.uid == m.uid }.xp)
            assertEquals(half, events.filterIsInstance<GameEvent.XpGained>().single { it.uid == holder.uid }.xp)
            assertEquals(0, holder.natureXpCarry)
        }
    }

    @Test fun docileAddsFourPercentToStudyOnlyAlongsideBellAndStopsAtCap() {
        for (bell in listOf(false, true)) {
            val m = mon(Nature.DOCILE); val events = mutableListOf<GameEvent>()
            repeat(100) { Bond.add(m, 1, events, bell, studyAnswer = true) }
            assertEquals((if (bell) 150 else 100) + 4, m.bondPoints)
            Bond.add(m, 3, events, bell) // win points have no nature bonus
            assertEquals(0, m.natureBondCarry)
            m.bondPoints = 799; m.natureBondCarry = 96
            Bond.add(m, 1, events, bell, studyAnswer = true)
            assertEquals(800, m.bondPoints); assertEquals(0, m.natureBondCarry)
        }
    }

    @Test fun otherNaturesStayUnchangedAndPerksAppearWithNature() {
        for (n in Nature.entries) {
            val m = mon(n); val s = state(m); qualify(s, m)
            if (n != Nature.HARDY) assertEquals(100, NaturePerks.rewardXp(s, m, 100))
            if (n != Nature.DOCILE) {
                repeat(100) { Bond.add(m, 1, mutableListOf(), studyAnswer = true) }
                assertEquals(100, m.bondPoints)
            }
            if (n in listOf(Nature.HARDY, Nature.DOCILE, Nature.SERIOUS, Nature.BASHFUL, Nature.QUIRKY)) assertTrue(DetailMath.natureEffect(n).contains(NaturePerks.description(n)!!))
            else assertNull(NaturePerks.description(n))
        }
    }

    @Test fun fractionalCarriesAndRoundOwnershipSurviveSave() {
        val m = mon(Nature.HARDY); val s = state(m); qualify(s, m)
        NaturePerks.rewardXp(s, m, 1); m.natureBondCarry = 96
        val back = SaveCodec.decode(SaveCodec.encode(s))
        assertEquals(6, back.main!!.natureXpCarry); assertEquals(96, back.main!!.natureBondCarry)
        assertEquals(m.uid, back.battle!!.natureMainUid); assertEquals(6, back.battle!!.natureMainRounds)
        assertEquals(103, NaturePerks.rewardXp(back, back.main!!, 100))
    }
    @Test fun countedPipelineGivesDocileBonusForEveryRatingButNotPracticeOrRedo() {
        val m = mon(Nature.DOCILE); val s = state(m)
        val now = 1_790_000_000_000L
        val game = Game(data, s, Random(7)) { now }.also { it.roundAnswers = 1000 }
        game.tick()
        repeat(25) { game.onAnswer(Answer(it.toLong() + 1, it % 4 + 1, now)) }
        assertEquals(26, m.bondPoints)
        val carry = m.natureBondCarry
        repeat(25) { game.onPracticeAnswer(Answer(it.toLong() + 100, 3, now)) }
        assertEquals(51, m.bondPoints); assertEquals(carry, m.natureBondCarry)
        s.redo[999L] = 1
        assertTrue(game.onAnswer(Answer(999, 4, now)).any { it is GameEvent.Ignored })
        assertEquals(51, m.bondPoints); assertEquals(carry, m.natureBondCarry)
    }

}
