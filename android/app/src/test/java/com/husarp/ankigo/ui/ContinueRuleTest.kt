package com.husarp.ankigo.ui

import com.husarp.ankigo.ui.ContinueRule.Act
import com.husarp.ankigo.ui.ContinueRule.Row
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueRuleTest {
    @Test fun noResultKeepsTheNormalRow() {
        assertEquals(Row.SHOW_ANSWER, ContinueRule.row(flipped = false, holding = false))
        assertEquals(Row.ANSWERS, ContinueRule.row(flipped = true, holding = false))
    }

    @Test fun aHeldResultTakesTheQuestionRow() {
        assertEquals(Row.CONTINUE, ContinueRule.row(flipped = false, holding = true))
    }

    @Test fun aHeldResultAlsoTakesTheAnswerButtons() {
        // the result arrived while the answer side was up: no answer may be pressed outside a battle
        assertEquals(Row.CONTINUE, ContinueRule.row(flipped = true, holding = true))
    }

    @Test fun everyFlipOrAnswerRequestActsAsContinueWhileHolding() {
        assertEquals(Act.CONTINUE, ContinueRule.onRequest(holding = true))
        assertEquals(Act.RUN, ContinueRule.onRequest(holding = false))
    }

    @Test fun theAutoTimerWaitsForContinue() {
        assertFalse(ContinueRule.autoMayRun(holding = true))
        assertTrue(ContinueRule.autoMayRun(holding = false))
    }

    @Test fun aHiddenPanelNeverLeavesAHoldParked() {
        // visibility never lets a flip / timer through: holding follows the hold alone, a hidden panel releases it at once
        assertTrue(ContinueRule.holding(resultHolds = true))
        assertFalse(ContinueRule.holding(resultHolds = false))
        assertTrue("type-in keyboard layout / summary: nobody could tap Continue", ContinueRule.mustRelease(resultHolds = true, panelVisible = false))
        assertFalse(ContinueRule.mustRelease(resultHolds = true, panelVisible = true))
        assertFalse(ContinueRule.mustRelease(resultHolds = false, panelVisible = false))
    }

    @Test fun noStateLetsAFlipOrTimerThroughWhileAHoldIsParked() {
        for (visible in listOf(true, false)) {
            val holding = ContinueRule.holding(resultHolds = true)
            assertEquals(Act.CONTINUE, ContinueRule.onRequest(holding))
            assertFalse(ContinueRule.autoMayRun(holding))
            // either the Continue is on screen, or the hold is released (then there is nothing parked)
            assertTrue(visible || ContinueRule.mustRelease(true, visible))
        }
    }

    @Test fun theButtonSaysWhatItDoes() {
        assertEquals("Continue", ContinueRule.LABEL)
        assertEquals("next battle", ContinueRule.HINT)
    }

    // PLAN 129: the deck's last answer is in, the finished screen waits for a tap
    @Test fun aFinishedDeckShowsOnlyFinishDeck() {
        assertEquals(Row.FINISH, ContinueRule.row(flipped = false, holding = false, deckDone = true))
        assertEquals(Row.FINISH, ContinueRule.row(flipped = true, holding = false, deckDone = true))
    }

    @Test fun aFinishedDeckWithAHeldResultStillShowsFinishDeck() {
        assertEquals(Row.FINISH, ContinueRule.row(flipped = false, holding = true, deckDone = true))
    }

    @Test fun withoutAFinishedDeckTheRowsAreAsBefore() {
        assertEquals(Row.CONTINUE, ContinueRule.row(flipped = false, holding = true, deckDone = false))
        assertEquals(Row.SHOW_ANSWER, ContinueRule.row(flipped = false, holding = false))
    }

    @Test fun theFinishTextsAreShort() {
        assertEquals("Finish deck", ContinueRule.FINISH_LABEL)
        assertEquals("Deck finished · tap Finish deck", ContinueRule.FINISH_LINE)
    }
}
