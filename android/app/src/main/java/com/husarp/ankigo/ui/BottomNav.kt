package com.husarp.ankigo.ui

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.view.Gravity
import android.view.View
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Shop

/**
 * The 5-tab bottom bar (mockup `.bnav`): Study (open book) / Pokémon (Poké Ball outline) / Shop / Stats / Settings. The Pokémon tab holds Team, Box
 * and Pokédex as sections; the Stats tab holds Statistics and Achievements; the Shop is a tab of its own (R10 W11) and its icon carries a
 * small red dot while the day's stock hasn't been looked at ([Shop.unseen]). The selected tab is in the accent colour
 * with its icon on a 20 % accent pill. Tapping another tab opens its screen through [Nav] (brought to front, no animation)
 * and closes the current one unless that is the home screen ([isHome]), so Back from any tab ends up on the home screen.
 * Tapping the current tab goes back to that tab's top page ([reselect], PLAN 218).
 * The home screen is Study (and the full-screen battle, its second view, which has no bar).
 */
object BottomNav {
    enum class Tab(val label: String, @DrawableRes val icon: Int, val dest: Dest) {
        STUDY("Study", R.drawable.ic_study, Dest.STUDY),
        POKEMON("Pokémon", R.drawable.ic_ball, Dest.TEAM),
        SHOP("Shop", R.drawable.ic_shop, Dest.SHOP),
        STATS("Stats", R.drawable.ic_stats, Dest.STATS),
        SETTINGS("Settings", R.drawable.ic_settings, Dest.SETTINGS),
    }

    /** The tab a destination lives under (the bar highlights it). The battle is the second view of Study. */
    fun tabOf(dest: Dest): Tab = when (dest) {
        Dest.STUDY, Dest.BATTLE -> Tab.STUDY
        Dest.TEAM, Dest.BOX, Dest.DEX -> Tab.POKEMON
        Dest.SHOP -> Tab.SHOP
        Dest.STATS, Dest.ACHIEVEMENTS -> Tab.STATS
        Dest.SETTINGS -> Tab.SETTINGS
    }

    /** The screen Back ends on: a tab switch never closes it. The home screen: Study with the battle. */
    fun isHome(activity: Activity): Boolean = activity is GameActivity

    /** True when [activity] already is the screen [tab] opens, so a tap on the tab changes nothing. */
    fun isHere(activity: Activity, tab: Tab): Boolean = Nav.target(tab.dest).screen == activity.javaClass

    /** PLAN 218: a tap on the tab the screen already shows. A [PageActivity] asks its page; the home screen scrolls the Study deck list to the top. */
    fun reselect(activity: Activity) {
        when (activity) {
            is PageActivity -> activity.reselectTab()
            is GameActivity -> activity.home.scrollToTop()
        }
    }

    /** The red dot of the Shop tab: shown when the Shop tab wasn't opened on game day [today] (null / empty = no game yet: no dot). Pure; the bar feeds it the saved day. */
    fun dotShown(lastSeenDay: String?, today: String?): Boolean = !today.isNullOrEmpty() && Shop.unseen(lastSeenDay, today)

    /** The TalkBack text of [tab]: its label, with "new stock" while the Shop's dot is on. */
    fun describe(tab: Tab, dot: Boolean): String = if (tab == Tab.SHOP && dot) "${tab.label}, new stock" else tab.label

    /** Builds the bar for [activity], with [current] highlighted. Add it at the bottom of the screen. */
    fun create(activity: Activity, current: Tab): View {
        val col = Ui.c(activity, R.color.ag_mauve)
        val idle = Ui.c(activity, R.color.ag_sub0)
        val bar = NavBar(activity)
        val labels = ArrayList<TextView>()
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(Ui.dp(activity, 2), Ui.dp(activity, 6), Ui.dp(activity, 2), Ui.dp(activity, 2)) // 5 tabs: thin side padding keeps the labels on one line at 320 dp
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
                labels += this
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
                setOnClickListener {
                    if (!isHere(activity, tab)) { com.husarp.ankigo.sound.GameSound.ui(activity, com.husarp.ankigo.sound.SoundId.TAB); open(activity, tab) }
                    else reselect(activity) // PLAN 218: no tab sound, nothing opens: the screen goes back to its top page
                }
            }
            if (tab == Tab.SHOP && !on) { // the Shop screen itself shows no dot: opening it is what clears it
                val dot = dotView(activity)
                pillBox.addView(dot, dotParams(activity))
                bar.shop(item, dot)
            }
            row.addView(item)
        }
        fitLabels(activity, labels)
        bar.orientation = LinearLayout.VERTICAL
        bar.setBackgroundColor(Ui.c(activity, R.color.ag_mantle))
        bar.addView(View(activity).apply { setBackgroundColor(Ui.c(activity, R.color.ag_s0)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 1)))
        bar.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        bar.refresh()
        return bar
    }

    /**
     * The one text size of the five labels: [maxPx] while the widest label fits its tab, else smaller so that it fits (all labels the same,
     * so they don't look uneven), but never under [minPx]. Plain arithmetic, tested on the JVM: [widthAtMax] is the widest label's width at [maxPx].
     */
    fun labelSizePx(tabWidthPx: Float, maxPx: Float, minPx: Float, widthAtMax: Float): Float =
        if (widthAtMax <= tabWidthPx || widthAtMax <= 0f) maxPx else (maxPx * tabWidthPx / widthAtMax).coerceIn(minOf(minPx, maxPx), maxPx)

    /**
     * Five tabs share the width, so at a big text size ("Pokémon", "Settings" at font 2.0 on 320 dp) a label would not fit on its one line:
     * all labels shrink together until the widest fits, but never below 10 dp (a label's size at the normal text size). At the normal size nothing changes.
     */
    private fun fitLabels(activity: Activity, labels: List<TextView>) {
        val first = labels.firstOrNull() ?: return
        val d = activity.resources.displayMetrics.density
        val tabPx = (activity.resources.configuration.screenWidthDp - 4f) * d / Tab.entries.size - 4f * d // the row's side padding, and a little air between neighbours
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = first.typeface; textSize = first.textSize }
        val widest = labels.maxOf { paint.measureText(it.text.toString()) }
        val size = labelSizePx(tabPx, first.textSize, 10f * d, widest)
        if (size != first.textSize) for (l in labels) l.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
    }

    /** The dot: an 8 dp red disc inside a 2 dp ring in the bar's colour (so it reads on the icon pill), 12 dp in all. */
    private fun dotView(ctx: Context): View = object : View(ctx) {
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.c(ctx, R.color.ag_mantle) }
        private val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.c(ctx, R.color.ag_red) }

        override fun onDraw(canvas: Canvas) {
            val r = minOf(width, height) / 2f
            canvas.drawCircle(width / 2f, height / 2f, r, ring)
            canvas.drawCircle(width / 2f, height / 2f, r - Ui.dp(ctx, 2f), disc)
        }
    }.apply { visibility = View.GONE; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }

    private fun dotParams(ctx: Context) = FrameLayout.LayoutParams(Ui.dp(ctx, 12), Ui.dp(ctx, 12), Gravity.TOP or Gravity.END).also { it.rightMargin = Ui.dp(ctx, 2) }

    private fun open(activity: Activity, to: Tab) {
        Nav.open(activity, to.dest)
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
        if (!isHome(activity)) activity.finish()
    }

    /**
     * The bar view: owns the Shop tab's dot. It is worked out when the bar is built, when the game changes (a new game day comes in through
     * GameRepo), and when the window comes back (the Shop tab stored today's day while another screen was in front).
     */
    private class NavBar(private val activity: Activity) : LinearLayout(activity) {
        private var shopItem: View? = null
        private var dot: View? = null
        private val onGame: () -> Unit = { refresh() }

        fun shop(item: View, dotView: View) { shopItem = item; dot = dotView }

        fun refresh() {
            val d = dot ?: return
            val on = dotShown(AppPrefs.shopSeenDay(activity), GameRepo.state?.day)
            d.visibility = if (on) View.VISIBLE else View.GONE
            shopItem?.contentDescription = describe(Tab.SHOP, on)
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            GameRepo.addListener(onGame)
            refresh()
        }

        override fun onDetachedFromWindow() {
            GameRepo.removeListener(onGame)
            super.onDetachedFromWindow()
        }

        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            super.onWindowFocusChanged(hasWindowFocus)
            if (hasWindowFocus) refresh()
        }

        override fun onWindowVisibilityChanged(visibility: Int) {
            super.onWindowVisibilityChanged(visibility)
            if (visibility == View.VISIBLE) refresh()
        }
    }
}
