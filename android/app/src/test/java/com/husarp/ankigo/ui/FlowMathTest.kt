package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R9 C2 (line breaks of the matchup flow) and C1 (Compare opens on Auto). Pure. */
class FlowMathTest {
    private fun ranges(widths: List<Int>, gap: Int, maxW: Int) = FlowMath.lines(widths, gap, maxW).map { it.start to it.end }

    @Test fun emptyFlowHasNoLines() = assertTrue(FlowMath.lines(emptyList(), 8, 100).isEmpty())

    @Test fun aBoxWrapsOnlyWhenItDoesNotFitInWhatIsLeft() {
        // 30 + 8 + 30 = 68; + 8 + 30 = 106 > 100 -> the third goes down
        assertEquals(listOf(0 to 2, 2 to 3), ranges(listOf(30, 30, 30), 8, 100))
        // exactly full is still a fit, one more pixel is not
        assertEquals(listOf(0 to 3), ranges(listOf(28, 28, 28), 8, 100))
        assertEquals(listOf(0 to 2, 2 to 3), ranges(listOf(28, 28, 29), 8, 100))
    }

    @Test fun shortBoxesPackTightlyAfterALongOne() {
        assertEquals(listOf(0 to 1, 1 to 4), ranges(listOf(90, 20, 20, 20), 8, 100))
        assertEquals(listOf(0 to 1, 1 to 5), ranges(listOf(95, 10, 10, 10, 10), 8, 100))
    }

    @Test fun aBoxWiderThanTheRowStandsAlone() {
        assertEquals(listOf(0 to 1, 1 to 2, 2 to 3), ranges(listOf(10, 150, 10), 8, 100))
    }

    @Test fun lineWidthIsTheSumPlusGaps() {
        assertEquals(30 + 20 + 40 + 16, FlowMath.lines(listOf(30, 20, 40), 8, 1000)[0].width)
    }

    @Test fun noLineEverBreaksWhileTheNextBoxWouldHaveFit() {
        val widths = listOf(33, 61, 25, 47, 18, 52, 39, 70, 22, 41)
        for (maxW in 40..260 step 7) {
            val lines = FlowMath.lines(widths, 8, maxW)
            for (i in 0 until lines.size - 1) assertTrue("w=$maxW line $i", lines[i].width + 8 + widths[lines[i].end] > maxW)
            for (ln in lines) assertTrue(ln.width <= maxW || ln.end - ln.start == 1)
            assertEquals(widths.size, lines.sumOf { it.end - it.start })
        }
    }

    @Test fun compareOpensOnAuto() = assertEquals(CompareFit.AUTO, CompareFit.atOpen())
}
