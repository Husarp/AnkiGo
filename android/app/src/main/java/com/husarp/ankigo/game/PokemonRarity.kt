package com.husarp.ankigo.game

/** PLAN 464: one presentation ladder for name marks and the ball shown on an auto-catch. */
enum class PokemonRarity(val mark: Int?, val ball: String) {
    PLAIN(null, "poke-ball"), COMMON(0, "great-ball"), BABY(1, "great-ball"),
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
