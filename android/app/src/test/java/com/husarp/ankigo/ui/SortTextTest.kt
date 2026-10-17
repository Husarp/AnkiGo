package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Stat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// R3-V6: the words of the sort chip, the small list and the tiles.
class SortTextTest {
    @Test fun chipLabelPlainKeys() {
        assertEquals("Level ↓", SortText.chipLabel(SortSpec.of(SortKey.LEVEL)))
        assertEquals("Level ↑", SortText.chipLabel(SortSpec.of(SortKey.LEVEL).flipped()))
        assertEquals("No. ↑", SortText.chipLabel(SortSpec.DEFAULT))
        assertEquals("Name ↑", SortText.chipLabel(SortSpec.of(SortKey.NAME)))
        assertEquals("Last caught ↓", SortText.chipLabel(SortSpec.of(SortKey.CAUGHT_DATE)))
        // a basis only shows on keys that read stats
        assertEquals("Level ↓", SortText.chipLabel(SortSpec.of(SortKey.LEVEL).copy(basis = StatBasis.AT_LEVEL, level = 50)))
    }

    @Test fun chipLabelStatKeysShowTheBasis() {
        val atk = SortSpec(key = SortKey.CHOSEN_TOTAL, chosenStats = setOf(Stat.DEF, Stat.ATK), basis = StatBasis.AT_LEVEL, level = 50)
        assertEquals("Atk + Def ↓ · Lv 50", SortText.chipLabel(atk))
        assertEquals("Atk + Def ↓", SortText.chipLabel(atk.copy(basis = StatBasis.CURRENT)))
        assertEquals("Atk + Def ↓ · Base", SortText.chipLabel(atk.copy(basis = StatBasis.BASE)))
        assertEquals("Atk + Def ↓ · Lv 100", SortText.chipLabel(atk.copy(basis = StatBasis.MAX_LEVEL)))
        assertEquals("Atk + Def ↑", SortText.chipLabel(atk.copy(basis = StatBasis.CURRENT).flipped()))
        assertEquals("Spe ↓", SortText.chipLabel(SortSpec(key = SortKey.STAT, stat = Stat.SPE)))
        assertEquals("HP ↓", SortText.chipLabel(SortSpec(key = SortKey.STAT)))
        assertEquals("Total ↓ · Base", SortText.chipLabel(SortSpec(key = SortKey.TOTAL, basis = StatBasis.BASE)))
        assertEquals("Strongest attack ↓ · Lv 30", SortText.chipLabel(SortSpec(key = SortKey.STRONGEST_ATTACK, basis = StatBasis.AT_LEVEL, level = 30)))
    }

    @Test fun chosenStatsNameStaysShort() {
        fun name(vararg s: Stat) = SortText.chipParts(SortSpec(key = SortKey.CHOSEN_TOTAL, chosenStats = s.toSet())).first
        assertEquals("Chosen stats", name())
        assertEquals("HP + SpA + Spe", name(Stat.SPE, Stat.HP, Stat.SPA))
        assertEquals("4 stats", name(Stat.HP, Stat.ATK, Stat.DEF, Stat.SPA))
        assertEquals("All stats", name(*Stat.entries.toTypedArray()))
    }

    @Test fun directionWords() {
        assertEquals("A → Z", SortText.directionLabel(SortKey.NAME, false))
        assertEquals("Z → A", SortText.directionLabel(SortKey.NAME, true))
        assertEquals("High → low", SortText.directionLabel(SortKey.LEVEL, true))
        assertEquals("Low → high", SortText.directionLabel(SortKey.DEX_NUMBER, false))
        assertEquals("Newest first", SortText.directionLabel(SortKey.FIRST_SEEN, true))
        assertEquals("At Lv 50", SortText.basisLabel(StatBasis.AT_LEVEL, 50))
    }

    @Test fun tileValuesStayShort() {
        assertNull(SortText.tileValue(null))
        assertEquals("212", SortText.tileValue(212.0))
        assertEquals("9999", SortText.tileValue(9999.4))
        assertEquals("12.3k", SortText.tileValue(12_340.0))
        assertEquals("12k", SortText.tileValue(12_010.0))
        assertEquals("345k", SortText.tileValue(345_678.0))
        assertEquals("2.4M", SortText.tileValue(2_400_000.0))
        assertTrue(SortText.tileValue(123_456_789.0)!!.length <= 6)
    }

    @Test fun usesBasisOnlyForStatAndAttackKeys() {
        val on = SortKey.entries.filter { SortText.usesBasis(it) }.toSet()
        assertEquals(setOf(SortKey.TOTAL, SortKey.STAT, SortKey.CHOSEN_TOTAL, SortKey.STRONGEST_ATTACK, SortKey.VS_WILD), on)
        assertTrue(SortText.dexKeys.none { it == SortKey.VS_WILD || it == SortKey.STRONGEST_ATTACK })
        assertEquals(SortKey.entries.toSet(), SortText.boxKeys.toSet())
    }

    @Test fun choosingKeys() {
        val cur = SortSpec(key = SortKey.LEVEL, basis = StatBasis.BASE, stat = Stat.ATK)
        assertEquals(false, SortViews.choose(cur, SortKey.LEVEL).descending) // same key flips
        val name = SortViews.choose(cur, SortKey.NAME)
        assertEquals(SortKey.NAME, name.key); assertEquals(false, name.descending)
        assertEquals(StatBasis.BASE, name.basis); assertEquals(Stat.ATK, name.stat) // settings are kept
        assertTrue(SortViews.needsStats(SortSpec(key = SortKey.CHOSEN_TOTAL)))
        assertTrue(!SortViews.needsStats(SortSpec(key = SortKey.CHOSEN_TOTAL, chosenStats = setOf(Stat.HP))))
        assertTrue(SortViews.needsStats(SortSpec(key = SortKey.STAT)))
        assertTrue(!SortViews.needsStats(SortSpec(key = SortKey.STAT, stat = Stat.DEF)))
    }
}
