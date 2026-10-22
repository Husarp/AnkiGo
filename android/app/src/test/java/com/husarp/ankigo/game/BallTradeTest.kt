package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PLAN 199 (R14 B2-B): trading balls up in the Bag: 5 Poké -> 1 Great, 3 Great -> 1 Ultra (old Ankimon balls count as Great and go first). */
class BallTradeTest {
    private fun state(vararg bag: Pair<String, Int>) = TestData.state(TestData.world()).also { it.bag.clear(); it.bag.putAll(bag) }

    private fun noNegative(s: GameState) = assertTrue(s.bag.toString(), s.bag.values.all { it > 0 })

    @Test fun fivePokeBallsMakeOneGreatBall() {
        val s = state("poke-ball" to 7)
        val r = BallTrade.trade(s, "poke-ball")
        assertTrue(r.message, r.ok)
        assertEquals("Traded 5 Poké Balls for 1 Great Ball", r.message)
        assertEquals(2, s.count("poke-ball"))
        assertEquals(1, s.count("great-ball"))
        noNegative(s)
    }

    @Test fun threeGreatBallsMakeOneUltraBall() {
        val s = state("great-ball" to 3)
        val r = BallTrade.trade(s, "great-ball")
        assertTrue(r.message, r.ok)
        assertEquals("Traded 3 Great Balls for 1 Ultra Ball", r.message)
        assertEquals("a count that reaches zero leaves the bag", false, "great-ball" in s.bag)
        assertEquals(1, s.count("ultra-ball"))
        noNegative(s)
    }

    @Test fun tooFewChangesNothing() {
        val s = state("poke-ball" to 4, "great-ball" to 2)
        val before = s.bag.toMap()
        val p = BallTrade.trade(s, "poke-ball")
        assertFalse(p.ok)
        assertEquals("Need 5, you have 4", p.message)
        val g = BallTrade.trade(s, "great-ball")
        assertFalse(g.ok)
        assertEquals("Need 3, you have 2", g.message)
        assertEquals(before, s.bag.toMap())
        assertEquals(0, s.count("ultra-ball"))
    }

    @Test fun anEmptyBagDoesNotGoUnderZero() {
        val s = state()
        assertFalse(BallTrade.trade(s, "poke-ball").ok)
        assertFalse(BallTrade.trade(s, "great-ball").ok)
        assertTrue(s.bag.isEmpty())
    }

    @Test fun ultraMasterAndNonBallsHaveNoOffer() {
        val s = state("ultra-ball" to 9, "master-ball" to 1, "potion" to 9)
        for (id in listOf("ultra-ball", "master-ball", "potion", "thunder-stone", "nonsense")) {
            assertNull(id, BallTrade.offer(s, id))
            assertFalse(id, BallTrade.trade(s, id).ok)
        }
        assertEquals(mapOf("ultra-ball" to 9, "master-ball" to 1, "potion" to 9), s.bag.toMap())
    }

    @Test fun theOfferSaysWhatGoesAndWhatYouGet() {
        val s = state("poke-ball" to 7)
        val o = BallTrade.offer(s, "poke-ball")!!
        assertEquals("great-ball", o.give)
        assertEquals(5, o.need)
        assertEquals(7, o.have)
        assertEquals(listOf("poke-ball" to 5), o.take)
        assertTrue(o.canTrade)
        assertEquals(2, BallTrade.keep(s, o))
        assertEquals("the offer itself takes nothing", 7, s.count("poke-ball"))
    }

    @Test fun anOfferWithTooFewKeepsTheCountsAndTakesNothing() {
        val s = state("poke-ball" to 3)
        val o = BallTrade.offer(s, "poke-ball")!!
        assertFalse(o.canTrade)
        assertEquals(3, o.have)
        assertEquals(5, o.need)
        assertTrue(o.take.isEmpty())
    }

    @Test fun oldBallsCountAsGreatBallsAndGoFirst() {
        val s = state("great-ball" to 4, "net-ball" to 1)
        val o = BallTrade.offer(s, "great-ball")!!
        assertEquals(5, o.have)
        assertEquals(listOf("net-ball" to 1, "great-ball" to 2), o.take)
        assertTrue(o.usesOldBalls)
        val r = BallTrade.trade(s, "great-ball")
        assertEquals("Traded 1 Net Ball and 2 Great Balls for 1 Ultra Ball", r.message)
        assertEquals(0, s.count("net-ball"))
        assertEquals(2, s.count("great-ball"))
        assertEquals(1, s.count("ultra-ball"))
        noNegative(s)
    }

    @Test fun anOldBallsOwnCardTradesToo() {
        val s = state("dive-ball" to 3)
        val o = BallTrade.offer(s, "dive-ball")!!
        assertEquals("ultra-ball", o.give)
        assertEquals(listOf("dive-ball" to 3), o.take)
        assertTrue(BallTrade.trade(s, "dive-ball").ok)
        assertEquals(setOf("ultra-ball"), s.bag.keys)
    }

    @Test fun oldBallsAloneCanBeEnoughAndAreUsedFewestFirstThenByName() {
        val s = state("timer-ball" to 2, "dusk-ball" to 1, "luxury-ball" to 1, "great-ball" to 1)
        val o = BallTrade.offer(s, "great-ball")!!
        // the one you have fewest of first, ties by name: dusk (1), luxury (1), then timer (2): 1 + 1 + 1 = 3
        assertEquals(listOf("dusk-ball" to 1, "luxury-ball" to 1, "timer-ball" to 1), o.take)
        val r = BallTrade.trade(s, "great-ball")
        assertEquals("Traded 1 Dusk Ball, 1 Luxury Ball and 1 Timer Ball for 1 Ultra Ball", r.message)
        assertEquals(1, s.count("timer-ball"))
        assertEquals(1, s.count("great-ball"))
        noNegative(s)
    }

    @Test fun oldBallsDoNotHelpThePokeBallTrade() {
        val s = state("poke-ball" to 4, "net-ball" to 9)
        assertFalse(BallTrade.offer(s, "poke-ball")!!.canTrade)
        assertFalse(BallTrade.trade(s, "poke-ball").ok)
    }

    @Test fun greatBallsStillCountWhenOnlySomeAreOldAndTheTotalIsEnough() {
        val s = state("great-ball" to 1, "net-ball" to 1, "premier-ball" to 1)
        val o = BallTrade.offer(s, "net-ball")!!
        assertEquals(3, o.have)
        assertEquals(listOf("net-ball" to 1, "premier-ball" to 1, "great-ball" to 1), o.take)
        assertTrue(BallTrade.trade(s, "net-ball").ok)
        assertTrue(s.bag.keys.toString(), s.bag.keys == setOf("ultra-ball"))
    }

    @Test fun repeatedTapsAreOneBatchEach() {
        val s = state("poke-ball" to 12)
        assertTrue(BallTrade.trade(s, "poke-ball").ok)
        assertEquals(7, s.count("poke-ball"))
        assertEquals(1, s.count("great-ball"))
        assertTrue(BallTrade.trade(s, "poke-ball").ok)
        assertEquals(2, s.count("poke-ball"))
        assertEquals(2, s.count("great-ball"))
        assertFalse("12 balls make two batches, not three", BallTrade.trade(s, "poke-ball").ok)
        assertEquals(2, s.count("poke-ball"))
        assertEquals(2, s.count("great-ball"))
        noNegative(s)
    }

    @Test fun aChainFromPokeToUltra() {
        val s = state("poke-ball" to 15)
        repeat(3) { assertTrue(BallTrade.trade(s, "poke-ball").ok) }
        assertEquals(3, s.count("great-ball"))
        assertTrue(BallTrade.trade(s, "great-ball").ok)
        assertEquals(setOf("ultra-ball"), s.bag.keys)
        assertNotNull(BallTrade.offer(s, "poke-ball"))
    }

    @Test fun theListIsPlainEnglishWithNaturalPlurals() {
        assertEquals("1 Great Ball", BallTrade.list(listOf("great-ball" to 1)))
        assertEquals("5 Poké Balls", BallTrade.list(listOf("poke-ball" to 5)))
        assertEquals("1 Net Ball and 2 Great Balls", BallTrade.list(listOf("net-ball" to 1, "great-ball" to 2)))
        assertEquals("1 Dive Ball, 1 Net Ball and 1 Great Ball", BallTrade.list(listOf("dive-ball" to 1, "net-ball" to 1, "great-ball" to 1)))
    }

    @Test fun theShopWorthIsOnlyThereWhenNoOldBallIsIn() {
        assertEquals(1000, BallTrade.worth(listOf("poke-ball" to 5)))
        assertEquals(2100, BallTrade.worth(listOf("great-ball" to 3)))
        assertEquals(700, BallTrade.worth(listOf("great-ball" to 1)))
        assertNull(BallTrade.worth(listOf("net-ball" to 1, "great-ball" to 2)))
    }

    @Test fun tradingIsWorseThanBuying() {
        // on purpose: what goes costs more in the Shop than what comes
        assertTrue(BallTrade.POKE_FOR_GREAT * Tuning.BALL_PRICE.getValue("poke-ball") > Tuning.BALL_PRICE.getValue("great-ball"))
        assertTrue(BallTrade.GREAT_FOR_ULTRA * Tuning.BALL_PRICE.getValue("great-ball") > Tuning.BALL_PRICE.getValue("ultra-ball"))
    }
}
