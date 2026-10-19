package com.husarp.ankigo.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * R9-C8: what the XP bar and its "XP a / b" label show while the XP orbs land. The bar starts at [fromFrac], and [frac] / [current]
 * give its fraction and number for the animation's progress [fill] (0..1 from [XpParticles.fill]). A normal gain ends exactly at
 * [toCurrent] of [needed]; a gain that grows a level ([levelUp]) ends at 100 %, then the event player restarts the bar at the new
 * level's progress. Pure maths (no android.*).
 */
class XpFill(val fromFrac: Float, val toCurrent: Int, val needed: Int, val levelUp: Boolean) {
    val toFrac: Float = if (levelUp || needed <= 0) 1f else (toCurrent.toFloat() / needed).coerceIn(0f, 1f)

    /** The bar's fraction at progress [fill]; exactly [toFrac] at 1, and it never leaves the span between the two ends. */
    fun frac(fill: Float): Float {
        if (fill >= 1f) return toFrac
        val f = if (fill <= 0f) 0f else fill
        return (fromFrac + (toFrac - fromFrac) * f).coerceIn(min(fromFrac, toFrac), max(fromFrac, toFrac))
    }

    /** The label's number at progress [fill]: grows with the bar, exactly [toCurrent] (or [needed] when a level is gained) at 1. */
    fun current(fill: Float): Int =
        if (fill >= 1f) (if (levelUp) needed else toCurrent) else (frac(fill) * needed).roundToInt().coerceIn(0, max(needed, 0))
}
