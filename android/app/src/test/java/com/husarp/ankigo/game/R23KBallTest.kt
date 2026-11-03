package com.husarp.ankigo.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R23KBallTest {
    private val data = TestData.world()
    private fun time(hour: Int) = java.util.Calendar.getInstance().apply { set(2026, 9, 30, hour, 0, 0) }.timeInMillis

    @Test fun conditionsAndCaps() {
        val s = TestData.state(data)
        val b = Battle(TestData.mon(data, 54, 10), Tier.NORMAL)
        fun chance(special: String, hour: Int = 12) = Catching.ballChance("great-ball", special, data, s, b, time(hour))
        for (id in listOf("net-ball", "dive-ball", "nest-ball", "quick-ball")) assertEquals(id, 70, chance(id))
        assertEquals(50, chance("dusk-ball")); assertEquals(70, chance("dusk-ball", 22))
        assertEquals(50, chance("repeat-ball")); s.caught += 54; assertEquals(50, chance("repeat-ball"))
        assertEquals(50, chance("premier-ball")); b.wild.shiny = true; assertEquals(70, chance("premier-ball")); b.wild.shiny = false
        for (tier in listOf(Tier.LEGENDARY, Tier.MYTHICAL, Tier.ULTRA)) { b.tier = tier; assertEquals(70, chance("premier-ball")) }
        b.tier = Tier.NORMAL
        assertEquals(50, chance("timer-ball")); b.rounds = 2; assertEquals(60, chance("timer-ball")); b.rounds = 100; assertEquals(70, chance("timer-ball"))
        assertEquals(50, chance("quick-ball")); b.wild.level = 21; assertEquals(50, chance("nest-ball"))
        val normal = Battle(TestData.mon(data, 19, 30), Tier.NORMAL)
        for (id in listOf("net-ball", "dive-ball")) assertEquals(50, Catching.ballChance("great-ball", id, data, s, normal, time(12)))
        assertEquals(100, Catching.ballChance("ultra-ball", "dive-ball", data, s, b, time(12)))
        assertEquals(100, Catching.ballChance("master-ball", "dive-ball", data, s, b, time(12)))
        assertEquals("Ultra Heal Ball", Catching.throwName("ultra-ball", "heal-ball"))
    }

    @Test fun ivBonusesAndConsumptionOnCatch() {
        for (special in listOf("heal-ball", "luxury-ball")) {
            val s = TestData.state(data)
            val wild = TestData.mon(data, 54, 10).apply { evs = Stats(80, 40, 0, 100, 20, 0) }
            s.battle = Battle(wild, Tier.NORMAL)
            s.bag["master-ball"] = 1; s.bag[special] = 1
            val ev = Game(data, s, Random(1)).throwBall("master-ball", special)
            assertTrue(ev.any { it is GameEvent.Caught })
            assertEquals(0, s.count("master-ball")); assertEquals(0, s.count(special))
            assertEquals(Catching.specialIvs(wild.ivs, special), s.mons.last().ivs)
            assertEquals("Special IV bonuses must preserve the wild EVs", wild.evs, s.mons.last().evs)
            assertEquals(0, s.mons.last().friendship)
        }
        val iv = Stats(30, 30, 30, 30, 30, 30)
        assertEquals(iv.copy(hp = 31), Catching.specialIvs(iv, "heal-ball"))
        assertEquals(Stats(31,31,31,31,31,31), Catching.specialIvs(iv, "luxury-ball"))
    }

    @Test fun missConsumesBothAndInvalidPairConsumesNeither() {
        val s = TestData.state(data)
        s.bag["poke-ball"] = 2; s.bag["heal-ball"] = 1
        s.battle = Battle(TestData.mon(data, 54, 10), Tier.NORMAL)
        assertTrue(Game(data, s, Random(1)).throwBall("poke-ball", "missing").isEmpty())
        assertEquals(2, s.count("poke-ball"))
        val rng = Random(1)
        val ev = Game(data, s, rng).throwBall("poke-ball", "heal-ball")
        assertFalse(ev.filterIsInstance<GameEvent.BallThrow>().single().success)
        assertEquals(1, s.count("poke-ball")); assertEquals(0, s.count("heal-ball"))
    }

    @Test fun tradeAndSpecialPrices() {
        val s = TestData.state(data); s.bag.clear()
        s.bag["poke-ball"] = 10; s.bag["great-ball"] = 5; s.bag["net-ball"] = 100
        assertFalse(BallTrade.trade(s, "great-ball").ok)
        assertTrue(BallTrade.trade(s, "poke-ball").ok)
        assertTrue(BallTrade.trade(s, "great-ball").ok)
        assertEquals(1, s.count("ultra-ball")); assertEquals(100, s.count("net-ball"))
        for (id in Tuning.OLD_BALLS) { assertNull(BallTrade.offer(s,id)); assertEquals(Tuning.SHOP_PRICE.getValue(id), data.price(id)); assertEquals(0, Catching.ballChance(id)) }
    }

    @Test fun seededShopDistribution() {
        val counts = mutableMapOf<String, Int>()
        val n = 100000
        repeat(n) { day ->
            val balls = Shop.dailyBalls("day-$day")
            assertEquals(balls, Shop.dailyBalls("day-$day"))
            assertTrue(balls.size in 1..2)
            assertTrue(balls.first() in Tuning.BALL_CHANCE && balls.first() != "master-ball")
            balls.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        for ((id,w) in Tuning.REGULAR_SHOP_WEIGHTS) assertEquals(id, w / 100.0, counts.getValue(id).toDouble()/n, 0.01)
        for ((id,w) in Tuning.SPECIAL_SHOP_WEIGHTS) assertEquals(id, w.toDouble() / Tuning.SPECIAL_SHOP_WEIGHTS.values.sum() * 0.5, counts.getValue(id).toDouble()/n, 0.005)
    }
    @Test fun netAlsoWorksOnBugAndNoSpecialPreservesIvs() {
        val bugs = TestData.data(listOf(TestData.species(19, types = listOf(Type.BUG))))
        val state = TestData.state(bugs, species = 19)
        val battle = Battle(TestData.mon(bugs, 19, 30), Tier.NORMAL)
        assertEquals(70, Catching.ballChance("great-ball", "net-ball", bugs, state, battle, time(12)))
        assertEquals(50, Catching.ballChance("great-ball", "dive-ball", bugs, state, battle, time(12)))
        assertEquals(0, Catching.ballChance("net-ball", "dive-ball", bugs, state, battle, time(12)))
        val ivs = Stats(1,2,3,4,5,6)
        assertEquals(ivs, Catching.specialIvs(ivs, null))
        assertEquals(ivs.copy(hp = 11), Catching.specialIvs(ivs, "heal-ball"))
        assertEquals(Stats(6,7,8,9,10,11), Catching.specialIvs(ivs, "luxury-ball"))
    }

    @Test fun oldSaveInventorySurvivesAndRerollsKeepDailyBalls() {
        val state = TestData.state(data)
        Tuning.OLD_BALLS.forEachIndexed { i, id -> state.bag[id] = i + 1 }
        val saved = com.husarp.ankigo.game.save.SaveCodec.decode(com.husarp.ankigo.game.save.SaveCodec.encode(state))
        assertEquals(state.bag, saved.bag)
        saved.day = "2026-10-30"
        Shop.ensureToday(data, saved)
        val balls = Shop.dailyBalls(saved.day)
        assertEquals(balls, saved.shopItems.filter { Items.def(it)?.kind == ItemKind.BALL })
        repeat(20) {
            saved.cash = 10000
            assertTrue(Shop.reroll(data, saved, Random(it)).ok)
            assertEquals(balls, saved.shopItems.filter { Items.def(it)?.kind == ItemKind.BALL })
            assertFalse(Shop.dealFor(data, saved.day)?.item in Tuning.BALL_CHANCE)
            assertFalse(Shop.dealFor(data, saved.day)?.item in Tuning.OLD_BALLS)
        }
    }

    @Test fun repeatReturnRatesAndRegularAlwaysSpent() {
        var catches = 0; var misses = 0; var catchReturns = 0; var missReturns = 0
        repeat(4000) { seed ->
            val s = TestData.state(data)
            s.battle = Battle(TestData.mon(data, 54, 10), Tier.NORMAL)
            s.bag["great-ball"] = 1; s.bag["repeat-ball"] = 1
            val ev = Game(data, s, Random(seed)).throwBall("great-ball", "repeat-ball")
            val caught = ev.filterIsInstance<GameEvent.BallThrow>().single().success
            assertEquals(0, s.count("great-ball"))
            if (caught) { catches++; catchReturns += s.count("repeat-ball") }
            else { misses++; missReturns += s.count("repeat-ball") }
        }
        assertEquals(0.25, catchReturns.toDouble() / catches, 0.04)
        assertEquals(0.50, missReturns.toDouble() / misses, 0.04)
    }

    @Test fun dailyPricesStableRoundedAndDealUsesDailyPrice() {
        val s = TestData.state(data); s.day = "2026-10-30"
        val before = Items.ALL.keys.associateWith { Shop.priceOf(data, s, it) }
        val reopened = com.husarp.ankigo.game.save.SaveCodec.decode(com.husarp.ankigo.game.save.SaveCodec.encode(s))
        assertEquals(before, Items.ALL.keys.associateWith { Shop.priceOf(data, reopened, it) })
        repeat(5) { s.cash = 10000; Shop.reroll(data, s, Random(it)) }
        assertEquals(before, Items.ALL.keys.associateWith { Shop.priceOf(data, s, it) })
        val seen = mutableSetOf<Int>()
        repeat(100) { day ->
            for (id in Tuning.SHOP_PRICE.keys) {
                val normal = data.price(id); val price = Shop.dailyPrice(data, "day-$day", id)
                val step = if (price < 1000) 10 else 50
                assertEquals(0, price % step)
                assertTrue(price >= normal * 0.9 - step / 2.0 && price <= normal * 1.1 + step / 2.0)
            }
            seen += Shop.dailyPrice(data, "day-$day", "potion")
            val deal = Shop.dealFor(data, "day-$day")!!
            assertEquals(Shop.dailyPrice(data, "day-$day", deal.item), deal.oldPrice)
            assertEquals(Shop.dealPrice(deal.oldPrice, deal.percent), Shop.priceOf(data, "day-$day", deal.item))
        }
        assertTrue(seen.size > 1)
        assertEquals(1000, Tuning.TM_PRICE); assertEquals(100, Tuning.REROLL_PRICE)
        assertEquals(10, Tuning.CASH_PER_ANSWER); assertEquals(25, Tuning.CASH_PER_WIN)
    }

    @Test fun proposalIdsAreCatalogueItemsAndOverrideApiCosts() {
        assertTrue(Tuning.SHOP_PRICE.keys.all { it in Items.ALL })
        val noApiPrices = GameData(data.species, data.moves, data.growth, mapOf("custom-item" to 1234))
        for ((id, price) in Tuning.SHOP_PRICE) assertEquals(id, price, noApiPrices.price(id))
        assertEquals(1234, noApiPrices.price("custom-item"))
        assertEquals(0, noApiPrices.price("master-ball"))
        assertTrue(Items.specialNote("repeat-ball").contains("no catch bonus"))
        assertTrue(ItemInfo.howToUse("repeat-ball").contains("50%"))
    }

}
