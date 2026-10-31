package com.husarp.ankigo.ui.movefx

import kotlin.math.sin

/** Frozen R21 implementations for honest before/after renderer comparisons. */
internal object R23ZbBeforeFx {
    private val white = 0xFFFFFFFF.toInt()

    fun multi(s: FxScript): FxTimes {
        s.lunge(100f)
        repeat(4) { i ->
            val t = 380f + i * 170f
            s.at(t) {
                val at = s.space.fromTarget(if (i % 2 == 0) -4f else 4f, (i - 1) * 3f)
                s.particle("slash_03", white, at.x, at.y, s0 = 46f, s1 = 58f, life = 260f, rot = -.6f + i * .5f)
                s.burst("spark_01", s.plan.palette.glow, at, 5, 110f, 190f, 24f, 6f, true)
            }
            s.hit(t, 170f, push = 4f)
        }
        return FxTimes(1300f, 380f, 1080f)
    }

    fun recoil(s: FxScript): FxTimes {
        s.lunge(70f)
        s.at(380f) { FxKits.normal(s, TackleFx.hitSpot(s)) }
        s.hit(380f, 300f, push = 12f)
        val red = FxColor.identity().also { it[4] = 80f; it[6] = .6f; it[12] = .6f }
        s.at(520f) { s.burst("spark_01", 0xFFFF6A5A.toInt(), s.space.fromAttacker(6f, -8f), 6, 110f, 340f, 20f, 6f) }
        // Added after the lunge so the return no longer overwrites the recoil shake.
        s.tween(520f, 520f) { p, _ -> s.attacker.set(x = sin(p * 30f) * 5f * (1f - p), filter = if (p < 1f) red else null) }
        return FxTimes(1400f, 380f, 1040f)
    }
}
