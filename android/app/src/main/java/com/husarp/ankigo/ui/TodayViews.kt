package com.husarp.ankigo.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.TodaySummary
import com.husarp.ankigo.stats.StatsText

/** The same daily totals on the finish screen and the Stats sheet. */
internal object TodayViews {
    fun body(ctx: Context, s: TodaySummary, sheet: Boolean = false): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        fun lp(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, top) }
        val date = runCatching { java.time.LocalDate.parse(s.day).format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.US)) }.getOrDefault(s.day)
        val scope = Ui.text(ctx, 12f, 700, R.color.ag_sub0).apply { text = "All decks · $date" }
        if (sheet) addView(scope, lp(4)) else addView(Ui.card(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 14), Ui.dp(ctx, 12), Ui.dp(ctx, 14))
            addView(android.widget.ImageView(ctx).apply { setImageResource(R.drawable.ic_check); setColorFilter(Ui.c(ctx, R.color.ag_green)) },
                LinearLayout.LayoutParams(Ui.dp(ctx, 32), Ui.dp(ctx, 32)).also { it.marginEnd = Ui.dp(ctx, 12) })
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(ctx, 20f, 900, R.color.ag_text).apply { text = "Your day so far" })
                addView(scope, lp(4))
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }, lp(4))
        val stats = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        for ((value, label) in listOf("${s.reviews}" to "answers", StatsText.duration(s.studyMillis) to "studied", s.correct to "correct")) {
            stats.addView(Ui.card(ctx).apply {
                gravity = Gravity.CENTER
                setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 12), Ui.dp(ctx, 4), Ui.dp(ctx, 12))
                addView(Ui.text(ctx, 21f, 900, R.color.ag_text).apply { text = value; gravity = Gravity.CENTER })
                addView(Ui.text(ctx, 11f, 800, R.color.ag_sub0).apply { text = label; gravity = Gravity.CENTER })
            }, LinearLayout.LayoutParams(0, -2, 1f).also { it.setMargins(Ui.dp(ctx, 2), 0, Ui.dp(ctx, 2), 0) })
        }
        if (!sheet) addView(stats, lp(12))
        if (!sheet) addView(Ui.text(ctx, 12f, 900, R.color.ag_mauve).apply { text = "TODAY’S REWARDS" }, lp(16))
        val rewards = Ui.card(ctx).apply { setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 6), Ui.dp(ctx, 12), Ui.dp(ctx, 6)) }
        fun row(label: String, value: String, icon: Int = 0, tone: Int = R.color.ag_sub0) {
            if (rewards.childCount > 0) rewards.addView(View(ctx).apply { setBackgroundColor(Ui.c(ctx, R.color.ag_s0)) }, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 1)))
            val line = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; minimumHeight = Ui.dp(ctx, 36) }
            if (icon != 0) line.addView(android.widget.ImageView(ctx).apply { setImageResource(icon); setColorFilter(Ui.c(ctx, tone)) },
                LinearLayout.LayoutParams(Ui.dp(ctx, 22), Ui.dp(ctx, 22)).also { it.marginEnd = Ui.dp(ctx, 8) })
            line.addView(Ui.text(ctx, 12.5f, 800, R.color.ag_text).apply { text = label }, LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(Ui.text(ctx, 12.5f, 900, R.color.ag_text).apply { text = value; gravity = Gravity.END }, LinearLayout.LayoutParams(-2, -2))
            rewards.addView(line)
        }
        if (sheet) {
            row("Answers counted", "${s.reviews}", R.drawable.ic_book)
            row("Study time", StatsText.duration(s.studyMillis), R.drawable.ic_clock)
            row("Correct answers", s.correct, R.drawable.ic_check)
        }
        row("XP for the team", "+${Bits.fmt(s.log.xp)}", R.drawable.ic_bolt, R.color.ag_sky)
        row("Defeated / caught", "${s.defeated} / ${s.log.caught}", R.drawable.ic_swords)
        row("Coins earned", "+${Bits.fmt(s.log.cash)}", R.drawable.ic_money, R.color.ag_yellow)
        row("Items found", if (s.items.isEmpty()) "No items" else "${s.items.values.sum()} items", R.drawable.ic_gift, R.color.ag_teal)
        for ((item, count) in s.items) row(if (item.startsWith("tm:")) com.husarp.ankigo.game.DailyGift.name(com.husarp.ankigo.GameRepo.data, null, item.removePrefix("tm:"), 1) else Items.name(item), "×$count")
        addView(rewards, lp(8))
        addView(Ui.text(ctx, 12f, 800, R.color.ag_sub0).apply { text = "Daily goal · ${s.reviews} / ${s.goal}" }, lp(12))
        addView(BarView(ctx).apply { setMeaning(BarMeaning.GOAL, s.reviews.toFloat() / s.goal, false) }, lp(6).also { it.height = Ui.dp(ctx, 10) })
        addView(Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply {
            text = "Again ${s.log.buttons[1]} · Hard ${s.log.buttons[2]} · Good ${s.log.buttons[3]} · Easy ${s.log.buttons[4]}\n" +
                "Shinies ${s.log.shinies} · Coins spent ${s.log.spent} · Heals used ${s.log.healItems}\n" +
                "Damage dealt ${s.log.dmgDealt} · taken ${s.log.dmgTaken} · blocked ${s.log.dmgBlocked} · misses ${s.log.misses}"
        }, lp(12))
    }

    fun sheet(ctx: Context, s: TodaySummary, close: () -> Unit): DragSheet = DragSheet(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 20, ctx)
        setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 8), Ui.dp(ctx, 16), Ui.dp(ctx, 16))
        isClickable = true
        addView(View(ctx).apply { background = Ui.rounded(Ui.c(ctx, R.color.ag_ov), 2, ctx) },
            LinearLayout.LayoutParams(Ui.dp(ctx, 36), Ui.dp(ctx, 4)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(Ui.text(ctx, 22f, 900, R.color.ag_text).apply { text = "Today" }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(Ui.text(ctx, 24f, 700, R.color.ag_sub0).apply {
            text = "×"; gravity = Gravity.CENTER; contentDescription = "Close Today"; setOnClickListener { close() }
        }, LinearLayout.LayoutParams(Ui.dp(ctx, 48), Ui.dp(ctx, 48)))
        addView(head)
        addView(object : MotionScrollView(ctx) {
            override fun onMeasure(w: Int, h: Int) {
                val cap = (ctx.resources.displayMetrics.heightPixels * .65f).toInt()
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(minOf(MeasureSpec.getSize(h), cap), MeasureSpec.AT_MOST))
            }
        }.apply { addView(body(ctx, s, sheet = true)) }, LinearLayout.LayoutParams(-1, -2))
    }
}
