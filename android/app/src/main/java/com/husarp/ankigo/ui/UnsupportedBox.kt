package com.husarp.ankigo.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.husarp.ankigo.R
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.UnsupportedMon

/**
 * The Box section "Not supported yet" (PLAN 67): Ankimon Pokémon AnkiGo can't use yet, greyed, with a placeholder tile and the
 * name and level from the save. They can't battle, join the team, be compared, released or traded; a tap explains why.
 * No Pokémon pictures here (the species isn't known), so the tile is a "?".
 */
object UnsupportedBox {
    private const val SHOWN = 30

    /** A view that is empty (GONE) when [state] has none. Call again after every change to refill it. */
    fun bind(box: LinearLayout, state: GameState) {
        val ctx = box.context
        box.removeAllViews()
        val list = state.unsupported
        box.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        if (list.isEmpty()) return
        box.addView(Bits.sect(ctx, "Not supported yet", "${list.size} Pokémon"))
        box.addView(Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply {
            text = "AnkiGo doesn't know these yet. They stay safe and come back to your Box when it does."
            setPadding(Ui.dp(ctx, 4), 0, Ui.dp(ctx, 4), Ui.dp(ctx, 8))
        })
        for (u in list.take(SHOWN)) box.addView(row(ctx, u), LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(ctx, 6) })
        if (list.size > SHOWN) {
            box.addView(Ui.text(ctx, 12f, 700, R.color.ag_sub0).apply {
                text = "and ${list.size - SHOWN} more"
                setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 2), 0, Ui.dp(ctx, 8))
            })
        }
    }

    private fun row(ctx: Context, u: UnsupportedMon): View {
        val tile = Ui.text(ctx, 18f, 900, R.color.ag_sub0).apply {
            text = "?"
            gravity = Gravity.CENTER
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 10, ctx)
        }
        val shown = u.nickname?.takeIf { it.isNotBlank() } ?: u.name
        val title = Ui.text(ctx, 14f, 800, R.color.ag_text).apply { text = "${shown.replaceFirstChar { it.uppercase() }} · Lv ${u.level}"; maxLines = 1 }
        val sub = Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { text = "Not supported yet"; maxLines = 1 }
        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(title); addView(sub)
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 10, ctx)
            alpha = 0.6f // greyed
            val pad = Ui.dp(ctx, 8)
            setPadding(pad, pad, pad, pad)
            addView(tile, LinearLayout.LayoutParams(Ui.dp(ctx, 44), Ui.dp(ctx, 44)))
            addView(texts, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 10) })
            minimumHeight = Ui.dp(ctx, 48)
            setOnClickListener { explain(ctx, u) }
        }
    }

    private fun explain(ctx: Context, u: UnsupportedMon) {
        val a = Ui.findActivity(ctx)
        if (a == null || a.isFinishing || a.isDestroyed) return
        Pickers.show(
            AppDialog.Builder(ctx).setTitle(u.name.replaceFirstChar { it.uppercase() } + " · Lv " + u.level)
                .setMessage(
                    "AnkiGo doesn't support this Pokémon yet (${u.reason}).\n\n" +
                        "It can't battle, join your team or be released for now. It is kept safe, and when you export back to Ankimon " +
                        "it goes back unchanged. When AnkiGo learns it, it turns into a normal Pokémon on its own.",
                )
                .setPositiveButton("OK", null).create(),
        )
    }
}
