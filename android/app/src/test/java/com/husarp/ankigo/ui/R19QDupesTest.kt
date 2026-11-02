package com.husarp.ankigo.ui

import com.husarp.ankigo.shots.Shots
import org.junit.Assert.*
import org.junit.Test

/** PLAN 282 / R19: independent saved looks and the complete duplicate tile matrix. */
class R19QDupesTest {
    @Test fun defaults() {
        assertEquals(BoxDupePrefs.Look(true, false), BoxDupePrefs.get(Shots.MemPrefs()))
    }
    @Test fun switchesPersistIndependently() {
        val prefs = Shots.MemPrefs()
        for (a in listOf(false, true)) for (c in listOf(false, true)) {
            BoxDupePrefs.set(prefs, BoxDupePrefs.STACKED, a)
            BoxDupePrefs.set(prefs, BoxDupePrefs.TWO, c)
            assertEquals(BoxDupePrefs.Look(a, c), BoxDupePrefs.get(prefs))
        }
    }
    private fun matrix(a: Boolean, c: Boolean) {
        for (n in listOf(1, 2, 3, 5, 12)) {
            val plan = BoxDupePlan.of(n, a, c)
            assertEquals(if (a) minOf(n - 1, 2) else 0, plan.cards)
            assertEquals(if (c && n > 1) 2 else 1, plan.sprites)
            assertEquals(n, plan.count)
            assertEquals(if (a && n > 1) 10 else 0, plan.insetDp)
        }
    }
    @Test fun offOff() = matrix(false, false)
    @Test fun stackedOnly() = matrix(true, false)
    @Test fun twoOnly() = matrix(false, true)
    @Test fun both() = matrix(true, true)
}
