package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R6-14: only a seen, not caught species can be added by hand; removing is always allowed. */
class WishlistTest {
    private fun st(): GameState {
        val s = GameState(createdAt = 1L, day = "2026-10-13", starterChosen = true)
        s.settings.wishlist.clear()
        s.seen += listOf(10, 11); s.caught += 11
        return s
    }

    @Test fun seenNotCaughtCanBeAdded() {
        val s = st()
        assertTrue(Wishlist.canAdd(s, 10))
        assertTrue(Wishlist.toggle(s, 10))
        assertTrue(10 in s.settings.wishlist)
    }

    @Test fun unseenAndCaughtCannot() {
        val s = st()
        assertFalse(Wishlist.canAdd(s, 99)); assertFalse(Wishlist.toggle(s, 99))
        assertFalse(Wishlist.canAdd(s, 11)); assertFalse(Wishlist.toggle(s, 11))
        assertTrue(s.settings.wishlist.isEmpty())
        assertEquals("unseen", Wishlist.blocker(s, 99))
        assertEquals("owned", Wishlist.blocker(s, 11))
        assertNull(Wishlist.blocker(s, 10))
    }

    @Test fun removeAlwaysAllowed() {
        val s = st()
        s.settings.wishlist += listOf(99, 11)   // old entries stay, and can go
        assertNull(Wishlist.blocker(s, 99))
        assertTrue(Wishlist.toggle(s, 99)); assertTrue(Wishlist.toggle(s, 11))
        assertTrue(s.settings.wishlist.isEmpty())
    }

    // ---- C9: a species that becomes caught leaves the list ----

    private val data = TestData.world()
    private fun wished(vararg nums: Int): GameState {
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        s.settings.wishlist.clear(); s.settings.wishlist += nums.toList()
        return s
    }

    @Test fun anAutoCatchRemovesTheSpeciesAndKeepsOthers() {
        val s = wished(54, 92)
        val g = Game(data, s, kotlin.random.Random(1)) { 1L }
        s.battle = Battle(TestData.mon(data, 54, 15, uid = "wild").also { it.hp = 1 }, Tier.NORMAL, startedAt = 1L)
        assertTrue(g.willCatch()) // wishlisted: the plan is a catch
        g.onAnswer(Answer(1000L, 3, 1L, false))
        val ev = g.onAnswer(Answer(1001L, 3, 1L, false))
        assertTrue(ev.any { it is GameEvent.Caught && it.species == 54 })
        assertEquals(setOf(92), s.settings.wishlist)
    }

    @Test fun aBallCatchRemovesTheSpecies() {
        val s = wished(54)
        val g = Game(data, s, kotlin.random.Random(1)) { 1L }
        Economy.give(s, "master-ball", 1)
        s.battle = Battle(TestData.mon(data, 54, 15, uid = "wild"), Tier.NORMAL, startedAt = 1L)
        g.throwBall("master-ball")
        assertTrue(54 in s.caught)
        assertTrue(s.settings.wishlist.isEmpty())
    }

    @Test fun aFossilReviveRemovesTheSpecies() {
        val d = GameData(data.species + (138 to TestData.species(138, "Spiralmon")), data.moves, data.growth, data.itemPrices)
        val s = TestData.state(d, 25, 20, listOf("tackle"))
        s.settings.wishlist.clear(); s.settings.wishlist += listOf(138, 92)
        Economy.give(s, "helix-fossil")
        assertTrue(ItemUse.use(d, s, "helix-fossil", null, TestData.rng(), 1234L).ok)
        assertEquals(setOf(92), s.settings.wishlist)
    }

    @Test fun anEvolutionIntoAWishlistedSpeciesRemovesIt() {
        val s = wished(2, 92)
        val m = TestData.mon(data, 1, 16, listOf("tackle"), uid = "evo").also { s.mons += it; s.caught += 1 }
        Progression.evolve(data, s, m, data.sp(1).evolutions[0], kotlin.random.Random(1), mutableListOf())
        assertTrue(2 in s.caught)
        assertEquals(setOf(92), s.settings.wishlist)
    }

    @Test fun theStarterLeavesTheList() {
        val s = GameState(createdAt = 1L)
        s.settings.wishlist.clear(); s.settings.wishlist += listOf(1, 92)
        Game(data, s, kotlin.random.Random(1)) { 1L }.pickStarter(1)
        assertEquals(setOf(92), s.settings.wishlist)
    }

    @Test fun pruneRemovesOnlyCaughtOnesAndSurvivesASaveRoundTrip() {
        val s = wished(25, 92, 99)   // 25 is the main's species = caught; 92 and 99 are not
        assertTrue(Wishlist.prune(s))
        assertEquals(setOf(92, 99), s.settings.wishlist)
        assertFalse(Wishlist.prune(s))   // nothing left to prune
        val back = com.husarp.ankigo.game.save.SaveCodec.decode(com.husarp.ankigo.game.save.SaveCodec.encode(s))
        assertEquals(setOf(92, 99), back.settings.wishlist)
    }
}
