package com.husarp.ankigo.stats

import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CollectionStatsTest {
    // gen 1: 1 grass/poison, 2 grass, 3 fire (all base); gen 2: 152 water; gen 4: 400 water/flying + regional form 10001 of #1
    private val data: GameData = TestData.data(
        listOf(
            TestData.species(1, types = listOf(Type.GRASS, Type.POISON)),
            TestData.species(2, types = listOf(Type.GRASS)),
            TestData.species(3, types = listOf(Type.FIRE)),
            TestData.species(152, types = listOf(Type.WATER), isLegendary = true),
            TestData.species(400, types = listOf(Type.WATER, Type.FLYING), isMythical = true),
            TestData.species(10001, "Regionalmon", listOf(Type.FIRE), gen = 7).copy(form = "Alola", baseNum = 1),
        ),
    )

    private fun mon(sp: Int, lv: Int, uid: String, at: Long = 0, shiny: Boolean = false, tier: Tier = Tier.NORMAL): Mon =
        TestData.mon(data, sp, lv, uid = uid).also { it.caughtAt = at; it.shiny = shiny; it.tier = tier }

    private fun stats(state: GameState) = CollectionStats.of(data, state, 1_000L)

    @Test fun emptyCollectionIsAllZeros() {
        val s = stats(GameState())
        assertEquals(0, s.owned); assertEquals(0, s.duplicates); assertEquals(0.0, s.averageTeamLevel, 0.0)
        assertNull(s.highest); assertNull(s.firstCatch); assertNull(s.lastCatch); assertNull(s.longestHeld); assertNull(s.rarest)
        assertEquals(DexProgress(0, 0, 5), s.dex)
        assertEquals(0, s.dex.caughtPercent)
        assertEquals(emptyList<Pair<String, Int>>(), s.ballCatches)
    }

    @Test fun countsOwnedKindsAndDuplicates() {
        val st = GameState()
        st.mons += listOf(
            mon(1, 10, "a", shiny = true), mon(1, 12, "b"), mon(152, 30, "c"), mon(400, 40, "d", shiny = true), mon(10001, 5, "e"),
        )
        st.team += listOf("a", "b")
        st.unsupported += com.husarp.ankigo.game.UnsupportedMon("x", null, "#9", null, 5, false, "r", null)
        st.catches = 7; st.defeats = 20; st.evolutions = 2
        val s = stats(st)
        assertEquals(5, s.owned); assertEquals(4, s.distinctSpecies); assertEquals(1, s.duplicates)
        assertEquals(2, s.shinies); assertEquals(1, s.legendaries); assertEquals(1, s.mythicals); assertEquals(1, s.regionalForms)
        assertEquals(1, s.unsupported)
        assertEquals(7, s.totalCatches); assertEquals(20, s.totalDefeats); assertEquals(2, s.totalEvolutions)
        assertEquals("d", s.highest!!.uid); assertEquals(40, s.highest!!.level)
        assertEquals(11.0, s.averageTeamLevel, 0.0)
    }

    @Test fun averageTeamLevelHasOneDecimal() {
        val st = GameState()
        st.mons += listOf(mon(1, 10, "a"), mon(2, 11, "b"), mon(3, 11, "c"))
        st.team += listOf("a", "b", "c")
        assertEquals(10.7, stats(st).averageTeamLevel, 0.0)
    }

    @Test fun dexOverallPerGenAndPerType() {
        val st = GameState()
        st.seen += listOf(1, 2, 152, 10001); st.caught += listOf(1, 152)
        val s = stats(st)
        // regional form 10001 is not a base species: not in the total, not in seen
        assertEquals(DexProgress(3, 2, 5), s.dex)
        assertEquals(60, s.dex.seenPercent); assertEquals(40, s.dex.caughtPercent)
        // gens: 1, 2, 4 only (gen 3 / 5..9 have no species; the form's gen 7 is ignored)
        assertEquals(listOf(1, 2, 4), s.dexByGen.keys.toList())
        assertEquals(DexProgress(2, 1, 3), s.dexByGen.getValue(1))
        assertEquals(DexProgress(1, 1, 1), s.dexByGen.getValue(2))
        assertEquals(DexProgress(0, 0, 1), s.dexByGen.getValue(4))
        // types: only types that exist; a dual type counts for both
        assertEquals(setOf(Type.GRASS, Type.POISON, Type.FIRE, Type.WATER, Type.FLYING), s.dexByType.keys)
        assertEquals(DexProgress(2, 1, 2), s.dexByType.getValue(Type.GRASS))
        assertEquals(DexProgress(1, 1, 1), s.dexByType.getValue(Type.POISON))
        assertEquals(DexProgress(1, 1, 2), s.dexByType.getValue(Type.WATER))
        assertEquals(DexProgress(0, 0, 1), s.dexByType.getValue(Type.FLYING))
        assertEquals(DexProgress(0, 0, 1), s.dexByType.getValue(Type.FIRE))
    }

    @Test fun ballCatchesSortedAndSaveWithoutThem() {
        val st = GameState()
        assertEquals(emptyList<Pair<String, Int>>(), stats(st).ballCatches)
        st.ballCatches.putAll(mapOf("poke-ball" to 3, "auto" to 5, "great-ball" to 3, "ultra-ball" to 0))
        assertEquals(listOf("auto" to 5, "great-ball" to 3, "poke-ball" to 3), stats(st).ballCatches)
    }

    @Test fun catchDatesAndLongestHeldIgnoreUnknownDates() {
        val st = GameState()
        st.mons += listOf(mon(1, 10, "a", at = 0), mon(2, 10, "b", at = 500), mon(3, 10, "c", at = 200), mon(152, 10, "d", at = 900))
        val s = stats(st)
        assertEquals(200L, s.firstCatch); assertEquals(900L, s.lastCatch); assertEquals("c", s.longestHeld!!.uid)
        st.mons.clear(); st.mons += mon(1, 10, "z", at = 0)
        assertNull(stats(st).firstCatch); assertNull(stats(st).longestHeld)
    }

    @Test fun rarestFollowsTierOrderThenShinyThenLevel() {
        val st = GameState()
        st.mons += listOf(
            mon(1, 90, "n"), mon(2, 10, "baby", tier = Tier.BABY), mon(3, 10, "starter", tier = Tier.STARTER),
            mon(152, 10, "ultra", tier = Tier.ULTRA), mon(400, 10, "mega", tier = Tier.MEGA),
        )
        assertEquals("ultra", stats(st).rarest!!.uid)
        st.mons += mon(1, 20, "leg1", tier = Tier.LEGENDARY)
        st.mons += mon(2, 50, "leg2", tier = Tier.LEGENDARY)
        assertEquals("leg2", stats(st).rarest!!.uid) // same tier: higher level
        st.mons += mon(3, 5, "leg3", tier = Tier.LEGENDARY, shiny = true)
        assertEquals("leg3", stats(st).rarest!!.uid) // same tier: shiny first
        st.mons += mon(152, 1, "myth", tier = Tier.MYTHICAL)
        assertEquals("myth", stats(st).rarest!!.uid)
    }

    @Test fun percentHelper() {
        assertEquals(0, CollectionStats.percent(0, 0)); assertEquals(0, CollectionStats.percent(5, 0))
        assertEquals(33, CollectionStats.percent(1, 3)); assertEquals(67, CollectionStats.percent(2, 3))
        assertEquals(100, CollectionStats.percent(3, 3))
        assertEquals("0 %", CollectionStats.percentText(0, 0)); assertEquals("50 %", CollectionStats.percentText(1, 2))
    }
}
