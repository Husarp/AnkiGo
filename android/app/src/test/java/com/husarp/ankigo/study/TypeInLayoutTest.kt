package com.husarp.ankigo.study

import org.junit.Assert.*
import org.junit.Test

class TypeInLayoutTest {
    private fun plan(window: Int, ime: Int, typeIn: Boolean = true, battle: Int = 204, buttons: Int = 64) =
        TypeInLayout.plan(window, PanelHeight.TOP_BAR, ime, typeIn, battle, buttons)

    @Test fun keyboardClosedKeepsTheNormalLayout() {
        val p = plan(800, 0)
        assertFalse(p.compact); assertEquals(204, p.battleDp); assertEquals(64, p.buttonDp); assertEquals(800 - 46 - 204 - 64, p.cardDp)
    }
    @Test fun otherCardsNeverChange() {
        val p = plan(800, 300, typeIn = false)
        assertFalse(p.compact); assertEquals(204, p.battleDp)
    }
    @Test fun phoneWithKeyboardGivesTheCardEverythingAboveTheButton() {
        val p = plan(800, 300)
        assertTrue(p.compact); assertEquals(0, p.battleDp); assertEquals(TypeInLayout.BUTTON_DP, p.buttonDp)
        assertEquals(800 - 300 - 46 - 64, p.cardDp); assertFalse(p.cardScrolls)
        assertEquals(800 - 300, p.topBarDp + p.cardDp + p.buttonDp)   // the three blocks end exactly at the keyboard
    }
    @Test fun splitScreenStillFitsCardAndButton() {
        val p = plan(400, 200)   // 412x400 window, a 200 dp keyboard
        assertTrue(p.compact); assertEquals(200 - 46 - 64, p.cardDp); assertTrue(p.cardScrolls)
        assertEquals(200, p.topBarDp + p.cardDp + p.buttonDp)
    }
    @Test fun noRoomLeftTheCardScrollsAndTheButtonStaysAboveTheKeyboard() {
        val p = plan(300, 240)
        assertTrue(p.compact); assertEquals(0, p.cardDp); assertTrue(p.cardScrolls); assertEquals(TypeInLayout.BUTTON_DP, p.buttonDp)
        val q = plan(200, 400)   // the keyboard is taller than the window
        assertEquals(0, q.cardDp); assertEquals(0, q.battleDp)
    }
    @Test fun tinyOrFloatingKeyboardIsNotTheKeyboardLayout() {
        assertFalse(plan(800, TypeInLayout.MIN_IME_DP - 1).compact)
        assertTrue(plan(800, TypeInLayout.MIN_IME_DP).compact)
    }
    @Test fun compactDecisionNeedsATypeInCard() {
        assertTrue(TypeInLayout.compact(true, 280)); assertFalse(TypeInLayout.compact(false, 280)); assertFalse(TypeInLayout.compact(true, 0))
    }
    @Test fun closedNormalLayoutNeverGoesNegative() {
        assertEquals(0, plan(200, 0).cardDp)
    }
}
