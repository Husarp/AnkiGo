package com.husarp.ankigo.ui

import android.app.Activity
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
 * The 4-tab bottom bar (mockup `.bnav`): Study (open book) / Pokémon (Poké Ball outline) / Stats / Settings. The Pokémon tab holds Team, Box,
 * Pokédex and Shop as sections; the Stats tab holds Statistics and Achievements. The selected tab is in the accent colour
 * with its icon on a 20 % accent pill. Tapping another tab opens its screen through [Nav] (brought to front, no animation)
 * and closes the current one unless that is the home screen ([isHome]), so Back from any tab ends up on the home screen.
 * The home screen is Study (and the full-screen battle, its second view, which has no bar).
 */
object BottomNav {
    enum class Tab(val label: String, @DrawableRes val icon: Int, val dest: Dest) {
        STUDY("Study", R.drawable.ic_study, Dest.STUDY),
        POKEMON("Pokémon", R.drawable.ic_ball, Dest.TEAM),
        STATS("Stats", R.drawable.ic_stats, Dest.STATS),
        SETTINGS("Settings", R.drawable.ic_settings, Dest.SETTINGS),
    }

    /** The tab a destination lives under (the bar highlights it). The battle is the second view of Study. */
    fun tabOf(dest: Dest): Tab = when (dest) {
        Dest.STUDY, Dest.BATTLE -> Tab.STUDY
        Dest.TEAM, Dest.BOX, Dest.DEX, Dest.SHOP -> Tab.POKEMON
        Dest.STATS, Dest.ACHIEVEMENTS -> Tab.STATS
        Dest.SETTINGS -> Tab.SETTINGS
    }

    /** The screen Back ends on: a tab switch never closes it. The home screen: Study with the battle. */
    fun isHome(activity: Activity): Boolean = activity is GameActivity

    /** True when [activity] already is the screen [tab] opens, so a tap on the tab changes nothing. */
    fun isHere(activity: Activity, tab: Tab): Boolean = Nav.target(tab.dest).screen == activity.javaClass

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
                layoutParams = LinearLayout.LayoutParams(Ui.dp(activity, 38), Ui.dp(activity, 24))
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
                setOnClickListener { if (!isHere(activity, tab)) open(activity, tab) }
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

    private fun open(activity: Activity, to: Tab) {
        Nav.open(activity, to.dest)
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
        if (!isHome(activity)) activity.finish()
    }
}
