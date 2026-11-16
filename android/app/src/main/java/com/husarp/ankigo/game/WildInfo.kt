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

/** The only move fact visible before discovery; no identity, type or numbers. */
data class MoveOutline(val category: Category) {
    companion object {
        fun of(move: Move) = MoveOutline(move.category)
    }
}

/** One slot of the wild Pokémon; null id/name means undiscovered. */
data class WildMoveSlot(val id: String?, val name: String?, val outline: MoveOutline? = null)

/** What the player already knows of the wild species. */
enum class WildDex { SEEN, DEFEATED, CAUGHT }

/**
 * The facts of the info window. Only things a player may see: IVs, EVs, ability, held item and undiscovered move identities are
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
    val base: Stats?,
    /** Null until known; otherwise stats at its level with the fixed assumption for every wild one (see [statsAt]). */
    val stats: Stats?,
    val matchups: TypeMatchups?,
    val moves: List<WildMoveSlot>,
    val nature: Nature = Nature.HARDY,
) {
    val known: Boolean get() = dex != WildDex.SEEN

    companion object {
        /** IV assumed for every stat of every wild Pokémon in the window (the middle of 0..31): its real IVs never show. */
        const val ASSUMED_IV = 15

        /**
         * Stats at [level] from [base] with its visible nature, 0 EVs and [ASSUMED_IV] in every stat: the same rule for every wild
         * Pokémon, so the numbers carry no trace of the real IVs or EVs.
         */
        fun statsAt(base: Stats, level: Int, nature: Nature = Nature.HARDY): Stats = Stats(
            StatCalc.hp(base.hp, ASSUMED_IV, 0, level),
            StatCalc.other(base.atk, ASSUMED_IV, 0, level, nature.mult(Stat.ATK)),
            StatCalc.other(base.def, ASSUMED_IV, 0, level, nature.mult(Stat.DEF)),
            StatCalc.other(base.spa, ASSUMED_IV, 0, level, nature.mult(Stat.SPA)),
            StatCalc.other(base.spd, ASSUMED_IV, 0, level, nature.mult(Stat.SPD)),
            StatCalc.other(base.spe, ASSUMED_IV, 0, level, nature.mult(Stat.SPE)),
        )

        /** Null while the wild species is not in the data. [usedMoves] = move names from [WildMoveLog.used]. */
        fun of(data: GameData, state: GameState, battle: Battle, usedMoves: Set<String>): WildInfo? {
            val wild = battle.wild
            val sp = data.species[wild.species] ?: return null
            val used = usedMoves.map { it.lowercase() }.toSet()
            val known = SpeciesKnowledge.known(data, state, wild.species)
            val visible = wild.moves.map { id ->
                val move = data.move(id)
                if (id in wild.discoveredMoves || move?.name?.lowercase() in used) WildMoveSlot(id, move?.name ?: id)
                else WildMoveSlot(null, null, move?.let(MoveOutline::of))
            }
            return WildInfo(
                name = wild.name, dexNum = sp.baseNum, level = wild.level, gender = wild.gender, shiny = wild.shiny,
                types = sp.types, tier = battle.tier, nature = wild.nature,
                dex = when {
                    wild.species in state.caught -> WildDex.CAUGHT
                    known -> WildDex.DEFEATED
                    else -> WildDex.SEEN
                },
                base = sp.base.takeIf { known }, stats = if (known) statsAt(sp.base, wild.level, wild.nature) else null,
                matchups = if (battle.playerAttacked || MatchupKnowledge.bonded(data, state, wild.species)) MatchupKnowledge.of(data, state, wild.species) else null, moves = visible,
            )
        }
    }
}
