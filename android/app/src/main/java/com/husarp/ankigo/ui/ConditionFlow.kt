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

    /** Each owner's rows are centred on its card, wrapping within the available lane. */
    fun anchored(card: ConditionSpot.R, right: Float, widths: FloatArray, stats: Int, gap: Float, lift: Float,
                 height: Float, sprites: List<ConditionSpot.R>, blocks: List<ConditionSpot.R> = emptyList(),
                 below: Boolean = true, left: Float = 0f): List<ConditionSpot.R> {
        if (widths.isEmpty()) return emptyList()
        val out = ArrayList<ConditionSpot.R>()
        val center = (card.l + card.r) / 2f
        var y = if (below) card.b + lift else card.t - lift - height
        var index = 0
        while (index < widths.size) {
            var l = left
            var r = right
            for (art in sprites) if (y < art.b && y + height > art.t) {
                if (art.l >= center) r = min(r, art.l + art.w * .15f)
                else if (art.r <= center) l = max(l, art.r - art.w * .15f)
            }
            for (block in blocks) if (y < block.b && y + height > block.t) {
                if (block.l >= center) r = min(r, block.l - gap)
                else if (block.r <= center) l = max(l, block.r + gap)
                else return emptyList()
            }
            val available = min(card.w, 2f * min(center - l, r - center))
            val lines = rows(widths.copyOfRange(index, widths.size), (stats - index).coerceAtLeast(0), available, gap)
            val line = lines.firstOrNull() ?: return emptyList()
            val lineWidth = line.sumOf { widths[index + it].toDouble() }.toFloat() + gap * (line.size - 1)
            var x = center - lineWidth / 2f
            for (i in line) {
                val w = widths[index + i]
                out += ConditionSpot.R(x, y, x + w, y + height)
                x += w + gap
            }
            index += line.size
            y += (height + gap) * if (below) 1 else -1
        }
        return out
    }

    class OwnerFlow(val rects: List<ConditionSpot.R>, val shown: Int, val more: Int)

    /** Tiny windows retain the full list behind the existing +N chip instead of displacing the owner's card. */
    fun owner(card: ConditionSpot.R, right: Float, widths: FloatArray, stats: Int, gap: Float, lift: Float,
              height: Float, sprites: List<ConditionSpot.R>, maxHeight: Float, blocks: List<ConditionSpot.R> = emptyList(), below: Boolean = true, left: Float = 0f, moreWidth: (Int) -> Float): OwnerFlow {
        for (n in widths.size downTo 0) {
            val more = widths.size - n
            val shownWidths = widths.take(n).toFloatArray() + if (more > 0) floatArrayOf(moreWidth(more)) else floatArrayOf()
            val rects = anchored(card, right, shownWidths, min(stats, n), gap, lift, height, sprites, blocks, below, left)
            if (rects.isEmpty() && shownWidths.isNotEmpty()) continue
            if (rects.isEmpty() || rects.maxOf { it.b } - rects.minOf { it.t } <= maxHeight) return OwnerFlow(rects, n, more)
        }
        return OwnerFlow(emptyList(), 0, widths.size)
    }

    /** Owners stay anchored; field conditions search the remaining free rectangular lanes. */
    fun place(scene: ConditionSpot.Scene, card: ConditionSpot.R?, below: Boolean, widths: FloatArray, stats: Int,
              gap: Float, lift: Float, height: Float, taken: List<ConditionSpot.R>): List<ConditionSpot.R> {
        if (widths.isEmpty()) return emptyList()
        if (card != null) return anchored(card, scene.area.r, widths, stats, gap, lift, height, scene.sprites)
        val a = scene.area
        val blocks = scene.blocks + taken
        val xs = (listOf(a.l, a.r) + blocks.flatMap { listOf(max(a.l, it.r + lift), min(a.r, it.l - lift)) }).distinct().sorted()
        val preferred = a.t + lift
        var best = emptyList<ConditionSpot.R>()
        var bestScore = Float.MAX_VALUE
        for (l in xs) for (r in xs) {
            if (r <= l) continue
            val lines = rows(widths, stats, r - l, gap)
            if (lines.isEmpty()) continue
            val h = lines.size * height + (lines.size - 1) * gap
            val ys = listOf(preferred, a.b - h - lift) +
                listOf(a.t, a.b - h) + blocks.flatMap { listOf(it.b + lift, it.t - lift - h) }
            val w = lines.maxOf { line -> line.sumOf { widths[it].toDouble() }.toFloat() + (line.size - 1) * gap }
            val left = r - w
            for (y in ys) {
                val bounds = ConditionSpot.R(left, y, left + w, y + h)
                if (!bounds.inside(a) || blocks.any { bounds.hits(it) }) continue
                val distance = abs(y - preferred)
                val score = lines.size * scene.h * 4f + distance + abs(left - a.r) * 0.1f
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
