package com.husarp.ankigo.ui

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Round 10 wave 2 (G): where the red "NEW" sticker sits on the wild info box. Pure (no android), so it is unit-tested.
 *
 * The sticker keeps today's look (8 sp red text, turned, pivot at its own top-left, scaled with the box). It goes on today's corner
 * (the box's top-left, overhanging it by 4 / 3 dp x the box scale, kept inside the panel's frame) when it fits there; otherwise on the
 * first other corner of the box where it fits: top-right, bottom-right, bottom-left. "Fits" = the whole turned sticker lies inside the
 * panel (not cut by its edge) and does not touch the HP numbers. The tilt mirrors with the corner (top-left / bottom-right -20 degrees,
 * the other two +20) so a top sticker never rises above its own corner. When no corner is free the least bad one is used and pushed
 * back into the panel ([Spot.fits] false).
 */
object NewTagRule {
    enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT }

    /** Today's tilt, in degrees (Android's rotation: clockwise positive). */
    const val TILT = 20f

    /** A rectangle in the panel's pixels. */
    class Box(val l: Float, val t: Float, val r: Float, val b: Float) {
        val w: Float get() = r - l
        val h: Float get() = b - t
        fun hits(o: Box) = l < o.r && o.l < r && t < o.b && o.t < b
        fun grow(m: Float) = Box(l - m, t - m, r + m, b + m)
        /** The area of this box that lies outside the panel [0, w] x [0, h]. */
        fun outside(w: Float, h: Float): Float {
            val iw = max(0f, min(r, w) - max(l, 0f))
            val ih = max(0f, min(b, h) - max(t, 0f))
            return max(0f, this.w * this.h - iw * ih)
        }
        fun overlap(o: Box): Float = max(0f, min(r, o.r) - max(l, o.l)) * max(0f, min(b, o.b) - max(t, o.t))
        override fun toString() = "($l,$t)-($r,$b)"
    }

    /** The result: the corner, the rotation to set, the sticker's pivot (its unturned top-left) in panel px, and the turned sticker's bounds. */
    class Spot(val corner: Corner, val angle: Float, val x: Float, val y: Float, val bounds: Box, val fits: Boolean)

    /**
     * True when the sticker stays exactly where today's layout put it: today's corner and it fits there. A top-left spot that does NOT fit
     * (nothing fits and the least bad one is today's, pushed back into the panel) carries a corrected position that must be applied.
     */
    fun keepsTodaysSpot(s: Spot): Boolean = s.corner == Corner.TOP_LEFT && s.fits

    fun angle(c: Corner): Float = if (c == Corner.TOP_LEFT || c == Corner.BOTTOM_RIGHT) -TILT else TILT

    /** The bounds of a [w] x [h] sticker drawn [scale] times bigger and turned [angle] degrees about its top-left corner placed at ([x], [y]). */
    fun turned(x: Float, y: Float, w: Float, h: Float, scale: Float, angle: Float): Box {
        val a = Math.toRadians(angle.toDouble())
        val c = cos(a).toFloat(); val s = sin(a).toFloat()
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (i in 0 until 4) {
            val u = (if (i and 1 == 0) 0f else w) * scale
            val v = (if (i and 2 == 0) 0f else h) * scale
            val px = x + u * c - v * s
            val py = y + u * s + v * c
            l = min(l, px); r = max(r, px); t = min(t, py); b = max(b, py)
        }
        return Box(l, t, r, b)
    }

    /**
     * [box] = the wild box as drawn (left, top, right, bottom, in the panel's px); [panelW] x [panelH] = the panel; [edge] = how far the
     * pivot of today's corner is kept from the panel's left / top (the frame's depth, 0 without a frame); [overX] / [overY] = how far the
     * sticker overhangs the box corner (4 / 3 dp x the box scale, in px); [tagW] x [tagH] = the sticker's own size, drawn [scale] times
     * bigger; [avoid] = the HP numbers' rectangle (a little padded), or null.
     */
    fun place(
        box: Box, panelW: Float, panelH: Float, edge: Float, overX: Float, overY: Float,
        tagW: Float, tagH: Float, scale: Float, avoid: Box?,
    ): Spot {
        var best: Spot? = null
        var bestPenalty = Float.MAX_VALUE
        for (corner in Corner.entries) {
            val ang = angle(corner)
            val rel = turned(0f, 0f, tagW, tagH, scale, ang)
            val x: Float
            val y: Float
            when (corner) {
                Corner.TOP_LEFT -> { x = max(edge, box.l - overX); y = max(edge, box.t - overY) }               // today's spot
                Corner.TOP_RIGHT -> { x = box.r + overX - rel.r; y = box.t - overY - rel.t }
                Corner.BOTTOM_RIGHT -> { x = box.r + overX - rel.r; y = box.b + overY - rel.b }
                Corner.BOTTOM_LEFT -> { x = box.l - overX - rel.l; y = box.b + overY - rel.b }
            }
            val bounds = Box(x + rel.l, y + rel.t, x + rel.r, y + rel.b)
            val out = bounds.outside(panelW, panelH)
            val hit = if (avoid == null) 0f else bounds.overlap(avoid)
            if (out <= EPS && hit <= EPS) return Spot(corner, ang, x, y, bounds, true)
            val penalty = out + 4f * hit
            if (penalty < bestPenalty - EPS) { bestPenalty = penalty; best = Spot(corner, ang, x, y, bounds, false) }
        }
        // nowhere free: the least bad corner, pushed back inside the panel where the sticker is small enough
        val s = best!!
        val dx = if (s.bounds.l < 0f) -s.bounds.l else if (s.bounds.r > panelW) panelW - s.bounds.r else 0f
        val dy = if (s.bounds.t < 0f) -s.bounds.t else if (s.bounds.b > panelH) panelH - s.bounds.b else 0f
        val ok = s.bounds.w <= panelW + EPS && s.bounds.h <= panelH + EPS
        val mx = if (ok) dx else 0f
        val my = if (ok) dy else 0f
        return Spot(s.corner, s.angle, s.x + mx, s.y + my, Box(s.bounds.l + mx, s.bounds.t + my, s.bounds.r + mx, s.bounds.b + my), false)
    }

    private const val EPS = 0.01f
}
