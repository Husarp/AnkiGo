package com.husarp.ankigo.ui

import com.husarp.ankigo.stats.*
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test

class R35KChartLoaderTest {
    private fun series() = ChartData.build(ChartMetric.BUTTONS, ChartPeriod.D1000, null, StatsSnapshot(), emptyMap(), "2026-11-15")

    @Test fun aggregationRunsOnWorkerCachesResultsAndRejectsOldChoices() {
        val work = ArrayDeque<Runnable>()
        val main = ArrayDeque<() -> Unit>()
        val loader = StatsChartLoader(Executor { work.add(it) }, { main.add(it) })
        var builds = 0
        var renders = 0
        val build = { builds++; series() }
        val ready = { renders++; Unit }
        assertNull(loader.load("old", build, ready))
        assertNull(loader.load("new", build, ready))
        assertNull(loader.load("new", build, ready))
        assertEquals(0, builds)
        assertEquals(2, work.size)
        work.removeFirst().run(); main.removeFirst().invoke()
        assertEquals(0, renders)
        work.removeFirst().run(); main.removeFirst().invoke()
        assertEquals(1, renders)
        assertNotNull(loader.load("new", build, ready))
        assertTrue(work.isEmpty())
        assertEquals(2, builds)
        assertNull(loader.load("discarded", build, ready))
        loader.clear()
        work.removeFirst().run(); main.removeFirst().invoke()
        assertEquals(1, renders)
    }

    @Test fun clippingKeepsOnlyVisibleSlotsAndLineNeighbours() {
        val visible = StatsChartMath.visibleIndices(1000, 0f, 1000f, 400f, 500f)
        assertEquals(399..501, visible)
        assertTrue(StatsChartMath.visibleIndices(1000, 0f, 1000f, 1001f, 1200f).isEmpty())
        assertEquals(0..999, StatsChartMath.visibleIndices(1000, 0f, 1000f, 0f, 1000f))
        var measures = 0
        val labels = StatsChartMath.labelPlan(1000, 0f, 320f, 8f) { measures++; 70f }
        assertEquals(0, labels.first().index)
        assertEquals(999, labels.last().index)
        assertTrue("label planning is bounded: $measures", measures < 1100)
    }
}
