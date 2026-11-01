package com.husarp.ankigo.game

/** Historical identity: independent of later renames, evolution or release. */
data class AchPokemon(val uid: String, val name: String, val num: Int, val shiny: Boolean)

object AchievementPokemon {
    private val cumulativeIds = setOf("catches", "ball-catches", "evolutions", "total-levels", "total-xp",
        "highest-level", "types", "defeats", "comebacks", "shinies", "legendary-mythical", "tms")
    private val pokemonBadges = setOf(5, 7, 8, 9, 10, 16, 17, 18, 19, 20)
    private val pokemonSecrets = setOf("one-shot", "last-breath", "revolving-door", "perfect-throw",
        "david-goliath", "deja-vu", "bully", "glow-up")

    fun isPokemonSecret(id: String) = id in pokemonSecrets
    fun applies(v: AchView) = v.id in cumulativeIds || v.ankimonBadge in pokemonBadges ||
        (v.group == "Secret" && isPokemonSecret(v.id))


    fun record(state: GameState, key: String, mon: Mon, data: GameData? = null) {
        val list = state.achPokemon.getOrPut(key) { mutableListOf() }
        list.removeAll { it.uid == mon.uid }
        list += AchPokemon(mon.uid, mon.shownName, if (data != null) mon.lookNum(data) else mon.species, mon.shiny)
    }
}
