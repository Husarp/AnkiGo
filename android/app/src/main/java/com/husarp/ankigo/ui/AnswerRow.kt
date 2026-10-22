package com.husarp.ankigo.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.husarp.ankigo.R
import com.husarp.ankigo.study.NewCardOk

/**
 * The study screen's answer buttons (Again / Hard / Good / Easy by the card's button count). PLAN 155: with [ok] the row of a
 * new card is [ OK (twice as wide) | Good | Easy ] (the calm sub-text colour, "again soon" under OK); in the two-row layout
 * ([wide]) OK has the first row to itself. Taken out of StudyActivity so the Paparazzi shots draw the real row.
 */
internal object AnswerRow {
    /**
     * Adds the rows to [into]. [heldEase] != 0 = a tap waits for the predicted card: [markHeld] gets every button and whether it is
     * the pressed one (the OK button is pressed when the held ease is one it replaces). [answer] gets the ease of the tapped button.
     */
    fun fill(
        ctx: Context, into: LinearLayout, buttons: Int, nextTimes: List<String>, ok: Boolean, wide: Boolean, enabled: Boolean,
        heldEase: Int, markHeld: (View, Boolean) -> Unit, answer: (Int) -> Unit,
    ) {
        val list = NewCardOk.row(buttons, ok)
        val splitAt = if (ok && list.size > 2) 1 else 2   // two rows: OK alone, then Good | Easy; the usual rows break after the second button
        var row = StudyUi.row(ctx)
        into.addView(row)
        list.forEachIndexed { i, b ->
            if (wide && i == splitAt) { row = StudyUi.row(ctx); into.addView(row, StudyUi.spaced(ctx, 6)) }
            val color = when (b.label) { "Again" -> R.color.ag_red; "Hard" -> R.color.ag_peach; "Good" -> R.color.ag_green; NewCardOk.LABEL -> R.color.ag_sub0; else -> R.color.ag_blue }
            val view = StudyUi.answer(ctx, b.label, if (b.ok) NewCardOk.HINT else nextTimes.getOrElse(b.ease - 1) { "" }, color, enabled) { answer(b.ease) }
            if (b.ok) view.contentDescription = NewCardOk.DESCRIPTION
            if (heldEase != 0) markHeld(view, if (b.ok) heldEase in 1..NewCardOk.replaces(buttons) else b.ease == heldEase)
            val weight = if (b.ok && !(wide && splitAt == 1)) 2f else 1f   // OK is twice as wide, unless it has a row alone
            row.addView(view, StudyUi.lp(0, Ui.dp(ctx, if (wide) 40 else 48), weight).also { if (row.childCount > 0) it.marginStart = Ui.dp(ctx, 6) })
        }
    }
}
