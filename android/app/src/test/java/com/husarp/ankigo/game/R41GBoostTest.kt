package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R41GBoostTest {
    private val data = TestData.world()
    private fun state() = TestData.state(data).apply { achTiers.clear(); badges.clear(); cash = 0 }
    private fun reached(s: GameState, id: String, tiers: Int) { s.achTiers[id] = MutableList(tiers) { 1L } }

    @Test fun cumulativeTiersSecretsAndBadgesHaveExactSeparateRates() {
        val s = state()
        val cumulative = listOf(10, 35, 85, 160, 260)
        for (n in 1..5) {
            reached(s, "answers", n)
            assertEquals(cumulative[n - 1], PermanentBoosts.xpBasisPoints(s))
        }
        reached(s, "types", 2)
        val secret = AchievementTable.ROWS.first { it.group == "Secret" }
        reached(s, secret.id, 1)
        reached(s, "not-a-current-achievement", 5)
        reached(s, AchievementTable.ROWS.first { it.ankimonBadge != null }.id, 1)
        s.badges += listOf(1, 5, 7)
        assertEquals(395, PermanentBoosts.xpBasisPoints(s))
        assertEquals(150, PermanentBoosts.moneyBasisPoints(s))
        assertEquals("XP bonus +3.95%", PermanentBoosts.xpLabel(s))
        assertEquals("Money bonus +1.5%", PermanentBoosts.moneyLabel(s))
        // Saved tiers stay effective even when the underlying metric is now zero.
        assertEquals(395, PermanentBoosts.xpBasisPoints(SaveCodec.decode(SaveCodec.encode(s))))
    }

    @Test fun totalsAndPayoutsHaveNoFiftyPercentCap() {
        val s = state()
        AchievementTable.ROWS.forEach { reached(s, it.id, it.tiers.size) }
        s.badges += 1..120
        val xp = PermanentBoosts.xpBasisPoints(s)
        assertTrue(xp > 5000)
        assertEquals(6000, PermanentBoosts.moneyBasisPoints(s))
        assertEquals(10_000 + xp, PermanentBoosts.reward(10_000, xp, 0).amount)
        assertEquals(16_000, Economy.rewardCash(s, 10_000, CashKind.ANSWER, mutableListOf()))
    }

    @Test fun smallMoneyBonusesAccumulateAndPersistAcrossSavesAndDays() {
        var s = state().apply { badges += 1 }
        var paid = 0
        repeat(40) { n ->
            paid += Economy.rewardCash(s, 5, CashKind.ANSWER, mutableListOf())
            s = SaveCodec.decode(SaveCodec.encode(s))
            if (n == 20) { s.day = "2026-11-17"; s.cashToday = 0 }
        }
        assertEquals(201, paid)
        assertEquals(201, s.cash)
        assertEquals(0, s.badgeCashCarry)
    }

    @Test fun battleXpMainShareTrainerAndSkippedRewardsReceiveOneBonus() {
        var s = state()
        reached(s, "answers", 5) // +2.6%
        val main = s.main!!
        val holder = Progression.newMon(data, 25, 30, Random(2), 1L)
        main.level = 30; main.xp = 0
        s.mons += holder; s.settings.xpShareUid = holder.uid
        val wild = Progression.newMon(data, 19, 20, Random(3), 1L)
        val plain = SaveCodec.decode(SaveCodec.encode(s)).apply { achTiers.clear() }
        val plainEvents = mutableListOf<GameEvent>()
        val events = mutableListOf<GameEvent>()
        Progression.rewardCatch(data, plain, plain.main!!, wild, 1L, Random(4), plainEvents)
        Progression.rewardCatch(data, s, main, wild, 1L, Random(4), events)
        for (e in plainEvents.filterIsInstance<GameEvent.XpGained>()) {
            val expected = PermanentBoosts.reward(e.xp, 260, 0)
            assertEquals(expected.amount, events.filterIsInstance<GameEvent.XpGained>().single { it.uid == e.uid }.xp)
            assertEquals(expected.carry, s.mon(e.uid)!!.achievementXpCarry)
        }
        Trainer.addDefeatXp(s, Tier.NORMAL, events)
        assertEquals(2600, s.achievementTrainerXpCarry)
        s = SaveCodec.decode(SaveCodec.encode(s))
        repeat(49) { Trainer.addDefeatXp(s, Tier.NORMAL, events) }
        val trainerTotal = (1 until s.trainerLevel).sumOf { Trainer.xpForNext(it) } + s.trainerXp
        assertEquals(513, trainerTotal)
        val before = s.main!!.achievementXpCarry
        val skipEvents = mutableListOf<GameEvent>()
        Progression.rewardSkip(data, s, s.main!!, wild, 50, skipEvents)
        assertTrue(skipEvents.any { it is GameEvent.XpGained })
        assertTrue(s.main!!.achievementXpCarry != before)
    }

    @Test fun commonTierBattleXpAccumulatesItsTenthPercentAcrossReloads() {
        val tiny = TestData.data(listOf(TestData.species(1), TestData.species(2, baseExp = 7)))
        var s = TestData.state(tiny, species = 1).apply { achTiers.clear(); main!!.level = 30; main!!.xp = 0; main!!.bondPoints = 0 }
        reached(s, "answers", 1)
        val wild = Progression.newMon(tiny, 2, 1, Random(1), 1L)
        var total = 0
        repeat(1000) { i ->
            val events = mutableListOf<GameEvent>()
            Progression.rewardSkip(tiny, s, s.main!!, wild, 100, events)
            total += events.filterIsInstance<GameEvent.XpGained>().single().xp
            if (i == 499) s = SaveCodec.decode(SaveCodec.encode(s))
        }
        assertEquals(1001, total)
        assertEquals(0, s.main!!.achievementXpCarry)
    }

    @Test fun practiceAndMaxLevelKeepTheirRewardRulesAndBerryGiftXpIsUnboosted() {
        val s = state()
        reached(s, "answers", 5); s.badges += 1
        val events = mutableListOf<GameEvent>()
        s.practiceRewards = true
        Economy.onReview(data, s, Random(1), events)
        Economy.onWin(s, events, 5, Random(1))
        Economy.onSkip(s, 50, events)
        assertEquals(0, s.cash)
        assertEquals(0, s.badgeCashCarry)
        s.main!!.level = 100
        Progression.rewardSkip(data, s, s.main!!, s.main!!, 100, events)
        assertEquals(0, s.main!!.achievementXpCarry)
        s.main!!.level = 30
        Progression.gainXp(data, s, s.main!!, 20, events)
        assertEquals(20, events.filterIsInstance<GameEvent.XpGained>().single().xp)
    }

    @Test fun answerWinBondSkipAndHoneyGatherAllUseBadgeBonusAfterOtherMultipliers() {
        val s = state().apply { badges += 1..20; main!!.heldItem = "amulet-coin" }
        val e = mutableListOf<GameEvent>()
        Economy.onReview(data, s, Random(1), e)
        assertEquals(22, s.cash) // ten, doubled, then +10%
        assertEquals(10, s.cashToday)
        Economy.onWin(s, e, 5, Random(1))
        assertEquals(82, s.cash) // 50 base payout and 5 Bond coins, +10% each
        val skip = state().apply { badges += 1..20 }
        Economy.onSkip(skip, 50, mutableListOf())
        assertEquals(13, skip.cash); assertEquals(2000, skip.badgeCashCarry)
        val honey = state().apply { badges += 1..20; main!!.ability = "honeygather" }
        val zero = object : Random() { override fun nextBits(bitCount: Int) = 0 }
        AbilityAdaptations.onWin(honey, zero, mutableListOf())
        AbilityAdaptations.onWin(honey, zero, mutableListOf())
        assertEquals(11, honey.cash)
        honey.practiceRewards = true
        AbilityAdaptations.onWin(honey, zero, mutableListOf())
        assertEquals(11, honey.cash)
    }

    @Test fun studyFlowReportsAndRecordsTheActualBoostedCoinsOnce() {
        val now = 1_800_000_000_000L
        val s = state().apply { badges += 1..20; itemCountdown = 10_000 }
        val game = Game(data, s, Random(4)) { now }
        game.tick(); game.ensureEncounter(); s.battle!!.awaitingContinue = true
        val events = game.onAnswer(Answer(1, 3, now)) + game.onAnswer(Answer(2, 3, now + 1000))
        assertEquals(22, s.cash)
        assertEquals(22, events.filterIsInstance<GameEvent.Cash>().sumOf { it.amount })
        assertEquals(22L, s.counters[Metric.MONEY_EARNED.name] ?: 0L)
        assertEquals(22, s.history.getValue(s.day).cash)
    }

    @Test fun aNewlyEarnedBadgeBoostsFollowingStudyPayouts() {
        val s = state().apply { itemCountdown = 1 }
        val events = mutableListOf<GameEvent>()
        Economy.onReview(data, s, Random(2), events)
        assertTrue(6 in s.badges)
        assertEquals(10, s.cash)
        s.itemCountdown = Int.MAX_VALUE
        repeat(20) { Economy.onReview(data, s, Random(2), events) }
        assertEquals(211, s.cash)
        assertEquals(210, s.cashToday)
        assertEquals(0, s.badgeCashCarry)
    }

    @Test fun bagSalesDontReceiveBonusesAndPayoutCannotOverflow() {
        val s = state().apply { badges += 1..20; bag["potion"] = 1 }
        val sale = BagSell.price(data, "potion")
        assertTrue(BagSell.sell(data, s, "potion", 1).ok)
        assertEquals(sale, s.cash); assertEquals(0, s.badgeCashCarry)
        s.cash = Int.MAX_VALUE - 2
        val events = mutableListOf<GameEvent>()
        assertEquals(2, Economy.rewardCash(s, 10, CashKind.DEFEAT, events))
        assertEquals(Int.MAX_VALUE, s.cash)
        assertEquals(2, events.filterIsInstance<GameEvent.Cash>().single().amount)
    }

    @Test fun missingAndInvalidSaveRemaindersDefaultSafely() {
        val oldJson = org.json.JSONObject(SaveCodec.encode(state())).apply {
            remove("badgeCashCarry"); remove("achievementTrainerXpCarry")
            getJSONArray("mons").getJSONObject(0).remove("achievementXpCarry")
        }
        val old = SaveCodec.decode(oldJson.toString())
        assertEquals(0, old.badgeCashCarry); assertEquals(0, old.achievementTrainerXpCarry)
        assertEquals(0, old.main!!.achievementXpCarry)
        val s = state().apply { badgeCashCarry = 9999; achievementTrainerXpCarry = 1234; main!!.achievementXpCarry = 5678 }
        val invalid = SaveCodec.decode(SaveCodec.encode(s).replace("\"badgeCashCarry\":9999", "\"badgeCashCarry\":-5"))
        assertEquals(0, invalid.badgeCashCarry)
        val restored = SaveCodec.decode(SaveCodec.encode(s))
        assertEquals(9999, restored.badgeCashCarry)
        assertEquals(1234, restored.achievementTrainerXpCarry)
        assertEquals(5678, restored.main!!.achievementXpCarry)
    }
}
