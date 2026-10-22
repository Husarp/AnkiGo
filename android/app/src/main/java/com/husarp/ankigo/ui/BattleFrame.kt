package com.husarp.ankigo.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.math.abs

/**
 * The battle frame setting (Round 9 wave 2, D2; docs/design/round9.html `FR` + `drawFrame`): a pixel-art border around the battle
 * picture (the arena). [NONE] is the default (no frame). The colours are fixed art, the same in light and dark. One art pixel is
 * 2 dp on screen ([BattleFrame.unitPx]).
 *
 * A style is pure data: [t] pixels thick on every side, [reach] the most pixels any edge cell goes in (the ice's icicles hang
 * deeper than the frame is thick), [edge] the colour of the cell at position `i` along a side and depth `d` from the outside
 * (side 't' top, 'b' bottom, 'l' left, 'r' right; 0 = nothing), and an optional square [corner] pattern (letters into [pal],
 * '.' = nothing) mirrored into the four corners on top of the edges.
 */
enum class BattleFrameStyle(
    val label: String,
    val sub: String,
    val t: Int,
    val reach: Int,
    private val edgeAt: (i: Int, d: Int, side: Char) -> Int,
    val corner: List<String> = emptyList(),
    private val pal: Map<Char, Int> = emptyMap(),
) {
    NONE("No frame", "The battle picture reaches the edge", 0, 0, { _, _, _ -> 0 }),

    SIMPLE("Simple pixel", "A thin dark line with a soft light line inside", 2, 2, { _, d, _ ->
        when (d) { 0 -> 0xFF1B1B26.toInt(); 1 -> 0x99FFFFFF.toInt(); else -> 0 }
    }),

    WOOD("Wood", "Brown planks with brass corners", 3, 3, { i, d, _ ->
        when (d) {
            0 -> 0xFF2B180B.toInt()
            2 -> 0xFF4A2E14.toInt()
            1 -> if (i % 9 == 0 || i % 13 == 5) 0xFF6E4520.toInt() else if (i % 2 != 0) 0xFF97632F.toInt() else 0xFF8A5A2B.toInt()
            else -> 0
        }
    }, listOf("kkkkkk", "kGGGGk", "kGhHGk", "kGHHGk", "kGGGGk", "kkkkkk"),
        mapOf('k' to 0xFF2B180B.toInt(), 'G' to 0xFFB8892E.toInt(), 'h' to 0xFFF3D27A.toInt(), 'H' to 0xFF7A5A16.toInt())),

    STONE("Stone", "Grey bricks with a block on each corner", 3, 3, { i, d, _ ->
        when {
            d == 0 -> 0xFF33343B.toInt()
            d > 2 -> 0
            (i + (if (d == 1) 0 else 3)) % 6 == 0 -> 0xFF5B5D66.toInt()
            d == 1 -> 0xFFACAEB8.toInt()
            else -> 0xFF8B8D97.toInt()
        }
    }, listOf("kkkkkk", "kLLLLk", "kLMMDk", "kLMMDk", "kLDDDk", "kkkkkk"),
        mapOf('k' to 0xFF33343B.toInt(), 'L' to 0xFFD0D2DB.toInt(), 'M' to 0xFFA6A8B3.toInt(), 'D' to 0xFF6B6D77.toInt())),

    GOLD("Gold", "A shiny gold rim with a ruby on each corner", 3, 3, { i, d, _ ->
        when (d) {
            0 -> 0xFF5A3D06.toInt()
            1 -> if (i % 6 == 0) 0xFFFFF3B8.toInt() else 0xFFF2C94C.toInt()
            2 -> 0xFFB8860B.toInt()
            else -> 0
        }
    }, listOf("kkkkkkk", "kYYYYYk", "kYwRRYk", "kYRRrYk", "kYRrrYk", "kYYYYYk", "kkkkkkk"),
        mapOf('k' to 0xFF5A3D06.toInt(), 'Y' to 0xFFE8B931.toInt(), 'w' to 0xFFFFFFFF.toInt(), 'R' to 0xFFE0245E.toInt(), 'r' to 0xFF8C1238.toInt())),

    ICE("Ice", "Pale blue ice with icicles hanging from the top", 2, 6, { i, d, side ->
        when {
            d == 0 -> 0xFF2F5F7C.toInt()
            d == 1 -> if (i % 9 == 0) 0xFFFFFFFF.toInt() else 0xFFBFE9FF.toInt()
            side != 't' -> 0
            else -> {
                val len = ICICLES[i % ICICLES.size]
                if (d - 2 < len) (if (d - 2 == len - 1) 0xFFFFFFFF.toInt() else 0xFFDFF6FF.toInt()) else 0
            }
        }
    }, listOf("kkkkk..", "kWBBk..", "kBWBkk.", "kBBWWk.", "kkkWBk.", "..kkkk.", "......."),
        mapOf('k' to 0xFF2F5F7C.toInt(), 'W' to 0xFFFFFFFF.toInt(), 'B' to 0xFFA8DEF7.toInt())),

    METAL("Dark metal", "Dark steel with rivets and bolted corners", 3, 3, { i, d, _ ->
        when (d) {
            0 -> 0xFF0E1015.toInt()
            1 -> when (i % 12) { 6 -> 0xFFC5CCD9.toInt(); 5, 7 -> 0xFF7D8596.toInt(); else -> 0xFF4A5060.toInt() }
            2 -> 0xFF23262F.toInt()
            else -> 0
        }
    }, listOf("kkkkkk", "kMMMMk", "kMbBMk", "kMBBMk", "kMMMMk", "kkkkkk"),
        mapOf('k' to 0xFF0E1015.toInt(), 'M' to 0xFF5A6072.toInt(), 'b' to 0xFFD5DBE6.toInt(), 'B' to 0xFF3A3F4B.toInt()));

    /** The colour (ARGB) of the edge cell at [i] along [side] and depth [d]; 0 = nothing there. */
    fun edge(i: Int, d: Int, side: Char): Int = if (d < 0 || d >= reach) 0 else edgeAt(i, d, side)

    /** The colour of the corner cell at ([x], [y]) counted from the picture's corner; 0 = nothing there. */
    fun cornerAt(x: Int, y: Int): Int {
        val row = corner.getOrNull(y) ?: return 0
        val ch = row.getOrNull(x) ?: return 0
        return pal[ch] ?: 0
    }

    val hasCorner: Boolean get() = corner.isNotEmpty()

    /**
     * PLAN 140: how far (art pixels) the corner pattern reaches along each side: the side of the square it fills at every corner
     * (the farthest drawn cell, counted from the picture's corner); 0 = no corner pattern.
     */
    val cornerUnits: Int get() {
        var n = 0
        for ((y, row) in corner.withIndex()) for (x in row.indices) if (cornerAt(x, y) != 0) n = maxOf(n, x + 1, y + 1)
        return n
    }

    companion object {
        private val ICICLES = intArrayOf(0, 0, 3, 1, 0, 2, 0, 0, 4, 1, 0)

        /** The saved choice ([BattleFrame.KEY] holds the enum name); unknown or missing = [NONE]. */
        fun parse(name: String?): BattleFrameStyle = entries.firstOrNull { it.name == name } ?: NONE
    }
}

object BattleFrame {
    /** The preference key (a [BattleFrameStyle] name). */
    const val KEY = "battleFrame"

    /** PLAN 140: the safe area of [style] drawn with [unit] (art pixel, in the arena's own dp); see [FrameSafe]. */
    internal fun safe(style: BattleFrameStyle, unit: Float): FrameSafe =
        if (style == BattleFrameStyle.NONE) FrameSafe.NONE else FrameSafe(style.t * unit, style.reach * unit, style.cornerUnits * unit)

    /**
     * One art pixel in whole px of the view it is drawn in: 2 dp on screen. The arena is drawn scaled by the panel scale
     * ([PanelFit.Fit.scale]), so in its own (virtual) px it is 2 dp / scale, rounded, at least 2.
     */
    fun unitPx(density: Float, scale: Float, dp: Float = 2f): Int =
        Math.round(dp * density / scale.coerceAtLeast(0.05f)).coerceAtLeast(2)
}

/**
 * PLAN 140: the part of a battle picture a [BattleFrameStyle] leaves free for the info boxes, in one unit (the arena's virtual dp):
 * [edge] is the band on the left, right and bottom sides, [top] the band at the top (the ice's icicles hang deeper there than the
 * band is thick), [corner] the side of the square the corner pattern covers at each of the four corners. A box is "clear" when it is
 * inside the edges and touches none of the four corner squares; nothing of the frame is then drawn over it. [NONE] (all 0) changes
 * nothing.
 */
internal class FrameSafe(val edge: Float, val top: Float, val corner: Float) {
    val none: Boolean get() = edge <= 0f && top <= 0f && corner <= 0f

    /**
     * Moves the box [out] (x, y, w, h, in place) so it is inside the edges and clear of the corner squares of a [vw] x [vh] picture. A
     * box that hits a corner square moves to the nearest place that touches none of the four (each axis stays, or sits on the inner
     * side of a left / right, top / bottom corner square, inside the edges; the shortest move wins, along x on a tie); a box too big
     * for any such place stays where the edges put it. Every place is checked against all four corners, so leaving one corner never
     * lands in another. No allocation.
     */
    fun clearInto(out: FloatArray, vw: Float, vh: Float) {
        if (none) return
        val w = out[2]
        val h = out[3]
        val x = maxOf(edge, minOf(vw - edge - w, out[0]))
        val y = maxOf(top, minOf(vh - edge - h, out[1]))
        out[0] = x; out[1] = y
        if (corner <= 0f || !hitsCorner(x, y, w, h, vw, vh)) return
        var best = Float.MAX_VALUE
        var bestDy = Float.MAX_VALUE
        for (i in 0 until 3) {
            val cx = when (i) { 0 -> x; 1 -> corner; else -> vw - corner - w }
            if (i > 0 && (cx < edge - EPS || cx + w > vw - edge + EPS)) continue
            for (j in 0 until 3) {
                val cy = when (j) { 0 -> y; 1 -> corner; else -> vh - corner - h }
                if (j > 0 && (cy < top - EPS || cy + h > vh - edge + EPS)) continue
                if (hitsCorner(cx, cy, w, h, vw, vh)) continue
                val dy = abs(cy - y)
                val cost = abs(cx - x) + dy
                if (cost < best - EPS || (cost <= best + EPS && dy < bestDy - EPS)) { best = cost; bestDy = dy; out[0] = cx; out[1] = cy }
            }
        }
    }

    private fun hitsCorner(x: Float, y: Float, w: Float, h: Float, vw: Float, vh: Float): Boolean {
        for (c in 0 until 4) {
            val cl = if ((c and 1) == 1) vw - corner else 0f
            val ct = if (c >= 2) vh - corner else 0f
            if (x < cl + corner - EPS && cl < x + w - EPS && y < ct + corner - EPS && ct < y + h - EPS) return true
        }
        return false
    }

    /** [b] moved as [clearInto] says (the same box when nothing moves). */
    fun clear(b: ArenaGeo.Box, vw: Float, vh: Float): ArenaGeo.Box {
        if (none) return b
        val a = floatArrayOf(b.x, b.y, b.w, b.h)
        clearInto(a, vw, vh)
        return if (abs(a[0] - b.x) < 0.0001f && abs(a[1] - b.y) < 0.0001f) b else ArenaGeo.Box(a[0], a[1], b.w, b.h)
    }

    /** True when the box (x, y, w, h) is inside the edges and touches no corner square: nothing of the frame is drawn over it. */
    fun isClear(x: Float, y: Float, w: Float, h: Float, vw: Float, vh: Float): Boolean {
        if (none) return true
        if (x < edge - EPS || y < top - EPS || x + w > vw - edge + EPS || y + h > vh - edge + EPS) return false
        return corner <= 0f || !hitsCorner(x, y, w, h, vw, vh)
    }

    companion object {
        val NONE = FrameSafe(0f, 0f, 0f)
        private const val EPS = 0.01f
    }
}

/**
 * Draws a [BattleFrameStyle] around its bounds in whole-pixel squares ([unit] px each, no anti-aliasing, no gaps); a port of the
 * mockup's `drawFrame`. It catches no touches. Reuses one Paint; runs of one colour along an edge are one rectangle.
 */
class BattleFrameDrawable(style: BattleFrameStyle = BattleFrameStyle.NONE, unit: Int = 2) : Drawable() {
    var style: BattleFrameStyle = style
        private set
    var unit: Int = unit.coerceAtLeast(1)
        private set

    private val paint = Paint().also { it.isAntiAlias = false; it.isFilterBitmap = false; it.style = Paint.Style.FILL }

    /** Changes style or unit and redraws; does nothing when nothing changed. */
    fun set(style: BattleFrameStyle, unit: Int) {
        val u = unit.coerceAtLeast(1)
        if (style == this.style && u == this.unit) return
        this.style = style
        this.unit = u
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val s = style
        if (s == BattleFrameStyle.NONE) return
        val b = bounds
        val w = b.width()
        val h = b.height()
        if (w <= 0 || h <= 0) return
        val u = unit
        val nx = (w + u - 1) / u
        val ny = (h + u - 1) / u
        val save = canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.clipRect(0, 0, w, h)
        for (d in 0 until s.reach) {
            horizontal(canvas, s, 't', d, nx, d * u)
            if (d < s.t) horizontal(canvas, s, 'b', d, nx, h - (d + 1) * u)
            if (d < s.t) {
                vertical(canvas, s, 'l', d, ny, d * u)
                vertical(canvas, s, 'r', d, ny, w - (d + 1) * u)
            }
        }
        if (s.hasCorner) {
            val n = s.corner.size
            for (fy in 0..1) for (fx in 0..1) for (y in 0 until n) for (x in 0 until s.corner[y].length) {
                val c = s.cornerAt(x, y)
                if (c == 0) continue
                val px = if (fx == 1) w - (x + 1) * u else x * u
                val py = if (fy == 1) h - (y + 1) * u else y * u
                fill(canvas, c, px, py, px + u, py + u)
            }
        }
        canvas.restoreToCount(save)
    }

    private fun horizontal(canvas: Canvas, s: BattleFrameStyle, side: Char, d: Int, n: Int, y: Int) {
        var run = 0
        var from = 0
        for (i in 0..n) {
            val c = if (i < n) s.edge(i, d, side) else 0
            if (c == run && i < n) continue
            if (run != 0) fill(canvas, run, from * unit, y, i * unit, y + unit)
            run = c
            from = i
        }
    }

    private fun vertical(canvas: Canvas, s: BattleFrameStyle, side: Char, d: Int, n: Int, x: Int) {
        var run = 0
        var from = 0
        for (i in 0..n) {
            val c = if (i < n) s.edge(i, d, side) else 0
            if (c == run && i < n) continue
            if (run != 0) fill(canvas, run, x, from * unit, x + unit, i * unit)
            run = c
            from = i
        }
    }

    private fun fill(canvas: Canvas, color: Int, l: Int, t: Int, r: Int, b: Int) {
        paint.color = color
        canvas.drawRect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat(), paint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** A small plain rectangle (`ag_mantle`) with [style] drawn around it, half the real size (1 dp art pixels): the Settings row's preview. */
internal class BattleFramePreview(ctx: android.content.Context, style: BattleFrameStyle) : android.view.View(ctx) {
    init {
        setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_mantle))
        if (style != BattleFrameStyle.NONE) foreground = BattleFrameDrawable(style, Math.round(resources.displayMetrics.density).coerceAtLeast(1))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
}
