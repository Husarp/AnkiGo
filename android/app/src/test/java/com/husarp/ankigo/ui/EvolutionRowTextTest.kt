package com.husarp.ankigo.ui

import com.husarp.ankigo.game.*
import org.junit.Assert.*
import org.junit.Test

class EvolutionRowTextTest {
    private val data = TestData.data(listOf(TestData.species(133, "Eevee"), TestData.species(135, "Jolteon")))

    @Test fun friendshipShowsThresholdAndPlainTime() {
        val e = Evolution(135, EvoKind.FRIENDSHIP, minFriendship = 160, time = "night")
        assertEquals("Level 3 Bond\nat night", EvolutionRowText.condition(data, e))
        val mon = TestData.mon(data, 133, 20).apply { bondPoints = 124 }
        assertEquals("Level 3 Bond\nat night", EvolutionRowText.condition(data, e, mon))
        mon.bondPoints = 250
        assertEquals("Level 3 Bond\nat night", EvolutionRowText.condition(data, e, mon))
        mon.bondPoints = 124
        val text = EvolutionRowText.explanation(data, e, data.sp(133), setOf(133), null, mon)
        assertTrue(text.contains("Level 3 Bond"))
        assertFalse(text.contains("124 points"))
        assertTrue(text.contains("18:00–06:00"))
        assertFalse(EvolutionRowText.explanation(data, e, data.sp(135), setOf(135), null, mon).contains("Bond now:"))
    }

    @Test fun normalRowShowsBondGateAlongsideTheUsualRequirement() {
        val e = Evolution(135, EvoKind.LEVEL, level = 16)
        val mon = TestData.mon(data, 133, 40).apply { bondPoints = 124 }
        assertEquals("Level 16\nLevel 2 Bond", EvolutionRowText.condition(data, e, mon))
        mon.bondPoints = 125
        assertEquals("Level 16", EvolutionRowText.condition(data, e, mon))
    }

    @Test fun itemInstructionsIncludeAvailabilityAndShopWithoutBagOrSpoilers() {
        val e = Evolution(135, EvoKind.ITEM, item = "thunder-stone")
        val state = TestData.state(data, 133, 20).apply { bag.clear() }
        val text = EvolutionRowText.explanation(data, e, data.sp(133), emptySet(), state, null)
        assertFalse(text.contains("open your Bag"))
        assertTrue(text.contains("You don't have a Thunder Stone"))
        assertTrue(text.contains("Shop"))
        assertFalse(text.contains("Eevee"))
        state.bag["thunder-stone"] = 2
        val owned = EvolutionRowText.explanation(data, e, data.sp(133), emptySet(), state, null)
        assertFalse(owned.contains("open your Bag"))
        assertTrue(owned.contains("You own 2 Thunder Stone"))
        assertFalse(owned.contains("Eevee"))
        assertTrue(EvolutionRowText.explanation(data, e, data.sp(133), setOf(133), state, null).contains("You own 2"))
    }

    @Test fun ownedItemOnPokemonExplainsHoldEvolve() {
        val state = TestData.state(data, 133, 20).apply { bag["thunder-stone"] = 1 }
        val text = EvolutionRowText.explanation(data, Evolution(135, EvoKind.ITEM, item = "thunder-stone"),
            data.sp(133), setOf(133), state, state.main)
        assertTrue(text.contains("Hold Evolve for one second"))
        assertFalse(text.contains("tap again"))
        assertFalse(text.contains("open your Bag"))
        assertTrue(text.contains("Shop"))
    }

    @Test fun heldEverstoneDoesNotAddObsoleteEvolutionRestriction() {
        val state = TestData.state(data, 133, 20)
        val mon = state.main!!.apply { heldItem = "everstone"; bondPoints = 125 }
        val text = EvolutionRowText.explanation(data, Evolution(135, EvoKind.ITEM, item = "thunder-stone"),
            data.sp(133), setOf(133), state, mon)
        assertTrue(text.contains("Level 2 Bond ✓"))
        assertFalse(text.contains("Everstone"))
        assertFalse(text.contains("take it off"))
    }

    @Test fun requirementsTrackEachGateAndMaskAnUnknownTarget() {
        val e = Evolution(135, EvoKind.LEVEL, level = 26, time = "day", gender = 'F')
        val st = TestData.state(data, 133, 20)
        val mon = st.main!!.apply { bondPoints = 124; gender = 'M' }
        val noon = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 12) }.timeInMillis
        fun checks() = EvolutionRowText.requirements(data, e, data.sp(133), st, mon, noon).associate { it.label to it.met }
        assertEquals(mapOf("Level 2 Bond" to false, "Level 26" to false, "in the daytime" to true, "Female Pokémon" to false), checks())
        mon.bondPoints = 125; mon.level = 26; mon.gender = 'F'
        assertTrue(checks().values.all { it })
        assertEquals("Evolution · ???", EvolutionRowText.title(data, e, emptySet()))
        assertEquals("Evolution · Jolteon", EvolutionRowText.title(data, e, setOf(135)))
        assertFalse(EvolutionRowText.requirements(data, e, data.sp(135), st, mon, noon).first().met)
    }

    @Test fun itemRequirementsUseTheSameLabelAndCheckBagAvailability() {
        val st = TestData.state(data, 133, 20).apply { bag.clear() }
        val mon = st.main!!.apply { bondPoints = 125; heldItem = "metal-coat" }
        for (e in listOf(Evolution(135, EvoKind.ITEM, item = "fire-stone"), Evolution(135, EvoKind.TRADE, item = "metal-coat"))) {
            val item = Progression.evolutionItem(e)!!
            val label = "Use ${Items.name(item)}"
            assertEquals(label, EvolutionRowText.condition(data, e, mon))
            fun requirement() = EvolutionRowText.requirements(data, e, data.sp(133), st, mon).single { it.label == label }
            assertFalse(requirement().met)
            st.bag[item] = 1
            assertTrue(requirement().met)
        }
    }

    @Test fun tradeUsesActualAnkiGoItemAndDayClockIsExplained() {
        val e = Evolution(135, EvoKind.TRADE, time = "day")
        assertEquals("Use Linking Cord\nin the daytime", EvolutionRowText.condition(data, e))
        val text = EvolutionRowText.explanation(data, e, null, emptySet(), null, null)
        assertTrue(text.contains("Linking Cord"))
        assertTrue(text.contains("06:00–18:00"))
    }
}
