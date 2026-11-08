package com.husarp.ankigo.game

/** Seen attacking types share the national Pokédex number, just like species knowledge. */
object MatchupKnowledge {
    fun seen(data: GameData, state: GameState, species: Int): Set<Type> =
        state.seenMatchups[data.species[species]?.baseNum ?: species].orEmpty().toSet()

    fun of(data: GameData, state: GameState, species: Int): TypeMatchups =
        TypeMatchups.discovered(data.sp(species).types, seen(data, state, species))

    fun available(data: GameData, state: GameState, species: Int, owned: Boolean = false): Boolean {
        if (owned || bonded(data, state, species)) return true
        state.battle?.takeIf { it.wild.species == species }?.let { return it.playerAttacked }
        return SpeciesKnowledge.known(data, state, species) || seen(data, state, species).isNotEmpty()
    }

    fun bonded(data: GameData, state: GameState, species: Int): Boolean {
        val base = data.species[species]?.baseNum ?: species
        return base in state.bondMatchups
    }

    fun reveal(data: GameData, state: GameState, mon: Mon, events: MutableList<GameEvent>) {
        val base = data.species[mon.species]?.baseNum ?: mon.species
        state.bondMatchups += base
        val seen = state.seenMatchups.getOrPut(base) { mutableSetOf() }
        if (seen.addAll(Type.entries)) events += GameEvent.Info("You know ${mon.shownName}'s matchups now")
    }

    /** Record before rewards/evolution or the next encounter can change either participant. */
    fun observe(data: GameData, state: GameState, battle: Battle, main: Mon, events: List<GameEvent>) {
        for (e in events.filterIsInstance<GameEvent.Attack>()) {
            if (e.category == Category.STATUS) continue
            if (e.byPlayer) battle.playerAttacked = true
            if (e.missed || e.failed || (e.damage <= 0 && e.effectiveness != 0.0)) continue
            val type = e.moveType ?: continue // Struggle has no type effectiveness
            val species = if (e.byPlayer) battle.wild.species else main.species
            val base = data.species[species]?.baseNum ?: species
            state.seenMatchups.getOrPut(base) { mutableSetOf() } += type
        }
    }
}
