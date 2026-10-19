package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueBarTest {
    private val today = "2026-10-16"

    @Test fun showsForLastDeckWithDueCards() = assertTrue(ContinueBar.show(5, 42, null, today))
    @Test fun hiddenWithoutLastDeck() = assertFalse(ContinueBar.show(0, 42, null, today))
    @Test fun hiddenWhenNothingDue() { assertFalse(ContinueBar.show(5, 0, null, today)); assertFalse(ContinueBar.show(5, -1, null, today)) }
    @Test fun hiddenWhenHiddenToday() = assertFalse(ContinueBar.show(5, 42, today, today))
    @Test fun backTheNextDay() = assertTrue(ContinueBar.show(5, 42, "2026-10-15", today))

    @Test fun texts() {
        assertEquals("Continue · Core 2k", ContinueBar.title("Japanese::Core 2k"))
        assertEquals("42 due", ContinueBar.dueText(42))
    }

    @Test fun aDelayedAnkiDroidStartNeedsAWantedRequestAndALiveActivity() {
        assertTrue(ContinueBar.mayStart(finishing = false, destroyed = false, wanted = true))
        assertFalse(ContinueBar.mayStart(finishing = false, destroyed = false, wanted = false))
        assertFalse(ContinueBar.mayStart(finishing = true, destroyed = false, wanted = true))
        assertFalse(ContinueBar.mayStart(finishing = false, destroyed = true, wanted = true))
    }
}
