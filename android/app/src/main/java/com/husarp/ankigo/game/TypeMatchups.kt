package com.husarp.ankigo.game

// PLAN items 9 and 60: the type matchups of a Pokémon (what hits it, and what its own types hit). Pure Kotlin.

/** One matchup entry: [type] hits for [mult] (4, 2, 0.5, 0.25 or 0). */
data class Matchup(val type: Type, val mult: Double) {
    val label: String get() = when (mult) { 4.0 -> "×4"; 2.0 -> "×2"; 0.5 -> "×½"; 0.25 -> "×¼"; 0.0 -> "×0"; 1.0 -> "×1"; else -> "×$mult" }
}

/**
 * [weakTo] attacking types that hit it for more than ×1 (×4 first, then ×2, each in type order); [resists] ×¼ before ×½;
 * [immune] ×0; [strongAgainst] the defending types that at least one of its own types hits super effectively (×2), in type order.
 */
data class TypeMatchups(
    val weakTo: List<Matchup>,
    val resists: List<Matchup>,
    val immune: List<Matchup>,
    val strongAgainst: List<Matchup>,
    val normal: List<Matchup> = emptyList(),
    val unknownCount: Int = 0,
) {
    companion object {
        fun discovered(types: List<Type>, seen: Set<Type>): TypeMatchups {
            val all = of(types)
            return TypeMatchups(
                all.weakTo.filter { it.type in seen }, all.resists.filter { it.type in seen },
                all.immune.filter { it.type in seen }, emptyList(),
                Type.entries.filter { it in seen && TypeChart.eff(it, types) == 1.0 }.map { Matchup(it, 1.0) },
                Type.entries.count { it !in seen },
            )
        }

        fun of(types: List<Type>): TypeMatchups {
            val all = Type.entries.map { Matchup(it, TypeChart.eff(it, types)) }
            val weak = all.filter { it.mult > 1.0 }.sortedWith(compareByDescending<Matchup> { it.mult }.thenBy { it.type.ordinal })
            val resist = all.filter { it.mult > 0.0 && it.mult < 1.0 }.sortedWith(compareBy<Matchup> { it.mult }.thenBy { it.type.ordinal })
            val immune = all.filter { it.mult == 0.0 }
            val strong = Type.entries.mapNotNull { target ->
                val best = types.maxOfOrNull { TypeChart.single(it, target) } ?: return@mapNotNull null
                if (best > 1.0) Matchup(target, best) else null
            }
            return TypeMatchups(weak, resist, immune, strong)
        }
    }
}
