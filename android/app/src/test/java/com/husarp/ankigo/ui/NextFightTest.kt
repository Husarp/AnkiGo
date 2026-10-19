package com.husarp.ankigo.ui

import com.husarp.ankigo.game.CashKind
import com.husarp.ankigo.game.DuplicateResult
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R9 C4 + C5: the pure rules of the next fight's order (early hold, early tiles, the cut at the next Encounter, the entrance gate). */
class NextFightTest {
    private val hold = CatchTilesPlan.Mode.HOLD
    private fun counted() = GameEvent.Counted(3, 10, 6, 1, false)
    private fun defeated() = GameEvent.Defeated(7, "Made", 40)
    private fun caught() = GameEvent.Caught("w1", 7, "Made", 12, false, null, true, true)
    private fun xp() = GameEvent.XpGained("m", "Mainy", 40, 7, 0, 100)
    private fun cash() = GameEvent.Cash(10, 20, CashKind.DEFEAT)
    private fun encounter() = GameEvent.Encounter(1, "Next", 5, false, Tier.NORMAL, 10, 10, false, false, "u2")
    private fun dup() = GameEvent.Duplicate("Made", 12, 10, DuplicateResult.SWAPPED, releasedName = "Old")

    // ---------------------------------------------------------------- C5: when the hold starts

    @Test fun aWinWithANextFightHoldsAtOnce() {
        assertTrue(NextFight.holdsEarly(listOf(counted(), defeated(), xp(), cash(), encounter()), hold))
    }

    @Test fun aCatchWithANextFightHoldsAtOnce() {
        assertTrue(NextFight.holdsEarly(listOf(counted(), caught(), cash(), encounter()), hold))
    }

    @Test fun aSmallWindowOrNoRoomNeverHolds() {
        val win = listOf(counted(), defeated(), encounter())
        assertFalse(NextFight.holdsEarly(win, CatchTilesPlan.Mode.TIMED))
        assertFalse(NextFight.holdsEarly(win, CatchTilesPlan.Mode.NONE))
    }

    @Test fun noNextEncounterKeepsTodaysFlow() {
        assertFalse("end of the deck: nothing to continue to", NextFight.holdsEarly(listOf(counted(), defeated(), xp()), hold))
    }

    @Test fun aFledWildOneIsNoResult() {
        assertFalse(NextFight.holdsEarly(listOf(counted(), GameEvent.Fled("Made"), encounter()), hold))
        assertFalse(NextFight.holdsEarly(listOf(counted(), encounter()), hold))
        assertFalse(NextFight.holdsEarly(emptyList(), hold))
    }

    @Test fun anAnswerOrAThrowBehindTheResultIsFastPlay() {
        assertFalse(NextFight.holdsEarly(listOf(counted(), defeated(), encounter(), counted()), hold))
        assertFalse(NextFight.holdsEarly(listOf(counted(), caught(), encounter(), GameEvent.BallThrow("poke-ball", 40, false, 3)), hold))
    }

    @Test fun anAnswerBeforeTheFightEndsDoesNotMatter() {
        // a catch-up batch: several answers, the last one ends the fight
        assertTrue(NextFight.holdsEarly(listOf(counted(), counted(), counted(), defeated(), encounter()), hold))
    }

    @Test fun theLastFightEndOfABatchDecides() {
        assertTrue(NextFight.holdsEarly(listOf(counted(), defeated(), encounter(), caught(), encounter()), hold))
        assertFalse("the last end has no Encounter behind it", NextFight.holdsEarly(listOf(counted(), defeated(), encounter(), caught()), hold))
    }

    // ---------------------------------------------------------------- C5: where the tiles go for screens with the panel's own Continue

    @Test fun earlyTilesComeRightBehindTheFightsEnd() {
        assertEquals(2, NextFight.earlyTilesIndex(listOf(counted(), defeated(), xp(), cash(), encounter())))
        assertEquals(2, NextFight.earlyTilesIndex(listOf(counted(), caught(), cash(), encounter())))
    }

    @Test fun aCatchKeepsItsSwapEventAheadOfTheTiles() {
        // EventPlayer.upcomingSwap() looks ahead from Caught: the Duplicate must still follow it directly
        assertEquals(3, NextFight.earlyTilesIndex(listOf(counted(), caught(), dup(), cash(), encounter())))
    }

    @Test fun aDefeatDoesNotSkipAnUnrelatedDuplicate() {
        assertEquals(2, NextFight.earlyTilesIndex(listOf(counted(), defeated(), dup(), encounter())))
    }

    @Test fun noFightEndPutsTilesAtTheEnd() {
        val e = listOf(counted(), cash())
        assertEquals(e.size, NextFight.earlyTilesIndex(e))
    }

    // ---------------------------------------------------------------- C5: what a Continue tap cuts

    @Test fun theCutIsAtTheFirstEncounter() {
        val items = listOf("log", "xp", "tiles", "ENC", "info", "ENC2")
        val s = NextFight.splitAtNext(items) { it.startsWith("ENC") }
        assertEquals(listOf("log", "xp", "tiles"), s.before)
        assertEquals("ENC", s.next)
        assertEquals(listOf("info", "ENC2"), s.after)
    }

    @Test fun withoutAnEncounterEverythingIsSkipped() {
        val s = NextFight.splitAtNext(listOf("a", "b")) { it == "ENC" }
        assertEquals(listOf("a", "b"), s.before)
        assertNull(s.next)
        assertTrue(s.after.isEmpty())
    }

    @Test fun anEncounterAloneAndAnEmptyQueue() {
        val s = NextFight.splitAtNext(listOf("ENC")) { it == "ENC" }
        assertTrue(s.before.isEmpty()); assertEquals("ENC", s.next); assertTrue(s.after.isEmpty())
        val e = NextFight.splitAtNext(emptyList<String>()) { true }
        assertTrue(e.before.isEmpty()); assertNull(e.next); assertTrue(e.after.isEmpty())
    }

    // ---------------------------------------------------------------- C4: the entrance gate

    @Test fun thePictureOpensTheGateOnceAndTheTimeoutAfterItDoesNothing() {
        val g = NextFight.EntranceGate()
        assertTrue(g.waiting)
        assertTrue("the picture came", g.open())
        assertFalse(g.waiting)
        assertFalse("the late timeout opens nothing", g.open())
    }

    @Test fun theTimeoutOpensTheGateAndALatePictureDoesNothing() {
        val g = NextFight.EntranceGate()
        assertTrue(g.open())
        assertFalse(g.open())
    }

    @Test fun theEntranceRunsOnce() {
        val g = NextFight.EntranceGate()
        g.open()
        assertTrue(g.enter())
        assertFalse(g.enter())
    }

    @Test fun thePictureWaitIsAboutAnSecond() {
        assertTrue(NextFight.PICTURE_WAIT_MS in 1000L..1500L)
    }
}
