package com.husarp.ankigo.ui

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * R19-F (PLAN 274), the sharp battle background (pure maths + a tiny runner; JVM-tested in `R19FBackdropSharpTest`).
 *
 * Why the pictures looked soft: Adam's 23 pictures are pixel-art-style images (about 5-6 file pixels per art pixel, with a 1 px soft
 * edge), 1024-1376 px wide, and were drawn with the GPU's bilinear filter ([Backdrop.crisp] false). On a phone they land at
 * 1.0 - 1.6 screen px per file px, so every art-pixel edge got smeared over a pixel or two; in the smaller hosts (Study panel,
 * picture-in-picture) they land at 0.45 - 0.9 and the GPU's two-tap filter both smears and aliases. Showdown's 12 were already drawn
 * with nearest neighbour, which at 0.45 - 0.9 drops pixels unevenly.
 *
 * What "sharp" does: nothing about the crop or the stage changes. A picture that lands at [HARD_MIN] screen px per file px or more is
 * drawn with no smoothing at all (hard pixel edges, [Backdrop.crisp] now true for Adam's pictures too). A picture that lands under it
 * (a smaller arena) is first reduced once, off the main thread, to exactly the pixels it will occupy on the screen with a Lanczos-3
 * filter ([resample]) and then drawn 1:1 without any filter, so it is clean instead of aliased.
 */
internal object BackdropSharp {
    /** At or above this many screen px per file px the picture is drawn by the GPU with nearest neighbour; under it [resample] makes the copy. */
    const val HARD_MIN = 0.85f

    /** The copy is made this long after the last layout change (a dragged panel height does not make one per frame). */
    const val DEBOUNCE_MS = 90L

    /** True when a picture drawn at [scale] screen px per file px (the smaller of the two axes) is drawn by the GPU, without smoothing. */
    fun hard(scale: Float): Boolean = scale >= HARD_MIN

    // ---- the runner (a seam: the preview and the tests run everything in place) -------------------------------------------

    /** Where the copy is made and delivered. */
    internal interface Runner {
        fun background(job: Runnable)
        fun main(delayMs: Long, job: Runnable)

        /** True when a job has run before the call that asked for it returns (then nothing needs to ask for another frame: the caller is drawing or about to). */
        val inPlace: Boolean get() = false
    }

    private object RealRunner : Runner {
        private val pool: Executor by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "backdrop-sharp").apply { isDaemon = true } } }
        private val handler by lazy { Handler(Looper.getMainLooper()) }
        override fun background(job: Runnable) = pool.execute(job)
        override fun main(delayMs: Long, job: Runnable) { if (delayMs <= 0L) handler.post(job) else handler.postDelayed(job, delayMs) }
    }

    /** Runs jobs at once, on the calling thread (preview renders and tests). */
    internal object InPlace : Runner {
        override val inPlace: Boolean get() = true
        override fun background(job: Runnable) = job.run()
        override fun main(delayMs: Long, job: Runnable) = job.run()
    }

    /** The preview renderer (Paparazzi) has no real threads or clock to wait on: it makes the copy in place, like [ArenaBackdrop]'s reduced motion check. */
    private fun previewRenderer(): Boolean =
        android.os.Build.DEVICE == "layoutlib" || android.os.Build.FINGERPRINT?.contains("layoutlib") == true

    @Volatile internal var runner: Runner = if (previewRenderer()) InPlace else RealRunner

    // ---- Lanczos-3 resampling ------------------------------------------------------------------------------------------------

    private const val FIX = 14
    private const val ONE = 1 shl FIX
    private const val LOBES = 3f

    private fun lanczos(x: Float): Float {
        val a = abs(x)
        if (a >= LOBES) return 0f
        if (a < 1e-6f) return 1f
        val px = PI.toFloat() * a
        return LOBES * sin(px) * sin(px / LOBES) / (px * px)
    }

    /** The taps of every output pixel along one axis: [start] (first source index), [count], and [w] (fixed point, [FIX] bits, each run adds up to exactly [ONE]). */
    private class Taps(val start: IntArray, val count: IntArray, val offset: IntArray, val w: IntArray, val lo: Int, val hi: Int)

    /**
     * Taps for [n] output pixels over the source span [from]..[to] (source pixels, may be fractional) of a line of [size] pixels.
     * A reduction widens the filter by the step, so every source pixel counts; positions outside the line take its end pixel.
     */
    private fun taps(size: Int, from: Float, to: Float, n: Int): Taps {
        val step = (to - from) / n
        val scale = max(1f, step)
        val support = LOBES * scale
        val start = IntArray(n)
        val count = IntArray(n)
        val offset = IntArray(n)
        val tmp = ArrayList<Int>()
        var lo = Int.MAX_VALUE
        var hi = Int.MIN_VALUE
        val weights = FloatArray(ceil(2f * support).toInt() + 3)
        for (i in 0 until n) {
            val center = from + (i + 0.5f) * step
            val first = floor(center - support + 0.5f).toInt()
            val last = floor(center + support + 0.5f).toInt()
            var sum = 0f
            var c = 0
            for (j in first until last) {
                val v = lanczos((j + 0.5f - center) / scale)
                weights[c++] = v
                sum += v
            }
            // fixed point, renormalised so the run adds up to exactly ONE (the largest tap takes the rounding)
            val fixed = IntArray(c)
            var total = 0
            var big = 0
            for (k in 0 until c) {
                fixed[k] = Math.round(weights[k] / sum * ONE)
                total += fixed[k]
                if (abs(fixed[k]) > abs(fixed[big])) big = k
            }
            fixed[big] += ONE - total
            start[i] = first
            count[i] = c
            offset[i] = tmp.size
            for (k in 0 until c) tmp.add(fixed[k])
            lo = min(lo, first)
            hi = max(hi, last)
        }
        return Taps(start, count, offset, tmp.toIntArray(), lo, hi)
    }

    /**
     * The part [x0]..[x1] x [y0]..[y1] of the [sw] x [sh] picture [px] (ARGB, opaque) reduced to [ow] x [oh] pixels with a Lanczos-3
     * filter (separable: columns, then rows; 8 bits between the passes). The result is opaque. The window may reach past the picture
     * (its end pixels repeat) and may even be smaller than the output (then it is an enlargement; the app uses it for reductions).
     */
    fun resample(px: IntArray, sw: Int, sh: Int, x0: Float, y0: Float, x1: Float, y1: Float, ow: Int, oh: Int): IntArray {
        require(px.size >= sw * sh && sw > 0 && sh > 0 && ow > 0 && oh > 0 && x1 > x0 && y1 > y0) { "bad resample request" }
        val tx = taps(sw, x0, x1, ow)
        val ty = taps(sh, y0, y1, oh)
        val rowLo = max(0, ty.lo)
        val rowHi = min(sh, ty.hi)
        val rows = rowHi - rowLo
        val mid = IntArray(ow * rows)
        // columns: every needed source row once
        for (r in 0 until rows) {
            val rowBase = (rowLo + r) * sw
            val midBase = r * ow
            for (i in 0 until ow) {
                var ar = 0; var ag = 0; var ab = 0
                val s = tx.start[i]
                val o = tx.offset[i]
                for (k in 0 until tx.count[i]) {
                    val p = px[rowBase + min(max(s + k, 0), sw - 1)]
                    val wk = tx.w[o + k]
                    ar += wk * ((p shr 16) and 0xFF); ag += wk * ((p shr 8) and 0xFF); ab += wk * (p and 0xFF)
                }
                mid[midBase + i] = pack(ar, ag, ab)
            }
        }
        // rows
        val out = IntArray(ow * oh)
        val accR = IntArray(ow); val accG = IntArray(ow); val accB = IntArray(ow)
        for (j in 0 until oh) {
            java.util.Arrays.fill(accR, 0); java.util.Arrays.fill(accG, 0); java.util.Arrays.fill(accB, 0)
            val s = ty.start[j]
            val o = ty.offset[j]
            for (k in 0 until ty.count[j]) {
                val row = min(max(s + k, rowLo), rowHi - 1) - rowLo
                val wk = ty.w[o + k]
                val base = row * ow
                for (i in 0 until ow) {
                    val p = mid[base + i]
                    accR[i] += wk * ((p shr 16) and 0xFF); accG[i] += wk * ((p shr 8) and 0xFF); accB[i] += wk * (p and 0xFF)
                }
            }
            val ob = j * ow
            for (i in 0 until ow) out[ob + i] = pack(accR[i], accG[i], accB[i])
        }
        return out
    }

    private fun pack(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)

    private fun clamp(sum: Int): Int = min(255, max(0, (sum + (ONE shr 1)) shr FIX))
}
