package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R24EShopTest {
    @Test fun refreshStepsPersistAndResetOnlyOnANewGameDay() {
        val d = TestData.world()
        var s = TestData.state(d).also { it.day = "2026-10-31"; it.cash = 10000 }
        Shop.ensureToday(d, s)
        for (price in listOf(100, 200, 300)) {
            assertEquals(price, Shop.refreshPrice(s))
            val before = s.cash
            assertTrue(Shop.reroll(d, s, Random(price)).ok)
            assertEquals(before - price, s.cash)
            s = SaveCodec.decode(SaveCodec.encode(s))
            Shop.ensureToday(d, s)
        }
        assertEquals(600, s.history.getValue(s.day).spent)
        assertEquals(400, Shop.refreshPrice(s))
        s.cash = 399
        val items = s.shopItems.toList()
        assertFalse(Shop.reroll(d, s, Random(1)).ok)
        assertEquals(399, s.cash)
        assertEquals(items, s.shopItems)
        assertEquals(400, Shop.refreshPrice(s))
        s.day = "2026-11-01"
        assertEquals(100, Shop.refreshPrice(s))
        assertTrue(Shop.reroll(d, s, Random(2)).ok)
        assertEquals(299, s.cash)
        assertEquals(200, Shop.refreshPrice(s))
    }

    @Test fun oldSavesStartAtFirstRefreshPrice() {
        val s = SaveCodec.decode("""{"schema":1,"mons":[],"day":"2026-10-31","shopDay":"2026-10-31"}""")
        assertEquals(0, s.shopRefreshes)
        assertEquals(100, Shop.refreshPrice(s))
    }
}
