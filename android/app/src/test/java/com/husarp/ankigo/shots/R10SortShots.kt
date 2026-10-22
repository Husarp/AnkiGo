package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.AppMenu
import com.husarp.ankigo.ui.BoxFilter
import com.husarp.ankigo.ui.DexFilter
import com.husarp.ankigo.ui.DexPage
import com.husarp.ankigo.ui.GamePage
import com.husarp.ankigo.ui.MonActions
import com.husarp.ankigo.ui.SortSheet
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TeamSection
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * R10-D (B3, B5, B14): the Pokédex header has only the two rows of type chips (Wishlist + Gen moved into the sort sheet), the
 * sort sheet holds "Only wishlist" + the generations, a long-press on the hero card opens the Pokémon options menu, and the Box
 * has one sort/filter button (no "Filters" chip) whose sheet holds direction, sort, Show and Group duplicates. Shots: r10sort-*.
 */
class R10SortShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val FLOAT = Variant("float", Size.FLOAT.wDp, Size.FLOAT.hDp)
    private val st get() = GameRepo.state!!
    private val gd get() = GameRepo.data!!

    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) all(v.getChildAt(i), out)
        return out
    }

    /** The texts that are visible (a GONE view hides its whole subtree). */
    private fun texts(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v.visibility == View.GONE) return out
        if (v is TextView) out += v.text.toString()
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    private fun tap(v: View, text: String) {
        val t = all(v).filterIsInstance<TextView>().first { it.text.toString() == text }
        assertTrue("$text has a click listener", t.performClick())
    }

    private fun render(p: GamePage) {
        GamePage::class.java.getDeclaredMethod("render").apply { isAccessible = true }.invoke(p)
    }

    private fun dexPage(ctx: Context, f: DexFilter, v: Variant? = null, open: Boolean = true): Pair<DexPage, View> {
        val p = DexPage(ctx, NoHost)
        setField(p, "filter", f)
        val screen = if (v === FLOAT) p.root.also { p.onShow() } else pageScreen(ctx, p)
        setField(p, "filtersOpen", open)
        render(p)
        return p to screen
    }

    private fun sheetOver(ctx: Context, screen: View, sheet: View) = FrameLayout(ctx).apply {
        addView(screen, FrameLayout.LayoutParams(-1, -1))
        addView(View(ctx).apply { setBackgroundColor(Color.argb(122, 0, 0, 0)) }, FrameLayout.LayoutParams(-1, -1))
        addView(sheet, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    }

    private fun maxBody(ctx: Context) = (ctx.resources.displayMetrics.heightPixels * SortSheet.BODY_SHARE).toInt() - Ui.dp(ctx, 150)

    // ------------------------------------------------------------------ B3

    @Test fun dexHeaderHasOnlyTwoRowsOfTypeChips() {
        for (v in listOf(Variants.PHONE, Variants.SPLIT, FLOAT)) paparazzi.shootPage("r10sort-dex-header", listOf(v)) { ctx ->
            val (p, screen) = dexPage(ctx, DexFilter(), v)
            val chips = getField(p, "chipsBox") as LinearLayout
            val t = texts(chips)
            assertFalse("no Wishlist chip", "Wishlist" in t)
            assertTrue("no Gen chips: $t", t.none { it.startsWith("Gen ") })
            // PLAN 192: the Any of / All of switch comes first, then the one scroller for both rows
            assertEquals("Any of / All of", listOf("Any of", "All of"), texts(chips.getChildAt(0)))
            val scroll = chips.getChildAt(1) as HorizontalScrollView
            assertEquals("the switch and one scroller for both rows", 2, chips.childCount)
            val rows = scroll.getChildAt(0) as LinearLayout
            assertEquals("exactly two rows", 2, rows.childCount)
            val counts = (0 until 2).map { (rows.getChildAt(it) as LinearLayout).childCount }
            assertEquals(Type.entries.size, counts.sum())
            assertTrue("about half each: $counts", Math.abs(counts[0] - counts[1]) <= 1)
            screen
        }
    }

    @Test fun sortChipShowsHiddenFilters() {
        paparazzi.shootPage("r10sort-dex-chip", listOf(Variants.PHONE)) { ctx ->
            val (p, screen) = dexPage(ctx, DexFilter(wishlistOnly = true, gens = setOf(1, 2)))
            assertTrue("chip says 2 filters are on: ${texts(getField(p, "sortPill") as View)}", "· 2" in texts(getField(p, "sortPill") as View))
            val (_, _) = dexPage(ctx, DexFilter())
            screen
        }
        paparazzi.shootPage("r10sort-dex-chip-off", listOf(Variants.PHONE)) { ctx ->
            val (p, screen) = dexPage(ctx, DexFilter())
            assertTrue("no count while nothing is on", texts(getField(p, "sortPill") as View).none { it.startsWith("·") })
            screen
        }
    }

    @Test fun dexSheetHasTheShowPart() {
        for (v in listOf(Variants.PHONE, Variants.SPLIT)) paparazzi.shootPage("r10sort-dex-sheet", listOf(v)) { ctx ->
            val (p, screen) = dexPage(ctx, DexFilter(gens = setOf(2)))
            val sheet = p.sortSheet().build(maxBody(ctx))
            val t = texts(sheet)
            assertTrue("title: $t", "Sort & filter" in t)
            assertTrue("Only wishlist" in t); assertTrue("GENERATION" in t); assertTrue("SHOW" in t)
            assertTrue("Gen 1" in t && "Gen 2" in t)
            assertTrue("DIRECTION" in t)
            val shown = (getField(p, "shown") as List<*>).size
            assertEquals("the button counts the grid", "Show $shown Pokémon", t.first { it.startsWith("Show ") && it.endsWith("Pokémon") })
            sheetOver(ctx, screen, sheet)
        }
    }

    @Test fun tickingAGenerationFiltersTheGrid() {
        paparazzi.shootPage("r10sort-dex-gen", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val (p, screen) = dexPage(ctx, DexFilter())
            val all = (getField(p, "shown") as List<*>).size
            val sheet = p.sortSheet().build(maxBody(ctx))
            tap(sheet, "Gen 2")
            assertEquals(setOf(2), (getField(p, "filter") as DexFilter).gens)
            val gen2 = gd.species.values.count { it.gen == 2 }
            assertTrue(gen2 in 1 until all)
            assertEquals("Show $gen2 Pokémon", texts(sheet).first { it.startsWith("Show ") && it.endsWith("Pokémon") })
            val sw = all(sheet).filterIsInstance<android.widget.Switch>().single()
            sw.isChecked = true
            val f = getField(p, "filter") as DexFilter
            assertTrue(f.wishlistOnly)
            tap(sheet, "Reset")
            val r = getField(p, "filter") as DexFilter
            assertFalse(r.wishlistOnly); assertTrue(r.gens.isEmpty())
            assertEquals("Show $all Pokémon", texts(sheet).first { it.startsWith("Show ") && it.endsWith("Pokémon") })
            sheetOver(ctx, screen, sheet)
        }
    }

    // ------------------------------------------------------------------ B5

    private fun menuRows(m: AppMenu?) = requireNotNull(m) { "no menu" }.describe()

    @Test fun longPressOnTheHeroCardOpensTheTeamRowMenu() {
        paparazzi.shootPage("r10sort-dex-hero", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val (p, screen) = dexPage(ctx, DexFilter())
            val mon = st.mons.maxByOrNull { it.caughtAt }!!
            val hero = getField(p, "heroView") as View
            val m = MenuKit.capture { assertTrue(hero.performLongClick()) }
            val expected = MenuKit.capture { MonActions(TeamPage(ctx, NoHost)).menu(mon, View(ctx)) }
            assertEquals(menuRows(expected), menuRows(m))
            // the Pokémon is looked up at press time: once it is gone, nothing opens
            st.mons.remove(mon)
            assertNull(MenuKit.capture { hero.performLongClick() })
            screen
        }
    }

    // ------------------------------------------------------------------ B14

    private fun boxPage(ctx: Context, v: Variant, pick: Boolean = false): Pair<TeamPage, View> {
        st.mon("b3")!!.also { it.shiny = true }
        val p = TeamPage(ctx, NoHost)
        p.preset(TeamSection.BOX, group = true, pick = if (pick) emptyList() else null)
        val screen = if (v === FLOAT) p.root.also { p.onShow() } else pageScreen(ctx, p)
        render(p)
        return p to screen
    }

    @Test fun boxHasOneSortFilterButton() {
        for (v in listOf(Variants.PHONE, Variants.SPLIT, FLOAT)) paparazzi.shootPage("r10sort-box-row", listOf(v)) { ctx ->
            val (p, screen) = boxPage(ctx, v)
            // the Box list is a ListView (its header is only attached after a layout pass): read the chip row itself
            val row = TeamPage::class.java.getDeclaredMethod("boxControls").apply { isAccessible = true }.invoke(p) as View
            val t = texts(row)
            assertFalse("no separate Filters chip: $t", "Filters" in t)
            assertTrue("quick chips stay: $t", "Favourites" in t && "Shiny" in t)
            screen
        }
    }

    @Test fun boxSheetHoldsEverything() {
        for (v in listOf(Variants.PHONE, Variants.SPLIT)) paparazzi.shootPage("r10sort-box-sheet", listOf(v)) { ctx ->
            val (p, screen) = boxPage(ctx, v)
            val sheet = p.sortSheet().build(maxBody(ctx))
            val t = texts(sheet).map { it.uppercase() }
            for (need in listOf("DIRECTION", "SORT BY", "SHOW", "↑ ASCENDING", "↓ DESCENDING", "FAVOURITES", "GROUP DUPLICATES")) assertTrue("$need in $t", need in t)
            sheetOver(ctx, screen, sheet)
        }
    }

    @Test fun boxSheetWhilePickingHasNoGroupSwitch() {
        paparazzi.shootPage("r10sort-box-sheet-pick", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val (p, screen) = boxPage(ctx, Variants.PHONE, pick = true)
            val sheet = p.sortSheet().build(maxBody(ctx))
            assertTrue("SHOW" in texts(sheet))
            assertFalse("GROUP DUPLICATES" in texts(sheet).map { it.uppercase() })
            screen
        }
    }

    @Test fun showInTheBoxSheetFiltersTheBox() {
        paparazzi.shootPage("r10sort-box-show", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val (p, screen) = boxPage(ctx, Variants.PHONE)
            val sheet = p.sortSheet().build(maxBody(ctx))
            val shiny = st.mons.count { it.shiny }
            assertTrue(shiny >= 1)
            tap(sheet, "Shiny")
            assertEquals(BoxFilter.SHINY, getField(p, "boxFilter"))
            assertEquals("Show $shiny Pokémon", texts(sheet).first { it.startsWith("Show ") && it.endsWith("Pokémon") })
            sheetOver(ctx, screen, sheet)
        }
    }
}
