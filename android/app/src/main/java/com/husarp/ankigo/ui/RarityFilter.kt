package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.PokemonRarity
import com.husarp.ankigo.game.Tier

/** PLAN 482: picked marks match any selected rarity; other filter groups still combine with AND. */
data class RarityFilter(val picked: Set<PokemonRarity> = emptySet()) {
    val active get() = picked.isNotEmpty()
    fun matches(tier: Tier?, baseTotal: Int): Boolean = !active || PokemonRarity.of(tier, baseTotal) in picked
    fun apply(data: GameData, mons: List<Mon>): List<Mon> = mons.filter {
        matches(it.tier, data.species[it.species]?.base?.total ?: 0)
    }
    fun toggle(rarity: PokemonRarity) = copy(picked = if (rarity in picked) picked - rarity else picked + rarity)
    fun encode(): String = picked.sortedBy { it.ordinal }.joinToString(",") { it.name }
    companion object {
        val options = PokemonRarity.entries.filter { it.mark != null }
        fun label(rarity: PokemonRarity): String = when (rarity) {
            PokemonRarity.PLAIN -> "Plain"
            PokemonRarity.COMMON -> "Starter / strong"
            PokemonRarity.BABY -> "Baby"
            PokemonRarity.ULTRA -> "Ultra Beast"
            PokemonRarity.LEGENDARY -> "Legendary"
            PokemonRarity.MYTHICAL -> "Mythical"
        }
        fun decode(s: String?) = RarityFilter(s.orEmpty().split(',').mapNotNull { name ->
            options.firstOrNull { it.name == name }
        }.toSet())
    }
}
