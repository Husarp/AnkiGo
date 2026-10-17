package com.husarp.ankigo.ui

/**
 * What a catch result that is already on screen does when its window changes (R4 fix B7; pure, [BattlePanelView] applies it).
 * [CatchTilesPlan.mode] is decided when the result arrives; the window can change afterwards (full battle -> picture-in-picture
 * or a smaller floating window, or the log stepping out): a result that was HOLD (stays until the next answer, the next wild
 * one parked behind it) must then not stay for ever in a window that only shows results for [CatchTilesPlan.SMALL_MS].
 */
object CatchResultShift {
    enum class Do {
        /** Nothing changes: the result keeps its mode and its timer. */
        KEEP,
        /** The window is small now: start the [CatchTilesPlan.SMALL_MS] timeout and let the parked next wild one appear. */
        TIMEOUT,
        /** No room for a result any more (the log stepped out): it goes at once and the parked next wild one appears. */
        HIDE,
    }

    /**
     * [timed] = the result already runs on the small-window timeout; [full] / [logShown] as for [CatchTilesPlan.mode] in the new
     * window. A timed result stays timed when the window grows again (the next wild one is on stage already); a held one
     * never comes back from a small window.
     */
    fun after(timed: Boolean, full: Boolean, logShown: Boolean): Do = when (CatchTilesPlan.mode(full, logShown, answerWaiting = false)) {
        CatchTilesPlan.Mode.NONE -> Do.HIDE
        CatchTilesPlan.Mode.HOLD -> Do.KEEP
        CatchTilesPlan.Mode.TIMED -> if (timed) Do.KEEP else Do.TIMEOUT
    }
}
