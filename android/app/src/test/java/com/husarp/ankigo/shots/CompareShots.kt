package com.husarp.ankigo.shots

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.AppMenu
import com.husarp.ankigo.ui.ColumnFilter
import com.husarp.ankigo.ui.ComparePage
import com.husarp.ankigo.ui.ComparePick
import com.husarp.ankigo.ui.CompareMode
import com.husarp.ankigo.ui.CompareState
import com.husarp.ankigo.ui.CompareStore
import com.husarp.ankigo.ui.CompareTable
import com.husarp.ankigo.ui.OwnerFilter
import com.husarp.ankigo.ui.RowChoice
import com.husarp.ankigo.ui.Ui
import org.junit.Rule
import org.junit.Test

/*
 * R3-V5 (pick 5A): the Compare screen. 2, 5 and 9 Pokémon in both modes, a column menu, the Rows sheet, sorted / filtered /
 * fully evolved, phone / split / float. PNGs: compare-<what>-<size>-<theme>.
 */
class CompareShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val float = Variant("float", Size.FLOAT.wDp, Size.FLOAT.hDp)
    private val three = listOf(Variants.PHONE, Variants.SPLIT, float)

    private fun find(v: View, pred: (View) -> Boolean, out: MutableList<View> = ArrayList()): List<View> {
        if (pred(v)) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), pred, out)
        return out
    }

    private val two = listOf(ComparePick.Owned("t1"), ComparePick.Owned("t2"))
    private val five = listOf(ComparePick.Owned("t1"), ComparePick.Owned("t2"), ComparePick.Dex(150), ComparePick.Owned("t6"), ComparePick.Dex(3))
    private val nine = listOf(
        ComparePick.Owned("t1"), ComparePick.Owned("t2"), ComparePick.Owned("t3"), ComparePick.Owned("t4"), ComparePick.Owned("t5"),
        ComparePick.Owned("t6"), ComparePick.Owned("b3"), ComparePick.Dex(150), ComparePick.Dex(3),
    )

    /** A page in [mode]; [seed] may sort / filter it once its state exists; [rows] = hidden row keys. */
    private fun page(ctx: Context, picks: List<ComparePick>, mode: CompareMode, rows: RowChoice = RowChoice(), seed: (CompareState) -> Unit = {}): ComparePage {
        CompareStore.saveMode(ctx, mode)
        CompareStore.saveRows(ctx, rows)
        return ComparePage(ctx, NoHost, picks).also { it.onNewState = seed }
    }

    private fun shoot(name: String, picks: List<ComparePick>, mode: CompareMode, variants: List<Variant> = three,
                      rows: RowChoice = RowChoice(), seed: (CompareState) -> Unit = {}) {
        paparazzi.shootPage(name, variants) { ctx -> pageScreen(ctx, page(ctx, picks, mode, rows, seed)) }
    }

    private val vs = CompareMode.VS_REFERENCE
    private val best = CompareMode.BEST_OF_ALL

    @Test fun vsTwo() = shoot("compare-vs-2", two, vs)
    @Test fun vsFive() = shoot("compare-vs-5", five, vs)
    @Test fun vsNine() = shoot("compare-vs-9", nine, vs)
    @Test fun bestTwo() = shoot("compare-best-2", two, best)
    @Test fun bestFive() = shoot("compare-best-5", five, best)
    @Test fun bestNine() = shoot("compare-best-9", nine, best)

    @Test fun fewRows() = shoot("compare-rows-few", five, vs, listOf(Variants.PHONE),
        rows = RowChoice(setOf("level", "types", "base_hp", "base_atk", "base_def", "base_spa", "base_spd", "base_spe", "base_total", "nature", "ivs", "moves", "weak", "resist", "immune", "evo", "becomes")))

    @Test fun sortedSecondReference() = shoot("compare-sorted", five, vs, listOf(Variants.PHONE, float)) {
        it.makeReference(five[1])
        it.tapRow("total")
    }

    @Test fun sortedAscBest() = shoot("compare-sorted-best", nine, best, listOf(Variants.PHONE)) { it.tapRow("atk"); it.tapRow("atk") }

    @Test fun ownedOnly() = shoot("compare-owned-only", five, vs, listOf(Variants.PHONE)) { it.filter = ColumnFilter(owner = OwnerFilter.OWNED) }

    @Test fun evolved() {
        paparazzi.shootPage("compare-evolved", listOf(Variants.PHONE)) { ctx ->
            val p = page(ctx, five, best)
            setField(p, "evolved", true)
            pageScreen(ctx, p)
        }
    }

    /** Taps the head of column [pos], grabs the menu and hangs it under that head. */
    private fun menu(name: String, picks: List<ComparePick>, mode: CompareMode, pos: Int, variants: List<Variant> = three) {
        paparazzi.shootPage(name, variants) { ctx ->
            val p = page(ctx, picks, mode)
            val screen = pageScreen(ctx, p)
            p.render()
            var got: AppMenu? = null
            AppMenu.intercept = { got = it }
            try {
                find(p.root, { it.contentDescription?.contains("Tap for options.") == true })[pos].performClick()
            } finally { AppMenu.intercept = null }
            val colDp = (ctx.resources.configuration.screenWidthDp - 12 - 44) / 5f
            FrameLayout(ctx).apply {
                addView(screen, FrameLayout.LayoutParams(-1, -1))
                addView(got!!.content(ctx), FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).also {
                    it.topMargin = Ui.dp(ctx, if (variants.size == 1 || ctx.resources.configuration.screenHeightDp > 600) 150 else 90)
                    it.marginStart = Ui.dp(ctx, (44 + 6 + colDp * pos).toInt().coerceAtMost(ctx.resources.configuration.screenWidthDp - 200))
                })
            }
        }
    }

    @Test fun menuVs() = menu("compare-menu-vs", five, vs, 2)
    @Test fun menuReference() = menu("compare-menu-ref", five, vs, 0, listOf(Variants.PHONE))
    @Test fun menuBest() = menu("compare-menu-best", nine, best, 1, listOf(Variants.PHONE, Variants.SPLIT))
    @Test fun menuTwoLeft() = menu("compare-menu-two", two, vs, 1, listOf(Variants.PHONE))

    @Test fun rowsSheet() {
        for (v in three) paparazzi.shootPage("compare-rows-sheet", listOf(v)) { ctx ->
            val p = page(ctx, five, vs, RowChoice(setOf("base_hp", "base_atk", "moves", "weak")))
            val screen = pageScreen(ctx, p)
            val full = CompareTable.build(GameRepo.data!!, GameRepo.state!!, five, false)
            val maxBody = (ctx.resources.displayMetrics.heightPixels * 0.9f).toInt() - Ui.dp(ctx, 150)
            FrameLayout(ctx).apply {
                addView(screen, FrameLayout.LayoutParams(-1, -1))
                addView(View(ctx).apply { setBackgroundColor(android.graphics.Color.argb(122, 0, 0, 0)) }, FrameLayout.LayoutParams(-1, -1))
                addView(p.rowsSheet(p.rowItems(full), maxBody) {}, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            }
        }
    }
}
