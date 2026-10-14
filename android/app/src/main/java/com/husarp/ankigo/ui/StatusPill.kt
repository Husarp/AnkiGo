package com.husarp.ankigo.ui

import android.content.Context
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.husarp.ankigo.R

/**
 * The small status label on an info card (BRN PAR SLP PSN FRZ): same shape and size as a type chip (9 sp, 900,
 * rounded), coloured from the `ag_*` tokens so it reads on both palettes. "tox" shows as PSN.
 */
object StatusPill {
    /** The three-letter label of an engine status ("par" "brn" "psn" "tox" "slp" "frz"); null when there is none to show. */
    fun label(status: String?): String? = when (status) {
        "par" -> "PAR"; "brn" -> "BRN"; "psn", "tox" -> "PSN"; "slp" -> "SLP"; "frz" -> "FRZ"
        else -> null
    }

    private fun color(status: String?): Int = when (status) {
        "par" -> R.color.ag_yellow
        "brn" -> R.color.ag_peach
        "psn", "tox" -> R.color.ag_mauve
        "slp" -> R.color.ag_ov
        else -> R.color.ag_sky
    }

    /** A new, hidden pill. */
    fun create(ctx: Context): TextView = Ui.text(ctx, 9f, 900, R.color.ag_text).apply {
        letterSpacing = 0.05f
        maxLines = 1
        setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 1), Ui.dp(ctx, 6), Ui.dp(ctx, 1))
        visibility = android.view.View.GONE
    }

    /** Shows [status] on [pill], or hides the pill when there is none. */
    fun apply(pill: TextView, status: String?) {
        val label = label(status)
        if (label == null) { pill.visibility = android.view.View.GONE; return }
        val ctx = pill.context
        val fill = Ui.c(ctx, color(status))
        pill.text = label
        pill.background = Ui.rounded(fill, 6, ctx)
        // dark or white text, whichever contrasts more with this palette's fill
        val dark = 0xFF1E1E2E.toInt()
        pill.setTextColor(if (ColorUtils.calculateContrast(dark, fill) >= ColorUtils.calculateContrast(0xFFFFFFFF.toInt(), fill)) dark else 0xFFFFFFFF.toInt())
        pill.visibility = android.view.View.VISIBLE
    }
}
