package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.BoxViews
import com.husarp.ankigo.ui.Chip
import com.husarp.ankigo.ui.TypeChips
import com.husarp.ankigo.ui.TypeStyle
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/* R10-W2-A: the 18 type chips (incl. the new Ice / Fighting / Bug), the Exp. Share mark and the gold money stack at real sizes. Shots: r10w2icons-*. */
class R10W2IconShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun frame(ctx: Context, vararg rows: View): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Ui.c(ctx, R.color.ag_base))
        val p = Ui.dp(ctx, 16); setPadding(p, p, p, p)
        rows.forEach { addView(it, LinearLayout.LayoutParams(-2, -2).also { lp -> lp.bottomMargin = Ui.dp(ctx, 12) }) }
    }

    private fun row(ctx: Context, vararg v: View) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        v.forEach { addView(it, LinearLayout.LayoutParams(-2, -2).also { lp -> lp.marginEnd = Ui.dp(ctx, 8) }) }
    }

    private fun img(ctx: Context, res: Int, dp: Int, tint: Int?): ImageView = ImageView(ctx).apply {
        setImageResource(res)
        if (tint != null) setColorFilter(Ui.c(ctx, tint), android.graphics.PorterDuff.Mode.SRC_IN)
        layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, dp), Ui.dp(ctx, dp))
    }

    @Test fun typeChips() {
        paparazzi.shootWith("r10w2icons-types", sizes = listOf(Size.PHONE)) { ctx ->
            val rows = Type.entries.chunked(6).map { g -> row(ctx, *g.map { TypeChips.chip(ctx, it, TypeStyle.ICON_TEXT) }.toTypedArray()) }
            val icons = row(ctx, *Type.entries.map { img(ctx, TypeChips.symbol(it), 24, R.color.ag_text) }.take(9).toTypedArray())
            frame(ctx, *rows.toTypedArray(), icons)
        }
    }

    @Test fun expShareAndMoney() {
        paparazzi.shootWith("r10w2icons-exp-money", sizes = listOf(Size.PHONE)) { ctx ->
            val exp = row(ctx, *listOf(13, 16, 24, 48).map { img(ctx, R.drawable.ic_exp_share, it, R.color.ag_teal) }.toTypedArray(),
                BoxViews.icon(ctx, R.drawable.ic_exp_share, R.color.ag_teal).apply { layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 16), Ui.dp(ctx, 16)) })
            val money = row(ctx, *listOf(13, 14, 24, 40).map { img(ctx, R.drawable.ic_money, it, null) }.toTypedArray())
            val chip = Ui.chip(ctx, R.drawable.ic_money, R.color.ag_yellow).also { it.setText("1,250") }
            val plain = Ui.chip(ctx, R.drawable.ic_money, R.color.ag_yellow, plain = true).also { it.setText("1,250") }
            val bar = Ui.appBar(ctx, "Shop", "1,250", pillIconRes = R.drawable.ic_money)
            val old = Ui.chip(ctx, R.drawable.ic_coin, R.color.ag_yellow).also { it.setText("1,250") }
            frame(ctx, exp, money, row(ctx, chip, plain, old), FrameLayout(ctx).apply { addView(bar, FrameLayout.LayoutParams(Ui.dp(ctx, 380), -2)) })
        }
    }

    @Test fun moneyChipKeepsItsColours() {
        paparazzi.shootWith("r10w2icons-chip-check", sizes = listOf(Size.PHONE)) { ctx ->
            val c = Ui.chip(ctx, R.drawable.ic_money, R.color.ag_yellow)
            val icon = c.getChildAt(0) as ImageView
            assertNull(icon.colorFilter)
            val coin = Ui.chip(ctx, R.drawable.ic_coin, R.color.ag_yellow).getChildAt(0) as ImageView
            assertEquals(false, coin.colorFilter == null)
            frame(ctx, c)
        }
    }
}
