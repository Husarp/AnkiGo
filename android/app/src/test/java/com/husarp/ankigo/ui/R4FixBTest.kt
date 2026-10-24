package com.husarp.ankigo.ui

import com.husarp.ankigo.stats.ChartData
import com.husarp.ankigo.stats.ChartMetric
import com.husarp.ankigo.stats.ChartPeriod
import com.husarp.ankigo.stats.StatsSnapshot
import com.husarp.ankigo.ui.CatchResultShift.Do
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R4 fix batch, FIX-B (UI) pure rules: B7 result mode after a window change, N1 fit-to-width scale, N2 empty chart texts. */
class R4FixBTest {
    // ---------------- B7: a result that is on screen when the window changes

    @Test fun aHeldResultThatMeetsASmallWindowGetsTheTimeout() {
        // full battle -> picture-in-picture / small floating window: [full] false, the log still shows
        assertEquals(Do.TIMEOUT, CatchResultShift.after(timed = false, full = false, logShown = true))
    }

    @Test fun aTimedResultKeepsItsOwnTimerWhateverTheWindowDoes() {
        assertEquals(Do.KEEP, CatchResultShift.after(timed = true, full = false, logShown = true))
        // back to a big window before the timeout: the next wild one is on stage already, so it must not be held again
        assertEquals(Do.KEEP, CatchResultShift.after(timed = true, full = true, logShown = true))
    }

    @Test fun aHeldResultInTheFullPanelStaysHeld() {
        assertEquals(Do.KEEP, CatchResultShift.after(timed = false, full = true, logShown = true))
    }

    @Test fun aResultWithNoRoomLeftGoesAtOnceInEveryMode() {
        for (timed in listOf(false, true)) for (full in listOf(false, true))
            assertEquals("timed=$timed full=$full", Do.HIDE, CatchResultShift.after(timed, full, logShown = false))
    }

    @Test fun theShiftAgreesWithTheModeThatWasDecidedWhenTheResultArrived() {
        // a result that arrives in a small window is TIMED from the start: the shift never changes it
        assertEquals(CatchTilesPlan.Mode.TIMED, CatchTilesPlan.mode(full = false, logShown = true, answerWaiting = false))
        assertEquals(Do.KEEP, CatchResultShift.after(timed = true, full = false, logShown = true))
        // and one that arrives in the full panel is HOLD: only a shrinking window moves it
        assertEquals(CatchTilesPlan.Mode.HOLD, CatchTilesPlan.mode(full = true, logShown = true, answerWaiting = false))
        assertEquals(Do.TIMEOUT, CatchResultShift.after(timed = false, full = false, logShown = true))
    }

    // ---------------- N1: the evolution line fits the room

    @Test fun aLineThatFitsKeepsItsSize() {
        assertEquals(1f, FitWidth.scaleFor(224, 259), 0f)
        assertEquals(1f, FitWidth.scaleFor(259, 259), 0f)
    }

    @Test fun aWiderLineIsScaledToTheRoom() {
        assertEquals(259f / 300f, FitWidth.scaleFor(300, 259), 0.0001f)   // a three-stage line with long names in the 340 dp card
        assertEquals(0.5f, FitWidth.scaleFor(518, 259), 0.0001f)
    }

    @Test fun neverUnderTheFloorAndNeverZeroOrNegative() {
        assertEquals(FitWidth.MIN_SCALE, FitWidth.scaleFor(2000, 259), 0f)
        assertEquals(1f, FitWidth.scaleFor(0, 259), 0f)
        assertEquals(1f, FitWidth.scaleFor(300, 0), 0f)
        assertEquals(1f, FitWidth.scaleFor(300, -5), 0f)
    }

    // ---------------- N2: the empty-chart text reads naturally for every metric

    @Test fun noMetricReadsLikeAUnit() {
        for (m in ChartMetric.entries) {
            val t = StatsChartMath.emptyText(m)
            assertFalse("$m: $t", t.startsWith("No HP "))
            assertFalse("$m: $t", t.startsWith("No coins "))
            assertFalse("$m: $t", t.startsWith("No min "))
            assertFalse("$m: $t", t.startsWith("No % "))
            assertTrue("$m: $t", t.endsWith("."))
        }
    }

    @Test fun otherGameAndStudyMetricsAreWordedToo() {
        assertEquals("No money earned in this period yet.", StatsChartMath.emptyText(ChartMetric.MONEY))
        assertEquals("No XP earned in this period yet.", StatsChartMath.emptyText(ChartMetric.XP))
        assertEquals("No study time in this period yet.", StatsChartMath.emptyText(ChartMetric.TIME))
        assertEquals("No catches in this period yet.", StatsChartMath.emptyText(ChartMetric.CATCHES))
        assertEquals("No battles won in this period yet.", StatsChartMath.emptyText(ChartMetric.WINS))
        assertEquals("No answers counted in this period yet.", StatsChartMath.emptyText(ChartMetric.RETENTION))
    }

    @Test fun theChartShowsTheMetricText() {
        val today = "2026-10-14"
        for (m in listOf(ChartMetric.MONEY, ChartMetric.XP, ChartMetric.CATCHES, ChartMetric.WINS)) {
            val s = ChartData.build(m, ChartPeriod.D30, null, StatsSnapshot(), emptyMap(), today)
            assertEquals(m.name, StatsChartMath.emptyText(m), StatsChartMath.emptyMessage(s, null, null))
        }
    }
}
