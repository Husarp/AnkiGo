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
        for (price in listOf(0, 100, 200)) {
            assertEquals(price, Shop.refreshPrice(s))
            val before = s.cash
            assertTrue(Shop.reroll(d, s, Random(price)).ok)
            assertEquals(before - price, s.cash)
            s = SaveCodec.decode(SaveCodec.encode(s))
            Shop.ensureToday(d, s)
        }
        assertEquals(300, s.history.getValue(s.day).spent)
        assertEquals(300, Shop.refreshPrice(s))
        s.cash = 299
        val items = s.shopItems.toList()
        assertFalse(Shop.reroll(d, s, Random(1)).ok)
        assertEquals(299, s.cash)
        assertEquals(items, s.shopItems)
        assertEquals(300, Shop.refreshPrice(s))
        s.day = "2026-11-01"
        assertEquals(0, Shop.refreshPrice(s))
        assertTrue(Shop.reroll(d, s, Random(2)).ok)
        assertEquals(299, s.cash)
        assertEquals(100, Shop.refreshPrice(s))
    }

    @Test fun oldSavesStartAtFirstRefreshPrice() {
        val s = SaveCodec.decode("""{"schema":1,"mons":[],"day":"2026-10-31","shopDay":"2026-10-31"}""")
        assertEquals(0, s.shopRefreshes)
        assertEquals(0, Shop.refreshPrice(s))
    }
}
