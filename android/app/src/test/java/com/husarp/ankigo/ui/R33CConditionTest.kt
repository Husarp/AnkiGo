package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Stats
import org.junit.Assert.*
import org.junit.Test

class R33CConditionTest {
    private fun mark(key: String, stage: Int) = ConditionMarks.Mark(key, ConditionMarks.Slot.OURS,
        ConditionMarks.def("stat.$key")!!, stage, 1L)

    @Test fun realNumbersCoverEveryStatAndStageWithIntegerRounding() {
        val stats = Stats(200, 156, 101, 89, 117, 73)
        for ((key, base) in listOf("atk" to 156, "def" to 101, "spa" to 89, "spd" to 117, "spe" to 73)) {
            for ((stage, numerator, denominator) in listOf(Triple(1, 3, 2), Triple(-1, 2, 3), Triple(6, 4, 1), Triple(-6, 1, 4)))
                assertEquals("$base → ${base * numerator / denominator}", ConditionStat.values(mark(key, stage), stats))
        }
        assertEquals("156 → 234", ConditionStat.values(mark("atk", 1), stats))
        assertNull(ConditionStat.values(mark("accuracy", 1), stats))
        assertNull(ConditionStat.values(mark("evasion", -1), stats))
        assertNull(ConditionStat.values(mark("atk", 1), null))
    }

    @Test fun bothSidesCenterEveryWrappedRowAndOpponentStaysAbove() {
        val card = ConditionSpot.R(90f, 130f, 230f, 180f)
        for (below in listOf(false, true)) {
            val flow = ConditionFlow.owner(card, 380f, floatArrayOf(50f, 45f, 60f, 80f), 3,
                4f, 5f, 18f, emptyList(), 90f, below = below, left = 4f) { 25f }
            assertEquals(4, flow.shown)
            for (line in flow.rects.groupBy { it.t }.values) {
                assertEquals(160f, (line.first().l + line.last().r) / 2f, .01f)
                assertTrue(if (below) line.first().t >= card.b + 5 else line.first().b <= card.t - 5)
            }
        }
    }

    @Test fun aboveOverflowCountsAllConditionsWithinItsHeightBudget() {
        val card = ConditionSpot.R(10f, 100f, 100f, 150f)
        val flow = ConditionFlow.owner(card, 200f, FloatArray(12) { 70f }, 7,
            4f, 4f, 18f, emptyList(), 40f, below = false) { 25f }
        assertTrue(flow.more > 0)
        assertEquals(12, flow.shown + flow.more)
        assertTrue(flow.rects.maxOf { it.b } - flow.rects.minOf { it.t } <= 40)
        assertTrue(flow.rects.all { it.b <= 96f })
    }
}
