package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import com.husarp.ankigo.game.Matchup

/** Unknown entries carry neither a type label nor a multiplier, including in accessibility text. */
internal object MatchupTiles {
    fun build(ctx: Context, entries: List<Matchup>, unknownCount: Int): View = FlowRow(ctx, Ui.dp(ctx, 5), centered = false, lineGapPx = Ui.dp(ctx, 5)).apply {
        for (entry in entries) addView(Ui.typeChip(ctx, entry.type).apply { text = "${entry.type.label} ${entry.label}" })
        repeat(unknownCount) {
            addView(Ui.text(ctx, 12f, 800, com.husarp.ankigo.R.color.ag_text).apply {
                text = "???"
                contentDescription = "Matchup not yet seen in battle"
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = Ui.rounded(0xFF303342.toInt(), 8, ctx)
                setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 8))
            })
        }
    }
}
