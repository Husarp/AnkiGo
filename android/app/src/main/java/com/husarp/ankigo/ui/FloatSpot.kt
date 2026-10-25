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

    /** R19-I: where [placeHead] put a text: its [spot], and whether it had to fall back onto the Pokémon ([onPokemon]). */
    data class HeadSpot(val spot: Spot, val onPokemon: Boolean)

    /**
     * R19-I (PLAN 244, pick B): a text [tvW] x [tvH] for the Pokémon whose real picture is [art], in an arena [w] x [h]. The first one
     * sits just above the head ([lift] px over it), centred on the picture; the next ones pile upward ([gap] between them, stepping over
     * every [taken] start rectangle). It may slide sideways (only while its middle stays over the picture) just to get clear of a stat card. A stack that would leave
     * the arena's safe top ([safeTop]) or hit an obstacle (a stat card) does not fit: the text then sits ON the Pokémon, centred on the
     * picture (further ones above / below that, clear of [taken]) - never beside it. [forceOn]: an earlier text of this side already sits on the
     * Pokémon, so the rest of the pile does too (one pile, not half above and half on it). The rise is [maxRise], cut to the room above
     * the text (down to the safe top and the obstacles over it).
     */
    fun placeHead(art: Rect, w: Int, h: Int, tvW: Int, tvH: Int, maxRise: Int, taken: List<Rect>, obstacles: List<Rect>, gap: Int, lift: Int, safeTop: Int, forceOn: Boolean = false): HeadSpot {
        val maxL = maxOf(0, w - tvW)
        val mid = (art.l + art.r) / 2
        val l0 = (mid - tvW / 2).coerceIn(0, maxL)
        // sideways steps that keep the text's middle over the picture
        val step = maxOf(1, tvW / 4)
        val lefts = (listOf(0) + (1..8).flatMap { listOf(it * step, -it * step) })
            .map { (l0 + it).coerceIn(0, maxL) }
            .filter { l -> val c = l + tvW / 2; c >= art.l && c <= art.r || l == l0 }
            .distinct()
        fun riseOf(l: Int, t: Int): Int {
            var room = t - safeTop
            for (o in obstacles) if (o.l < l + tvW && l < o.r && o.b <= t) room = minOf(room, t - o.b)
            return minOf(maxRise, maxOf(0, room))
        }
        for (l in if (forceOn) emptyList() else lefts) {
            var bottom = art.t - lift
            repeat(8) {
                val r = Rect(l, bottom - tvH, l + tvW, bottom)
                val over = taken.filter { it.hits(r) }.minOfOrNull { it.t }
                if (over != null) bottom = over - gap
            }
            val top = bottom - tvH
            val r = Rect(l, top, l + tvW, bottom)
            if (top < safeTop || taken.any { it.hits(r) }) break   // the stack is out of room: on the Pokémon, never slid sideways
            if (obstacles.any { it.hits(r) }) continue               // a stat card is in the way: slide along the head
            return HeadSpot(Spot(l, top, riseOf(l, top)), false)
        }
        // no room above the head: on the Pokémon, centred on its picture, the next ones stepping up / down from there
        val l = l0
        val top0 = ((art.t + art.b - tvH) / 2).coerceIn(0, maxOf(0, h - tvH))
        fun overlap(r: Rect): Long = taken.sumOf { o ->
            val x = minOf(r.r, o.r) - maxOf(r.l, o.l); val y = minOf(r.b, o.b) - maxOf(r.t, o.t)
            if (x > 0 && y > 0) x.toLong() * y else 0L
        }
        var best = top0
        var bestScore = Long.MAX_VALUE
        for (k in STACK) {
            val t = (top0 + k * (tvH + gap)).coerceIn(0, maxOf(0, h - tvH))
            val score = overlap(Rect(l, t, l + tvW, t + tvH))
            if (score == 0L) { best = t; bestScore = 0L; break }
            if (score < bestScore) { bestScore = score; best = t }
        }
        return HeadSpot(Spot(l, best, minOf(maxRise, maxOf(0, best))), true)
    }
}
