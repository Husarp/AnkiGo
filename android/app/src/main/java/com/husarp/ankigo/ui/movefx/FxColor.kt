package com.husarp.ankigo.ui.movefx

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The CSS filters of the approved mockup as colour matrices (pure maths, no android.*): `brightness()`, `saturate()`, `sepia()`
 * and `hue-rotate()`, with the formulas of the W3C "Filter Effects" spec, and their composition in CSS order.
 *
 * A matrix is a [FloatArray] of 20 in `android.graphics.ColorMatrix` order (rows R, G, B, A; each row = 4 factors and an offset),
 * which is what [MoveFxStage.pose] takes. All these filters are linear without offsets, so the 0..255 scale does not matter.
 * Limit: CSS clamps to 0..1 after every function of a chain; one composed matrix clamps only once at the end. The look is the
 * same for the values the mockup uses.
 *
 * Use [Chain] in a look (no allocation per frame):
 * `val f = FxColor.Chain(); ... filter = { p -> f.reset().sepia(.55f).saturate(2.2f).brightness(1.1f).matrix }`.
 */
object FxColor {
    const val SIZE = 20

    fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** `brightness(b)`: every colour channel times [b] (1 = unchanged). */
    fun brightness(b: Float): FloatArray = brightnessInto(b, FloatArray(SIZE))

    /** [brightness] written into [out] (20 floats, returned): no allocation. */
    fun brightnessInto(b: Float, out: FloatArray): FloatArray {
        out[0] = b; out[1] = 0f; out[2] = 0f; out[3] = 0f; out[4] = 0f
        out[5] = 0f; out[6] = b; out[7] = 0f; out[8] = 0f; out[9] = 0f
        out[10] = 0f; out[11] = 0f; out[12] = b; out[13] = 0f; out[14] = 0f
        out[15] = 0f; out[16] = 0f; out[17] = 0f; out[18] = 1f; out[19] = 0f
        return out
    }

    /** `saturate(s)`: 0 = grey, 1 = unchanged, above 1 = more colour. */
    fun saturate(s: Float): FloatArray = saturateInto(s, FloatArray(SIZE))

    /** [saturate] written into [out] (20 floats, returned): no allocation. */
    fun saturateInto(s: Float, out: FloatArray): FloatArray {
        out[0] = 0.213f + 0.787f * s; out[1] = 0.715f - 0.715f * s; out[2] = 0.072f - 0.072f * s; out[3] = 0f; out[4] = 0f
        out[5] = 0.213f - 0.213f * s; out[6] = 0.715f + 0.285f * s; out[7] = 0.072f - 0.072f * s; out[8] = 0f; out[9] = 0f
        out[10] = 0.213f - 0.213f * s; out[11] = 0.715f - 0.715f * s; out[12] = 0.072f + 0.928f * s; out[13] = 0f; out[14] = 0f
        out[15] = 0f; out[16] = 0f; out[17] = 0f; out[18] = 1f; out[19] = 0f
        return out
    }

    /** `sepia(a)`: [a] clamped to 0..1 (0 = unchanged, 1 = full sepia). */
    fun sepia(a: Float): FloatArray = sepiaInto(a, FloatArray(SIZE))

    /** [sepia] written into [out] (20 floats, returned): no allocation. */
    fun sepiaInto(a: Float, out: FloatArray): FloatArray {
        val c = 1f - a.coerceIn(0f, 1f)
        out[0] = 0.393f + 0.607f * c; out[1] = 0.769f - 0.769f * c; out[2] = 0.189f - 0.189f * c; out[3] = 0f; out[4] = 0f
        out[5] = 0.349f - 0.349f * c; out[6] = 0.686f + 0.314f * c; out[7] = 0.168f - 0.168f * c; out[8] = 0f; out[9] = 0f
        out[10] = 0.272f - 0.272f * c; out[11] = 0.534f - 0.534f * c; out[12] = 0.131f + 0.869f * c; out[13] = 0f; out[14] = 0f
        out[15] = 0f; out[16] = 0f; out[17] = 0f; out[18] = 1f; out[19] = 0f
        return out
    }

    /** `hue-rotate(deg)`: turns the hue by [deg] degrees. */
    fun hueRotate(deg: Float): FloatArray = hueRotateInto(deg, FloatArray(SIZE))

    /** [hueRotate] written into [out] (20 floats, returned): no allocation. */
    fun hueRotateInto(deg: Float, out: FloatArray): FloatArray {
        val r = deg.toDouble() * PI / 180.0
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        out[0] = 0.213f + c * 0.787f - s * 0.213f; out[1] = 0.715f - c * 0.715f - s * 0.715f; out[2] = 0.072f - c * 0.072f + s * 0.928f; out[3] = 0f; out[4] = 0f
        out[5] = 0.213f - c * 0.213f + s * 0.143f; out[6] = 0.715f + c * 0.285f + s * 0.140f; out[7] = 0.072f - c * 0.072f - s * 0.283f; out[8] = 0f; out[9] = 0f
        out[10] = 0.213f - c * 0.213f - s * 0.787f; out[11] = 0.715f - c * 0.715f + s * 0.715f; out[12] = 0.072f + c * 0.928f + s * 0.072f; out[13] = 0f; out[14] = 0f
        out[15] = 0f; out[16] = 0f; out[17] = 0f; out[18] = 1f; out[19] = 0f
        return out
    }

    /** Writes into [out] the matrix that applies [first], then [second] (the product second x first). [out] must not be either. */
    fun thenInto(first: FloatArray, second: FloatArray, out: FloatArray) {
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                var v = 0f
                for (m in 0 until 4) v += second[row * 5 + m] * first[m * 5 + col]
                if (col == 4) v += second[row * 5 + 4]
                out[row * 5 + col] = v
            }
        }
    }

    /** The matrix that applies [first], then [second]. */
    fun then(first: FloatArray, second: FloatArray): FloatArray = FloatArray(SIZE).also { thenInto(first, second, it) }

    /** The matrix of a CSS filter list: [filters] in the order they are written (the first one is applied first). */
    fun chain(vararg filters: FloatArray): FloatArray {
        var acc = identity()
        for (f in filters) acc = then(acc, f)
        return acc
    }

    /**
     * A reusable filter list in CSS order: `Chain().sepia(.5f).saturate(3f)` = `filter: sepia(.5) saturate(3)`. [matrix] is the
     * running result and stays the same array (the stage copies it at once); [reset] starts over. Not thread safe.
     */
    class Chain {
        val matrix = identity()
        private val tmp = FloatArray(SIZE)
        private val op = FloatArray(SIZE)   // the filter being added: written in place, nothing is allocated per operation

        fun reset(): Chain { System.arraycopy(IDENTITY, 0, matrix, 0, SIZE); return this }
        fun brightness(b: Float) = add(brightnessInto(b, op))
        fun saturate(s: Float) = add(saturateInto(s, op))
        fun sepia(a: Float) = add(sepiaInto(a, op))
        fun hueRotate(deg: Float) = add(hueRotateInto(deg, op))

        private fun add(f: FloatArray): Chain {
            thenInto(matrix, f, tmp)
            System.arraycopy(tmp, 0, matrix, 0, SIZE)
            return this
        }

        private companion object { val IDENTITY = identity() }
    }
}
