package com.husarp.ankigo.game

/** Defeating or catching any form unlocks every form with the same national Pokédex number. */
object SpeciesKnowledge {
    fun known(data: GameData, state: GameState, species: Int): Boolean {
        val base = data.species[species]?.baseNum ?: species
        fun matches(num: Int) = (data.species[num]?.baseNum ?: num) == base
        return state.caught.any(::matches) || state.defeatedSpecies.any(::matches) || state.mons.any { matches(it.species) }
    }
}
