package com.husarp.ankigo.ui.movefx

import kotlin.math.abs
import kotlin.math.sin

/**
 * Thunderbolt (the mockup's `thunderbolt`, "C"): the arena dims, a jagged bolt falls from the top edge of the layer onto the target
 * (re-jagged every 45 ms, with 1 branch, 2 when k > 1.05), a flash and a star where it lands, sparks, and the target flickers bright
 * yellow. The bolt comes from y = 0 of the layer above the target, in both directions.
 */
internal object ThunderboltFx {
    const val IMPACT = 220f
    const val BOLT_START = 120f
    const val BOLT_END = 900f
    const val HIT_MS = 700f
    const val TOTAL = 1300f
    private const val RE_JAG_MS = 45f

    private const val YELLOW = 0xFFFFE14D.toInt()
    private const val PALE = 0xFFFFF7B0.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    fun build(s: FxScript): FxTimes {
        val k = s.k
        val u = s.u
        val b = s.space.fromTarget(0f, 0f)
        val branches = if (k > 1.05f) 2 else 1

        s.tween(0f, 120f) { p, _ -> s.dim = .5f * p }
        s.tween(900f, 300f) { p, _ -> s.dim = .5f * (1f - p) }
        s.at(1200f) { s.dim = 0f }

        // the bolt: a new shape every 45 ms on the fixed step (so a slow phone still shows the same flicker)
        var bolt = FloatArray(0)
        val branch = ArrayList<FloatArray>(2)
        s.ticks(BOLT_START, BOLT_END - BOLT_START, RE_JAG_MS) { _, _ ->
            bolt = s.jag(b.x + s.rnd(-8f, 8f) * u, 0f, b.x, b.y - 4f * u, 9, 22f * k * u)
            branch.clear()
            for (i in 0 until branches) {
                val from = 3 + i
                branch += s.jag(bolt[from * 2], bolt[from * 2 + 1], b.x + s.rnd(-90f, 90f) * k * u, b.y + s.rnd(10f, 70f) * u, 5, 14f * u)
            }
        }
        s.proc(BOLT_START, BOLT_END) { c, p, _ ->
            if (bolt.isEmpty()) return@proc
            val a = if (p < .75f) 1f else (1f - p) * 4f
            c.stroke(bolt, 14f * k * u, YELLOW, .35f * a, glow = 24f * u)
            c.stroke(bolt, 6f * k * u, PALE, .9f * a, glow = 10f * u)
            c.stroke(bolt, 2.2f * k * u, WHITE, a)
            for (br in branch) {
                c.stroke(br, 7f * k * u, YELLOW, .35f * a, glow = 12f * u)
                c.stroke(br, 2f * k * u, WHITE, .9f * a)
            }
        }

        s.at(IMPACT) {
            s.flashOnce(.55f, 70f)
            s.particle("star_06", PALE, b.x, b.y, s0 = 40f, s1 = 170f * k, life = 360f, add = true)
        }
        s.ticks(IMPACT, 560f) { _, _ ->
            if (s.chance(.5f)) s.particle("spark_05", 0xFFFFE84A.toInt(), b.x + s.space.len(s.rnd(-40f, 40f) * k), b.y + s.space.len(s.rnd(-30f, 40f)),
                s0 = s.rnd(30f, 60f) * k, s1 = 20f, life = 140f, rot = s.rnd(-.5f, .5f), add = true)
            if (s.chance(.35f)) s.particle("spark_01", 0xFFFFF2A0.toInt(), b.x + s.space.len(s.rnd(-40f, 40f) * k), b.y + s.space.len(s.rnd(-30f, 40f)),
                s0 = s.rnd(40f, 70f) * k, s1 = 30f, life = 160f, rot = s.rnd(0f, 6f), add = true)
        }

        val f = FxColor.Chain()
        s.hit(IMPACT, HIT_MS, filter = FxFilterAt { p -> f.reset().brightness(1.2f + 1.2f * abs(sin(p * 30f))).sepia(.5f).saturate(3f).matrix })
        return FxTimes(TOTAL, IMPACT, if (s.react) IMPACT + HIT_MS else BOLT_END)
    }
}
