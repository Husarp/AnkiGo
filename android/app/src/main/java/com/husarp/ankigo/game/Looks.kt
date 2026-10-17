package com.husarp.ankigo.game

// PLAN 102: "choose which look a Pokémon shows". Purely visual: stats, species, moves and battles never read Mon.look.
// Pure Kotlin, no android.*.

object Looks {
    /**
     * The looks [mon] can show: its evolution line from the base form up to and including its current species, oldest
     * first (follows `prevo`, so a regional line works because its prevo already points at the regional pre-form).
     * Empty only when the species is not in [data].
     */
    fun choices(data: GameData, mon: Mon): List<Species> {
        val line = ArrayList<Species>()
        val seen = HashSet<Int>()
        var cur: Species? = data.species[mon.species]
        while (cur != null && seen.add(cur.num)) {
            line += cur
            cur = cur.prevo?.let { data.species[it] }
        }
        line.reverse()
        return line
    }

    /** True when [mon] has more than one look to choose from (the menu entry "Change look" is hidden otherwise). */
    fun hasChoice(data: GameData, mon: Mon): Boolean = choices(data, mon).size > 1
}

/** The species whose sprite [this] shows: its chosen [Mon.look] while that is still one of its [Looks.choices], else its own species. */
fun Mon.lookSpecies(data: GameData): Species =
    look?.let { want -> Looks.choices(data, this).firstOrNull { it.num == want } } ?: data.sp(species)

/** The Pokédex number of the sprite [this] shows (see [lookSpecies]); its own species when [data] isn't loaded. Owned Pokémon only. */
fun Mon.lookNum(data: GameData?): Int = if (data == null) species else lookSpecies(data).num
