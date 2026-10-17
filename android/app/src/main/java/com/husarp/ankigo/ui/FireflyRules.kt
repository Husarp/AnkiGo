package com.husarp.ankigo.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor

/**
 * The night battle background's fireflies (pure, JVM-tested): a fixed, made-up set of glowing dots that sit on the picture's own
 * pixel grid and twinkle slowly, each fading in and out at its own pace and phase. [ArenaBackdrop] draws them over the darkened forest.
 */
internal object FireflyRules {
    /** One firefly: its place in the picture (fractions of its width and height), its size in picture pixels, and its twinkle. */
    class Fly(val x: Float, val y: Float, val wide: Int, val tall: Int, val periodMs: Float, val phase: Float)

    const val COUNT = 26

    /** The glow reaches this many picture pixels from the dot. */
    const val GLOW_PX = 4f

    /** The frame gap of the twinkle: it is slow, so about 12 frames a second are plenty. */
    const val FRAME_MS = 80L

    /** The fireflies; the same ones every time (a seeded generator), so the picture always looks the same at a given moment. */
    val FLIES: List<Fly> = run {
        var seed = 0x51F1E5
        fun next(): Float { seed = seed * 1664525 + 1013904223; return (seed ushr 8) / 16777216f }
        List(COUNT) {
            val wide = if (next() < 0.5f) 2 else 1
            Fly(
                x = 0.03f + next() * 0.94f,
                y = 0.06f + next() * 0.80f,
                wide = wide,
                tall = if (wide == 1) 1 + (next() * 1.99f).toInt() else 1,
                periodMs = 3000f + next() * 4000f,   // 3 - 7 s per breath
                phase = next(),
            )
        }
    }

    /**
     * How lit [fly] is at [timeMs], 0..1: a slow breath that rests dark for a while (so they come and go instead of all glowing at once).
     */
    fun alpha(fly: Fly, timeMs: Long): Float {
        val cycle = (timeMs / fly.periodMs + fly.phase)
        val breath = 0.5f - 0.5f * cos((2 * PI * (cycle - floor(cycle))).toFloat())   // 0 -> 1 -> 0
        val s = ((breath - 0.3f) / 0.7f).coerceIn(0f, 1f)                              // dark for the lowest 30 %
        return s * s * (3f - 2f * s)                                                   // smooth in and out
    }

    /** The steady look with reduced motion: no twinkle, every dot lit to a different, fixed degree. */
    fun still(fly: Fly): Float = 0.45f + 0.55f * fly.phase
}
