package com.husarp.ankigo.ui

/** Pure layout rules of [AppDialog]; no Android classes, so they are unit-tested on the JVM. */
object AppDialogRules {
    const val MAX_WIDTH_DP = 560
    const val SIDE_MARGIN_DP = 16

    /** Width of the dialog card in dp: at most 560, with 16 dp of margin on each side of a narrow window. */
    fun widthDp(windowWidthDp: Int): Int = (windowWidthDp - 2 * SIDE_MARGIN_DP).coerceIn(0, MAX_WIDTH_DP)

    /**
     * Splits the buttons (their widths, in order) into rows that fit [avail] px with [gap] px between neighbours: a row is
     * filled until the next button would not fit; every row has at least one button (a button wider than the row stays alone).
     * Returns the indices per row. Three buttons on a narrow screen come out as two rows.
     */
    fun rows(widths: List<Int>, avail: Int, gap: Int): List<List<Int>> {
        val out = ArrayList<List<Int>>()
        var row = ArrayList<Int>()
        var used = 0
        widths.forEachIndexed { i, w ->
            val need = if (row.isEmpty()) w else used + gap + w
            if (row.isNotEmpty() && need > avail) { out += row; row = ArrayList(); used = 0 }
            used = if (row.isEmpty()) w else used + gap + w
            row += i
        }
        if (row.isNotEmpty()) out += row
        return out
    }

    /** Text colour on the filled button: white or [dark], whichever has the higher contrast against [fill] ([contrast] = WCAG ratio). */
    fun onFill(fill: Int, dark: Int, contrast: (Int, Int) -> Double): Int {
        val white = 0xFFFFFFFF.toInt()
        return if (contrast(white, fill) >= contrast(dark, fill)) white else dark
    }
}
