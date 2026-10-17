package com.husarp.ankigo.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The XP animation (pick 38A, mockup `round5c.html` `plan38` / `xpFrame`): 16 soft blue sparks fly from the defeated
 * Pokémon's box into our Pokémon's box, each on its own random curve; our Pokémon flashes white at every landing; a
 * "+N XP" number over the defeated one's spot counts up while they fly and fades once the last one has landed.
 * Pure maths (no android.*); [XpAbsorbFx] draws what [forEach] hands out. Times are milliseconds since the event started
 * and are already multiplied by the pace factor the [Plan] was built with (1 normal, 0.5 when answers are waiting).
 * The random numbers are drawn in the mockup's order, so [MOCK_SEED] gives the picture the mockup shows.
 */
object XpParticles {
    const val COUNT = 16
    /** The mockup's seed for variant A. */
    const val MOCK_SEED = 381L

    const val LAUNCH_FROM = 520f
    const val LAUNCH_SPAN = 1000f
    const val FLY_MIN = 750f
    const val FLY_MAX = 1100f
    /** The small white star where a spark lands. */
    const val LAND_MS = 220f
    /** One landing's white flash on our Pokémon dies away in this long. */
    const val FLASH_MS = 130f
    /** The number pops in between these two moments. */
    const val POP_FROM = 380f
    const val POP_TO = 640f
    const val POP_MS = 260f
    /** The count reaches the total this long after the last launch. */
    const val COUNT_PAD = 200f
    /** The number stays this long after the last landing, then fades for [FADE_TO] - [FADE_FROM]. */
    const val FADE_FROM = 180f
    const val FADE_TO = 650f
    /** The count-up tilt of the number (the mockup's -0.07 rad). */
    const val TILT_DEG = -4f

    /** Spark radius in units at size factor 1 (a unit is about a dp), and the trail behind it. */
    const val SPARK_UNITS = 6f
    const val TRAIL = 3

    /** [SpriteView.flash] is 0..1 and a bit weaker than the mockup's filter: this share of the summed landings. */
    const val FLASH_GAIN = 0.75f

    const val HEAD = 0
    const val TRAIL_MARK = 1
    const val LAND = 2

    /** One spark. Offsets are shares of the boxes' size; control points are polar (angle in radians, radius in units). */
    class Spark(
        val t0: Float, val dur: Float, val ez: Float, val size: Float,
        val sx: Float, val sy: Float, val ex: Float, val ey: Float,
        val a1: Float, val r1: Float, val a2: Float, val r2: Float,
    ) {
        val land: Float get() = t0 + dur
    }

    /** A sprite box in the arena's pixels. */
    class Box(val left: Float, val top: Float, val w: Float, val h: Float) {
        val cx: Float get() = left + w / 2f
    }

    /** The whole schedule of one animation, built once (seeded), so every frame and the still frame look the same. */
    class Plan(val sparks: List<Spark>, val factor: Float) {
        val firstLaunch = sparks.minOf { it.t0 }
        val lastLaunch = sparks.maxOf { it.t0 }
        val firstLanding = sparks.minOf { it.land }
        val lastLanding = sparks.maxOf { it.land }
        val popFrom = POP_FROM * factor
        val popTo = POP_TO * factor
        val countTo = lastLaunch + COUNT_PAD * factor
        val fadeFrom = lastLanding + FADE_FROM * factor
        val fadeTo = lastLanding + FADE_TO * factor
        /** Nothing is left to draw from this moment on. */
        val end = max(fadeTo, lastLanding + LAND_MS * factor)
        val landMs = LAND_MS * factor
        val flashMs = FLASH_MS * factor
        val popMs = POP_MS * factor
    }

    /** Receives the marks of one frame (an interface, so a frame allocates nothing): [kind] is [HEAD], [TRAIL_MARK] or [LAND]. */
    fun interface Sink { fun mark(kind: Int, x: Float, y: Float, r: Float, alpha: Float) }

    /** One mark, for tests and tools. */
    data class Mark(val kind: Int, val x: Float, val y: Float, val r: Float, val alpha: Float)

    /** The mockup's xorshift32. */
    private class Rng(seed: Long) {
        private var s = seed.toInt().let { if (it == 0) 1 else it }
        fun next(): Float {
            s = s xor (s shl 13)
            s = s xor (s ushr 17)
            s = s xor (s shl 5)
            return ((s.toLong() and 0xFFFFFFFFL) % 100000L) / 100000f
        }
    }

    /** The plan for [seed] at pace [factor] (every time in it is multiplied by it). */
    fun plan(seed: Long, factor: Float = 1f): Plan {
        val r = Rng(seed)
        val out = ArrayList<Spark>(COUNT)
        for (i in 0 until COUNT) {
            val t0 = LAUNCH_FROM + (i + r.next() * 0.8f) / COUNT * LAUNCH_SPAN
            val dur = FLY_MIN + r.next() * (FLY_MAX - FLY_MIN)
            val sx = (r.next() - 0.5f) * 0.5f
            val sy = (r.next() - 0.5f) * 0.4f
            val ex = (r.next() - 0.5f) * 0.35f
            val ey = (r.next() - 0.5f) * 0.3f
            val a1 = r.next() * 2f * PI.toFloat()
            val r1 = 26f + r.next() * 70f
            val a2 = r.next() * 2f * PI.toFloat()
            val r2 = 26f + r.next() * 80f
            r.next(); r.next(); r.next()    // the mockup's wobble phase / frequency / side (unused by variant A)
            val ez = 0.75f + r.next() * 0.7f
            val sz = 0.75f + r.next() * 0.5f
            out += Spark(t0 * factor, dur * factor, ez, sz, sx, sy, ex, ey, a1, r1, a2, r2)
        }
        return Plan(out, factor)
    }

    /** Mock units to pixels: about a dp, a bit smaller in a narrow window and bigger in a wide one. */
    fun unit(widthPx: Float, density: Float): Float {
        val dp = if (density > 0f) widthPx / density else 360f
        return density * (dp / 360f).coerceIn(0.7f, 1.25f)
    }

    private fun clamp01(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v
    private fun seg(t: Float, a: Float, b: Float) = if (b <= a) (if (t >= b) 1f else 0f) else clamp01((t - a) / (b - a))
    private fun easeOut(q: Float) = 1f - (1f - q) * (1f - q) * (1f - q)
    private fun easeInOut(q: Float) = if (q < 0.5f) 4f * q * q * q else 1f - (-2f * q + 2f).pow(3) / 2f

    private fun bez(k: Float, a: Float, b: Float, c: Float, d: Float): Float {
        val m = 1f - k
        return m * m * m * a + 3f * m * m * k * b + 3f * m * k * k * c + k * k * k * d
    }

    /** Hands [emit] everything on screen at [tMs]: sparks (head + trail) in flight and the landing stars. [from] is the defeated one's box, [to] ours. */
    fun forEach(plan: Plan, tMs: Float, from: Box, to: Box, unit: Float, emit: Sink) {
        for (s in plan.sparks) {
            val lt = tMs - s.t0
            if (lt < 0f || lt > s.dur + plan.landMs) continue
            val p0x = from.cx + s.sx * from.w
            val p0y = from.top + from.h * 0.55f + s.sy * from.h
            val p3x = to.cx + s.ex * to.w
            val p3y = to.top + to.h * 0.55f + s.ey * to.h
            if (lt > s.dur) {
                val k = (lt - s.dur) / plan.landMs
                emit.mark(LAND, p3x, p3y, (2f + 21f * k * (1f - k)) * unit, 1f - k)
                continue
            }
            val p1x = p0x + cos(s.a1) * s.r1 * unit
            val p1y = p0y + sin(s.a1) * s.r1 * 0.8f * unit - 10f * unit
            val p2x = p3x + cos(s.a2) * s.r2 * unit
            val p2y = p3y + sin(s.a2) * s.r2 * 0.8f * unit - 8f * unit
            val u = lt / s.dur
            val k = easeInOut(u).pow(s.ez)
            for (j in TRAIL downTo 1) {
                val kk = max(0f, k - j * 0.035f)
                emit.mark(TRAIL_MARK, bez(kk, p0x, p1x, p2x, p3x), bez(kk, p0y, p1y, p2y, p3y), SPARK_UNITS * s.size * (1f - j * 0.18f) * unit, 0.5f - j * 0.12f)
            }
            emit.mark(HEAD, bez(k, p0x, p1x, p2x, p3x), bez(k, p0y, p1y, p2y, p3y), SPARK_UNITS * s.size * unit, min(1f, u * 8f) * 0.95f)
        }
    }

    /** [forEach] as a list (tests, tools). */
    fun at(plan: Plan, tMs: Float, from: Box, to: Box, unit: Float): List<Mark> {
        val out = ArrayList<Mark>()
        forEach(plan, tMs, from, to, unit) { kind, x, y, r, a -> out += Mark(kind, x, y, r, a) }
        return out
    }

    /** Where spark [s] starts and ends (before the unit-sized bend of its curve), for tests. */
    fun start(s: Spark, from: Box): Pair<Float, Float> = (from.cx + s.sx * from.w) to (from.top + from.h * 0.55f + s.sy * from.h)
    fun landing(s: Spark, to: Box): Pair<Float, Float> = (to.cx + s.ex * to.w) to (to.top + to.h * 0.55f + s.ey * to.h)

    /** Our Pokémon's white flash (0..1, for [com.husarp.ankigo.gfx.SpriteView.flash]): every landing adds a burst that dies away in 130 ms. Exactly 0 once all are over. */
    fun flash(plan: Plan, tMs: Float): Float {
        var sum = 0f
        for (s in plan.sparks) {
            val dt = tMs - s.land
            if (dt >= 0f && dt < plan.flashMs) sum += 1f - dt / plan.flashMs
        }
        return min(1f, sum * FLASH_GAIN)
    }

    /** The number shown at [tMs]: counts 0 to [total] from the first launch to just after the last one (ease-out). */
    fun count(plan: Plan, tMs: Float, total: Int): Int = (total * easeOut(seg(tMs, plan.firstLaunch, plan.countTo))).roundToInt()

    /** 0 before it pops in, 1 while it stays, down to 0 as it fades after the last landing. */
    fun numberAlpha(plan: Plan, tMs: Float): Float = min(seg(tMs, plan.popFrom, plan.popTo), 1f - seg(tMs, plan.fadeFrom, plan.fadeTo))

    /** The small pop as it appears: 1, up to 1.16, back to 1. */
    fun numberScale(plan: Plan, tMs: Float): Float = 1f + 0.16f * sin(min(1f, max(0f, tMs - plan.popFrom) / plan.popMs) * PI.toFloat())

    /** The number's anchor: over the defeated one's box (its centre, 18 % down), kept inside an arena [arenaW] wide with [halfW] x [halfH] of text and [pad] to spare. */
    fun numberSpot(from: Box, arenaW: Float, halfW: Float, halfH: Float, pad: Float): Pair<Float, Float> {
        val lo = halfW + pad
        val hi = arenaW - halfW - pad
        val x = if (hi < lo) arenaW / 2f else from.cx.coerceIn(lo, hi)
        return x to max(halfH + pad, from.top + from.h * 0.18f)
    }
}
