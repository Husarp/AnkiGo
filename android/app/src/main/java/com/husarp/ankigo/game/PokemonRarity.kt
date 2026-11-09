package com.husarp.ankigo.game

/** Shared presentation ladder. Blue diamond = Baby, Starters and base total 500+; marks and auto-catch balls agree (PLAN 562). */
enum class PokemonRarity(val mark: Int?, val ball: String) {
    PLAIN(null, "poke-ball"), GREAT(1, "great-ball"),
    ULTRA(2, "ultra-ball"), LEGENDARY(3, "ultra-ball"), MYTHICAL(4, "master-ball");

    companion object {
        const val GREAT_BASE_TOTAL = 500
        fun of(tier: Tier?, baseTotal: Int): PokemonRarity = when (tier) {
            Tier.BABY -> GREAT
            Tier.ULTRA -> ULTRA
            Tier.LEGENDARY -> LEGENDARY
            Tier.MYTHICAL -> MYTHICAL
            Tier.STARTER -> GREAT
            Tier.NORMAL, Tier.MEGA, Tier.GMAX, null -> if (baseTotal >= GREAT_BASE_TOTAL) GREAT else PLAIN
        }
    }
}
