package com.husarp.ankigo.game

// PLAN 102: "choose which look a Pokémon shows". Purely visual: stats, species, moves and battles never read Mon.look.
// Pure Kotlin, no android.*.

object Looks {
    /**
     * The looks [mon] can show: its evolution line from the base form up to and including its current species, oldest
     * first (follows `prevo`, so a regional line works because its prevo already points at the regional pre-form).
     * Earlier forms are offered only when caught ([caught] = the Pokédex "caught" set); its own species is always offered.
     * Empty only when the species is not in [data].
     */
    fun choices(data: GameData, mon: Mon, caught: Set<Int>): List<Species> {
        val line = ArrayList<Species>()
        val seen = HashSet<Int>()
        var cur: Species? = data.species[mon.species]
        while (cur != null && seen.add(cur.num)) {
            line += cur
            cur = cur.prevo?.let { data.species[it] }
        }
        line.reverse()
        return line.filter { it.num == mon.species || it.num in caught }
    }

    /** The whole line (no caught filter), used only to check that a look is still one of the Pokémon's line. */
    private fun fullLine(data: GameData, mon: Mon): List<Species> = choices(data, mon, data.species.keys)

    /** Clears every owned Pokémon's look that is not its own species and not caught (old saves; looks set before the caught rule). */
    fun sanitize(state: GameState, data: GameData) {
        for (m in state.mons) {
            val l = m.look ?: continue
            if (l != m.species && (l !in state.caught || fullLine(data, m).none { it.num == l })) m.look = null
        }
    }

    /** True when [mon] has more than one look to choose from (the menu entry "Change look" is hidden otherwise). */
    fun hasChoice(data: GameData, mon: Mon, caught: Set<Int>): Boolean = choices(data, mon, caught).size > 1
}

/**
 * The species whose sprite [this] shows: its chosen [Mon.look] while that is still one of its line, else its own species.
 * Data-only: caught-ness is enforced by [Looks.sanitize] at every READY (the caught set never shrinks).
 */
fun Mon.lookSpecies(data: GameData): Species =
    look?.let { want -> Looks.choices(data, this, data.species.keys).firstOrNull { it.num == want } } ?: data.sp(species)

/** The Pokédex number of the sprite [this] shows (see [lookSpecies]); its own species when [data] isn't loaded. Owned Pokémon only. */
fun Mon.lookNum(data: GameData?): Int = if (data == null) species else lookSpecies(data).num
