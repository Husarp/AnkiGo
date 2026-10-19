package com.husarp.ankigo.ui

// R10-G (B7, B12): the plain-words texts of the Compare "Rows" sheet and the tap-to-sort cycle. Pure Kotlin (no android.*): unit-tested on the JVM.

/** What each Compare row is: its full name and ONE short sentence, for the Rows sheet. */
object CompareRowText {
    /** Every row key [CompareTable.build] makes. */
    val KEYS: List<String> = listOf(
        "level", "types",
        "hp", "atk", "def", "spa", "spd", "spe", "total",
        "base_hp", "base_atk", "base_def", "base_spa", "base_spd", "base_spe", "base_total",
        "nature", "ivs", "moves", "weak", "resist", "immune", "evo", "becomes",
    )

    /** The full name and the one-sentence explanation of row [key]; a key nobody knows says its [label] and nothing more. */
    fun of(key: String, label: String): Pair<String, String> = when (key) {
        "level" -> "Level" to "How far it has grown; its stats rise with it."
        "types" -> "Types" to "Its type or types; they decide what hits it hard."
        "hp" -> "Hit points" to "How much damage it takes before it faints."
        "atk" -> "Attack" to "The power of its physical moves."
        "def" -> "Defense" to "How well it shrugs off physical moves."
        "spa" -> "Special Attack" to "The power of its special moves."
        "spd" -> "Special Defense" to "How well it shrugs off special moves."
        "spe" -> "Speed" to "Who attacks first; higher is faster."
        "total" -> "Total" to "All six stats added up; its overall strength."
        "base_hp" -> "Base hit points" to "Its hit points before level, IVs and EVs."
        "base_atk" -> "Base Attack" to "Its physical power before level, IVs and EVs."
        "base_def" -> "Base Defense" to "Its physical toughness before level, IVs and EVs."
        "base_spa" -> "Base Special Attack" to "Its special power before level, IVs and EVs."
        "base_spd" -> "Base Special Defense" to "Its special toughness before level, IVs and EVs."
        "base_spe" -> "Base Speed" to "Its speed before level, IVs and EVs."
        "base_total" -> "Base total" to "All six base stats added up; the species' strength."
        "nature" -> "Nature" to "Raises one stat by 10 % and lowers another."
        "ivs" -> "IVs (Individual values)" to "Hidden bonus per stat, fixed when it was found."
        "moves" -> "Moves" to "The attacks it knows now, with their type."
        "weak" -> "Weak against" to "Attack types that hit it for double damage or more."
        "resist" -> "Resists" to "Attack types that hit it for half damage or less."
        "immune" -> "Immune to" to "Attack types that cannot hurt it at all."
        "evo" -> "Evolution line" to "The family it belongs to, in the order it evolves."
        "becomes" -> "Becomes" to "The last form it can evolve into."
        else -> label to ""
    }
}

/** B12: tapping a stat's label sorts the columns: high to low, then low to high, then no sort; another stat starts at high to low. */
object CompareSortCycle {
    /** The sort after a tap on [tapped], given the sort now ([key], [desc]): (key or null for no sort, descending). */
    fun next(key: String?, desc: Boolean, tapped: String): Pair<String?, Boolean> = when {
        key != tapped -> tapped to true
        desc -> tapped to false
        else -> null to true
    }

    /** What a TalkBack user hears a tap on the label will do now. */
    fun tapSays(key: String?, desc: Boolean, tapped: String): String = when {
        key != tapped -> "Tap to sort high to low."
        desc -> "Tap to sort low to high."
        else -> "Tap to clear the sort."
    }
}
