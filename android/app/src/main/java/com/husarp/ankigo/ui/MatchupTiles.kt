package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.game.Matchup

/** Unknown entries carry neither a type label nor a multiplier, including in accessibility text. */
internal object MatchupTiles {
    fun build(ctx: Context, entries: List<Matchup>, unknownCount: Int): View = FlowRow(ctx, Ui.dp(ctx, 5), centered = false, lineGapPx = Ui.dp(ctx, 5)).apply {
        for (entry in entries) addView(Ui.typeChip(ctx, entry.type).apply { text = "${entry.type.label} ${entry.label}" })
        repeat(unknownCount) {
            addView(unknown(ctx))
        }
        equalSizes(ctx, (0 until childCount).map { getChildAt(it) as TextView })
    }

    /** Use the same grid cell for known and hidden types, independent of which types were discovered. */
    fun equalSizes(ctx: Context, chips: List<TextView>) {
        val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val samples = Type.entries.map { Ui.typeChip(ctx, it) } + chips
        samples.forEach { it.measure(spec, spec) }
        val width = samples.maxOf { it.measuredWidth }
        val height = samples.maxOf { it.measuredHeight }
        chips.forEach { it.width = width; it.height = height; it.gravity = Gravity.CENTER }
    }

    fun unknown(ctx: Context) = Ui.text(ctx, 10f, 900, com.husarp.ankigo.R.color.ag_text).apply {
        text = "???"
        contentDescription = "Matchup not yet seen in battle"
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        includeFontPadding = false
        maxLines = 1
        background = Ui.rounded(0xFF303342.toInt(), 6, ctx)
        setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 1), Ui.dp(ctx, 6), Ui.dp(ctx, 1))
    }
}
