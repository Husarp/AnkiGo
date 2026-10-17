package com.husarp.ankigo.ui

/**
 * Small sparks rising from the daily goal bar once the goal is passed (round 5, pick 111C, sparks only). Pure maths; the
 * mockup's `drawBar` branch C (docs/design/round5.html) with the shine and the glow left out. Sizes are in dp for a bar that
 * is [BAR_DP] tall; [BarView] scales them with the bar's real height (the battle panel shrinks its bar in small windows).
 */
object GoalSparks {
    /** The bar height (dp) the sizes below are written for. */
    const val BAR_DP = 8f

    const val COLOR_ODD = 0xFFFFD447.toInt()
    const val COLOR_EVEN = 0xFFFB8F2B.toInt()

    /** Whether the sparks ticker may schedule another frame: sparks wanted, bar attached, shown and its window visible, and motion allowed. */
    fun keepTicking(level: Int, attached: Boolean, shown: Boolean, windowVisible: Boolean, reducedMotion: Boolean): Boolean =
        level > 0 && attached && shown && windowVisible && !reducedMotion

    private val COUNTS = intArrayOf(0, 3, 6, 10)

    /** One spark: [x] is a fraction of the bar width (0..1), [rise] how far above the bar's top edge (dp), [radius] in dp, [alpha] 0..1. */
    class Spark(val x: Float, val rise: Float, val radius: Float, val alpha: Float, val color: Int)

    /** Sparks shown at flame [level] (0 = none, 1..3), clamped. */
    fun count(level: Int): Int = COUNTS[level.coerceIn(0, COUNTS.size - 1)]

    /** How high the sparks can rise (dp) at [level]; 0 with no sparks. */
    fun maxRise(level: Int): Float = if (count(level) == 0) 0f else 10f + 5f * level.coerceIn(1, 3)

    /** The largest spark radius (dp), for sizing the area to redraw. */
    const val MAX_RADIUS = 1.6f

    private fun frac(v: Double): Double = v - Math.floor(v)

    /**
     * The sparks at [tMs] milliseconds (any clock, only differences matter), or the still frame for a null [tMs] (reduced motion):
     * every spark at a fixed phase, so the picture is the same every time.
     */
    fun frame(level: Int, tMs: Long?): List<Spark> {
        val n = count(level)
        if (n == 0) return emptyList()
        val lv = level.coerceIn(1, 3)
        val up = 10f + 5f * lv
        return List(n) { i ->
            val speed = 0.8 + 0.3 * frac(i * 0.53)
            val base = if (tMs == null) i.toDouble() / n else tMs / 1000.0 * speed   // double: uptime in ms gets big
            val q = frac(base + i.toDouble() / n).toFloat()
            Spark(
                x = frac(i * 0.618).toFloat(),
                rise = q * up,
                radius = 1.2f + 0.4f * frac(i * 0.3).toFloat(),
                alpha = 1f - q,
                color = if (i % 2 == 1) COLOR_ODD else COLOR_EVEN,
            )
        }
    }
}
