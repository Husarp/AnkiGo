package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Stat
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.ui.BoxFilter
import com.husarp.ankigo.ui.DexFilter
import com.husarp.ankigo.ui.DexPage
import com.husarp.ankigo.ui.DexSort
import com.husarp.ankigo.ui.SortKey
import com.husarp.ankigo.ui.SortSheet
import com.husarp.ankigo.ui.SortSpec
import com.husarp.ankigo.ui.SortText
import com.husarp.ankigo.ui.SortViews
import com.husarp.ankigo.ui.StatBasis
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TeamSection
import com.husarp.ankigo.ui.Ui
import org.junit.Rule
import org.junit.Test

/*
 * R3-V6 (pick 6A): the Box with the sort chip row, the "Sort & filter" sheet, the sort chip's sheet, the tile values of a stat
 * sort, and the Pokédex with the same chip. PNGs: sort-box-*, sort-sheet-*, sort-chip-sheet-*, sort-dex-*.
 */
class SortShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val FLOAT = Variant("float", Size.FLOAT.wDp, Size.FLOAT.hDp)
    private val sizes = listOf(Variants.PHONE, Variants.SPLIT, FLOAT)
    private val st get() = GameRepo.state!!
    private val gd get() = GameRepo.data!!

    private val atkDef = SortSpec(key = SortKey.CHOSEN_TOTAL, chosenStats = setOf(Stat.ATK, Stat.DEF), basis = StatBasis.AT_LEVEL, level = 50)

    private fun rich() {
        fun more(sp: Int, lv: Int, uid: String, at: Long, shiny: Boolean = false) =
            TestData.mon(gd, sp, lv, listOf("tackle"), uid).also { it.caughtAt = at; it.shiny = shiny; st.mons += it }
        more(25, 31, "x1", 1_760_100_000_000)
        more(25, 12, "x2", 1_760_200_000_000, shiny = true)
        more(16, 30, "x3", 1_760_250_000_000)
        st.mon("t1")!!.favourite = true
        st.mon("b3")!!.also { it.favourite = true; it.shiny = true }
        st.mon("b7")!!.favourite = true
    }

    private fun box(ctx: Context, v: Variant, spec: SortSpec, filter: BoxFilter = BoxFilter.ALL): Pair<TeamPage, View> {
        rich()
        val p = TeamPage(ctx, NoHost)
        p.preset(TeamSection.BOX, filter = filter, group = true, sort = spec)
        return p to (if (v === FLOAT) p.root.also { p.onShow() } else pageScreen(ctx, p))
    }

    private fun shootBox(name: String, spec: SortSpec, filter: BoxFilter = BoxFilter.ALL) {
        for (v in sizes) paparazzi.shootPage(name, listOf(v)) { ctx -> box(ctx, v, spec, filter).second }
    }

    @Test fun defaultSort() = shootBox("sort-box-default", SortSpec.of(SortKey.LEVEL))
    @Test fun statSortWithValues() = shootBox("sort-box-stat", atkDef, BoxFilter.ALL)
    @Test fun statSortFavourites() = shootBox("sort-box-stat-fav", SortSpec(key = SortKey.STAT, stat = Stat.SPE, basis = StatBasis.BASE), BoxFilter.FAVOURITES)
    @Test fun attackSort() = shootBox("sort-box-attack", SortSpec(key = SortKey.STRONGEST_ATTACK))

    private fun sheetOver(ctx: Context, screen: View, sheet: View) = FrameLayout(ctx).apply {
        addView(screen, FrameLayout.LayoutParams(-1, -1))
        addView(View(ctx).apply { setBackgroundColor(Color.argb(122, 0, 0, 0)) }, FrameLayout.LayoutParams(-1, -1))
        addView(sheet, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    }

    private fun sheet(name: String, spec: SortSpec, filter: BoxFilter = BoxFilter.ALL, vs: List<Variant> = sizes, vsWild: Boolean = false) {
        for (v in vs) paparazzi.shootPage(name, listOf(v)) { ctx ->
            val (p, screen) = box(ctx, v, spec, filter)
            val maxBody = (ctx.resources.displayMetrics.heightPixels * SortSheet.BODY_SHARE).toInt() - Ui.dp(ctx, 150)
            val s = SortSheet(
                p, SortText.boxKeys, spec, SortSpec.DEFAULT, vsWild, BoxFilter.entries.map { it.label }, filter.ordinal,
                SortSheet.GroupToggle(true) {}, count = { 54 }, onChange = { _, _ -> },
            )
            sheetOver(ctx, screen, s.build(maxBody))
        }
    }

    @Test fun sheetChosenStats() = sheet("sort-sheet-chosen", atkDef.copy(basis = StatBasis.CURRENT), vsWild = true)
    @Test fun sheetAtLevel() = sheet("sort-sheet-level", atkDef, BoxFilter.FAVOURITES)
    @Test fun sheetOneStat() = sheet("sort-sheet-onestat", SortSpec(key = SortKey.STAT, stat = Stat.SPE, basis = StatBasis.BASE), vs = listOf(Variants.PHONE, FLOAT))
    /** R4-X1 item 15: the sheet the sort chip opens (direction on top, the options below). */
    @Test fun sortSheetLevel() = sheet("sort-chip-sheet", SortSpec.of(SortKey.LEVEL), vs = listOf(Variants.PHONE, Variants.SPLIT))
    @Test fun sortSheetAsc() = sheet("sort-chip-sheet-asc", SortSpec(key = SortKey.NAME, descending = false), vs = listOf(Variants.PHONE))
    @Test fun sortSheetStats() = sheet("sort-chip-sheet-stats", atkDef, vs = listOf(Variants.PHONE), vsWild = true)
    @Test fun sheetLevelSort() = sheet("sort-sheet-plain", SortSpec.of(SortKey.LEVEL), vs = listOf(Variants.PHONE, Variants.SPLIT))

    // ---- Pokédex ----

    private fun dex(ctx: Context, f: DexFilter): DexPage {
        val p = DexPage(ctx, NoHost)
        setField(p, "filter", f)
        return p
    }

    private val dexStat = DexFilter(sort = DexSort.CHOSEN_TOTAL, chosenStats = setOf(Stat.ATK, Stat.DEF), basis = StatBasis.MAX_LEVEL)

    @Test fun dexDefault() { for (v in listOf(Variants.PHONE, Variants.SPLIT)) paparazzi.shootPage("sort-dex-default", listOf(v)) { ctx -> pageScreen(ctx, dex(ctx, DexFilter())) } }
    @Test fun dexStatSort() { for (v in listOf(Variants.PHONE, Variants.SPLIT)) paparazzi.shootPage("sort-dex-stat", listOf(v)) { ctx -> pageScreen(ctx, dex(ctx, dexStat)) } }
    @Test fun dexLevelSort() {
        paparazzi.shootPage("sort-dex-level", listOf(Variants.PHONE)) { ctx -> pageScreen(ctx, dex(ctx, DexFilter(sort = DexSort.LEVEL))) }
    }

    @Test fun dexSheet() {
        for (v in listOf(Variants.PHONE, Variants.SPLIT)) paparazzi.shootPage("sort-dex-sheet", listOf(v)) { ctx ->
            val p = dex(ctx, dexStat)
            val screen = pageScreen(ctx, p)
            val maxBody = (ctx.resources.displayMetrics.heightPixels * SortSheet.BODY_SHARE).toInt() - Ui.dp(ctx, 150)
            val s = SortSheet(p, SortText.dexKeys, dexStat.spec(), SortViews.DEX_DEFAULT, false, null, 0, null, count = { 151 }, onChange = { _, _ -> })
            sheetOver(ctx, screen, s.build(maxBody))
        }
    }
}
