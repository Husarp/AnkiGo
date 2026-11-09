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

    /** Target average sparks at flame [level] (0 = none, 1..3), clamped. */
    fun count(level: Int): Int = COUNTS[level.coerceIn(0, COUNTS.size - 1)]

    /** How high the sparks can rise (dp) at [level]; 0 with no sparks. */
    fun maxRise(level: Int): Float = if (count(level) == 0) 0f else 10f + 5f * level.coerceIn(1, 3)

    /** The largest spark radius (dp), for sizing the area to redraw. */
    const val MAX_RADIUS = 5.1f

    // One birth per jittered interval: about four alive, without simultaneous births or dense bursts.
    internal const val SPAWN_INTERVAL_MS = 1770L
    private const val STILL_MS = 12_345L

    /** Stable random attributes per birth, independent of frame rate and wall-clock uptime. */
    private fun random(index: Long, salt: Long): Float {
        var bits = index * -7046029254386353131L + salt
        bits = (bits xor (bits ushr 30)) * -4658895280553007687L
        bits = (bits xor (bits ushr 27)) * -7723592293110705685L
        return ((bits xor (bits ushr 31)) ushr 40).toFloat() / 16777216f
    }

    internal fun spawnTime(index: Long): Long =
        index * SPAWN_INTERVAL_MS + (SPAWN_INTERVAL_MS * (.15f + .7f * random(index, 1))).toLong()

    internal fun lifetime(index: Long): Long = 6400L + (random(index, 2) * 1351).toLong()

    /** The null clock is a repeatable, visible reduced-motion frame. Only live births are evaluated. */
    fun frame(level: Int, tMs: Long?): List<Spark> = MutableFrame().at(level, tMs)

    /** One per live bar: reuse particle storage across frames. */
    class MutableFrame {
        private val pool = List(6) { Spark(0f, 0f, 0f, 0f, 0) }
        private val sparks = ArrayList<Spark>(6)

        fun at(level: Int, tMs: Long?): List<Spark> {
            sparks.clear()
            if (count(level) == 0) return emptyList()
            val t = tMs ?: STILL_MS
            val bucket = Math.floorDiv(t, SPAWN_INTERVAL_MS)
            val up = maxRise(level)
            for (index in bucket - 5..bucket) {
                val age = t - spawnTime(index)
                val duration = lifetime(index)
                if (age < 0 || age >= duration) continue
                val q = age.toFloat() / duration
                val kind = random(index, 3)
                val shape = when { kind < .25f -> 0; kind < .5f -> 6; else -> 5 }
                val baseColor = if (random(index, 4) < .5f) COLOR_ODD else COLOR_EVEN
                val shade = if (random(index, 5) < .3f) .92f else 1f
                fun channel(shift: Int) = (((baseColor ushr shift) and 255) * shade).toInt()
                val spark = pool[sparks.size]
                spark.x = random(index, 6)
                spark.rise = q * up
                spark.radius = (if (shape == 0) 1.3f else 2.4f) * (.9f + .2f * random(index, 7))
                spark.shape = shape
                spark.rotation = random(index, 8) * 360f + q * 80f
                spark.alpha = (kotlin.math.sin(q * Math.PI) * .65).toFloat()
                spark.color = (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
                sparks.add(spark)
            }
            return sparks
        }
    }
}
