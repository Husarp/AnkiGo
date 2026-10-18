package com.husarp.ankigo.ui

/**
 * The golden outline and warm glow round the daily goal bar once the goal is reached (round 8, mockup 111 option C in
 * docs/design/round5.html: the outline + glow that round 5 left out). Pure maths; sizes are in dp for a bar that is
 * [GoalSparks.BAR_DP] tall and [BarView] scales them like the sparks. Static: no animation of its own.
 */
object GoalGlow {
    /** Outline colour (mockup `rgba(255,170,60)`) and glow colour (`rgba(255,150,40)`). */
    const val OUTLINE_COLOR = 0xFFFFAA3C.toInt()
    const val HALO_COLOR = 0xFFFF9628.toInt()

    /** The outline: 2 dp wide, sitting just outside the bar. */
    const val OUTLINE_WIDTH = 2f

    /** The daily goal bar's height (dp) on every screen except the battle (which keeps its 8 dp): ~1.75x, a whole dp. */
    const val THICK_BAR_DP = 14

    private const val RINGS = 6

    /** The glow is a little fainter than the outline (a blurred shadow of it in the mockup). */
    private const val HALO_PEAK = 0.7f

    /** Whether the goal is reached: [fraction] of the goal done is 100 % or more. */
    fun reached(fraction: Float): Boolean = !fraction.isNaN() && fraction >= 1f

    /** The glow is drawn exactly when the sparks are: flame [level] 1..3 (0 = no glow). */
    fun shown(level: Int): Boolean = level >= 1

    /** Outline opacity (mockup `.25 + .15 * L`), 0 without a glow. */
    fun outlineAlpha(level: Int): Float = if (level < 1) 0f else 0.25f + 0.15f * level.coerceAtMost(3)

    /** How far the blur reaches (mockup `shadowBlur = 4 + 4 * L`), dp; 0 without a glow. */
    fun blur(level: Int): Float = if (level < 1) 0f else 4f + 4f * level.coerceAtMost(3)

    /** How far outside the bar the whole effect reaches (outline + blur), dp; 0 without a glow. */
    fun extent(level: Int): Float = if (level < 1) 0f else OUTLINE_WIDTH + blur(level)

    /**
     * One stroked rounded rectangle round the bar: [outset] is where its centre line sits outside the bar's edge, [width] its
     * stroke width (both dp), [alpha] 0..1 and [color] the opaque ARGB it is drawn in.
     */
    class Ring(val outset: Float, val width: Float, val alpha: Float, val color: Int)

    /**
     * The rings to draw, outline first, then the glow from the outline outward, fading out. [maxExtent] (dp) caps how far the
     * effect may reach when the bar sits close to the edge of what draws it (default: no cap). Empty without a glow.
     */
    fun rings(level: Int, maxExtent: Float = Float.MAX_VALUE): List<Ring> {
        if (level < 1) return emptyList()
        val total = minOf(extent(level), maxExtent.coerceAtLeast(0f))
        if (total <= 0f) return emptyList()
        val ow = minOf(OUTLINE_WIDTH, total)
        val out = ArrayList<Ring>(RINGS + 1)
        out += Ring(ow / 2f, ow, outlineAlpha(level), OUTLINE_COLOR)
        val halo = total - ow
        if (halo > 0f) {
            val step = halo / RINGS
            val peak = outlineAlpha(level) * HALO_PEAK
            for (i in 0 until RINGS) {
                val t = (i + 0.5f) / RINGS
                out += Ring(ow + step * (i + 0.5f), step, peak * (1f - t) * (1f - t), HALO_COLOR)
            }
        }
        return out
    }
}
