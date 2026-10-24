package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Tuning
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.CatchSummary
import com.husarp.ankigo.ui.CatchTilesView
import com.husarp.ankigo.ui.CoinText
import com.husarp.ankigo.ui.ShopPage
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** R18 K (PLAN 268): the coin icon before the numbers (float over the coin chip, Refresh button, result tiles, log line). PNGs: r18kc-*. */
class R18KCoinShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun coinTextPutsTheIconBeforeTheNumber() {
        val ctx = Shots.files(paparazzi.context)
        val t = CoinText.of(ctx, 40f, "+5")
        assertEquals("${CoinText.MARK}+5", t.toString())
        assertEquals("1 coin", CoinText.word(1)); assertEquals("25 coins", CoinText.word(25)); assertEquals("1,500 coins", CoinText.word(1500, "1,500"))
    }

    @Test fun floatOverTheCoinChip() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
            val p = BattlePanelView(ctx).also { it.renderAll() }
            val frame = FrameLayout(ctx).apply { addView(p, FrameLayout.LayoutParams(-1, -1)) }
            paparazzi.snapshot(frame, "r18kc-float-before-${theme.name.lowercase()}", 0)
            p.floatAtCoins("+5")
            paparazzi.snapshot(frame, "r18kc-float-${theme.name.lowercase()}", 250)
        }
    }

    @Test fun shopRefreshButton() {
        paparazzi.shootPage("r18kc-shop", listOf(Variants.TALL)) { ctx -> pageScreen(ctx, ShopPage(ctx, NoHost)) }
    }

    @Test fun resultTilesAndLogLine() {
        paparazzi.shootWith("r18kc-tiles", listOf(Size.PHONE)) { ctx ->
            val s = CatchSummary(true, "Pidgey", CatchSummary.PokedexLine(true, 16), CatchSummary.ThrowLine("poke-ball", 1, 63),
                listOf(CatchSummary.XpLine("a", "Bulbasaur", 30, 7, true)), 25, "Next: Rattata")
            val grid = CatchTilesView(ctx).also { it.bindGrid(s, next = false) }
            val row = CatchTilesView(ctx).also { it.bindRow(s, next = false, rowH = Ui.dp(ctx, 24), radiusDp = 8f) }
            val tv = Ui.text(ctx, 11f, 900, R.color.ag_yellow).also { it.text = CoinText.of(it, "+25") }
            val big = Ui.text(ctx, 20f, 900, R.color.ag_yellow).also { it.text = CoinText.of(it, "Refresh 100") }
            val price = Ui.text(ctx, 13f, 900, R.color.ag_text).also { it.text = CoinText.of(it, "${Tuning.REROLL_PRICE}") }
            val sentence = Ui.text(ctx, 13f, 700, R.color.ag_text).also { it.text = "Not enough money (need ${CoinText.word(Tuning.REROLL_PRICE)})." }
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 16), Ui.dp(ctx, 16), Ui.dp(ctx, 16))
                for (v in listOf<View>(grid, row, tv, big, price, sentence)) addView(v, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(ctx, 12) })
            }
        }
    }
}
