package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PickSetTest {
    private fun owned(n: Int) = ComparePick.Owned("m$n")

    @Test fun tickAndUntick() {
        val s = PickSet()
        assertEquals(PickSet.Result.ADDED, s.toggle(owned(1)))
        assertTrue(s.has(owned(1)))
        assertEquals(PickSet.Result.REMOVED, s.toggle(owned(1)))
        assertEquals(0, s.size)
    }

    @Test fun noLimitByDefault() {
        val s = PickSet()
        (1..12).forEach { assertEquals(PickSet.Result.ADDED, s.toggle(owned(it))) }
        assertEquals(12, s.size)
    }

    @Test fun atMostFour() {
        val s = PickSet(max = 4)
        (1..4).forEach { assertEquals(PickSet.Result.ADDED, s.toggle(owned(it))) }
        assertEquals(PickSet.Result.FULL, s.toggle(owned(5)))
        assertEquals(4, s.size)
        assertFalse(s.has(owned(5)))
        // unticking one makes room again
        s.toggle(owned(2))
        assertEquals(PickSet.Result.ADDED, s.toggle(owned(5)))
    }

    @Test fun ownedAndDexAreDifferentKeys() {
        val s = PickSet()
        s.toggle(ComparePick.Owned("25"))
        s.toggle(ComparePick.Dex(25))
        assertEquals(2, s.size)
        assertTrue(s.has(ComparePick.Dex(25)))
        s.toggle(ComparePick.Dex(25))
        assertTrue(s.has(ComparePick.Owned("25")))
        assertFalse(s.has(ComparePick.Dex(25)))
    }

    @Test fun compareNeedsTwo() {
        val s = PickSet()
        assertFalse(s.canCompare)
        s.toggle(owned(1))
        assertFalse(s.canCompare)
        s.toggle(ComparePick.Dex(7))
        assertTrue(s.canCompare)
    }

    @Test fun keepsTickOrder() {
        val s = PickSet()
        s.toggle(ComparePick.Dex(9)); s.toggle(owned(1)); s.toggle(ComparePick.Dex(3))
        assertEquals(listOf(ComparePick.Dex(9), owned(1), ComparePick.Dex(3)), s.picks)
    }

    @Test fun keepOwnedDropsReleased() {
        val s = PickSet()
        s.toggle(owned(1)); s.toggle(owned(2)); s.toggle(ComparePick.Dex(4))
        s.keepOwned { it == "m2" }
        assertEquals(listOf(owned(2), ComparePick.Dex(4)), s.picks)
    }

    @Test fun clearEmpties() {
        val s = PickSet()
        s.toggle(owned(1)); s.clear()
        assertEquals(0, s.size)
    }

    @Test fun sectionOfName() {
        assertEquals(TeamSection.BOX, TeamSection.of("BOX"))
        assertEquals(TeamSection.TEAM, TeamSection.of(null))
        assertEquals(TeamSection.TEAM, TeamSection.of("nonsense"))
    }
}
