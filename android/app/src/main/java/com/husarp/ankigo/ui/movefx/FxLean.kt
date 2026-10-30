package com.husarp.ankigo.ui.movefx

/**
 * Small casting lean for non-physical moves that send an effect toward the target (PLAN 228 / 248).
 * Self-heals and status auras remain stationary. Physical looks keep their own lunge.
 * The plain fallback uses the same 5 dp, 250 ms curve; reduced motion skips it.
 */
object FxLean {
    /** How far the user leans, in stage units (about dp). */
    const val UNITS = 5f
    /** The same distance in dp for the plain fallback. */
    const val DP = 5f
    /** Mockup ms toward the target, and back to rest. */
    const val OUT_MS = 110f
    const val BACK_MS = 140f
    /** The plain fallback's whole lean, in ms. */
    const val TOTAL_MS = 250L

    /** The lean in stage units at [ms] after its start: eased out to [UNITS] in [OUT_MS], eased back to 0 in [BACK_MS], 0 before and after. */
    fun at(ms: Float): Float = when {
        ms <= 0f -> 0f
        ms < OUT_MS -> UNITS * FxEase.OUT2.at(ms / OUT_MS)
        ms < OUT_MS + BACK_MS -> UNITS * (1f - FxEase.INOUT.at((ms - OUT_MS) / BACK_MS))
        else -> 0f
    }

    /** Only projectile looks lean; the physical looks and stationary self/status effects do not. */
    fun leans(look: MoveLook): Boolean = when (look) {
        MoveLook.HEAL, MoveLook.MULTI_HIT, MoveLook.RECOIL, MoveLook.AURA_TEMPLATE,
        MoveLook.TACKLE, MoveLook.VINE_WHIP, MoveLook.EARTHQUAKE, MoveLook.HIT_TEMPLATE -> false
        MoveLook.EMBER, MoveLook.WATER_JET, MoveLook.THUNDERBOLT, MoveLook.ICE_BEAM, MoveLook.PSYCHIC,
        MoveLook.DRAIN, MoveLook.SHADOW_BALL, MoveLook.ORB_TEMPLATE -> true
    }

    /** The plain fallback's lean: only special projectiles; status stays in place, physical lunges. */
    fun leansCategory(category: com.husarp.ankigo.game.Category): Boolean = category == com.husarp.ankigo.game.Category.SPECIAL
}
