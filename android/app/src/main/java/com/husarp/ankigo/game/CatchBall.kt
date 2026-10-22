package com.husarp.ankigo.game

/**
 * PLAN 205 (R14 B2-E): the ball drawn in the catch animation when a fight ends in a catch WITHOUT a thrown ball. Visual only: it changes
 * nothing about catching, the Bag, money, logs or the save, and a really thrown ball always shows as itself.
 */
object CatchBall {
    /** Base stat total from which a plain Pokémon gets the Great Ball. */
    const val GREAT_BASE_TOTAL = 500

    /**
     * The ball item id for a wild [sp]: Mythical -> Master Ball; Legendary -> Ultra Ball; Starter, Ultra Beast, Baby, and Normal with a
     * base stat total of [GREAT_BASE_TOTAL] or more -> Great Ball; everyone else -> Poké Ball. A species with no tier, a Mega or a G-Max
     * follows the Normal rule by base total. No species data (null) -> Poké Ball.
     */
    fun forAuto(sp: Species?): String {
        if (sp == null) return "poke-ball"
        return when (Encounters.tierOf(sp)) {
            Tier.MYTHICAL -> "master-ball"
            Tier.LEGENDARY -> "ultra-ball"
            Tier.STARTER, Tier.ULTRA, Tier.BABY -> "great-ball"
            Tier.NORMAL, Tier.MEGA, Tier.GMAX, null ->
                if (sp.base.total >= GREAT_BASE_TOTAL) "great-ball" else "poke-ball"
        }
    }
}
