package com.husarp.ankigo.overlay

/** Pure window geometry for [FloatWindow] (px, no Android types) so it can be unit-tested on the JVM. */
internal object FloatGeometry {
    data class Box(val x: Int, val y: Int, val w: Int, val h: Int)

    /** [v] limited to lo..hi; when the range is empty (screen smaller than the minimum) [lo] wins. */
    private fun fit(v: Int, lo: Int, hi: Int): Int = if (hi < lo) lo else v.coerceIn(lo, hi)

    /** Size between min and the screen (the minimum gives way when the screen is smaller), position fully on screen. */
    fun clamp(b: Box, screenW: Int, screenH: Int, minW: Int, minH: Int): Box {
        val w = fit(b.w, minOf(minW, screenW), screenW)
        val h = fit(b.h, minOf(minH, screenH), screenH)
        return Box(fit(b.x, 0, screenW - w), fit(b.y, 0, screenH - h), w, h)
    }

    /** First-use position: centred on the whole display (screen = full display, not the split-screen half). */
    fun default(screenW: Int, screenH: Int, w: Int, h: Int, minW: Int, minH: Int): Box =
        clamp(Box((screenW - w) / 2, (screenH - h) / 2, w, h), screenW, screenH, minW, minH)

    fun move(start: Box, dx: Int, dy: Int, screenW: Int, screenH: Int, minW: Int, minH: Int): Box =
        clamp(start.copy(x = start.x + dx, y = start.y + dy), screenW, screenH, minW, minH)

    /** Drag of the bottom-right grip: grows towards the screen edge (the top-left corner stays put), never below the minimum. */
    fun resize(start: Box, dx: Int, dy: Int, screenW: Int, screenH: Int, minW: Int, minH: Int): Box {
        val w = maxOf(minW, minOf(start.w + dx, screenW - start.x))
        val h = maxOf(minH, minOf(start.h + dy, screenH - start.y))
        return clamp(start.copy(w = w, h = h), screenW, screenH, minW, minH)
    }
}
