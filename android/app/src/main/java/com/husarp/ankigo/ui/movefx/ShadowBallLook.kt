package com.husarp.ankigo.ui.movefx

/**
 * Shadow Ball (the mockup's `shadowball`, Showdown's projectile + "C"'s dark burst): five wisps gather at the attacker, the ball
 * grows (decelerating), then flies to the target (accelerating); on the hit [FxKits.darkBurst] and the target's dark flinch.
 * [sprites] picks the art: the purplewisp / shadowball PNGs untinted (Shadow Ball) or the plain `wisp` tinted with the palette's
 * glow / deep (the special template, [OrbFx]). The dim returns to 0 at 1.0 s.
 */
internal object ShadowBallFx {
    const val IMPACT = 900f
    const val HIT_MS = 600f
    const val TOTAL = 1700f
    private const val Z = 20f
    /** The smoke of the burst lives up to 0.84 s at full length: shortened so it is gone by [TOTAL] (the mockup simply cut it). */
    private const val BURST_LIFE = .95f

    fun build(s: FxScript, orb: Boolean): FxTimes {
        val k = s.k
        val pal = s.plan.palette
        val pw = if (orb) FxSprite("ps:wisp", pal.glow) else FxSprite("ps:purplewisp")
        val sb = if (orb) FxSprite("ps:wisp", pal.deep) else FxSprite("ps:shadowball")

        s.tween(0f, 250f) { p, _ -> s.dim = .12f * p }
        s.tween(750f, 250f) { p, _ -> s.dim = .12f * (1f - p) }
        s.at(1000f) { s.dim = 0f }

        // five wisps gather at the attacker, the ball grows, then it flies (accelerating)
        for (g in GATHER) {
            s.psFx(pw, PsKey(x = g[0], y = g[1], z = Z, scale = .5f * k, opacity = 0f, time = g[2]), PsEnd(x = 0f, y = 0f, scale = k, opacity = .8f, time = g[3]), PsEase.DECEL, PsAfter.FADE)
        }
        s.psFx(sb, PsKey(z = Z, scale = 0f, opacity = 0f, time = 0f), PsEnd(scale = .8f * k, opacity = .5f, time = 600f), PsEase.DECEL, PsAfter.FADE)
        s.psFx(pw, PsKey(z = Z, scale = 0f, opacity = 0f, time = 0f), PsEnd(scale = 1.5f * k, opacity = .8f, time = 600f), PsEase.DECEL, PsAfter.FADE)
        s.psFx(sb, PsKey(z = Z, scale = .8f * k, opacity = .8f, time = 600f), PsEnd(z = 200f, time = IMPACT), PsEase.ACCEL)
        s.psFx(pw, PsKey(z = Z, scale = 1.5f * k, opacity = .8f, time = 600f), PsEnd(z = 200f, scale = 2f * k, time = IMPACT), PsEase.ACCEL)

        // the dark burst of "C" instead of Showdown's explosion, and the target's dark flinch
        val spot = s.space.fromTarget(-6f, 4f)
        s.at(IMPACT) { FxKits.darkBurst(s, spot, pal, k, BURST_LIFE) }
        val f = FxColor.Chain()
        s.hit(IMPACT, HIT_MS, push = 12f, filter = FxFilterAt { p -> f.reset().brightness(.5f + p * .5f).saturate(1.5f).matrix })
        // the orb template: a type with its own kit also throws that kit's particles (Ghost / Plain have nothing more to add)
        if (orb && s.plan.kit != FxKit.PLAIN && s.plan.kit != FxKit.GHOST) FxKits.schedule(s, s.plan.kit, IMPACT, spot)
        return FxTimes(TOTAL, IMPACT, if (s.react) IMPACT + HIT_MS else IMPACT)
    }

    /** Where each of the five wisps starts (Showdown units x, y) and when it starts and arrives. */
    private val GATHER = arrayOf(
        floatArrayOf(0f, 100f, 0f, 200f), floatArrayOf(-60f, -80f, 50f, 300f), floatArrayOf(60f, -80f, 100f, 400f),
        floatArrayOf(-90f, 40f, 150f, 500f), floatArrayOf(90f, 40f, 200f, 600f),
    )
}

/**
 * The special template ([MoveLook.ORB_TEMPLATE], fallback rule 3 and rule 2): Shadow Ball's orb and burst in the plan's colours
 * (the mockup's `o.tint` path), with the type kit's particles added to the burst when the type has one.
 */
internal object OrbFx {
    fun build(s: FxScript): FxTimes = ShadowBallFx.build(s, orb = true)
}
