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
            "Paralysed: ${pct(Tuning.PARA_SKIP)} chance it can't move, and its speed is halved."),
        Info("brn", "BRN", "Burned",
            "Burned: loses 1/16 of its HP each round, and its physical attacks do half damage. Some abilities (Magic Guard, Guts) change this."),
        Info("psn", "PSN", "Poisoned",
            "Poisoned: loses 1/8 of its HP each round. Some abilities (Magic Guard) stop this."),
        Info("tox", "PSN", "Badly poisoned",
            "Badly poisoned: loses more each round (1/16, 2/16, 3/16 ... up to 15/16 of its HP). Some abilities (Magic Guard) stop this."),
        Info("slp", "SLP", "Asleep",
            "Asleep: can't move, but has a ${pct(Tuning.WAKE_CHANCE)} chance to wake up each time it tries. After Rest it sleeps through exactly ${SideMoves.REST_SLEEP} tries and wakes up on the next one. An Uproar wakes it up."),
        Info("frz", "FRZ", "Frozen",
            "Frozen: can't move, ${pct(Tuning.FREEZE_THAW)} chance to thaw each time it tries; a Fire move that hits it thaws it at once."),
    )

    /** Info for an engine status, or null when there is none to show. */
    fun of(status: String?): Info? = all.firstOrNull { it.status == status }
}
