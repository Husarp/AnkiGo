package com.husarp.ankigo.ui

import org.junit.Assert.*
import org.junit.Test

class R31BConditionTest {
    @Test fun ownersNeverDriftToAnUnrelatedFreeLane() {
        for (w in listOf(170f, 240f, 320f, 412f, 800f)) for (left in listOf(4f, w / 2)) {
            val card = ConditionSpot.R(left, 10f, left + 70f, 50f)
            val art = ConditionSpot.R(left + 75f, 55f, w, 200f)
            val widths = FloatArray(12) { 32f + it % 3 * 10f }
            val flow = ConditionFlow.anchored(card, w - 2, widths, 7, 4f, 4f, 16f, listOf(art))
            assertEquals(12, flow.size)
            assertEquals(54f, flow.first().t, .01f)
            for (line in flow.groupBy { it.t }.values) assertEquals(left, line.first().l, .01f)
            for ((i, r) in flow.withIndex()) {
                assertEquals(widths[i], r.w, .01f)
                assertTrue(r.r <= w - 2)
                if (r.t < art.b && r.b > art.t) assertTrue(r.r <= art.l + art.w * .15f + .01f)
                for (j in 0 until i) assertFalse(r.hits(flow[j]))
            }
        }
    }

    @Test fun tinyWindowCountsEveryHiddenConditionAndKeepsTheOverflowAnchored() {
        val card = ConditionSpot.R(10f, 10f, 100f, 50f)
        val flow = ConditionFlow.owner(card, 160f, FloatArray(10) { 75f }, 0, 4f, 4f, 16f, emptyList(), 36f) { 24f }
        assertTrue(flow.more > 0)
        assertEquals(10, flow.shown + flow.more)
        assertEquals(flow.shown + 1, flow.rects.size)
        assertTrue(flow.rects.last().b <= 90f)
        assertEquals(10f, flow.rects.first().l, .01f)
    }

    @Test fun aSpriteUnderTheLeftEdgeStillHasABoundedOverlap() {
        val card = ConditionSpot.R(30f, 10f, 130f, 50f)
        val art = ConditionSpot.R(20f, 54f, 220f, 200f)
        val flow = ConditionFlow.owner(card, 300f, floatArrayOf(50f, 50f), 0, 4f, 4f, 16f, listOf(art), 36f) { 24f }
        assertEquals(2, flow.shown + flow.more)
        assertTrue(flow.rects.isNotEmpty())
        assertTrue(flow.rects.all { it.w * it.h <= art.w * art.h * .08f })
        assertTrue(flow.rects.groupBy { it.t }.values.all { it.size == 1 })
    }

    @Test fun theOtherInfoBoxIsAHardWrapBoundary() {
        val card = ConditionSpot.R(10f, 10f, 100f, 50f)
        val other = ConditionSpot.R(100f, 50f, 200f, 110f)
        val flow = ConditionFlow.owner(card, 300f, FloatArray(6) { 70f }, 0, 4f, 4f, 16f, emptyList(), 36f, listOf(other)) { 24f }
        assertEquals(6, flow.shown + flow.more)
        assertTrue(flow.rects.isNotEmpty())
        assertTrue(flow.rects.all { !it.hits(other) })
    }

    @Test fun stickerAvoidsTheAnchoredChipBand() {
        val card = NewTagRule.Box(200f, 0f, 400f, 80f)
        val chips = NewTagRule.Box(200f, 84f, 350f, 110f)
        val spot = NewTagRule.place(card, 400f, 300f, 0f, 4f, 3f, 40f, 16f, 1f, null, listOf(chips))
        assertFalse(spot.bounds.hits(chips))
    }

    @Test fun exactFitAndLongLabelsWrapWithoutLosingMarks() {
        val card = ConditionSpot.R(10f, 0f, 110f, 40f)
        val fit = ConditionFlow.anchored(card, 110f, floatArrayOf(48f, 48f, 90f), 2, 4f, 3f, 16f, emptyList())
        assertEquals(listOf(43f, 43f, 63f), fit.map { it.t })
        assertEquals(listOf(10f, 62f, 10f), fit.map { it.l })
    }
}
