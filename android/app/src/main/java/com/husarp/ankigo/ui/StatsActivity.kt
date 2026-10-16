package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.StudyStats
import com.husarp.ankigo.stats.AnkiGoTime
import com.husarp.ankigo.stats.AnkiStats
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.StatsSnapshot
import com.husarp.ankigo.stats.StatsText
import java.util.Locale

// TICKET T40: the Stats screen (streak, 18-week heatmap, all-time tiles) from AnkiGo's own per-day history.
// TICKET L2-07: the heatmap and an "AnkiDroid" section now come from AnkiDroid's official API (AnkiStatsStore, cached);
// the game's own count is the fallback. Study time is only what AnkiGo measured itself.

/** Streak hero, review heatmap and the all-time tiles. No bottom bar; it works like Settings. */
class StatsPage(base: Context, host: PageHost) : GamePage(base, host) {
    override val tab: BottomNav.Tab? = null

    private lateinit var content: LinearLayout
    private val statsListener: () -> Unit = { requestRender() }
    private var refreshWanted = false

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(top) }

    override fun buildBar(): View =
        Ui.appBar(this, "Stats", back = host.hostActivity != null, onBack = { host.closePage() })

    override fun buildBody(): View {
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(2), dp(16), dp(28))
        }
        return ScrollView(this).apply { addView(content) }
    }

    override fun onShow() {
        super.onShow()
        AnkiStatsStore.cached(this) // the last numbers at once; the render is posted, so it sees them
        AnkiStatsStore.addListener(statsListener)
        refreshWanted = true
    }

    override fun onHide() {
        AnkiStatsStore.removeListener(statsListener)
        super.onHide()
    }

    override fun onGone() {
        AnkiStatsStore.removeListener(statsListener)
        super.onGone()
    }

    override fun render() {
        content.removeAllViews()
        val today = GameRepo.game?.dayKey() ?: state.day
        if (refreshWanted) { // once per visit, when the game is ready (the store skips a refresh that just finished)
            refreshWanted = false
            AnkiStatsStore.refresh(this, today, state.settings.dayStartHour)
        }
        val s = StudyStats.summary(state, today, WEEKS)
        val snap = AnkiStatsStore.snapshot
        val anki = snap.stats
        content.addView(streakCard(s))
        content.addView(Bits.sect(this, "Reviews"))
        content.addView(heatmapCard(if (anki != null) StudyStats.heatmapFromCounts(anki.perDay, s.goal, today, WEEKS) else s.heatmap, today, snap))
        if (anki != null) {
            content.addView(Bits.sect(this, "AnkiDroid"))
            content.addView(ankiTiles(anki, today))
            content.addView(note("New ${Bits.fmt(anki.counts.new)} · Learning ${Bits.fmt(anki.counts.learning)} · Relearning ${Bits.fmt(anki.counts.relearning)} · Buried ${Bits.fmt(anki.counts.buried)}\n" +
                "Cards, not reviews: a card answered twice counts once. Retention is an estimate: cards answered in the last 30 days (not new then) that you didn't answer Again."), lp(6))
            content.addView(buttonsCard(anki), lp(8))
        }
        content.addView(Bits.sect(this, "Study time"))
        content.addView(timeRow(snap, today))
        content.addView(Bits.sect(this, "All time"))
        content.addView(tiles(s))
        content.addView(summaryLine(s, anki), lp(12))
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

    // ---- 2. heatmap ----

    private fun heatmapCard(heat: StudyStats.Heatmap, today: String, snap: StatsSnapshot): View {
        val fromAnki = snap.stats != null
        val card = Ui.card(this).apply { setPadding(dp(14), dp(12), dp(14), dp(12)) }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(Ui.text(this, 13f, 900, R.color.ag_text).apply { text = "${if (fromAnki) "Cards studied" else "Reviews"} · last $WEEKS weeks" }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(Ui.text(this, 13f, 900, R.color.ag_text).apply {
            text = SpannableStringBuilder("${Bits.fmt(heat.total)} ").also { sb ->
                val at = sb.length
                sb.append("total")
                sb.setSpan(ForegroundColorSpan(Ui.c(this@StatsPage, R.color.ag_sub0)), at, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        })
        card.addView(head)
        val map = HeatmapView(this).apply { set(heat, today, if (fromAnki) "Cards studied" else "Reviews") }
        card.addView(map, lp(8))

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
        card.addView(legend, lp(8))
        card.addView(note(StatsText.heatmapNote(snap, System.currentTimeMillis())), lp(6))
        return card
    }

    private fun note(text: String): TextView = Ui.text(this, 10f, 700, R.color.ag_sub0).apply { this.text = text }

    // ---- 3. tiles ----

    private fun tiles(s: StudyStats.Summary): View {
        val all = listOf(
            "Reviews" to String.format(Locale.US, "%,d", s.reviews),
            "Battles won" to Bits.fmt(s.battlesWon),
            "Catches" to Bits.fmt(s.catches),
            "Shinies" to Bits.fmt(s.shinies),
            "Evolutions" to Bits.fmt(s.evolutions),
            "Best combo" to Bits.fmt(s.bestCombo),
        )
        return tileGrid(all)
    }

    /** [items] (label to value) as rows of three tiles. */
    private fun tileGrid(items: List<Pair<String, String>>): View {
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for ((rowIndex, row) in items.chunked(3).withIndex()) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((i, t) in row.withIndex()) {
                line.addView(tile(t.first, t.second), LinearLayout.LayoutParams(0, -2, 1f).also { if (i > 0) it.marginStart = dp(8) })
            }
            grid.addView(line, lp(if (rowIndex > 0) 8 else 0))
        }
        return grid
    }

    /** Value centred over its label (the 0.4.3 tile rule). */
    private fun tile(label: String, value: String): View = Ui.card(this).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(4), dp(12), dp(4), dp(11))
        addView(Ui.text(this@StatsPage, 21f, 900, R.color.ag_text).apply { text = value; gravity = Gravity.CENTER; maxLines = 1 }, LinearLayout.LayoutParams(-1, -2))
        addView(Ui.text(this@StatsPage, 11f, 700, R.color.ag_sub0).apply { text = label; gravity = Gravity.CENTER; maxLines = 1 },
            LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(3) })
    }

    // ---- 4. AnkiDroid ----

    private fun ankiTiles(a: AnkiStats, today: String): View = tileGrid(listOf(
        "Studied today" to Bits.fmt(a.studied(today)),
        "New today" to Bits.fmt(a.newIn(1)),
        "Due now" to Bits.fmt(a.dueNow),
        "Due tomorrow" to Bits.fmt(a.dueTomorrow),
        "Next 7 days" to Bits.fmt(a.dueNext7),
        "Retention (est.)" to (a.retention30?.let { "$it%" } ?: "–"),
        "Young" to Bits.fmt(a.counts.young),
        "Mature" to Bits.fmt(a.counts.mature),
        "Suspended" to Bits.fmt(a.counts.suspended),
    ))

    /** Again / Hard / Good / Easy card counts of the last 30 days, in the study buttons' colours. */
    private fun buttonsCard(a: AnkiStats): View {
        val card = Ui.card(this).apply { setPadding(dp(14), dp(12), dp(14), dp(12)) }
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

    // ---- 5. time in AnkiGo ----

    /** Today's time as a tile, the last 7 days beside it. Only reviews done inside AnkiGo are timed. */
    private fun timeRow(snap: StatsSnapshot, today: String): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(tile("Today in AnkiGo", StatsText.duration(snap.timeByDay[today] ?: 0L)), LinearLayout.LayoutParams(0, -2, 1f))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.text(this, 12f, 900, R.color.ag_text).apply {
            text = SpannableStringBuilder("Last 7 days in AnkiGo: ").also { sb ->
                sb.setSpan(ForegroundColorSpan(Ui.c(this@StatsPage, R.color.ag_sub1)), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.append(StatsText.duration(AnkiGoTime.lastDays(snap.timeByDay, today, 7)))
            }
        })
        col.addView(note("Only reviews you do inside AnkiGo are timed. AnkiDroid doesn't share its own study time."), lp(4))
        row.addView(col, LinearLayout.LayoutParams(0, -2, 2f).also { it.marginStart = dp(12) })
        return row
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
    override fun createPage(): GamePage = StatsPage(this, this)
}
