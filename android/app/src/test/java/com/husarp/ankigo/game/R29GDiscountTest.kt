package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import com.husarp.ankigo.ui.ShopPlan
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

class R29GDiscountTest {
    private val data = TestData.world()
    private fun at(day: String, hour: Int, minute: Int = 0) = LocalDate.parse(day)
        .atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun everyPercentageAppearsInBothPeriodsAndStaysFixedWithinTheGameDay() {
        val daySeen = mutableSetOf<Int>()
        val nightSeen = mutableSetOf<Int>()
        for (offset in 0L until 365L) {
            val day = LocalDate.parse("2026-01-01").plusDays(offset).toString()
            val daytime = Shop.dealFor(data, day, at(day, 6))!!
            val night = Shop.dealFor(data, day, at(day, 21))!!
            assertEquals(daytime, Shop.dealFor(data, day, at(day, 20, 59)))
            assertEquals(night, Shop.dealFor(data, day, at(day, 23, 59)))
            assertEquals(night, Shop.dealFor(data, day, at(day, 0)))
            assertEquals(night, Shop.dealFor(data, day, at(day, 5, 59)))
            assertNotEquals(daytime.item, night.item)
            for ((deal, hour) in listOf(daytime to 12, night to 22)) {
                assertTrue(deal.percent in Tuning.DEAL_PERCENTS)
                assertEquals(((deal.oldPrice.toLong() * (100 - deal.percent) + 50) / 100).coerceAtLeast(1).toInt(), deal.price)
                val price = ShopPlan.price(data, day, deal.item, at(day, hour))
                assertEquals(deal.price, price.now)
                assertEquals(deal.oldPrice, price.was)
                assertEquals(deal.percent, price.percent)
                assertEquals(deal.price, Shop.priceOf(data, day, deal.item, at(day, hour)))
            }
            daySeen += daytime.percent
            nightSeen += night.percent
        }
        assertEquals(Tuning.DEAL_PERCENTS.toSet(), daySeen)
        assertEquals(Tuning.DEAL_PERCENTS.toSet(), nightSeen)
    }

    @Test fun oldSavesRefreshesAndRepeatedBulkPurchasesKeepTheOfferAndChargeItsPrice() {
        for (hour in listOf(12, 22)) {
            val day = "2026-11-03"
            val now = at(day, hour)
            val state = SaveCodec.decode("""{"schema":1,"mons":[],"day":"2026-11-03","shopDay":"2026-11-03"}""")
            state.cash = 1000000
            Shop.ensureToday(data, state)
            val offer = Shop.dealFor(data, state.day, now)!!
            assertTrue(Shop.reroll(data, state, Random(9), now).ok)
            val loaded = SaveCodec.decode(SaveCodec.encode(state))
            Shop.ensureToday(data, loaded)
            assertEquals(offer, Shop.dealFor(data, loaded.day, now))
            for (quantity in listOf(1, 3, 5)) {
                val before = loaded.cash
                assertTrue(Shop.buy(data, loaded, offer.item, now, quantity).ok)
                assertEquals(before - offer.price * quantity, loaded.cash)
                assertEquals(offer, Shop.dealFor(data, loaded.day, now))
            }
        }
    }

    @Test fun allDiscountsRoundHalfUpWithAMinimumOfOneAndNoIntegerOverflow() {
        for (percent in Tuning.DEAL_PERCENTS) {
            for (normal in listOf(0, 1, 2, 5, 10, 50, 700, 3000, Int.MAX_VALUE)) {
                val expected = ((normal.toLong() * (100 - percent) + 50) / 100).coerceAtLeast(1).toInt()
                assertEquals(expected, Shop.dealPrice(normal, percent))
            }
        }
        assertEquals(7, Shop.dealPrice(10, 35)) // 6.5 rounds up
    }
}
