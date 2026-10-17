package com.husarp.ankigo.ui.movefx

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * Ember (docs/design/attack-anims-v2/v2-fx.js `ember`, the approved v6): a stream of our own pixel-art fireballs from one launch
 * spot, along slightly different curves, uneven gaps and sometimes two side by side; the target flickers sepia. No spin: the
 * flame frames are mirrored when the target is on the left, so the head always leads.
 */
internal object EmberFx {
    /** Mockup ms: the first fireball leaves at [FIRST], the attacker's small recoil takes [RECOIL], the target starts shaking at [SHAKE_AT]. */
    const val FIRST = 90f
    const val RECOIL = 200f
    const val SHAKE_AT = 260f
    /** The stream's base gap (mockup `gap`) and the width of a flame frame in art pixels. */
    const val GAP = 55f
    private const val FLAME_W = 22f

    private val BIG = booleanArrayOf(true, false, true, true, false, true, false, true)
    private val BENDS = arrayOf(
        floatArrayOf(42f, 14f), floatArrayOf(-6f, 30f), floatArrayOf(70f, 46f), floatArrayOf(18f, -22f),
        floatArrayOf(56f, 4f), floatArrayOf(-14f, 40f), floatArrayOf(80f, 30f), floatArrayOf(30f, -10f),
    )
    private const val GLOW = 0xFFFFA030.toInt()
    private val TRAIL: IntArray = intArrayOf(FxPixelArt.EMBER_COLORS[1], FxPixelArt.EMBER_COLORS[2], FxPixelArt.EMBER_COLORS[3])

    /** Flame frame keys [big 1 / small 0][mirrored 1 / not 0][frame]: built once, so a frame allocates nothing. */
    private val KEYS: Array<Array<Array<String>>> = Array(2) { set ->
        Array(2) { mirror -> Array(6) { f -> (if (set == 1) "flame_big_" else "flame_small_") + f + (if (mirror == 1) "-x" else "") } }
    }

    /** How many fireballs a plan of size [k] fires: round(10 k). */
    fun count(k: Float): Int = (10f * k).roundToInt().coerceIn(1, 24)

    /** When each fireball leaves (mockup ms, ascending) and its vertical side offset in stage units (0, or -9 / +9 for a pair side by side). */
    class Stream(@JvmField val times: FloatArray, @JvmField val offsets: FloatArray)

    /**
     * The uneven stream (v6): each step waits 0.35 .. 1.9 x [gap]; with 30 % chance two fireballs leave within 12 ms of each other,
     * 9 units above and below the line. Always exactly [n] fireballs.
     */
    fun stream(random: Random, n: Int, gap: Float = GAP): Stream {
        val times = FloatArray(n)
        val offs = FloatArray(n)
        var i = 0
        var t = FIRST
        while (i < n) {
            if (i < n - 1 && random.nextFloat() < .3f) {
                times[i] = t; offs[i] = -9f; i++
                times[i] = t + random.nextFloat() * 12f; offs[i] = 9f; i++
            } else {
                times[i] = t; offs[i] = 0f; i++
            }
            t += (.35f + random.nextFloat() * 1.55f) * gap
        }
        return Stream(times, offs)
    }

    fun build(s: FxScript): FxTimes {
        val k = s.k
        val sp = s.space
        val n = count(k)
        val px = s.artPx(k)
        val dev = sp.artPixels(k)
        val mirror = if (sp.dir < 0f) 1 else 0
        val headX = if (mirror == 1) FLAME_W - FxPixelArt.FLAME_HEAD_X else FxPixelArt.FLAME_HEAD_X
        val glow = sp.len(40f * k)
        s.tween(0f, RECOIL) { p, _ -> s.attacker.set(x = -10f * sin(p * 3.14f)) }
        val a = sp.fromAttacker(55f, -28f)                       // the same launch spot for every fireball
        val st = stream(s.random, n)
        var last = 0f
        var impact = Float.MAX_VALUE
        for (i in 0 until n) {
            val t0 = st.times[i]
            val dur = 215f + s.rnd(-15f, 15f)
            val off = st.offsets[i]
            val b = sp.fromTarget(-12f + s.rnd(-18f, 18f), 4f + s.rnd(-20f, 16f) + off)
            val bend = BENDS[i % BENDS.size]
            val path = FxCubic(
                a.x, a.y,
                FxMath.lerp(a.x, b.x, .33f), FxMath.lerp(a.y, b.y, .33f) - sp.len(bend[0] * .3f + off + s.rnd(-6f, 6f)),
                FxMath.lerp(a.x, b.x, .66f), FxMath.lerp(a.y, b.y, .66f) - sp.len(bend[1] * .3f + off + s.rnd(-6f, 6f)),
                b.x, b.y,
            )
            val keys = KEYS[if (BIG[i % BIG.size]) 1 else 0][mirror]
            val f0 = s.random.nextInt(keys.size)
            val fms = s.rnd(60f, 85f)
            s.proc(t0, t0 + dur, 1) { c, p, ms ->
                val qx = path.x(p)
                val qy = path.y(p)
                c.additive = true; c.alpha = .22f                  // faint warm light around the ball
                c.tex("circle_05", GLOW, qx, qy, glow, glow)
                c.additive = false; c.alpha = 1f
                c.crisp(keys[(f0 + (ms / fms).toInt()) % keys.size], qx - headX * dev, qy - FxPixelArt.FLAME_HEAD_Y * dev, dev)
            }
            s.ticks(t0, dur) { p, _ ->                              // a few pixel embers falling off behind
                if (s.chance(.3f)) {
                    s.particle(
                        x = path.x(p) - sp.dir * sp.len(s.rnd(8f, 16f) * px), y = path.y(p) - sp.len(s.rnd(1f, 6f) * px),
                        vx = s.rnd(-40f, -10f), vy = s.rnd(-50f, -15f), life = s.rnd(220f, 420f),
                        s0 = px * (if (s.chance(.25f)) 2f else 1f), a1 = .3f, pix = TRAIL,
                    )
                }
            }
            s.at(t0 + dur) {                                        // impact: small pixel embers
                s.particle("circle_05", 0xFFFFB040.toInt(), b.x, b.y, s0 = 16f * k, s1 = 56f * k, life = 220f, a0 = .45f, add = true)
                for (j in 0 until 7) {
                    s.particle(
                        x = b.x + sp.len(s.rnd(-8f, 8f)), y = b.y + sp.len(s.rnd(-8f, 6f)),
                        vx = s.rnd(-110f, 110f) * k, vy = s.rnd(-170f, -40f) * k, ay = 280f, life = s.rnd(350f, 600f),
                        s0 = px * (if (s.chance(.35f)) 2f else 1f), a1 = .4f, pix = FxPixelArt.EMBER_COLORS,
                    )
                }
            }
            last = max(last, t0 + dur)
            impact = minOf(impact, t0 + dur)
        }
        val flicker = FxColor.Chain()
        val shakeMs = last - 200f + 300f
        s.hit(SHAKE_AT, shakeMs, filter = FxFilterAt { p ->
            if (p < .85f) flicker.reset().sepia(.55f).saturate(2.2f).brightness(1.08f + .22f * sin(p * 40f)).matrix else null
        })
        return FxTimes(last + 700f, impact, if (s.react) SHAKE_AT + shakeMs else max(impact, RECOIL))
    }
}
