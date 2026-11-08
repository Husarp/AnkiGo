package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

class R30OStreakShopTest {
    private val data = TestData.world()
    private fun at(day: String, hour: Int = 12): Long = LocalDate.parse(day).atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun streak(held: Int) = TestData.state(data, 25, 20).apply {
        day = "2026-11-01"; lastGoalDay = day; streakDays = 17; bestStreakDays = 17
        bag[StreakFreeze.ITEM] = held
    }

    @Test fun everyGapAndInventoryCombinationProtectsOnlyFullyCoveredGaps() {
        for (missed in 1..4) for (held in 0..3) {
            val s = streak(held)
            val target = LocalDate.parse(s.day).plusDays(missed + 1L).toString()
            val g = Game(data, s, Random(17)) { at(target) }
            val counters = s.counters.toMap()
            val events = g.tick()
            val covered = held >= missed
            assertEquals("gap=$missed held=$held", if (covered) 17 else 0, StudyStats.streak(s, target).current)
            assertEquals(if (covered) held - missed else held, s.count(StreakFreeze.ITEM))
            assertEquals("2026-11-01", s.lastGoalDay)
            assertEquals(17, s.bestStreakDays)
            assertEquals(0L, s.totalReviews)
            assertEquals(counters, s.counters)
            assertTrue(s.history.isEmpty())
            assertEquals(if (covered) 1 else 0, events.filterIsInstance<GameEvent.StreakSaved>().size)
            assertFalse(events.any { it is GameEvent.DailyGift || it is GameEvent.AchievementReached })
            assertTrue(g.tick().isEmpty())
            val restored = SaveCodec.decode(SaveCodec.encode(s))
            assertTrue(Game(data, restored) { at(target) }.tick().isEmpty())
            assertEquals(s.count(StreakFreeze.ITEM), restored.count(StreakFreeze.ITEM))
        }
    }

    @Test fun cutoffProtectsYesterdayOnlyAndTodayStillMustBeEarned() {
        val s = streak(3).apply { settings.dayStartHour = 4; settings.dailyAverage = 2 }
        var now = at("2026-11-03", 3)
        val g = Game(data, s, Random(4)) { now }
        assertFalse(g.tick().any { it is GameEvent.StreakSaved })
        assertEquals(3, s.count(StreakFreeze.ITEM))
        now = at("2026-11-03", 4)
        val saved = g.tick().filterIsInstance<GameEvent.StreakSaved>().single()
        assertEquals(1, saved.used); assertEquals("2026-11-02", saved.from)
        assertEquals(17, s.streakDays)
        g.onAnswer(Answer(1, 3, now))
        assertEquals(17, s.streakDays)
        g.onAnswer(Answer(2, 3, now))
        assertEquals(18, s.streakDays); assertEquals("2026-11-03", s.lastGoalDay)
        assertEquals("2026-11-03", s.streakThroughDay)
    }

    @Test fun uncoveredGapCannotBeRevivedByBuyingAFreezeLater() {
        val s = streak(0)
        var now = at("2026-11-04")
        val g = Game(data, s) { now }; g.tick()
        Economy.give(s, StreakFreeze.ITEM, 3)
        now = at("2026-11-05"); g.tick()
        assertEquals(0, s.streakDays); assertEquals(3, s.count(StreakFreeze.ITEM))
    }

    @Test fun protectedStreakStaysVisibleInWidgetsAndReminderFacts() {
        val s = streak(2)
        val now = at("2026-11-04")
        val saved = Game(data, s) { now }.tick().filterIsInstance<GameEvent.StreakSaved>().single()
        assertEquals(17, com.husarp.ankigo.ui.WidgetLogic.summarize(s, now).streak)
        val facts = com.husarp.ankigo.remind.ReminderFacts.from(data, s)
        assertEquals("2026-11-03", facts.streakThroughDay)
        assertEquals("2026-11-01", facts.lastGoalDay)
        assertEquals("Mon and Tue protected", StreakFreeze.protectedDays(saved))
    }

    @Test fun oldSavesAndNewOfferStateRoundTrip() {
        val s = streak(2)
        val old = JSONObject(SaveCodec.encode(s)).apply {
            remove("streakThroughDay"); remove("battleFreezeDay"); remove("shopPurchases")
            getJSONObject("bag").remove(StreakFreeze.ITEM)
        }
        val migrated = SaveCodec.decode(old.toString())
        assertEquals(0, migrated.count(StreakFreeze.ITEM)); assertNull(migrated.streakThroughDay)
        assertNull(migrated.battleFreezeDay); assertTrue(migrated.shopPurchases.isEmpty())
        s.streakThroughDay = "2026-11-02"; s.battleFreezeDay = s.day; s.shopPurchases += "2026-11-01:everstone"
        val back = SaveCodec.decode(SaveCodec.encode(s))
        assertEquals(s.streakThroughDay, back.streakThroughDay); assertEquals(s.battleFreezeDay, back.battleFreezeDay)
        assertEquals(s.shopPurchases, back.shopPurchases)
    }

    @Test fun freezePricesCapAndRepeatPurchasesSurviveRestart() {
        var s = streak(0).apply { cash = 50_000 }
        for (held in 0..2) {
            assertEquals(2000 + held * 1000, Shop.priceOf(data, s, StreakFreeze.ITEM))
            val before = s.cash
            assertFalse(Shop.buy(data, s, StreakFreeze.ITEM, quantity = 3).ok)
            assertEquals(before, s.cash)
            assertTrue(Shop.buy(data, s, StreakFreeze.ITEM).ok)
            assertEquals(before - 2000 - held * 1000, s.cash)
            val back = SaveCodec.decode(SaveCodec.encode(s))
            assertFalse(Shop.soldOut(back, StreakFreeze.ITEM))
            s = back
        }
        s.day = "2026-11-14"
        s.shopItems += StreakFreeze.ITEM
        val cashAtCap = s.cash
        assertFalse(Shop.freezeOnSale(s)); assertFalse(Shop.buy(data, s, StreakFreeze.ITEM).ok)
        assertEquals(cashAtCap, s.cash)
        Economy.give(s, StreakFreeze.ITEM, 10); assertEquals(3, s.count(StreakFreeze.ITEM))
        assertFalse(StreakFreeze.ITEM in Items.dropPool()); assertFalse(StreakFreeze.ITEM in Items.shopPool(data))
    }

    @Test fun freezeHasNoManualUseAndCorruptCountsAreClampedOnLoad() {
        val s = streak(3)
        assertFalse(ItemUse.canUse(data, s, StreakFreeze.ITEM, s.main))
        assertFalse(Game(data, s).useItem(StreakFreeze.ITEM, s.main!!.uid).ok)
        assertEquals(3, s.count(StreakFreeze.ITEM))
        val encoded = JSONObject(SaveCodec.encode(s))
        encoded.getJSONObject("bag").put(StreakFreeze.ITEM, 999)
        assertEquals(3, SaveCodec.decode(encoded.toString()).count(StreakFreeze.ITEM))
    }

    @Test fun giftReplacementIsRareDeterministicAndFallsBackAtCap() {
        var freezes = 0
        for (offset in 0..999) {
            val day = LocalDate.parse("2026-01-01").plusDays(offset.toLong()).toString()
            val s = streak(0).apply { this.day = day }
            val gift = DailyGift.give(data, s, mutableListOf())!!
            val same = streak(0).apply { this.day = day }
            assertEquals(gift, DailyGift.give(data, same, mutableListOf()))
            if (gift.item == StreakFreeze.ITEM) freezes++
            val capped = streak(3).apply { this.day = day }
            val expected = DailyGift.draw(data, day, capped.streakDays, capped.tms)
            val fallback = DailyGift.give(data, capped, mutableListOf())!!
            assertEquals(expected, fallback)
            assertNotEquals(StreakFreeze.ITEM, fallback.item)
        }
        assertTrue("2% over 1000 seeded days: $freezes", freezes in 8..35)
    }

    @Test fun rewardedWinDropHasExactRollGateAndDailyCap() {
        class Roll(private val value: Int) : Random() {
            override fun nextBits(bitCount: Int) = 0
            override fun nextInt(until: Int): Int { assertEquals(1000, until); return value }
        }
        for (roll in 0..2) {
            val s = streak(0); val ev = mutableListOf<GameEvent>()
            Economy.onWin(s, ev, 0, Roll(roll))
            assertEquals(if (roll < 2) 1 else 0, s.count(StreakFreeze.ITEM))
            if (roll < 2) {
                Economy.onWin(s, ev, 0, Roll(0)); assertEquals(1, s.count(StreakFreeze.ITEM))
                val back = SaveCodec.decode(SaveCodec.encode(s))
                Economy.onWin(back, ev, 0, Roll(0)); assertEquals(1, back.count(StreakFreeze.ITEM))
                s.day = "2026-11-02"; Economy.onWin(s, ev, 0, Roll(0)); assertEquals(2, s.count(StreakFreeze.ITEM))
            }
        }
        for (practice in listOf(false, true)) {
            val s = streak(if (practice) 0 else 3).apply { practiceRewards = practice }
            val ev = mutableListOf<GameEvent>()
            Economy.onWin(s, ev, 0, Roll(0))
            assertFalse(ev.any { it is GameEvent.ItemFound })
        }
    }

    @Test fun weekendAllowanceIsSharedUntilSundayAndRefillsNextWeekend() {
        val allPrices = Shop.weekendPrices.keys.associateWith { 10_000 }
        val d = GameData(data.species, data.moves, data.growth, data.itemPrices + allPrices)
        val s = streak(0).apply { day = "2026-11-07"; cash = 100_000 }
        val item = Shop.weekendFor(d, s.day)!!.item!!
        assertTrue(Shop.buy(d, s, item).ok)
        assertFalse(Shop.buy(d, s, item).ok)
        Shop.reroll(d, s, Random(8)); assertTrue(Shop.soldOut(s, item))
        s.day = "2026-11-08"; Shop.ensureToday(d, s)
        assertEquals(item, Shop.weekendFor(d, s.day)!!.item)
        assertFalse(Shop.buy(d, SaveCodec.decode(SaveCodec.encode(s)), item).ok)
        s.day = "2026-11-14"; Shop.ensureToday(d, s)
        assertFalse(Shop.soldOut(s, item))
        assertTrue(Shop.buy(d, s, Shop.weekendFor(d, s.day)!!.item!!).ok)
    }

    @Test fun everstoneDealAndStockShareAllowanceAndConsumablesStayUnlimited() {
        val s = streak(0).apply { cash = 100_000 }
        var day = LocalDate.parse("2026-01-01")
        while (Shop.dealFor(data, day.toString())?.item != "everstone") day = day.plusDays(1)
        s.day = day.toString(); s.shopItems += "everstone"
        assertTrue(Shop.buy(data, s, "everstone").ok)
        Shop.reroll(data, s, Random(2)); s.shopItems += "everstone"
        assertFalse(Shop.buy(data, s, "everstone").ok)
        s.day = day.plusDays(1).toString(); Shop.ensureToday(data, s); s.shopItems += "everstone"
        assertTrue(Shop.buy(data, s, "everstone").ok)
        repeat(2) { assertTrue(Shop.buy(data, s, "potion", quantity = 5).ok) }
        assertEquals(10, s.count("potion"))
        assertEquals(Shop.weekendPrices.keys + setOf("everstone"), Items.ALL.keys.filter { Shop.limited(it) }.toSet())
        s.shopTms += "thunderbolt"
        assertTrue(Shop.buyTm(data, s, "thunderbolt").ok)
        assertFalse(Shop.buyTm(data, s, "thunderbolt").ok)
    }
}
