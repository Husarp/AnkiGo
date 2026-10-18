package com.husarp.ankigo.gfx

/**
 * Where a sprite's feet are (pure maths, JVM-tested): the lowest row that holds a visible pixel, over all the frames of an
 * animation. Gen-5 frames keep empty rows under the feet, 3D ones none; [SpriteView.anchorFeet] uses this to put the feet,
 * not the frame's bottom edge, on the spot the Pokémon stands on (round 8 stage rule).
 */
internal object SpriteFoot {
    /** A pixel belongs to the Pokémon above this alpha (0..255). */
    const val ALPHA = 16

    /** True when any of the [n] pixels of [argb] from [from] on is visible. */
    fun anyVisible(argb: IntArray, from: Int, n: Int): Boolean {
        for (i in from until from + n) if ((argb[i] ushr 24) > ALPHA) return true
        return false
    }

    /**
     * The lowest row (0 = top) with a visible pixel in any of [frames] frames of [rows] rows; [visible] says if row y of frame f has
     * one. Each frame is scanned from the bottom up and only as far as the best row so far. -1 = nothing visible anywhere.
     */
    inline fun lowestRow(frames: Int, rows: Int, visible: (frame: Int, row: Int) -> Boolean): Int {
        var best = -1
        for (f in 0 until frames) {
            var y = rows - 1
            while (y > best && !visible(f, y)) y--
            if (y > best) best = y
        }
        return best
    }

    /** The empty rows under the feet of an image [rows] high whose [lowest] visible row is known (0 when nothing is visible). */
    fun rowsBelow(rows: Int, lowest: Int): Int = if (lowest < 0) 0 else (rows - 1 - lowest).coerceAtLeast(0)
}
