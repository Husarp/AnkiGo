package com.husarp.ankigo.ui

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * R19-I (PLAN 244): the room a floating text's letters keep inside the shape drawn round them (pure maths, no views). The burst of
 * "Super effective!" and the stat badge's pill are measured from the real ink; the burst's font shrinks inside the same star until
 * no letter comes closer than [ROOM_DP] to its edge. Distances are in px, [d] = px per dp.
 */
internal object FloatFrame {
    /** The least room (dp) between any letter and the drawn frame's inner edge. */
    const val ROOM_DP = 1.5f
    /** The text paint's italic skew (negative [android.graphics.Paint.setTextSkewX] = leaning right). */
    const val SKEW = 0.22f
    /** The burst's outline stroke (dp) and the inset of its tips from the view's edge (dp), as FloatTextView draws them. */
    const val BURST_STROKE_DP = 2f
    const val BURST_INSET_DP = 1.5f
    /** The badge's pill outline (dp). */
    const val PILL_STROKE_DP = 1.5f
    /** The star: 16 points, valleys at this share of the tips' radius. */
    const val TIPS = 16
    const val VALLEY = 0.86f
    /** The smallest share of the font the burst may shrink to before its frame grows instead. */
    private const val MIN_FONT = 0.7f

    /** An ink rectangle centred on (cx, cy) of the frame's own centred coordinates. */
    class Ink(val cx: Float, val cy: Float, val w: Float, val h: Float) {
        fun corners() = arrayOf(floatArrayOf(cx - w / 2, cy - h / 2), floatArrayOf(cx + w / 2, cy - h / 2), floatArrayOf(cx - w / 2, cy + h / 2), floatArrayOf(cx + w / 2, cy + h / 2))
    }

    /** The burst: the frame size ([w] x [h], px) and the font share [k] (1 = full size) that keeps the room. */
    class Burst(val w: Float, val h: Float, val k: Float)

    /** The ink box of a skewed label [tw] x [th] (the lean adds [SKEW] x height to the width). */
    fun skewedInk(tw: Float, th: Float, k: Float = 1f): Ink = Ink(0f, 0f, (tw + SKEW * th) * k, th * k)

    /** The star's outline points (centred coordinates) for a frame [w] x [h]. */
    fun burstPoints(w: Float, h: Float, d: Float): List<FloatArray> {
        val rx = w / 2f - BURST_INSET_DP * d
        val ry = h / 2f - BURST_INSET_DP * d
        return (0 until TIPS * 2).map { i ->
            val a = Math.PI * i / TIPS
            val r = if (i % 2 == 0) 1f else VALLEY
            floatArrayOf((rx * r * cos(a)).toFloat(), (ry * r * sin(a)).toFloat())
        }
    }

    private fun segDist(px: Float, py: Float, a: FloatArray, b: FloatArray): Float {
        val dx = b[0] - a[0]; val dy = b[1] - a[1]
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((px - a[0]) * dx + (py - a[1]) * dy) / len2).coerceIn(0f, 1f)
        return hypot(px - (a[0] + t * dx), py - (a[1] + t * dy))
    }

    private fun inside(px: Float, py: Float, poly: List<FloatArray>): Boolean {
        var c = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[j]
            if ((a[1] > py) != (b[1] > py) && px < (b[0] - a[0]) * (py - a[1]) / (b[1] - a[1]) + a[0]) c = !c
            j = i
        }
        return c
    }

    /** Room (px) between the closest letter corner and the burst's drawn edge (its inner side: the half stroke is taken off); negative = outside. */
    fun burstRoom(ink: Ink, w: Float, h: Float, d: Float): Float {
        val poly = burstPoints(w, h, d)
        var best = Float.MAX_VALUE
        for (c in ink.corners()) {
            var m = Float.MAX_VALUE
            for (i in poly.indices) m = minOf(m, segDist(c[0], c[1], poly[i], poly[(i + 1) % poly.size]))
            val signed = if (inside(c[0], c[1], poly)) m else -m
            best = minOf(best, signed - BURST_STROKE_DP * d / 2f)
        }
        return best
    }

    /**
     * The burst for a label [tw] x [th] at font size [sizePx]: the frame as it has always been drawn ([oldW] x [oldH]); the font shrinks
     * in steps of 2 % (down to [MIN_FONT]) until [burstRoom] is at least [ROOM_DP]; if even that is short the frame grows 4 % at a time.
     */
    fun burst(tw: Float, th: Float, oldW: Float, oldH: Float, d: Float): Burst {
        var w = oldW; var h = oldH
        val need = ROOM_DP * d
        repeat(40) {
            var k = 1f
            while (k >= MIN_FONT - 0.001f) {
                if (burstRoom(skewedInk(tw, th, k), w, h, d) >= need) return Burst(w, h, k)
                k -= 0.02f
            }
            w *= 1.04f; h *= 1.04f
        }
        return Burst(w, h, MIN_FONT)
    }

    /**
     * Room (px) between the closest [inks] corner and a stadium-shaped pill [w] x [h] (end caps of radius h / 2), its half stroke taken
     * off. The inks sit in the pill's centred coordinates.
     */
    fun pillRoom(inks: List<Ink>, w: Float, h: Float, d: Float): Float {
        val r = h / 2f
        val l = max(0f, w / 2f - r)
        var best = Float.MAX_VALUE
        for (ink in inks) for (c in ink.corners()) {
            val dx = max(abs(c[0]) - l, 0f)
            best = minOf(best, r - hypot(dx, c[1]) - PILL_STROKE_DP * d / 2f)
        }
        return best
    }

    /** The least pill height (px) for ink [inkH] high: [ROOM_DP] of room above and below it, inside the pill's outline. */
    fun pillMinH(inkH: Float, d: Float): Float = inkH + 2f * (ROOM_DP + PILL_STROKE_DP / 2f) * d

    /** The badge's horizontal padding (px each side) from [startPad]: grown in half-dp steps until [pillRoom] reaches [ROOM_DP]. */
    fun pillPad(startPad: Float, h: Float, d: Float, inksFor: (Float) -> Pair<Float, List<Ink>>): Float {
        var pad = startPad
        repeat(60) {
            val (w, inks) = inksFor(pad)
            if (pillRoom(inks, w, h, d) >= ROOM_DP * d) return pad
            pad += 0.5f * d
        }
        return pad
    }
}
