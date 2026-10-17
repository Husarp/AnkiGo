package com.husarp.ankigo.ui.movefx

/**
 * Earthquake (the mockup's `earthquake`, "C"'s dirt and dust + Showdown's shake, about 60 %): the background jolts (via the stage),
 * both Pokemon bounce (the attacker squashes on the second beat), dirt clods fly up at both feet and in the middle, two dust
 * puffs. No ground cracks. The jolt is back at 0 when the shake ends at 1.075 s.
 */
internal object EarthquakeFx {
    const val IMPACT = 75f
    const val SHAKE_MS = 1075f
    const val TOTAL = 1800f

    // background jolt in units (x .75 x k), attacker [ms, lift, squash] (x .6 x k), target [ms, lift] (x .6 x k)
    private val BG = FxMath.keys(2, 0f, 0f, 75f, 0f, 175f, -10f, 275f, 0f, 375f, -5f, 475f, 0f, 575f, -5f, 675f, 0f, 775f, -2f, 875f, 0f, 975f, -2f, 1075f, 0f)
    private val AT = FxMath.keys(3, 0f, 0f, 1f, 75f, -10f, 1f, 175f, 10f, .9f, 275f, -7f, 1f, 375f, 7f, 1f, 475f, -7f, 1f, 575f, 7f, 1f, 675f, -2f, 1f, 775f, 2f, 1f, 875f, 0f, 1f)
    private val DF = FxMath.keys(2, 0f, 0f, 75f, -10f, 175f, 10f, 275f, -7f, 375f, 7f, 475f, -7f, 575f, 7f, 675f, -7f, 775f, 7f, 875f, -2f, 975f, 2f, 1075f, 0f)

    fun build(s: FxScript): FxTimes {
        val k = s.k
        s.tween(0f, SHAKE_MS) { p, _ ->
            val ms = p * SHAKE_MS
            s.jolt(FxMath.kf(BG, ms) * .75f * k)
            s.attacker.set(y = -FxMath.kf(AT, ms) * .6f * k, sx = 1f, sy = FxMath.kf(AT, ms, 1))
            s.target.set(y = -FxMath.kf(DF, ms) * .6f * k)
        }
        val feet = arrayOf(s.space.fromAttacker(0f, 62f), s.space.fromTarget(0f, 48f), s.space.between(.5f, 0f, 56f))
        s.ticks(80f, 1000f) { _, _ ->
            for (g in feet) if (s.chance(.55f)) {
                s.particle(
                    if (s.chance(.5f)) "dirt_01" else "dirt_02", 0xFF9A7448.toInt(),
                    g.x + s.space.len(s.rnd(-70f, 70f)), g.y + s.space.len(s.rnd(-6f, 6f)),
                    vx = s.rnd(-30f, 30f), vy = s.rnd(-180f, -70f) * k, ay = 480f,
                    s0 = s.rnd(16f, 34f) * k, s1 = s.rnd(10f, 22f) * k, life = 700f, rot = s.rnd(0f, 6f), vrot = s.rnd(-4f, 4f), a0 = .95f,
                )
            }
        }
        for (t in floatArrayOf(150f, 600f)) {
            s.at(t) {
                val a = s.space.fromAttacker(0f, 70f)
                val d = s.space.fromTarget(0f, 56f)
                s.particle("smoke_08", 0xFF8B7355.toInt(), a.x, a.y, s0 = 40f, s1 = 140f * k, life = 900f, a0 = .45f)
                s.particle("smoke_08", 0xFF8B7355.toInt(), d.x, d.y, s0 = 40f, s1 = 130f * k, life = 900f, a0 = .45f)
            }
        }
        return FxTimes(TOTAL, IMPACT, SHAKE_MS)
    }
}
