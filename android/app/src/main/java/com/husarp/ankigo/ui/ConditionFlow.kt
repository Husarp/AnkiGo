package com.husarp.ankigo.ui

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Named chips, stats first. Effects start a new row only if the combined row does not fit. */
internal object ConditionFlow {
    fun rows(widths: FloatArray, stats: Int, available: Float, gap: Float): List<List<Int>> {
        if (widths.isEmpty() || widths.any { it > available }) return emptyList()
        fun width(indices: List<Int>) = indices.sumOf { widths[it].toDouble() }.toFloat() + gap * (indices.size - 1).coerceAtLeast(0)
        val all = widths.indices.toList()
        if (width(all) <= available) return listOf(all)
        val out = mutableListOf<List<Int>>()
        fun append(indices: List<Int>) {
            var line = mutableListOf<Int>()
            for (i in indices) {
                if (line.isNotEmpty() && width(line + i) > available) { out += line; line = mutableListOf() }
                line += i
            }
            if (line.isNotEmpty()) out += line
        }
        append(all.take(stats))
        append(all.drop(stats))
        return out
    }

    /** Tries free rectangular lanes around the owner, then other free space, without hiding any mark. */
    fun place(scene: ConditionSpot.Scene, card: ConditionSpot.R?, below: Boolean, widths: FloatArray, stats: Int,
              gap: Float, lift: Float, height: Float, taken: List<ConditionSpot.R>): List<ConditionSpot.R> {
        if (widths.isEmpty()) return emptyList()
        val a = scene.area
        val blocks = scene.blocks + taken
        val xs = (listOf(a.l, a.r) + blocks.flatMap { listOf(max(a.l, it.r + lift), min(a.r, it.l - lift)) }).distinct().sorted()
        val preferred = if (card == null) a.t + lift else if (below) card.b + lift else card.t - lift
        var best = emptyList<ConditionSpot.R>()
        var bestScore = Float.MAX_VALUE
        for (l in xs) for (r in xs) {
            if (r <= l) continue
            val lines = rows(widths, stats, r - l, gap)
            if (lines.isEmpty()) continue
            val h = lines.size * height + (lines.size - 1) * gap
            val ys = listOf(if (below || card == null) preferred else preferred - h,
                if (card == null) a.b - h - lift else if (below) card.t - lift - h else card.b + lift) +
                listOf(a.t, a.b - h) + blocks.flatMap { listOf(it.b + lift, it.t - lift - h) }
            val w = lines.maxOf { line -> line.sumOf { widths[it].toDouble() }.toFloat() + (line.size - 1) * gap }
            val left = if (card == null) r - w else if (below) card.l.coerceIn(l, (r - w).coerceAtLeast(l)) else (card.r - w).coerceIn(l, (r - w).coerceAtLeast(l))
            for (y in ys) {
                val bounds = ConditionSpot.R(left, y, left + w, y + h)
                if (!bounds.inside(a) || blocks.any { bounds.hits(it) }) continue
                val distance = if (card == null) abs(y - preferred) else min(abs(y - card.b - lift), abs(y + h - card.t + lift))
                val score = lines.size * scene.h * 4f + distance + abs(left - (card?.l ?: a.r)) * 0.1f +
                    if (card != null && below != (y >= card.b)) height else 0f
                if (score >= bestScore) continue
                val rects = arrayOfNulls<ConditionSpot.R>(widths.size)
                for ((n, line) in lines.withIndex()) {
                    var x = left
                    for (i in line) {
                        val top = y + n * (height + gap)
                        rects[i] = ConditionSpot.R(x, top, x + widths[i], top + height)
                        x += widths[i] + gap
                    }
                }
                best = rects.map { it!! }; bestScore = score
            }
        }
        return best
    }
}
