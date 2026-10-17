package com.husarp.ankigo.ui

import kotlin.math.max
import kotlin.math.min

/**
 * Round 7 battle backgrounds, how a picture sits in the arena (pure, JVM-tested). The picture is scaled to COVER the arena (never
 * an edge showing, also while Earthquake jolts it by up to [JOLT_SHARE] of the height) and moved so that its two ground spots
 * (where the Showdown art draws the floor ovals the two Pokémon stand on) sit as close as it can to the Pokémon's feet: the zoom
 * (cover up to [MAX_ZOOM] times) and offset with the least squared miss, the offset kept inside the cover limits.
 */
internal object BackdropFit {
    /** Where the two ground spots are in a picture, as fractions of its width and height. */
    class Spots(val enemyX: Float, val enemyY: Float, val playerX: Float, val playerY: Float)

    /**
     * Measured on all 14 sample pictures (10 % grid): Showdown draws the floor ovals at the same places in every one, the wild
     * one right of the middle in the upper half, ours left of the middle at the bottom, so one pair serves them all.
     */
    val SPOTS = Spots(0.60f, 0.47f, 0.33f, 0.91f)

    /** Where the Pokémon's feet are in the arena, in pixels (bottom centre of each sprite box). */
    class Feet(val enemyX: Float, val enemyY: Float, val playerX: Float, val playerY: Float)

    /** How the picture is drawn: picture pixel (x, y) lands at (left + x * scale, top + y * scale) in the arena. */
    class Placement(val scale: Float, val left: Float, val top: Float)

    /** The most the picture may be enlarged beyond "just covers" to bring the feet onto the spots. */
    const val MAX_ZOOM = 1.6f

    private const val ZOOM_STEPS = 24

    /** The jolt (Earthquake) that must show no gap, as a share of the arena's height, up and down. */
    const val JOLT_SHARE = 0.06f

    /**
     * The placement of a [picW] x [picH] picture in an arena of [w] x [h] px with the Pokémon's [feet] (null = the arena has no
     * layout yet: the picture just covers, centred). Null when a size is not positive.
     */
    fun fit(picW: Int, picH: Int, w: Float, h: Float, feet: Feet?, spots: Spots = SPOTS): Placement? {
        if (picW <= 0 || picH <= 0 || w <= 0f || h <= 0f) return null
        val over = h * JOLT_SHARE
        val cover = max(w / picW, (h + 2f * over) / picH)
        if (feet == null) return clamp(picW, picH, w, h, over, cover, (w - picW * cover) / 2f, (h - picH * cover) / 2f)

        // the spots in picture pixels (a, b), the feet in arena pixels; at a given zoom s the best offset puts the spots' middle on the feet's middle
        val ax = spots.enemyX * picW; val ay = spots.enemyY * picH
        val bx = spots.playerX * picW; val by = spots.playerY * picH
        val mx = (ax + bx) / 2f; val my = (ay + by) / 2f
        val tx = (feet.enemyX + feet.playerX) / 2f; val ty = (feet.enemyY + feet.playerY) / 2f
        fun at(s: Float) = clamp(picW, picH, w, h, over, s, tx - s * mx, ty - s * my)
        fun miss(p: Placement): Float {
            val ex = p.left + ax * p.scale - feet.enemyX; val ey = p.top + ay * p.scale - feet.enemyY
            val px = p.left + bx * p.scale - feet.playerX; val py = p.top + by * p.scale - feet.playerY
            return ex * ex + ey * ey + px * px + py * py
        }
        // The best zoom: the picture can only move inside the arena (the cover limits), so a little more zoom often buys room to
        // move. Plain cover first; a bigger zoom has to bring the spots at least 2 % closer to count. The zoom that matches the
        // spots' distance to the feet's (least squares, without the limits) is tried too.
        var best = at(cover)
        var bestMiss = miss(best)
        fun consider(s: Float) {
            val p = at(s.coerceIn(cover, cover * MAX_ZOOM))
            val m = miss(p)
            if (m < bestMiss * 0.98f) { best = p; bestMiss = m }
        }
        for (i in 1..ZOOM_STEPS) consider(cover * (1f + (MAX_ZOOM - 1f) * i / ZOOM_STEPS))
        val den = (ax - mx) * (ax - mx) + (ay - my) * (ay - my) + (bx - mx) * (bx - mx) + (by - my) * (by - my)
        if (den > 0f) {
            val num = (ax - mx) * (feet.enemyX - tx) + (ay - my) * (feet.enemyY - ty) + (bx - mx) * (feet.playerX - tx) + (by - my) * (feet.playerY - ty)
            consider(num / den)
        }
        return best
    }

    /** Keeps the picture over the whole arena plus the jolt's room ([over] px above and below). */
    private fun clamp(picW: Int, picH: Int, w: Float, h: Float, over: Float, s: Float, left: Float, top: Float): Placement {
        val l = min(0f, max(left, w - picW * s))
        val t = min(-over, max(top, h + over - picH * s))
        return Placement(s, l, t)
    }
}
