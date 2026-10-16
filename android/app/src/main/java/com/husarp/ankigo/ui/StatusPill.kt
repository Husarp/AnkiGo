package com.husarp.ankigo.ui

/**
 * The short text label of an engine status (BRN PAR SLP PSN FRZ). The cards no longer show it (they show a [StatusBadge]
 * under the HP bar); [StatusInfo] keeps it as each status's label. "tox" shows as PSN.
 */
object StatusPill {
    /** The three-letter label of an engine status ("par" "brn" "psn" "tox" "slp" "frz"); null when there is none to show. */
    fun label(status: String?): String? = when (status) {
        "par" -> "PAR"; "brn" -> "BRN"; "psn", "tox" -> "PSN"; "slp" -> "SLP"; "frz" -> "FRZ"
        else -> null
    }
}
