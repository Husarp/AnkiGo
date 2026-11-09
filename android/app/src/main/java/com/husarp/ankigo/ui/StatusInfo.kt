package com.husarp.ankigo.ui

import com.husarp.ankigo.game.SideMoves
import com.husarp.ankigo.game.Tuning

/**
 * What each engine status ("par" "brn" "psn" "tox" "slp" "frz") does in AnkiGo, one line each, numbers taken from
 * game/Tuning.kt and game/BattleEngine.kt (burn 1/16, poison 1/8, toxic n/16 capped at 15/16 of max HP per round).
 * The short label stays in [StatusPill.label]. Confusion and love are no status badges (they show as battle lines and in the guide), so
 * there is none here. R17C-10: the lines follow the new rules (Rest's fixed sleep, Uproar, Magic Guard and the like).
 */
object StatusInfo {
    class Info(val status: String, val label: String, val name: String, val line: String)

    private fun pct(x: Double) = "${Math.round(x * 100)} %"

    val all: List<Info> = listOf(
        Info("par", "PAR", "Paralysed",
            "Paralysed: ${pct(Tuning.PARA_SKIP)} missed moves; Speed halved."),
        Info("brn", "BRN", "Burned",
            "Burned: loses 1/16 of max HP per round; physical damage halved. Magic Guard/Guts can change this."),
        Info("psn", "PSN", "Poisoned",
            "Poisoned: loses 1/8 of max HP per round; Magic Guard prevents this."),
        Info("tox", "PSN", "Badly poisoned",
            "Badly poisoned: loses an extra 1/16 of max HP per round, up to 15/16 of max HP; Magic Guard prevents this."),
        Info("slp", "SLP", "Asleep",
            "Asleep: usually can't move; ${pct(Tuning.WAKE_CHANCE)} wake chance per try. After Rest it sleeps through exactly ${SideMoves.REST_SLEEP} tries. Uproar wakes it."),
        Info("frz", "FRZ", "Frozen",
            "Frozen: can't move; ${pct(Tuning.FREEZE_THAW)} thaw chance each time it tries. Fire hits or thawing moves thaw it."),
    )

    /** Compact battle-card text; detailed guide text retains ability exceptions. */
    fun effect(status: String): String = when (status) {
        "par" -> "${pct(Tuning.PARA_SKIP)} missed moves; Speed halved."
        "brn" -> "Loses 1/16 of max HP per round; physical damage halved."
        "psn" -> "Loses 1/8 of max HP per round."
        "tox" -> "Poison damage rises per round, up to 15/16 of max HP."
        "slp" -> "Usually can't move; Rest lasts ${SideMoves.REST_SLEEP} tries."
        "frz" -> "Can't move; ${pct(Tuning.FREEZE_THAW)} thaw chance per try."
        else -> ""
    }

    /** Info for an engine status, or null when there is none to show. */
    fun of(status: String?): Info? = all.firstOrNull { it.status == status }
}
