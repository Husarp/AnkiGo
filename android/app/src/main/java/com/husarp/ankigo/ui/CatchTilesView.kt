package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.PorterDuff
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Items
import kotlin.math.max
import kotlin.math.min

/**
 * The catch result tiles (PLAN 40, pick 8A): Pokédex, the throw, XP, coins as small rounded tiles with a coloured icon, a small
 * label and a bold value, and a NEXT line under them. It takes the log card's place under the arena ([BattlePanelView]).
 * Two layouts: the big one (two columns, labels, NEXT line) and the one-line one for small windows and short panels (icon +
 * short value per tile, the last tiles drop out when they do not fit). The texts come from [CatchTilesPlan]; it only draws.
 */
internal class CatchTilesView(ctx: Context) : LinearLayout(ctx) {
    init { orientation = VERTICAL }

    private fun dpi(v: Int) = Ui.dp(context, v)

    /**
     * The result is held until a tap on Continue ([cont] given: the NEXT line gets a Continue pill and the tiles take the tap too);
     * null = nothing to tap (the Study screen has its own button, small windows run on a timeout).
     */
    private fun setContinue(cont: (() -> Unit)?) {
        setOnClickListener(cont?.let { c -> View.OnClickListener { c() } })
        isClickable = cont != null
    }

    /** Fills the view with the big layout for [s]; [next] adds the NEXT line, [cont] its Continue tap (see [setContinue]). */
    fun bindGrid(s: CatchSummary, next: Boolean, cont: (() -> Unit)? = null) {
        removeAllViews()
        setContinue(cont)
        background = null
        setPadding(0, 0, 0, 0)
        val tiles = CatchTilesPlan.tiles(s) { Items.name(it) }
        var i = 0
        while (i < tiles.size) {
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            row.addView(bigTile(tiles[i]), LayoutParams(0, -2, 1f))
            val second = tiles.getOrNull(i + 1)
            row.addView(second?.let { bigTile(it) } ?: View(context), LayoutParams(0, if (second != null) -2 else 0, 1f).also { it.marginStart = dpi(6) })   // an empty View would fill the height
            addView(row, LayoutParams(-1, -2).also { if (i > 0) it.topMargin = dpi(6) })
            i += 2
        }
        if (next) addView(nextLine(s.next, cont), LayoutParams(-1, -2).also { it.topMargin = dpi(8); it.marginStart = dpi(2) })
    }

    /**
     * Fills the view with the one-line layout for [s]: a flat card like the log card ([radiusDp] corners, [card] colour) with the
     * short tiles in a row; [rowH] px tall. [next] puts the NEXT line under the row (the panel has room for it).
     */
    fun bindRow(s: CatchSummary, next: Boolean, rowH: Int, radiusDp: Float, cont: (() -> Unit)? = null) {
        removeAllViews()
        setContinue(cont)
        background = Ui.rounded(Ui.c(context, R.color.ag_card), radiusDp, context)
        setPadding(dpi(10), dpi(2), dpi(8), dpi(2))
        gravity = Gravity.CENTER_VERTICAL
        val row = TileRow(context)
        val h = if (next) dpi(26) else rowH - dpi(4)
        val ic = max(dpi(14), min(dpi(16), h - dpi(6)))
        for (t in CatchTilesPlan.rowTiles(s)) row.addView(miniTile(t, ic), TileRow.full())
        for (t in CatchTilesPlan.rowTiles(s, compact = true)) row.addView(miniTile(t, ic), TileRow.compact())
        addView(row, LayoutParams(-1, if (next) h else -2))
        if (next) addView(nextLine(s.next, cont), LayoutParams(-1, -2).also { it.topMargin = dpi(1) })
    }

    // ---------------------------------------------------------------- parts

    @ColorRes private fun tone(k: CatchTilesPlan.Kind) = when (k) {
        CatchTilesPlan.Kind.DEX -> R.color.ag_teal
        CatchTilesPlan.Kind.DEFEATED -> R.color.ag_red
        CatchTilesPlan.Kind.THROW -> R.color.ag_red
        CatchTilesPlan.Kind.XP -> R.color.ag_blue
        CatchTilesPlan.Kind.COINS -> R.color.ag_yellow
        CatchTilesPlan.Kind.ASK -> R.color.ag_teal
    }

    @DrawableRes private fun icon(k: CatchTilesPlan.Kind) = when (k) {
        CatchTilesPlan.Kind.DEX -> R.drawable.ic_dex
        CatchTilesPlan.Kind.DEFEATED -> R.drawable.ic_swords
        CatchTilesPlan.Kind.THROW -> R.drawable.ic_ball
        CatchTilesPlan.Kind.XP -> R.drawable.ic_up
        CatchTilesPlan.Kind.COINS -> R.drawable.ic_money
        CatchTilesPlan.Kind.ASK -> R.drawable.ic_ball
    }

    /** R19 174B: a bare icon, [sizePx] wide; the ball stays the real item picture. */
    private fun iconBox(t: CatchTilesPlan.Tile, sizePx: Int): View {
        val color = Ui.c(context, tone(t.kind))
        val box = FrameLayout(context)
        val pad = (sizePx * 0.2f).toInt()
        val img = ImageView(context)
        if (t.ball != null) {
            img.setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
            Ui.itemIcon(context, t.ball, img)
        } else {
            img.setImageResource(icon(t.kind))
            if (t.kind != CatchTilesPlan.Kind.COINS) img.setColorFilter(color, PorterDuff.Mode.SRC_IN) // the gold money stack is never tinted
            img.setPadding(pad, pad, pad, pad)
        }
        box.addView(img, FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER))
        return box
    }

    private fun bigTile(t: CatchTilesPlan.Tile): View {
        val tile = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(Ui.c(context, R.color.ag_card), 12, context)
            setPadding(dpi(7), dpi(6), dpi(8), dpi(6))
            contentDescription = "${t.label}: ${t.value} ${t.extra}".trim()
        }
        tile.addView(iconBox(t, dpi(28)), LayoutParams(dpi(28), dpi(28)).also { it.marginEnd = dpi(8) })
        val col = LinearLayout(context).apply { orientation = VERTICAL }
        col.addView(Ui.text(context, 10f, 800, R.color.ag_sub0).apply { text = t.label; oneLine() })
        val value = Ui.text(context, 13f, 900, R.color.ag_text).apply {
            text = if (t.extra.isEmpty()) t.value else SpanTail.tail(context, t.value, " " + t.extra)
            oneLine()
        }
        col.addView(value)
        tile.addView(col, LayoutParams(0, -2, 1f))
        return tile
    }

    /** R19 174B: a bare icon (no circle behind it) and the words; the tile says the whole fact to TalkBack. */
    private fun miniTile(t: CatchTilesPlan.Tile, iconPx: Int): View {
        val tile = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            contentDescription = t.desc.ifEmpty { t.value }
        }
        val img = ImageView(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        if (t.ball != null) Ui.itemIcon(context, t.ball, img)
        else {
            img.setImageResource(icon(t.kind))
            if (t.kind != CatchTilesPlan.Kind.COINS) img.setColorFilter(Ui.c(context, tone(t.kind)), PorterDuff.Mode.SRC_IN)  // the gold money stack is never tinted
        }
        tile.addView(img, LayoutParams(iconPx, iconPx).also { it.marginEnd = dpi(5) })
        tile.addView(Ui.text(context, 11.5f, 900, R.color.ag_text).apply { text = t.value; oneLine(); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO })
        return tile
    }

    private fun nextLine(text: String, cont: (() -> Unit)? = null): View {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(Ui.text(context, 10f, 900, R.color.ag_mauve).apply { this.text = "NEXT"; letterSpacing = 0.06f }, LayoutParams(-2, -2).also { it.marginEnd = dpi(6) })
        row.addView(Ui.text(context, 11f, 700, R.color.ag_sub0).apply { this.text = text; oneLine() }, LayoutParams(0, -2, 1f))
        if (cont != null) row.addView(continuePill(cont), LayoutParams(-2, -2).also { it.marginStart = dpi(8) })
        return row
    }

    /** The small "Continue ›" pill at the end of the NEXT line (the same mauve as the Study screen's Continue button). */
    private fun continuePill(cont: () -> Unit): View = Ui.text(context, 10.5f, 900, R.color.ag_base).apply {
        text = "${ContinueRule.LABEL} ›"
        gravity = Gravity.CENTER
        setPadding(dpi(9), dpi(2), dpi(9), dpi(2))
        background = StudyUi.ripple(context, Ui.rounded(Ui.c(context, R.color.ag_mauve), 9, context))
        contentDescription = "${ContinueRule.LABEL}, ${ContinueRule.HINT}"
        setOnClickListener { cont() }
        oneLine()
    }

    private fun TextView.oneLine() { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }

    /**
     * A light row that never wraps: the full-word tiles, or (when those do not fit) the compact-word ones; if even those do not fit
     * the tiles from the end drop out. Children are added with [full] / [compact] layout params.
     */
    private class TileRow(ctx: Context) : ViewGroup(ctx) {
        class P(val compact: Boolean) : ViewGroup.LayoutParams(-2, -2)
        companion object { fun full() = P(false); fun compact() = P(true) }

        private val gap = Ui.dp(ctx, 16)
        private val shown = HashSet<View>()

        override fun checkLayoutParams(p: ViewGroup.LayoutParams?) = p is P
        override fun generateDefaultLayoutParams() = P(false)

        private fun fit(compact: Boolean, avail: Int, into: MutableSet<View>): Int {
            var x = 0
            var h = 0
            for (i in 0 until childCount) {
                val k = getChildAt(i)
                if ((k.layoutParams as P).compact != compact) continue
                k.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                val need = x + (if (x > 0) gap else 0) + k.measuredWidth
                if (need > avail && into.isNotEmpty()) continue
                into += k
                x = need
                h = max(h, k.measuredHeight)
            }
            return h
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val avail = MeasureSpec.getSize(widthMeasureSpec)
            shown.clear()
            var h = fit(false, avail, shown)
            val all = (0 until childCount).count { !(getChildAt(it).layoutParams as P).compact }
            if (shown.size < all) { shown.clear(); h = fit(true, avail, shown) }
            for (i in 0 until childCount) {
                val k = getChildAt(i)
                k.visibility = if (k in shown) View.VISIBLE else View.GONE
            }
            val hs = MeasureSpec.getSize(heightMeasureSpec)
            setMeasuredDimension(avail, if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) hs else h)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            var x = 0
            for (i in 0 until childCount) {
                val k = getChildAt(i)
                if (k !in shown) { k.layout(0, 0, 0, 0); continue }
                val top = (height - k.measuredHeight) / 2
                k.layout(x, top, x + k.measuredWidth, top + k.measuredHeight)
                x += k.measuredWidth + gap
            }
        }
    }
}

/** A value with a smaller grey tail ("Throw 2  · 12 %"). */
private object SpanTail {
    fun tail(ctx: Context, head: String, tail: String): CharSequence {
        val sb = android.text.SpannableStringBuilder(head)
        val at = sb.length
        sb.append(tail)
        sb.setSpan(android.text.style.ForegroundColorSpan(Ui.c(ctx, R.color.ag_sub0)), at, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(android.text.style.RelativeSizeSpan(0.85f), at, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sb
    }
}
