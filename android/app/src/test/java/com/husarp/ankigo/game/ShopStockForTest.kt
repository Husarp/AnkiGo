package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Shop.stockFor is the draw ensureToday uses: tomorrow's stock (reminders) can't drift from the real one. */
class ShopStockForTest {
    private val base = TestData.world()
    private val data = GameData(
        base.species + (900 to TestData.species(900, "Tmmon", tmMoves = setOf("thunderbolt", "watergun", "ember", "vinewhip", "earthquake", "shadowball"))),
        base.moves, base.growth, base.itemPrices,
    )

    @Test fun matchesEnsureTodayForTheSameDayAndLevel() {
        for (level in listOf(1, 5, 10, 25)) for (day in listOf("2026-10-13", "2026-10-14", "2027-01-01", "2026-12-31")) {
            val s = TestData.state(data)
            s.day = day; s.trainerLevel = level
            Shop.ensureToday(data, s)
            val (items, tms) = Shop.stockFor(data, day, level)
            assertEquals("items $day L$level", s.shopItems.toList(), items)
            assertEquals("tms $day L$level", s.shopTms.toList(), tms)
            assertEquals(Shop.slots(level), items.size)
        }
    }

    @Test fun isPureAndDifferentPerDay() {
        assertEquals(Shop.stockFor(data, "2026-10-14", 3), Shop.stockFor(data, "2026-10-14", 3))
        assertTrue((13..20).map { Shop.stockFor(data, "2026-10-$it", 3) }.toSet().size > 1)
    }

    @Test fun doesNotTouchAnyState() {
        val s = TestData.state(data)
        Shop.ensureToday(data, s)
        val items = s.shopItems.toList()
        Shop.stockFor(data, "2030-01-01", 50)
        assertEquals(items, s.shopItems.toList())
    }

    @Test fun rerollStillDrawsFromTheGivenGenerator() {
        val s = TestData.state(data)
        s.cash = 1000
        Shop.ensureToday(data, s)
        assertTrue(Shop.reroll(data, s, Random(7)).ok)
        val (items, tms) = run {
            val t = TestData.state(data); t.cash = 1000; Shop.ensureToday(data, t); Shop.reroll(data, t, Random(7)); t.shopItems.toList() to t.shopTms.toList()
        }
        assertEquals(items, s.shopItems.toList()); assertEquals(tms, s.shopTms.toList())
        assertEquals("2026-10-13", s.shopDay)
    }
}
