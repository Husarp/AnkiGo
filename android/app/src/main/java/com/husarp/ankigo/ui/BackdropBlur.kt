package com.husarp.ankigo.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The blurred, darkened copy of a battle background (round 8, pure, JVM-tested): what fills the panel around a picture that had to
 * shrink, and what shows behind Earthquake's jolt. The same look as the design page (`blur(10px) brightness(.55) saturate(1.15)`),
 * made once per picture on a tiny copy ([smallSize]: long side at most [MAX_SIDE] px), so it costs a few KB and draws as one filtered
 * upscale. All pixels are ARGB ints as in [android.graphics.Bitmap.getPixels].
 */
internal object BackdropBlur {
    /** The working copy's long side. */
    const val MAX_SIDE = 96

    /** Box radius (a window of 2 * [RADIUS] + 1 pixels) and number of passes: three boxes make a near-gaussian of about 2.4 px at this size. */
    const val RADIUS = 2
    const val PASSES = 3

    /** Darker and a little more colourful, like the page: brightness first, then saturation. */
    const val BRIGHTNESS = 0.55f
    const val SATURATION = 1.15f

    /** The glass box's copy ([glass]): a finer blur (about 1.4 px at the working size, 7 px of a phone-wide picture) and saturation 1.35. */
    const val GLASS_RADIUS = 1
    const val GLASS_SATURATION = 1.35f

    /** The size of the copy of a [w] x [h] picture: the long side [MAX_SIDE] (never larger than the picture), the short side in proportion, at least 1. */
    fun smallSize(w: Int, h: Int): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return 1 to 1
        val long = max(w, h)
        if (long <= MAX_SIDE) return w to h
        val k = MAX_SIDE.toFloat() / long
        return max(1, (w * k).roundToInt()) to max(1, (h * k).roundToInt())
    }

    /** The finished copy: [blur] then [grade]. [px] is not changed. */
    fun process(px: IntArray, w: Int, h: Int): IntArray = grade(blur(px, w, h))

    /**
     * Round 10 W7, the glass info box: the picture seen through frosted glass. Not darkened (brightness 1) and a little more colourful
     * ([GLASS_SATURATION], the design page's `blur(7px) saturate(1.35)`), blurred less than the panel fill ([GLASS_RADIUS]): at the
     * working copy's size a box's behind shows what is there, softened. [px] is not changed.
     */
    fun glass(px: IntArray, w: Int, h: Int): IntArray = grade(blur(px, w, h, GLASS_RADIUS), 1f, GLASS_SATURATION)

    /** [passes] box blurs of [radius] (horizontal then vertical each time; the edge pixels repeat outward), alpha included. [px] is not changed. */
    fun blur(px: IntArray, w: Int, h: Int, radius: Int = RADIUS, passes: Int = PASSES): IntArray {
        require(px.size == w * h) { "pixels do not match $w x $h" }
        val cur = px.copyOf()
        if (w <= 0 || h <= 0 || radius <= 0) return cur
        val tmp = IntArray(px.size)
        val line = IntArray(max(w, h))
        val out = IntArray(max(w, h))
        repeat(passes) {
            for (y in 0 until h) { // along x
                for (x in 0 until w) line[x] = cur[y * w + x]
                boxLine(line, w, radius, out)
                for (x in 0 until w) tmp[y * w + x] = out[x]
            }
            for (x in 0 until w) { // along y
                for (y in 0 until h) line[y] = tmp[y * w + x]
                boxLine(line, h, radius, out)
                for (y in 0 until h) cur[y * w + x] = out[y]
            }
        }
        return cur
    }

    /** One line of [n] pixels through a box filter with a window of 2 * [r] + 1, each channel on its own, rounded; positions beyond the line take its end pixel. */
    private fun boxLine(src: IntArray, n: Int, r: Int, dst: IntArray) {
        val size = 2 * r + 1
        for (shift in intArrayOf(24, 16, 8, 0)) {
            var sum = 0
            for (j in -r..r) sum += (src[min(max(j, 0), n - 1)] ushr shift) and 0xFF
            for (i in 0 until n) {
                val v = (sum + size / 2) / size
                dst[i] = if (shift == 24) v shl 24 else dst[i] or (v shl shift)
                // slide the window one pixel on: the one entering on the right, the one leaving on the left
                sum += ((src[min(i + r + 1, n - 1)] ushr shift) and 0xFF) - ((src[max(i - r, 0)] ushr shift) and 0xFF)
            }
        }
    }

    /** Brightness x [brightness], then saturation x [saturation] around the pixel's grey (Rec. 601 weights); alpha stays. [px] is not changed. */
    fun grade(px: IntArray, brightness: Float = BRIGHTNESS, saturation: Float = SATURATION): IntArray {
        val out = IntArray(px.size)
        for (i in px.indices) {
            val p = px[i]
            val r = ((p shr 16) and 0xFF) * brightness
            val g = ((p shr 8) and 0xFF) * brightness
            val b = (p and 0xFF) * brightness
            val grey = 0.299f * r + 0.587f * g + 0.114f * b
            out[i] = (p and 0xFF000000.toInt()) or
                (channel(grey + (r - grey) * saturation) shl 16) or (channel(grey + (g - grey) * saturation) shl 8) or channel(grey + (b - grey) * saturation)
        }
        return out
    }

    private fun channel(v: Float) = min(255, max(0, v.roundToInt()))
}
