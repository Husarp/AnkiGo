package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Matchup
import com.husarp.ankigo.game.Type
import kotlin.math.roundToInt

// R4-X1 (Compare items 7, 8, 9, 10, 12): the pure rules behind the Compare table's zoom, label fitting, column drag, the
// red-to-green value scale and the "what is this" texts. No android.*: unit-tested on the JVM; CompareGrid / ComparePage draw them.

/** The zoom choice: how many columns fit next to the label column (0 = automatic, from the window width, as before). */
object CompareFit {
    const val AUTO = 0
    val OPTIONS = listOf(3, 4, 5, 6)

    /** A stored value is kept only when it is [AUTO] or one of [OPTIONS]. */
    fun valid(v: Int): Int = if (v in OPTIONS) v else AUTO

    fun label(v: Int): String = if (valid(v) == AUTO) "Auto" else "${valid(v)}"
}

/** The column width and the text / sprite scale of the table. */
object CompareSizing {
    /** The column width every size was designed for (dp). */
    const val REF_COL_DP = 72f

    /** The window width that makes about 5 columns fit in automatic mode: one column per this many dp. */
    private const val AUTO_STEP_DP = 66

    /**
     * Width of one column in dp. Automatic ([fit] = [CompareFit.AUTO]): about 5 on a phone, fewer in a small window, and a table
     * with fewer columns fills the width. A chosen [fit] always makes `fit` columns fit. With more columns than fit, a sliver of the
     * next one shows (0.4) so it is clear the table scrolls.
     */
    fun colDp(viewportDp: Int, labelDp: Int, columns: Int, fit: Int): Float {
        val avail = (viewportDp - labelDp).toFloat()
        val f = CompareFit.valid(fit)
        val visible = if (f != CompareFit.AUTO) f else ((viewportDp - labelDp) / AUTO_STEP_DP).coerceIn(2, 5)
        val across = if (f != CompareFit.AUTO) (if (columns > visible) visible + 0.4f else visible.toFloat())
        else (if (columns > visible) visible + 0.4f else columns.coerceAtLeast(1).toFloat())
        return avail / across
    }

    /** Scale of text, sprites and row heights: 1 in automatic mode (nothing changes there); follows the column width when zoomed. */
    fun zoom(colDp: Float, fit: Int): Float =
        if (CompareFit.valid(fit) == CompareFit.AUTO) 1f else (colDp / REF_COL_DP).coerceIn(0.8f, 1.3f)
}

/** Fitting a row label into the label column without breaking a word in the middle. */
object CompareLabelFit {
    /**
     * The text size (sp) for a label whose longest word is [wordPx] wide at [maxSp], in [availPx] pixels: [maxSp] when it fits,
     * else shrunk in proportion, but never below [minSp] (then the word is as small as it may get).
     */
    fun sp(maxSp: Float, minSp: Float, wordPx: Float, availPx: Float): Float =
        if (wordPx <= availPx || wordPx <= 0f || availPx <= 0f) maxSp else (maxSp * availPx / wordPx).coerceIn(minSp, maxSp)

    /** The longest run of non-space characters in [text]. */
    fun longestWord(text: String): String = text.split(' ', '\n').maxByOrNull { it.length }.orEmpty()
}

/** Dragging a column to a new place (long press, then move the finger). Positions are indexes among the shown columns. */
object CompareDrag {
    /** The place the dragged column would take: its left edge [leftPx] rounded to a column slot, between [minPos] and the last. */
    fun target(leftPx: Float, labelPx: Float, colPx: Float, n: Int, minPos: Int): Int {
        if (n <= 0 || colPx <= 0f) return minPos
        return ((leftPx - labelPx) / colPx).roundToInt().coerceIn(minPos.coerceAtMost(n - 1), n - 1)
    }

    /** How many places column [j] steps aside (-1 left, +1 right, 0 stays) while column [from] hovers at [target]. */
    fun shift(j: Int, from: Int, target: Int): Int = when {
        j == from -> 0
        from < target && j in (from + 1)..target -> -1
        from > target && j in target until from -> 1
        else -> 0
    }

    /** Pixels to scroll the table while the finger is at [x] (view coordinates): negative = left, 0 = none. */
    fun autoScroll(x: Float, viewWidth: Float, stickyPx: Float, edgePx: Float, maxStepPx: Float): Float = when {
        x < stickyPx + edgePx -> -maxStepPx * ((stickyPx + edgePx - x) / edgePx).coerceIn(0f, 1f)
        x > viewWidth - edgePx -> maxStepPx * ((x - (viewWidth - edgePx)) / edgePx).coerceIn(0f, 1f)
        else -> 0f
    }
}

/** What a tap on a label or a type icon says (item 9). */
object CompareInfo {
    /** Title and text of a row label; null for no row of this table. Numeric rows reuse the stat texts. */
    fun forRow(key: String, label: String): StatInfoText? = StatInfo.forKey(key) ?: when (key) {
        "types" -> StatInfoText("Types", "The Pokémon's type or types. They decide which attacks are strong or weak against it.")
        "moves" -> StatInfoText("Moves", "The attacks it knows now, with their type and power. A dash means the move does not hit for damage.")
        "weak" -> StatInfoText("Weak to", "Attack types that hit it for double (×2) or four times (×4) damage.")
        "resist" -> StatInfoText("Resists", "Attack types that hit it for half (×½) or a quarter (×¼) damage.")
        "immune" -> StatInfoText("Immune", "Attack types that cannot hurt it at all (×0).")
        "evo" -> StatInfoText("Evolution line", "The family it belongs to, in the order it evolves. The highlighted one is this Pokémon.")
        "becomes" -> StatInfoText("Becomes", "The last form it can evolve into.")
        else -> null
    } ?: label.takeIf { it.isNotBlank() }?.let { StatInfoText(it, it) }

    fun typeText(t: Type): String = "${t.label} type"

    /**
     * R6-05 item 8: what a column head's menu says now that the heads carry no tags: a Pokédex entry says so, an owned Pokémon
     * lists its tags ("Main · Exp. Share"), then the "Fully evolved" note. Null when there is nothing to say.
     */
    fun headInfo(dex: Boolean, tags: List<String>, note: String?): String? =
        (listOfNotNull(if (dex) "Pokédex entry, not one of your Pokémon" else null) + tags + listOfNotNull(note))
            .filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { null }

    /** "Water: ×2, takes double damage" for a matchup chip. */
    fun matchupText(m: Matchup): String = "${m.type.label} ${m.label}: " + when (m.mult) {
        4.0 -> "takes four times the damage"
        2.0 -> "takes double damage"
        0.5 -> "takes half the damage"
        0.25 -> "takes a quarter of the damage"
        0.0 -> "takes no damage"
        else -> "damage ×${m.mult}"
    }
}
