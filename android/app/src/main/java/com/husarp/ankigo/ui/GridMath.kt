package com.husarp.ankigo.ui

/**
 * Box / Pokédex grid sizes (PLAN 78). Pure; the Box and the Pokédex tiles use it (R4-V5).
 *
 * Columns by the screen width in dp: under 340 very narrow phones 3; 340..599 phones 4; 600..839 small tablets / split
 * screens 5; 840 and wider tablets 6.
 */
object GridMath {
    /** Smallest width (dp) of each step, in order: (width, columns). The first entry is the fallback for anything narrower. */
    private val STEPS = listOf(0 to 3, 340 to 4, 600 to 5, 840 to 6)

    fun columns(widthDp: Int): Int = STEPS.last { widthDp >= it.first || it === STEPS.first() }.second

    /**
     * The largest whole-number scale (at least 1) at which a sprite [nativePx] wide still fits a cell [cellPx] wide, so
     * upscaled pixel art stays sharp. A cell narrower than the sprite still gives 1 (the caller may then shrink it).
     */
    fun spriteScale(cellPx: Int, nativePx: Int): Int = if (nativePx <= 0) 1 else (cellPx / nativePx).coerceAtLeast(1)

    /**
     * The scale a sprite [nativeW] x [nativeH] px is drawn at inside a room of [availW] x [availH] px (R4-V5, 10A): the largest
     * whole number that fits, so the pixels stay sharp. When that would fill under [MIN_FILL] of the room (low-density screens,
     * where 1x or 2x leaves a lot unused) the exact fit is used instead; it is still drawn without smoothing. A room smaller than
     * the sprite shrinks it to fit.
     */
    fun tileScale(availW: Int, availH: Int, nativeW: Int, nativeH: Int): Float {
        if (nativeW <= 0 || nativeH <= 0 || availW <= 0 || availH <= 0) return 1f
        val fit = minOf(availW.toFloat() / nativeW, availH.toFloat() / nativeH)
        if (fit < 1f) return fit
        val whole = kotlin.math.floor(fit)
        return if (whole >= fit * MIN_FILL) whole else fit
    }

    /** The share of the room a whole-number scale has to fill, else [tileScale] falls back to the exact fit. */
    const val MIN_FILL = 0.7f
}
