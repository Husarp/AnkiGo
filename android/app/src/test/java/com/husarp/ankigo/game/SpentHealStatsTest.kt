package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R3-14: the per-day counters behind "Money spent" and "Healing used" (DayLog.spent / DayLog.healItems). */
class SpentHealStatsTest {
    private val tmMon = TestData.species(900, "Tmmon", tmMoves = setOf("thunderbolt", "watergun", "ember", "vinewhip", "earthquake", "shadowball"))
    private val data = TestData.world().let { GameData(it.species + (900 to tmMon), it.moves, it.growth, it.itemPrices) }
    private fun state() = TestData.state(data)

    @Test fun buyingAnItemAddsItsPriceToTodaysSpent() {
        val s = state()
        s.shopDay = s.day; s.shopItems += listOf("poke-ball", "great-ball")
        s.cash = 5000
        assertTrue(Shop.buy(data, s, "poke-ball").ok)
        assertTrue(Shop.buy(data, s, "great-ball").ok)
        assertTrue(Shop.buy(data, s, "poke-ball").ok)
        assertEquals(200 + 600 + 200, s.history.getValue(s.day).spent)
        assertEquals(5000 - 1000, s.cash)
        assertEquals(0, s.history.getValue(s.day).healItems)
    }

    @Test fun buyingATmAddsItsPrice() {
        val s = state()
        s.shopDay = s.day; s.shopTms += listOf("thunderbolt")
        s.cash = 1500
        assertTrue(Shop.buyTm(data, s, "thunderbolt").ok)
        assertEquals(Tuning.TM_PRICE, s.history.getValue(s.day).spent)
    }

    @Test fun aRerollAddsItsPrice() {
        val s = state()
        Shop.ensureToday(data, s)
        s.cash = 500
        assertTrue(Shop.reroll(data, s, TestData.rng(2)).ok)
        assertTrue(Shop.reroll(data, s, TestData.rng(3)).ok)
        assertEquals(2 * Tuning.REROLL_PRICE, s.history.getValue(s.day).spent)
    }

    @Test fun failedPurchasesAddNothing() {
        val s = state()
        Shop.ensureToday(data, s)
        s.shopItems += "poke-ball"; s.shopTms += "thunderbolt"
        s.cash = 50
        assertFalse(Shop.buy(data, s, "poke-ball").ok)                 // not enough money
        assertFalse(Shop.buy(data, s, "ultra-ball").ok)                // not in the shop
        assertFalse(Shop.buyTm(data, s, "thunderbolt").ok)             // not enough money
        assertFalse(Shop.buyTm(data, s, "ember").ok)                   // not in the shop
        assertFalse(Shop.reroll(data, s, TestData.rng(1)).ok)          // not enough money
        s.cash = 5000; s.tms += "thunderbolt"
        assertFalse(Shop.buyTm(data, s, "thunderbolt").ok)             // already owned
        assertNull(s.history[s.day])                                   // not even an empty day was made
        assertEquals(5000, s.cash)
    }

    @Test fun spentGoesIntoTheDayItHappenedOn() {
        val s = state()
        s.shopDay = s.day; s.shopItems += "poke-ball"; s.cash = 1000
        Shop.buy(data, s, "poke-ball")
        s.day = "2026-10-14"; s.shopDay = s.day
        Shop.buy(data, s, "poke-ball")
        assertEquals(200, s.history.getValue("2026-10-13").spent)
        assertEquals(200, s.history.getValue("2026-10-14").spent)
    }

    @Test fun spentKeepsTheDayLogsOtherNumbers() {
        val s = state()
        s.history[s.day] = DayLog(reviews = 12, xp = 30, cash = 40)
        s.shopDay = s.day; s.shopItems += "poke-ball"; s.cash = 1000
        Shop.buy(data, s, "poke-ball")
        val d = s.history.getValue(s.day)
        assertEquals(12, d.reviews); assertEquals(30, d.xp); assertEquals(40, d.cash); assertEquals(200, d.spent)
    }

    @Test fun addSpentIgnoresZeroNegativeAndAGameWithoutADay() {
        val s = GameState()
        StudyStats.addSpent(s, 100)
        assertTrue(s.history.isEmpty())
        s.day = "2026-10-13"
        StudyStats.addSpent(s, 0); StudyStats.addSpent(s, -5)
        assertTrue(s.history.isEmpty())
        StudyStats.addSpent(s, 30)
        assertEquals(30, s.history.getValue("2026-10-13").spent)
    }

    @Test fun newDayLogDropsOldDays() {
        val s = GameState(day = "2026-10-13")
        s.history["2024-01-01"] = DayLog(reviews = 3)
        StudyStats.addSpent(s, 10)
        assertFalse("2024-01-01" in s.history)
        assertEquals(10, s.history.getValue("2026-10-13").spent)
    }

    // ---- healing items ----

    private fun hurt(s: GameState, d: Int): Mon {
        val m = s.main!!
        m.hp = StatCalc.maxHp(data, m) - d
        return m
    }

    @Test fun eachHealUsedCountsOnce() {
        val s = state()
        hurt(s, 50)
        Economy.give(s, "potion", 2)
        assertTrue(ItemUse.use(data, s, "potion", null, TestData.rng(), 0).ok)
        assertEquals(1, s.history.getValue(s.day).healItems)
        assertTrue(ItemUse.use(data, s, "potion", null, TestData.rng(), 0).ok)
        assertEquals(2, s.history.getValue(s.day).healItems)
        assertEquals(0, s.history.getValue(s.day).spent)
    }

    @Test fun refusedHealsCountNothing() {
        val s = state()
        Economy.give(s, "potion")
        assertFalse(ItemUse.use(data, s, "potion", null, TestData.rng(), 0).ok)      // already healthy
        assertFalse(ItemUse.use(data, s, "super-potion", null, TestData.rng(), 0).ok) // none in the bag
        assertFalse(ItemUse.use(data, s, "potion", "nobody", TestData.rng(), 0).ok)   // no such Pokémon
        assertNull(s.history[s.day])
    }

    @Test fun otherItemsAreNotHealing() {
        val s = state()
        Economy.give(s, "everstone")
        assertTrue(ItemUse.use(data, s, "everstone", "main", TestData.rng(), 0).ok)   // a held item
        assertNull(s.history[s.day])
    }
}
