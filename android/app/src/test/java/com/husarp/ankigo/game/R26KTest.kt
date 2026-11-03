package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import com.husarp.ankigo.ui.ShopPlan
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class R26KTest {
    private fun at(hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 1, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test fun boundariesAndCountdownUseLocalClock() {
        for ((h, m, night) in listOf(Triple(5,59,true), Triple(6,0,false), Triple(20,59,false), Triple(21,0,true), Triple(0,0,true))) {
            assertEquals(night, Shop.isNight(at(h,m)))
            assertTrue(Shop.dealEndsAt(at(h,m)) > at(h,m))
        }
        assertEquals("Ends in 2 h 53 min", ShopPlan.dealEndsText(at(18,7)))
        assertEquals("Ends in 2 h 53 min", ShopPlan.dealEndsText(at(3,7)))
        assertEquals("Ends in 0 h 1 min", ShopPlan.dealEndsText(at(20,59)))
    }

    @Test fun differentDealAndActualPurchasePriceAtBothBoundaries() {
        val data = TestData.world()
        val day = "2026-11-01"
        val daytime = Shop.dealFor(data, day, at(6))!!
        val night = Shop.dealFor(data, day, at(21))!!
        assertNotEquals(daytime.item, night.item)
        assertEquals(night, Shop.dealFor(data, day, at(2)))
        for (hour in listOf(5,6,20,21)) {
            val now = at(hour)
            val deal = Shop.dealFor(data, day, now)!!
            val state = TestData.state(data).apply { this.day = day; cash = 100000; shopItems.clear() }
            assertEquals(Shop.dealPrice(deal.oldPrice, deal.percent), deal.price)
            assertEquals(deal.price, ShopPlan.price(data, day, deal.item, now).now)
            assertTrue(Shop.buy(data, state, deal.item, now, 3).ok)
            assertEquals(100000 - 3 * deal.price, state.cash)
        }
    }

    @Test fun expiredDealLosesItsDiscountAndExclusiveAvailability() {
        val data = TestData.world()
        val state = TestData.state(data).apply { day = "2026-11-02"; cash = 100000; shopItems.clear() }
        val old = Shop.dealFor(data, state.day, at(20))!!
        assertEquals(Shop.dailyPrice(data, state.day, old.item), Shop.priceOf(data, state, old.item, at(21)))
        assertFalse(Shop.buy(data, state, old.item, at(21)).ok)
        assertEquals(100000, state.cash)
        state.shopItems.add(old.item)
        val price = Shop.priceOf(data, state, old.item, at(21))
        assertTrue(Shop.buy(data, state, old.item, at(21)).ok)
        assertEquals(100000 - price, state.cash)
    }

    @Test fun savedCountdownClampsOnlyAboveMaximum() {
        val state = TestData.state(TestData.world())
        assertEquals(60..140, Tuning.ITEM_COUNTDOWN)
        for (n in listOf(0,1,59,60,140,141,385)) {
            state.itemCountdown = n
            assertEquals(n.coerceAtMost(140), SaveCodec.decode(SaveCodec.encode(state)).itemCountdown)
        }
    }
}
