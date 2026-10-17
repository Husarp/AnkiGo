package com.husarp.ankigo.ui

/** The three colours the daily goal bar blends between (resolved for the current theme by the caller, like [BarPalette]). */
class GoalPalette(val red: Int, val orange: Int, val green: Int)

/**
 * Daily goal bar colour (PLAN 111): red at 0, orange around the middle, green at 100 % and beyond. Pure; not wired into the
 * bar yet. The stops and the flame steps are the ONE place to move them when Adam's pick changes them.
 */
object GoalTone {
    private enum class Slot { RED, ORANGE, GREEN }

    /** (fraction, colour) stops in order; the colour between two stops is a straight blend per channel. */
    private val STOPS = listOf(0f to Slot.RED, 0.5f to Slot.ORANGE, 1f to Slot.GREEN)

    /** Fractions at which the flame level goes 1, 2, 3 (round 5's flames); below the first it is 0. */
    private val FLAME_STEPS = listOf(1f, 1.5f, 2f)

    private fun pick(p: GoalPalette, s: Slot) = when (s) { Slot.RED -> p.red; Slot.ORANGE -> p.orange; Slot.GREEN -> p.green }

    fun color(fraction: Float, p: GoalPalette): Int {
        val f = if (fraction.isNaN()) 0f else fraction.coerceIn(STOPS.first().first, STOPS.last().first)
        val hi = STOPS.indexOfFirst { f <= it.first }.coerceAtLeast(1)
        val (f0, s0) = STOPS[hi - 1]
        val (f1, s1) = STOPS[hi]
        return blend(pick(p, s0), pick(p, s1), if (f1 > f0) (f - f0) / (f1 - f0) else 1f)
    }

    /** 0 below 100 %, then 1..3 as the goal is passed by more ([FLAME_STEPS]). */
    fun flameLevel(fraction: Float): Int = if (fraction.isNaN()) 0 else FLAME_STEPS.count { fraction >= it }

    /** ARGB channels blended by [t] (0..1), rounded. */
    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val x = (a ushr shift) and 0xFF
            val y = (b ushr shift) and 0xFF
            return Math.round(x + (y - x) * t).coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
