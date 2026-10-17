package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.husarp.ankigo.R

// R4-V5 (picks 10A + 6A): the pieces of a Box / Pokédex tile that are not plain text: the sharp sprite, the golden glow of a shiny,
// the red NEW sticker, the mark icons and the "?" legend. Pure maths is in GridMath / TileMarks.

/**
 * A sprite drawn at a whole-number scale, nearest-neighbour (sharp pixels), centred in the room the view has. The picture never
 * leaves the view's bounds, so a tile that reserves the corners and the label strip around it never has its marks covered.
 * Placeholders without a size just fill the view.
 */
internal class TileSprite(ctx: Context) : ImageView(ctx) {
    init { super.setScaleType(ScaleType.MATRIX) }

    /** Always [ScaleType.MATRIX]: [Ui.staticSprite] asks for FIT_CENTER, but the scale is computed here. */
    override fun setScaleType(scaleType: ScaleType?) = Unit

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        fit()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fit()
    }

    private fun fit() {
        val d = drawable ?: return
        val dw = d.intrinsicWidth
        val dh = d.intrinsicHeight
        val aw = width - paddingLeft - paddingRight
        val ah = height - paddingTop - paddingBottom
        if (dw <= 0 || dh <= 0 || aw <= 0 || ah <= 0) { imageMatrix = Matrix(); return }
        val s = GridMath.tileScale(aw, ah, dw, dh)
        val m = Matrix()
        m.setScale(s, s)
        m.postTranslate(paddingLeft + (aw - dw * s) / 2f, paddingTop + (ah - dh * s) / 2f)
        imageMatrix = m
    }
}

/**
 * One line: child 0 is the name (it shrinks and ends with "…" when the line is short), the other children are the mark icons that
 * follow it right after the name and are never pushed out (Team rows, 6A).
 */
internal class NameMarksRow(ctx: Context) : LinearLayout(ctx) {
    init { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val name = getChildAt(0) as? android.widget.TextView
        val avail = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        if (name != null && avail > 0) {
            var rest = 0
            for (i in 1 until childCount) {
                val c = getChildAt(i)
                if (c.visibility == GONE) continue
                c.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
                val lp = c.layoutParams as MarginLayoutParams
                rest += c.measuredWidth + lp.marginStart + lp.marginEnd
            }
            val nlp = name.layoutParams as MarginLayoutParams
            val room = (avail - rest - nlp.marginStart - nlp.marginEnd).coerceAtLeast(0)
            if (name.maxWidth != room) name.maxWidth = room
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

/** A soft golden aura (radial, fading to nothing) that sits behind the sprite of a shiny tile. */
internal class ShinyGlow(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gold = Ui.c(ctx, R.color.ag_yellow)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val r = minOf(w, h) / 2f
        fun a(alpha: Int) = (gold and 0x00FFFFFF) or (alpha shl 24)
        paint.shader = if (r <= 0f) null else
            RadialGradient(w / 2f, h / 2f, r, intArrayOf(a(0xB0), a(0x70), a(0x00)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        if (paint.shader != null) canvas.drawCircle(width / 2f, height / 2f, minOf(width, height) / 2f, paint)
    }
}

internal object TileViews {
    /** The NEW sticker: small red tilted text, no background (the Round 2 look of the wild card's sticker). */
    fun newSticker(ctx: Context): android.widget.TextView = Ui.text(ctx, 8f, 900, R.color.ag_red).apply {
        text = "NEW"
        maxLines = 1
        letterSpacing = 0.04f
        rotation = -20f
        contentDescription = "New: caught today, not opened yet"
    }

    /** The icon of [mark] (NEW and SHINY have no icon: the sticker and the glow); null for those. [sizeDp] = the square it is drawn in. */
    fun markIcon(ctx: Context, mark: TileMark, sizeDp: Int): ImageView? {
        val icon = when (mark) {
            TileMark.FAVOURITE -> BoxViews.heartMark(ctx)
            TileMark.IN_TEAM -> BoxViews.teamMark(ctx)
            TileMark.MAIN -> BoxViews.icon(ctx, R.drawable.ic_swords, R.color.ag_peach)
            TileMark.NEW, TileMark.SHINY -> return null
        }
        icon.contentDescription = mark.label
        icon.layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, sizeDp), Ui.dp(ctx, sizeDp))
        return icon
    }

    /** A sample of the golden glow, for the legend. */
    private fun glowSample(ctx: Context): View = ShinyGlow(ctx)

    /** The small "?" chip that opens the legend (same 28 dp look / 48 dp touch as the other chips). */
    fun legendChip(ctx: Context, dex: Boolean): View = BoxViews.chip(ctx, "?", false) { showLegend(ctx, dex) }.also {
        it.contentDescription = "What the icons mean"
        it.gravity = Gravity.CENTER
        it.minWidth = Ui.dp(ctx, 38)
    }

    /** The legend: each tile mark with its words. A scrolling dialog, so it stays inside a small window. */
    fun showLegend(ctx: Context, dex: Boolean = false) {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 4), Ui.dp(ctx, 20), Ui.dp(ctx, 4))
        }
        for (mark in TileMark.entries) col.addView(legendRow(ctx, mark))
        if (dex) col.addView(legendRow(ctx, "★", R.color.ag_yellow, "Wishlist: a species on your wishlist"))
        col.addView(Ui.text(ctx, 12f, 700, R.color.ag_sub0).apply {
            text = "Held items, evolutions, fainted and regional forms are in a Pokémon's details."
            setPadding(0, Ui.dp(ctx, 10), 0, 0)
        })
        val dialog = AppDialog.Builder(ctx).setTitle("What the icons mean").setView(Bits.scroll(ctx, col))
            .setPositiveButton("Got it", null).create()
        Pickers.show(dialog)
    }

    private fun legendRow(ctx: Context, mark: TileMark): View {
        val spot: View = when (mark) {
            TileMark.NEW -> newSticker(ctx).also { it.gravity = Gravity.CENTER }
            TileMark.SHINY -> glowSample(ctx)
            else -> markIcon(ctx, mark, 18)!!
        }
        return legendRow(ctx, spot, mark.legend)
    }

    private fun legendRow(ctx: Context, glyph: String, tone: Int, words: String): View =
        legendRow(ctx, Ui.text(ctx, 14f, 900, tone).apply { text = glyph; gravity = Gravity.CENTER }, words)

    private fun legendRow(ctx: Context, spot: View, words: String): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = Ui.dp(ctx, 36)
        addView(spot, LinearLayout.LayoutParams(Ui.dp(ctx, 28), Ui.dp(ctx, 28)).also { it.marginEnd = Ui.dp(ctx, 12) })
        addView(Ui.text(ctx, 14f, 700, R.color.ag_text).apply { text = words }, LinearLayout.LayoutParams(0, -2, 1f))
    }
}
