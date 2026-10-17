package com.husarp.ankigo.deck

import org.junit.Assert.*
import org.junit.Test

/** The generation guard of a move / delete started from the study screen. */
class CardChangeTest {
    private class Screen {
        var generation = 1
        var locked = false
        val calls = mutableListOf<String>()
        fun change() = CardChange({ generation }, { locked = true; calls += "lock" }, { locked = false; calls += "unlock" }, { locked = false; calls += "next" })
    }

    @Test fun locksAtOnceThenUnlocksOnCancel() {
        val s = Screen()
        val c = s.change(); c.begin()
        assertTrue(s.locked)
        c.cancelled()
        assertFalse(s.locked)
        assertEquals(listOf("lock", "unlock"), s.calls)
    }

    @Test fun finishedLoadsTheNextCardWhenTheCardIsGoneElseUnlocks() {
        val a = Screen(); a.change().also { it.begin(); it.finished(true) }
        assertEquals(listOf("lock", "next"), a.calls)
        val b = Screen(); b.change().also { it.begin(); it.finished(false) }
        assertEquals(listOf("lock", "unlock"), b.calls)
    }

    @Test fun aNewerCardIsNeverTouchedByAnOldFlow() {
        val s = Screen()
        val c = s.change(); c.begin()
        s.generation++            // another card was loaded meanwhile
        s.locked = true           // ... and it is locked for its own reasons
        c.finished(true)
        c.cancelled()
        assertTrue(s.locked)
        assertEquals(listOf("lock"), s.calls)
    }

    @Test fun onlyTheFirstEndCounts() {
        val s = Screen()
        val c = s.change(); c.begin()
        c.cancelled(); c.finished(true); c.cancelled()
        assertEquals(listOf("lock", "unlock"), s.calls)
    }

    @Test fun eachChangeKeepsItsOwnCard() {
        val s = Screen()
        val first = s.change(); first.begin()
        s.generation++
        val second = s.change(); second.begin()
        first.cancelled()                                 // stale: ignored
        assertTrue(s.locked)
        second.cancelled()
        assertFalse(s.locked)
        assertEquals(listOf("lock", "lock", "unlock"), s.calls)
    }
}
