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
import java.util.Locale

// TICKET T40: the Stats screen (streak, 18-week heatmap, all-time tiles) from AnkiGo's own per-day history.

/** Streak hero, review heatmap and the all-time tiles. No bottom bar; it works like Settings. */
class StatsPage(base: Context, host: PageHost) : GamePage(base, host) {
    override val tab: BottomNav.Tab? = null

    private lateinit var content: LinearLayout

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

    override fun render() {
        content.removeAllViews()
        val today = GameRepo.game?.dayKey() ?: state.day
        val s = StudyStats.summary(state, today, WEEKS)
        content.addView(streakCard(s))
        content.addView(Bits.sect(this, "Reviews"))
        content.addView(heatmapCard(s))
        content.addView(Bits.sect(this, "All time"))
        content.addView(tiles(s))
        content.addView(summaryLine(s), lp(12))
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
        bar.setGradient(Ui.c(this, R.color.ag_mauve), Ui.c(this, R.color.ag_pink))
        bar.set(minOf(1f, s.reviewsToday.toFloat() / s.goal), Ui.c(this, R.color.ag_mauve), false)
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

    private fun heatmapCard(s: StudyStats.Summary): View {
        val card = Ui.card(this).apply { setPadding(dp(14), dp(12), dp(14), dp(12)) }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(Ui.text(this, 13f, 900, R.color.ag_text).apply { text = "Reviews · last $WEEKS weeks" }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(Ui.text(this, 13f, 900, R.color.ag_text).apply {
            text = SpannableStringBuilder("${Bits.fmt(s.heatmap.total)} ").also { sb ->
                val at = sb.length
                sb.append("total")
                sb.setSpan(ForegroundColorSpan(Ui.c(this@StatsPage, R.color.ag_sub0)), at, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        })
        card.addView(head)
        val map = HeatmapView(this).apply { set(s.heatmap, s.today) }
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
        card.addView(Ui.text(this, 10f, 700, R.color.ag_sub0).apply { text = "AnkiGo keeps this history from version 0.5.0 on." }, lp(6))
        return card
    }

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
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for ((rowIndex, row) in all.chunked(3).withIndex()) {
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

    private fun summaryLine(s: StudyStats.Summary): TextView =
        Ui.text(this, 12f, 700, R.color.ag_sub1).apply {
            gravity = Gravity.CENTER
            text = if (s.daysStudied == 0) "No study days logged yet."
            else "Studied ${s.daysStudied} ${if (s.daysStudied == 1) "day" else "days"} · avg ${Bits.fmt(s.avgPerDay)} per day · best day ${Bits.fmt(s.bestDay)}"
        }

    private companion object { const val WEEKS = 18 }
}

/** The Stats screen as its own Activity: a thin shell around [StatsPage]. */
class StatsActivity : PageActivity() {
    override fun createPage(): GamePage = StatsPage(this, this)
}
