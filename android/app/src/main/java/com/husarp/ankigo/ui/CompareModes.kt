package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Type

// PLAN item 53 (R3-09): the math behind Compare's two modes ("vs reference" / "best of all") for any number of Pokémon.
// Pure Kotlin (no android.*): unit-tested on the JVM. A column "order" is a list of indexes into CompareData.columns:
// it says which columns show, in which order, and its first entry is the reference column of the "vs reference" mode.

/** Which Compare mode a screen shows. */
enum class CompareMode { VS_REFERENCE, BEST_OF_ALL }

/** Owned Pokémon only, Pokédex entries only, or both. */
enum class OwnerFilter { ALL, OWNED, DEX }

/** Which columns stay: by ownership and (when [types] is not empty) by the shown species having any of those types. */
data class ColumnFilter(val owner: OwnerFilter = OwnerFilter.ALL, val types: Set<Type> = emptySet()) {
    fun matches(c: CompareColumn): Boolean =
        when (owner) { OwnerFilter.ALL -> true; OwnerFilter.OWNED -> c.mon != null; OwnerFilter.DEX -> c.mon == null } &&
            (types.isEmpty() || c.shown.types.any { it in types })
}

/** Which rows show: everything except the [hidden] row keys (so rows added later show by default). */
data class RowChoice(val hidden: Set<String> = emptySet()) {
    fun visible(key: String): Boolean = key !in hidden
    fun toggle(key: String): RowChoice = RowChoice(if (key in hidden) hidden - key else hidden + key)
    /** For prefs: the hidden keys joined by commas ("" = all rows show). */
    fun encode(): String = hidden.sorted().joinToString(",")

    companion object {
        fun decode(s: String?): RowChoice = RowChoice(s.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet())
    }
}

object CompareModes {
    // ---- Mode 1: vs reference ----

    /**
     * For every numeric row (by row key): per column the value minus the value of column [ref]. Null when either side has
     * no value; the reference column itself is always null. Empty when [ref] isn't a column.
     */
    fun diffs(data: CompareData, ref: Int): Map<String, List<Int?>> {
        if (ref !in data.columns.indices) return emptyMap()
        val out = LinkedHashMap<String, List<Int?>>()
        for (row in data.rows) {
            if (!row.numeric) continue
            val values = values(row)
            val base = values.getOrNull(ref)
            out[row.key] = values.indices.map { i -> if (i == ref || base == null) null else values[i]?.let { it - base } }
        }
        return out
    }

    /** The green/red text of a difference: "+12", "−8" (real minus sign); "" for none and for an equal value (R4-X1: no "±0"). */
    fun diffLabel(diff: Int?): String = when {
        diff == null -> ""
        diff > 0 -> "+$diff"
        diff < 0 -> "−${-diff}"
        else -> ""
    }

    /** 1 = better than the reference (green), -1 = worse (red), 0 = equal or none (neutral). */
    fun diffSign(diff: Int?): Int = when { diff == null -> 0; diff > 0 -> 1; diff < 0 -> -1; else -> 0 }

    // ---- Mode 2: best of all ----

    /**
     * Rank tier per column: 0 = best (gold), 1 = the next distinct value (orange), 2 ...; equal values share a tier;
     * null value = no tier. With fewer than two values nothing is compared, so no tiers (same rule as bestIndexes).
     */
    fun ranks(values: List<Int?>): List<Int?> {
        if (values.count { it != null } < 2) return values.map { null }
        val distinct = values.filterNotNull().distinct().sortedDescending()
        return values.map { v -> v?.let { distinct.indexOf(it) } }
    }

    /**
     * R4-X1 item 10: where each value sits between the row's worst (0.0 = red) and best (1.0 = green) value, in proportion to
     * the VALUE (not the rank): near-equal values get near-equal positions. Null for a column without a value; with fewer than
     * two values, or when they are all equal, there is nothing to compare, so every entry is null.
     */
    fun scale(values: List<Int?>): List<Float?> {
        val have = values.filterNotNull()
        if (have.size < 2) return values.map { null }
        val lo = have.min()
        val hi = have.max()
        if (hi == lo) return values.map { null }
        return values.map { v -> v?.let { (it - lo).toFloat() / (hi - lo) } }
    }

    /** [scale] for every numeric row, by row key. */
    fun scaleRows(data: CompareData): Map<String, List<Float?>> =
        data.rows.filter { it.numeric }.associate { it.key to scale(values(it)) }

    /** [ranks] for every numeric row, by row key. */
    fun rankRows(data: CompareData): Map<String, List<Int?>> =
        data.rows.filter { it.numeric }.associate { it.key to ranks(values(it)) }

    // ---- Order: move / reference / remove / sort / filter ----

    fun identity(columnCount: Int): List<Int> = (0 until columnCount).toList()

    /** Moves the entry at position [from] to position [to] (drag or arrows). Out-of-range positions leave [order] as is. */
    fun move(order: List<Int>, from: Int, to: Int): List<Int> {
        if (from !in order.indices || to !in order.indices || from == to) return order
        val m = order.toMutableList()
        m.add(to, m.removeAt(from))
        return m
    }

    /** Makes the entry at position [pos] the reference: it moves to the front, the rest keep their order. */
    fun makeReference(order: List<Int>, pos: Int): List<Int> = move(order, pos, 0)

    /** Removes the entry at position [pos]. */
    fun remove(order: List<Int>, pos: Int): List<Int> =
        if (pos in order.indices) order.toMutableList().also { it.removeAt(pos) } else order

    /**
     * Sorts [order] by the numeric row [rowKey]: descending (best first) or ascending, columns without a value always
     * last, equal values keep their current order (stable). A row that isn't numeric leaves [order] as is.
     */
    fun sortBy(data: CompareData, order: List<Int>, rowKey: String, descending: Boolean = true): List<Int> {
        val row = data.rowOrNull(rowKey)?.takeIf { it.numeric } ?: return order
        val values = values(row)
        val (withValue, without) = order.partition { values.getOrNull(it) != null }
        val cmp = compareBy<Int> { values[it] }
        return withValue.sortedWith(if (descending) cmp.reversed() else cmp) + without // sortedWith is stable
    }

    /** Keeps the entries of [order] whose column passes [filter]. */
    fun filter(data: CompareData, order: List<Int>, filter: ColumnFilter): List<Int> =
        order.filter { data.columns.getOrNull(it)?.let(filter::matches) == true }

    // ---- Putting it together ----

    /**
     * The data a screen draws: columns in [order] (others dropped), only the rows [rows] allows (a section heading moves
     * to its first visible row; a section without visible rows disappears), and the "best" marks recomputed for the
     * columns that are left.
     */
    fun view(data: CompareData, order: List<Int>, rows: RowChoice = RowChoice()): CompareData {
        val idx = order.filter { it in data.columns.indices }
        val cols = idx.map { data.columns[it] }
        val out = ArrayList<CompareRow>()
        var heading: String? = null
        for (row in data.rows) {
            if (row.section != null) heading = row.section
            if (!rows.visible(row.key)) continue
            val cells = idx.map { row.cells[it] }
            val marked = if (row.numeric) {
                val best = CompareTable.bestIndexes(cells.map { (it as? Cell.Text)?.value })
                cells.mapIndexed { i, c -> if (c is Cell.Text) c.copy(best = i in best) else c }
            } else cells
            out += row.copy(cells = marked, section = heading)
            heading = null // only the first visible row of a group carries the heading
        }
        return CompareData(cols, out)
    }

    private fun values(row: CompareRow): List<Int?> = row.cells.map { (it as? Cell.Text)?.value }
}
