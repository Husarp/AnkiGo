package com.husarp.ankigo.game

// PLAN 93 (R4-X2): what the "tap the wild Pokémon" info window may show. Pure Kotlin; the window itself is ui/BattleDetails.kt.

/**
 * The moves the wild Pokémon has used in the fight that is on now, from the Attack events (by name, as the events carry it).
 * Kept in memory only, never in the save: after an app restart in the middle of a fight the list starts empty again.
 */
class WildMoveLog {
    private val names = LinkedHashSet<String>()
    private var bound: Battle? = null

    /** Feeds the events one action returned; [battle] = the fight that is on after them (null = none). */
    fun observe(events: List<GameEvent>, battle: Battle?) {
        if (events.isEmpty()) return
        if (bound !== battle) names.clear() // another fight than the one the list belongs to (restart, restore)
        for (e in events) when (e) {
            is GameEvent.Attack -> if (!e.byPlayer) names += e.move
            is GameEvent.Encounter, is GameEvent.Defeated, is GameEvent.Caught, is GameEvent.Fled -> names.clear()
            else -> {}
        }
        bound = battle
    }

    /** The move names the wild one of [battle] has used (empty for any other fight). */
    fun used(battle: Battle?): Set<String> = if (battle != null && battle === bound) names.toSet() else emptySet()
}

/** One move slot of the wild Pokémon: [id] is null while the move is still unknown ("?"). */
data class WildMoveSlot(val id: String?, val name: String?)

/** What the player already knows of the wild species. */
enum class WildDex { SEEN, CAUGHT }

/**
 * The facts of the info window. Only things a player may see: IVs, nature, EVs, ability, held item and unused moves are
 * not in here, so no screen can show them by mistake.
 */
data class WildInfo(
    val name: String,
    val dexNum: Int,
    val level: Int,
    val gender: Char,
    val shiny: Boolean,
    val types: List<Type>,
    val tier: Tier,
    val dex: WildDex,
    val base: Stats,
    val matchups: TypeMatchups,
    val moves: List<WildMoveSlot>,
) {
    companion object {
        /** Null while the wild species is not in the data. [usedMoves] = move names from [WildMoveLog.used]. */
        fun of(data: GameData, state: GameState, battle: Battle, usedMoves: Set<String>): WildInfo? {
            val wild = battle.wild
            val sp = data.species[wild.species] ?: return null
            val used = usedMoves.map { it.lowercase() }.toSet()
            val known = wild.moves.mapNotNull { id -> data.move(id)?.takeIf { it.name.lowercase() in used } }
                .distinctBy { it.id }.map { WildMoveSlot(it.id, it.name) }
            // known moves first, then one "?" for each of the rest: the slot order of the real list never shows
            val unknown = List((wild.moves.size - known.size).coerceAtLeast(0)) { WildMoveSlot(null, null) }
            return WildInfo(
                name = wild.name, dexNum = sp.baseNum, level = wild.level, gender = wild.gender, shiny = wild.shiny,
                types = sp.types, tier = battle.tier,
                dex = if (wild.species in state.caught) WildDex.CAUGHT else WildDex.SEEN,
                base = sp.base, matchups = TypeMatchups.of(sp.types), moves = known + unknown,
            )
        }
    }
}
