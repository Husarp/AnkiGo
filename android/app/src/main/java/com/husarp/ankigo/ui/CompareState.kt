package com.husarp.ankigo.ui

// PLAN item 53 (R3-V5): what the Compare screen remembers between taps. Pure Kotlin (no android.*): unit-tested on the JVM.
// A column is remembered by its ComparePick (stable identity), never by its index in CompareData.columns: that list changes
// when an owned Pokémon is released, so indexes would point at other columns. The functions take/return indexes of the
// CompareData they are given. [order] is the manual order; a sort and the reference column are layered on top of it by
// [display], so a sort never loses the manual order and "clear sort" gets it back.

/** The mode, the column order and the sort / filter of one Compare screen. */
class CompareState(var mode: CompareMode = CompareMode.VS_REFERENCE) {
    /** The manual order (without sort and reference), by pick. Follows the data: see [fit]. */
    private var order: List<ComparePick> = emptyList()
    /** The columns the player took off the screen. */
    private val removed = LinkedHashSet<ComparePick>()
    /** The reference column of "vs reference"; null = the first column of the manual order (before any sort). */
    var ref: ComparePick? = null
        private set
    /** The row key the columns are sorted by (null = the manual order) and its direction. */
    var sortKey: String? = null
        private set
    var sortDesc: Boolean = true
        private set
    var filter: ColumnFilter = ColumnFilter()

    /** Re-fits the state to [data]: drops picks that no longer have a column (an owned Pokémon was released), keeps the order of the rest. */
    fun fit(data: CompareData) {
        val known = data.columns.map { it.pick }
        order = order.filter { it in known && it !in removed } + known.filter { it !in order && it !in removed }
        if (ref != null && ref !in order) ref = null
    }

    private fun indexOf(data: CompareData, pick: ComparePick): Int = data.columns.indexOfFirst { it.pick == pick }

    private fun baseIndexes(data: CompareData): List<Int> {
        fit(data)
        return order.map { indexOf(data, it) }.filter { it >= 0 }
    }

    private fun picksOf(data: CompareData, indexes: List<Int>): List<ComparePick> = indexes.mapNotNull { data.columns.getOrNull(it)?.pick }

    /** The columns to draw, in order: filtered, sorted, and (vs reference) with the reference first. */
    fun display(data: CompareData): List<Int> {
        var list = CompareModes.filter(data, baseIndexes(data), filter)
        // the default reference is the first column BEFORE any sort, so sorting never changes it
        val defaultRef = list.firstOrNull()
        sortKey?.let { list = CompareModes.sortBy(data, list, it, sortDesc) }
        if (mode == CompareMode.VS_REFERENCE && list.isNotEmpty()) {
            val r = ref?.let { indexOf(data, it) }?.takeIf { it in list } ?: defaultRef!!
            list = CompareModes.makeReference(list, list.indexOf(r))
        }
        return list
    }

    /** The column that is the reference now (vs reference), or null in "best of all" / with no columns. */
    fun reference(data: CompareData): Int? =
        if (mode == CompareMode.VS_REFERENCE) display(data).firstOrNull() else null

    /** Tapping a row label: a new row sorts high to low, the same row again flips the direction. */
    fun tapRow(key: String) {
        if (sortKey == key) sortDesc = !sortDesc else { sortKey = key; sortDesc = true }
    }

    fun setSort(key: String?, desc: Boolean = true) { sortKey = key; sortDesc = desc }

    fun makeReference(pick: ComparePick) { ref = pick }

    fun makeReference(data: CompareData, columnIndex: Int) { data.columns.getOrNull(columnIndex)?.pick?.let(::makeReference) }

    /** Position of [columnIndex] among the shown columns -> moves it one place left (-1) or right (+1) among the movable ones. */
    fun move(data: CompareData, columnIndex: Int, step: Int) {
        val shown = display(data)
        val movable = if (mode == CompareMode.VS_REFERENCE && shown.isNotEmpty()) shown.drop(1) else shown
        val at = movable.indexOf(columnIndex)
        if (at < 0) return
        moveTo(data, columnIndex, shown.indexOf(columnIndex) + step)
    }

    /**
     * Drag and drop (R4-X1 item 7): moves [columnIndex] to place [toPos] among the shown columns (the reference column of
     * "vs reference" keeps place 0; a [toPos] outside the movable places is pulled in).
     */
    fun moveTo(data: CompareData, columnIndex: Int, toPos: Int) {
        val shown = display(data)
        val movable = if (mode == CompareMode.VS_REFERENCE && shown.isNotEmpty()) shown.drop(1) else shown
        val at = movable.indexOf(columnIndex)
        if (at < 0) return
        val offset = shown.size - movable.size
        val moved = CompareModes.move(movable, at, (toPos - offset).coerceIn(0, (movable.size - 1).coerceAtLeast(0)))
        // the shown order becomes the manual order: the sort is spent, the reference stays first
        val head = if (movable.size < shown.size) listOf(shown.first()) else emptyList()
        val hidden = baseIndexes(data).filter { it !in shown }
        order = picksOf(data, head + moved + hidden)
        sortKey = null
        if (head.isNotEmpty()) ref = data.columns[head.first()].pick
    }

    /** True when [columnIndex] can move one place [step] (-1 / +1) among the movable shown columns. */
    fun canMove(data: CompareData, columnIndex: Int, step: Int): Boolean {
        val shown = display(data)
        val movable = if (mode == CompareMode.VS_REFERENCE && shown.isNotEmpty()) shown.drop(1) else shown
        val at = movable.indexOf(columnIndex)
        return at >= 0 && at + step in movable.indices
    }

    /** Takes [columnIndex] off the screen. The reference passes to the next shown column. */
    fun remove(data: CompareData, columnIndex: Int) {
        val pick = data.columns.getOrNull(columnIndex)?.pick ?: return
        fit(data)
        removed += pick
        order = order.filter { it != pick }
        if (ref == pick) ref = null
    }

    /** How many columns are left (before any filter). */
    val size: Int get() = order.size

    /** Back from an empty screen: no filter, no sort (removed columns stay removed). */
    fun resetView() {
        filter = ColumnFilter()
        sortKey = null
    }
}

/** What Compare remembers in prefs (SharedPreferences "ankigo_ui"): the mode and the hidden rows. */
object CompareStore {
    private const val FILE = "ankigo_ui"
    private const val MODE = "compare_mode"
    private const val ROWS = "compare_rows_hidden"
    private const val FIT = "compare_fit"
    private const val GROUP = "compare_group"

    fun loadMode(ctx: android.content.Context): CompareMode =
        CompareMode.entries.firstOrNull { it.name == prefs(ctx).getString(MODE, null) } ?: CompareMode.VS_REFERENCE

    fun saveMode(ctx: android.content.Context, m: CompareMode) = prefs(ctx).edit().putString(MODE, m.name).apply()

    fun loadRows(ctx: android.content.Context): RowChoice = RowChoice.decode(prefs(ctx).getString(ROWS, null))

    fun saveRows(ctx: android.content.Context, r: RowChoice) = prefs(ctx).edit().putString(ROWS, r.encode()).apply()

    /** R6-05 item 7: "Stats" or "Base stats" (default Stats). */
    fun loadGroup(ctx: android.content.Context): CompareGroup =
        CompareGroup.entries.firstOrNull { it.name == prefs(ctx).getString(GROUP, null) } ?: CompareGroup.STATS

    fun saveGroup(ctx: android.content.Context, g: CompareGroup) = prefs(ctx).edit().putString(GROUP, g.name).apply()

    /** The zoom: how many columns fit (see [CompareFit]); automatic until the player picks one. */
    fun loadFit(ctx: android.content.Context): Int = CompareFit.valid(prefs(ctx).getInt(FIT, CompareFit.AUTO))

    fun saveFit(ctx: android.content.Context, fit: Int) = prefs(ctx).edit().putInt(FIT, CompareFit.valid(fit)).apply()

    private fun prefs(ctx: android.content.Context) = ctx.applicationContext.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE)
}

/** Compare's picks as text, for the Intent of the Compare screen: "o:<uid>" (owned) / "d:<species>" (Pokédex). */
object CompareArgs {
    fun encode(p: ComparePick): String = when (p) { is ComparePick.Owned -> "o:${p.uid}"; is ComparePick.Dex -> "d:${p.species}" }

    fun decode(s: String): ComparePick? = when {
        s.startsWith("o:") && s.length > 2 -> ComparePick.Owned(s.substring(2))
        s.startsWith("d:") -> s.substring(2).toIntOrNull()?.let { ComparePick.Dex(it) }
        else -> null
    }

    fun decodeAll(list: List<String>?): List<ComparePick> = list.orEmpty().mapNotNull(::decode)
}
