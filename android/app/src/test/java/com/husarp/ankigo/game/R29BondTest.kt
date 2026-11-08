package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import kotlin.random.Random

class R29BondTest {
    private val data = TestData.world()
    private val noon = Calendar.getInstance().apply { set(2026, 9, 13, 12, 0, 0) }.timeInMillis
    private val night = Calendar.getInstance().apply { set(2026, 9, 13, 0, 0, 0) }.timeInMillis
    private fun events() = mutableListOf<GameEvent>()
    private fun state() = TestData.state(data, 25, 20, listOf("dragonrage"))
    private fun wild() = TestData.mon(data, 16, 20, listOf("splash"), "wild")
    private class Roll(private val value: Int) : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextInt(until: Int): Int = value.coerceAtMost(until - 1)
    }

    @Test fun everyLevelStartsAtItsExactThreshold() {
        assertEquals(listOf(0, 50, 125, 250, 500, 800), Bond.thresholds)
        assertEquals(0, Bond.level(-1))
        Bond.thresholds.forEachIndexed { level, points ->
            assertEquals(level, Bond.level(points))
            if (level > 0) assertEquals(level - 1, Bond.level(points - 1))
        }
        assertEquals(5, Bond.level(Int.MAX_VALUE))
    }

    @Test fun countedCardsGiveOneToTheirFighterIncludingNeutralAndPractice() {
        val s = state(); val m = s.main!!
        val bench = TestData.mon(data, 19, 20, uid = "bench"); s.mons += bench; s.team += bench.uid
        s.settings.xpShareUid = bench.uid
        s.battle = Battle(wild(), Tier.NORMAL)
        val game = Game(data, s, Random(4)) { noon }.apply { roundAnswers = 10000 }
        game.onAnswer(Answer(1, 3, noon))
        game.onAnswer(Answer(2, 0, noon, neutral = true))
        game.onPracticeAnswer(Answer(3, 4, noon))
        assertEquals(3, m.bondPoints)
        assertEquals(0, bench.bondPoints)
        game.onUndo(1, 1)
        assertTrue(game.onAnswer(Answer(1, 3, noon)).any { it is GameEvent.Ignored })
        assertEquals(3, m.bondPoints)
    }

    @Test fun queuedSwitchGivesThePointToThePokemonThatFoughtTheCard() {
        val s = state(); val old = s.main!!
        val next = TestData.mon(data, 19, 20, uid = "next"); s.mons += next; s.team += next.uid
        s.battle = Battle(wild(), Tier.NORMAL).apply { answers = 1; buttons += 3 }
        val game = Game(data, s, Random(1)) { noon }.apply { roundAnswers = 10000 }
        game.requestMain(next.uid)
        game.onAnswer(Answer(1, 3, noon))
        assertEquals(next.uid, s.main!!.uid)
        assertEquals(1, old.bondPoints); assertEquals(0, next.bondPoints)
    }

    @Test fun defeatedAndCaughtRewardsGiveExactlyThreePointsWithoutTrainingTheBench() {
        for (caught in listOf(false, true)) {
            val s = state(); val m = s.main!!
            val holder = TestData.mon(data, 19, 20, uid = "share"); s.mons += holder; s.settings.xpShareUid = holder.uid
            if (caught) Progression.rewardCatch(data, s, m, wild(), noon, Random(1), events())
            else Progression.rewardDefeat(data, s, m, wild(), noon, Random(1), events())
            assertEquals(3, m.bondPoints); assertEquals(0, holder.bondPoints)
        }
    }

    @Test fun autoCatchDefeatAndBallCatchAllAwardOneWin() {
        for (kind in listOf("defeat", "auto", "ball")) {
            val s = state(); val m = s.main!!
            val w = wild().apply { hp = 1 }
            s.battle = Battle(w, Tier.NORMAL).apply { this.override = if (kind == "defeat") Outcome.DEFEAT else Outcome.CATCH }
            val game = Game(data, s, Roll(0)) { noon }.apply { roundAnswers = 1 }
            if (kind == "ball") { s.bag["master-ball"] = 1; game.throwBall("master-ball") }
            else game.onAnswer(Answer(1, 4, noon))
            assertEquals(kind, if (kind == "ball") 3 else 4, m.bondPoints)
            assertEquals(kind, 1, s.defeats)
            if (kind != "defeat") assertEquals(0, s.mons.last().bondPoints)
        }
    }

    @Test fun failedBallAndEscapeGiveNoPoints() {
        val s = state(); val m = s.main!!
        s.battle = Battle(wild(), Tier.NORMAL); s.bag["poke-ball"] = 1
        Game(data, s, Roll(99)) { noon }.throwBall("poke-ball")
        assertEquals(0, m.bondPoints)
        assertEquals(0, s.defeats)
        s.battle = Battle(wild(), Tier.NORMAL); s.bag["poke-doll"] = 1
        Game(data, s, Roll(0)) { noon }.escape("poke-doll")
        assertEquals(0, m.bondPoints)
    }

    @Test fun sootheBellRetainsHalfPointsAcrossSaveAndSwitchingTheItem() {
        val s = state(); val m = s.main!!
        Bond.add(m, 1, events(), sootheBell = true)
        assertEquals(1, m.bondPoints); assertEquals(1, m.bondHalf)
        val restored = SaveCodec.decode(SaveCodec.encode(s)).main!!
        Bond.add(restored, 3, events(), sootheBell = true)
        assertEquals(6, restored.bondPoints); assertEquals(0, restored.bondHalf)
        Bond.add(restored, 1, events(), sootheBell = true)
        Bond.add(restored, 1, events())
        assertEquals(8, restored.bondPoints); assertEquals(1, restored.bondHalf)
    }

    @Test fun aLevelUpIsOneShortMomentAndMaxHasNoFurtherEvents() {
        val m = state().main!!; val out = events(); m.bondPoints = 49
        Bond.add(m, 1, out)
        assertEquals(50, m.bondPoints)
        assertTrue((out.single() as GameEvent.Info).text.contains("Level 1"))
        out.clear(); Bond.add(m, 1, out); assertTrue(out.isEmpty())
        m.bondPoints = 799; Bond.add(m, 3, out)
        assertEquals(800, m.bondPoints); assertEquals(1, out.size)
        out.clear(); Bond.add(m, 1, out, true); assertTrue(out.isEmpty()); assertEquals(800, m.bondPoints)
    }

    @Test fun bondNeverDecaysForDaysOrOtherPokemonsAnswers() {
        val s = state(); val m = s.main!!; m.bondPoints = 370
        val bench = TestData.mon(data, 19, 20, uid = "bench").apply { bondPoints = 249 }
        s.mons += bench; s.team += bench.uid; s.battle = Battle(wild(), Tier.NORMAL)
        var now = noon
        val game = Game(data, s, Random(4)) { now }.apply { roundAnswers = 10000 }
        game.tick(); now += 365L * 86400000; game.tick()
        assertEquals(370, m.bondPoints); assertEquals(249, bench.bondPoints)
        repeat(101) { game.onAnswer(Answer(it.toLong(), 3, now)) }
        assertEquals(471, m.bondPoints); assertEquals(249, bench.bondPoints)
    }

    @Test fun normalLevelAndMoveEvolutionNeedHeartTwoAndUsualRequirement() {
        val s = TestData.state(data, 1, 16); val m = s.main!!
        m.bondPoints = 124; assertNull(Progression.levelEvolution(data, s, m, noon))
        assertTrue(Progression.readyEvolutions(data, s, m, noon).isEmpty())
        m.bondPoints = 125; assertEquals(2, Progression.levelEvolution(data, s, m, noon)?.to)
        m.level = 15; assertTrue(Progression.readyEvolutions(data, s, m, noon).isEmpty())
        val e = Evolution(26, EvoKind.LEVEL_MOVE, move = "mimic", level = 20)
        val d = TestData.data(listOf(data.sp(25).copy(evolutions = listOf(e)), data.sp(26)))
        val s2 = TestData.state(d, 25, 20); val m2 = s2.main!!; m2.bondPoints = 125
        assertNull(Progression.levelEvolution(d, s2, m2, noon))
        m2.moves += "mimic"; assertEquals(26, Progression.levelEvolution(d, s2, m2, noon)?.to)
        m2.bondPoints = 124; assertNull(Progression.levelEvolution(d, s2, m2, noon))
    }

    @Test fun stoneTradeAndOtherEvolutionCannotBypassBondOrConsumeAnItem() {
        for (kind in listOf(EvoKind.ITEM, EvoKind.TRADE, EvoKind.OTHER)) {
            val e = Evolution(26, kind, item = if (kind == EvoKind.ITEM) "thunder-stone" else null)
            val d = TestData.data(listOf(data.sp(25).copy(evolutions = listOf(e)), data.sp(26)))
            val s = TestData.state(d, 25, 40); val m = s.main!!
            val item = Progression.evolutionItem(e)!!; s.bag[item] = 1; m.bondPoints = 124
            assertNull(Progression.itemEvolution(d, m, item, noon))
            assertNull(Progression.itemEvolutionAnyTime(d, m, item))
            assertFalse(Game(d, s).chooseEvolution(m.uid, 26).ok)
            val blocked = ItemUse.use(d, s, item, m.uid, Random(1), noon)
            assertFalse(blocked.ok); assertTrue(blocked.message.contains("Level 2")); assertEquals(1, s.count(item))
            m.bondPoints = 125
            assertEquals(26, Progression.itemEvolution(d, m, item, noon)?.to)
            m.heldItem = "everstone"; assertEquals(26, Progression.itemEvolution(d, m, item, noon)?.to)
        }
    }

    @Test fun friendship160BecomesHeartThreeAndHigherBecomesHeartFour() {
        for ((old, points) in listOf(160 to 250, 220 to 500, 255 to 500)) {
            val e = Evolution(26, EvoKind.FRIENDSHIP, minFriendship = old, time = "day", moveType = Type.FAIRY, level = 20, gender = 'F')
            val d = TestData.data(listOf(data.sp(25).copy(evolutions = listOf(e)), data.sp(26)))
            val s = TestData.state(d, 25, 20); val m = s.main!!; m.gender = 'F'; m.moves += "moonblast"
            m.friendship = 255; m.bondPoints = points - 1
            assertNull(Progression.friendshipEvolution(d, s, m, noon))
            m.bondPoints = points
            assertEquals(26, Progression.friendshipEvolution(d, s, m, noon)?.to)
            assertEquals(listOf(e), Progression.readyEvolutions(d, s, m, noon))
            assertNull(Progression.friendshipEvolution(d, s, m, night))
            m.moves.remove("moonblast"); assertNull(Progression.friendshipEvolution(d, s, m, noon))
            m.moves += "moonblast"; m.level = 19; assertNull(Progression.friendshipEvolution(d, s, m, noon))
            m.level = 20; m.gender = 'M'; assertNull(Progression.friendshipEvolution(d, s, m, noon))
            m.gender = 'F'; m.heldItem = "everstone"; assertEquals(listOf(e), Progression.readyEvolutions(d, s, m, noon))
        }
    }

    @Test fun eachLevelAddsOneFlatCoinAndFivePercentXpForEveryWinType() {
        for ((level, points) in Bond.thresholds.withIndex()) for (caught in listOf(false, true)) {
            val s = state(); val m = s.main!!; m.bondPoints = points
            val out = events()
            val base = Progression.xpForDefeat(data.sp(16), 20)
            if (caught) Progression.rewardCatch(data, s, m, wild(), noon, Random(1), out)
            else Progression.rewardDefeat(data, s, m, wild(), noon, Random(1), out)
            assertEquals(base * (100 + level * 5) / 100, out.filterIsInstance<GameEvent.XpGained>().single().xp)
            s.cash = 0; s.cashToday = 0
            Economy.onWin(s, out, level)
            assertEquals(Tuning.CASH_PER_WIN + level, s.cash)
        }
    }

    @Test fun flatCoinsStayFlatAfterCurveAndItemsAndPracticePaysNone() {
        val s = state(); val m = s.main!!; m.bondPoints = 800; m.heldItem = "amulet-coin"
        s.cashToday = Tuning.CASH_TIER
        Economy.onWin(s, events())
        assertEquals((Tuning.CASH_PER_WIN / 2) * 2 + 5, s.cash)
        val before = s.cash; s.practiceRewards = true
        Economy.onWin(s, events()); assertEquals(before, s.cash)
    }

    @Test fun shareAndLuckyEggUseEachRecipientsOwnXpBonus() {
        val s = state(); val m = s.main!!; m.bondPoints = 250; m.heldItem = "lucky-egg"
        val h = TestData.mon(data, 19, 20, uid = "holder").apply { bondPoints = 500; heldItem = "lucky-egg" }
        s.mons += h; s.settings.xpShareUid = h.uid
        val out = events(); Progression.rewardDefeat(data, s, m, wild(), noon, Random(1), out)
        val halfEgg = ((Progression.xpForDefeat(data.sp(16), 20) * Tuning.XP_SHARE_PART).toInt() * Tuning.LUCKY_EGG).toInt()
        val xp = out.filterIsInstance<GameEvent.XpGained>().associate { it.uid to it.xp }
        assertEquals(halfEgg * 115 / 100, xp[m.uid]); assertEquals(halfEgg * 120 / 100, xp[h.uid])
        assertEquals(500, h.bondPoints)
    }

    @Test fun winningAcrossAThresholdUsesOneConsistentLevelForTheWinsXpAndCoins() {
        val s = state(); val m = s.main!!; m.bondPoints = 47
        s.battle = Battle(wild().apply { hp = 1 }, Tier.NORMAL).apply { this.override = Outcome.DEFEAT }
        val out = Game(data, s, Roll(0)) { noon }.apply { roundAnswers = 1 }.onAnswer(Answer(1, 4, noon))
        assertEquals(51, m.bondPoints)
        assertEquals(Progression.xpForDefeat(data.sp(16), 20), out.filterIsInstance<GameEvent.XpGained>().single { it.uid == m.uid }.xp)
        assertEquals(Tuning.CASH_PER_WIN, out.filterIsInstance<GameEvent.Cash>().filter { it.kind == CashKind.DEFEAT }.sumOf { it.amount })
        assertEquals(m.uid, out.filterIsInstance<GameEvent.Info>().single { it.text.startsWith("Bond grew!") }.uid)
    }

    @Test fun aSuppressedSootheBellUsesTheBaseCardAndWinAmounts() {
        val s = state(); val m = s.main!!; m.heldItem = "soothe-bell"
        s.battle = Battle(wild().apply { hp = 1 }, Tier.NORMAL).apply {
            this.override = Outcome.DEFEAT
            player.volatiles["embargo"] = 5
        }
        Game(data, s, Roll(0)) { noon }.apply { roundAnswers = 1 }.onAnswer(Answer(1, 4, noon))
        assertEquals(4, m.bondPoints); assertEquals(0, m.bondHalf)
    }

    @Test fun lethalHitsSurviveExactlyFiveOrTenOfOneHundredRollsAndSaySo() {
        for ((points, expected) in listOf(0 to 0, 250 to 0, 499 to 0, 500 to 5, 799 to 5, 800 to 10)) {
            var survived = 0
            repeat(100) { roll ->
                val m = state().main!!.apply { hp = 12; bondPoints = points }
                val w = wild().apply { moves.clear(); moves += "dragonrage" }
                val out = BattleEngine.switchHit(data, Battle(w, Tier.NORMAL), m, Roll(roll), power = 1.0)
                if (m.hp > 0) {
                    survived++; assertEquals(1, m.hp)
                    assertTrue(out.filterIsInstance<GameEvent.Effect>().any { it.text.contains("toughed it out") && it.hp == 1 })
                    assertFalse(out.any { it is GameEvent.Fainted && it.player })
                }
            }
            assertEquals("points=$points", expected, survived)
        }
    }

    @Test fun aWildPokemonCannotToughItOutAndNonlethalHitsDoNotAnnounceBond() {
        val m = state().main!!.apply { bondPoints = 800 }
        val w = wild().apply { hp = 1; bondPoints = 800 }
        val out = BattleEngine.playRound(data, Battle(w, Tier.NORMAL), m, 1.0, false, Roll(0))
        assertEquals(0, w.hp)
        assertFalse(out.filterIsInstance<GameEvent.Effect>().any { it.text.contains("toughed it out") })
    }
}
