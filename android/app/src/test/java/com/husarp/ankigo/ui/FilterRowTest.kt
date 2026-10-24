package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R16-2-F (PLAN 192F2 / 192E, X9, X10): the words of the active chip and of the empty states, and the width plan of the one row. */
class FilterRowTest {
    private fun tf(m: TypeMatch, vararg t: Type) = TypeFilter(t.toCollection(LinkedHashSet()), m)
    private fun plain(types: TypeFilter, show: BoxFilter? = null, gens: Set<Int> = emptySet()) =
        FilterRowText.plain(FilterRowText.activePieces(types, show, gens))

    // ---- the active chip ----

    @Test fun nothingOnMeansNoPieces() {
        assertTrue(FilterRowText.activePieces(TypeFilter()).isEmpty())
        assertTrue(FilterRowText.activePieces(TypeFilter(emptySet(), TypeMatch.ONLY), BoxFilter.ALL).isEmpty()) // a mode alone filters nothing
    }

    @Test fun typesShowNamesAndTheMode() {
        assertEquals("Water + Ice · All of", plain(tf(TypeMatch.ALL, Type.WATER, Type.ICE)))
        assertEquals("Water · Only", plain(tf(TypeMatch.ONLY, Type.WATER)))
        assertEquals("3 types · Any of", plain(tf(TypeMatch.ANY, Type.FIRE, Type.WATER, Type.ICE)))
        assertEquals("5 types · Only", plain(tf(TypeMatch.ONLY, Type.FIRE, Type.WATER, Type.ICE, Type.ROCK, Type.BUG)))
    }

    @Test fun eachTypeNameHasItsSymbolBeforeIt() {
        val p = FilterRowText.typePieces(tf(TypeMatch.ALL, Type.WATER, Type.ICE))
        val kinds = p.map { it::class.simpleName }
        assertEquals(listOf("Sym", "Txt", "Txt", "Sym", "Txt", "Txt"), kinds)
        assertEquals(Type.WATER, (p[0] as FilterRowText.Piece.Sym).type)
        assertTrue("no symbols for 3+ types", FilterRowText.typePieces(tf(TypeMatch.ANY, Type.FIRE, Type.WATER, Type.ICE)).none { it is FilterRowText.Piece.Sym })
    }

    @Test fun onlyWhatTheSheetFiltersShowsAndQuickChipFiltersAreNotRepeated() {
        // R18 253: Favourites, Shiny and In team are lit quick chips, not part of the "Filters" chip
        for (q in listOf(BoxFilter.FAVOURITES, BoxFilter.SHINY, BoxFilter.IN_TEAM)) {
            assertTrue("$q has a quick chip", q.hasQuickChip)
            assertTrue("$q alone: no chip", FilterRowText.activePieces(TypeFilter(), q).isEmpty())
        }
        assertEquals("Water · Only", plain(tf(TypeMatch.ONLY, Type.WATER), BoxFilter.FAVOURITES))
        // the sheet-only Show choices are named
        assertEquals("Can evolve now", plain(TypeFilter(), BoxFilter.CAN_EVOLVE))
        assertEquals("Holds an item", plain(TypeFilter(), BoxFilter.HOLDS_ITEM))
        assertEquals("Holds Exp. Share", plain(TypeFilter(), BoxFilter.HOLDS_XP_SHARE))
        assertEquals("Water · Only · Can evolve now", plain(tf(TypeMatch.ONLY, Type.WATER), BoxFilter.CAN_EVOLVE))
        assertEquals("Gen 1–3", plain(TypeFilter(), gens = setOf(1, 2, 3)))
        assertEquals("Fire · Any of · Gen 2", plain(tf(TypeMatch.ANY, Type.FIRE), gens = setOf(2)))
    }

    @Test fun generationWords() {
        assertEquals("Gen 4", FilterRowText.gensLabel(setOf(4)))
        assertEquals("Gen 1–3", FilterRowText.gensLabel(setOf(3, 1, 2)))
        assertEquals("Gen 1, 3", FilterRowText.gensLabel(setOf(1, 3)))
        assertEquals("Gen 1, 2, 4", FilterRowText.gensLabel(setOf(4, 1, 2)))
    }

    // ---- empty states (X9) ----

    @Test fun emptyBoxSaysWhy() {
        assertEquals("No pure Poison Pokémon in your box.", FilterRowText.emptyBox(BoxFilter.ALL, tf(TypeMatch.ONLY, Type.POISON)))
        assertEquals("No Water or Ice Pokémon in your box.", FilterRowText.emptyBox(BoxFilter.ALL, tf(TypeMatch.ANY, Type.WATER, Type.ICE)))
        assertEquals("No Water, Ice or Fire Pokémon in your box.", FilterRowText.emptyBox(BoxFilter.ALL, tf(TypeMatch.ANY, Type.WATER, Type.ICE, Type.FIRE)))
        assertEquals("No Water/Ice Pokémon in your box.", FilterRowText.emptyBox(BoxFilter.ALL, tf(TypeMatch.ALL, Type.WATER, Type.ICE)))
        assertEquals("No Pokémon with only Water + Ice types in your box.", FilterRowText.emptyBox(BoxFilter.ALL, tf(TypeMatch.ONLY, Type.WATER, Type.ICE)))
        assertEquals("No favourite Water Pokémon in your box.", FilterRowText.emptyBox(BoxFilter.FAVOURITES, tf(TypeMatch.ANY, Type.WATER)))
        assertEquals("No shiny pure Water Pokémon in your box.", FilterRowText.emptyBox(BoxFilter.SHINY, tf(TypeMatch.ONLY, Type.WATER)))
        assertEquals("No Water Pokémon in your team.", FilterRowText.emptyBox(BoxFilter.IN_TEAM, tf(TypeMatch.ANY, Type.WATER)))
        assertEquals("No Water Pokémon holds an item.", FilterRowText.emptyBox(BoxFilter.HOLDS_ITEM, tf(TypeMatch.ANY, Type.WATER)))
        assertEquals("No Water Pokémon holds the Exp. Share.", FilterRowText.emptyBox(BoxFilter.HOLDS_XP_SHARE, tf(TypeMatch.ANY, Type.WATER)))
        assertEquals("No Water Pokémon can evolve right now.", FilterRowText.emptyBox(BoxFilter.CAN_EVOLVE, tf(TypeMatch.ANY, Type.WATER)))
    }

    @Test fun emptyBoxWithoutATypeKeepsTodaysTexts() {
        val none = TypeFilter()
        assertEquals("Your box is empty. Pokémon you catch beyond six wait here.", FilterRowText.emptyBox(BoxFilter.ALL, none))
        assertEquals("None of your Pokémon holds an item.", FilterRowText.emptyBox(BoxFilter.HOLDS_ITEM, none))
        assertEquals("None of your Pokémon holds the Exp. Share.", FilterRowText.emptyBox(BoxFilter.HOLDS_XP_SHARE, none))
        assertEquals("No Pokémon can evolve right now.", FilterRowText.emptyBox(BoxFilter.CAN_EVOLVE, none))
        assertEquals("No shiny Pokémon in your box.", FilterRowText.emptyBox(BoxFilter.SHINY, none))
        // X6: no "Hold a Pokémon and pick ..." hint any more
        assertEquals("No favourites yet.", FilterRowText.emptyBox(BoxFilter.FAVOURITES, none))
        // a type mode alone is no filter
        assertEquals("Your box is empty. Pokémon you catch beyond six wait here.", FilterRowText.emptyBox(BoxFilter.ALL, TypeFilter(emptySet(), TypeMatch.ONLY)))
    }

    @Test fun emptyDexSaysWhy() {
        assertEquals("No pure Poison Pokémon in Gen 1–3.", FilterRowText.emptyDex(DexFilter(types = setOf(Type.POISON), typeMatch = TypeMatch.ONLY, gens = setOf(1, 2, 3))))
        assertEquals("No Pokémon named “abc”.", FilterRowText.emptyDex(DexFilter(query = "abc")))
        assertEquals("No Pokémon numbered “25”.", FilterRowText.emptyDex(DexFilter(query = "#25")))
        assertEquals("No Water Pokémon on your wishlist.", FilterRowText.emptyDex(DexFilter(types = setOf(Type.WATER), wishlistOnly = true)))
        assertEquals("No Pokémon with a favourite copy.", FilterRowText.emptyDex(DexFilter(favouritesOnly = true)))
        assertEquals("No owned Fire or Ice Pokémon in Gen 2 named “x”.", FilterRowText.emptyDex(DexFilter(owned = DexOwned.OWNED, types = setOf(Type.FIRE, Type.ICE), gens = setOf(2), query = " x ")))
        assertEquals("No unowned Pokémon.", FilterRowText.emptyDex(DexFilter(owned = DexOwned.NOT_OWNED)))
        assertEquals("No Pokémon to show.", FilterRowText.emptyDex(DexFilter()))
    }

    // ---- the width plan ----

    private val gap = 6
    private fun plan(avail: Int, sort: Int = 100, active: Int = 0, activeMin: Int = 80, quick: List<Int> = emptyList(), legend: Int = 38, sortMin: Int = 104, lit: List<Boolean> = emptyList()) =
        FilterRowPlan.plan(avail, legend, sort, minOf(sortMin, sort), active, activeMin, quick, gap, lit)

    /** The numbers of a plan (sort width, active width, quick chips shown). */
    private fun FilterRowPlan.Plan.t() = Triple(sortW, activeW, quickShown)

    @Test fun nothingOnShowsAsManyQuickChipsAsFit() {
        // legend 38 + gap + sort 100 = 144; then each quick chip: gap + width
        assertEquals(Triple(100, 0, 2), plan(avail = 336, quick = listOf(90, 60)).t())
        assertEquals(Triple(100, 0, 1), plan(avail = 250, quick = listOf(90, 60)).t())
        assertEquals(Triple(100, 0, 0), plan(avail = 200, quick = listOf(90, 60)).t())
        // in order: a small chip behind one that does not fit stays hidden too
        assertEquals(Triple(100, 0, 0), plan(avail = 200, quick = listOf(90, 20)).t())
        assertEquals(listOf(true, false), plan(avail = 250, quick = listOf(90, 60)).shown)
    }

    @Test fun theActiveChipGetsTheRestAndTheQuickChipsFollowWhileTheyFit() {
        // 144 used; active wants 120 -> 144 + 6 + 120 = 270; one quick chip of 60 fits in 336 (270 + 6 + 60), a second does not
        assertEquals(Triple(100, 120, 1), plan(avail = 336, active = 120, quick = listOf(60, 60)).t())
        // a long label is cut to what is left (never below the minimum)
        assertEquals(Triple(100, 186, 0), plan(avail = 336, active = 300, quick = listOf(60)).t())
        assertEquals(Triple(100, 80, 0), plan(avail = 200, active = 300, quick = listOf(60)).t())
    }

    @Test fun aLitQuickChipIsNeverTheOneThatHides() {
        // 144 used, room for one 90 chip (250) but not two: unlit first chip hides, the lit second one shows
        val p = plan(avail = 250, quick = listOf(90, 90), lit = listOf(false, true))
        assertEquals(listOf(false, true), p.shown)
        // two lit ones and an unlit one in between: both lit stay, the unlit one goes
        val q = plan(avail = 144 + 2 * (gap + 60), quick = listOf(60, 70, 60), lit = listOf(true, false, true))
        assertEquals(listOf(true, false, true), q.shown)
        // a long active label gives way (to its minimum) for a lit chip
        val r = plan(avail = 336, active = 300, quick = listOf(60), lit = listOf(true))
        assertEquals(listOf(true), r.shown)
        assertEquals(336 - 144 - gap - gap - 60, r.activeW)
        // the sort chip shrinks too before a lit chip is hidden
        val s = plan(avail = 336, sort = 262, active = 300, activeMin = 80, quick = listOf(60), lit = listOf(true))
        assertEquals(listOf(true), s.shown)
        assertTrue(38 + gap + s.sortW + gap + s.activeW + gap + 60 <= 336)
    }

    @Test fun theLegendGoesAsALastResortSoNoLitChipIsHidden() {
        // legend 38, sort 73 (its natural width is below the minimum), active min 52, two lit chips 83 + 96: 38+6+73+6+52+6+83+6+96 = 366 > 336
        val p = plan(avail = 336, legend = 38, sort = 73, sortMin = 73, active = 150, activeMin = 52, quick = listOf(83, 96), lit = listOf(true, true))
        assertFalse(p.legendShown)
        assertEquals(listOf(true, true), p.shown)
        assertTrue(73 + gap + p.activeW + gap + 83 + gap + 96 <= 336)
        assertTrue(p.activeW >= 52)
        // it stays while everything fits, and without a lit chip
        assertTrue(plan(avail = 336, legend = 38, sort = 73, sortMin = 73, active = 150, activeMin = 52, quick = listOf(83, 96), lit = listOf(true, false)).legendShown)
        assertTrue(plan(avail = 200, quick = listOf(90, 60)).legendShown)
    }

    @Test fun theSortChipGivesWayBeforeAnActiveFilterIsHidden() {
        // a wide sort chip (262) at 336: the active chip keeps its minimum (80), the sort chip shrinks to what is left
        val p = plan(avail = 336, sort = 262, active = 300, activeMin = 80, quick = listOf(60))
        assertEquals(336 - 38 - gap - gap - 80, p.sortW)
        assertEquals(80, p.activeW)
        assertEquals(0, p.quickShown)
        // the row never exceeds its width
        assertTrue(38 + gap + p.sortW + gap + p.activeW <= 336)
    }

    @Test fun theSortChipNeverShrinksBelowItsMinimum() {
        val p = plan(avail = 200, sort = 262, active = 300, activeMin = 80)
        assertEquals(104, p.sortW)
        assertEquals(80, p.activeW)
    }

    @Test fun withNothingOnAWideSortChipOnlyShrinksWhenItMust() {
        assertEquals(262, plan(avail = 336, sort = 262).sortW)
        assertEquals(336 - 38 - gap, plan(avail = 336, sort = 400).sortW)
    }

    @Test fun everyStateFitsOneLineAt360() {
        val avail = 336 // 360 dp window, 12 dp each side
        for (sort in listOf(90, 130, 262)) for (active in listOf(0, 70, 150, 400)) for (q in listOf(emptyList(), listOf(88), listOf(88, 62), listOf(88, 62, 70))) {
            for (litIdx in listOf(-1, 0, 1)) {
                val lit = q.indices.map { it == litIdx }
                val p = plan(avail, sort = sort, active = active, quick = q, lit = lit)
                var used = (if (p.legendShown) 38 + gap else 0) + p.sortW
                if (p.activeW > 0) used += gap + p.activeW
                for (i in q.indices) if (p.shown[i]) used += gap + q[i]
                assertTrue("sort $sort active $active quick $q lit $litIdx: $used", used <= avail)
                // a lit chip that could fit with the smallest sort / active chip is shown
                if (litIdx in q.indices && 104 + (if (active > 0) gap + 80 else 0) + gap + q[litIdx] <= avail) assertTrue("lit shown: $p", p.shown[litIdx])
            }
        }
    }
}
