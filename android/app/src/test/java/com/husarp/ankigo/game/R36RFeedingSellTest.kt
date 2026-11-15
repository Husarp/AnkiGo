package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.TimeZone
import kotlin.random.Random

class R36RFeedingSellTest {
    private val data = TestData.world()
    private fun state() = TestData.state(data).apply {
        day = "2026-11-15"; bag.clear(); bag["oran-berry"] = 10
        mons += TestData.mon(data, 16, 20, uid = "other")
    }
    @Test fun feedingAddsFiveBondAndThirdTryHasOnlyTheHiddenLimitMessageWithoutConsuming() {
        var s = state(); val uid = s.main!!.uid
        repeat(2) { assertTrue(BerryFeeding.feed(data, s, "oran-berry", uid, Random(100)).ok) }
        assertEquals(10, s.main!!.bondPoints); assertEquals(8, s.count("oran-berry"))
        s = SaveCodec.decode(SaveCodec.encode(s))
        val before = SaveCodec.encode(s)
        val result = BerryFeeding.feed(data, s, "oran-berry", uid, Random(0))
        assertFalse(result.ok); assertEquals("${s.main!!.shownName} isn't hungry right now.", result.message)
        assertEquals(before, SaveCodec.encode(s))
        assertTrue(BerryFeeding.feed(data, s, "oran-berry", "other", Random(100)).ok)
        s.day = "2026-11-16"
        assertTrue(BerryFeeding.feed(data, s, "oran-berry", uid, Random(100)).ok)
        assertEquals(15, s.main!!.bondPoints)
    }
    @Test fun feedEntryPointFollowsTheConfiguredGameDayCutoff() {
        val saved = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val s = state(); s.settings.dayStartHour = 4
            var now = Instant.parse("2026-11-16T03:59:00Z").toEpochMilli()
            val game = Game(data, s, Random(100)) { now }; val uid = s.main!!.uid
            assertTrue(game.feedBerry("oran-berry", uid).ok)
            assertTrue(game.feedBerry("oran-berry", uid).ok)
            assertFalse(game.feedBerry("oran-berry", uid).ok)
            now = Instant.parse("2026-11-16T04:00:00Z").toEpochMilli()
            assertTrue(game.feedBerry("oran-berry", uid).ok)
        } finally { TimeZone.setDefault(saved) }
    }
    @Test fun everyBerryCanBeFedWithoutTakingHeldCopiesAndInvalidInputsAreAtomic() {
        for (item in AbilityAdaptations.berries.keys) {
            val s = state(); s.bag[item] = 1; s.main!!.heldItem = item
            assertTrue(BerryFeeding.feed(data, s, item, s.main!!.uid, Random(100)).ok)
            assertEquals(item, s.main!!.heldItem)
        }
        val s = state(); val before = SaveCodec.encode(s)
        assertFalse(BerryFeeding.feed(data, s, "potion", s.main!!.uid, Random(1)).ok)
        assertFalse(BerryFeeding.feed(data, s, "oran-berry", "missing", Random(1)).ok)
        assertEquals(before, SaveCodec.encode(s))
    }
    @Test fun giftsOccurAtFifteenPercentWithXpCoinAndSingleEvOutcomes() {
        val kinds = mutableSetOf<Int>(); var gifts = 0
        repeat(1000) { seed ->
            val expected = Random(seed); val gift = expected.nextInt(100) < 15
            val s = state(); val m = s.main!!; val xp = m.xp; val coins = s.cash; val evs = m.evs.total
            val result = BerryFeeding.feed(data, s, "oran-berry", m.uid, Random(seed))
            assertEquals(gift, result.message.contains("A gift:"))
            if (gift) {
                gifts++; val kind = expected.nextInt(3); kinds += kind
                when (kind) {
                    0 -> assertEquals(xp + 20, m.xp)
                    1 -> assertEquals(coins + 5, s.cash)
                    2 -> assertEquals(evs + 1, m.evs.total)
                }
            } else { assertEquals(xp, m.xp); assertEquals(coins, s.cash); assertEquals(evs, m.evs.total) }
        }
        assertEquals(setOf(0, 1, 2), kinds); assertTrue(gifts in 110..190)
    }
    @Test fun bondAndEvCapsAreRespected() {
        val seed = (0..1000).first { Random(it).let { r -> r.nextInt(100) < 15 && r.nextInt(3) == 2 } }
        val s = state(); val m = s.main!!; m.bondPoints = Bond.MAX_POINTS - 1
        m.evs = Stats(252, 252, 6, 0, 0, 0); val before = m.evs; val coins = s.cash
        BerryFeeding.feed(data, s, "oran-berry", m.uid, Random(seed))
        assertEquals(Bond.MAX_POINTS, m.bondPoints); assertEquals(before, m.evs); assertEquals(coins + 5, s.cash)
    }
    @Test fun berriesAreRemovedFromNewAndExistingShopOffersAndCannotBeBoughtDirectly() {
        val s = state(); s.shopDay = s.day; s.shopItems += AbilityAdaptations.berries.keys; s.cash = 100000
        Shop.ensureToday(data, s)
        assertTrue(s.shopItems.none { it in AbilityAdaptations.berries })
        for (berry in AbilityAdaptations.berries.keys) {
            assertFalse(berry in Items.shopPool(data)); assertTrue(berry in Items.dropPool())
            s.shopItems += berry
            assertFalse(Shop.buy(data, s, berry).ok)
        }
        repeat(100) { n ->
            val day = "day$n"
            assertTrue(Shop.stockFor(data, day, 30).first.none { it in AbilityAdaptations.berries })
            assertFalse(Shop.dealFor(data, day)?.item in AbilityAdaptations.berries)
        }
    }
    @Test fun sellUsesTenPercentOfNormalPriceNotTodaysOfferWithQuantitiesAndSaving() {
        val s = state(); s.bag["potion"] = 10; val coins = s.cash
        assertEquals(10, BagSell.price(data, "potion"))
        assertTrue(BagSell.sell(data, s, "potion", 3).ok)
        assertEquals(7, s.count("potion")); assertEquals(coins + 30, s.cash)
        assertTrue(BagSell.sell(data, s, "potion", 5).ok)
        val restored = SaveCodec.decode(SaveCodec.encode(s))
        assertEquals(2, restored.count("potion")); assertEquals(coins + 80, restored.cash)
        assertEquals(1, BagSell.price(data, "black-augurite"))
        restored.bag["black-augurite"] = 1
        assertTrue(BagSell.sell(data, restored, "black-augurite", 1).ok)
        assertEquals(coins + 81, restored.cash)
    }
    @Test fun sellRejectsPermanentItemsTmsHeldCopiesStaleQuantitiesAndOverflowAtomically() {
        val s = state(); s.bag["potion"] = 2; s.bag[PokeDoll.ITEM] = 1; s.bag[StreakFreeze.ITEM] = 1
        s.main!!.heldItem = "leftovers"; s.tms += "tackle"
        val before = SaveCodec.encode(s)
        for ((item, n) in listOf(PokeDoll.ITEM to 1, StreakFreeze.ITEM to 1, "tackle" to 1, "leftovers" to 1, "potion" to 3, "potion" to -1, "potion" to 2))
            assertFalse(BagSell.sell(data, s, item, n).ok)
        assertEquals(before, SaveCodec.encode(s))
        s.cash = Int.MAX_VALUE
        assertFalse(BagSell.sell(data, s, "potion", 1).ok); assertEquals(2, s.count("potion"))
    }
}
