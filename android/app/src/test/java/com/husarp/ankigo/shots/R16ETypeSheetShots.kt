package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.BoxFilter
import com.husarp.ankigo.ui.BoxTiles
import com.husarp.ankigo.ui.SortKey
import com.husarp.ankigo.ui.SortSheet
import com.husarp.ankigo.ui.SortSpec
import com.husarp.ankigo.ui.SortText
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
 * R16-2-E (PLAN 192A + M1): the "Sort & filter" sheet with its Type section (the Any of / All of / Only switch, one plain line, the 18
 * type chips) and the "Clear" button in its head. PNGs: r16e-*-w360|w412-light|dark.
 */
class R16ETypeSheetShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val st get() = GameRepo.state!!
    private val gd get() = GameRepo.data!!

    // tall windows so the whole sheet shows (the real one scrolls inside 90 % of the screen)
    private val w360 = Variant("w360", 360, 1500)
    private val w412 = Variant("w412", 412, 1500)
    private val widths = listOf(w360, w412)

    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) all(v.getChildAt(i), out)
        return out
    }

    private fun texts(v: View) = all(v).filterIsInstance<TextView>().map { it.text.toString() }
    private fun tv(v: View, text: String) = all(v).filterIsInstance<TextView>().first { it.text.toString() == text }
    private fun tap(v: View, text: String) { assertTrue("$text has a click listener", tv(v, text).performClick()) }
    private fun sentence(v: View) = texts(v).first { it.startsWith("No type picked") || it.startsWith("Has ") || it.startsWith("Pure ") || it.startsWith("No type outside") }

    /** What the host keeps: the sort, the Show index, the type filter, the group switch. */
    private class Host(var spec: SortSpec = SortSpec.of(SortKey.LEVEL), var show: Int = 0, var types: TypeFilter = TypeFilter(), var grouped: Boolean = true) {
        var typeCalls = 0
    }

    private fun boxSheet(ctx: Context, p: TeamPage, h: Host): SortSheet = SortSheet(
        p, SortText.boxKeys, h.spec, false, BoxFilter.entries.map { it.label }, h.show,
        SortSheet.GroupToggle(h.grouped) { h.grouped = it },
        count = { BoxTiles.filter(st.mons, BoxFilter.entries[h.show], types = h.types, typesOf = { gd.sp(it.species).types }).size },
        onChange = { s, i -> h.spec = s; h.show = i },
        type = SortSheet.TypePart(h.types) { h.types = it; h.typeCalls++ },
    )

    private fun maxBody(ctx: Context) = (ctx.resources.displayMetrics.heightPixels * SortSheet.BODY_SHARE).toInt() - Ui.dp(ctx, 150)

    private fun over(ctx: Context, screen: View, sheet: View) = FrameLayout(ctx).apply {
        addView(screen, FrameLayout.LayoutParams(-1, -1))
        addView(View(ctx).apply { setBackgroundColor(Color.argb(122, 0, 0, 0)) }, FrameLayout.LayoutParams(-1, -1))
        addView(sheet, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    }

    private fun shoot(name: String, f: TypeFilter) {
        paparazzi.shootPage(name, widths) { ctx ->
            val p = TeamPage(ctx, NoHost); p.preset(TeamSection.BOX, group = true)
            val screen = pageScreen(ctx, p)
            over(ctx, screen, boxSheet(ctx, p, Host(types = f)).build(maxBody(ctx)))
        }
    }

    @Test fun noPick() = shoot("r16e-sheet-none", TypeFilter())
    @Test fun anyOfTwo() = shoot("r16e-sheet-any", TypeFilter(linkedSetOf(Type.WATER, Type.ICE), TypeMatch.ANY))
    @Test fun allOfTwo() = shoot("r16e-sheet-all", TypeFilter(linkedSetOf(Type.WATER, Type.ICE), TypeMatch.ALL))
    @Test fun onlyOne() = shoot("r16e-sheet-only", TypeFilter(linkedSetOf(Type.WATER), TypeMatch.ONLY))
    @Test fun onlyTwo() = shoot("r16e-sheet-only2", TypeFilter(linkedSetOf(Type.WATER, Type.ICE), TypeMatch.ONLY))

    @Test fun theBoxSheetHasTheTypeSectionAfterShowAndBeforeGrouping() {
        paparazzi.shootPage("r16e-order", listOf(w412), themes = listOf(Theme.LIGHT)) { ctx ->
            val p = TeamPage(ctx, NoHost); p.preset(TeamSection.BOX, group = true)
            val sheet = boxSheet(ctx, p, Host()).build(maxBody(ctx))
            val t = texts(sheet)
            val show = t.indexOf("SHOW"); val type = t.indexOf("TYPE"); val group = t.indexOf("Group duplicates")
            assertTrue("Show, Type, Group duplicates in this order: $t", show in 0 until type && type < group)
            assertEquals(listOf("Any of", "All of", "Only"), t.filter { it == "Any of" || it == "All of" || it == "Only" })
            assertEquals("all 18 type chips", Type.entries.size, Type.entries.count { it.label in t })
            assertEquals("No type picked: every type shows.", sentence(sheet))
            assertTrue("Clear is in the head", "Clear" in t && "Reset" !in t)
            over(ctx, pageScreen(ctx, p), sheet)
        }
    }

    @Test fun picksModesAndTheWordsFollowAtOnce() {
        paparazzi.shootPage("r16e-flow", listOf(w412), themes = listOf(Theme.LIGHT)) { ctx ->
            val p = TeamPage(ctx, NoHost); p.preset(TeamSection.BOX, group = true)
            val h = Host()
            val sheet = boxSheet(ctx, p, h).build(maxBody(ctx))
            val total = st.mons.size
            assertEquals("Show $total Pokémon", texts(sheet).first { it.startsWith("Show ") && it.endsWith("Pokémon") })
            tap(sheet, "Water")
            assertEquals(TypeFilter(linkedSetOf(Type.WATER)), h.types)
            assertEquals("Has at least one of: Water.", sentence(sheet))
            val water = st.mons.count { Type.WATER in gd.sp(it.species).types }
            assertEquals("the button counts with the filter", "Show $water Pokémon", texts(sheet).first { it.startsWith("Show ") && it.endsWith("Pokémon") })
            tap(sheet, "Ice")
            assertEquals("Has at least one of: Water, Ice.", sentence(sheet))
            tap(sheet, "All of")
            assertEquals(TypeMatch.ALL, h.types.match)
            assertEquals("Has both Water and Ice.", sentence(sheet))
            // a third pick in All of is refused with the toast, nothing changes
            Ui.lastToast = null
            tap(sheet, "Fire")
            assertEquals("All of: pick up to 2 types", Ui.lastToast)
            assertEquals(linkedSetOf(Type.WATER, Type.ICE), h.types.types)
            tap(sheet, "Only")
            assertEquals("No type outside Water + Ice: pure Water, pure Ice and Water/Ice stay.", sentence(sheet))
            tap(sheet, "Fire") // Only takes any number of picks
            assertEquals("No type outside Water + Ice + Fire.", sentence(sheet))
            tap(sheet, "Water"); tap(sheet, "Ice")
            assertEquals("Pure Fire only.", sentence(sheet))
            // three picks, then All of keeps the last two
            tap(sheet, "Water"); tap(sheet, "Ice")
            tap(sheet, "All of")
            assertEquals(listOf(Type.WATER, Type.ICE), h.types.types.toList())
            over(ctx, pageScreen(ctx, p), sheet)
        }
    }

    @Test fun clearIsDimmedUntilSomethingIsOnThenClearsFiltersButKeepsSortAndGrouping() {
        paparazzi.shootPage("r16e-clear", listOf(w412), themes = listOf(Theme.LIGHT)) { ctx ->
            val p = TeamPage(ctx, NoHost); p.preset(TeamSection.BOX, group = true)
            val h = Host(spec = SortSpec.of(SortKey.NAME), grouped = true)
            val sheet = boxSheet(ctx, p, h).build(maxBody(ctx))
            assertFalse("dimmed with nothing on", tv(sheet, "Clear").isEnabled)
            assertTrue(tv(sheet, "Clear").alpha < 1f)
            assertEquals("a real button: the close icon before the word", 1, tv(sheet, "Clear").compoundDrawablesRelative.count { it != null })
            // Clear with nothing on does nothing
            tv(sheet, "Clear").performClick()
            assertEquals(0, h.typeCalls)
            // a mode alone is no filter
            tap(sheet, "Only")
            assertFalse(tv(sheet, "Clear").isEnabled)
            tap(sheet, "Water"); tap(sheet, "Favourites")
            assertTrue(tv(sheet, "Clear").isEnabled); assertEquals(1f, tv(sheet, "Clear").alpha, 0f)
            assertEquals(BoxFilter.FAVOURITES.ordinal, h.show)
            tap(sheet, "Clear")
            assertEquals(TypeFilter(), h.types) // types gone and the mode back to Any of
            assertEquals(0, h.show) // Show = All
            assertEquals("the sort stays", SortSpec.of(SortKey.NAME), h.spec)
            assertTrue("grouping stays", h.grouped)
            assertEquals("No type picked: every type shows.", sentence(sheet))
            assertFalse("dimmed again", tv(sheet, "Clear").isEnabled)
            over(ctx, pageScreen(ctx, p), sheet)
        }
    }

    @Test fun theDexSheetSaysWhatNoGenerationMeansAndHasTheTypeSectionAfterIt() {
        paparazzi.shootPage("r16e-dex", widths) { ctx ->
            val p = TeamPage(ctx, NoHost); p.preset(TeamSection.BOX)
            var types = TypeFilter(linkedSetOf(Type.FIRE), TypeMatch.ANY)
            var wish = false; var picked = emptySet<Int>()
            val sheet = SortSheet(
                p, SortText.dexKeys, SortSpec.of(SortKey.DEX_NUMBER), false, null, 0, null, count = { 151 }, onChange = { _, _ -> },
                dex = SortSheet.DexPart(wish, listOf(1, 2, 3), picked) { w, g -> wish = w; picked = g },
                type = SortSheet.TypePart(types) { types = it },
            ).build(maxBody(ctx))
            val t = texts(sheet)
            assertTrue("Generation, then Type: $t", t.indexOf("GENERATION") in 0 until t.indexOf("TYPE"))
            assertTrue("None picked: every generation shows." in t)
            tap(sheet, "Gen 2")
            assertTrue("the line goes once a generation is ticked", "None picked: every generation shows." !in texts(sheet))
            tap(sheet, "Clear")
            assertTrue(picked.isEmpty()); assertEquals(TypeFilter(), types)
            assertTrue("None picked: every generation shows." in texts(sheet))
            tap(sheet, "Water")
            over(ctx, pageScreen(ctx, p), sheet)
        }
    }

    @Test fun theSortChipHasTheFunnelAndNoCount() {
        paparazzi.shootPage("r16e-chip", listOf(w412)) { ctx ->
            val row = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_base))
                setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 16), Ui.dp(ctx, 16), 0)
            }
            for ((spec, n) in listOf(SortSpec.of(SortKey.LEVEL) to 0, SortSpec.of(SortKey.NAME) to 2, SortSpec(key = SortKey.CHOSEN_TOTAL, chosenStats = setOf(com.husarp.ankigo.game.Stat.ATK, com.husarp.ankigo.game.Stat.DEF), basis = com.husarp.ankigo.ui.StatBasis.AT_LEVEL, level = 50) to 3)) {
                val chip = com.husarp.ankigo.ui.SortViews.sortChip(ctx, spec, n) {}
                assertTrue("no count: ${texts(chip)}", texts(chip).none { Regex("· \\d+").matches(it) })
                assertTrue(chip.getChildAt(0) is android.widget.ImageView)
                assertEquals("the description counts the filters: ${chip.contentDescription}", n > 0, "$n filters are on" in chip.contentDescription)
                row.addView(chip, android.widget.LinearLayout.LayoutParams(-2, -2).also { it.bottomMargin = Ui.dp(ctx, 8) })
            }
            row
        }
    }
}
