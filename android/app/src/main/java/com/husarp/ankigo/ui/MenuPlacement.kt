package com.husarp.ankigo.ui

/**
 * Where a popup menu goes so that it stays fully on screen (pure maths, unit-tested; [AppMenu] feeds it real pixels).
 * All numbers are pixels in ONE coordinate space (the anchor window's). The [Box] is left/top inclusive, right/bottom exclusive.
 */
object MenuPlacement {
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }

    /** The popup window: its top-left corner and its size. Taller than [height] means: scroll inside. */
    data class Spot(val x: Int, val y: Int, val width: Int, val height: Int)

    /**
     * Opens the menu under [anchor], right edge to right edge; flips above when it does not fit below; slides sideways to stay
     * inside [bounds]; shrinks (and so scrolls) when it is taller than the room on either side.
     *
     * @param menuW,menuH the menu window's wanted size (its card plus shadow room)
     * @param inset how far the visible card sits inside the window (shadow room); the card, not the shadow, hugs the anchor
     * @param minUseful below this much room on the roomier side, the menu covers the anchor instead and uses all of [bounds]
     */
    fun place(anchor: Box, menuW: Int, menuH: Int, bounds: Box, inset: Int, minUseful: Int): Spot {
        val w = minOf(menuW, bounds.width)
        // the anchor, kept inside the screen (a long row half scrolled out still has a visible part)
        val a = Box(
            anchor.left.coerceIn(bounds.left, bounds.right), anchor.top.coerceIn(bounds.top, bounds.bottom),
            anchor.right.coerceIn(bounds.left, bounds.right), anchor.bottom.coerceIn(bounds.top, bounds.bottom),
        )
        val x = (a.right + inset - w).coerceIn(bounds.left, bounds.right - w)
        val belowTop = (a.bottom - inset / 2).coerceIn(bounds.top, bounds.bottom)
        val aboveBottom = (a.top + inset / 2).coerceIn(bounds.top, bounds.bottom)
        val roomBelow = (bounds.bottom - belowTop).coerceAtLeast(0)
        val roomAbove = (aboveBottom - bounds.top).coerceAtLeast(0)
        return when {
            menuH <= roomBelow -> Spot(x, belowTop, w, menuH)
            menuH <= roomAbove -> Spot(x, aboveBottom - menuH, w, menuH)
            maxOf(roomBelow, roomAbove) >= minOf(menuH, minUseful) ->
                if (roomBelow >= roomAbove) Spot(x, belowTop, w, roomBelow) else Spot(x, bounds.top, w, roomAbove)
            else -> {
                val h = minOf(menuH, bounds.height)
                Spot(x, (a.top - inset).coerceIn(bounds.top, bounds.bottom - h), w, h)
            }
        }
    }
}
