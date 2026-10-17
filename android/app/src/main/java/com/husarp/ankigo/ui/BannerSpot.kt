package com.husarp.ankigo.ui

import com.husarp.ankigo.ui.FloatSpot.Rect

/**
 * Where the "Gotcha!" banner goes in the arena (PLAN 35; pure pixel maths). First choice: straight above the Poké Ball (the ball
 * is short, so there is room), centred on it. No room there: centred in the arena. Both candidates must miss every obstacle
 * (the sprite pictures and the stat cards that are on screen); when even the centre is taken the whole arena is scanned
 * for the free spot nearest the centre. When even the smallest banner ([SCALES]) finds no clear spot, [Placed.shown] is false and
 * the banner is skipped: "Gotcha!" never covers one of our Pokémon or a card (the result tiles and the log already say it). The
 * banner always stays inside the arena ([fitScale] shrinks it first when the arena is smaller than the banner).
 */
object BannerSpot {
    enum class Mode { ABOVE_BALL, CENTRE, FREE }

    /** [left]/[top] = where the banner's top-left corner goes (arena px); [clear] = it covers no obstacle. */
    data class Spot(val left: Int, val top: Int, val mode: Mode, val clear: Boolean = true)

    /** [scale] = how much the banner is drawn smaller (1 = full size), [spot] = where its (scaled) box goes. */
    data class Placed(val scale: Float, val spot: Spot) {
        /** False = no clear spot even at the smallest size: the banner is not shown (it would cover a Pokémon or a card). */
        val shown: Boolean get() = spot.clear
    }

    /** Banner sizes tried in turn when the full-size one has no free place (a small window), down to about half size. */
    private val SCALES = floatArrayOf(1f, 0.85f, 0.72f, 0.6f, 0.52f, 0.45f)

    /**
     * The biggest banner size (full size down to 0.45) that finds a spot clear of all [obstacles]; when none does, [Placed.shown] is
     * false (the smallest size, where it covers least, stays in [Placed] for tests). [bw] x [bh] is the banner's natural size; it
     * also never exceeds the arena ([fitScale]).
     */
    fun placeFitting(w: Int, h: Int, bw: Int, bh: Int, ball: Rect?, obstacles: List<Rect>, edge: Int, gap: Int): Placed {
        val fit = fitScale(w, h, bw, bh, edge)
        var last = Placed(fit, place(w, h, (bw * fit).toInt(), (bh * fit).toInt(), ball, obstacles, edge, gap))
        for (k in SCALES) {
            val sc = minOf(k, fit)
            last = Placed(sc, place(w, h, (bw * sc).toInt(), (bh * sc).toInt(), ball, obstacles, edge, gap))
            if (last.spot.clear) return last
        }
        return last
    }

    /** How much to shrink a [bw] x [bh] banner (1 = not at all) so it fits a [w] x [h] arena with [edge] px to spare. */
    fun fitScale(w: Int, h: Int, bw: Int, bh: Int, edge: Int): Float {
        if (bw <= 0 || bh <= 0) return 1f
        val sw = (w - 2 * edge).coerceAtLeast(1).toFloat() / bw
        val sh = (h - 2 * edge).coerceAtLeast(1).toFloat() / bh
        return minOf(1f, sw, sh)
    }

    /**
     * The spot for a banner [bw] x [bh] (already scaled) in an arena [w] x [h], [edge] px from the sides when there is room.
     * [ball] = the Poké Ball's box (null: no ball), [gap] = space between banner and ball, [obstacles] = what must stay uncovered.
     */
    fun place(w: Int, h: Int, bw: Int, bh: Int, ball: Rect?, obstacles: List<Rect>, edge: Int, gap: Int): Spot {
        val minX = if (w - bw >= 2 * edge) edge else 0
        val maxX = maxOf(minX, if (w - bw >= 2 * edge) w - bw - edge else w - bw)
        val minY = if (h - bh >= 2 * edge) edge else 0
        val maxY = maxOf(minY, if (h - bh >= 2 * edge) h - bh - edge else h - bh)
        fun box(x: Int, y: Int) = Rect(x, y, x + bw, y + bh)
        fun hits(x: Int, y: Int) = obstacles.any { it.hits(box(x, y)) }

        if (ball != null) {
            val x = ((ball.l + ball.r - bw) / 2).coerceIn(minX, maxX)
            val y = ball.t - gap - bh
            if (y >= minY && !hits(x, y) && !ball.hits(box(x, y))) return Spot(x, y, Mode.ABOVE_BALL)
        }
        val cx = ((w - bw) / 2).coerceIn(minX, maxX)
        val cy = ((h - bh) / 2).coerceIn(minY, maxY)
        if (!hits(cx, cy)) return Spot(cx, cy, Mode.CENTRE)

        // Centre taken: the free spot nearest the centre (sprites / cards count 4x a covered ball), else the least covered.
        fun area(a: Rect, b: Rect): Long {
            val x = minOf(a.r, b.r) - maxOf(a.l, b.l)
            val y = minOf(a.b, b.b) - maxOf(a.t, b.t)
            return if (x > 0 && y > 0) x.toLong() * y else 0L
        }
        val step = maxOf(2, minOf(w, h) / 40)
        var bestX = cx; var bestY = cy
        var bestScore = Long.MAX_VALUE; var bestDist = Long.MAX_VALUE; var bestHard = 0L
        var y = minY
        while (true) {
            var x = minX
            while (true) {
                val b = box(x, y)
                val hard = obstacles.sumOf { area(b, it) }
                val score = 4L * hard + (ball?.let { area(b, it) } ?: 0L)
                val dx = (x - cx).toLong(); val dy = (y - cy).toLong()
                val dist = dx * dx + dy * dy
                if (score < bestScore || (score == bestScore && dist < bestDist)) { bestScore = score; bestDist = dist; bestX = x; bestY = y; bestHard = hard }
                if (x >= maxX) break
                x = minOf(maxX, x + step)
            }
            if (y >= maxY) break
            y = minOf(maxY, y + step)
        }
        return Spot(bestX, bestY, Mode.FREE, bestHard == 0L)
    }
}
