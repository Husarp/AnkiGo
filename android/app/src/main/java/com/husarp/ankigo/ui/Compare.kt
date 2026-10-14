package com.husarp.ankigo.ui

/** One Pokémon picked for Compare: an owned one (by uid) or a Pokédex entry (by species number). */
sealed class ComparePick {
    data class Owned(val uid: String) : ComparePick()
    data class Dex(val species: Int) : ComparePick()
}

/** Compare 2–4 Pokémon side by side (PLAN item 9). Stub until ticket U-05 builds the table. */
object Compare {
    const val MIN = 2
    const val MAX = 4

    /** Opens the comparison of [picks] (MIN..MAX entries) from [page]. */
    fun open(page: GamePage, picks: List<ComparePick>) {
        Ui.toast(page, "Compare ${picks.size} Pokémon")
    }
}
