package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.Bits
import com.husarp.ankigo.ui.ItemIconPlan
import com.husarp.ankigo.ui.Ui
import org.junit.Rule
import org.junit.Test

/*
 * R14-B2-C: the five picture-less item icons (166A), the coin without its outer ring (144C) and the adaptive app icon (AI-A).
 * PNGs: r14b2c-<items|coin|launcher>-phone-<light|dark>.
 */
class R14B2CIconShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun col(ctx: Context, vararg v: View) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Ui.c(ctx, R.color.ag_base))
        val p = Ui.dp(ctx, 16); setPadding(p, p, p, p)
        v.forEach { addView(it, LinearLayout.LayoutParams(-2, -2).also { lp -> lp.bottomMargin = Ui.dp(ctx, 10) }) }
    }

    private fun row(ctx: Context, gap: Int = 8, vararg v: View) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        v.forEach { addView(it, LinearLayout.LayoutParams(-2, -2).also { lp -> lp.marginEnd = Ui.dp(ctx, gap) }) }
    }

    private fun cap(ctx: Context, t: String) = Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { text = t }

    private val drawn = ItemIconPlan.DRAWN.toList().sorted()

    @Test fun items() {
        paparazzi.shootWith("r14b2c-items", sizes = listOf(Size.PHONE)) { ctx ->
            val big = row(ctx, 10, *drawn.map { Bits.itemTile(ctx, it, 56) }.toTypedArray())
            val mid = row(ctx, 6, *drawn.map { Bits.itemTile(ctx, it, 36) }.toTypedArray())
            val small = row(ctx, 6, *drawn.map { Bits.itemTile(ctx, it, 28) }.toTypedArray())
            val rows = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                for (id in listOf("fire-stone") + drawn.take(3) + listOf("kings-rock") + drawn.drop(3))
                    addView(Bits.itemRow(ctx, id, "x1"), LinearLayout.LayoutParams(Ui.dp(ctx, 360), -2))
            }
            col(ctx, cap(ctx, "56 dp tiles (pixel art, nearest neighbour)"), big, cap(ctx, "36 dp tiles"), mid, cap(ctx, "28 dp tiles"), small,
                cap(ctx, "In Bag-like rows, between real sprites"), rows)
        }
    }

    @Test fun coin() {
        paparazzi.shootWith("r14b2c-coin", sizes = listOf(Size.PHONE)) { ctx ->
            fun coin(dp: Int) = ImageView(ctx).apply { setImageResource(R.drawable.ic_money); layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, dp), Ui.dp(ctx, dp)) }
            fun amt(dp: Int, sp: Float, c: Int) = row(ctx, 4, coin(dp), Ui.text(ctx, sp, 800, c).apply { text = "1,250" })
            val sizes = row(ctx, 14, amt(13, 12f, R.color.ag_yellow), amt(14, 13f, R.color.ag_text), amt(24, 20f, R.color.ag_text), coin(40))
            val chip = Ui.chip(ctx, R.drawable.ic_money, R.color.ag_yellow).also { it.setText("1,250") }
            val plain = Ui.chip(ctx, R.drawable.ic_money, R.color.ag_yellow, plain = true).also { it.setText("1,250") }
            val bar = Ui.appBar(ctx, "Shop", "1,250", pillIconRes = R.drawable.ic_money)
            col(ctx, cap(ctx, "13 / 14 / 24 / 40 dp"), sizes, cap(ctx, "Chips"), row(ctx, 8, chip, plain),
                FrameLayout(ctx).apply { addView(bar, FrameLayout.LayoutParams(Ui.dp(ctx, 380), -2)) })
        }
    }

    /** The launcher icon as a launcher draws it (layoutlib cannot inflate an <adaptive-icon>, so this draws background colour + foreground itself): the 72 dp mask window of the 108 dp canvas, cut to a circle or a rounded square. */
    private class Mask(ctx: Context, private val sizeDp: Int, private val circle: Boolean) : View(ctx) {
        private val fg = ctx.getDrawable(R.drawable.ic_launcher_foreground)!!
        private val bgPaint = Paint().apply { color = ctx.getColor(R.color.ic_launcher_background) }
        private val px = Ui.dp(ctx, sizeDp)
        override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(px, px)
        override fun onDraw(c: Canvas) {
            val k = px / 72f
            c.scale(k, k); c.translate(-18f, -18f)
            val clip = Path().apply { if (circle) addCircle(54f, 54f, 36f, Path.Direction.CW) else addRoundRect(RectF(18f, 18f, 90f, 90f), 14f, 14f, Path.Direction.CW) }
            c.clipPath(clip)
            c.drawRect(0f, 0f, 108f, 108f, bgPaint)
            fg.setBounds(0, 0, 108, 108); fg.draw(c)
        }
    }

    @Test fun launcher() {
        paparazzi.shootWith("r14b2c-launcher", sizes = listOf(Size.PHONE)) { ctx ->
            val big = row(ctx, 12, Mask(ctx, 108, true), Mask(ctx, 108, false))
            val small = row(ctx, 12, Mask(ctx, 48, true), Mask(ctx, 48, false), Mask(ctx, 36, true), Mask(ctx, 36, false))
            col(ctx, cap(ctx, "Circle and rounded-square mask, 108 dp"), big, cap(ctx, "48 dp and 36 dp"), small)
        }
    }
}
