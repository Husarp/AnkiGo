package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R31EShopTest {
    private val data = TestData.world()

    @Test fun oldFreezePurchaseKeysDoNotBlockTopUpsOrResetOtherLimits() {
        var state = TestData.state(data, 25, 20).apply {
            day = "2026-11-07"; shopDay = day; cash = 50_000
            bag[StreakFreeze.ITEM] = 1
            shopPurchases += "$day:${StreakFreeze.ITEM}"
            shopPurchases += "$day:everstone"
            shopPurchases += "$day:amulet-coin"
        }
        state = SaveCodec.decode(SaveCodec.encode(state))
        Shop.ensureToday(data, state)
        assertFalse(Shop.soldOut(state, StreakFreeze.ITEM))
        assertTrue(Shop.soldOut(state, "everstone"))
        assertTrue(Shop.soldOut(state, "amulet-coin"))
        for (price in listOf(3000, 4000)) {
            val before = state.cash
            assertTrue(Shop.buy(data, state, StreakFreeze.ITEM).ok)
            assertEquals(before - price, state.cash)
            state = SaveCodec.decode(SaveCodec.encode(state))
        }
        assertFalse(Shop.buy(data, state, StreakFreeze.ITEM).ok)
        Economy.take(state, StreakFreeze.ITEM)
        assertTrue(Shop.buy(data, state, StreakFreeze.ITEM).ok)
        state.day = "2026-11-08"
        Shop.ensureToday(data, state)
        assertTrue(Shop.soldOut(state, "amulet-coin"))
        assertFalse(Shop.soldOut(state, "everstone"))
        assertFalse(state.shopPurchases.any { it.endsWith(":${StreakFreeze.ITEM}") })
        assertEquals(state.shopPurchases, SaveCodec.decode(SaveCodec.encode(state)).shopPurchases)
    }

    @Test fun failedFreezePurchasesAreAtomicAndNeverOfferMultipliers() {
        val state = TestData.state(data, 25, 20).apply { day = "2026-11-07"; cash = 1999 }
        val before = SaveCodec.encode(state)
        assertFalse(Shop.buy(data, state, StreakFreeze.ITEM).ok)
        assertEquals(before, SaveCodec.encode(state))
        state.cash = 50_000
        for (quantity in listOf(0, 2, 3, 5)) {
            assertFalse(Shop.buy(data, state, StreakFreeze.ITEM, quantity = quantity).ok)
        }
        assertEquals(50_000, state.cash)
        assertEquals(0, state.count(StreakFreeze.ITEM))
        assertTrue(state.shopPurchases.isEmpty())
    }

    @Test fun giftsAndBattleDropsRespectCapAfterShopPurchases() {
        val state = TestData.state(data, 25, 20).apply { day = "2026-11-07"; cash = 50_000 }
        repeat(3) { assertTrue(Shop.buy(data, state, StreakFreeze.ITEM).ok) }
        val events = mutableListOf<GameEvent>()
        val winningRoll = object : Random() { override fun nextBits(bitCount: Int) = 0 }
        StreakFreeze.battleDrop(state, winningRoll, events)
        assertTrue(events.isEmpty())
        assertNull(state.battleFreezeDay)
        val gift = DailyGift.give(data, state, events)
        assertNotEquals(StreakFreeze.ITEM, gift?.item)
        assertEquals(3, state.count(StreakFreeze.ITEM))
        Economy.take(state, StreakFreeze.ITEM)
        StreakFreeze.battleDrop(state, winningRoll, events)
        assertEquals(3, state.count(StreakFreeze.ITEM))
        assertEquals(state.day, state.battleFreezeDay)
    }

    @Test fun freezeSourcesAndPricesLiveInWhereToFind() {
        assertFalse(ItemInfo.facts(data, StreakFreeze.ITEM).any { it.title == "In a fight" })
        val sources = ItemInfo.whereToFind(data, StreakFreeze.ITEM)
        for (text in listOf("0.2%", "practice gives none", "Every next freeze costs more", "2,000 / 3,000 / 4,000", "holding 3")) {
            assertTrue(text, sources.contains(text))
        }
        assertFalse(sources.contains("Shop, once per game day"))
    }
}
