package com.husarp.ankigo.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.graphics.ColorUtils
import com.husarp.ankigo.Anki
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.Report
import com.husarp.ankigo.game.StudyStats
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.stats.AnkiGoTime
import com.husarp.ankigo.stats.AnkiStats
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.CardState
import com.husarp.ankigo.stats.ChartData
import com.husarp.ankigo.stats.ChartGrain
import com.husarp.ankigo.stats.ChartKind
import com.husarp.ankigo.stats.ChartMetric
import com.husarp.ankigo.stats.ChartPeriod
import com.husarp.ankigo.stats.ChartSeries
import com.husarp.ankigo.stats.CollectionStats
import com.husarp.ankigo.stats.StatsSnapshot
import com.husarp.ankigo.stats.StatsText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

// TICKET T40: the Stats screen (streak, all-time tiles) from AnkiGo's own per-day history.
// TICKET L2-07: the numbers come from AnkiDroid's official API (AnkiStatsStore, cached); the game's own count is the fallback.
// TICKET R3-V1 (pick 1): one chart at a time (metric button + period / deck / grain chips + three key numbers + the tappable
// chart, [StatsChartView]), and a Collection section at the bottom.
// TICKET R4-01 (PLAN 108): the page is the Stats tab: a section switch, Statistics (all of the above) and Achievements.

/** The sections of the Stats tab. */
enum class StatsSection(val label: String, val icon: Int) {
    STATISTICS("Statistics", R.drawable.ic_stats),
    ACHIEVEMENTS("Achievements", R.drawable.ic_star);

    companion object {
        fun of(name: String?): StatsSection = entries.firstOrNull { it.name == name } ?: STATISTICS
    }
}

/**
 * The Stats tab. Statistics: streak hero, the chart with its filters, the AnkiDroid / time / all-time tiles and the Collection.
 * Achievements: [AchievementsView]. A plain open is on Statistics; [Nav] can ask for a section.
 */
class StatsPage(base: Context, host: PageHost, start: StatsSection? = null) : GamePage(base, host) {
    override val tab: BottomNav.Tab? = BottomNav.Tab.STATS

    private lateinit var appBar: AppBar
    private lateinit var switchBox: LinearLayout
    private lateinit var statsScroll: ScrollView
    private lateinit var achievementsBody: View
    private lateinit var content: LinearLayout
    private lateinit var sheetLayer: FrameLayout
    private val statsListener: () -> Unit = { requestRender() }
    private var refreshWanted = false

    // what the chart shows (kept while the page lives); a tap's choice is kept over re-renders
    private var metric = ChartMetric.REVIEWS
    private var period = ChartPeriod.D30
    private var deck: String? = null
    private var grain: ChartGrain? = null
    private var barSel = -1
    private var genSel = -1
    private var heatDay: String? = null
    /** R4-X1 item 1: closes the open chart / calendar info box (set while the chart or calendar is on screen). */
    private val charts = ArrayList<StatsChartView>()
    private var closeHeat: (() -> Unit)? = null
    private var heatRef: View? = null

    /** True when the touch [ev] is on [v]. */
    private fun hits(v: View?, ev: MotionEvent): Boolean {
        if (v == null || !v.isShown) return false
        val at = IntArray(2).also { v.getLocationOnScreen(it) }
        return ev.rawX >= at[0] && ev.rawX < at[0] + v.width && ev.rawY >= at[1] && ev.rawY < at[1] + v.height
    }
    private var sheetOpen = false
    private var section: StatsSection = start ?: StatsSection.STATISTICS
    private val achievements by lazy { AchievementsView(this) }

    /** AnkiDroid's deck names for the deck filter (read once per visit, on a thread; empty until then or when AnkiDroid can't be read). */
    private var decks: List<String> = emptyList()
    private var decksLoading = false

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(top) }
    private fun c(@ColorRes r: Int) = Ui.c(this, r)

    /** Screenshot tests: start with this chart state. */
    internal fun preset(
        metric: ChartMetric = this.metric, period: ChartPeriod = this.period, deck: String? = null, grain: ChartGrain? = null,
        bar: Int = -1, sheet: Boolean = false, heatDay: String? = null, decks: List<String> = this.decks,
        section: StatsSection = this.section,
    ) {
        this.section = section
        this.metric = metric; this.period = period; this.deck = deck; this.grain = grain
        barSel = bar; sheetOpen = sheet; this.heatDay = heatDay; this.decks = decks
    }

    override fun buildBar(): View {
        appBar = Ui.appBar(this, "Stats") // a tab: no back arrow, the title stays "Stats" on both sections
        return appBar
    }

    override fun buildBody(): View {
        switchBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(2), dp(16), dp(28))
        }
        statsScroll = object : ScrollView(this) {
            // a touch anywhere except on the chart / calendar itself (they handle their own taps) closes an open info box (R4-X1 item 1)
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                    for (c in charts) if (c.selected >= 0 && !hits(c, ev)) { c.selected = -1; c.onSelect?.invoke(-1) }
                    if (heatDay != null && !hits(heatRef, ev)) closeHeat?.invoke()
                }
                return super.dispatchTouchEvent(ev)
            }
        }.apply { addView(content) }
        achievementsBody = achievements.build()
        sheetLayer = FrameLayout(this).apply { visibility = View.GONE }
        val frame = FrameLayout(this).apply {
            addView(statsScroll, FrameLayout.LayoutParams(-1, -1))
            addView(achievementsBody, FrameLayout.LayoutParams(-1, -1))
            addView(sheetLayer, FrameLayout.LayoutParams(-1, -1))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(switchBox, LinearLayout.LayoutParams(-1, -2).also { it.setMargins(dp(16), 0, dp(16), dp(4)) })
            addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }

    /** The segmented Statistics / Achievements switch (same look as the Pokémon tab's). */
    private fun drawSwitch() {
        SectionSwitch.draw(this, switchBox, StatsSection.entries.map { SectionSwitch.Seg(it.label, it.icon) }, section.ordinal) { showSection(StatsSection.entries[it]) }
    }

    /** Test hook and Nav: shows [s]. The chart state (metric, period, deck, grain) is kept while the page lives. */
    internal fun showSection(s: StatsSection) {
        if (s == section) return
        section = s
        sheetOpen = false
        achievements.open(null)
        requestRender()
    }

    /** The popup's "See it" (Nav.EXTRA_ACHIEVEMENT): shows Achievements with the sheet of [id] open (R4-V2). */
    internal fun openAchievement(id: String) {
        section = StatsSection.ACHIEVEMENTS
        sheetOpen = false
        achievements.open(id)
        requestRender()
    }

    /** Test hook: the Achievements section's view. */
    internal fun achievementsView(): AchievementsView = achievements

    override fun onShow() {
        super.onShow()
        AnkiStatsStore.cached(this) // the last numbers at once; the render is posted, so it sees them
        AnkiStatsStore.addListener(statsListener)
        refreshWanted = true
        loadDecks()
    }

    override fun onHide() {
        AnkiStatsStore.removeListener(statsListener)
        super.onHide()
    }

    override fun onGone() {
        AnkiStatsStore.removeListener(statsListener)
        super.onGone()
    }

    private fun loadDecks() {
        if (!AnkiStatsStore.enabled || decksLoading) return
        decksLoading = true
        val app = applicationContext
        Thread {
            val names = try {
                Anki.load(app)
                if (Anki.hasPermission(app)) Anki.decks(app).filter { !it.dyn }.map { it.name }.sorted() else emptyList()
            } catch (e: Exception) {
                Report.fail("STATS", "reading the deck list for the chart", e)
                emptyList()
            }
            Handler(Looper.getMainLooper()).post { decksLoading = false; if (alive) { decks = names; requestRender() } }
        }.start()
    }

    override fun render() {
        drawSwitch()
        statsScroll.visibility = if (section == StatsSection.STATISTICS) View.VISIBLE else View.GONE
        achievementsBody.visibility = if (section == StatsSection.ACHIEVEMENTS) View.VISIBLE else View.GONE
        if (section == StatsSection.ACHIEVEMENTS) {
            sheetLayer.visibility = View.GONE
            achievements.render()
            return
        }
        content.removeAllViews()
        charts.clear(); closeHeat = null; heatRef = null
        val today = GameRepo.game?.dayKey() ?: state.day
        if (refreshWanted) { // once per visit, when the game is ready (the store skips a refresh that just finished)
            refreshWanted = false
            AnkiStatsStore.refresh(this, today, state.settings.dayStartHour)
        }
        val s = StudyStats.summary(state, today, WEEKS)
        val snap = AnkiStatsStore.snapshot
        val anki = snap.stats
        val collection = CollectionStats.of(data, state, System.currentTimeMillis())
        val series = ChartData.build(metric, period, deck, snap, state.history, today, grain, goal = s.goal, collection = collection)
        AnkiStatsStore.requestSeries(this, ChartData.seriesKeys(metric, period, deck, today))

        content.addView(streakCard(s))
        content.addView(sectTitle(R.drawable.ic_stats, "Charts", R.color.ag_mauve))
        content.addView(metricButton())
        content.addView(filterChips(series), lp(8))
        val message = StatsChartMath.emptyMessage(series, snap.access, snap.message)
        if (message == null) content.addView(tileRow(StatsChartMath.tiles(series)), lp(8)) // zeros would only mislead while loading
        content.addView(chartCard(series, message, snap, today, barSel) { barSel = it }, lp(8))

        if (anki != null) { // pick 11A: one card per section, a coloured header, the numbers in soft rounded cells, the small print behind the (i)
            val tone = R.color.ag_blue
            val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            body.addView(ankiTiles(anki, today, tone))
            body.addView(note("New ${Bits.fmt(anki.counts.new)} · Learning ${Bits.fmt(anki.counts.learning)} · Relearning ${Bits.fmt(anki.counts.relearning)} · Buried ${Bits.fmt(anki.counts.buried)}"), lp(8))
            body.addView(buttonsCard(anki, tone), lp(8))
            content.addView(sectionCard(R.drawable.ic_book, "AnkiDroid", tone, null,
                "Cards, not reviews: a card answered twice counts once. Retention is an estimate: cards answered in the last 30 days (not new then) that you didn't answer Again.", body), lp(14))
        }
        content.addView(sectionCard(R.drawable.ic_clock, "Study time", R.color.ag_green, null,
            "Counts study in AnkiGo plus answers it timed live in AnkiDroid (split screen, floating). AnkiDroid doesn't share its own study time.",
            timeRow(snap, today, R.color.ag_green)), lp(14))
        val allTime = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        allTime.addView(tiles(s, R.color.ag_yellow))
        allTime.addView(summaryLine(s, anki), lp(10))
        allTime.addView(battleTiles(s, R.color.ag_yellow), lp(10)) // R4-V11
        allTime.addView(note("Damage and misses are counted since 0.13."), lp(6))
        content.addView(sectionCard(R.drawable.ic_star, "All time", R.color.ag_yellow, null, null, allTime), lp(14))
        content.addView(collectionSection(collection, snap, today), lp(14))
        syncSheet()
    }

    // ---- 1. streak ----

    private fun streakCard(s: StudyStats.Summary): View {
        val card = Ui.card(this).apply { setPadding(dp(16), dp(14), dp(16), dp(16)) }
        val streak = s.streak.current
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_flame)
            setColorFilter(Ui.c(this@StatsPage, if (streak > 0) R.color.ag_peach else R.color.ag_ov))
        }, LinearLayout.LayoutParams(dp(34), dp(34)).also { it.marginEnd = dp(10) })

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.text(this, 30f, 900, R.color.ag_text).apply {
            text = SpannableStringBuilder().also { sb ->
                sb.append(streak.toString())
                val at = sb.length
                sb.append(if (streak == 1) " day" else " days")
                sb.setSpan(AbsoluteSizeSpan(16, true), at, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(ForegroundColorSpan(Ui.c(this@StatsPage, R.color.ag_sub1)), at, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            maxLines = 1
        })
        col.addView(Ui.text(this, 11f, 700, R.color.ag_sub0).apply { text = "Daily goal streak" })
        top.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(Ui.chip(this, R.drawable.ic_star, R.color.ag_yellow).also { it.setText("Best ${s.streak.best}") })
        card.addView(top)

        val goalRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val bar = BarView(this)
        bar.setMeaning(BarMeaning.GOAL, minOf(1f, s.reviewsToday.toFloat() / s.goal), false)
        bar.setGoalSparks(GoalTone.flameLevel(s.reviewsToday.toFloat() / s.goal))
        goalRow.addView(bar, LinearLayout.LayoutParams(0, dp(8), 1f).also { it.marginEnd = dp(10) })
        goalRow.addView(Ui.text(this, 12f, 900, R.color.ag_text).apply { text = goalText(s); maxLines = 1 })
        card.addView(goalRow, lp(16))
        return card
    }

    /** Same words as the battle panel: "86 / 100 today", or "Goal done · 112". */
    private fun goalText(s: StudyStats.Summary): CharSequence {
        val sub = Ui.c(this, R.color.ag_sub0)
        val sb = SpannableStringBuilder()
        if (s.reviewsToday >= s.goal) {
            sb.append("Goal done").append(" · ${s.reviewsToday}")
            sb.setSpan(ForegroundColorSpan(sub), "Goal done".length, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            sb.append(s.reviewsToday.toString())
            val at = sb.length
            sb.append(" / ${s.goal} today")
            sb.setSpan(ForegroundColorSpan(sub), at, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }

    // ---- 2. the chart: metric button, filters, key numbers, chart ----

    /** The big "Reviews · tap to change" button that opens the metric sheet. */
    private fun metricButton(): View {
        val tone = StatsSheets.groupTone(metric.group)
        val card = Ui.card(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            minimumHeight = dp(52)
            isClickable = true
            setOnClickListener { sheetOpen = true; syncSheet() }
            contentDescription = "${metric.label}, tap to change the chart"
        }
        card.addView(iconTile(StatsSheets.icon(metric.icon), tone, 32, 18), LinearLayout.LayoutParams(dp(32), dp(32)).also { it.marginEnd = dp(10) })
        card.addView(Ui.text(this, 17f, 900, R.color.ag_text).apply {
            text = SpannableStringBuilder(metric.label).also { sb ->
                val at = sb.length
                sb.append("  ·  tap to change")
                sb.setSpan(AbsoluteSizeSpan(11, true), at, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(ForegroundColorSpan(c(R.color.ag_sub0)), at, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            maxLines = 1
        }, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(ImageView(this).apply { setImageResource(R.drawable.ic_down); setColorFilter(c(R.color.ag_sub0)) }, LinearLayout.LayoutParams(dp(22), dp(22)))
        return card
    }

    /** A rounded square in a faint [tone] with the [icon] in it. */
    private fun iconTile(@DrawableRes icon: Int, @ColorRes tone: Int, sizeDp: Int, iconDp: Int): View = FrameLayout(this).apply {
        background = Ui.rounded(ColorUtils.setAlphaComponent(c(tone), 46), (sizeDp * 0.3f), this@StatsPage)
        addView(ImageView(this@StatsPage).apply { setImageResource(icon); setColorFilter(c(tone)) }, FrameLayout.LayoutParams(dp(iconDp), dp(iconDp), Gravity.CENTER))
    }

    /** Period, deck and bar-size chips. A chip that doesn't apply to this metric is dimmed (the deck one) or left out (period / bar size of a picture of now). */
    private fun filterChips(series: ChartSeries): View {
        val row = StatsSheets.FlowLayout(this)
        if (metric.usesPeriod) {
            row.addView(chip(StatsChartMath.periodLabel(metric, series.period), true) { anchor ->
                val menu = AppMenu(anchor)
                for ((i, p) in StatsChartMath.periods(metric).withIndex()) menu.item(i, StatsChartMath.periodLabel(metric, p), if (p == series.period) R.drawable.ic_check else R.drawable.ic_blank)
                menu.show { i ->
                    val p = StatsChartMath.periods(metric)[i]
                    if (p != period) { period = p; grain = null; resetPicks(); requestRender() }
                }
            })
        }
        row.addView(chip(if (series.deckApplies) deck ?: "All decks" else "All decks", series.deckApplies, 150) { anchor ->
            val menu = AppMenu(anchor).header("Deck")
            menu.item(0, "All decks", if (deck == null) R.drawable.ic_check else R.drawable.ic_blank)
            for ((i, d) in decks.withIndex()) menu.item(i + 1, d, if (d == deck) R.drawable.ic_check else R.drawable.ic_blank)
            if (decks.isEmpty()) menu.item(-1, "No decks found", R.drawable.ic_blank, enabled = false, sub = "Allow AnkiGo to read AnkiDroid")
            menu.show { i ->
                val d = if (i <= 0) null else decks.getOrNull(i - 1)
                if (i >= 0 && d != deck) { deck = d; resetPicks(); requestRender() }
            }
        })
        val grains = ChartData.grains(metric, series.period)
        if (grains.isNotEmpty()) {
            row.addView(chip(series.grain.label, grains.size > 1) { anchor ->
                val menu = AppMenu(anchor)
                for ((i, g) in grains.withIndex()) menu.item(i, g.label, if (g == series.grain) R.drawable.ic_check else R.drawable.ic_blank)
                menu.show { i -> if (grains[i] != grain) { grain = grains[i]; resetPicks(); requestRender() } }
            })
        }
        return row
    }

    private fun chip(text: String, enabled: Boolean, maxDp: Int = 0, onTap: (View) -> Unit): TextView =
        Ui.text(this, 12f, 800, R.color.ag_sub1).apply {
            this.text = text
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            if (maxDp > 0) maxWidth = dp(maxDp)
            Ui.touchPill(this, Ui.rounded(c(R.color.ag_card), 12, this@StatsPage), 32, 12)
            getDrawable(R.drawable.ic_down)?.mutate()?.let { d ->
                d.setTint(c(R.color.ag_sub0))
                d.setBounds(0, 0, dp(14), dp(14))
                setCompoundDrawablesRelative(null, null, d, null)
                compoundDrawablePadding = dp(4)
            }
            if (enabled) setOnClickListener { onTap(this) } else { alpha = 0.5f; isClickable = false }
        }

    private fun resetPicks() { barSel = -1; heatDay = null }

    /** The three key numbers (or two for a category chart), one card each. */
    private fun tileRow(tiles: List<StatTile>): View {
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, t) in tiles.withIndex()) line.addView(tile(t.label, t.value), LinearLayout.LayoutParams(0, -1, 1f).also { if (i > 0) it.marginStart = dp(8) })
        return line
    }

    /** The bar colours of [s]: the answer buttons' own colours for Buttons, one colour per metric otherwise. */
    private fun tonesFor(s: ChartSeries): List<Int> = when (s.metric) {
        ChartMetric.REVIEWS, ChartMetric.ANSWERS -> listOf(R.color.ag_mauve)
        ChartMetric.TIME -> listOf(R.color.ag_blue, R.color.ag_teal)
        ChartMetric.RETENTION -> listOf(R.color.ag_green)
        ChartMetric.NEW_CARDS -> listOf(R.color.ag_sky)
        ChartMetric.BUTTONS -> listOf(R.color.ag_red, R.color.ag_peach, R.color.ag_green, R.color.ag_blue)
        ChartMetric.DUE -> listOf(R.color.ag_yellow)
        ChartMetric.MONEY -> listOf(R.color.ag_yellow)
        ChartMetric.MONEY_SPENT -> listOf(R.color.ag_peach)
        ChartMetric.CATCHES -> listOf(R.color.ag_teal)
        ChartMetric.WINS -> listOf(R.color.ag_red)
        ChartMetric.XP -> listOf(R.color.ag_sky)
        ChartMetric.HEALING -> listOf(R.color.ag_pink)
        ChartMetric.DMG_DEALT -> listOf(R.color.ag_red)
        ChartMetric.DMG_TAKEN -> listOf(R.color.ag_peach)
        ChartMetric.DMG_BLOCKED -> listOf(R.color.ag_blue)
        ChartMetric.MISSES -> listOf(R.color.ag_mauve)
        ChartMetric.CARD_STATES -> CardState.values().map {
            when (it) {
                CardState.NEW -> R.color.ag_blue; CardState.LEARNING -> R.color.ag_peach; CardState.RELEARNING -> R.color.ag_red
                CardState.YOUNG -> R.color.ag_green; CardState.MATURE -> R.color.ag_teal; CardState.SUSPENDED -> R.color.ag_yellow
                CardState.BURIED -> R.color.ag_ov
            }
        }
        ChartMetric.DEX_PROGRESS -> listOf(R.color.ag_teal)
        ChartMetric.SHINIES_RARE -> listOf(R.color.ag_yellow, R.color.ag_mauve, R.color.ag_pink, R.color.ag_sky, R.color.ag_teal)
        ChartMetric.STREAK_CALENDAR, ChartMetric.PER_TYPE -> listOf(R.color.ag_mauve)
    }.map { c(it) }.let { base ->
        // one bar per type: the type's own colour
        if (s.metric == ChartMetric.PER_TYPE) s.bars.map { b -> Type.values().firstOrNull { it.label == b.label }?.let(Ui::typeColor) ?: base[0] } else base
    }

    /** Title + hint, legend of a stacked chart, the chart (or the reason there is none), and where the numbers come from. */
    private fun chartCard(s: ChartSeries, message: String?, snap: StatsSnapshot, today: String, sel: Int, title: String? = null, onSel: (Int) -> Unit): View {
        val card = Ui.card(this).apply { setPadding(dp(14), dp(12), dp(14), dp(10)) }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(Ui.text(this, 13f, 900, R.color.ag_text).apply { text = title ?: StatsChartMath.title(s); maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
        if (message == null) head.addView(Ui.text(this, 11f, 700, R.color.ag_sub0).apply { text = StatsChartMath.hint(s); maxLines = 1 }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dp(8) })
        card.addView(head)
        val tones = tonesFor(s)
        val legend = if (message == null) StatsChartMath.legend(s) else emptyList()
        if (legend.isNotEmpty()) {
            val flow = StatsSheets.FlowLayout(this, 12)
            for (l in legend) flow.addView(Ui.text(this, 11f, 800, R.color.ag_sub1).apply {
                text = l.name + (l.percent?.let { " $it %" } ?: "")
                maxLines = 1
                compoundDrawablePadding = dp(5)
                setCompoundDrawablesRelative(GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(tones[l.index % tones.size]); setBounds(0, 0, dp(8), dp(8)) }, null, null, null)
                setPadding(0, dp(3), 0, dp(3))
            })
            card.addView(flow, lp(4))
        }
        when {
            message != null -> card.addView(Ui.text(this, 13f, 700, R.color.ag_sub1).apply {
                text = message; gravity = Gravity.CENTER
                setLineSpacing(0f, 1.1f)
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }, LinearLayout.LayoutParams(-1, dp(150)).also { it.topMargin = dp(6) })
            s.kind == ChartKind.HEATMAP && s.heatmap != null -> card.addView(heatBody(s, today), lp(8))
            else -> card.addView(StatsChartView(this).apply {
                set(s, tones, sel); onSelect = onSel
                charts += this
            }, lp(8))
        }
        val source = if (s.kind == ChartKind.HEATMAP) StatsText.heatmapNote(snap, System.currentTimeMillis()) else s.source.takeIf { message == null && it.isNotBlank() }?.let { "Source: $it" }
        if (!source.isNullOrBlank()) card.addView(note(source), lp(6))
        return card
    }

    // ---- 3. the streak calendar ----

    private fun heatBody(s: ChartSeries, today: String): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val slot = FrameLayout(this).apply { minimumHeight = dp(58) }
        val map = HeatmapView(this).apply {
            set(s.heatmap!!, today, "Reviews")
            selectedDay = heatDay
            onDayTap = { d -> heatDay = StatsChartMath.tapPick(heatDay, d, null); selectedDay = heatDay; fillHeatSlot(slot, s) }
            closeHeat = { heatDay = null; selectedDay = null; fillHeatSlot(slot, s) }
            heatRef = this
        }
        col.addView(map)
        val legend = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL or Gravity.END }
        legend.addView(Ui.text(this, 10f, 700, R.color.ag_sub0).apply { text = "Less" })
        for (level in 0..4) {
            legend.addView(View(this).apply {
                background = GradientDrawable().apply {
                    setColor(HeatmapView.levelColor(this@StatsPage, level))
                    cornerRadius = Ui.dp(this@StatsPage, 3f)
                }
            }, LinearLayout.LayoutParams(dp(11), dp(11)).also { it.marginStart = dp(3) })
        }
        legend.addView(Ui.text(this, 10f, 700, R.color.ag_sub0).apply { text = "More" }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dp(5) })
        col.addView(legend, lp(8))
        fillHeatSlot(slot, s)
        col.addView(slot, lp(8))
        return col
    }

    /** The line under the calendar: the tapped day's info box, or a hint. Same height either way, so the page doesn't jump. */
    private fun fillHeatSlot(slot: FrameLayout, s: ChartSeries) {
        slot.removeAllViews()
        val lines = heatDay?.let { s.detailFor(it) }.orEmpty()
        if (lines.isEmpty()) {
            slot.addView(Ui.text(this, 11f, 700, R.color.ag_sub0).apply { text = "Tap a day to see its count."; gravity = Gravity.CENTER_VERTICAL }, FrameLayout.LayoutParams(-1, dp(46)))
            return
        }
        slot.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(c(R.color.ag_mantle), 10, this@StatsPage)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            for ((i, t) in lines.withIndex()) addView(Ui.text(this@StatsPage, if (i == 0) 12f else 11f, if (i == 0) 900 else 700, if (i == 0) R.color.ag_text else R.color.ag_sub1).apply { text = t })
        }, FrameLayout.LayoutParams(-1, -2))
    }

    // ---- 4. the metric sheet ----

    /** Shows or hides the layer with the "Show" sheet, as [sheetOpen] says. */
    private fun syncSheet() {
        sheetLayer.removeAllViews()
        if (!sheetOpen) { sheetLayer.visibility = View.GONE; return }
        sheetLayer.visibility = View.VISIBLE
        sheetLayer.addView(View(this).apply {
            setBackgroundColor(Color.argb(122, 0, 0, 0))
            setOnClickListener { sheetOpen = false; syncSheet() }
        }, FrameLayout.LayoutParams(-1, -1))
        sheetLayer.addView(StatsSheets.metricSheet(this, metric) { m -> pickMetric(m) }, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    }

    private fun pickMetric(m: ChartMetric) {
        sheetOpen = false
        if (m != metric) {
            metric = m
            if (period !in StatsChartMath.periods(m)) period = ChartPeriod.D30
            grain = null
            resetPicks()
        }
        requestRender()
    }

    private fun note(text: String): TextView = Ui.text(this, 10f, 700, R.color.ag_sub0).apply { this.text = text }

    // ---- 3. tiles ----

    private fun tiles(s: StudyStats.Summary, @ColorRes tint: Int = 0): View {
        val all = listOf(
            "Reviews" to String.format(Locale.US, "%,d", s.reviews),
            "Battles won" to Bits.fmt(s.battlesWon),
            "Catches" to Bits.fmt(s.catches),
            "Shinies" to Bits.fmt(s.shinies),
            "Evolutions" to Bits.fmt(s.evolutions),
            "Best combo" to Bits.fmt(s.bestCombo),
        )
        return tileGrid(all, tint)
    }

    /** R4-V11: the all-time battle numbers (GameState.counters, counted since 0.13), two tiles a row. */
    private fun battleTiles(s: StudyStats.Summary, @ColorRes tint: Int = 0): View = tileGrid(
        listOf(
            "Damage dealt" to String.format(Locale.US, "%,d", s.damageDealt), "Damage taken" to String.format(Locale.US, "%,d", s.damageTaken),
            "Damage blocked" to String.format(Locale.US, "%,d", s.damageBlocked), "Misses" to String.format(Locale.US, "%,d", s.misses),
        ),
        tint, perRow = 2,
    )

    /** [items] (label to value) as rows of [perRow] tiles. */
    private fun tileGrid(items: List<Pair<String, String>>, @ColorRes tint: Int = 0, perRow: Int = 3): View {
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for ((rowIndex, row) in items.chunked(perRow).withIndex()) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((i, t) in row.withIndex()) {
                line.addView(tile(t.first, t.second, tint = tint), LinearLayout.LayoutParams(0, -2, 1f).also { if (i > 0) it.marginStart = dp(8) })
            }
            grid.addView(line, lp(if (rowIndex > 0) 8 else 0))
        }
        return grid
    }

    /** Value centred over its label (the 0.4.3 tile rule). A long plain value ("Great ball") gets a smaller size so it stays on one line. */
    private fun tile(label: String, value: CharSequence, @ColorRes tone: Int = R.color.ag_text, @ColorRes tint: Int = 0): View = Ui.card(this).apply {
        if (tint != 0) background = softCell(tint)
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(4), dp(12), dp(4), dp(11))
        val size = if (value is Spanned || value.length <= 6) 21f else if (value.length <= 9) 17f else 14f
        addView(Ui.text(this@StatsPage, size, 900, tone).apply { text = value; gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(-1, -2))
        addView(Ui.text(this@StatsPage, 11f, 700, R.color.ag_sub0).apply { text = label; gravity = Gravity.CENTER; maxLines = 2 },
            LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(3) })
    }

    /** Section title with a small icon tile and a faint colour of its own (the main sections each get one; not everything is coloured). */
    private fun sectTitle(@DrawableRes icon: Int, title: String, @ColorRes tone: Int, right: CharSequence? = null): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(2), dp(18), dp(4), dp(8))
        addView(iconTile(icon, tone, 24, 14), LinearLayout.LayoutParams(dp(24), dp(24)).also { it.marginEnd = dp(8) })
        addView(Ui.text(this@StatsPage, 12f, 900, R.color.ag_sub0).apply {
            text = title.uppercase()
            letterSpacing = 0.058f
            setTextColor(Bits.blend(c(tone), c(R.color.ag_text), 0.55f))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (right != null) addView(Ui.text(this@StatsPage, 12f, 700, R.color.ag_sub0).apply { text = right })
    }

    /** Test hook: the (i) texts start open (the render tests can't tap). */
    internal var infosOpen = false

    /** A rounded cell with a soft tint of [tint] (the cells inside a section card). */
    private fun softCell(@ColorRes tint: Int) = Ui.rounded(ColorUtils.setAlphaComponent(c(tint), 36), 12, this)

    /**
     * One card per section (pick 11A): a header band in a faint colour of its own (small icon, title, optional [right] note and an (i) that
     * opens [info], the small print, in place inside the card), then [body]. Nothing is cut: the text only hides until the (i) is tapped.
     */
    private fun sectionCard(@DrawableRes icon: Int, title: String, @ColorRes tone: Int, right: CharSequence?, info: String?, body: View): View {
        val card = Ui.card(this)
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                setColor(ColorUtils.setAlphaComponent(c(tone), 34))
                val r = dp(14).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
        }
        head.addView(ImageView(this).apply {
            setImageResource(icon)
            setColorFilter(Bits.blend(c(tone), c(R.color.ag_text), 0.3f))
        }, LinearLayout.LayoutParams(dp(18), dp(18)).also { it.marginEnd = dp(8) })
        head.addView(Ui.text(this, 12f, 900, R.color.ag_sub0).apply {
            text = title.uppercase()
            letterSpacing = 0.058f
            setTextColor(Bits.blend(c(tone), c(R.color.ag_text), 0.55f))
            maxLines = 1
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (right != null) head.addView(Ui.text(this, 12f, 700, R.color.ag_sub0).apply { text = right; maxLines = 1 }, LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = dp(8) })
        val infoText = info?.let { Ui.text(this, 11f, 700, R.color.ag_sub1).apply { text = it; visibility = if (infosOpen) View.VISIBLE else View.GONE; setPadding(dp(12), dp(8), dp(12), 0) } }
        if (info != null) {
            head.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_info)
                setColorFilter(Bits.blend(c(tone), c(R.color.ag_text), 0.3f))
                setPadding(dp(11), dp(11), dp(11), dp(11))
                contentDescription = "About the numbers in $title"
                setOnClickListener { infoText!!.visibility = if (infoText.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
            }, LinearLayout.LayoutParams(dp(40), dp(40)))
        }
        card.addView(head, LinearLayout.LayoutParams(-1, dp(40)))
        if (infoText != null) card.addView(infoText, LinearLayout.LayoutParams(-1, -2))
        body.setPadding(dp(10), dp(10), dp(10), dp(10))
        card.addView(body, LinearLayout.LayoutParams(-1, -2))
        return card
    }

    // ---- 4. AnkiDroid ----

    private fun ankiTiles(a: AnkiStats, today: String, @ColorRes tint: Int = 0): View = tileGrid(listOf(
        "Studied today" to Bits.fmt(a.studied(today)),
        "New today" to Bits.fmt(a.newIn(1)),
        "Due now" to Bits.fmt(a.dueNow),
        "Due tomorrow" to Bits.fmt(a.dueTomorrow),
        "Next 7 days" to Bits.fmt(a.dueNext7),
        "Retention (est.)" to (a.retention30?.let { "$it%" } ?: "–"),
        "Young" to Bits.fmt(a.counts.young),
        "Mature" to Bits.fmt(a.counts.mature),
        "Suspended" to Bits.fmt(a.counts.suspended),
    ), tint)

    /** Again / Hard / Good / Easy card counts of the last 30 days, in the study buttons' colours. */
    private fun buttonsCard(a: AnkiStats, @ColorRes tint: Int = 0): View {
        val card = Ui.card(this).apply { setPadding(dp(14), dp(12), dp(14), dp(12)); if (tint != 0) background = softCell(tint) }
        card.addView(Ui.text(this, 12f, 900, R.color.ag_text).apply { text = "Answer buttons · last 30 days" })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val counts = a.buttonsIn(30)
        listOf("Again" to R.color.ag_red, "Hard" to R.color.ag_peach, "Good" to R.color.ag_green, "Easy" to R.color.ag_blue).forEachIndexed { i, (label, color) ->
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(Ui.text(this, 19f, 900, color).apply { text = Bits.fmt(counts[i]); gravity = Gravity.CENTER; maxLines = 1 }, LinearLayout.LayoutParams(-1, -2))
            col.addView(Ui.text(this, 11f, 700, R.color.ag_sub0).apply { text = label; gravity = Gravity.CENTER; maxLines = 1 },
                LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(2) })
            row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        }
        card.addView(row, lp(8))
        return card
    }

    // ---- 5. study time ----

    /** Today's time as a tile, the last 7 days beside it: study in AnkiGo plus the answers it timed live in AnkiDroid (split screen, floating). */
    private fun timeRow(snap: StatsSnapshot, today: String, @ColorRes tint: Int = 0): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(tile("Today", StatsText.duration(snap.timeTotalOn(today)), tint = tint), LinearLayout.LayoutParams(0, -2, 1f))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.text(this, 12f, 900, R.color.ag_text).apply {
            text = SpannableStringBuilder("Last 7 days: ").also { sb ->
                sb.setSpan(ForegroundColorSpan(Ui.c(this@StatsPage, R.color.ag_sub1)), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.append(StatsText.duration(AnkiGoTime.lastDays(snap.timeTotalByDay, today, 7)))
            }
        })
        row.addView(col, LinearLayout.LayoutParams(0, -2, 2f).also { it.marginStart = dp(12) })
        return row
    }

    // ---- 6. collection ----

    /** The Collection section: clearly apart from the rest, in a faint panel of its own colour; tiles, then the per-generation bars. */
    private fun collectionSection(cs: CollectionStats, snap: StatsSnapshot, today: String): View {
        val tone = R.color.ag_teal
        val since = cs.firstCatch?.let { ms ->
            val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
            "since ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}" + if (d.year != LocalDate.now().year) " ${d.year}" else ""
        }
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(collectionTiles(cs, tone))
        val gens = ChartData.build(ChartMetric.DEX_PROGRESS, ChartPeriod.D30, null, snap, state.history, today, collection = cs)
        panel.addView(chartCard(gens, StatsChartMath.emptyMessage(gens, snap.access, snap.message), snap, today, genSel, "Caught per generation") { genSel = it }
            .also { it.background = softCell(tone) }, lp(8))
        return sectionCard(R.drawable.ic_dex, "Collection", tone, since, null, panel)
    }

    private fun collectionTiles(cs: CollectionStats, @ColorRes tint: Int = 0): View {
        val dex = SpannableStringBuilder("${cs.dex.caught}").also { sb ->
            val at = sb.length
            sb.append(" / ${cs.dex.total}")
            sb.setSpan(AbsoluteSizeSpan(11, true), at, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(c(R.color.ag_sub0)), at, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val avg = if (cs.averageTeamLevel <= 0.0) "–" else if (cs.averageTeamLevel % 1.0 == 0.0) cs.averageTeamLevel.toInt().toString() else String.format(Locale.US, "%.1f", cs.averageTeamLevel)
        val ball = cs.ballCatches.firstOrNull()?.first?.let { if (it == "auto") "Auto catch" else BattleLogBuilder.prettyItem(it) } ?: "–"
        val items = listOf<Triple<String, CharSequence, Int>>(
            Triple("Pokémon owned", Bits.fmt(cs.owned), R.color.ag_text),
            Triple("Pokédex caught", dex, R.color.ag_text),
            Triple("Shinies", Bits.fmt(cs.shinies), R.color.ag_text),
            Triple("Duplicates", Bits.fmt(cs.duplicates), R.color.ag_text),
            Triple("Legendary & Mythical", Bits.fmt(cs.legendaries + cs.mythicals), R.color.ag_text),
            Triple("Highest level", cs.highest?.let { "Lv ${it.level}" } ?: "–", R.color.ag_blue),
            Triple("Average team level", avg, R.color.ag_text),
            Triple("Most used ball", ball, R.color.ag_text),
            Triple("Rarest catch", cs.rarest?.name ?: "–", R.color.ag_text),
            Triple("Wild catches", Bits.fmt(cs.totalCatches), R.color.ag_text),
            Triple("Defeated", Bits.fmt(cs.totalDefeats), R.color.ag_text),
            Triple("Evolutions", Bits.fmt(cs.totalEvolutions), R.color.ag_text),
        )
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for ((rowIndex, row) in items.chunked(3).withIndex()) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((i, t) in row.withIndex()) line.addView(tile(t.first, t.second, t.third, tint), LinearLayout.LayoutParams(0, -1, 1f).also { if (i > 0) it.marginStart = dp(8) })
            grid.addView(line, lp(if (rowIndex > 0) 8 else 0))
        }
        return grid
    }

    private fun summaryLine(s: StudyStats.Summary, anki: AnkiStats?): TextView =
        Ui.text(this, 12f, 700, R.color.ag_sub1).apply {
            gravity = Gravity.CENTER
            // the same per-day source as the heatmap
            val t = if (anki != null) StudyStats.dayTotals(anki.perDay.values) else StudyStats.DayTotals(s.daysStudied, s.avgPerDay, s.bestDay)
            text = if (t.daysStudied == 0) "No study days logged yet."
            else "${if (anki != null) "Last 365 days: studied" else "Studied"} ${t.daysStudied} ${if (t.daysStudied == 1) "day" else "days"} · avg ${Bits.fmt(t.avgPerDay)} per day · best day ${Bits.fmt(t.bestDay)}"
        }

    private companion object { const val WEEKS = 18 }
}

/** The Stats screen as its own Activity: a thin shell around [StatsPage]. */
class StatsActivity : PageActivity() {
    private var statsPage: StatsPage? = null

    override fun createPage(): GamePage = StatsPage(this, this, StatsSection.of(intent.getStringExtra(EXTRA_SECTION))).also { p ->
        statsPage = p
        intent.getStringExtra(Nav.EXTRA_ACHIEVEMENT)?.let { p.openAchievement(it) }
    }

    /** An open that finds this screen already there (brought to the front) may ask for the other section. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_SECTION)?.let { statsPage?.showSection(StatsSection.of(it)) }
        intent.getStringExtra(Nav.EXTRA_ACHIEVEMENT)?.let { statsPage?.openAchievement(it) }
    }

    companion object {
        /** Opens the Stats tab on this [StatsSection] (its name); without it the tab opens on Statistics. [Nav] sets it. */
        const val EXTRA_SECTION = "section"
    }
}
