package com.husarp.ankigo.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The shiny entrance star burst (pick 75A, mockup `shinyFrame`, `st==='A'`): a white flash star in the middle of the sprite,
 * then a ring of 8 four-point stars bursting outward and a smaller ring of 6 right behind it. Pure maths (no android.*);
 * [ArenaFxView] draws what [forEach] hands out. Positions are in the same pixels as [cx] / [cy] and sizes are star radii.
 */
object ShinyBurst {
    /** The burst starts this long after the entrance began (the sprite is in by then). */
    const val DELAY_MS = 450L
    const val FLASH_MS = 220
    const val RING_MS = 800
    /** Last frame with anything on screen: ring 2 starts at 180 ms and runs 800 ms. */
    const val DURATION_MS = 980L

    private const val RING1_COUNT = 8
    private const val RING2_COUNT = 6
    private const val RING2_START = 180
    private const val RING1_REACH = 46f
    private const val RING2_REACH = 30f
    private const val RING2_ANGLE = 0.4f
    private const val STAR_UNITS = 7f
    private const val FLASH_UNITS = 22f

    /** Star colours cycle through these (white, pale yellow, pale blue). */
    val COLORS = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFF1A8.toInt(), 0xFFCFE9FF.toInt())

    /** One star to draw; [flash] is the big centre star. */
    data class Star(val x: Float, val y: Float, val r: Float, val alpha: Float, val color: Int, val flash: Boolean)

    /** Mock units to pixels: 72 units is the sprite box's short side. */
    fun scale(boxW: Float, boxH: Float): Float = min(boxW, boxH) / 72f

    /** The burst's centre: the box's centre x, 55 % down. */
    fun centreY(boxTop: Float, boxH: Float): Float = boxTop + boxH * 0.55f

    private fun clamp01(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v
    private fun easeOut(q: Float) = 1f - (1f - q) * (1f - q) * (1f - q)

    /** Receives the stars of one frame (an interface, so a frame allocates nothing). */
    fun interface Sink { fun star(x: Float, y: Float, r: Float, alpha: Float, color: Int, flash: Boolean) }

    /** Hands [emit] every star alive at [tMs] since the burst began. */
    fun forEach(tMs: Float, cx: Float, cy: Float, sc: Float, emit: Sink) {
        for (ring in 0..1) {
            val n = if (ring == 0) RING1_COUNT else RING2_COUNT
            val q = ringProgress(tMs, if (ring == 0) 0 else RING2_START)
            if (q <= 0f || q >= 1f) continue
            val reach = (if (ring == 0) RING1_REACH else RING2_REACH) * sc * easeOut(q)
            val offset = if (ring == 0) 0f else RING2_ANGLE
            val alpha = 1f - q * q
            for (i in 0 until n) {
                val a = i.toFloat() / n * (2f * PI.toFloat()) + offset
                val twinkle = 0.6f + 0.4f * sin(tMs / 40f + i * 2f)
                emit.star(cx + cos(a) * reach, cy + sin(a) * reach * 0.85f, STAR_UNITS * sc * (1f - 0.6f * q) * twinkle, alpha, COLORS[i % 3], false)
            }
        }
        if (tMs >= 0f && tMs < FLASH_MS) {
            emit.star(cx, cy, FLASH_UNITS * sc * (tMs / FLASH_MS + 0.3f), 1f - tMs / FLASH_MS, COLORS[0], true)
        }
    }

    /** [forEach] as a list (tests, tools). */
    fun at(tMs: Float, cx: Float, cy: Float, sc: Float): List<Star> {
        val out = ArrayList<Star>()
        forEach(tMs, cx, cy, sc) { x, y, r, a, c, f -> out += Star(x, y, r, a, c, f) }
        return out
    }

    fun ringProgress(tMs: Float, startMs: Int): Float = clamp01((tMs - startMs) / RING_MS)

    /** The burst is over (nothing to draw any more). */
    fun finished(tMs: Float): Boolean = tMs >= DURATION_MS
}
