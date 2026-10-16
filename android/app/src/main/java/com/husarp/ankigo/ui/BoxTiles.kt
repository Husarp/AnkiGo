package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.Mon

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

/** The Box filter chips (PLAN 81): everything, or only the favourites. */
enum class BoxFilter { ALL, FAVOURITES }

/** The sort toggle of the copies sheet: highest level first, or latest caught first. */
enum class CopySort(val label: String) { LEVEL("Level"), RECENT("Recent") }

object BoxTiles {
    fun filter(mons: List<Mon>, f: BoxFilter): List<Mon> = if (f == BoxFilter.FAVOURITES) mons.filter { it.favourite } else mons

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
}
