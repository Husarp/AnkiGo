package com.husarp.ankigo.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.husarp.ankigo.R

/** The same three-column, square inventory tiles in the Bag and over battle. */
internal object BagTiles {
    fun tile(ctx: Context, icon: View, name: String, note: String, count: String): View = SquareFrame(ctx, 1f).apply {
        background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 12, ctx)
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(Ui.dp(ctx, 3), Ui.dp(ctx, 10), Ui.dp(ctx, 3), Ui.dp(ctx, 3))
            addView(icon, LinearLayout.LayoutParams(Ui.dp(ctx, 40), 0, 1f))
            addView(Ui.text(ctx, 11f, 900, R.color.ag_text).apply { text = name; gravity = Gravity.CENTER; maxLines = 2 })
            addView(Ui.text(ctx, 10f, 700, R.color.ag_sub0).apply { text = note; gravity = Gravity.CENTER; maxLines = 2 })
        }
        addView(body, FrameLayout.LayoutParams(-1, -1))
        addView(Ui.text(ctx, 10f, 900, R.color.ag_sub0).apply { text = count; setPadding(0, 2, Ui.dp(ctx, 6), 0) }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END))
        contentDescription = "$name, $note, $count"
    }

    fun rows(into: LinearLayout, tiles: List<View>) {
        tiles.chunked(3).forEach { chunk ->
            into.addView(LinearLayout(into.context).apply {
                repeat(3) { i -> addView(chunk.getOrNull(i) ?: View(context), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(Ui.dp(context, 3), Ui.dp(context, 3), Ui.dp(context, 3), Ui.dp(context, 3)) }) }
            }, LinearLayout.LayoutParams(-1, -2))
        }
    }
}
