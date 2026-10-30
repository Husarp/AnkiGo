package com.husarp.ankigo.ui

import com.husarp.ankigo.game.*
import org.junit.Assert.*
import org.junit.Test

class EvolutionRowTextTest {
    private val data = TestData.data(listOf(TestData.species(133, "Eevee"), TestData.species(135, "Jolteon")))

    @Test fun friendshipShowsThresholdAndPlainTime() {
        val e = Evolution(135, EvoKind.FRIENDSHIP, minFriendship = 160, time = "night")
        assertEquals("Friendship 160\nat night", EvolutionRowText.condition(data, e))
        val mon = TestData.mon(data, 133, 20).apply { friendship = 124 }
        val text = EvolutionRowText.explanation(data, e, data.sp(133), setOf(133), null, mon)
        assertTrue(text.contains("124 of 160"))
        assertTrue(text.contains("18:00–06:00"))
        assertFalse(EvolutionRowText.explanation(data, e, data.sp(135), setOf(135), null, mon).contains("124 of"))
    }

    @Test fun itemInstructionsIncludeCountSourceAndBagWithoutSpoilingNames() {
        val e = Evolution(135, EvoKind.ITEM, item = "thunder-stone")
        val state = TestData.state(data, 133, 20).apply { bag.clear() }
        val text = EvolutionRowText.explanation(data, e, data.sp(133), emptySet(), state, null)
        assertTrue(text.contains("open your Bag"))
        assertTrue(text.contains("choose ???"))
        assertTrue(text.contains("You own 0"))
        assertTrue(text.contains("Shop"))
        assertFalse(text.contains("Eevee"))
        state.bag["thunder-stone"] = 2
        assertTrue(EvolutionRowText.explanation(data, e, data.sp(133), setOf(133), state, null).contains("You own 2"))
    }

    @Test fun tradeUsesActualAnkiGoItemAndDayClockIsExplained() {
        val e = Evolution(135, EvoKind.TRADE, time = "day")
        assertEquals("Linking Cord\nin the daytime", EvolutionRowText.condition(data, e))
        val text = EvolutionRowText.explanation(data, e, null, emptySet(), null, null)
        assertTrue(text.contains("Linking Cord"))
        assertTrue(text.contains("06:00–18:00"))
    }
}
