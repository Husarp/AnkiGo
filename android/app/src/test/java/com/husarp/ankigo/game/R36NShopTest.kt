package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

class R36NShopTest {
    private val base = TestData.world()
    private val data = GameData(base.species, base.moves, base.growth,
        base.itemPrices + Items.ALL.keys.associateWith { 1000 })
    private fun at(day: String, hour: Int) = LocalDate.parse(day).atTime(hour, 0)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun state(day: String = "2026-11-07") = TestData.state(data).apply {
        this.day = day; cash = 1_000_000
        Shop.ensureToday(data, this)
    }

    @Test fun everyDrawExcludesBothDealsAndAllOtherSections() {
        repeat(120) { offset ->
            val day = LocalDate.parse("2026-11-01").plusDays(offset.toLong()).toString()
            val s = state(day)
            val dayDeal = Shop.dealFor(data, day, at(day, 12))!!
            val nightDeal = Shop.dealFor(data, day, at(day, 22))!!
            repeat(5) { draw ->
                val items = Shop.basics() + s.shopItems + listOfNotNull(dayDeal.item, Shop.weekendFor(data, day)?.item)
                assertEquals(items.size, items.distinct().size)
                assertFalse(nightDeal.item in s.shopItems)
                assertEquals(Shop.slots(s.trainerLevel) + Shop.dailyBalls(day).size, s.shopItems.size)
                assertEquals(s.shopTms.size, s.shopTms.distinct().size)
                assertTrue(Shop.reroll(data, s, Random(draw), at(day, 12)).ok)
            }
        }
    }

    @Test fun repairOldStockRedrawsConflictingSlotsWithoutResettingPurchases() {
        val s = state()
        val deal = Shop.dealFor(data, s.day, at(s.day, 12))!!.item
        val reserved = listOf(deal, Shop.dealFor(data, s.day, at(s.day, 22))!!.item)
        val heal = Items.ALL.values.first { it.kind == ItemKind.HEAL && it.id !in reserved && it.id !in Shop.basics() }.id
        s.shopItems.clear(); s.shopItems += listOf(deal, deal, "potion", heal)
        assertTrue(Shop.buy(data, s, heal).ok)
        Shop.ensureToday(data, s)
        assertFalse(deal in s.shopItems); assertFalse("potion" in s.shopItems)
        assertEquals(s.shopItems.size, s.shopItems.distinct().size)
        assertEquals(4, Shop.remaining(s, heal))
    }

    @Test fun limitsAreAtomicPersistAndResetWithOffer() {
        for ((item, limit) in listOf("fire-stone" to 1, "helix-fossil" to 1, "oran-berry" to 1,
            "super-potion" to 5, "great-ball" to 3, "ultra-ball" to 3)) {
            var s = state()
            s.shopItems += item
            assertEquals(limit, Shop.remaining(s, item))
            val cash = s.cash
            if (limit < 5) {
                assertFalse(Shop.buy(data, s, item, quantity = 5).ok)
                assertEquals(cash, s.cash)
            }
            // Start with a fresh offer after the attempted bulk purchase above.
            s = state(); s.shopItems += item
            repeat(limit) { n ->
                assertTrue(Shop.buy(data, s, item).ok)
                s = SaveCodec.decode(SaveCodec.encode(s))
                assertEquals(limit - n - 1, Shop.remaining(s, item))
            }
            val after = s.cash
            assertFalse(Shop.buy(data, s, item).ok)
            assertEquals(after, s.cash)
            Economy.take(s, item)
            assertFalse("using an item does not refill the offer", Shop.buy(data, s, item).ok)
            assertTrue(after < cash)
            assertTrue(Shop.reroll(data, s, Random(6)).ok)
            s.shopItems += item
            assertEquals(limit, Shop.remaining(s, item))
            s.day = "2026-11-08"; Shop.ensureToday(data, s); s.shopItems += item
            assertEquals(limit, Shop.remaining(s, item))
        }
    }

    @Test fun bulkCannotExceedRemainderAndBasicsStayUnlimited() {
        val s = state(); s.shopItems += "super-potion"
        assertTrue(Shop.buy(data, s, "super-potion", quantity = 3).ok)
        val cash = s.cash
        assertFalse(Shop.buy(data, s, "super-potion", quantity = 3).ok)
        assertEquals(cash, s.cash); assertEquals(3, s.count("super-potion"))
        repeat(2) { assertTrue(Shop.buy(data, s, "super-potion").ok) }
        for (item in Shop.basics()) repeat(4) {
            assertNull(Shop.remaining(s, item)); assertTrue(Shop.buy(data, s, item, quantity = 5).ok)
        }
        s.shopTms += "thunderbolt"
        assertTrue(Shop.buyTm(data, s, "thunderbolt").ok)
        assertFalse(Shop.buyTm(data, SaveCodec.decode(SaveCodec.encode(s)), "thunderbolt").ok)
    }

    @Test fun failedRefreshKeepsSoldOutStockAndPurchases() {
        val s = state("2026-11-09")
        val item = Shop.dailyBalls(s.day).first()
        assertTrue(Shop.buy(data, s, item, quantity = 3).ok)
        s.cash = 0
        val before = SaveCodec.encode(s)
        assertFalse(Shop.reroll(data, s, Random(2)).ok)
        assertEquals(before, SaveCodec.encode(s))
        assertEquals(0, Shop.remaining(s, item))
    }

    @Test fun refreshPreservesEveryFixedOfferAtBothClockBoundariesAndAfterReload() {
        for (hour in listOf(5, 6, 12, 20, 21, 22)) {
            var s = state()
            val now = at(s.day, hour)
            val dayDeal = Shop.dealFor(data, s.day, at(s.day, 12))
            val nightDeal = Shop.dealFor(data, s.day, at(s.day, 22))
            val weekend = Shop.weekendFor(data, s.day)!!
            assertTrue(Shop.buy(data, s, weekend.item!!, now).ok)
            val balls = Shop.dailyBalls(s.day)
            repeat(8) {
                assertTrue(Shop.reroll(data, s, Random(it), now).ok)
                s = SaveCodec.decode(SaveCodec.encode(s)); Shop.ensureToday(data, s)
                assertEquals(dayDeal, Shop.dealFor(data, s.day, at(s.day, 12)))
                assertEquals(nightDeal, Shop.dealFor(data, s.day, at(s.day, 22)))
                assertEquals(weekend, Shop.weekendFor(data, s.day))
                assertEquals(balls, s.shopItems.filter { Items.def(it)?.kind == ItemKind.BALL })
                assertTrue(Shop.soldOut(s, weekend.item!!))
            }
        }
    }

    @Test fun freezeDropThresholdsScaleAndKeepDailyPracticeAndInventoryCaps() {
        class Roll(val value: Int) : Random() {
            override fun nextBits(bitCount: Int) = 0
            override fun nextInt(until: Int): Int { assertEquals(1000, until); return value }
        }
        for (held in 0..2) {
            val threshold = Tuning.STREAK_FREEZE_WIN_PER_THOUSAND[held]
            for (roll in listOf(threshold - 1, threshold)) {
                val s = state(); s.bag[StreakFreeze.ITEM] = held
                val events = mutableListOf<GameEvent>()
                StreakFreeze.battleDrop(s, Roll(roll), events)
                assertEquals(held + if (roll < threshold) 1 else 0, s.count(StreakFreeze.ITEM))
                if (roll < threshold) {
                    val restored = SaveCodec.decode(SaveCodec.encode(s))
                    StreakFreeze.battleDrop(restored, Roll(0), events)
                    assertEquals(s.count(StreakFreeze.ITEM), restored.count(StreakFreeze.ITEM))
                }
            }
        }
        for ((held, practice) in listOf(3 to false, 0 to true)) {
            val s = state().apply { bag[StreakFreeze.ITEM] = held; practiceRewards = practice }
            StreakFreeze.battleDrop(s, Roll(0), mutableListOf())
            assertEquals(held, s.count(StreakFreeze.ITEM))
        }
    }

    @Test fun giftRatesMeetWaitingTimeTargetsEvenWithoutBattleWins() {
        val counts = IntArray(3)
        repeat(3000) { offset ->
            val day = LocalDate.parse("2026-01-01").plusDays(offset.toLong()).toString()
            for (held in 0..2) {
                val s = state(day).apply { bag[StreakFreeze.ITEM] = held }
                val expected = Random(("freeze-gift:$day").hashCode()).nextInt(100) < Tuning.STREAK_FREEZE_GIFT_PERCENT[held]
                val gift = DailyGift.give(data, s, mutableListOf())!!
                assertEquals(expected, gift.item == StreakFreeze.ITEM)
                if (expected) counts[held]++
            }
        }
        assertTrue(counts[0] > counts[1] && counts[1] > counts[2])
        assertTrue(3000.0 / counts[0] <= 7)
        assertTrue(3000.0 / counts[2] <= 14)
        assertEquals(listOf(5.0, 100.0 / 12, 12.5), Tuning.STREAK_FREEZE_GIFT_PERCENT.map { 100.0 / it })
    }
}
