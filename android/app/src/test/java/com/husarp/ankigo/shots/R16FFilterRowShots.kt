package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.ActiveChip
import com.husarp.ankigo.ui.BoxFilter
import com.husarp.ankigo.ui.DexFilter
import com.husarp.ankigo.ui.DexOwned
import com.husarp.ankigo.ui.DexPage
import com.husarp.ankigo.ui.EmptyState
import com.husarp.ankigo.ui.FilterRow
import com.husarp.ankigo.ui.SortKey
import com.husarp.ankigo.ui.SortSheet
import com.husarp.ankigo.ui.SortSpec
import com.husarp.ankigo.ui.StatBasis
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TeamSection
import com.husarp.ankigo.ui.TypeFilter
import com.husarp.ankigo.ui.TypeMatch
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * R16-2-F (PLAN 192F2 / 192E, X8 to X10): the Box and the Pokédex have ONE control row that never scrolls ("?" legend, the funnel sort &
 * filter button, the active-filter chip while a filter is on, quick chips that hide when they do not fit), an empty state that says why
 * with "Clear filters", and a type filter that each screen remembers. Shots: r16f-*-w360|w412-light|dark.
 */
class R16FFilterRowShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val st get() = GameRepo.state!!
    private val gd get() = GameRepo.data!!

    private val w360 = Variant("w360", 360, 800)
    private val w412 = Variant("w412", 412, 869)
    private val widths = listOf(w360, w412)

    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) all(v.getChildAt(i), out)
        return out
    }

    private fun texts(v: View) = all(v).filterIsInstance<TextView>().filter { it.visibility == View.VISIBLE }.map { it.text.toString() }
    private fun tap(v: View, text: String) { assertTrue("$text has a click listener", all(v).filterIsInstance<TextView>().first { it.text.toString() == text }.performClick()) }
    private fun maxBody(ctx: Context) = (ctx.resources.displayMetrics.heightPixels * SortSheet.BODY_SHARE).toInt() - Ui.dp(ctx, 150)
    private fun render(p: Any) { call(p, "render") }

    /** The three types most of the fake game's Pokémon have (so the filters pick something that exists). */
    private val top: List<Type> get() = st.mons.flatMap { gd.sp(it.species).types }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    private fun tf(m: TypeMatch, n: Int) = TypeFilter(top.take(n).toCollection(LinkedHashSet()), m)

    private val longSort = SortSpec(key = SortKey.CHOSEN_TOTAL, chosenStats = setOf(com.husarp.ankigo.game.Stat.ATK, com.husarp.ankigo.game.Stat.DEF), basis = StatBasis.AT_LEVEL, level = 50)

    private fun boxPage(ctx: Context, types: TypeFilter = TypeFilter(), show: BoxFilter = BoxFilter.ALL, sort: SortSpec = SortSpec.of(SortKey.LEVEL)): TeamPage {
        st.mon("b3")!!.also { it.favourite = true; it.shiny = true }
        val p = TeamPage(ctx, NoHost)
        p.preset(TeamSection.BOX, filter = show, group = true, sort = sort, types = types)
        return p
    }

    private fun dexPage(ctx: Context, f: DexFilter): DexPage {
        val p = DexPage(ctx, NoHost)
        setField(p, "filter", f)
        return p
    }

    /** Lays [row] out at the width the header gives it (the window minus 12 dp each side). */
    private fun layoutRow(ctx: Context, row: View, windowDp: Int) {
        val w = Ui.dp(ctx, windowDp - 24)
        row.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        row.layout(0, 0, row.measuredWidth, row.measuredHeight)
    }

    private fun boxRow(p: TeamPage) = call(p, "boxControls") as FilterRow
    private fun dexRow(p: DexPage): FilterRow { render(p); return (getField(p, "rowBox") as ViewGroup).getChildAt(0) as FilterRow }

    /** One line, nothing scrolls, nothing outside the row, and an active chip stays in view. */
    private fun assertOneLine(ctx: Context, row: FilterRow, windowDp: Int, what: String) {
        layoutRow(ctx, row, windowDp)
        val w = Ui.dp(ctx, windowDp - 24)
        assertTrue("$what: no scrolling", all(row).none { it is HorizontalScrollView })
        assertEquals("$what: one 48 dp line", Ui.dp(ctx, 48), row.measuredHeight)
        var right = 0
        for (i in 0 until row.childCount) {
            val c = row.getChildAt(i)
            if (c.visibility == View.GONE) continue
            assertTrue("$what: child $i overlaps the one before (${c.left} < $right)", c.left >= right)
            assertTrue("$what: child $i sticks out (${c.right} > $w)", c.right <= w)
            right = c.right
        }
        all(row).filterIsInstance<ActiveChip>().forEach { a ->
            assertTrue("$what: the chip is in view", a.width > 0 && a.right <= w)
        }
    }

    // ------------------------------------------------------------------ the row, in every state, at 360 and 412

    private fun boxStates(): List<Pair<String, (Context) -> TeamPage>> = listOf(
        "none" to { c -> boxPage(c) },
        "two-all" to { c -> boxPage(c, tf(TypeMatch.ALL, 2)) },
        "fav-only" to { c -> boxPage(c, tf(TypeMatch.ONLY, 1), BoxFilter.FAVOURITES) },
        "shiny" to { c -> boxPage(c, TypeFilter(), BoxFilter.SHINY) },
        "long" to { c -> boxPage(c, tf(TypeMatch.ANY, 3), BoxFilter.CAN_EVOLVE, longSort) },
        "team" to { c -> boxPage(c, TypeFilter(), BoxFilter.IN_TEAM) },
        "evolve-long" to { c -> boxPage(c, tf(TypeMatch.ANY, 3), BoxFilter.CAN_EVOLVE, longSort) },
    )

    private fun dexStates(): List<Pair<String, DexFilter>> = listOf(
        "none" to DexFilter(),
        "two-all" to DexFilter(types = top.take(2).toSet(), typeMatch = TypeMatch.ALL),
        "wish-only" to DexFilter(types = top.take(1).toSet(), typeMatch = TypeMatch.ONLY, wishlistOnly = true, gens = setOf(1, 2, 3)),
        "long" to DexFilter(types = top.take(3).toSet(), wishlistOnly = true, gens = setOf(1, 2, 3), sort = com.husarp.ankigo.ui.DexSort.CHOSEN_TOTAL,
            chosenStats = setOf(com.husarp.ankigo.game.Stat.ATK, com.husarp.ankigo.game.Stat.DEF), basis = StatBasis.AT_LEVEL, level = 50),
    )

    @Test fun theBoxRowIsOneLineInEveryState() {
        for (v in widths) paparazzi.shootPage("r16f-box-row-check", listOf(v), themes = listOf(Theme.LIGHT)) { ctx ->
            for ((name, make) in boxStates()) {
                val p = make(ctx)
                assertOneLine(ctx, boxRow(p), v.wDp, "box $name at ${v.wDp}")
            }
            pageScreen(ctx, boxPage(ctx))
        }
    }

    @Test fun theDexRowIsOneLineInEveryState() {
        for (v in widths) paparazzi.shootPage("r16f-dex-row-check", listOf(v), themes = listOf(Theme.LIGHT)) { ctx ->
            for ((name, f) in dexStates()) {
                val p = dexPage(ctx, f); p.root
                assertOneLine(ctx, dexRow(p), v.wDp, "dex $name at ${v.wDp}")
            }
            pageScreen(ctx, dexPage(ctx, DexFilter()))
        }
    }

    @Test fun nothingOnHasNoActiveChipAndFillsTheRowWithQuickChips() {
        paparazzi.shootPage("r16f-row-none", listOf(w360), themes = listOf(Theme.LIGHT)) { ctx ->
            val box = boxRow(boxPage(ctx))
            layoutRow(ctx, box, 360)
            assertTrue("no active chip", all(box).none { it is ActiveChip })
            assertTrue("Favourites and Shiny fit at 360: ${texts(box)}", listOf("?", "Favourites", "Shiny").all { it in texts(box) })
            assertTrue("the funnel sort button", texts(box).any { it.startsWith("Level") })
            val dex = dexRow(dexPage(ctx, DexFilter()).also { it.root })
            layoutRow(ctx, dex, 360)
            assertTrue("no active chip", all(dex).none { it is ActiveChip })
            assertTrue("★ Wishlist quick chip: ${texts(dex)}", "★ Wishlist" in texts(dex))
            pageScreen(ctx, boxPage(ctx))
        }
    }

    @Test fun anActiveFilterIsNeverHiddenQuickChipsHideInstead() {
        paparazzi.shootPage("r16f-row-long", listOf(w360), themes = listOf(Theme.LIGHT)) { ctx ->
            val row = boxRow(boxPage(ctx, tf(TypeMatch.ANY, 3), BoxFilter.CAN_EVOLVE, longSort))
            layoutRow(ctx, row, 360)
            val chip = all(row).filterIsInstance<ActiveChip>().single()
            assertTrue("the chip is in view", chip.width > 0 && chip.right <= Ui.dp(ctx, 336))
            assertTrue("the sort chip still shows its arrow and note", all(row).filterIsInstance<TextView>().any { it.text.toString().startsWith("· Lv 50") })
            // the quick chips (all unlit) hide when there is no room; the filter chip stays
            // a long label is cut with "…" instead of pushing out the chip's end
            assertTrue("the label is cut with an ellipsis", chip.label.layout.getEllipsisCount(0) > 0)
            pageScreen(ctx, boxPage(ctx))
        }
    }

    // ------------------------------------------------------------------ what the row's buttons do

    @Test fun theBoxQuickChipsToggleInPlaceAndTheSheetClearsTheRest() {
        paparazzi.shootPage("r16f-box-actions", listOf(w412), themes = listOf(Theme.LIGHT)) { ctx ->
            val p = boxPage(ctx, tf(TypeMatch.ANY, 1), BoxFilter.FAVOURITES); p.root
            var row = boxRow(p)
            fun chip(t: String) = all(row).filterIsInstance<TextView>().first { it.text.toString() == t }
            assertTrue("Favourites is on: a lit quick chip, still there", chip("Favourites").isSelected)
            assertFalse("Shiny is off", chip("Shiny").isSelected)
            // Shiny while Favourites is on switches to Shiny (Show stays one choice); the type filter stays
            chip("Shiny").performClick()
            assertEquals(BoxFilter.SHINY, getField(p, "boxFilter"))
            assertEquals(tf(TypeMatch.ANY, 1), getField(p, "boxTypes"))
            render(p); row = boxRow(p)
            assertTrue(chip("Shiny").isSelected); assertFalse(chip("Favourites").isSelected)
            // a lit chip tapped again turns Show off
            chip("Shiny").performClick()
            assertEquals(BoxFilter.ALL, getField(p, "boxFilter"))
            // the sheet's Clear turns the sheet filters (the types) off, the sort and the grouping stay
            render(p); row = boxRow(p)
            assertTrue("the chip names the type filter", all(row).any { it is ActiveChip })
            tap(p.sortSheet().build(maxBody(ctx)), "Clear")
            assertEquals(TypeFilter(), getField(p, "boxTypes"))
            assertEquals(SortSpec.of(SortKey.LEVEL), getField(p, "boxSort"))
            assertEquals(true, getField(p, "grouped"))
            render(p); row = boxRow(p)
            assertTrue("no active chip again", all(row).none { it is ActiveChip })
            pageScreen(ctx, boxPage(ctx))
        }
    }

    @Test fun theDexQuickChipsToggleInPlaceAndKeepSearchAndOwned() {
        paparazzi.shootPage("r16f-dex-actions", listOf(w412), themes = listOf(Theme.LIGHT)) { ctx ->
            val p = dexPage(ctx, DexFilter(owned = DexOwned.OWNED, query = "a", types = top.take(1).toSet(), typeMatch = TypeMatch.ONLY, gens = setOf(1, 2))); p.root
            var row = dexRow(p)
            fun chip(t: String) = all(row).filterIsInstance<TextView>().first { it.text.toString() == t }
            assertFalse("★ Wishlist is off", chip("★ Wishlist").isSelected)
            chip("★ Wishlist").performClick()
            assertTrue((getField(p, "filter") as DexFilter).wishlistOnly)
            row = dexRow(p)
            assertTrue("it stays where it is, lit", chip("★ Wishlist").isSelected)
            val words = all(row).filterIsInstance<ActiveChip>().single().contentDescription.toString()
            assertTrue("and is not repeated in the filter chip: $words", "Wishlist" !in words)
            chip("★ Wishlist").performClick()
            assertFalse((getField(p, "filter") as DexFilter).wishlistOnly)
            // the sheet's Clear: types and generations off; the search and All / Owned / Not owned stay
            tap(p.sortSheet().build(maxBody(ctx)), "Clear")
            val f = getField(p, "filter") as DexFilter
            assertFalse(f.wishlistOnly); assertTrue(f.gens.isEmpty()); assertEquals(TypeFilter(), f.typeFilter)
            assertEquals("All / Owned / Not owned stays", DexOwned.OWNED, f.owned)
            assertEquals("the search stays", "a", f.query)
            pageScreen(ctx, dexPage(ctx, DexFilter()))
        }
    }

    // ------------------------------------------------------------------ remembered per screen (X8)

    @Test fun aSavedTypeFilterComesBackWhenThePageIsRebuilt() {
        paparazzi.shootPage("r16f-saved", listOf(w412), themes = listOf(Theme.LIGHT)) { ctx ->
            val box = TeamPage(ctx, NoHost); box.root
            val boxSheet = box.sortSheet().build(maxBody(ctx))
            tap(boxSheet, "Only"); tap(boxSheet, "Water"); tap(boxSheet, "Ice")
            val dex = DexPage(ctx, NoHost); dex.root
            val dexSheet = dex.sortSheet().build(maxBody(ctx))
            tap(dexSheet, "All of"); tap(dexSheet, "Fire")
            // new page instances: each screen has its own type filter back (R18 249 keeps the other filters too: see R18FFilterShots)
            val box2 = TeamPage(ctx, NoHost)
            assertEquals(TypeFilter(linkedSetOf(Type.WATER, Type.ICE), TypeMatch.ONLY), getField(box2, "boxTypes"))
            assertEquals(BoxFilter.ALL, getField(box2, "boxFilter"))
            val dex2 = DexPage(ctx, NoHost)
            assertEquals(TypeFilter(linkedSetOf(Type.FIRE), TypeMatch.ALL), (getField(dex2, "filter") as DexFilter).typeFilter)
            // clearing in the sheet is remembered too
            tap(boxSheet, "Clear")
            assertEquals(TypeFilter(), getField(TeamPage(ctx, NoHost), "boxTypes"))
            assertEquals(TypeFilter(linkedSetOf(Type.FIRE), TypeMatch.ALL), (getField(DexPage(ctx, NoHost), "filter") as DexFilter).typeFilter)
            // and the rebuilt Box shows the active chip right away
            box2.root
            pageScreen(ctx, boxPage(ctx, TypeFilter(linkedSetOf(Type.WATER), TypeMatch.ONLY)))
        }
    }

    // ------------------------------------------------------------------ empty states

    private fun pureTypeNoOneHas(): Type = Type.entries.first { t -> st.mons.none { gd.sp(it.species).types == listOf(t) } }

    @Test fun anEmptyBoxSaysWhyAndClearFiltersBringsEverythingBack() {
        for (v in widths) paparazzi.shootPage("r16f-box-empty", listOf(v)) { ctx ->
            val t = pureTypeNoOneHas()
            val p = boxPage(ctx, TypeFilter(linkedSetOf(t), TypeMatch.ONLY)); pageScreen(ctx, p)
            render(p)
            fun emptyState(q: TeamPage) = all(getField(q, "boxHeader") as View).filterIsInstance<EmptyState>().single() // the list's header is attached only after a layout pass
            val empty = emptyState(p)
            assertEquals("No pure ${t.label} Pokémon in your box.", empty.message.text.toString())
            assertEquals(View.VISIBLE, empty.clear.visibility)
            assertTrue("48 dp touch", empty.clear.minHeight >= Ui.dp(ctx, 48))
            empty.clear.performClick()
            assertEquals(TypeFilter(), getField(p, "boxTypes"))
            assertEquals(BoxFilter.ALL, getField(p, "boxFilter"))
            // the screen to shoot: an empty result with its button
            pageScreen(ctx, boxPage(ctx, TypeFilter(linkedSetOf(t), TypeMatch.ONLY), BoxFilter.FAVOURITES))
        }
    }

    @Test fun anEmptyDexSaysWhyAndClearFiltersAlsoResetsOwnedAndTheSearch() {
        for (v in widths) paparazzi.shootPage("r16f-dex-empty", listOf(v)) { ctx ->
            val t = pureTypeNoOneHas()
            val p = dexPage(ctx, DexFilter(owned = DexOwned.OWNED, query = "zzzz", types = setOf(t), typeMatch = TypeMatch.ONLY, gens = setOf(1, 2, 3)))
            pageScreen(ctx, p); render(p)
            val empty = getField(p, "emptyView") as EmptyState
            assertEquals("No owned pure ${t.label} Pokémon in Gen 1–3 named “zzzz”.", empty.message.text.toString())
            assertEquals(View.VISIBLE, empty.clear.visibility)
            empty.clear.performClick()
            val f = getField(p, "filter") as DexFilter
            assertFalse(f.active)
            assertEquals("", (getField(p, "search") as android.widget.EditText).text.toString())
            assertEquals(View.GONE, empty.visibility)
            val q = dexPage(ctx, DexFilter(query = "xyzq", types = setOf(t), typeMatch = TypeMatch.ONLY, gens = setOf(1, 2, 3)))
            pageScreen(ctx, q).also { render(q) }
        }
    }

    // ------------------------------------------------------------------ shots

    private fun shootBox(name: String, make: (Context) -> TeamPage) {
        for (v in widths) paparazzi.shootPage(name, listOf(v)) { ctx -> pageScreen(ctx, make(ctx)) }
    }

    private fun shootDex(name: String, f: () -> DexFilter) {
        for (v in widths) paparazzi.shootPage(name, listOf(v)) { ctx -> val p = dexPage(ctx, f()); pageScreen(ctx, p).also { render(p) } }
    }

    @Test fun boxNothingOn() = shootBox("r16f-box-none") { boxPage(it) }
    @Test fun boxTwoTypesAllOf() = shootBox("r16f-box-twoall") { boxPage(it, tf(TypeMatch.ALL, 2)) }
    @Test fun boxFavouritesOnlyOneType() = shootBox("r16f-box-favonly") { boxPage(it, tf(TypeMatch.ONLY, 1), BoxFilter.FAVOURITES) }
    @Test fun boxLongLabel() = shootBox("r16f-box-long") { boxPage(it, tf(TypeMatch.ANY, 3), BoxFilter.FAVOURITES, longSort) }
    @Test fun dexNothingOn() = shootDex("r16f-dex-none") { DexFilter() }
    @Test fun dexTwoTypesAllOf() = shootDex("r16f-dex-twoall") { DexFilter(types = top.take(2).toSet(), typeMatch = TypeMatch.ALL) }
    @Test fun dexWishlistOnlyOneType() = shootDex("r16f-dex-wishonly") { DexFilter(types = top.take(1).toSet(), typeMatch = TypeMatch.ONLY, wishlistOnly = true, gens = setOf(1, 2, 3)) }
    @Test fun dexLongLabel() = shootDex("r16f-dex-long") {
        DexFilter(types = top.take(3).toSet(), wishlistOnly = true, gens = setOf(1, 2, 3), sort = com.husarp.ankigo.ui.DexSort.CHOSEN_TOTAL,
            chosenStats = setOf(com.husarp.ankigo.game.Stat.ATK, com.husarp.ankigo.game.Stat.DEF), basis = StatBasis.AT_LEVEL, level = 50)
    }
}
