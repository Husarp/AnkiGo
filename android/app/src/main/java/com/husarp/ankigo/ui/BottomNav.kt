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
 * small red dot while the day's stock hasn't been looked at ([Shop.unseen]). The selected tab uses its own palette colour
 * using the chosen tab-bar style. Tapping another tab opens its screen through [Nav] (brought to front, directional content slide)
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
    fun describe(tab: Tab, dot: Boolean): String = if (tab == Tab.SHOP && dot) "${tab.label}, new stock" else if (tab == Tab.STATS && dot) "${tab.label}, new achievements" else tab.label

    /** Builds the bar for [activity], with [current] highlighted. Add it at the bottom of the screen. */
    fun create(activity: Activity, current: Tab): View {
        OpenOnPrefs.setLastTab(activity, current) // R18 263: "Open AnkiGo on: Last tab"
        val style = TabBarStyle.get(activity)
        val col = tone(activity, current)
        val idle = Ui.c(activity, R.color.ag_sub0)
        val bar = NavBar(activity)
        val labels = ArrayList<TextView>()
        val row = HighlightRow(activity, current, style).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(Ui.dp(activity, 2), Ui.dp(activity, 6), Ui.dp(activity, 2), Ui.dp(activity, 2)) // 5 tabs: thin side padding keeps the labels on one line at 320 dp
        }
        for (tab in Tab.entries) {
            val on = tab == current
            val tint = if (on) col else idle
            val icon = ImageView(activity).apply {
                setImageResource(tab.icon)
                setColorFilter(if (Multicolor.enabled(activity)) Multicolor.icon(activity, Multicolor.tab(tab),
                    if (on) Bits.blend(col, Ui.c(activity, R.color.ag_mantle), .20f) else Ui.c(activity, R.color.ag_mantle))
                    else tint, PorterDuff.Mode.SRC_IN)
                layoutParams = FrameLayout.LayoutParams(Ui.dp(activity, if (style == TabBarStyle.E1 && on) 28 else 20), Ui.dp(activity, if (style == TabBarStyle.E1 && on) 28 else 20), Gravity.CENTER)
            }
            val pillBox = FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(Ui.dp(activity, 38), Ui.dp(activity, if (style == TabBarStyle.E1 && on) 28 else 24))
                addView(icon)
            }
            val label = Ui.text(activity, if (style == TabBarStyle.A3 && on) 12f else 10f, if (style == TabBarStyle.A3 && on) 900 else 800, R.color.ag_sub0).apply {
                text = tab.label
                if (style == TabBarStyle.E1 && !on) visibility = View.GONE
                setTextColor(if (on && Multicolor.enabled(activity)) Multicolor.ink(activity, Multicolor.tab(tab),
                    Bits.blend(col, Ui.c(activity, R.color.ag_mantle), .20f)) else tint)
                gravity = Gravity.CENTER
                maxLines = 1
                labels += this
            }
            val item = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = Ui.dp(activity, if (style == TabBarStyle.E1) 56 else 48)
                isSelected = on
                setPadding(0, Ui.dp(activity, 4), 0, Ui.dp(activity, 4))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(pillBox)
                addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .also { it.topMargin = Ui.dp(activity, 2) })
                contentDescription = tab.label
                setOnClickListener {
                    if (!isHere(activity, tab)) { com.husarp.ankigo.sound.GameSound.ui(activity, com.husarp.ankigo.sound.SoundId.TAB); UiMotion.prepareTab(activity, current, tab); row.moveTo(tab) { open(activity, tab) } }
                    else reselect(activity) // PLAN 218: no tab sound, nothing opens: the screen goes back to its top page
                }
            }
            if (tab == Tab.STATS) {
                val achDot = dotView(activity)
                pillBox.addView(achDot, dotParams(activity))
                bar.achievements(item, achDot)
            }
            if (tab == Tab.SHOP && !on) { // the Shop screen itself shows no dot: opening it is what clears it
                val dot = dotView(activity)
                pillBox.addView(dot, dotParams(activity))
                bar.shop(item, dot)
            }
            row.addView(item)
        }
        fitLabels(activity, labels)
        bar.clipChildren = false
        bar.orientation = LinearLayout.VERTICAL
        bar.setBackgroundColor(Ui.c(activity, R.color.ag_mantle))
        bar.addView(View(activity).apply { setBackgroundColor(Ui.c(activity, R.color.ag_s0)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 1)))
        bar.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        bar.refresh()
        return bar
    }

    internal fun tone(ctx: Context, tab: Tab): Int = if (Multicolor.enabled(ctx)) Multicolor.color(ctx, Multicolor.tab(tab)) else ctx.getColor(when (tab) {
        Tab.STUDY -> R.color.ag_blue
        Tab.POKEMON -> R.color.ag_green
        Tab.SHOP -> R.color.ag_peach
        Tab.STATS -> R.color.ag_mauve
        Tab.SETTINGS -> R.color.ag_teal
    })

    /** Destination-owned motion: selection ink is final from the first frame. */
    internal class HighlightRow(ctx: Context, private val current: Tab, private val style: TabBarStyle) : LinearLayout(ctx) {
        internal var position = current.ordinal.toFloat()
            private set
        internal var colour = tone(ctx, current)
            private set
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        internal fun moveTo(tab: Tab, done: () -> Unit) {
            // Navigation commits on this tap; the destination animates its own indicator.
            done()
        }
        internal fun arriveFrom(tab: Tab) {
            if (tab == current) return
            val start = tab.ordinal.toFloat()
            val from = tone(context, tab)
            val duration = when (style) {
                TabBarStyle.A3, TabBarStyle.C1 -> 140L
                TabBarStyle.D3 -> 130L
                TabBarStyle.E1 -> 160L
            }
            UiMotion.run(this, duration, { f ->
                position = if (style == TabBarStyle.C1) start + (current.ordinal - start) * f else current.ordinal.toFloat()
                colour = if (style == TabBarStyle.C1) android.animation.ArgbEvaluator().evaluate(f, from, tone(context, current)) as Int else tone(context, current)
                reveal = f
                val item = getChildAt(current.ordinal) as? LinearLayout
                val label = item?.getChildAt(1)
                val icon = (item?.getChildAt(0) as? FrameLayout)?.getChildAt(0)
                when (style) {
                    TabBarStyle.A3 -> { label?.scaleX = .84f + .16f * f; label?.scaleY = .84f + .16f * f }
                    TabBarStyle.E1 -> {
                        icon?.scaleX = (20f + 8f * f) / 28f; icon?.scaleY = (20f + 8f * f) / 28f
                        label?.alpha = f; label?.translationY = Ui.dp(context, 3f) * (1f - f)
                    }
                    else -> Unit
                }
                invalidate()
            })
        }
        private var reveal = 1f

        override fun dispatchDraw(canvas: Canvas) {
            val cell = (width - paddingLeft - paddingRight) / 5f
            val left = paddingLeft + cell * position
            when (style) {
                TabBarStyle.C1 -> {
                    paint.color = (colour and 0x00ffffff) or 0x29000000
                    canvas.drawRoundRect(left + Ui.dp(context, 2), paddingTop.toFloat(), left + cell - Ui.dp(context, 2),
                        (height - paddingBottom).toFloat(), Ui.dp(context, 6f), Ui.dp(context, 6f), paint)
                }
                TabBarStyle.D3 -> {
                    paint.color = (colour and 0x00ffffff) or (((.35f + .65f * reveal) * 255).toInt() shl 24)
                    val half = (cell / 2f - Ui.dp(context, 4)) * (.15f + .85f * reveal)
                    canvas.drawRoundRect(left + cell / 2f - half, -Ui.dp(context, 1f), left + cell / 2f + half,
                        Ui.dp(context, 2f), Ui.dp(context, 3f), Ui.dp(context, 3f), paint)
                }
                else -> Unit
            }
            super.dispatchDraw(canvas)
        }
        override fun onDetachedFromWindow() { UiMotion.finish(this); super.onDetachedFromWindow() }

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
        OpenOnPrefs.setLastTab(activity, to)
        Nav.open(activity, to.dest)
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
        if (!isHome(activity)) activity.finish()
    }

    /**
     * The bar view: owns the Shop tab's dot. It is worked out when the bar is built, when the game changes (a new game day comes in through
     * GameRepo), and when the window comes back (the Shop tab stored today's day while another screen was in front).
     */
    internal class NavBar(private val activity: Activity) : LinearLayout(activity) {
        private var achItem: View? = null
        private var achDot: View? = null
        fun achievements(item: View, dot: View) { achItem = item; achDot = dot }
        private var shopItem: View? = null
        private var dot: View? = null
        private val onGame: () -> Unit = { refresh() }
        private val onPrefs = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "achievementsSeen" || key?.startsWith("achievementSeen:") == true) refresh()
        }

        fun shop(item: View, dotView: View) { shopItem = item; dot = dotView }

        fun refresh() {
            val unseen = AchievementNotice.unseen(activity)
            achDot?.let { UiMotion.notice(it, unseen) }
            achItem?.contentDescription = describe(Tab.STATS, unseen)
            val d = dot ?: return
            val on = dotShown(AppPrefs.shopSeenDay(activity), GameRepo.state?.day)
            UiMotion.notice(d, on)
            shopItem?.contentDescription = describe(Tab.SHOP, on)
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            GameRepo.addListener(onGame)
            activity.getSharedPreferences("look", 0).registerOnSharedPreferenceChangeListener(onPrefs)
            refresh()
        }

        override fun onDetachedFromWindow() {
            GameRepo.removeListener(onGame)
            activity.getSharedPreferences("look", 0).unregisterOnSharedPreferenceChangeListener(onPrefs)
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
