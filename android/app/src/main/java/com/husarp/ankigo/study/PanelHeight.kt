package com.husarp.ankigo.study

/**
 * Height rules of the stacked study screen's battle panel (the drag bar between the card and the battle).
 * All numbers are dp. Presets: Full / Strip / Mini (mode 0 / 1 / 2). The panel mode follows the height.
 */
object PanelHeight {
    const val MINI = 34
    /** Below this height only the one-line log fits (Mini look); from here up the strip look, from the Full height up the Full look. */
    const val MINI_LINE = 100
    /** A release this close (dp) to a preset snaps to it. */
    const val SNAP = 24
    /** The card area (card + its top bar) keeps at least this much. */
    const val MIN_CARD = 120
    /** The drag bar's touch height. */
    const val BAR = 24
    /** The study top bar: the 48 dp row (R18: was 45, the ⋯ button got a 48 dp touch area) + its 1 dp line. */
    const val TOP_BAR = 49
    /** A tap this close (dp) to a preset height counts as being on it. */
    private const val SAME = 2

    fun preset(tablet: Boolean, mode: Int) = when (mode) { 0 -> if (tablet) 400 else 220; 2 -> MINI; else -> if (tablet) 240 else 180 }

    /** Height to open with: the saved dp; else the old three-way pref (0/1/2, migrated to its preset); else the default (tablet Full, phone Strip). */
    fun restore(savedDp: Int, oldMode: Int, tablet: Boolean): Int = when {
        savedDp > 0 -> savedDp
        oldMode in 0..2 -> preset(tablet, oldMode)
        else -> preset(tablet, if (tablet) 0 else 1)
    }

    /** Keeps the panel between the Mini height and the most the card area allows ([maxDp], never below Mini). */
    fun clamp(dp: Int, maxDp: Int) = dp.coerceIn(MINI, maxOf(MINI, maxDp))

    /** The most the panel may take: [bodyDp] (card column + battle column) minus the fixed parts and the card's minimum. */
    fun maxDp(bodyDp: Int, buttonsDp: Int) = bodyDp - TOP_BAR - BAR - buttonsDp - MIN_CARD

    /** Panel look for [dp]: 0 full, 1 strip, 2 mini. */
    fun mode(dp: Int, fullDp: Int) = if (dp < MINI_LINE) 2 else if (dp < fullDp) 1 else 0

    /** Where a released drag settles: the nearest preset within [SNAP]; below the Mini line the Mini height (nothing fits above it); else free. */
    fun snap(dp: Int, tablet: Boolean): Int {
        if (dp < MINI_LINE) return MINI
        val near = (0..2).map { preset(tablet, it) }.filter { it >= MINI_LINE }.minByOrNull { Math.abs(it - dp) }
        return if (near != null && Math.abs(near - dp) <= SNAP) near else dp
    }

    /** A tap steps down Full > Strip > Mini and from Mini back to Full (from a free height: the next preset below it). */
    fun tap(dp: Int, tablet: Boolean): Int =
        listOf(preset(tablet, 0), preset(tablet, 1), preset(tablet, 2)).firstOrNull { it < dp - SAME } ?: preset(tablet, 0)

    /** Height (dp) of the answer-button container: it never changes while the card changes, so nothing above it moves. */
    fun buttonsDp(answerRows: Int) = (if (answerRows == 2) 40 + 6 + 40 else 48) + 16
}
