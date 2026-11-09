package com.husarp.ankigo.game

/** Shared presentation ladder. Starters / base total 500+ have no mark but still use a Great Ball (PLAN 562). */
enum class PokemonRarity(val mark: Int?, val ball: String) {
    PLAIN(null, "poke-ball"), COMMON(null, "great-ball"), BABY(1, "great-ball"),
    ULTRA(2, "ultra-ball"), LEGENDARY(3, "ultra-ball"), MYTHICAL(4, "master-ball");

    companion object {
        const val GREAT_BASE_TOTAL = 500
        fun of(tier: Tier?, baseTotal: Int): PokemonRarity = when (tier) {
            Tier.BABY -> BABY
            Tier.ULTRA -> ULTRA
            Tier.LEGENDARY -> LEGENDARY
            Tier.MYTHICAL -> MYTHICAL
            Tier.STARTER -> COMMON
            Tier.NORMAL, Tier.MEGA, Tier.GMAX, null -> if (baseTotal >= GREAT_BASE_TOTAL) COMMON else PLAIN
        }
    }
}
