package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class R34AShopTest {
    private val data = TestData.world()

    @Test fun slotsGrowEveryTenLevelsAndStopAtTheirCaps() {
        for ((level, items, tms) in listOf(Triple(1, 3, 2), Triple(9, 3, 2), Triple(10, 4, 3),
            Triple(19, 4, 3), Triple(20, 5, 4), Triple(100, 5, 4))) {
            assertEquals(items, Shop.slots(level))
            assertEquals(tms, Shop.tmSlots(level))
        }
    }

    @Test fun tmPricesUseTheItemVariationAndRoundingAndChargeExactlyThatAmount() {
        val seen = mutableSetOf<Int>()
        for (offset in 0..90L) {
            val day = LocalDate.parse("2026-11-01").plusDays(offset).toString()
            val price = Shop.tmPrice(day, "thunderbolt")
            val priced = GameData(data.species, data.moves, data.growth, mapOf("tm:thunderbolt" to Tuning.TM_PRICE))
            assertEquals(Shop.dailyPrice(priced, day, "tm:thunderbolt"), price)
            assertEquals(price, Shop.tmPrice(day, "thunderbolt"))
            assertTrue(price in 900..1100)
            seen += price
            val s = TestData.state(data).also { it.day = day; it.shopTms += "thunderbolt"; it.cash = price - 1 }
            assertFalse(Shop.buyTm(data, s, "thunderbolt").ok)
            assertEquals(price - 1, s.cash)
            s.cash = price
            assertTrue(Shop.buyTm(data, s, "thunderbolt").ok)
            assertEquals(0, s.cash)
            assertEquals(price, s.history.getValue(day).spent)
            assertEquals(price.toLong(), s.counters[Metric.MONEY_SPENT.name])
        }
        assertTrue(seen.size > 1)
    }

    @Test fun freeRefreshUsesGameDaySurvivesReloadAndResetsOnSundayAndMonday() {
        var s = TestData.state(data).also { it.day = "2026-11-07"; it.cash = 0 }
        assertEquals(0, Shop.refreshPrice(s))
        assertTrue(Shop.reroll(data, s, Random(1)).ok)
        assertEquals(0, s.cash)
        s = SaveCodec.decode(SaveCodec.encode(s))
        Shop.ensureToday(data, s)
        assertEquals(100, Shop.refreshPrice(s))
        assertFalse(Shop.reroll(data, s, Random(1)).ok)
        assertEquals(1, s.shopRefreshes)
        s.cash = 300
        assertTrue(Shop.reroll(data, s, Random(2)).ok)
        assertEquals(200, Shop.refreshPrice(s))
        s.day = "2026-11-08"
        assertEquals(0, Shop.refreshPrice(s))
        assertTrue(Shop.reroll(data, s, Random(3)).ok)
        assertEquals(100, Shop.refreshPrice(s))
        s.day = "2026-11-09"
        assertEquals(100, Shop.refreshPrice(s))
        assertTrue(Shop.reroll(data, s, Random(4)).ok)
        assertEquals(200, Shop.refreshPrice(s))
    }

    @Test fun alreadyDrawnStockSurvivesReloadUntilTheNextDay() {
        val s = TestData.state(data).also { it.shopDay = it.day; it.trainerLevel = 100 }
        s.shopItems += Items.shopPool(data).filter { it !in Shop.basics() && it !in Shop.weekendPrices && Items.def(it)?.kind != ItemKind.BALL }.take(6)
        s.shopTms += listOf("watergun", "ember", "vinewhip", "thunderbolt", "earthquake")
        val back = SaveCodec.decode(SaveCodec.encode(s))
        Shop.ensureToday(data, back)
        val date = LocalDate.parse(s.day)
        val reserved = listOf(12, 22).mapNotNull { hour ->
            val now = date.atTime(hour, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            Shop.dealFor(data, s.day, now)?.item
        }
        assertTrue(back.shopItems.containsAll(s.shopItems.filter { it !in reserved }))
        assertTrue(reserved.none { it in back.shopItems })
        assertEquals(6, back.shopItems.count { Items.def(it)?.kind != ItemKind.BALL })
        assertEquals(s.shopTms, back.shopTms)
        back.day = "2026-10-14"
        Shop.ensureToday(data, back)
        assertTrue(back.shopTms.size <= 4)
        assertTrue(back.shopItems.count { Items.def(it)?.kind != ItemKind.BALL } <= 5)
    }
}
