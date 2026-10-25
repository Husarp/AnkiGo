package com.husarp.ankigo.ui

import kotlin.math.roundToInt

/** PLAN 163 / R19: three HP palettes; missing or older preferences select the smooth blend. */
object HpColors {
    enum class Mode(val label: String) { A("Three steps"), B("Smooth blend"), C("Type colour") }
    fun mode(saved: String?): Mode = Mode.entries.firstOrNull { it.name == saved } ?: Mode.B

    fun color(frac: Float, mode: Mode = Mode.B, dark: Boolean = false, type: Int? = null): Int {
        val f = if (frac.isFinite()) frac.coerceIn(0f, 1f) else 0f
        val green = if (dark) 0xFF86D98D.toInt() else 0xFF2E9E3B.toInt()
        val yellow = if (dark) 0xFFF5C542.toInt() else 0xFFE8B100.toInt()
        val red = if (dark) 0xFFF4647F.toInt() else 0xFFD20F39.toInt()
        if (mode == Mode.C && type != null) return if (f <= 0.2f) red else type
        if (mode == Mode.A) return if (f > 0.5f) green else if (f > 0.2f) yellow else red
        return if (f <= 0.5f) blend(red, yellow, f * 2) else blend(yellow, green, (f - 0.5f) * 2)
    }

    /** Perceptual OKLab interpolation, split at gold so red/green never make a muddy midpoint. */
    private fun blend(a: Int, b: Int, t: Float): Int {
        if (t <= 0f) return a
        if (t >= 1f) return b
        fun lab(c: Int): DoubleArray {
            fun channel(shift: Int): Double {
                val v = ((c shr shift) and 255) / 255.0
                return if (v <= .04045) v / 12.92 else Math.pow((v + .055) / 1.055, 2.4)
            }
            val r = channel(16); val g = channel(8); val b0 = channel(0)
            val l = Math.cbrt(.4122214708*r + .5363325363*g + .0514459929*b0)
            val m = Math.cbrt(.2119034982*r + .6806995451*g + .1073969566*b0)
            val z = Math.cbrt(.0883024619*r + .2817188376*g + .6299787005*b0)
            return doubleArrayOf(.2104542553*l + .7936177850*m - .0040720468*z,
                1.9779984951*l - 2.4285922050*m + .4505937099*z,
                .0259040371*l + .7827717662*m - .8086757660*z)
        }
        val x = lab(a); val y = lab(b)
        val v = DoubleArray(3) { x[it] + (y[it] - x[it]) * t }
        fun cube(n: Double) = n*n*n
        val l = cube(v[0] + .3963377774*v[1] + .2158037573*v[2])
        val m = cube(v[0] - .1055613458*v[1] - .0638541728*v[2])
        val z = cube(v[0] - .0894841775*v[1] - 1.2914855480*v[2])
        fun channel(n: Double): Int {
            val c = if (n <= .0031308) 12.92*n else 1.055*Math.pow(n, 1/2.4) - .055
            return (c*255).roundToInt().coerceIn(0,255)
        }
        return (0xFF shl 24) or (channel(4.0767416621*l - 3.3077115913*m + .2309699292*z) shl 16) or
            (channel(-1.2684380046*l + 2.6097574011*m - .3413193965*z) shl 8) or
            channel(-.0041960863*l - .7034186147*m + 1.7076147010*z)
    }
}
