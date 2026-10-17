package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Stat
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Test

/** The new Pokédex sorts (PLAN 55): level, stats with a basis, chosen-stat total, first seen; each with All / Owned / Not owned. */
class DexSortTest {
    // base stats: 1 hp 100 atk 40 | 2 hp 40 atk 100 | 3 hp 70 atk 70 | 4 hp 55 atk 55 | 5 hp 90 atk 20
    private val all = listOf(
        TestData.species(1, "Aa", listOf(Type.NORMAL), TestData.stats(100, 40, 50, 50, 50, 50)),
        TestData.species(2, "Bb", listOf(Type.NORMAL), TestData.stats(40, 100, 50, 50, 50, 50)),
        TestData.species(3, "Cc", listOf(Type.NORMAL), TestData.stats(70, 70, 50, 50, 50, 50)),
        TestData.species(4, "Dd", listOf(Type.NORMAL), TestData.stats(55, 55, 50, 50, 50, 50)),
        TestData.species(5, "Ee", listOf(Type.NORMAL), TestData.stats(90, 20, 50, 50, 50, 50)),
    )
    private val data = TestData.data(all)
    private val gens = setOf(1)

    private var n = 0
    private fun mon(sp: Int, lv: Int, ivs: Int = 15) =
        TestData.mon(data, sp, lv, uid = "u${n++}").let { m ->
            if (ivs == 15) m else Mon(m.uid, sp, m.name, lv, 0, Stats(ivs, ivs, ivs, ivs, ivs, ivs), m.evs, m.nature, 'M', false, "x", m.moves, 1)
        }

    // owned: species 1 (Lv 10 and Lv 40 copies), 3 (Lv 25); 2 is seen only; 4 and 5 never met
    private val owned = listOf(mon(1, 10), mon(1, 40), mon(3, 25))
    private val state = GameState(createdAt = 1L, starterChosen = true).also { s ->
        s.mons += owned
        s.markSeen(1, 500); s.markSeen(2, 100); s.markSeen(3, 300)
    }
    private val last = DexFilter.lastCaught(owned)
    private val info = DexSortInfo(data, state)

    private fun run(f: DexFilter) = f.apply(all, last, emptySet(), gens, info).map { it.num }

    @Test fun levelIsTheHighestOwnedLevelAndNotOwnedGoLast() {
        assertEquals(listOf(1, 3, 2, 4, 5), run(DexFilter(sort = DexSort.LEVEL)))
        assertEquals(listOf(3, 1, 2, 4, 5), run(DexFilter(sort = DexSort.LEVEL, descending = false)))
        assertEquals(listOf(1, 3), run(DexFilter(sort = DexSort.LEVEL, owned = DexOwned.OWNED)))
        assertEquals(listOf(2, 4, 5), run(DexFilter(sort = DexSort.LEVEL, owned = DexOwned.NOT_OWNED)))
    }

    @Test fun baseBasisTotalAndStat() {
        // totals: 1, 2, 3 = 340; 4, 5 = 310 (ties keep number order)
        assertEquals(listOf(1, 2, 3, 4, 5), run(DexFilter(sort = DexSort.TOTAL, basis = StatBasis.BASE)))
        assertEquals(listOf(4, 5, 1, 2, 3), run(DexFilter(sort = DexSort.TOTAL, basis = StatBasis.BASE, descending = false)))
        assertEquals(listOf(1, 5, 3, 4, 2), run(DexFilter(sort = DexSort.STAT, stat = Stat.HP, basis = StatBasis.BASE)))
        assertEquals(listOf(2, 3, 4, 1, 5), run(DexFilter(sort = DexSort.STAT, stat = Stat.ATK, basis = StatBasis.BASE)))
    }

    @Test fun chosenTotalWorksWithEveryOwnershipFilter() {
        val f = DexFilter(sort = DexSort.CHOSEN_TOTAL, chosenStats = setOf(Stat.HP, Stat.ATK), basis = StatBasis.BASE)
        // hp+atk: 1 = 140, 2 = 140, 3 = 140, 4 = 110, 5 = 110 -> ties fall back to number
        assertEquals(listOf(1, 2, 3, 4, 5), run(f))
        val g = f.copy(chosenStats = setOf(Stat.ATK))
        assertEquals(listOf(2, 3, 4, 1, 5), run(g))
        assertEquals(listOf(3, 1), run(g.copy(owned = DexOwned.OWNED)))
        assertEquals(listOf(2, 4, 5), run(g.copy(owned = DexOwned.NOT_OWNED)))
    }

    @Test fun maxLevelAndAtLevelUseIv31EvZeroNeutralForSpecies() {
        val f = DexFilter(sort = DexSort.STAT, stat = Stat.HP, basis = StatBasis.MAX_LEVEL)
        val want = all.sortedWith(compareBy({ -StatCalc.hp(it.base.hp, 31, 0, 100) }, { it.num })).map { it.num }
        assertEquals(want, run(f))
        val low = f.copy(basis = StatBasis.AT_LEVEL, level = 1)
        assertEquals(all.sortedWith(compareBy({ -StatCalc.hp(it.base.hp, 31, 0, 1) }, { it.num })).map { it.num }, run(low))
        // an out-of-range level is clamped, not a crash
        assertEquals(run(f), run(f.copy(basis = StatBasis.AT_LEVEL, level = 999)))
    }

    @Test fun currentBasisUsesTheBestOwnedCopyAndNotOwnedGoLastBothWays() {
        val f = DexFilter(sort = DexSort.TOTAL, basis = StatBasis.CURRENT)
        val best1 = StatCalc.stats(data, owned[1]).total  // the Lv 40 copy of species 1
        val t3 = StatCalc.stats(data, owned[2]).total
        val want = if (best1 >= t3) listOf(1, 3) else listOf(3, 1)
        assertEquals(want + listOf(2, 4, 5), run(f))
        assertEquals(want.reversed() + listOf(2, 4, 5), run(f.copy(descending = false)))
        assertEquals(listOf(2, 4, 5), run(f.copy(owned = DexOwned.NOT_OWNED)))
    }

    @Test fun firstSeenOldestOrNewestAndUnknownLast() {
        // seen at: 2 -> 100, 3 -> 300, 1 -> 500; 4 and 5 unknown
        assertEquals(listOf(1, 3, 2, 4, 5), run(DexFilter(sort = DexSort.FIRST_SEEN)))
        assertEquals(listOf(2, 3, 1, 4, 5), run(DexFilter(sort = DexSort.FIRST_SEEN, descending = false)))
        // "seen but not caught" (species 2) stays a Not-owned entry and still sorts by its date
        assertEquals(listOf(2, 4, 5), run(DexFilter(sort = DexSort.FIRST_SEEN, descending = false, owned = DexOwned.NOT_OWNED)))
    }

    @Test fun withoutInfoTheCollectionSortsHaveNoValuesAndKeepNumberOrder() {
        val f = DexFilter(sort = DexSort.LEVEL)
        assertEquals(listOf(1, 2, 3, 4, 5), f.apply(all, last, emptySet(), gens).map { it.num })
    }

    @Test fun existingSortsCanBeReversedAndNotOwnedStayLastForLastCaught() {
        assertEquals(listOf(5, 4, 3, 2, 1), run(DexFilter(sort = DexSort.NUMBER, descending = true)))
        assertEquals(listOf(5, 4, 3, 2, 1), run(DexFilter(sort = DexSort.NAME, descending = true)))
        val lc = DexFilter(sort = DexSort.LAST_CAUGHT)
        assertEquals(listOf(1, 3, 2, 4, 5), run(lc)) // owned first (equal dates: number order), rest by number
        assertEquals(listOf(1, 3, 2, 4, 5), run(lc.copy(descending = false)))
    }

    @Test fun filterRoundTripsThroughASortSpec() {
        val f = DexFilter(sort = DexSort.CHOSEN_TOTAL, chosenStats = setOf(Stat.ATK, Stat.DEF), basis = StatBasis.MAX_LEVEL, level = 50, descending = false)
        val back = DexFilter().withSort(SortSpec.decode(f.spec().encode()))
        assertEquals(DexSort.CHOSEN_TOTAL, back.sort); assertEquals(setOf(Stat.ATK, Stat.DEF), back.chosenStats)
        assertEquals(StatBasis.MAX_LEVEL, back.basis); assertEquals(false, back.descending)
        assertEquals(DexSort.NUMBER, DexFilter().withSort(SortSpec.of(SortKey.VS_WILD)).sort) // no attacks in the Dex
        assertEquals(DexSort.LAST_CAUGHT, DexFilter().withSort(SortSpec.of(SortKey.CAUGHT_DATE)).sort)
    }
}
