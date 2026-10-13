package com.husarp.ankigo.ui

import android.app.Activity
import android.content.Intent
import android.graphics.PorterDuff
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import com.husarp.ankigo.R

/**
 * The 4-tab bottom bar (mockup `.bnav`): Battle / Team / Pokédex / Shop. The selected tab is mauve with its icon on a
 * 20 % mauve pill. Tapping another tab opens its screen (brought to front, no animation) and closes the current one
 * unless that is the Battle screen, so Back from any tab ends up on Battle.
 */
object BottomNav {
    enum class Tab(val label: String, @DrawableRes val icon: Int, val screen: Class<out Activity>) {
        BATTLE("Battle", R.drawable.ic_battle, GameActivity::class.java),
        TEAM("Team", R.drawable.ic_team, TeamActivity::class.java),
        DEX("Pokédex", R.drawable.ic_dex, DexActivity::class.java),
        SHOP("Shop", R.drawable.ic_shop, ShopActivity::class.java),
    }

    /** Builds the bar for [activity], with [current] highlighted. Add it at the bottom of the screen. */
    fun create(activity: Activity, current: Tab): View {
        val col = Ui.c(activity, R.color.ag_mauve)
        val idle = Ui.c(activity, R.color.ag_sub0)
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(Ui.dp(activity, 6), Ui.dp(activity, 6), Ui.dp(activity, 6), Ui.dp(activity, 2))
        }
        for (tab in Tab.entries) {
            val on = tab == current
            val tint = if (on) col else idle
            val icon = ImageView(activity).apply {
                setImageResource(tab.icon)
                setColorFilter(tint, PorterDuff.Mode.SRC_IN)
                layoutParams = FrameLayout.LayoutParams(Ui.dp(activity, 20), Ui.dp(activity, 20), Gravity.CENTER)
            }
            val pillBox = FrameLayout(activity).apply {
                if (on) background = Ui.rounded((col and 0x00FFFFFF) or 0x33000000, 10, activity)
                layoutParams = LinearLayout.LayoutParams(Ui.dp(activity, 46), Ui.dp(activity, 24))
                addView(icon)
            }
            val label = Ui.text(activity, 10f, 800, R.color.ag_sub0).apply {
                text = tab.label
                setTextColor(tint)
                gravity = Gravity.CENTER
                maxLines = 1
            }
            val item = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, Ui.dp(activity, 4), 0, Ui.dp(activity, 4))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(pillBox)
                addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .also { it.topMargin = Ui.dp(activity, 2) })
                contentDescription = tab.label
                setOnClickListener { if (!on) open(activity, current, tab) }
            }
            row.addView(item)
        }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(activity, R.color.ag_mantle))
            addView(View(activity).apply { setBackgroundColor(Ui.c(activity, R.color.ag_s0)) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 1)))
            addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun open(activity: Activity, from: Tab, to: Tab) {
        activity.startActivity(Intent(activity, to.screen).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
        if (from != Tab.BATTLE) activity.finish()
    }
}
