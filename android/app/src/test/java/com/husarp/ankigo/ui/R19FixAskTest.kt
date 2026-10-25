package com.husarp.ankigo.ui

import com.husarp.ankigo.game.AskMe
import com.husarp.ankigo.game.CatchChoice
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.PendingAsk
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R19-FIX (FIX-1): the release window never opens for a pair that changed since the question was asked (CX-1, [ReplacePlan.of]), and the
 * process-wide "one question at a time" slot is freed when its screen stops or goes to picture-in-picture (CX-4, [AskSlot]).
 */
class R19FixAskTest {
    private val data = TestData.world()

    private fun mon(species: Int, level: Int, uid: String, iv: Int = 90): Mon {
        val b = TestData.mon(data, species, level, listOf("tackle"), uid)
        val a = IntArray(6); var left = iv
        for (i in a.indices) { a[i] = minOf(31, left); left -= a[i] }
        return Mon(uid = uid, species = species, name = b.name, level = level, xp = 0, ivs = Stats(a[0], a[1], a[2], a[3], a[4], a[5]), evs = Stats.ZERO,
            nature = Nature.HARDY, gender = 'M', shiny = false, ability = "Testability", moves = mutableListOf("tackle"), hp = 1).also { it.hp = StatCalc.maxHp(data, it) }
    }

    private fun state() = TestData.state(data, 25, 20).also {
        it.mons += mon(16, 18, "old", iv = 100); it.mons += mon(16, 34, "new", iv = 120); it.caught += 16
        CatchChoice.apply(it.settings, CatchChoice.ASK_ME)
    }

    private val ask = PendingAsk(PendingAsk.Kind.REPLACE, "new", oldUid = "old")

    // ---- CX-1: the window's plan

    @Test fun thePairThatWasAskedStillOpensTheWindow() {
        assertNotNull(ReplacePlan.of(data, state(), ask))
    }

    @Test fun anEvolvedOrWorsePairHasNoWindow() {
        val evolvedOld = state().also { it.mon("old")!!.species = 17 }
        assertNull(ReplacePlan.of(data, evolvedOld, ask))
        val evolvedNew = state().also { it.mon("new")!!.species = 17 }
        assertNull(ReplacePlan.of(data, evolvedNew, ask))
        // the old copy is not worse by level or IV total any more
        val stronger = state().also { s -> s.mons[s.mons.indexOf(s.mon("old")!!)] = mon(16, 40, "old", iv = 130) }
        assertNull(ReplacePlan.of(data, stronger, ask))
        val equal = state().also { s -> s.mons[s.mons.indexOf(s.mon("old")!!)] = mon(16, 34, "old", iv = 120) }
        assertNull(ReplacePlan.of(data, equal, ask))
    }

    @Test fun theStaleLineIsShortAndTrue() {
        assertEquals("Kept both: your Pokémon changed since.", AskMe.STALE)
    }

    // ---- CX-4: the slot

    private class Dialog { var closed = 0; val close: () -> Unit = { closed++ } }

    @Test fun theSlotIsTakenAndFreedByTheDialogItself() {
        val slot = AskSlot(); val d = Dialog(); val token = Any()
        assertFalse(slot.isOpen)
        slot.take(token, d.close)
        assertTrue(slot.isOpen)
        slot.free(Any())                       // another dialog's end does not free it
        assertTrue(slot.isOpen)
        slot.free(token)
        assertFalse(slot.isOpen); assertEquals("freeing is not closing", 0, d.closed)
    }

    @Test fun whenTheOwnerStopsTheDialogIsClosedWithoutAnsweringAndTheSlotIsFree() {
        val slot = AskSlot(); val d = Dialog(); val token = Any()
        slot.take(token, d.close)
        assertTrue(slot.onOwner(token, OwnerEvent.STOPPED, inPip = false))
        assertEquals(1, d.closed); assertFalse("a front screen can offer its question now", slot.isOpen)
        assertFalse("a second event does nothing", slot.onOwner(token, OwnerEvent.STOPPED, inPip = false))
        assertEquals(1, d.closed)
    }

    @Test fun enteringPictureInPictureFreesTheSlotButAPlainPauseDoesNot() {
        val slot = AskSlot(); val d = Dialog(); val token = Any()
        slot.take(token, d.close)
        assertFalse("a pause (a system dialog in front) keeps the question", slot.onOwner(token, OwnerEvent.PAUSED, inPip = false))
        assertTrue(slot.isOpen); assertEquals(0, d.closed)
        assertTrue("paused into picture-in-picture ends it", slot.onOwner(token, OwnerEvent.PAUSED, inPip = true))
        assertEquals(1, d.closed); assertFalse(slot.isOpen)
    }

    @Test fun theOwnerBeingDestroyedFreesTheSlotToo() {
        val slot = AskSlot(); val d = Dialog(); val token = Any()
        slot.take(token, d.close)
        assertTrue(slot.onOwner(token, OwnerEvent.DESTROYED, inPip = false))
        assertEquals(1, d.closed); assertFalse(slot.isOpen)
    }

    @Test fun anEventOfAnotherDialogsOwnerChangesNothing() {
        val slot = AskSlot(); val first = Dialog(); val second = Dialog(); val t1 = Any(); val t2 = Any()
        slot.take(t1, first.close)
        assertFalse(slot.onOwner(t2, OwnerEvent.STOPPED, inPip = false))     // the screen behind, which never owned the open question
        assertTrue(slot.isOpen); assertEquals(0, first.closed)
        slot.onOwner(t1, OwnerEvent.STOPPED, inPip = false)
        slot.take(t2, second.close)                                           // the front screen offers its question
        assertTrue(slot.isOpen)
        assertFalse("the old owner's late event can not close the new dialog", slot.onOwner(t1, OwnerEvent.STOPPED, inPip = false))
        assertEquals(0, second.closed); assertTrue(slot.isOpen)
    }
}
