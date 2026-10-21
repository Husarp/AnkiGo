package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CatchChoiceTextTest {
    @Test fun theMenuEntrySaysWhatATapDoes() {
        assertEquals("Don't catch this one", CatchChoiceText.menuLabel(willCatch = true))
        assertEquals("Catch this one", CatchChoiceText.menuLabel(willCatch = false))
    }

    @Test fun theMessageNamesTheRealDifference() {
        assertEquals("Will catch it", CatchChoiceText.toast(before = false, after = true))
        assertEquals("Won't catch it: it will be defeated", CatchChoiceText.toast(before = true, after = false))   // a defeat and a catch pay the same ₽ (R13 Part C): only the Pokémon differs
        assertEquals("This one is always caught", CatchChoiceText.toast(before = true, after = true))
        assertEquals("No change", CatchChoiceText.toast(before = false, after = false))
    }

    @Test fun noOldWordingIsLeft() {
        for (t in listOf(CatchChoiceText.menuLabel(true), CatchChoiceText.menuLabel(false), CatchChoiceText.toast(true, false), CatchChoiceText.toast(false, true))) {
            assert("XP" !in t && "instead (" !in t) { t }
        }
    }
}
