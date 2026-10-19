package com.husarp.ankigo.ui

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * R9-W3-A (D7-A "Two-sided strength"): the fill of a number in Compare's "Best of all". Pure maths (colour ints), unit-tested.
 * [p] = 0 is the row's worst value .. 1 its best. The middle stays the plain row band; the further a value is from the middle the
 * stronger its red (below) or green (above): a = 0.14 + 0.66 * d^1.2 is the share of the colour over the band.
 */
object CompareScale {
    /** [fill] null = no fill (the plain row band); [ink] null = the normal text colour. */
    class Look(val fill: Int?, val ink: Int?)

    const val MIDDLE = 0.12f
    const val INK_SWITCH = 0.55f
    private const val RED_LIGHT = 0xFFF2545B.toInt()
    private const val RED_DARK = 0xFFFF6B7A.toInt()
    private const val GREEN_LIGHT = 0xFF2FB36A.toInt()
    private const val GREEN_DARK = 0xFF4FD987.toInt()
    private const val INK_LIGHT = 0xFFFFFFFF.toInt()
    private const val INK_DARK = 0xFF11111B.toInt()

    /** How far from the middle: 0 (exactly in the middle) .. 1 (an end). */
    fun distance(p: Float): Float = abs(p.coerceIn(0f, 1f) - 0.5f) * 2f

    /** The share of the colour mixed over the band for a distance [d] (0 below the [MIDDLE] cut: no fill). */
    fun share(d: Float): Float = if (d < MIDDLE) 0f else (0.14 + 0.66 * d.toDouble().pow(1.2)).toFloat()

    fun look(p: Float, band: Int, dark: Boolean): Look {
        val q = p.coerceIn(0f, 1f)
        val d = distance(q)
        if (d < MIDDLE) return Look(null, null)
        val col = if (q < 0.5f) (if (dark) RED_DARK else RED_LIGHT) else (if (dark) GREEN_DARK else GREEN_LIGHT)
        val a = share(d)
        return Look(mix(band, col, a), if (a > INK_SWITCH) (if (dark) INK_DARK else INK_LIGHT) else null)
    }

    /** The legend: [STEPS] colours from the worst to the best value over [card] (the plain colour where a value has no fill). */
    const val STEPS = 21
    fun legend(card: Int, dark: Boolean): IntArray = IntArray(STEPS) { look(it / (STEPS - 1f), card, dark).fill ?: card }

    /** [a] with [t] (0..1) of [b] mixed in; alpha 255. */
    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
