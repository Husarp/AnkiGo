package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.shownName

internal object BuddyPicker {
    enum class Sort { LEVEL, NAME, RECENT }
    enum class Place { ALL, TEAM, BOX }
    data class Filters(val place: Place = Place.ALL, val favourites: Boolean = false, val shiny: Boolean = false, val types: TypeFilter = TypeFilter()) {
        val active get() = place != Place.ALL || favourites || shiny || types.active
        fun matches(mon: Mon, team: Collection<String>, typesOf: (Mon) -> List<com.husarp.ankigo.game.Type>): Boolean =
            (place == Place.ALL || (mon.uid in team) == (place == Place.TEAM)) &&
                (!favourites || mon.favourite) && (!shiny || mon.shiny) && types.matches(typesOf(mon))
    }
    fun mons(mons: List<Mon>, selected: String, query: String, sort: Sort, filters: Filters = Filters(), team: Collection<String> = emptyList(), typesOf: (Mon) -> List<com.husarp.ankigo.game.Type> = { emptyList() }): List<Mon> {
        val byName=compareBy<Mon> { it.shownName.lowercase(java.util.Locale.ROOT) }.thenBy { it.uid }
        val order=when(sort) {
            Sort.LEVEL -> compareByDescending<Mon> { it.level }.then(byName)
            Sort.NAME -> byName
            Sort.RECENT -> compareByDescending<Mon> { it.caughtAt }.then(byName)
        }
        return mons.distinctBy { it.uid }.filter { filters.matches(it, team, typesOf) }.filter { it.shownName.contains(query.trim(),ignoreCase=true) || it.name.contains(query.trim(),ignoreCase=true) }
            .sortedWith(compareByDescending<Mon> { it.uid==selected }.then(order))
    }
}
