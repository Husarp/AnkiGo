package com.husarp.ankigo.game

/**
 * PLAN 205 (R14 B2-E): the ball drawn in the catch animation when a fight ends in a catch WITHOUT a thrown ball. Visual only: it changes
 * nothing about catching, the Bag, money, logs or the save, and a really thrown ball always shows as itself.
 */
object CatchBall {
    /** Base stat total from which a plain Pokémon gets the Great Ball. */
    const val GREAT_BASE_TOTAL = PokemonRarity.GREAT_BASE_TOTAL

    /**
     * Mythical -> Master Ball; Ultra Beast and Legendary -> Ultra Ball; Baby and Starter, plus Normal with a base stat
     * total of [GREAT_BASE_TOTAL] or more -> Great Ball; everyone else -> Poké Ball. No tier, Mega and G-Max follow the Normal rule.
     * Missing species data -> Poké Ball. Uses the same ladder as the name marks.
     */
    fun forAuto(sp: Species?): String =
        if (sp == null) "poke-ball" else PokemonRarity.of(Encounters.tierOf(sp), sp.base.total).ball
}
