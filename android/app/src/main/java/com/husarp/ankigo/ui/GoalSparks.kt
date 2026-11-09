package com.husarp.ankigo.ui

/** Sparse reached-goal celebration: C's soft firefly and lobed leaf mixed with two tiny D leaves. Sizes are in dp
 * for an 8 dp bar; reduced motion uses a fixed frame. */
object GoalSparks {
    /** The bar height (dp) the sizes below are written for. */
    const val BAR_DP = 8f

    const val COLOR_ODD = 0xFFB9DFA0.toInt()
    const val COLOR_EVEN = 0xFF78B878.toInt()

    /** Whether the sparks ticker may schedule another frame: sparks wanted, bar attached, shown and its window visible, and motion allowed. */
    fun keepTicking(level: Int, attached: Boolean, shown: Boolean, windowVisible: Boolean, reducedMotion: Boolean): Boolean =
        level > 0 && attached && shown && windowVisible && !reducedMotion

    private val COUNTS = intArrayOf(0, 4, 4, 4)

    /** One spark: [x] is a fraction of the bar width (0..1), [rise] how far above the bar's top edge (dp), [radius] in dp, [alpha] 0..1. */
    class Spark(var x: Float, var rise: Float, var radius: Float, var alpha: Float, var color: Int, var shape: Int = 0, var rotation: Float = 0f)

    /** Sparks shown at flame [level] (0 = none, 1..3), clamped. */
    fun count(level: Int): Int = COUNTS[level.coerceIn(0, COUNTS.size - 1)]

    /** How high the sparks can rise (dp) at [level]; 0 with no sparks. */
    fun maxRise(level: Int): Float = if (count(level) == 0) 0f else 10f + 5f * level.coerceIn(1, 3)

    /** The largest spark radius (dp), for sizing the area to redraw. */
    const val MAX_RADIUS = 5.1f

    private fun frac(v: Double): Double = v - Math.floor(v)

    /**
     * The sparks at [tMs] milliseconds (any clock, only differences matter), or the still frame for a null [tMs] (reduced motion):
     * every spark at a fixed phase, so the picture is the same every time.
     */
    fun frame(level: Int, tMs: Long?): List<Spark> = MutableFrame().at(level, tMs)

    /** One per live bar: no particle lists or objects are allocated by a frame. */
    class MutableFrame {
        private val sparks = List(4) { Spark(0f, 0f, 0f, 0f, 0) }
        fun at(level: Int, tMs: Long?): List<Spark> {
            val n = count(level)
            if (n == 0) return emptyList()
            val lv = level.coerceIn(1, 3)
            val up = 10f + 5f * lv
            for (i in 0 until n) {
                val base = if (tMs == null) 0.35 else tMs / (6400.0 + i * 450)
                val q = frac(base + i.toDouble() / n).toFloat()
                sparks[i].apply {
                    x = (0.12 + i * 0.24 + kotlin.math.sin(q * Math.PI * 2) * .025).toFloat()
                    rise = q * up
                    radius = if (i == 3) 1.3f else 2.4f
                    shape = when (i) { 3 -> 0; 2 -> 6; else -> 5 }
                    rotation = i * 47f + q * 80f
                    alpha = (kotlin.math.sin(q * Math.PI) * .65).toFloat()
                    color = if (i == 3) COLOR_ODD else COLOR_EVEN
                }
            }
            return sparks
        }
    }
}
