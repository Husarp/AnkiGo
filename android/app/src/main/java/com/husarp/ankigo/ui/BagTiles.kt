package com.husarp.ankigo.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.husarp.ankigo.R

/** Inventory tiles: roomy two-column Shop Bag cards, and the existing compact battle tiles. */
internal object BagTiles {
    fun tile(ctx: Context, icon: View, name: String, note: String, count: String, large: Boolean = false): View = (if (large) FrameLayout(ctx) else SquareFrame(ctx, 1f)).apply {
        background = Ui.rounded(Ui.c(ctx, if (large) R.color.ag_card else R.color.ag_mantle), 12, ctx)
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(Ui.dp(ctx, 3), Ui.dp(ctx, if (count.isEmpty()) 10f else 26f * ctx.resources.configuration.fontScale).toInt(), Ui.dp(ctx, 3), Ui.dp(ctx, 3))
            addView(icon, if (large) LinearLayout.LayoutParams(Ui.dp(ctx, 80), Ui.dp(ctx, 80)) else LinearLayout.LayoutParams(Ui.dp(ctx, 40), 0, 1f))
            addView(Ui.text(ctx, if (large) 13f else 11f, 900, R.color.ag_text).apply { text = name; gravity = Gravity.CENTER; if (!large) maxLines = 2 })
            addView(Ui.text(ctx, 10f, 700, R.color.ag_sub0).apply { text = note; gravity = Gravity.CENTER; if (!large) maxLines = 2 })
        }
        addView(body, FrameLayout.LayoutParams(-1, if (large) -2 else -1))
        addView(Ui.text(ctx, if (large) 18f else 16f, 900, R.color.ag_text).apply { text = count; setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 2), Ui.dp(ctx, 6), 0) }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END))
        contentDescription = "$name, $note, $count"
    }

    fun rows(into: LinearLayout, tiles: List<View>, columns: Int = 3) {
        tiles.chunked(columns).forEach { chunk ->
            into.addView(LinearLayout(into.context).apply {
                repeat(columns) { i -> addView(chunk.getOrNull(i) ?: View(context), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(Ui.dp(context, 3), Ui.dp(context, 3), Ui.dp(context, 3), Ui.dp(context, 3)) }) }
            }, LinearLayout.LayoutParams(-1, -2))
        }
    }
}
