package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Stat
import com.husarp.ankigo.game.Tuning

// TICKET R2-10: the texts of the "tap a stat name" popup (Details, Pokédex entry, Compare). Pure, unit-tested.

/** One explanation: [title] is the full name, [text] one or two lines about what it does in AnkiGo's battles. */
data class StatInfoText(val title: String, val text: String)

object StatInfo {
    /** Shown at the top of the stats blocks. */
    const val HINT = "Tap a stat name for what it means"

    fun of(s: Stat): StatInfoText = when (s) {
        Stat.HP -> StatInfoText("HP (Hit points)", "How much damage it can take before it faints. It is a number, never a percent.")
        Stat.ATK -> StatInfoText("Attack", "Power of physical moves. The target's Defense lowers the damage.")
        Stat.DEF -> StatInfoText("Defense", "Cuts the damage of physical moves against it.")
        Stat.SPA -> StatInfoText("Special Attack", "Power of special moves. The target's Sp. Def lowers the damage.")
        Stat.SPD -> StatInfoText("Special Defense", "Cuts the damage of special moves against it.")
        Stat.SPE -> StatInfoText("Speed", "Who attacks first in a round. Paralysis halves it.")
    }

    val TOTAL = StatInfoText("Total", "All six stats added up. A quick way to compare overall strength.")
    val IV = StatInfoText("IV (Individual value)",
        "Hidden bonus from 0 to 31 per stat, set when the Pokémon is found and never changes. Higher means a bit more of that stat.")
    val EV = StatInfoText("EV (Effort value)",
        "Grows with every win, defeat or catch (Power items add more). Up to ${Tuning.EV_STAT_CAP} per stat and ${Tuning.EV_TOTAL_CAP} in all; every 4 EV is worth about 1 stat point at Lv 100.")
    val FRIENDSHIP = StatInfoText("Friendship",
        "Grows by ${Tuning.FRIENDSHIP_GAIN.first} to ${Tuning.FRIENDSHIP_GAIN.last} with each win you make while it is your main (a Soothe Bell makes it ×${Tuning.SOOTHE_BELL}), up to ${Tuning.FRIENDSHIP_MAX}. " +
            "Some Pokémon evolve once it is high enough, usually ${Tuning.DEFAULT_MIN_FRIENDSHIP}. It also makes Return stronger and Frustration weaker.")
    val NATURE = StatInfoText("Nature", "Raises one stat by 10 % and lowers another by 10 %. Five natures change nothing. The arrows mark the two stats.")
    val NATURE_UP = StatInfoText("▲ Raised stat", "This nature gives this stat +10 %.")
    val NATURE_DOWN = StatInfoText("▼ Lowered stat", "This nature gives this stat −10 %.")
    val LEVEL = StatInfoText("Level", "Stats grow with level. Defeating enemies and Anki reviews give XP.")

    /** Compare row keys ("hp", "base_atk", "total", "ivs", "nature", "level"...) to their text, null when none. */
    fun forKey(key: String): StatInfoText? {
        val k = key.removePrefix("base_")
        Stat.entries.firstOrNull { it.name.lowercase() == k }?.let { return of(it) }
        return when (key) {
            "total", "base_total" -> TOTAL
            "ivs" -> IV
            "nature" -> NATURE
            "level" -> LEVEL
            else -> null
        }
    }

    /** All texts, for the tests. */
    fun all(): List<StatInfoText> = Stat.entries.map(::of) + listOf(TOTAL, IV, EV, FRIENDSHIP, NATURE, NATURE_UP, NATURE_DOWN, LEVEL)
}
