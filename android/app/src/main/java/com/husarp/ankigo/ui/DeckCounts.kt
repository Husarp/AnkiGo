package com.husarp.ankigo.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.husarp.ankigo.Anki
import com.husarp.ankigo.R

/** New / Learn / Due numbers in deck lists: up to 5 digits are shown whole on one line, bigger ones as "123k" / "1.2M". */
object DeckCounts {
    /** Pure text for a count (negative counts show as 0). */
    fun format(n: Int): String {
        val v = n.coerceAtLeast(0)
        return when {
            v < 100_000 -> v.toString()
            v < 1_000_000 -> "${v / 1000}k"
            else -> "${v / 100_000 / 10}.${v / 100_000 % 10}M".replace(".0M", "M")
        }
    }

    private fun label(ctx: Context, n: Int, zeroTone: Int, tone: Int): TextView {
        val zero = n <= 0
        return StudyUi.label(ctx, format(n), 13, if (zero) zeroTone else tone, if (zero) 700 else 900).apply {
            gravity = Gravity.END; maxLines = 1; setHorizontallyScrolling(true)
        }
    }

    /** Width in px of one count column: at least 28 dp, wide enough for the longest number of [decks] in the real font. */
    fun columnWidth(ctx: Context, decks: List<Anki.Deck>): Int {
        val widest = decks.flatMap { listOf(it.new, it.learn, it.review) }.maxOfOrNull { format(it).length } ?: 1
        val probe = StudyUi.label(ctx, "8".repeat(widest), 13, R.color.ag_text, 900)
        probe.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        return maxOf(Ui.dp(ctx, 28), probe.measuredWidth + Ui.dp(ctx, 8))
    }

    /** The three count labels (New, Learn, Review order) with accessibility text, each [width] px wide. */
    fun addTo(row: android.widget.LinearLayout, ctx: Context, deck: Anki.Deck, width: Int) {
        listOf(deck.new to R.color.ag_blue, deck.learn to R.color.ag_red, deck.review to R.color.ag_green).forEachIndexed { i, (n, color) ->
            row.addView(label(ctx, n, R.color.ag_ov, color).apply { contentDescription = "${listOf("New", "Learn", "Review")[i]} $n" }, StudyUi.lp(width, -2))
        }
    }

    /** The "New Lrn Due" header row cells, same width as the counts below. */
    fun addHeader(row: android.widget.LinearLayout, ctx: Context, width: Int) {
        listOf("New" to R.color.ag_blue, "Lrn" to R.color.ag_red, "Due" to R.color.ag_green).forEach { (name, tone) ->
            row.addView(StudyUi.label(ctx, name, 9.5f, tone, 900).apply { gravity = Gravity.END }, StudyUi.lp(width, -2))
        }
    }
}
