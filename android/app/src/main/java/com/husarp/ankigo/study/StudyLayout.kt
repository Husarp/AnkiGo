package com.husarp.ankigo.study

/** Which layout the study screen uses for a window: columns or stacked, how wide each column is, which panel mode and button rows. */
object StudyLayout {
    /** [mode]: battle panel mode (0 full with chips, 1 strip, 2 mini); null = stacked (portrait), where the user's height handle decides. */
    data class Plan(
        val columns: Boolean, val cardShare: Float, val mode: Int?, val narrow: Boolean,
        val answerRows: Int, val summaryMaxDp: Int, val tablet: Boolean = false,
    ) {
        val sideShare get() = 1f - cardShare
        val handle get() = mode == null
        /** Height (dp) of the stacked battle panel for [mode] (0 full, 1 strip, 2 mini); tablets get taller Full and Strip. */
        fun panelDp(mode: Int) = PanelHeight.preset(tablet, mode)
    }

    const val TABLET_DP = 600
    /** A split-screen window narrower than this (dp) gets 50/50 columns and the compact fixed-height strip. */
    const val NARROW_DP = 560
    /** The landscape tablet battle column is at least this wide (dp). */
    const val SIDE_MIN_DP = 340
    const val SUMMARY_MAX_DP = 640

    /** Widest content (dp) for one-column pages (summary, deck picker) on tablets; 0 = no cap (phones). */
    fun contentMaxDp(smallestDp: Int) = if (smallestDp >= TABLET_DP) SUMMARY_MAX_DP else 0

    fun plan(widthDp: Int, smallestDp: Int, landscape: Boolean, finished: Boolean): Plan {
        val tablet = smallestDp >= TABLET_DP
        val summary = contentMaxDp(smallestDp)
        if (finished) return Plan(false, 1f, null, false, 1, summary, tablet)
        if (!landscape) return Plan(false, 1f, null, false, 1, summary, tablet)
        if (tablet) return Plan(true, 1f - maxOf(.42f, SIDE_MIN_DP.toFloat() / widthDp).coerceAtMost(.5f), 0, false, 1, summary, true)
        val narrow = widthDp < NARROW_DP
        return Plan(true, if (narrow) .5f else .58f, 1, narrow, 2, 0)
    }
}
