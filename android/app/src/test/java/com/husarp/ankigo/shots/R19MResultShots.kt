package com.husarp.ankigo.shots

import android.view.View
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.CatchSummary
import com.husarp.ankigo.ui.CatchTilesView
import com.husarp.ankigo.ui.Ui
import org.junit.Rule
import org.junit.Test

/** R19 M (PLAN 174, pick B): the lighter battle result row. PNGs: r19m-rows-<size>-<theme>. */
class R19MResultShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun xp(n: Int) = listOf(CatchSummary.XpLine("a", "Bulbasaur", n, 7, false))
    private val newThrow = CatchSummary(true, "Pidgey", CatchSummary.PokedexLine(true, 25), CatchSummary.ThrowLine("poke-ball", 2, 45), xp(120), 10, CatchSummary.NEXT_TEXT)
    private val newAuto = CatchSummary(true, "Pidgey", CatchSummary.PokedexLine(true, 25), null, xp(120), 10, CatchSummary.NEXT_TEXT)
    private val before = CatchSummary(true, "Pidgey", CatchSummary.PokedexLine(false, 25), null, xp(120), 10, CatchSummary.NEXT_TEXT)
    private val defeated = CatchSummary(false, "Pidgey", null, null, xp(84), 20, CatchSummary.NEXT_TEXT)
    private val big = CatchSummary(true, "Pidgey", CatchSummary.PokedexLine(true, 1025), CatchSummary.ThrowLine("poke-ball", 12, 100), xp(1200), 1250, CatchSummary.NEXT_TEXT)

    @Test fun rows() {
        paparazzi.shoot("r19m-rows", listOf(Size.SMALL, Size.FLOATB, Size.PIP_169M)) { ctx ->
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8))
                for ((s, next) in listOf(newThrow to true, newAuto to true, before to false, defeated to true, defeated to false, big to true, big to false)) {
                    val v: View = CatchTilesView(ctx).also { it.bindRow(s, next = next, rowH = Ui.dp(ctx, 26), radiusDp = 12f) }
                    addView(v, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(ctx, 8) })
                }
            }
        }
    }
}
