package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Type

/**
 * PLAN 192: how the picked types match a species. [ANY] "Any of": it has at least one of them (the default). [ALL] "All of": it has every
 * one (at most [TypeFilter.MAX_ALL_TYPES] picks). [ONLY] "Only": it has no type outside the picks (Fire = pure Fire; Fire + Flying = pure
 * Fire, pure Flying and Fire/Flying).
 */
enum class TypeMatch(val label: String) { ANY("Any of"), ALL("All of"), ONLY("Only") }

/**
 * PLAN 192 (192A + M1, X7, X8): the one type filter of the Box and the Pokédex, pure Kotlin. No picks = everything shows, in every mode.
 * It is its own state, independent of the Box "Show" filter ([BoxFilter]), so "Water + Can evolve now" works. Each screen remembers its
 * own ([encode] / [decode], see [SortPrefs.loadType]).
 */
data class TypeFilter(val types: Set<Type> = emptySet(), val match: TypeMatch = TypeMatch.ANY) {
    /** Any type picked (a mode alone filters nothing). */
    val active: Boolean get() = types.isNotEmpty()

    /** True when a species (or Pokémon) with [speciesTypes] shows under this filter. */
    fun matches(speciesTypes: Collection<Type>): Boolean = when {
        types.isEmpty() -> true
        match == TypeMatch.ANY -> speciesTypes.any { it in types }
        match == TypeMatch.ALL -> types.all { it in speciesTypes }
        else -> speciesTypes.isNotEmpty() && speciesTypes.all { it in types }
    }

    /** This filter with [t] picked or un-picked; null = refused (All of already has [MAX_ALL_TYPES] picks and [t] is a new one: the caller toasts [FULL]). */
    fun toggle(t: Type): TypeFilter? = when {
        t in types -> copy(types = types - t)
        match == TypeMatch.ALL && types.size >= MAX_ALL_TYPES -> null
        else -> copy(types = types + t)
    }

    /** This filter with the match mode [m]; switching to All of with more than [MAX_ALL_TYPES] picked keeps the ones picked last. */
    fun withMatch(m: TypeMatch): TypeFilter =
        copy(match = m, types = if (m == TypeMatch.ALL && types.size > MAX_ALL_TYPES) types.toList().takeLast(MAX_ALL_TYPES).toSet() else types)

    /** M1: the one plain line under the mode switch; it changes with the mode and names the picks (in pick order). */
    fun sentence(): String {
        if (types.isEmpty()) return "No type picked: every type shows."
        val n = types.map { it.label }
        return when (match) {
            TypeMatch.ANY -> "Has at least one of: ${n.joinToString(", ")}."
            TypeMatch.ALL -> when (n.size) {
                1 -> "Has ${n[0]}."
                2 -> "Has both ${n[0]} and ${n[1]}."
                else -> "Has all of: ${n.joinToString(", ")}."
            }
            TypeMatch.ONLY -> when (n.size) {
                1 -> "Pure ${n[0]} only."
                2 -> "No type outside ${n[0]} + ${n[1]}: pure ${n[0]}, pure ${n[1]} and ${n[0]}/${n[1]} stay."
                else -> "No type outside ${n.joinToString(" + ")}."
            }
        }
    }

    /** The saved form: "ONLY:FIRE,FLYING" (mode, a colon, the picks in pick order; no picks = "ANY:"). */
    fun encode(): String = "${match.name}:${types.joinToString(",") { it.name }}"

    companion object {
        /** All of takes at most this many picked types (a species has at most two). */
        const val MAX_ALL_TYPES = 2

        /** The toast when All of refuses a third type. */
        const val FULL = "All of: pick up to $MAX_ALL_TYPES types"

        /** The filter saved by [encode]; null, unknown or garbled = nothing picked, Any of. */
        fun decode(s: String?): TypeFilter {
            val parts = s?.split(':') ?: return TypeFilter()
            if (parts.size != 2) return TypeFilter()
            val match = TypeMatch.entries.firstOrNull { it.name == parts[0] } ?: return TypeFilter()
            val picks = if (parts[1].isEmpty()) emptyList<Type>() else parts[1].split(',').map { n -> Type.entries.firstOrNull { it.name == n } ?: return TypeFilter() }
            return TypeFilter(picks.toCollection(LinkedHashSet()), TypeMatch.ANY).withMatch(match)
        }
    }
}
