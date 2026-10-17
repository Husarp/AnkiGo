package com.husarp.ankigo.ui.movefx

/*
 * The looks of R7-03 (docs/design/attack-anims-v2/v2-fx.js ported): Tackle and the physical template. Every look is a function
 * (FxScript) -> FxTimes that is called once, fills the timeline, and returns its timings (see [FxTimes]). The other looks live in
 * their own files and are registered in [MoveFx.create].
 */

/** Tackle (the mockup's `tackle`): the lunge, then a star, a ring, sparks and stars where it lands, a white flash and the target's knock-back. */
internal object TackleFx {
    /** Mockup ms of the first hit, the end of the lunge and of the target's reaction. */
    const val IMPACT = 310f
    const val LUNGE_END = 610f
    const val HIT_END = 690f
    const val TOTAL = 1100f

    fun build(s: FxScript): FxTimes {
        s.lunge(0f)
        s.at(IMPACT) { FxKits.normal(s, hitSpot(s)) }
        s.hit(IMPACT, 380f, push = 10f)
        return times(s)
    }

    /** Where the attacker lands: a little in front of the target's centre, toward the attacker. */
    fun hitSpot(s: FxScript): FxPt = s.space.fromTarget(-22f, 4f)

    fun times(s: FxScript): FxTimes = FxTimes(TOTAL, IMPACT, if (s.react) HIT_END else LUNGE_END)
}

/**
 * The physical template (fallback rules 2 / 3): Tackle's lunge and timing, but the burst on impact is made of the plan's kit
 * ([FxKits]), sized by the plan's k. As short as Tackle (1.1 s).
 */
internal object HitTemplateFx {
    fun build(s: FxScript): FxTimes {
        s.lunge(0f)
        FxKits.schedule(s, s.plan.kit, TackleFx.IMPACT, TackleFx.hitSpot(s))
        s.hit(TackleFx.IMPACT, 380f, push = 10f)
        return TackleFx.times(s)
    }
}
