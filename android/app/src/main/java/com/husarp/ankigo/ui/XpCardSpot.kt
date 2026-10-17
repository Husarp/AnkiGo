package com.husarp.ankigo.ui

/** Where the team-XP cards go (pure pixel maths): stacked in the biggest free gap at the arena's left edge. */
object XpCardSpot {
    /**
     * Tops of up to [max] cards of [cardW] x [cardH] at x = [left] in an arena [h] high, in the largest vertical gap
     * (kept [edge] from the top and bottom, [gap] between cards) that no [obstacles] rectangle crosses in the card's column.
     * The cards are centred in that gap; an empty list = no room (the cards are skipped).
     */
    fun slots(h: Int, left: Int, cardW: Int, cardH: Int, edge: Int, gap: Int, obstacles: List<FloatSpot.Rect>, max: Int = 3): List<Int> {
        if (cardH <= 0 || h <= 0) return emptyList()
        val column = obstacles.filter { it.l < left + cardW && it.r > left }.sortedBy { it.t }
        val gaps = ArrayList<IntArray>()
        var from = edge
        for (o in column) {
            if (o.t - from >= cardH) gaps += intArrayOf(from, o.t)
            from = maxOf(from, o.b)
        }
        if (h - edge - from >= cardH) gaps += intArrayOf(from, h - edge)
        var best: IntArray? = null
        var bestN = 0
        for (g in gaps) {
            val n = minOf(max, (g[1] - g[0] + gap) / (cardH + gap))
            if (n > bestN || (n == bestN && n > 0 && g[1] - g[0] > best!![1] - best[0])) { best = g; bestN = n }
        }
        if (best == null || bestN == 0) return emptyList()
        val used = bestN * cardH + (bestN - 1) * gap
        val y0 = best[0] + (best[1] - best[0] - used) / 2
        return List(bestN) { y0 + it * (cardH + gap) }
    }

    /** Top-left corner of a card placed by [anywhere]. */
    data class Pos(val x: Int, val y: Int)

    /**
     * Small windows have no free strip along the left edge, so this looks for any free spot: the card's corner is tried at
     * the arena's edges and right beside / above / below every obstacle ([gap] away), and a spot is free when the card
     * ([cardW] x [cardH], kept [edge] inside an arena [w] x [h]) touches no obstacle. Spots hugging the top or bottom
     * edge win, then the ones nearest a side edge. Up to [max] spots, each placed after the previous one counted as an
     * obstacle, so they never overlap. Empty = no room.
     */
    fun anywhere(w: Int, h: Int, cardW: Int, cardH: Int, edge: Int, gap: Int, obstacles: List<FloatSpot.Rect>, max: Int = 2): List<Pos> {
        if (cardW <= 0 || cardH <= 0 || w < cardW + 2 * edge || h < cardH + 2 * edge) return emptyList()
        val taken = ArrayList(obstacles)
        val out = ArrayList<Pos>()
        repeat(max) {
            val xs = LinkedHashSet<Int>().apply {
                add(edge); add(w - edge - cardW)
                for (o in taken) { add(o.r + gap); add(o.l - gap - cardW) }
            }.filter { it >= edge && it + cardW <= w - edge }
            val ys = LinkedHashSet<Int>().apply {
                add(edge); add(h - edge - cardH)
                for (o in taken) { add(o.b + gap); add(o.t - gap - cardH) }
            }.filter { it >= edge && it + cardH <= h - edge }
            var best: Pos? = null
            var bestKey = Long.MAX_VALUE
            for (y in ys) for (x in xs) {
                val r = FloatSpot.Rect(x, y, x + cardW, y + cardH)
                if (taken.any { it.hits(r) }) continue
                val key = minOf(y, h - cardH - y).toLong() * 100000L + minOf(x, w - cardW - x)
                if (key < bestKey) { bestKey = key; best = Pos(x, y) }
            }
            val b = best ?: return out
            out += b
            taken += FloatSpot.Rect(b.x, b.y - gap, b.x + cardW, b.y + cardH + gap)
        }
        return out
    }
}
