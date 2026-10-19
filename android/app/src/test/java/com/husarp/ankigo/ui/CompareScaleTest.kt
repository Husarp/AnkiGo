package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R9-W3-A: the D7-A "Two-sided strength" scale of Compare's "Best of all". */
class CompareScaleTest {
    private val white = 0xFFFFFFFF.toInt()
    private val darkBand = 0xFF262637.toInt()

    private fun rgb(c: Int) = Triple((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)

    @Test fun middleStaysThePlainBand() {
        assertNull(CompareScale.look(0.5f, white, false).fill)
        assertNull(CompareScale.look(0.5f, darkBand, true).fill)
        assertNull(CompareScale.look(0.5f, white, false).ink)
    }

    @Test fun theCutIsAtD012() {
        // d = |p - 0.5| * 2: p = 0.44 / 0.56 -> 0.12, where the colour starts
        assertNull(CompareScale.look(0.45f, white, false).fill)   // d 0.10
        assertNull(CompareScale.look(0.55f, white, false).fill)
        assertNotNull(CompareScale.look(0.44f, white, false).fill) // d 0.12
        assertNotNull(CompareScale.look(0.56f, white, false).fill)
    }

    @Test fun shareFollowsTheFormula() {
        assertEquals(0f, CompareScale.share(0.11f), 0f)
        assertEquals(0.14 + 0.66 * Math.pow(0.12, 1.2), CompareScale.share(0.12f).toDouble(), 1e-4)
        assertEquals(0.80f, CompareScale.share(1f), 1e-5f)
        assertEquals(0.14 + 0.66 * Math.pow(0.5, 1.2), CompareScale.share(0.5f).toDouble(), 1e-4)
    }

    /** Each channel within 1 (the share is a float). */
    private fun assertRgb(expected: Triple<Int, Int, Int>, c: Int) {
        val got = rgb(c)
        assertTrue("expected $expected, got $got", Math.abs(got.first - expected.first) <= 1 && Math.abs(got.second - expected.second) <= 1 && Math.abs(got.third - expected.third) <= 1)
    }

    @Test fun lightEnds() {
        // worst: 80 % of #f2545b over white; best: 80 % of #2fb36a
        val worst = CompareScale.look(0f, white, false)
        assertRgb(Triple(245, 118, 124), worst.fill!!)
        assertEquals(white, worst.ink)
        val best = CompareScale.look(1f, white, false)
        assertRgb(Triple(89, 194, 136), best.fill!!)
        assertEquals(white, best.ink)
    }

    @Test fun darkEnds() {
        // worst: 80 % of #ff6b7a over #262637; best: 80 % of #4fd987
        val worst = CompareScale.look(0f, darkBand, true)
        assertRgb(Triple(212, 93, 109), worst.fill!!)
        assertEquals(0xFF11111B.toInt(), worst.ink)
        val best = CompareScale.look(1f, darkBand, true)
        assertRgb(Triple(8 + 63, 8 + 174, 11 + 108), best.fill!!) // 38*.2 + 79*.8, 38*.2 + 217*.8, 55*.2 + 135*.8
        assertEquals(0xFF11111B.toInt(), best.ink)
    }

    @Test fun inkSwitchesAbove055() {
        // a = 0.14 + 0.66 d^1.2 = 0.55 at d = ((0.55 - 0.14) / 0.66)^(1/1.2) ~ 0.6757 -> p = 0.5 +- 0.3378
        val dLow = 0.67f; val dHigh = 0.69f
        val pLow = 0.5f - dLow / 2; val pHigh = 0.5f - dHigh / 2
        assertTrue(CompareScale.share(dLow) <= CompareScale.INK_SWITCH)
        assertTrue(CompareScale.share(dHigh) > CompareScale.INK_SWITCH)
        assertNull(CompareScale.look(pLow, white, false).ink)
        assertEquals(white, CompareScale.look(pHigh, white, false).ink)
        assertNull(CompareScale.look(0.5f + dLow / 2, darkBand, true).ink)
        assertEquals(0xFF11111B.toInt(), CompareScale.look(0.5f + dHigh / 2, darkBand, true).ink)
    }

    @Test fun belowIsRedAboveIsGreen() {
        val lo = rgb(CompareScale.look(0.2f, white, false).fill!!)
        val hi = rgb(CompareScale.look(0.8f, white, false).fill!!)
        assertTrue("red: r > g", lo.first > lo.second)
        assertTrue("green: g > r", hi.second > hi.first)
    }

    @Test fun strongerTowardsTheEnds() {
        val near = rgb(CompareScale.look(0.3f, white, false).fill!!).second // red channel g falls as the red gets stronger
        val far = rgb(CompareScale.look(0.05f, white, false).fill!!).second
        assertTrue(far < near)
    }

    @Test fun legendHas21StepsAndTheMiddleIsThePlainCard() {
        val l = CompareScale.legend(white, false)
        assertEquals(21, l.size)
        assertEquals(white, l[10])
        assertEquals(CompareScale.look(0f, white, false).fill, l[0])
        assertEquals(CompareScale.look(1f, white, false).fill, l[20])
        val d = CompareScale.legend(darkBand, true)
        assertEquals(darkBand, d[10])
        assertEquals(21, d.size)
    }
}
