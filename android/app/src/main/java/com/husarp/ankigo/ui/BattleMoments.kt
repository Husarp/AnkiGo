package com.husarp.ankigo.ui

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/** PLAN 706: approved battle-moments-ai motion, in the mockup's units and milliseconds. */
internal object BattleMoments {
    const val THROW = 120f
    const val CONTACT = 620f
    const val LAND = 1150f
    const val CAUGHT = 2350f
    const val FREE = 1950f
    const val LEVEL_END = 1500f
    const val EVOLVE_SWAP = 900f
    const val EVOLVE_END = 2200f
    const val SHINY_END = 1250f // entrance's 450 ms delay is scheduled by ArenaView
    const val STAT_END = 1700f
    const val HIT_END = 800f // contact's 100 ms lead is supplied by the attack player

    fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
    fun out2(p: Float) = 1f - (1f - p) * (1f - p)
    fun out3(p: Float) = 1f - (1f - p) * (1f - p) * (1f - p)
    fun bell(p: Float) = sin(p * PI.toFloat())
    fun mix(a: Float, b: Float, p: Float) = a + (b - a) * p
    fun catchEnd(success: Boolean) = if (success) CAUGHT else FREE
    fun throwProgress(t: Float): Float { val p = seg(t, THROW, CONTACT); return p * p * .35f + .65f * p }
    fun ballX(t: Float, launchX: Float, targetX: Float) = mix(launchX, targetX, throwProgress(t))
    fun ballY(t: Float, launchY: Float, targetY: Float, floorY: Float, unit: Float): Float {
        if (t < CONTACT) { val p = throwProgress(t); return mix(launchY, targetY, p) - 96f * unit * bell(p) }
        val q = seg(t, CONTACT + 120f, LAND)
        val bounce = if (q < .6f) (q / .6f) * (q / .6f) else 1f - .25f * sin((q - .6f) / .4f * PI.toFloat())
        return mix(targetY, floorY, bounce)
    }
    fun wobble(t: Float, success: Boolean): Float {
        var rotation = 0f
        for (i in 0 until if (success) 3 else 2) {
            val u = seg(t, LAND + 400f * i, LAND + 400f * i + 300f)
            if (u > 0f && u < 1f) rotation = sin(u * PI.toFloat() * 2f) * 24f * bell(u) * 1.2f
        }
        return rotation
    }
    fun ribbonAngle(index: Int, u: Float, up: Boolean): Float {
        val direction = if (up) 1f else -1f
        return index / 4f * 2f * PI.toFloat() + .5f + direction * (40f + index * 120f) / 1000f * 1.6f * 2f * PI.toFloat() + direction * u * 1.6f * 2f * PI.toFloat()
    }
    fun ribbonY(u: Float, up: Boolean): Float = if (up) mix(48f, -62f, out2(u) * .6f + .4f * u) else mix(-62f, 48f, u * u * .4f + .6f * u)
    fun ribbonFront(angle: Float) = sin(angle) > 0f
    fun absorbed(t: Float): Float { val p = seg(t, CONTACT, CONTACT + 280f); return p * p }
    fun evolutionWhite(t: Float): Float = if (t < EVOLVE_SWAP) {
        val p = seg(t, 0f, EVOLVE_SWAP); p * p * (.85f + .15f * sin(t / 60f))
    } else 1f - out2(seg(t, 1000f, 1700f))

    /** Solid plate reaches .62 of its radius: enclose BOTH visible boxes, with the approved diagonal and margin. No clipping cap. */
    fun coverRadius(halfExtent: Float) = maxOf(52f, halfExtent * sqrt(2f) * 1.22f / .62f)
}
