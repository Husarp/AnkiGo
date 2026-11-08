package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R32CShopTest {
    @Test fun dailyBallsExcludeBasicsAndKeepGreatUltraRatio() {
        assertEquals(linkedMapOf("great-ball" to 85, "ultra-ball" to 15), Tuning.REGULAR_SHOP_WEIGHTS)
        assertEquals(17 * Tuning.REGULAR_SHOP_WEIGHTS.getValue("ultra-ball"), 3 * Tuning.REGULAR_SHOP_WEIGHTS.getValue("great-ball"))
        repeat(1000) {
            val balls = Shop.dailyBalls("day-$it")
            assertTrue(balls.first() in setOf("great-ball", "ultra-ball"))
            assertTrue(balls.none { id -> id in Shop.basics() })
            assertEquals(balls, Shop.dailyBalls("day-$it"))
            if (balls.size == 2) assertTrue(balls.last() in Tuning.SPECIAL_SHOP_WEIGHTS)
        }
    }

    @Test fun oldSameDayStockAndRerollsKeepPokeBallBuyableInBasics() {
        val data = TestData.world()
        val state = TestData.state(data).apply {
            day = "2026-11-08"; shopDay = day; cash = 100000
            shopItems.clear(); shopItems += listOf("poke-ball", "super-potion")
            bag["poke-ball"] = 12
        }.let { SaveCodec.decode(SaveCodec.encode(it)) }
        Shop.ensureToday(data, state)
        assertFalse("poke-ball" in state.shopItems)
        assertTrue("super-potion" in state.shopItems)
        assertEquals(12, state.count("poke-ball"))
        repeat(2) {
            val price = Shop.priceOf(data, state, "poke-ball")
            val before = state.cash
            assertTrue(Shop.buy(data, state, "poke-ball", quantity = 5).ok)
            assertEquals(before - price * 5, state.cash)
            assertFalse(Shop.soldOut(state, "poke-ball"))
            assertTrue(Shop.reroll(data, state, Random(it)).ok)
            assertFalse("poke-ball" in state.shopItems)
        }
        assertEquals(22, state.count("poke-ball"))
        val sources = ItemInfo.whereToFind(data, "poke-ball")
        assertTrue(sources.contains("Always in the Shop, in Basics"))
        state.cash = 0
        val before = SaveCodec.encode(state)
        assertFalse(Shop.buy(data, state, "poke-ball").ok)
        assertEquals(before, SaveCodec.encode(state))
    }
}
