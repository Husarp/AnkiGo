package com.husarp.ankigo.ui.movefx

import kotlin.math.PI
import kotlin.math.sin

/**
 * Earthquake (the mockup's `earthquake`, "C"'s dirt and dust + Showdown's shake): the background jolts (via the stage), both Pokemon
 * bounce and shake sideways (the attacker squashes on the second beat), dirt clods and rock chips fly up at both feet and in the
 * middle, three waves of dust. No ground cracks. Every ground move without its own look plays it (Magnitude, Bulldoze, Dig ...).
 * PLAN 157 (2026-10-21, "too weak to notice"): about 2.5 x the R7-08 jolt (up AND down now), 1.6 x the bounce, a new sideways
 * shake, about 3 x the dirt (bigger, thrown higher, but never above the arena), rock chips, a third dust wave, and 0.4 s longer.
 * The arena's background keeps the jolt within its blurred margin ([com.husarp.ankigo.ui.BackdropDraw.JOLT_MAX]), so no edge
 * shows; reduced motion plays no move looks at all (the event player skips them). Everything is back at 0 when the shake ends.
 */
internal object EarthquakeFx {
    const val IMPACT = 75f
    const val SHAKE_MS = 1500f
    const val TOTAL = 2200f

    /** Sideways shake of both Pokemon: its size at the start (units x k) and its rate (Hz); it fades out to 0 at [SHAKE_MS]. */
    const val SIDE = 7f
    private const val SIDE_HZ = 14f

    // background jolt in units (x k), attacker [ms, lift, squash] (x k), target [ms, lift] (x k)
    private val BG = FxMath.keys(
        2, 0f, 0f, 75f, 0f, 140f, -24f, 205f, 8f, 270f, -22f, 340f, 7f, 410f, -19f, 480f, 6f, 550f, -16f, 625f, 5f, 700f, -13f,
        780f, 4f, 860f, -10f, 940f, 3f, 1020f, -7f, 1110f, 2f, 1200f, -5f, 1300f, 1f, 1400f, -2f, 1500f, 0f,
    )
    private val AT = FxMath.keys(
        3, 0f, 0f, 1f, 75f, -10f, 1f, 175f, 10f, .85f, 275f, -8f, 1f, 375f, 8f, 1f, 475f, -7f, 1f, 575f, 7f, 1f, 675f, -4f, 1f,
        775f, 4f, 1f, 875f, -2f, 1f, 975f, 2f, 1f, 1100f, 0f, 1f,
    )
    private val DF = FxMath.keys(
        2, 0f, 0f, 75f, -10f, 175f, 10f, 275f, -8f, 375f, 8f, 475f, -7f, 575f, 7f, 675f, -7f, 775f, 7f, 875f, -4f, 975f, 4f,
        1075f, -3f, 1175f, 3f, 1275f, -1.5f, 1375f, 1.5f, 1500f, 0f,
    )

    private const val DIRT = 0xFF9A7448.toInt()
    private const val ROCK = 0xFF7A7068.toInt()
    private const val DUST = 0xFF8B7355.toInt()

    fun build(s: FxScript): FxTimes {
        val k = s.k
        s.tween(0f, SHAKE_MS) { p, _ ->
            val ms = p * SHAKE_MS
            val side = sin(ms / 1000f * SIDE_HZ * 2f * PI.toFloat()) * SIDE * k * (1f - p)
            s.jolt(FxMath.kf(BG, ms) * k)
            s.attacker.set(x = side, y = -FxMath.kf(AT, ms) * k, sx = 1f, sy = FxMath.kf(AT, ms, 1))
            s.target.set(x = -side, y = -FxMath.kf(DF, ms) * k)
        }
        val feet = arrayOf(s.space.fromAttacker(0f, 62f), s.space.fromTarget(0f, 48f), s.space.between(.5f, 0f, 56f))
        s.ticks(60f, 1300f, 50f) { _, _ ->
            for (g in feet) if (s.chance(.8f)) {
                val rock = s.chance(.3f)   // a small grey chip that keeps its size
                val s0 = (if (rock) s.rnd(14f, 24f) else s.rnd(24f, 46f)) * k
                s.particle(
                    if (s.chance(.5f)) "dirt_01" else "dirt_02", if (rock) ROCK else DIRT,
                    g.x + s.space.len(s.rnd(-90f, 90f)), g.y + s.space.len(s.rnd(-6f, 6f)),
                    vx = s.rnd(-45f, 45f), vy = s.rnd(-240f, -100f) * k, ay = 520f,
                    s0 = s0, s1 = if (rock) s0 * .85f else s.rnd(12f, 24f) * k,
                    life = 800f, rot = s.rnd(0f, 6f), vrot = s.rnd(-6f, 6f), a0 = 1f,
                )
            }
        }
        for (t in floatArrayOf(150f, 550f, 950f)) {
            s.at(t) {
                val a = s.space.fromAttacker(0f, 70f)
                val d = s.space.fromTarget(0f, 56f)
                val m = s.space.between(.5f, 0f, 60f)
                s.particle("smoke_08", DUST, a.x, a.y, s0 = 50f, s1 = 190f * k, life = 1000f, a0 = .6f)
                s.particle("smoke_08", DUST, d.x, d.y, s0 = 50f, s1 = 170f * k, life = 1000f, a0 = .6f)
                s.particle("smoke_08", DUST, m.x, m.y, s0 = 40f, s1 = 150f * k, life = 900f, a0 = .5f)
            }
        }
        return FxTimes(TOTAL, IMPACT, SHAKE_MS)
    }
}
