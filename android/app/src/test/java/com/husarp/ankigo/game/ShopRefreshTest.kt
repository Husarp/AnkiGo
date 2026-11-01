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

/** R10 T5: always in stock (Potion), the deal of the day (-30 %), the weekend special, one price rule, the tab dot. */
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

    @Test fun onlyPotionsAreAlwaysInStock() {
        assertEquals(listOf("potion"), Shop.basics())
    }

    @Test fun basicsAreBuyableEveryDayAtTheDailyPriceWhateverTheStockIs() {
        for (offset in 0 until 40L) {
            val day = LocalDate.parse("2026-10-01").plusDays(offset).toString()
            val s = state(day)
            for (item in Shop.basics()) {
                assertFalse("$item must not be drawn on $day", item in s.shopItems)
                s.cash = 10_000
                val r = Shop.buy(data, s, item)
                assertTrue("$item on $day: ${r.message}", r.ok)
                assertEquals("Bought ${Items.name(item)} for ${Shop.priceOf(data, s, item)} coins", r.message)
                assertEquals(10_000 - Shop.priceOf(data, s, item), s.cash)
                assertEquals(1, s.count(item))
            }
        }
    }

    @Test fun theDailyDrawNeverHoldsABasicAndKeepsItsSize() {
        for (level in listOf(1, 10, 25)) for (offset in 0 until 60L) {
            val day = LocalDate.parse("2026-10-01").plusDays(offset).toString()
            val (items, tms) = Shop.stockFor(data, day, level)
            assertTrue(items.none { it in Shop.basics() })
            val drawn = items.filter { Items.def(it)?.kind != ItemKind.BALL }
            assertEquals(Shop.slots(level), drawn.size); assertEquals(Shop.slots(level), tms.size)
            assertEquals(items.size, items.toSet().size)
            assertTrue(items.all { it in Items.shopPool(data) })
        }
    }

    @Test fun aRerollNeverDrawsABasicEither() {
        val s = state("2026-10-13", level = 10)
        for (seed in 1..40) {
            s.cash = 100_000
            assertTrue(Shop.reroll(data, s, Random(seed)).ok)
            assertTrue(s.shopItems.none { it in Shop.basics() })
            assertEquals(5 + Shop.dailyBalls(s.day).size, s.shopItems.size)
        }
    }

    @Test fun aBasicWithoutApiPriceUsesProposalPrice() {
        val noPotion = GameData(data.species, data.moves, data.growth, data.itemPrices - "potion")
        val s = TestData.state(noPotion).also { it.day = "2026-10-13"; it.cash = 1000; Shop.ensureToday(noPotion, it) }
        assertTrue(Shop.buy(noPotion, s, "potion").ok)
        assertEquals(1000 - Shop.priceOf(noPotion, s, "potion"), s.cash)
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
            assertEquals(Shop.dailyPrice(data, day, d.item), d.oldPrice)
            assertEquals(Shop.dealPrice(d.oldPrice), d.price)
            assertTrue(d.price in 1 until d.oldPrice || d.oldPrice == 1)
            seen += d.item
        }
        assertTrue("the deal changes from day to day", seen.size > 3)
    }

    @Test fun proposalProvidesDealPoolWithoutApiPrices() {
        val broke = GameData(data.species, data.moves, data.growth, emptyMap())
        assertNotNull(Shop.dealFor(broke, "2026-10-13"))
        val onlyBasics = GameData(data.species, data.moves, data.growth, mapOf("poke-ball" to 200, "potion" to 200))
        assertNotNull(Shop.dealFor(onlyBasics, "2026-10-13"))
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
        for (seed in 1..10) { s.cash = 100_000; assertTrue(Shop.reroll(data, s, Random(seed)).ok) }
        assertEquals(before, Shop.dealFor(data, s.day))
    }

    @Test fun theDealItemCostsTheDealPriceInTheStockTileToo() {
        val s = state("2026-10-13")
        val d = Shop.dealFor(data, s.day)!!
        s.shopItems.clear(); s.shopItems += listOf(d.item, "great-ball")
        assertEquals(d.price, Shop.priceOf(data, s, d.item))
        assertEquals(Shop.dailyPrice(data, s.day, "great-ball"), Shop.priceOf(data, s, "great-ball"))
        s.cash = 100_000
        val r = Shop.buy(data, s, d.item)
        assertEquals("Bought ${Items.name(d.item)} for ${d.price} coins", r.message)
        assertEquals(100_000 - d.price, s.cash)
    }

    @Test fun otherItemsAndBasicsUseTheirDailyPrice() {
        val s = state("2026-10-13")
        val d = Shop.dealFor(data, s.day)!!
        for (item in Items.shopPool(data).filter { it != d.item && it !in Shop.weekendPrices }) assertEquals(Shop.dailyPrice(data, s.day, item), Shop.priceOf(data, s, item))
        for (item in Shop.basics()) assertEquals(Shop.dailyPrice(data, s.day, item), Shop.priceOf(data, s, item))
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

    @Test fun onlyTheGameWeekendHasASpecialWithSameItemBothDays() {
        for (sat in saturdays) {
            val sp = Shop.weekendFor(data, sat.toString())
            assertNotNull(sp)
            assertEquals(sp!!.id, Shop.weekendFor(data, sat.plusDays(1).toString())!!.id)
            assertEquals(sat.plusDays(1).toString(), sp!!.endsDay)
            for (i in 2..6L) assertNull("weekday ${sat.plusDays(i)}", Shop.weekendFor(data, sat.plusDays(i).toString()))
            assertNull(Shop.weekendFor(data, sat.minusDays(1).toString()))
            assertTrue(Shop.isWeekend(sat.toString())); assertTrue(Shop.isWeekend(sat.plusDays(1).toString()))
            for (i in 2..6L) assertFalse(Shop.isWeekend(sat.plusDays(i).toString()))
        }
    }

    @Test fun weekendUsesExclusiveFixedPricesAndCoversAllElevenItems() {
        val seen = mutableSetOf<String>()
        for (sat in saturdays) {
            val sp = Shop.weekendFor(data, sat.toString())!!
            assertFalse(sp.isTm)
            assertTrue(sp.item in Shop.weekendPrices)
            assertEquals(Shop.weekendPrices[sp.item], sp.price)
            assertEquals(sp.price, Shop.priceOf(data, sat.plusDays(1).toString(), sp.item!!))
            seen += sp.id
        }
        assertEquals(Shop.weekendPrices.keys, seen)
    }

    @Test fun exclusiveItemsNeverEnterNormalStockRerollsOrDealsAndOldStockIsCleaned() {
        assertEquals(mapOf(
            "lucky-egg" to 6000, "amulet-coin" to 6000, "luck-incense" to 6000,
            "macho-brace" to 2500, "soothe-bell" to 2500,
            "power-weight" to 2000, "power-bracer" to 2000, "power-belt" to 2000,
            "power-lens" to 2000, "power-band" to 2000, "power-anklet" to 2000,
        ), Shop.weekendPrices)
        for (sat in saturdays) {
            val s = state(sat.toString(), 100)
            s.cash = 100000
            assertTrue(s.shopItems.none { it in Shop.weekendPrices })
            assertTrue(Shop.dealFor(data, s.day)!!.item !in Shop.weekendPrices)
            assertTrue(Shop.reroll(data, s, Random(sat.hashCode())).ok)
            assertTrue(s.shopItems.none { it in Shop.weekendPrices })
            s.shopItems.addAll(Shop.weekendPrices.keys)
            val inventory = s.bag.toMap()
            Shop.ensureToday(data, s)
            assertTrue(s.shopItems.none { it in Shop.weekendPrices })
            assertEquals(inventory, s.bag)
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
        assertEquals(Shop.weekendFor(data, "2026-10-17")!!.id, Shop.weekendFor(data, g.dayKey(at(2026, 10, 19, 2)))!!.id)
        assertFalse(Shop.isWeekend(g.dayKey(at(2026, 10, 19, 4))))
        g.state.settings.dayStartHour = 0
        assertTrue(Shop.isWeekend(g.dayKey(at(2026, 10, 17, 0))))
        assertFalse(Shop.isWeekend(g.dayKey(at(2026, 10, 19, 0))))
    }

    // ---- buy / buyTm accept and reject each source ----

    private fun satWith() = saturdays.first()

    @Test fun theWeekendItemIsBoughtOnTheWeekendOnlyAtItsPrice() {
        val sat = satWith()
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
        // On Monday the reserved item cannot be bought.
        s.day = sat.plusDays(2).toString(); Shop.ensureToday(data, s); s.shopItems.remove(sp.item)
        assertEquals(ActionResult(false, "Not in today's shop"), Shop.buy(data, s, sp.item!!))
    }

    @Test fun tmsStillNeedNormalStockAndCanOnlyBeOwnedOnce() {
        val s = state(saturdays.first().toString()); s.cash = 5000
        val listed = s.shopTms.first()
        assertTrue(Shop.buyTm(data, s, listed).ok)
        assertEquals(4000, s.cash)
        assertFalse(Shop.buyTm(data, s, listed).ok)
        s.shopTms.clear()
        assertFalse(Shop.buyTm(data, s, strongTms.first()).ok)
    }

    @Test fun staleReservedStockCannotBeBoughtOnAWeekday() {
        val s = state("2026-10-14"); s.cash = 100000
        s.shopItems.addAll(Shop.weekendPrices.keys)
        for (item in Shop.weekendPrices.keys) assertFalse(Shop.buy(data, s, item).ok)
        assertEquals(100000, s.cash)
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
        val sat = satWith()
        val sp = Shop.weekendFor(data, sat.toString())!!
        val w = state(sat.toString()); w.cash = 100_000
        Shop.buy(data, w, sp.item!!)
        val dealPaid = d.price + Shop.priceOf(data, s, "potion")
        assertEquals(dealPaid.toLong(), s.counters[Metric.MONEY_SPENT.name])
        assertEquals(dealPaid, s.history[s.day]!!.spent)
        assertEquals(100_000 - dealPaid, s.cash)
        assertEquals(sp.price.toLong(), w.counters[Metric.MONEY_SPENT.name])
        assertEquals(sp.price, w.history[w.day]!!.spent)
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
        assertTrue(Shop.buy(data, back, "potion").ok)
        val tm = back.shopTms.first()
        assertTrue(Shop.buyTm(data, back.also { it.cash = 2000 }, tm).ok)
        assertEquals(1000, back.cash)
    }
}
