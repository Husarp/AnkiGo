package com.husarp.ankigo.shots

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.ActiveChip
import com.husarp.ankigo.ui.BoxFilter
import com.husarp.ankigo.ui.DexFilter
import com.husarp.ankigo.ui.DexOwned
import com.husarp.ankigo.ui.DexPage
import com.husarp.ankigo.ui.FilterRow
import com.husarp.ankigo.ui.SortKey
import com.husarp.ankigo.ui.SortSheet
import com.husarp.ankigo.ui.SortSpec
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
 * R18-F (PLAN 249, 253): the Pokédex and Box filters are kept per screen (rebuilding the page brings them back, the search text starts
 * empty); the quick chips toggle in place (lit while on), Favourites is new in the Pokédex, the Box has Favourites / Shiny / In team, and
 * the "Filters" chip no longer has a clear button. Shots: r18f-dex-rows, r18f-box-rows, r18f-dex-page, r18f-box-page (-light / -dark).
 */
class R18FFilterShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val st get() = GameRepo.state!!
    private val gd get() = GameRepo.data!!
    private val w360 = Variant("w360", 360, 800)
    private val w412 = Variant("w412", 412, 869)

    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) all(v.getChildAt(i), out)
        return out
    }

    private fun chips(v: View) = all(v).filterIsInstance<TextView>().filter { it.visibility == View.VISIBLE }
    private fun chip(v: View, text: String) = chips(v).first { it.text.toString() == text }
    private fun render(p: Any) { call(p, "render") }
    private fun maxBody(ctx: Context) = (ctx.resources.displayMetrics.heightPixels * SortSheet.BODY_SHARE).toInt() - Ui.dp(ctx, 150)
    private fun dexRow(p: DexPage): FilterRow { render(p); return (getField(p, "rowBox") as ViewGroup).getChildAt(0) as FilterRow }
    private fun boxRow(p: TeamPage) = call(p, "boxControls") as FilterRow
    private fun dexFilter(p: DexPage) = getField(p, "filter") as DexFilter

    private val top: List<Type> get() = st.mons.flatMap { gd.sp(it.species).types }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

    // ------------------------------------------------------------------ 253: the quick chips toggle in place

    @Test fun theDexQuickChipsToggleInPlaceAndFavouritesFiltersTheGrid() {
        paparazzi.shootPage("r18f-dex-toggle", listOf(w360), themes = listOf(Theme.LIGHT)) { ctx ->
            val p = DexPage(ctx, NoHost); p.root
            var row = dexRow(p)
            assertEquals("order: ★ Wishlist, then Favourites", listOf("★ Wishlist", "Favourites"), chips(row).map { it.text.toString() }.filter { it == "★ Wishlist" || it == "Favourites" })
            assertTrue("no ✕ anywhere", all(row).none { it is android.widget.ImageView && it.contentDescription == "Clear all filters" })
            assertFalse(chip(row, "Favourites").isSelected); assertEquals("Favourites, off", chip(row, "Favourites").contentDescription.toString())
            val before = (getField(p, "shown") as List<*>).size
            // lit, in the same place; the grid keeps only species with a favourite copy of yours
            st.mons.first().favourite = true
            chip(row, "Favourites").performClick()
            assertTrue(dexFilter(p).favouritesOnly)
            row = dexRow(p)
            assertTrue(chip(row, "Favourites").isSelected); assertEquals("Favourites, on", chip(row, "Favourites").contentDescription.toString())
            val favSpecies = st.mons.filter { it.favourite }.map { it.species }.toSet()
            assertEquals(favSpecies, (getField(p, "shown") as List<*>).map { (it as com.husarp.ankigo.game.Species).num }.toSet())
            assertTrue((getField(p, "shown") as List<*>).size < before)
            assertTrue("a lit quick chip is no 'Filters' chip", all(row).none { it is ActiveChip })
            // both lit; a second tap turns each off
            chip(row, "★ Wishlist").performClick()
            row = dexRow(p)
            assertTrue(chip(row, "★ Wishlist").isSelected && chip(row, "Favourites").isSelected)
            chip(row, "Favourites").performClick(); row = dexRow(p)
            chip(row, "★ Wishlist").performClick(); row = dexRow(p)
            assertFalse(dexFilter(p).favouritesOnly); assertFalse(dexFilter(p).wishlistOnly)
            assertFalse(chip(row, "Favourites").isSelected || chip(row, "★ Wishlist").isSelected)
            assertEquals(before, (getField(p, "shown") as List<*>).size)
            View(ctx)
        }
    }

    @Test fun theFiltersChipShowsOnlySheetFiltersHasNoClearButtonAndTheSheetClearsThem() {
        paparazzi.shootPage("r18f-dex-chip", listOf(w412), themes = listOf(Theme.LIGHT)) { ctx ->
            val p = DexPage(ctx, NoHost); p.root
            setField(p, "filter", DexFilter(wishlistOnly = true, favouritesOnly = true, types = top.take(1).toSet(), typeMatch = TypeMatch.ONLY, gens = setOf(1, 2)))
            val row = dexRow(p)
            val chipView = all(row).filterIsInstance<ActiveChip>().single()
            assertEquals(0, all(chipView).filterIsInstance<android.widget.ImageView>().size)
            val words = chipView.contentDescription.toString()
            assertTrue(words, "Gen 1–2" in words && "Only" in words && "Wishlist" !in words && "Favourites" !in words)
            assertTrue("lit chips stay", chip(row, "★ Wishlist").isSelected && chip(row, "Favourites").isSelected)
            // the sheet's Clear: types and generations (and the wishlist switch it holds); the owner and the search stay
            setField(p, "filter", dexFilter(p).copy(owned = DexOwned.OWNED, query = "a"))
            tap(p.sortSheet().build(maxBody(ctx)), "Clear")
            val f = dexFilter(p)
            assertEquals(TypeFilter(), f.typeFilter); assertTrue(f.gens.isEmpty()); assertFalse(f.wishlistOnly)
            assertEquals(DexOwned.OWNED, f.owned); assertEquals("a", f.query)
            assertTrue("Favourites is not in the sheet, so Clear keeps it", f.favouritesOnly)
            View(ctx)
        }
    }

    private fun tap(v: View, text: String) { assertTrue("$text has a click listener", all(v).filterIsInstance<TextView>().first { it.text.toString() == text }.performClick()) }

    @Test fun theBoxQuickChipsAreOneChoiceAndTapAgainTurnsItOff() {
        paparazzi.shootPage("r18f-box-toggle", listOf(w412), themes = listOf(Theme.LIGHT)) { ctx ->
            val p = TeamPage(ctx, NoHost)
            p.preset(TeamSection.BOX, filter = BoxFilter.ALL, group = true, sort = SortSpec.of(SortKey.LEVEL), types = TypeFilter())
            p.root
            var row = boxRow(p)
            assertEquals(listOf("Favourites", "Shiny", "In team"), chips(row).map { it.text.toString() }.filter { it in setOf("Favourites", "Shiny", "In team") })
            assertTrue("none lit", listOf("Favourites", "Shiny", "In team").none { chip(row, it).isSelected })
            chip(row, "In team").performClick()
            assertEquals(BoxFilter.IN_TEAM, getField(p, "boxFilter")); row = boxRow(p)
            assertTrue(chip(row, "In team").isSelected); assertEquals("In team, on", chip(row, "In team").contentDescription.toString())
            assertTrue("no filters chip: In team is the lit chip", all(row).none { it is ActiveChip })
            chip(row, "Shiny").performClick() // another one switches, the first goes unlit
            assertEquals(BoxFilter.SHINY, getField(p, "boxFilter")); row = boxRow(p)
            assertTrue(chip(row, "Shiny").isSelected && !chip(row, "In team").isSelected)
            chip(row, "Shiny").performClick() // tap again = off
            assertEquals(BoxFilter.ALL, getField(p, "boxFilter")); row = boxRow(p)
            assertTrue(listOf("Favourites", "Shiny", "In team").none { chip(row, it).isSelected })
            View(ctx)
        }
    }

    @Test fun aSheetOnlyShowChoiceIsNamedInTheFiltersChipAndClearTurnsItOff() {
        paparazzi.shootPage("r18f-box-sheetonly", listOf(w412), themes = listOf(Theme.LIGHT)) { ctx ->
            val p = TeamPage(ctx, NoHost); p.root
            tap(p.sortSheet().build(maxBody(ctx)), "Can evolve now")
            assertEquals(BoxFilter.CAN_EVOLVE, getField(p, "boxFilter"))
            val row = boxRow(p)
            assertTrue("Can evolve now" in all(row).filterIsInstance<ActiveChip>().single().contentDescription.toString())
            assertTrue("no quick chip is lit", listOf("Favourites", "Shiny", "In team").none { chip(row, it).isSelected })
            tap(p.sortSheet().build(maxBody(ctx)), "Clear")
            assertEquals(BoxFilter.ALL, getField(p, "boxFilter"))
            View(ctx)
        }
    }

    // ------------------------------------------------------------------ 249: kept across rebuilds (a restart / tab switch builds the page again)

    @Test fun theDexFiltersComeBackWhenThePageIsBuiltAgainButNotTheSearch() {
        paparazzi.shootPage("r18f-dex-kept", listOf(w412), themes = listOf(Theme.LIGHT)) { ctx ->
            val p = DexPage(ctx, NoHost); p.root
            val seg = getField(p, "segBox") as ViewGroup
            render(p)
            chip(seg, "Owned").performClick()
            var row = dexRow(p)
            chip(row, "★ Wishlist").performClick(); row = dexRow(p)
            chip(row, "Favourites").performClick()
            val gens = st.settings.gens.sorted()
            if (gens.size >= 2) tap(p.sortSheet().build(maxBody(ctx)), "Gen ${gens[1]}")
            tap(p.sortSheet().build(maxBody(ctx)), "Water")
            (getField(p, "search") as EditText).setText("pik")
            val f1 = dexFilter(p)
            assertEquals("pik", f1.query)
            val p2 = DexPage(ctx, NoHost); p2.root
            val f2 = dexFilter(p2)
            assertEquals(DexOwned.OWNED, f2.owned)
            assertTrue(f2.wishlistOnly); assertTrue(f2.favouritesOnly)
            assertEquals(f1.gens, f2.gens)
            assertEquals(f1.typeFilter, f2.typeFilter)
            assertEquals("the search always starts empty", "", f2.query)
            // the lit chips are drawn lit after the rebuild
            val row2 = dexRow(p2)
            assertTrue(chip(row2, "★ Wishlist").isSelected && chip(row2, "Favourites").isSelected)
            // a tab tapped again goes to the top and keeps the filters (item 222)
            p2.onTabReselected()
            assertTrue(dexFilter(p2).favouritesOnly && dexFilter(p2).owned == DexOwned.OWNED)
            // the empty state's "Clear filters" turns everything off, and that is kept too
            call(p2, "clearFilters")
            val f3 = dexFilter(DexPage(ctx, NoHost))
            assertFalse(f3.active)
            View(ctx)
        }
    }

    @Test fun theBoxShowComesBackWhenThePageIsBuiltAgain() {
        paparazzi.shootPage("r18f-box-kept", listOf(w412), themes = listOf(Theme.LIGHT)) { ctx ->
            val p = TeamPage(ctx, NoHost); p.root
            chip(boxRow(p), "Shiny").performClick()
            val p2 = TeamPage(ctx, NoHost)
            assertEquals(BoxFilter.SHINY, getField(p2, "boxFilter"))
            p2.root
            assertTrue(chip(boxRow(p2), "Shiny").isSelected)
            p2.onTabReselected() // item 222 on the Box: back to the top, same filters
            assertEquals(BoxFilter.SHINY, getField(p2, "boxFilter"))
            // sheet-only choices are kept too; Clear in the sheet is kept
            tap(p2.sortSheet().build(maxBody(ctx)), "Holds an item")
            assertEquals(BoxFilter.HOLDS_ITEM, getField(TeamPage(ctx, NoHost), "boxFilter"))
            tap(p2.sortSheet().build(maxBody(ctx)), "Clear")
            assertEquals(BoxFilter.ALL, getField(TeamPage(ctx, NoHost), "boxFilter"))
            View(ctx)
        }
    }

    // ------------------------------------------------------------------ shots

    private fun caption(ctx: Context, t: String) = Ui.text(ctx, 10f, 700, R.color.ag_sub0).apply { text = t; setPadding(Ui.dp(ctx, 2), Ui.dp(ctx, 8), 0, 0) }

    private fun strip(ctx: Context, rows: List<Pair<String, View>>): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.TOP
        setBackgroundColor(Ui.c(ctx, R.color.ag_base))
        setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 16), Ui.dp(ctx, 12), 0)
        for ((c, r) in rows) {
            addView(caption(ctx, c))
            (r.parent as? ViewGroup)?.removeView(r)
            addView(r, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun dexStates(ctx: Context): List<Pair<String, DexFilter>> = listOf(
        "Pokédex: nothing on" to DexFilter(),
        "Wishlist on" to DexFilter(wishlistOnly = true),
        "Wishlist and Favourites on, types and generations in the Filters chip" to DexFilter(wishlistOnly = true, favouritesOnly = true, types = top.take(2).toSet(), typeMatch = TypeMatch.ALL, gens = setOf(1, 2, 3)),
        "Favourites on, one type" to DexFilter(favouritesOnly = true, types = top.take(1).toSet(), typeMatch = TypeMatch.ONLY),
    )

    @Test fun dexRows() {
        for (v in listOf(w360, w412)) paparazzi.shootPage("r18f-dex-rows", listOf(v)) { ctx ->
            strip(ctx, dexStates(ctx).map { (c, f) ->
                val p = DexPage(ctx, NoHost); p.root; setField(p, "filter", f)
                c to dexRow(p)
            })
        }
    }

    @Test fun boxRows() {
        for (v in listOf(w360, w412)) paparazzi.shootPage("r18f-box-rows", listOf(v)) { ctx ->
            fun page(show: BoxFilter, types: TypeFilter = TypeFilter()) = TeamPage(ctx, NoHost).also {
                it.preset(TeamSection.BOX, filter = show, group = true, sort = SortSpec.of(SortKey.LEVEL), types = types); it.root
            }
            strip(ctx, listOf(
                "Box: nothing on" to boxRow(page(BoxFilter.ALL)),
                "Favourites on" to boxRow(page(BoxFilter.FAVOURITES)),
                "Shiny on, one type in the Filters chip" to boxRow(page(BoxFilter.SHINY, TypeFilter(top.take(1).toCollection(LinkedHashSet()), TypeMatch.ONLY))),
                "In team on" to boxRow(page(BoxFilter.IN_TEAM)),
                "Can evolve now (sheet only) in the Filters chip" to boxRow(page(BoxFilter.CAN_EVOLVE)),
            ))
        }
    }

    @Test fun dexPage() {
        paparazzi.shootPage("r18f-dex-page", listOf(w412)) { ctx ->
            val p = DexPage(ctx, NoHost); p.root
            setField(p, "filter", DexFilter(wishlistOnly = true, favouritesOnly = false, types = top.take(1).toSet(), gens = setOf(1, 2)))
            st.mons.first().favourite = true
            pageScreen(ctx, p).also { render(p) }
        }
    }

    @Test fun boxPage() {
        paparazzi.shootPage("r18f-box-page", listOf(w412)) { ctx ->
            st.mon("b3")?.also { it.favourite = true }
            val p = TeamPage(ctx, NoHost)
            p.preset(TeamSection.BOX, filter = BoxFilter.FAVOURITES, group = true, sort = SortSpec.of(SortKey.LEVEL), types = TypeFilter(top.take(1).toCollection(LinkedHashSet()), TypeMatch.ANY))
            pageScreen(ctx, p)
        }
    }
}
