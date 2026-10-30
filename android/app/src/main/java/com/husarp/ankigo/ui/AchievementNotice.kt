package com.husarp.ankigo.ui

import android.content.Context
import com.husarp.ankigo.GameRepo

/** The Stats dot survives notification mode changes and is cleared by viewing achievements. */
internal object AchievementNotice {
    private fun count(): Int = GameRepo.state?.let { st -> st.achTiers.values.sumOf { it.size } + st.badges.size } ?: 0
    fun unseen(ctx: Context): Boolean = count() > ctx.getSharedPreferences("look", 0).getInt("achievementsSeen", 0)
    fun seen(ctx: Context) { ctx.getSharedPreferences("look", 0).edit().putInt("achievementsSeen", count()).apply() }
}
