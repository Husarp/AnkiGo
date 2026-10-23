package com.husarp.ankigo.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// TICKET R2-07: the Battle log screen (menu entry "Battle log"). The current battle and the last few finished ones,
// newest on top; inside a battle the result first, then its rounds newest first, each with its answers and moves.

class BattleLogPage(base: Context, host: PageHost) : GamePage(base, host) {
    override val tab: BottomNav.Tab? = null
    override val needsGame: Boolean = false // it reads its own file only; GamePage would never call render() for this

    private lateinit var content: LinearLayout

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun buildBar(): View = Ui.appBar(this, "Battle log", back = host.hostActivity != null, onBack = { host.closePage() })

    override fun buildBody(): View {
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(2), dp(16), dp(28))
        }
        return ScrollView(this).apply { isFillViewport = true; addView(content) }
    }

    override fun onShow() {
        super.onShow()
        val main = Handler(Looper.getMainLooper())
        Thread {
            val battles = BattleLog.snapshot()
            main.post { if (alive) show(battles) }
        }.apply { isDaemon = true; name = "ankigo-battle-log-read" }.start()
    }

    override fun render() = Unit

    /** Draws [battles] (newest first). Main thread. */
    internal fun show(battles: List<LogBattle>) {
        content.removeAllViews()
        if (battles.isEmpty()) {
            content.addView(Ui.text(this, 14f, 700, R.color.ag_sub0).apply {
                gravity = Gravity.CENTER
                text = "No battles logged yet.\nAnswer a card and the fight shows up here, round by round."
                setLineSpacing(0f, 1.15f)
            }, LinearLayout.LayoutParams(-1, dp(200)).also { it.topMargin = dp(40) })
            return
        }
        battles.forEachIndexed { i, b ->
            if (i == 0) content.addView(Bits.sect(this, if (b.outcome == Outcome.OPEN) "Current battle" else "Last battle"))
            if (i == 1) content.addView(Bits.sect(this, "Earlier battles"))
            content.addView(battleCard(b), LinearLayout.LayoutParams(-1, -2).also { if (i > 1) it.topMargin = dp(10) })
        }
    }

    // ---- one battle ----

    private fun battleCard(b: LogBattle): View {
        val card = Ui.card(this).apply { setPadding(dp(14), dp(12), dp(14), dp(12)) }
        card.addView(header(b))
        val coins = b.coins
        if (b.outcome != Outcome.OPEN && (b.result.isNotEmpty() || coins > 0)) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = Ui.rounded(Ui.c(this@BattleLogPage, R.color.ag_mantle), 10, this@BattleLogPage)
                setPadding(dp(10), dp(8), dp(10), dp(8))
            }
            b.result.forEach { box.addView(line(it), lp(2)) }
            if (coins > 0) box.addView(line(LogLine(Tone.PLAIN, "+$coins ₽ earned in this fight")), lp(2))
            card.addView(box, lp(10))
        }
        if (b.rounds.isEmpty()) {
            card.addView(Ui.text(this, 12f, 700, R.color.ag_sub0).apply { text = "No answers in this battle yet." }, lp(10))
        }
        var number = 0
        val numbers = b.rounds.map { if (it.kind == RoundKind.ROUND) ++number else 0 }
        b.rounds.indices.reversed().forEach { i ->
            card.addView(divider(), LinearLayout.LayoutParams(-1, 1).also { it.topMargin = dp(10) })
            card.addView(roundView(b.rounds[i], numbers[i]), lp(8))
        }
        return card
    }

    private fun header(b: LogBattle): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.text(this, 15f, 900, R.color.ag_text).apply {
            text = (b.wild ?: "Wild Pokémon") + if (b.level > 0) " · Lv ${b.level}" else ""
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        col.addView(Ui.text(this, 11f, 700, R.color.ag_sub0).apply {
            text = SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()).format(Date(b.startedAt)) + if (b.shiny) " · shiny" else ""
        })
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Ui.pill(this, b.outcome.label.uppercase(), outcomeTone(b.outcome)))
        return row
    }

    private fun outcomeTone(o: Outcome) = when (o) {
        Outcome.OPEN -> R.color.ag_sky
        Outcome.DEFEATED -> R.color.ag_green
        Outcome.CAUGHT -> R.color.ag_blue
        Outcome.FLED -> R.color.ag_ov
        Outcome.LOST -> R.color.ag_red
        Outcome.ENDED -> R.color.ag_ov
    }

    // ---- one round ----

    private fun roundView(r: LogRound, number: Int): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val m = r.multiplier
        head.addView(Ui.text(this, 12f, 900, R.color.ag_text).apply {
            text = if (r.kind == RoundKind.BALL) "Ball" else "Round $number"
        })
        if (r.kind == RoundKind.ROUND) {
            head.addView(Ui.text(this, 12f, 900, if (m == null) R.color.ag_sub0 else if (m >= 1.0) R.color.ag_green else R.color.ag_peach).apply {
                text = if (m == null) "collecting answers" else String.format(Locale.US, "×%.2f", m)
            }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dp(8) })
        }
        box.addView(head)
        r.answers.forEach { box.addView(answerRow(it), lp(6)) }
        r.lines.forEach { box.addView(line(it), lp(4)) }
        return box
    }

    private fun answerRow(a: LogAnswer): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(Ui.pill(this, BattleLogBuilder.buttonLabel(a.button).uppercase(), buttonTone(a.button)).apply {
            minWidth = dp(58); gravity = Gravity.CENTER
        })
        row.addView(Ui.text(this, 12f, 700, R.color.ag_text).apply {
            text = a.card; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = dp(8) })
        if (a.catchUp) row.addView(Ui.text(this, 10f, 700, R.color.ag_sub0).apply { text = "catch-up" }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dp(6) })
        if (a.coins > 0) row.addView(Ui.text(this, 11f, 900, R.color.ag_yellow).apply { text = "+${a.coins} ₽" }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dp(6) })
        return row
    }

    /** Again red, Hard peach, Good green, Easy sky. */
    private fun buttonTone(button: Int) = when (button) {
        1 -> R.color.ag_red
        2 -> R.color.ag_peach
        3 -> R.color.ag_green
        4 -> R.color.ag_sky
        else -> R.color.ag_ov
    }

    private fun line(l: LogLine): TextView = Ui.text(this, 12f, if (l.tone == Tone.PLAIN || l.tone == Tone.SUB) 700 else 800, when (l.tone) {
        Tone.GOOD -> R.color.ag_green
        Tone.BAD -> R.color.ag_red
        Tone.SUB -> R.color.ag_sub0
        Tone.PLAIN -> R.color.ag_text
    }).apply { text = l.text; setLineSpacing(0f, 1.1f) }

    private fun divider(): View = View(this).apply { setBackgroundColor(Ui.c(this@BattleLogPage, R.color.ag_s0)) }

    private fun lp(top: Int) = LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(top) }
}

/** The Battle log as its own Activity: a thin shell around [BattleLogPage]. */
class BattleLogActivity : PageActivity() {
    override val closesWhenLeft = true // PLAN 222
    override fun createPage(): GamePage = BattleLogPage(this, this)
}
