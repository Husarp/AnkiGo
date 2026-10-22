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

/**
 * PLAN 138 + 182: a gain that crosses levels is played as a loop, the same for the main's info box and the Exp. Share side card:
 * fill to the end, hold [HOLD_MS], the level number changes, the bar starts again from EMPTY at the same speed, ... and the final
 * partial fill. [segments] turns (from level / fraction, to level / fraction) into the bar's runs; pure maths (no android.*).
 */
object XpLoop {
    /** How long a bar that reached the end stays full before the level changes and it restarts. */
    const val HOLD_MS = 250L

    /** Time a whole empty-to-full run takes; a shorter run takes its share, never less than [MIN_MS] (the main bar's one speed). */
    const val FULL_MS = 700L
    const val MIN_MS = 120L

    /**
     * One run of the bar: [level] is the level it belongs to, from [from] to [to] (0..1); [toEnd] = the run ends at the full bar, after
     * which comes the hold and the next level (the last run never has it).
     */
    data class Seg(val level: Int, val from: Float, val to: Float, val toEnd: Boolean) {
        val length: Float get() = to - from
    }

    /**
     * The runs from [fromLevel] at [fromFrac] to [toLevel] at [toFrac]: none crossed = one run; every level crossed adds one that ends
     * at the full bar and the next one starts at empty. [toFrac] 0 right after a level (a gain that lands exactly on it) is a last run
     * of length 0: the level changes and the bar stays empty. A to-level below the from-level counts as no level.
     */
    fun segments(fromLevel: Int, fromFrac: Float, toLevel: Int, toFrac: Float): List<Seg> {
        val a = fromFrac.coerceIn(0f, 1f)
        val b = toFrac.coerceIn(0f, 1f)
        val crossed = toLevel - fromLevel
        if (crossed <= 0) return listOf(Seg(fromLevel, a, maxOf(a, b), false))
        val out = ArrayList<Seg>()
        out += Seg(fromLevel, a, 1f, true)
        for (l in fromLevel + 1 until toLevel) out += Seg(l, 0f, 1f, true)
        out += Seg(toLevel, 0f, b, false)
        return out
    }

    /** How long a run of [length] (0..1) takes, before the pace is applied. */
    fun runMs(length: Float): Long = if (length <= 0f) 0L else maxOf(MIN_MS, (FULL_MS * length).toLong())

    /**
     * The XP each level of a gain needs, from the old level (index 0) to the new one (last): [crossedNeeds] are the old levels', [lastNeed]
     * is the new level's. [levels] = levels crossed; a gain that did not record its needs ([crossedNeeds] shorter than [levels]) gets
     * [lastNeed] for the unknown ones, so labels stay sensible.
     */
    fun needs(levels: Int, crossedNeeds: List<Int>, lastNeed: Int): List<Int> =
        List(levels.coerceAtLeast(0) + 1) { i -> if (i < levels && i < crossedNeeds.size) crossedNeeds[i] else lastNeed }
}
