package com.husarp.ankigo.ui.movefx

import kotlin.math.pow

/** A point in the effect layer's pixels. */
class FxPt(@JvmField val x: Float, @JvmField val y: Float) {
    override fun toString() = "($x, $y)"
}

/** An easing curve: progress 0..1 in, 0..1 out (the mockup's `AA.ease`). */
fun interface FxEase {
    fun at(p: Float): Float

    companion object {
        @JvmField val LIN = FxEase { it }
        @JvmField val IN2 = FxEase { it * it }
        @JvmField val OUT2 = FxEase { 1f - (1f - it) * (1f - it) }
        @JvmField val INOUT = FxEase { if (it < .5f) 2f * it * it else 1f - (-2f * it + 2f).pow(2) / 2f }
    }
}

/** Small maths helpers of the mockup (`AA.lerp`, `kf`, `cubic`), pure. */
object FxMath {
    fun lerp(a: Float, b: Float, p: Float): Float = a + (b - a) * p

    fun clamp01(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v

    /**
     * Linear keyframes (the mockup's `kf`): [keys] rows are `[ms, v0, v1, ...]`, ascending in ms. Returns value number [valueIndex]
     * (0 = the first value after the time) at [ms]; before the first key / after the last it holds that key's value.
     */
    fun kf(keys: Array<FloatArray>, ms: Float, valueIndex: Int = 0): Float {
        val col = valueIndex + 1
        if (ms <= keys[0][0]) return keys[0][col]
        for (i in 1 until keys.size) {
            if (ms <= keys[i][0]) {
                val a = keys[i - 1]
                val b = keys[i]
                val p = (ms - a[0]) / (b[0] - a[0])
                return lerp(a[col], b[col], p)
            }
        }
        return keys[keys.size - 1][col]
    }

    /** Builds a keyframe table from rows written like the mockup: `keys(0f, 0f,  75f, 0f,  175f, -10f)` with [width] numbers per row. */
    fun keys(width: Int, vararg flat: Float): Array<FloatArray> =
        Array(flat.size / width) { i -> FloatArray(width) { j -> flat[i * width + j] } }
}

/**
 * A cubic Bezier through four points (the mockup's `cubic(a, c1, c2, b)`), sampled with [x] / [y] without allocating.
 */
class FxCubic(private val ax: Float, private val ay: Float, private val c1x: Float, private val c1y: Float,
              private val c2x: Float, private val c2y: Float, private val bx: Float, private val by: Float) {
    constructor(a: FxPt, c1: FxPt, c2: FxPt, b: FxPt) : this(a.x, a.y, c1.x, c1.y, c2.x, c2.y, b.x, b.y)

    fun x(p: Float): Float { val q = 1f - p; return q * q * q * ax + 3f * q * q * p * c1x + 3f * q * p * p * c2x + p * p * p * bx }
    fun y(p: Float): Float { val q = 1f - p; return q * q * q * ay + 3f * q * q * p * c1y + 3f * q * p * p * c2y + p * p * p * by }
    fun at(p: Float): FxPt = FxPt(x(p), y(p))
}
