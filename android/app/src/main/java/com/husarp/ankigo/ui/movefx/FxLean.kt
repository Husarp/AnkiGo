package com.husarp.ankigo.ui.movefx

/**
 * R18-C (PLAN 228): the lean of the user of a special or status move. Physical moves lunge (Tackle's run-up, 14 dp in the plain
 * fallback); the others used to stand still. Now they step a little toward the target and back, much smaller than the lunge, as if
 * casting at it: [UNITS] stage units (about 5 dp; a stage unit is about a dp), eased, [OUT_MS] out and [BACK_MS] back (about
 * 250 ms together, mockup ms: the player divides them by the battle pace), at the start of the look. The plain fallback of
 * "Attack animations" off uses the same numbers ([DP], [TOTAL_MS]) in `ArenaView.lean`. Reduced motion plays no look and no lean.
 * Pure Kotlin.
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

    /** True for the looks of special and status moves (a look is chosen by category, see [MoveFxCatalog.plan]); the physical looks lunge or stay as they are. */
    fun leans(look: MoveLook): Boolean = when (look) {
        MoveLook.TACKLE, MoveLook.VINE_WHIP, MoveLook.EARTHQUAKE, MoveLook.HIT_TEMPLATE -> false
        MoveLook.EMBER, MoveLook.WATER_JET, MoveLook.THUNDERBOLT, MoveLook.ICE_BEAM, MoveLook.PSYCHIC,
        MoveLook.SHADOW_BALL, MoveLook.ORB_TEMPLATE, MoveLook.AURA_TEMPLATE -> true
    }

    /** The plain fallback's lean for a move of [category]: special and status lean, physical lunges. */
    fun leansCategory(category: com.husarp.ankigo.game.Category): Boolean = category != com.husarp.ankigo.game.Category.PHYSICAL
}
