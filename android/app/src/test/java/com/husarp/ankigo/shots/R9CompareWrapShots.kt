package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.AppLook
import com.husarp.ankigo.ui.CompareFit
import com.husarp.ankigo.ui.CompareMode
import com.husarp.ankigo.ui.ComparePage
import com.husarp.ankigo.ui.ComparePick
import com.husarp.ankigo.ui.CompareStore
import com.husarp.ankigo.ui.FlowRow
import com.husarp.ankigo.ui.RowChoice
import com.husarp.ankigo.ui.TypeStyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * R9-C1/C2: Compare opens on Auto columns even when an old save holds a manual count; the matchup chips ("Weak to / Resists /
 * Immune") wrap only when the next pair does not fit in the rest of the line. Besides the PNGs (r9wrap-<what>-<size>-<theme>) every
 * shot reads the child positions of every FlowRow after layout and fails when a line broke although the next pair would have fit.
 */
class R9CompareWrapShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @After fun reset() {
        val ctx = Shots.files(paparazzi.context)
        AppLook.setTypeStyle(ctx, TypeStyle.ICON_TEXT)
        CompareStore.saveFit(ctx, CompareFit.AUTO)
    }

    private val two = listOf(ComparePick.Owned("t1"), ComparePick.Owned("t2"))
    private val three = listOf(ComparePick.Owned("t1"), ComparePick.Owned("t2"), ComparePick.Owned("t3"))
    private val five = listOf(ComparePick.Owned("t1"), ComparePick.Owned("t2"), ComparePick.Owned("t3"), ComparePick.Owned("t4"), ComparePick.Dex(150))
    private val phone = listOf(Variants.PHONE)

    private fun find(v: View, out: MutableList<FlowRow> = ArrayList()): List<FlowRow> {
        if (v is FlowRow) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), out)
        return out
    }

    /** Lays [root] out at the window size and returns how many flows (and wrapped line breaks) were checked. */
    private fun checkFlows(ctx: Context, root: View, gapPx: Int): Pair<Int, Int> {
        val dm = ctx.resources.displayMetrics
        root.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, dm.widthPixels, dm.heightPixels)
        var flows = 0
        var breaks = 0
        for (f in find(root)) {
            flows++
            val kids = (0 until f.childCount).map { f.getChildAt(it) }.filter { it.visibility != View.GONE }
            val inner = f.width - f.paddingLeft - f.paddingRight
            var lineStart = 0
            for (i in 1..kids.size) {
                if (i < kids.size && kids[i].left > kids[i - 1].left) continue // same line
                if (i == kids.size) break
                // kids[i] starts a new line: the line before it ran from kids[lineStart] to kids[i - 1]
                val lineW = kids[i - 1].right - kids[lineStart].left
                assertTrue("a line broke although the next pair fits: line $lineW + gap $gapPx + ${kids[i].width} <= $inner", lineW + gapPx + kids[i].width > inner)
                breaks++
                lineStart = i
            }
        }
        return flows to breaks
    }

    /** Only the Types and Matchups rows show, so the PNG is about them (the rest of the table is not what this ticket changes). */
    private val onlyMatchups = RowChoice(setOf("level", "nature", "ivs", "moves", "evo", "becomes", "hp", "atk", "def", "spa", "spd", "spe", "total",
        "base_hp", "base_atk", "base_def", "base_spa", "base_spd", "base_spe", "base_total"))

    private fun shoot(name: String, picks: List<ComparePick>, mode: CompareMode, fit: Int, style: TypeStyle, variants: List<Variant> = phone) {
        paparazzi.shootPage(name, variants) { ctx ->
            CompareStore.saveMode(ctx, mode); CompareStore.saveRows(ctx, onlyMatchups)
            AppLook.setTypeStyle(ctx, style)
            val p = ComparePage(ctx, NoHost, picks)
            p.fit = fit
            val screen = pageScreen(ctx, p)
            p.render()
            val (flows, _) = checkFlows(ctx, p.root, com.husarp.ankigo.ui.Ui.dp(ctx, 8))
            assertTrue("matchup flows were laid out: $flows, root ${p.root.javaClass.simpleName} ${(p.root as? ViewGroup)?.childCount}", flows >= 3)
            screen
        }
    }

    private val best = CompareMode.BEST_OF_ALL
    private val vs = CompareMode.VS_REFERENCE

    @Test fun twoAuto() = shoot("r9wrap-2-auto", two, vs, CompareFit.AUTO, TypeStyle.ICON_TEXT, listOf(Variants.PHONE, Variants.SPLIT))
    @Test fun threeAuto() = shoot("r9wrap-3-auto", three, best, CompareFit.AUTO, TypeStyle.ICON_TEXT)
    @Test fun fiveAuto() = shoot("r9wrap-5-auto", five, vs, CompareFit.AUTO, TypeStyle.ICON_TEXT)
    @Test fun fiveSix() = shoot("r9wrap-5-six", five, best, 6, TypeStyle.ICON_TEXT)
    @Test fun threeSix() = shoot("r9wrap-3-six", three, vs, 6, TypeStyle.ICON_TEXT)
    @Test fun twoIcons() = shoot("r9wrap-2-icon", two, best, CompareFit.AUTO, TypeStyle.ICON)
    @Test fun fiveIconsAuto() = shoot("r9wrap-5-icon", five, vs, CompareFit.AUTO, TypeStyle.ICON)
    @Test fun fiveIconsSix() = shoot("r9wrap-5-icon-six", five, best, 6, TypeStyle.ICON)

    /** C1: an old save with a manual count does not come back: a fresh screen starts on Auto. */
    @Test fun freshScreenStartsOnAutoEvenWithAnOldSavedCount() {
        paparazzi.shootPage("r9wrap-open-auto", phone) { ctx ->
            CompareStore.saveFit(ctx, 6)
            val p = ComparePage(ctx, NoHost, five)
            assertEquals(CompareFit.AUTO, p.fit)
            pageScreen(ctx, p)
        }
    }
}
