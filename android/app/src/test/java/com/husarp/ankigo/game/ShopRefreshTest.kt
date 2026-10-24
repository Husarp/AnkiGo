package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.random.Random

/** R10 T5: always in stock (Poké Ball + Potion), the deal of the day (-30 %), the weekend special, one price rule, the tab dot. */
class ShopRefreshTest {
    private val base = TestData.world()
    private val data = GameData(
        base.species + (900 to TestData.species(900, "Tmmon", tmMoves = setOf("thunderbolt", "watergun", "ember", "vinewhip", "earthquake", "shadowball", "bodyslam", "doubleedge", "solarbeam", "fly"))),
        base.moves, base.growth, base.itemPrices,
    )
    private val oldZone = TimeZone.getDefault()

    @Before fun utc() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    @After fun restoreZone() = TimeZone.setDefault(oldZone)

    private fun state(day: String, level: Int = 1): GameState = TestData.state(data).also { it.day = day; it.trainerLevel = level; Shop.ensureToday(data, it) }

    private val saturdays = generateSequence(LocalDate.parse("2026-01-03")) { it.plusWeeks(1) }.take(80).toList()
    private val strongTms = setOf("thunderbolt", "earthquake", "doubleedge", "solarbeam", "fly")
    private val stones = setOf("thunder-stone", "moon-stone") // the only priced evolution stones of the test world

    // ---- always in stock ----

    @Test fun theBasicsAreTheBallAndThePotion() {
        assertEquals(listOf("poke-ball", "potion"), Shop.basics())
    }

    @Test fun basicsAreBuyableEveryDayAtTheNormalPriceWhateverTheStockIs() {
        for (offset in 0 until 40L) {
            val day = LocalDate.parse("2026-10-01").plusDays(offset).toString()
            val s = state(day)
            for (item in Shop.basics()) {
                assertFalse("$item must not be drawn on $day", item in s.shopItems)
                s.cash = 10_000
                val r = Shop.buy(data, s, item)
                assertTrue("$item on $day: ${r.message}", r.ok)
                assertEquals("Bought ${Items.name(item)} for ${data.price(item)} coins", r.message)
                assertEquals(10_000 - data.price(item), s.cash)
                assertEquals(1, s.count(item))
            }
        }
    }

    @Test fun theDailyDrawNeverHoldsABasicAndKeepsItsSize() {
        for (level in listOf(1, 10, 25)) for (offset in 0 until 60L) {
            val day = LocalDate.parse("2026-10-01").plusDays(offset).toString()
            val (items, tms) = Shop.stockFor(data, day, level)
            assertTrue(items.none { it in Shop.basics() })
            val drawn = if (Shop.ultraBallDay(day)) items.drop(1) else items   // PLAN 171: the Ultra Ball's rare days add it first
            assertEquals(Shop.slots(level), drawn.size); assertEquals(Shop.slots(level), tms.size)
            assertEquals(items.size, items.toSet().size)
            assertTrue(items.all { it in Items.shopPool(data) })
        }
    }

    @Test fun aRerollNeverDrawsABasicEither() {
        val s = state("2026-10-13", level = 10)
        for (seed in 1..40) {
            s.cash = 1000
            assertTrue(Shop.reroll(data, s, Random(seed)).ok)
            assertTrue(s.shopItems.none { it in Shop.basics() })
            assertEquals(5, s.shopItems.size)
        }
    }

    @Test fun aBasicWithoutAPriceIsNotSold() {
        val noBall = GameData(data.species, data.moves, data.growth, data.itemPrices - "poke-ball")
        val s = TestData.state(noBall).also { it.day = "2026-10-13"; it.cash = 1000; Shop.ensureToday(noBall, it) }
        assertEquals(ActionResult(false, "Not in today's shop"), Shop.buy(noBall, s, "poke-ball"))
        assertEquals(1000, s.cash)
    }

    // ---- deal of the day ----

    @Test fun theDealPriceIsRoundedHalfUpAndAtLeastOne() {
        assertEquals(2100, Shop.dealPrice(3000))
        assertEquals(490, Shop.dealPrice(700))
        assertEquals(140, Shop.dealPrice(200))
        assertEquals(4, Shop.dealPrice(5))   // 3.5 -> 4
        assertEquals(1, Shop.dealPrice(2))   // 1.4 -> 1
        assertEquals(1, Shop.dealPrice(1))   // 0.7 -> 1, never free
        assertEquals(1, Shop.dealPrice(0))
        assertEquals(30, Tuning.DEAL_PERCENT)
    }

    @Test fun theDealIsOneFixedNonBasicItemOfThePoolWithTheDiscount() {
        val seen = mutableSetOf<String>()
        for (offset in 0 until 120L) {
            val day = LocalDate.parse("2026-06-01").plusDays(offset).toString()
            val d = Shop.dealFor(data, day)!!
            assertEquals(d, Shop.dealFor(data, day))
            assertTrue(d.item in Items.shopPool(data))
            assertTrue(d.item !in Shop.basics())
            assertEquals(data.price(d.item), d.oldPrice)
            assertEquals(Shop.dealPrice(d.oldPrice), d.price)
            assertTrue(d.price in 1 until d.oldPrice || d.oldPrice == 1)
            seen += d.item
        }
        assertTrue("the deal changes from day to day", seen.size > 3)
    }

    @Test fun noItemPoolMeansNoDeal() {
        val broke = GameData(data.species, data.moves, data.growth, emptyMap())
        assertNull(Shop.dealFor(broke, "2026-10-13"))
        val onlyBasics = GameData(data.species, data.moves, data.growth, mapOf("poke-ball" to 200, "potion" to 200))
        assertNull(Shop.dealFor(onlyBasics, "2026-10-13"))
    }

    @Test fun theDealPriceHoldsForAnyNumberOfBuysAndIsNotInTheStock() {
        val s = state("2026-10-13")
        val d = Shop.dealFor(data, s.day)!!
        s.shopItems.remove(d.item)
        s.cash = 100_000
        repeat(4) { Shop.buy(data, s, d.item) }
        assertEquals(100_000 - 4 * d.price, s.cash)
        assertEquals(4, s.count(d.item))
        assertEquals(d.price, Shop.priceOf(data, s, d.item))
    }

    @Test fun aPaidRefreshDoesNotChangeTheDeal() {
        val s = state("2026-10-13")
        val before = Shop.dealFor(data, s.day)
        for (seed in 1..10) { s.cash = 500; assertTrue(Shop.reroll(data, s, Random(seed)).ok) }
        assertEquals(before, Shop.dealFor(data, s.day))
    }

    @Test fun theDealItemCostsTheDealPriceInTheStockTileToo() {
        val s = state("2026-10-13")
        val d = Shop.dealFor(data, s.day)!!
        s.shopItems.clear(); s.shopItems += listOf(d.item, "great-ball")
        assertEquals(d.price, Shop.priceOf(data, s, d.item))
        assertEquals(data.price("great-ball"), Shop.priceOf(data, s, "great-ball"))
        s.cash = 100_000
        val r = Shop.buy(data, s, d.item)
        assertEquals("Bought ${Items.name(d.item)} for ${d.price} coins", r.message)
        assertEquals(100_000 - d.price, s.cash)
    }

    @Test fun otherItemsAndTheBasicsKeepTheirNormalPrice() {
        val s = state("2026-10-13")
        val d = Shop.dealFor(data, s.day)!!
        for (item in Items.shopPool(data).filter { it != d.item }) assertEquals(data.price(item), Shop.priceOf(data, s, item))
        for (item in Shop.basics()) assertEquals(data.price(item), Shop.priceOf(data, s, item))
    }

    @Test fun theDealOfYesterdayIsNotOnSaleToday() {
        val s = state("2026-10-13")
        s.cash = 100_000
        val today = Shop.dealFor(data, s.day)!!.item
        val other = (14..30).map { Shop.dealFor(data, "2026-10-$it")!!.item }.first { it != today && it !in s.shopItems && it !in Shop.basics() }
        val r = Shop.buy(data, s, other)
        assertEquals(ActionResult(false, "Not in today's shop"), r)
        assertEquals(100_000, s.cash)
    }

    // ---- weekend special ----

    @Test fun onlyTheGameWeekendHasASpecialAndSaturdayEqualsSunday() {
        for (sat in saturdays) {
            val sp = Shop.weekendFor(data, sat.toString())
            assertNotNull(sp)
            assertEquals(sp, Shop.weekendFor(data, sat.plusDays(1).toString()))
            assertEquals(sat.plusDays(1).toString(), sp!!.endsDay)
            for (i in 2..6L) assertNull("weekday ${sat.plusDays(i)}", Shop.weekendFor(data, sat.plusDays(i).toString()))
            assertNull(Shop.weekendFor(data, sat.minusDays(1).toString()))
            assertTrue(Shop.isWeekend(sat.toString())); assertTrue(Shop.isWeekend(sat.plusDays(1).toString()))
            for (i in 2..6L) assertFalse(Shop.isWeekend(sat.plusDays(i).toString()))
        }
    }

    @Test fun theSpecialIsAPricedStoneOrAStrongTmAtTheNormalPriceAndBothKindsComeUp() {
        val kinds = mutableSetOf<Boolean>()
        for (sat in saturdays) {
            val sp = Shop.weekendFor(data, sat.toString())!!
            assertTrue("exactly one of item / tm", (sp.item == null) != (sp.tm == null))
            kinds += sp.isTm
            if (sp.isTm) {
                assertTrue(sp.tm in strongTms)
                assertTrue((data.moves.getValue(sp.tm!!).power) >= Tuning.WEEKEND_TM_MIN_POWER)
                assertEquals(Tuning.TM_PRICE, sp.price)
                assertEquals(sp.tm, sp.id)
            } else {
                assertTrue(sp.item in stones)
                assertTrue(sp.item!!.endsWith("-stone"))
                assertEquals(data.price(sp.item!!), sp.price)
                assertEquals(sp.item, sp.id)
            }
        }
        assertEquals(setOf(true, false), kinds)
        assertEquals(90, Tuning.WEEKEND_TM_MIN_POWER)
    }

    @Test fun anEmptyKindFallsBackToTheOtherAndNothingMeansNone() {
        val weakTms = GameData(
            base.species + (900 to TestData.species(900, "Tmmon", tmMoves = setOf("bodyslam", "watergun", "shadowball"))),
            base.moves, base.growth, base.itemPrices,
        )
        val noStones = GameData(data.species, data.moves, data.growth, data.itemPrices - "thunder-stone" - "moon-stone")
        val neither = GameData(weakTms.species, weakTms.moves, weakTms.growth, data.itemPrices - "thunder-stone" - "moon-stone")
        for (sat in saturdays) {
            assertFalse(Shop.weekendFor(weakTms, sat.toString())!!.isTm)
            assertTrue(Shop.weekendFor(noStones, sat.toString())!!.isTm)
            assertNull(Shop.weekendFor(neither, sat.toString()))
        }
    }

    @Test fun aBadDayIsNoWeekend() {
        assertFalse(Shop.isWeekend("")); assertFalse(Shop.isWeekend("soon"))
        assertNull(Shop.weekendFor(data, "")); assertNull(Shop.weekendFor(data, "2026-13-40"))
    }

    @Test fun theWeekendIsTheGameDayNotTheCalendarDay() {
        val g = Game(data, state("2026-10-13"), Random(1)) { 0L }
        fun at(y: Int, mo: Int, d: Int, h: Int) = ZonedDateTime.of(y, mo, d, h, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        // dayStartHour 4: Saturday 2026-10-17 02:00 is still the Friday game day, Monday 02:00 still the Sunday one.
        assertFalse(Shop.isWeekend(g.dayKey(at(2026, 10, 17, 2))))
        assertNull(Shop.weekendFor(data, g.dayKey(at(2026, 10, 17, 2))))
        assertTrue(Shop.isWeekend(g.dayKey(at(2026, 10, 17, 4))))
        assertTrue(Shop.isWeekend(g.dayKey(at(2026, 10, 19, 2))))
        assertEquals(Shop.weekendFor(data, "2026-10-17"), Shop.weekendFor(data, g.dayKey(at(2026, 10, 19, 2))))
        assertFalse(Shop.isWeekend(g.dayKey(at(2026, 10, 19, 4))))
        g.state.settings.dayStartHour = 0
        assertTrue(Shop.isWeekend(g.dayKey(at(2026, 10, 17, 0))))
        assertFalse(Shop.isWeekend(g.dayKey(at(2026, 10, 19, 0))))
    }

    // ---- buy / buyTm accept and reject each source ----

    private fun satWith(tm: Boolean) = saturdays.first { Shop.weekendFor(data, it.toString())!!.isTm == tm }

    @Test fun theWeekendStoneIsBoughtOnTheWeekendOnlyAtItsPrice() {
        val sat = satWith(tm = false)
        val sp = Shop.weekendFor(data, sat.toString())!!
        val s = state(sat.toString()); s.shopItems.remove(sp.item); s.cash = 100_000
        val expected = Shop.priceOf(data, s, sp.item!!)
        val r = Shop.buy(data, s, sp.item!!)
        assertTrue(r.message, r.ok)
        assertEquals("Bought ${Items.name(sp.item!!)} for $expected coins", r.message)
        assertEquals(100_000 - expected, s.cash)
        // on the Sunday it is still there, and several can be bought
        s.day = sat.plusDays(1).toString(); Shop.ensureToday(data, s); s.shopItems.remove(sp.item)
        assertTrue(Shop.buy(data, s, sp.item!!).ok)
        assertEquals(2, s.count(sp.item!!))
        // on Monday it is not (unless the stock or the deal happens to hold the same stone)
        s.day = sat.plusDays(2).toString(); Shop.ensureToday(data, s); s.shopItems.remove(sp.item)
        if (Shop.dealFor(data, s.day)!!.item != sp.item) assertEquals(ActionResult(false, "Not in today's shop"), Shop.buy(data, s, sp.item!!))
    }

    @Test fun theWeekendTmIsBoughtOnceAtThePriceAndAnOwnedOneIsRefused() {
        val sat = satWith(tm = true)
        val sp = Shop.weekendFor(data, sat.toString())!!
        val s = state(sat.toString()); s.shopTms.remove(sp.tm); s.cash = 5_000
        val r = Shop.buyTm(data, s, sp.tm!!)
        assertTrue(r.message, r.ok)
        assertEquals("Bought ${Items.tmName(data, sp.tm!!)} for 1000 coins", r.message)
        assertEquals(4_000, s.cash); assertTrue(sp.tm!! in s.tms)
        val again = Shop.buyTm(data, s, sp.tm!!)
        assertFalse(again.ok); assertEquals("You already own ${Items.tmName(data, sp.tm!!)}", again.message)
        assertEquals(4_000, s.cash)
        // a TM is not an item: buy() refuses it
        assertFalse(Shop.buy(data, s, sp.tm!!).ok)
    }

    @Test fun theWeekendTmIsRefusedOnWeekdaysAndOtherTmsStillNeedTheStock() {
        val sat = satWith(tm = true)
        val sp = Shop.weekendFor(data, sat.toString())!!
        val s = state(sat.plusDays(2).toString()); s.shopTms.remove(sp.tm); s.cash = 5_000
        assertEquals(ActionResult(false, "Not in today's shop"), Shop.buyTm(data, s, sp.tm!!))
        assertEquals(5_000, s.cash)
        // on the weekend a TM that is neither in the stock nor the special is refused
        s.day = sat.toString(); Shop.ensureToday(data, s)
        val stray = strongTms.first { it != sp.tm && it !in s.shopTms }
        assertFalse(Shop.buyTm(data, s, stray).ok)
        // a stock TM works as before
        val listed = s.shopTms.first()
        if (listed !in s.tms) assertTrue(Shop.buyTm(data, s, listed).ok)
    }

    @Test fun anItemOutsideEverySourceIsRefusedAndKeepsTheMoney() {
        val s = state("2026-10-14") // a Wednesday
        s.cash = 100_000
        val d = Shop.dealFor(data, s.day)!!.item
        val outside = Items.shopPool(data).first { it !in s.shopItems && it !in Shop.basics() && it != d }
        assertEquals(ActionResult(false, "Not in today's shop"), Shop.buy(data, s, outside))
        assertEquals(100_000, s.cash); assertEquals(0, s.count(outside))
        assertFalse(Shop.buy(data, s, "no-such-item").ok)
    }

    @Test fun notEnoughMoneyNamesTheRealPrice() {
        val s = state("2026-10-13")
        val d = Shop.dealFor(data, s.day)!!
        s.cash = d.price - 1
        assertEquals("Not enough money (need ${d.price} coins)", Shop.buy(data, s, d.item).message)
        assertEquals(d.price - 1, s.cash)
    }

    // ---- stats and achievements count what was paid ----

    @Test fun spentStatsAndAchievementsCountThePriceReallyPaid() {
        val s = state("2026-10-13"); s.cash = 100_000
        val d = Shop.dealFor(data, s.day)!!
        Shop.buy(data, s, d.item); Shop.buy(data, s, "potion")
        val sat = satWith(tm = true)
        val sp = Shop.weekendFor(data, sat.toString())!!
        val w = state(sat.toString()); w.cash = 100_000
        Shop.buyTm(data, w, sp.tm!!)
        val dealPaid = d.price + data.price("potion")
        assertEquals(dealPaid.toLong(), s.counters[Metric.MONEY_SPENT.name])
        assertEquals(dealPaid, s.history[s.day]!!.spent)
        assertEquals(100_000 - dealPaid, s.cash)
        assertEquals(1000L, w.counters[Metric.MONEY_SPENT.name])
        assertEquals(1000, w.history[w.day]!!.spent)
    }

    // ---- the tab dot ----

    @Test fun theDotShowsUntilTheTabIsOpenedOnTheGameDay() {
        assertTrue(Shop.unseen(null, "2026-10-13"))
        assertTrue(Shop.unseen("2026-10-12", "2026-10-13"))
        assertFalse(Shop.unseen("2026-10-13", "2026-10-13"))
        assertTrue(Shop.unseen("2026-10-14", "2026-10-13")) // a clock going back counts as new
        assertFalse(Shop.unseen(null, ""))
        assertFalse(Shop.unseen("2026-10-13", ""))
    }

    // ---- old saves ----

    @Test fun anOldSaveLoadsAndBuysAsBefore() {
        val s = state("2026-10-13", level = 5); s.cash = 100_000
        val back = SaveCodec.decode(SaveCodec.encode(s))
        assertEquals(s.shopItems.toList(), back.shopItems.toList())
        assertEquals(s.shopTms.toList(), back.shopTms.toList())
        val item = back.shopItems.first()
        val price = Shop.priceOf(data, back, item)
        assertTrue(Shop.buy(data, back, item).ok)
        assertEquals(100_000 - price, back.cash)
        assertTrue(Shop.buy(data, back, "poke-ball").ok)
        val tm = back.shopTms.first()
        assertTrue(Shop.buyTm(data, back.also { it.cash = 2000 }, tm).ok)
        assertEquals(1000, back.cash)
    }
}
