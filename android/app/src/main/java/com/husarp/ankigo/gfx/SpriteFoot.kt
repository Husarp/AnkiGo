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

    /**
     * Round 9 "real art box": the box of the visible pixels (alpha above [ALPHA]) over all the frames of an animation, as an
     * accumulator of four ints (left, top, right, bottom; right and bottom exclusive). [emptyBounds] starts one, [addFrame] adds a
     * frame, [isEmpty] says if nothing visible was seen. Pure maths on pixel arrays; [SpriteView.artShare] feeds it the bitmaps once
     * per image.
     */
    fun emptyBounds(): IntArray = intArrayOf(Int.MAX_VALUE, Int.MAX_VALUE, -1, -1)

    fun isEmpty(bounds: IntArray): Boolean = bounds[2] < 0

    /** Adds the visible pixels of one [w] x [h] frame ([argb], row-major, at least w * h ints) to [bounds]. */
    fun addFrame(bounds: IntArray, argb: IntArray, w: Int, h: Int) {
        for (y in 0 until h) {
            val row = y * w
            var x0 = 0
            while (x0 < w && (argb[row + x0] ushr 24) <= ALPHA) x0++
            if (x0 == w) continue
            var x1 = w - 1
            while ((argb[row + x1] ushr 24) <= ALPHA) x1--
            if (x0 < bounds[0]) bounds[0] = x0
            if (x1 + 1 > bounds[2]) bounds[2] = x1 + 1
            if (y < bounds[1]) bounds[1] = y
            if (y + 1 > bounds[3]) bounds[3] = y + 1
        }
    }
}
