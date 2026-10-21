package com.husarp.ankigo.debug

import com.husarp.ankigo.game.Settings

/** R13 Part E: the game settings as key -> value for the debug log ("setting dailyAverage: 100 -> 150"), and the share header. Pure. */
object SettingsText {
    fun of(s: Settings): Map<String, String> = linkedMapOf(
        "gens" to s.gens.sorted().joinToString(","), "autoMode" to "${s.autoMode}", "autoReleaseDuplicates" to "${s.autoReleaseDuplicates}",
        "protectLegendary" to "${s.protectLegendary}", "protectMythical" to "${s.protectMythical}", "protectUltra" to "${s.protectUltra}",
        "protectStarter" to "${s.protectStarter}", "protectRegional" to "${s.protectRegional}", "wishlist" to "${s.wishlist.size} species",
        "dailyAverage" to "${s.dailyAverage}", "goalByWeekday" to (s.goalByWeekday?.joinToString(",") ?: "same every day"),
        "dayStartHour" to "${s.dayStartHour}", "xpShare" to if (s.xpShareUid == null) "off" else "on", "fasterStart" to "${s.fasterStart}",
        "moveChoice" to s.moveChoice.name,
    )

    /** "key: old -> new" for each value that differs. */
    fun diff(before: Map<String, String>, after: Map<String, String>): List<String> =
        (before.keys + after.keys).distinct().filter { before[it] != after[it] }.map { "$it: ${before[it]} -> ${after[it]}" }

    /** One line for the share header. */
    fun line(s: Settings): String = of(s).entries.joinToString(", ") { "${it.key}=${it.value}" }
}
