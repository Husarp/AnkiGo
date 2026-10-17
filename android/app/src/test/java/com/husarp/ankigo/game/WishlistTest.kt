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
}
