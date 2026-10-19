package com.husarp.ankigo.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The pixel look of the battle info boxes' HP / XP bars (Round 10 W8, docs mock D9-E): the maths, in whole pixels, so it is unit-tested
 * on the JVM. A bar is an outline [unit] thick with its corner squares left out, the track inside it, and the fill in 5 % steps with a
 * light band on top and a darker one at the bottom. The mock's bar is `hh` tall inside plus an outline of one unit on each side; here
 * [outerPx] gives that total, which the card lays the bar out at (so the box grows by 2 units, like the mock).
 */
internal object PixelBar {
    /** The fill moves in steps of this fraction (5 %). */
    const val STEPS = 20

    /** The unit (px) for an inner height of [innerPx] at [density]: max(1 px, round(inner dp / 7 x 2) / 2 dp), whole pixels. */
    fun unitPx(innerPx: Int, density: Float): Int {
        val dp = innerPx / density
        val unitDp = (dp / 7f * 2f).roundToInt() / 2f
        return max(1, (unitDp * density).roundToInt())
    }

    /** The height (px) the whole bar takes for an inner height of [innerPx]: the inner part plus an outline unit on each side. */
    fun outerPx(innerPx: Int, density: Float): Int = innerPx + 2 * unitPx(innerPx, density)

    /** The unit of a bar [outerPx] tall (the inverse of [outerPx]); 0 when it is too thin to hold an outline and a track pixel. */
    fun unitOfOuter(outerPx: Int, density: Float): Int {
        if (outerPx < 3) return 0
        var u = min(unitPx(outerPx, density), (outerPx - 1) / 2)
        while (u > 1 && unitPx(outerPx - 2 * u, density) != u) u--
        return max(1, u)
    }

    /** The fraction drawn: [f] (0..1) in 5 % steps, but a bar that is not empty keeps one step so it never looks dead. */
    fun step(f: Float): Float {
        val c = f.coerceIn(0f, 1f)
        val s = (c * STEPS).roundToInt()
        return if (s == 0 && c > 0f) 1f / STEPS else s.toFloat() / STEPS
    }

    /** The fill's width (px) in a track [innerW] wide for fraction [f]: the 5 % step, in whole pixels. */
    fun fillPx(innerW: Int, f: Float): Int = (innerW * step(f)).roundToInt().coerceIn(0, max(0, innerW))

    /** The height (px) of the light band on top of the fill: max(1, 1.2 units). */
    fun highlightPx(unit: Int): Int = max(1, (1.2f * unit).roundToInt())

    /**
     * The outline as four rectangles (left, top, right, bottom) in a bar [w] x [h]: the top and bottom edges and the left and right
     * edges between them, so the four corner unit squares are left out (the cut corners). Written into [out] (16 ints).
     */
    fun frame(w: Int, h: Int, unit: Int, out: IntArray) {
        val u = unit
        out[0] = u; out[1] = 0; out[2] = w - u; out[3] = u                  // top
        out[4] = u; out[5] = h - u; out[6] = w - u; out[7] = h               // bottom
        out[8] = 0; out[9] = u; out[10] = u; out[11] = h - u                 // left
        out[12] = w - u; out[13] = u; out[14] = w; out[15] = h - u           // right
    }
}
