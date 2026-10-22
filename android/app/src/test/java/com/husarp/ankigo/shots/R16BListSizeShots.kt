package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.DexPage
import com.husarp.ankigo.ui.ListSpriteSize
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.SettingsSection
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TeamSection
import com.husarp.ankigo.ui.TileSprite
import com.husarp.ankigo.ui.Ui
import org.junit.Rule
import org.junit.Test

/*
 * PLAN 215 (R16-1-B): the list size. The Look card with the slider and its live preview, a Box grid and a Pokédex grid at Smallest /
 * Medium / Largest, and synthetic Pokémon of different content sizes at every step. PNGs: r16b-<what>-<phone|tall>-<light|dark>.
 */
class R16BListSizeShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val steps = listOf(0, 2, 4)

    private fun look(ctx: Context, step: Int): View {
        AppPrefs.setListSpriteSize(ctx, step)
        val c = ShotCtx(ctx)
        val p = SettingsPage(c, NoHost)
        val v = pageScreen(c, p)
        p.open(SettingsSection.LOOK)
        return v
    }

    @Test fun lookCard() {
        for (s in steps) paparazzi.shootPage("r16b-look-${ListSpriteSize.name(s).lowercase()}", listOf(Variants.TALL)) { look(it, s) }
    }

    @Test fun boxGrid() {
        for (s in steps) paparazzi.shootPage("r16b-box-${ListSpriteSize.name(s).lowercase()}", listOf(Variants.PHONE)) { ctx ->
            AppPrefs.setListSpriteSize(ctx, s)
            val p = TeamPage(ctx, NoHost)
            p.preset(TeamSection.BOX)
            pageScreen(ctx, p)
        }
    }

    @Test fun dexGrid() {
        for (s in steps) paparazzi.shootPage("r16b-dex-${ListSpriteSize.name(s).lowercase()}", listOf(Variants.PHONE)) { ctx ->
            AppPrefs.setListSpriteSize(ctx, s)
            pageScreen(ctx, DexPage(ctx, NoHost))
        }
    }

    /** A made-up Pokémon: [cw] x [ch] opaque pixels in a 96 x 96 picture (offset a little off-centre like real ones), 1 px dark edge, two dark "eyes". */
    private fun mon(color: Int, cw: Int, ch: Int, dx: Int, dy: Int): Bitmap {
        val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val x0 = (96 - cw) / 2 + dx
        val y0 = (96 - ch) / 2 + dy
        for (y in 0 until ch) for (x in 0 until cw) {
            val edge = x == 0 || y == 0 || x == cw - 1 || y == ch - 1
            b.setPixel(x0 + x, y0 + y, if (edge) Color.rgb(40, 40, 60) else color)
        }
        if (cw >= 12 && ch >= 12) for (e in listOf(cw / 3, cw - cw / 3 - 1)) { b.setPixel(x0 + e, y0 + ch / 3, Color.BLACK); b.setPixel(x0 + e, y0 + ch / 3 + 1, Color.BLACK) }
        return b
    }

    @Test fun contentSizes() {
        paparazzi.shootPage("r16b-contents", listOf(Variants.TALL)) { ctx ->
            val bitmaps = listOf(mon(Color.rgb(250, 200, 40), 24, 34, 0, 2), mon(Color.rgb(90, 150, 240), 56, 56, -3, 0),
                mon(Color.rgb(240, 90, 130), 84, 70, 0, 4), mon(Color.rgb(120, 190, 80), 96, 96, 0, 0))
            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.c(ctx, R.color.ag_base)); setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8)) }
            for (step in 0..4) {
                col.addView(TextView(ctx).apply { text = "${ListSpriteSize.name(step)}  (step $step)"; setTextColor(Ui.c(ctx, R.color.ag_text)) })
                val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                for (b in bitmaps) {
                    val tile = FrameLayout(ctx).apply { background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 10, ctx) }
                    // the Box cell: 94 x 118 dp, 17 dp top row, 14 dp strip, 2 dp sides
                    tile.addView(TileSprite(ctx).apply { sizeStep = step; setImageDrawable(Ui.pixelBitmap(ctx, b)) },
                        FrameLayout.LayoutParams(-1, -1).also { it.topMargin = Ui.dp(ctx, 17); it.bottomMargin = Ui.dp(ctx, 14); it.leftMargin = Ui.dp(ctx, 2); it.rightMargin = Ui.dp(ctx, 2) })
                    row.addView(tile, LinearLayout.LayoutParams(Ui.dp(ctx, 82), Ui.dp(ctx, 102)).also { it.setMargins(Ui.dp(ctx, 3), Ui.dp(ctx, 3), Ui.dp(ctx, 3), Ui.dp(ctx, 3)) })
                }
                col.addView(row)
            }
            ScrollView(ctx).apply { addView(col) }
        }
    }
}
