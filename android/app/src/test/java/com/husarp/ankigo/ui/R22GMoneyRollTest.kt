package com.husarp.ankigo.ui

import org.junit.Assert.*
import org.junit.Test

class R22GMoneyRollTest {
    @Test fun smallAndLargeChangesHaveBoundedDurations() {
        assertEquals(300L, MoneyRollMath.duration(100, 101))
        assertEquals(1000L, MoneyRollMath.duration(0, 10000))
        assertEquals(1000L, MoneyRollMath.duration(Int.MIN_VALUE, Int.MAX_VALUE))
        assertEquals(MoneyRollMath.duration(2000, 0), MoneyRollMath.duration(0, 2000))
        assertTrue(MoneyRollMath.duration(0, 100) < MoneyRollMath.duration(0, 1000))
    }
    @Test fun gainsAndPurchasesMoveMonotonicallyToTheirExactDestinations() {
        for ((a, b) in listOf(100 to 600, 600 to 100, Int.MIN_VALUE to Int.MAX_VALUE)) {
            val values = (0..100).map { MoneyRollMath.value(a, b, it / 100f) }
            assertEquals(a, values.first()); assertEquals(b, values.last())
            assertTrue(values.zipWithNext().all { (x, y) -> if (a < b) x <= y else x >= y })
        }
    }
    @Test fun outOfRangeFractionsNeverOvershoot() {
        assertEquals(25, MoneyRollMath.value(25, 0, -1f))
        assertEquals(0, MoneyRollMath.value(25, 0, 2f))
    }
}
