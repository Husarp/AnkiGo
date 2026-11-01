package com.husarp.ankigo.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.AchievementTable

/** Read progress is independent for each achievement; later tiers become unread again. */
internal object AchievementNotice {
    private fun progress(id: String): Int {
        val st = GameRepo.state ?: return 0
        val row = AchievementTable.row(id) ?: return 0
        return row.ankimonBadge?.let { if (it in st.badges) 1 else 0 } ?: (st.achTiers[id]?.size ?: 0)
    }
    fun unseen(ctx: Context, id: String): Boolean = progress(id) > ctx.getSharedPreferences("look", 0).getInt("achievementSeen:$id", 0)
    fun unseen(ctx: Context): Boolean = AchievementTable.ROWS.any { unseen(ctx, it.id) }
    fun seen(ctx: Context, id: String) { ctx.getSharedPreferences("look", 0).edit().putInt("achievementSeen:$id", progress(id)).apply() }
    fun seen(ctx: Context) { AchievementTable.ROWS.forEach { seen(ctx, it.id) } }
    /** A foreground overlay keeps the centred content and touch target unchanged. */
    fun corner(view: View) {
        val ctx = view.context
        view.foreground = android.graphics.drawable.LayerDrawable(arrayOf(Ui.rounded(Ui.c(ctx, R.color.ag_red), 4, ctx))).apply {
            setLayerSize(0, Ui.dp(ctx, 8), Ui.dp(ctx, 8))
            setLayerGravity(0, android.view.Gravity.TOP or android.view.Gravity.RIGHT)
            setLayerInset(0, 0, Ui.dp(ctx, 8), Ui.dp(ctx, 8), 0)
        }
    }
    fun dot(ctx: Context): View = View(ctx).apply {
        contentDescription = "New achievement"
        background = Ui.rounded(Ui.c(ctx, R.color.ag_red), 4, ctx)
        layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 8), Ui.dp(ctx, 8)).also { it.marginStart = Ui.dp(ctx, 5) }
    }
}
