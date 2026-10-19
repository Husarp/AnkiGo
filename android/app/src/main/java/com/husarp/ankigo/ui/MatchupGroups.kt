package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Matchup
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.game.TypeMatchups

/**
 * R9-W3-A (D8-V1): the matchups of a Pokémon grouped by multiplier, in this order. [STRONG_AGAINST] is the odd one out: its own
 * attacks hit those types for ×2 (details only, always last). Pure Kotlin, unit-tested.
 */
enum class MatchupGroup(val mult: Double, val label: String, val pill: String) {
    VERY_WEAK(4.0, "Very weak against", "×4"),
    WEAK(2.0, "Weak against", "×2"),
    RESISTS(0.5, "Resists", "×½"),
    STRONGLY_RESISTS(0.25, "Strongly resists", "×¼"),
    IMMUNE(0.0, "Immune to", "×0"),
    STRONG_AGAINST(2.0, "Strong against", "×2"),
}

object MatchupGroups {
    /** The groups of the defender multipliers in [entries], in the fixed order; groups without a type are left out. */
    fun of(entries: List<Matchup>): List<Pair<MatchupGroup, List<Type>>> =
        MatchupGroup.entries.filter { it != MatchupGroup.STRONG_AGAINST }.mapNotNull { g ->
            val types = entries.filter { it.mult == g.mult }.map { it.type }
            if (types.isEmpty()) null else g to types
        }

    /** Details: the defender groups, then "Strong against" last. */
    fun details(m: TypeMatchups): List<Pair<MatchupGroup, List<Type>>> =
        of(m.weakTo + m.resists + m.immune) +
            (if (m.strongAgainst.isEmpty()) emptyList() else listOf(MatchupGroup.STRONG_AGAINST to m.strongAgainst.map { it.type }))

    /** "A", "A and B", "A, B and C". */
    fun list(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> names[0]
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }

    /** What a tap on a group says (the mockup's TOAST). [name] is the Pokémon's display name. */
    fun toast(g: MatchupGroup, types: List<Type>, name: String): String {
        val ts = list(types.map { it.label })
        val mv = "$ts moves"
        return when (g) {
            MatchupGroup.VERY_WEAK -> "$mv hit $name 4× as hard: both its types are weak to them."
            MatchupGroup.WEAK -> "$mv hit $name twice as hard."
            MatchupGroup.RESISTS -> "$mv do only half damage to $name."
            MatchupGroup.STRONGLY_RESISTS -> "$mv do only a quarter of the damage to $name."
            MatchupGroup.IMMUNE -> "$mv can't hurt $name at all."
            MatchupGroup.STRONG_AGAINST -> "$name's own-type moves hit $ts Pokémon twice as hard."
        }
    }

    /** Pill text: white unless it would be under 2.6 contrast on [fill], then the dark ink (the mockup's rule). */
    fun pillInk(fill: Int): Int = if (TypeInk.contrast(0xFFFFFFFF.toInt(), fill) >= 2.6) 0xFFFFFFFF.toInt() else 0xFF1E1E2E.toInt()

    /** The thin outline of a type chip in a group: a darker, saturated shade of the group colour (light); the colour itself on dark (a dark ring would vanish there). */
    fun outline(groupColor: Int, dark: Boolean): Int = if (dark) groupColor else TypeInk.mix(groupColor, 0xFF000000.toInt(), 0.3f)

    /** Box colour: light = the mantle; dark = the card mixed 18 % towards black. */
    fun boxColor(card: Int, mantle: Int, dark: Boolean): Int = if (dark) TypeInk.mix(card, 0xFF000000.toInt(), 0.18f) else mantle
}
