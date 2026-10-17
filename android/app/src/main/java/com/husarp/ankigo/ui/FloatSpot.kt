package com.husarp.ankigo.ui

/** Where a floating damage number starts and how far it rises so it stays fully inside the arena (pure pixel maths). */
object FloatSpot {
    /** [left]/[top] = the view's start margins, [rise] = how far it travels up (px, never negative). */
    data class Spot(val left: Int, val top: Int, val rise: Int)

    /**
     * Wanted start ([left], [top]) of a number [tvW] x [tvH] in an arena [w] x [h]; [maxRise] is the usual 34 dp.
     * The rise shrinks to the room the arena has (h - tvH), left stays in [0, w - tvW], top in [rise, h - tvH]:
     * with no room above the sprite the number starts lower (over it) and still ends inside.
     */
    fun place(left: Int, top: Int, w: Int, h: Int, tvW: Int, tvH: Int, maxRise: Int): Spot {
        val rise = minOf(maxRise, maxOf(0, h - tvH))
        val l = left.coerceIn(0, maxOf(0, w - tvW))
        val t = top.coerceIn(rise, maxOf(rise, h - tvH))
        return Spot(l, t, rise)
    }

    /** A rectangle in arena pixels (right/bottom exclusive). */
    data class Rect(val l: Int, val t: Int, val r: Int, val b: Int) {
        fun hits(o: Rect) = l < o.r && o.l < r && t < o.b && o.t < b
    }

    /** Vertical offsets (in text heights) tried when a text would print over another one that is still showing. */
    private val STACK = intArrayOf(0, 1, -1, 2, -2, 3, -3)

    /**
     * [place], but also keeping the text clear of the [obstacles] (the stat cards: the whole rise must miss them, so the
     * text slides sideways to the nearest free left) and of the [taken] start rectangles of texts still on screen (it
     * moves down / up in steps of its own height + [gap], last resort sideways). When nothing is free it takes the spot with the least overlap.
     */
    fun placeClear(left: Int, top: Int, w: Int, h: Int, tvW: Int, tvH: Int, maxRise: Int, taken: List<Rect>, obstacles: List<Rect>, gap: Int): Spot {
        val first = place(left, top, w, h, tvW, tvH, maxRise)
        val maxL = maxOf(0, w - tvW)
        val lefts = ArrayList<Int>()
        lefts += first.left
        for (o in obstacles + taken) { lefts += (o.r + gap).coerceIn(0, maxL); lefts += (o.l - gap - tvW).coerceIn(0, maxL) }
        val ordered = lefts.distinct().sortedBy { kotlin.math.abs(it - first.left) }
        fun area(a: Rect, b: Rect): Long {
            val x = minOf(a.r, b.r) - maxOf(a.l, b.l)
            val y = minOf(a.b, b.b) - maxOf(a.t, b.t)
            return if (x > 0 && y > 0) x.toLong() * y else 0L
        }
        var best = first
        var bestScore = Long.MAX_VALUE
        for (l in ordered) for (k in STACK) {
            val s = place(l, top + k * (tvH + gap), w, h, tvW, tvH, maxRise)
            val swept = Rect(s.left, s.top - s.rise, s.left + tvW, s.top + tvH)
            val start = Rect(s.left, s.top, s.left + tvW, s.top + tvH)
            val score = taken.sumOf { area(start, it) } + 4L * obstacles.sumOf { area(swept, it) }
            if (score == 0L) return s
            if (score < bestScore) { bestScore = score; best = s }
        }
        return best
    }
}
