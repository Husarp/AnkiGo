package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Type

/** One cell of the Box grid: a single Pokémon, or a group of copies of one species ("×N"). Pure logic, no views. */
class BoxTile(
    /** Best copy first (the order [BoxTiles.order] gives for [CopySort.LEVEL]); one member = a plain tile. */
    val members: List<Mon>,
) {
    /** The copy whose sprite and level the tile shows. */
    val best: Mon get() = members.first()
    val count: Int get() = members.size
    val isGroup: Boolean get() = members.size > 1

    /** Marks of a group show when any copy has them, so grouping never hides a shiny / favourite / team member. */
    fun anyShiny(): Boolean = members.any { it.shiny }
    fun anyFavourite(): Boolean = members.any { it.favourite }
    fun anyInTeam(team: Collection<String>): Boolean = members.any { it.uid in team }
}

/** The Box "show" filters (PLAN 81, 6A): everything, or only the favourites / the shiny ones / the team members / those holding an item / the Exp. Share / those that can evolve now. */
enum class BoxFilter(val label: String, val none: String? = null) {
    ALL("All"), FAVOURITES("Favourites"), SHINY("Shiny"), IN_TEAM("In team"),
    /** R4-V5: the details show held items and evolutions, the tiles no longer do, so these two find them. */
    HOLDS_ITEM("Holds an item", "None of your Pokémon holds an item."),
    /** R6-06: the holder can be any owned Pokémon, also one in the Box. */
    HOLDS_XP_SHARE("Holds Exp. Share", "None of your Pokémon holds the Exp. Share."),
    CAN_EVOLVE("Can evolve now", "No Pokémon can evolve right now."),
    ;

    /** R18 253: these three have a quick chip in the Box row (it toggles Show); the others are only in the sheet, so the "Filters" chip names them. */
    val hasQuickChip: Boolean get() = this == FAVOURITES || this == SHINY || this == IN_TEAM
}

/** The sort toggle of the copies sheet: highest level first, or latest caught first. */
enum class CopySort(val label: String) { LEVEL("Level"), RECENT("Recent") }

object BoxTiles {
    /**
     * [team] = the uids of the team (for [BoxFilter.IN_TEAM]); [xpShareUid] = the holder of the Exp. Share (for [BoxFilter.HOLDS_XP_SHARE]); [canEvolve] answers [BoxFilter.CAN_EVOLVE] (see [MonTags.canEvolve]).
     * PLAN 192: [types] is the separate type filter (it also applies with any [f], X7); [typesOf] gives a Pokémon's species types (a regional form has its own).
     */
    fun filter(
        mons: List<Mon>, f: BoxFilter, team: Collection<String> = emptyList(), xpShareUid: String? = null, canEvolve: (Mon) -> Boolean = { false },
        types: TypeFilter = TypeFilter(), typesOf: (Mon) -> List<Type> = { emptyList() },
    ): List<Mon> {
        val shown = when (f) {
            BoxFilter.ALL -> mons
            BoxFilter.FAVOURITES -> mons.filter { it.favourite }
            BoxFilter.SHINY -> mons.filter { it.shiny }
            BoxFilter.IN_TEAM -> mons.filter { it.uid in team }
            BoxFilter.HOLDS_ITEM -> mons.filter { it.heldItem != null }
            BoxFilter.HOLDS_XP_SHARE -> mons.filter { xpShareUid != null && it.uid == xpShareUid }
            BoxFilter.CAN_EVOLVE -> mons.filter(canEvolve)
        }
        return if (types.active) shown.filter { types.matches(typesOf(it)) } else shown
    }

    /**
     * The tiles of the Box for [mons] (all owned Pokémon, team members too). [grouped]: copies of one species collapse into
     * one tile (shinies stay in the group, regional forms are their own group); off: one tile per Pokémon, ordered by
     * species then level, as the Box always was.
     */
    fun build(data: GameData, mons: List<Mon>, grouped: Boolean): List<BoxTile> =
        if (grouped) BoxGroups.group(data, mons, shinySeparate = false, formSeparate = true).map { BoxTile(it.members) }
        else mons.sortedWith(compareBy({ it.species }, { it.level })).map { BoxTile(listOf(it)) }

    /** The copies in the sheet's order. Both sorts break ties by level / latest caught so the order is stable. */
    fun order(members: List<Mon>, sort: CopySort): List<Mon> = when (sort) {
        CopySort.LEVEL -> members.sortedWith(compareBy<Mon>({ -it.level }, { if (it.shiny) 0 else 1 }, { -it.caughtAt }))
        CopySort.RECENT -> members.sortedWith(compareBy<Mon>({ -it.caughtAt }, { -it.level }))
    }

    /**
     * [tiles] in [spec]'s order (PLAN 48/55). A group sorts by its best member under [spec] (the first copy [MonSort.sort] gives);
     * the tile still shows its own best-level copy and keeps its members as they are. Team members are in [tiles] like any other.
     */
    fun sorted(data: GameData, state: GameState, tiles: List<BoxTile>, spec: SortSpec): List<BoxTile> =
        MonSort.sortGroups(data, state, tiles, { it.members }, spec)
}
