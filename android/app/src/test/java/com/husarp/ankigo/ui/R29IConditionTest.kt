package com.husarp.ankigo.ui

import org.junit.Assert.*
import org.junit.Test

class R29IConditionTest {
    private fun mark(key: String, value: Int = 1) = ConditionMarks.Mark(key, ConditionMarks.Slot.OURS, ConditionMarks.def(key)!!, value, 1)

    @Test fun everyChipHasAShortExplanationWithoutNeedingASave() {
        assertEquals(ConditionMarks.allKeys.filterNot { it.startsWith("stat.") }.toSet(), ConditionExplanation.effects.keys)
        for ((key, text) in ConditionExplanation.effects) {
            assertTrue(key, text.isNotBlank() && text.length <= 100)
            assertTrue(key, text.endsWith('.'))
        }
    }

    @Test fun stagesAndEffectsEachHaveTheirOwnGroup() {
        for (count in listOf(1, 3, 5, 7)) {
            val stages = ConditionMarks.STAT_KEYS.take(count).map { mark("stat.$it") }
            val confusion = mark("confusion", 3)
            val seed = mark("leechseed")
            val groups = ConditionSummary.groups(listOf(confusion) + stages + seed)
            assertEquals(count + 2, groups.size)
            assertEquals(stages, groups.take(count).map { it.single() })
            assertEquals(listOf(confusion, seed), groups.drop(count).map { it.single() })
        }
        assertTrue(ConditionSummary.groups(emptyList()).isEmpty())
    }

    @Test fun stageSizeTracksVisibleWindowAndNeverFallsBelowEitherTextFloor() {
        assertEquals(10.5f, ConditionSummary.textSp(360f, 604f, 1f, 8f), 0.001f)
        assertEquals(8f, ConditionSummary.textSp(320f, 360f, 1f, 8f), 0.001f)
        for (scale in listOf(0.4f, 0.75f, 1f, 1.5f)) {
            val virtual = ConditionSummary.textSp(320f / scale, 360f / scale, scale, 8f / scale)
            assertEquals(8f, virtual * scale, 0.001f)
            assertTrue(virtual >= 8f / scale)
        }
        assertEquals(16f, ConditionSummary.textSp(360f, 604f, 0.5f, 16f), 0.001f)
    }

    @Test fun statExplanationsUseAimFactorsAndRetainExactStages() {
        assertTrue(ConditionExplanation.line(mark("stat.atk", -2)).contains("Atk −2"))
        assertTrue(ConditionExplanation.effect(mark("stat.atk", -2)).contains("×1/2"))
        assertTrue(ConditionExplanation.effect(mark("stat.accuracy", -2)).contains("×3/5"))
        assertTrue(ConditionExplanation.effect(mark("stat.evasion", 2)).contains("harder to hit"))
        assertEquals("Defense ×4.", ConditionExplanation.effect(mark("stat.def", 6)))
    }
}
