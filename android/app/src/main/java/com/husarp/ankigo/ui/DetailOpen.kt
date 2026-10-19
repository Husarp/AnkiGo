package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.TypeMatchups
import java.util.Locale

// PLAN item 60 (pick 4B): the details are sections that open on tap; AnkiGo remembers which are open. Pure: no android.*, unit-tested.

/** The sections of the details, in screen order. [key] is the saved name (never rename), [open] whether it starts open. */
enum class DetailSection(val key: String, val title: String, val open: Boolean) {
    STATS("stats", "Stats", true),
    MOVES("moves", "Moves", true),
    MATCHUPS("matchups", "Matchups", false),
    TRAINING("training", "Training", false),
    INFO("info", "Info", false),
}

/** Where the open/closed answers are kept (AppPrefs on the phone, a map in the tests). */
interface DetailOpenStore {
    fun get(key: String): Boolean?
    fun set(key: String, open: Boolean)
}

/** Open/closed memory per section: one answer each, shared by every Pokémon, species and the battle's details window. */
class DetailOpen(private val store: DetailOpenStore) {
    fun isOpen(s: DetailSection): Boolean = store.get(s.key) ?: s.open

    /** Flips [s] and keeps the answer; returns the new state. */
    fun toggle(s: DetailSection): Boolean = (!isOpen(s)).also { store.set(s.key, it) }
}

/** Texts of the details sections. */
object DetailFormat {
    /** "0.7 m", or null when the data has no height. */
    fun height(m: Double?): String? = m?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.1f m", it) }

    /** "9.6 kg", or null when the data has no weight. */
    fun weight(kg: Double?): String? = kg?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.1f kg", it) }

    const val IV_MAX_TOTAL = 6 * 31
    const val FRIENDSHIP_MAX = 255

    /** "142 / 186" */
    fun ivTotal(ivs: Stats): String = "${ivs.total} / $IV_MAX_TOTAL"

    /** "HP 28 · Atk 12 · Def 25 ..." ([long]: full stat names; [skipZero]: only the stats above 0). */
    fun perStat(s: Stats, long: Boolean = false, skipZero: Boolean = false): String =
        com.husarp.ankigo.game.Stat.entries.filter { !skipZero || s[it] != 0 }
            .joinToString(" · ") { "${if (long) DetailMath.statName(it) else DetailMath.LABELS[it.ordinal]} ${s[it]}" }

    /** What the Matchups row says when closed: "weak to Rock ×4", "weak to Water, Ground ×2 +1", "no weaknesses". */
    fun matchupSummary(m: TypeMatchups): String {
        val top = m.weakTo.firstOrNull() ?: return "no weaknesses"
        val same = m.weakTo.filter { it.mult == top.mult }
        val names = same.take(2).joinToString(", ") { it.type.label }
        val more = (m.weakTo.size - minOf(2, same.size)).let { if (it > 0) " +$it" else "" }
        return "weak to $names ${top.label}$more"
    }

    /** "Others of its kind: Keen Eye · Gale Wings (hidden)": the abilities of [sp] except [current]; null when there is none. */
    fun otherAbilities(sp: Species, current: String?): String? {
        fun norm(s: String) = s.filter { it.isLetterOrDigit() }.lowercase()
        val own = current?.let(::norm)
        val list = sp.abilities.filter { norm(it) != own }.map { it } +
            listOfNotNull(sp.hiddenAbility?.takeIf { norm(it) != own }?.let { "$it (hidden)" })
        return list.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private val REGIONS = listOf("Kanto", "Johto", "Hoenn", "Sinnoh", "Unova", "Kalos", "Alola", "Galar", "Paldea")

    /** "Gen 3 · Hoenn" (1 Kanto ... 9 Paldea); any other number is just "Gen N". */
    fun generation(gen: Int): String = REGIONS.getOrNull(gen - 1)?.let { "Gen $gen \u00B7 $it" } ?: "Gen $gen"

    /** What the species' learnset row says about when a move is learned: "Lv 12"; level 1 = "Start", level 0 (on evolving) = "Evo". */
    fun learnLevel(level: Int): String = when {
        level <= 0 -> "Evo"
        level == 1 -> "Start"
        else -> "Lv $level"
    }

    /** All abilities of a species (the Pokédex entry): "Static · Lightning Rod (hidden)"; null when none. */
    fun allAbilities(sp: Species): String? =
        (sp.abilities + listOfNotNull(sp.hiddenAbility?.let { "$it (hidden)" })).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
