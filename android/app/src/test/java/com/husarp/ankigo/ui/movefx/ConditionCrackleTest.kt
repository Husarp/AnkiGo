package com.husarp.ankigo.ui.movefx

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class ConditionCrackleTest {
    @Test fun shortSnapsHaveLongRandomGapsAndDoNotRestartOnEveryFrame() {
        val crackle = ConditionCrackle(Random(38))
        val starts = mutableListOf<Long>()
        val widths = mutableListOf<Long>()
        var was = false
        for (ms in 0L..40000L step 10) {
            val on = crackle.visible(ms, 0)
            if (on && !was) starts += ms
            if (!on && was) widths += ms - starts.last()
            was = on
        }
        assertTrue(starts.size >= 5)
        assertTrue(widths.all { it in 160L..250L })
        val gaps = starts.zipWithNext { a, b -> b - a }
        assertTrue(gaps.all { it in 1960L..5450L })
        assertTrue(gaps.distinct().size > 2)
        assertFalse(crackle.visible(40001, 40001))
    }
}
