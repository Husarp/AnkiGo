package com.husarp.ankigo.shots

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.AppMenu
import com.husarp.ankigo.ui.CompareFit
import com.husarp.ankigo.ui.CompareGroup
import com.husarp.ankigo.ui.CompareMode
import com.husarp.ankigo.ui.ComparePage
import com.husarp.ankigo.ui.ComparePick
import com.husarp.ankigo.ui.CompareState
import com.husarp.ankigo.ui.CompareStore
import com.husarp.ankigo.ui.RowChoice
import com.husarp.ankigo.ui.Ui
import org.junit.Rule
import org.junit.Test

/*
 * R6-05 (Compare items 6-10): the "Stats | Base stats" switch, heads without tags, full type names in the moves rows, the sort
 * menu's drawn icons. PNGs: r6compare-<what>-<size>-<theme>.
 */
class R6CompareShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val two = listOf(ComparePick.Owned("t1"), ComparePick.Owned("t2"))
    private val four = listOf(ComparePick.Owned("t1"), ComparePick.Owned("t2"), ComparePick.Dex(150), ComparePick.Owned("t3"))
    private val phoneSplit = listOf(Variants.PHONE, Variants.SPLIT)
    private val moveRows = RowChoice(setOf("nature", "ivs", "weak", "resist", "immune", "evo", "becomes", "hp", "atk", "def", "spa", "spd", "spe", "total",
        "base_hp", "base_atk", "base_def", "base_spa", "base_spd", "base_spe", "base_total"))

    private fun find(v: View, pred: (View) -> Boolean, out: MutableList<View> = ArrayList()): List<View> {
        if (pred(v)) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), pred, out)
        return out
    }

    /** Owned Pokémon with moves of long-named types, so the moves rows have something to cut. */
    private fun giveMoves() {
        val s = GameRepo.state!!
        val sets = mapOf("t1" to listOf("thunderbolt", "moonblast", "earthquake", "fly"), "t2" to listOf("hypnosis", "solarbeam", "shadowball", "seismictoss"),
            "t3" to listOf("dragonrage", "toxic", "ember", "tackle"))
        for ((uid, ms) in sets) s.mon(uid)?.let { it.moves.clear(); it.moves.addAll(ms) }
    }

    private fun page(ctx: Context, picks: List<ComparePick>, mode: CompareMode, group: CompareGroup, rows: RowChoice = RowChoice(), seed: (CompareState) -> Unit = {}): ComparePage {
        CompareStore.saveMode(ctx, mode); CompareStore.saveRows(ctx, rows); CompareStore.saveGroup(ctx, group); CompareStore.saveFit(ctx, CompareFit.AUTO)
        giveMoves()
        return ComparePage(ctx, NoHost, picks).also { it.onNewState = seed }
    }

    private fun shoot(name: String, picks: List<ComparePick>, mode: CompareMode, group: CompareGroup, rows: RowChoice = RowChoice(),
                      variants: List<Variant> = phoneSplit, evolved: Boolean = false, seed: (CompareState) -> Unit = {}) {
        paparazzi.shootPage(name, variants) { ctx ->
            val p = page(ctx, picks, mode, group, rows, seed)
            if (evolved) setField(p, "evolved", true)
            pageScreen(ctx, p)
        }
    }

    private val vs = CompareMode.VS_REFERENCE
    private val best = CompareMode.BEST_OF_ALL

    @Test fun statsTwo() = shoot("r6compare-stats-2", two, vs, CompareGroup.STATS)
    @Test fun baseTwo() = shoot("r6compare-base-2", two, vs, CompareGroup.BASE)
    @Test fun statsFour() = shoot("r6compare-stats-4", four, vs, CompareGroup.STATS)
    @Test fun baseFour() = shoot("r6compare-base-4", four, best, CompareGroup.BASE)
    @Test fun evolvedFour() = shoot("r6compare-evolved-4", four, vs, CompareGroup.STATS, variants = listOf(Variants.PHONE), evolved = true)
    @Test fun dexOnly() = shoot("r6compare-dex-only", listOf(ComparePick.Dex(150), ComparePick.Dex(3), ComparePick.Dex(25)), vs, CompareGroup.STATS, variants = listOf(Variants.PHONE))
    @Test fun sortedBaseFromStats() = shoot("r6compare-sorted-base", four, best, CompareGroup.BASE, variants = listOf(Variants.PHONE)) { it.setSort("atk", true) }

    @Test fun movesTwo() = shoot("r6compare-moves-2", two, vs, CompareGroup.STATS, moveRows)
    @Test fun movesFour() = shoot("r6compare-moves-4", four, vs, CompareGroup.STATS, moveRows)
    @Test fun movesFourBest() = shoot("r6compare-moves-4best", four, best, CompareGroup.STATS, moveRows, listOf(Variants.PHONE))

    /** Six narrow columns: the full type names shrink a little instead of getting a "...". */
    @Test fun movesSixNarrow() {
        val six = listOf("t1", "t2", "t3", "t4", "t5", "t6").map { ComparePick.Owned(it) }
        paparazzi.shootPage("r6compare-moves-6narrow", listOf(Variants.PHONE)) { ctx ->
            val p = page(ctx, six, vs, CompareGroup.STATS, moveRows)
            CompareStore.saveFit(ctx, 6)
            pageScreen(ctx, p)
        }
        CompareStore.saveFit(Shots.files(paparazzi.context), CompareFit.AUTO)
    }

    /** The sort menu of a tapped row label. */
    @Test fun sortMenu() {
        paparazzi.shootPage("r6compare-sortmenu", listOf(Variants.PHONE)) { ctx ->
            val p = page(ctx, four, vs, CompareGroup.STATS) { it.setSort("atk", false) }
            val screen = pageScreen(ctx, p)
            p.render()
            var got: AppMenu? = null
            AppMenu.intercept = { got = it }
            try { find(p.root, { it.contentDescription?.startsWith("Sort by Atk") == true }).first().performClick() } finally { AppMenu.intercept = null }
            FrameLayout(ctx).apply {
                addView(screen, FrameLayout.LayoutParams(-1, -1))
                addView(got!!.content(ctx), FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).also { it.topMargin = Ui.dp(ctx, 330); it.marginStart = Ui.dp(ctx, 50) })
            }
        }
    }
}
