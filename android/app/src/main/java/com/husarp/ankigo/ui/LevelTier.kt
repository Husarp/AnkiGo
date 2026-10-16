package com.husarp.ankigo.ui

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.annotation.ColorRes
import com.husarp.ankigo.R

/** Level colour tiers (10 levels each): bronze 1-9, silver 10-19, gold 20-29 ... diamond 80-89, master 90-100. */
object LevelTier {
    val names = listOf("bronze", "silver", "gold", "emerald", "sapphire", "ruby", "amethyst", "platinum", "diamond", "master")

    private val colors = intArrayOf(
        R.color.ag_tier_0, R.color.ag_tier_1, R.color.ag_tier_2, R.color.ag_tier_3, R.color.ag_tier_4,
        R.color.ag_tier_5, R.color.ag_tier_6, R.color.ag_tier_7, R.color.ag_tier_8, R.color.ag_tier_9,
    )

    /** Tier index 0..9 for [level] (levels below 1 count as 1, above 100 as 100). */
    fun index(level: Int): Int = (level.coerceIn(1, 100) / 10).coerceAtMost(names.size - 1)

    /** Tier name for [level], e.g. "gold". */
    fun name(level: Int): String = names[index(level)]

    /** The tier's colour resource (light and dark values), for text. */
    @ColorRes fun colorRes(level: Int): Int = colors[index(level)]

    /** The label every level shows: always with the "Lv" prefix, never a bare number. */
    fun label(level: Int): String = "Lv $level"

    /** Sets [tv] to "Lv N" in the tier colour of [level]. */
    fun style(tv: TextView, level: Int) {
        tv.text = label(level)
        tv.setTextColor(Ui.c(tv.context, colorRes(level)))
    }

    /** "Lv N" in its tier colour as a span, to join into a longer line: `LevelTier.line(ctx, "#001 · ", 5, " · Hardy")`. */
    fun line(ctx: Context, before: String, level: Int, after: String = ""): CharSequence {
        val sb = SpannableStringBuilder(before)
        val s = sb.length
        sb.append(label(level))
        sb.setSpan(ForegroundColorSpan(color(ctx, level)), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sb.append(after)
    }

    /** The tier colour as a colour int. */
    fun color(ctx: Context, level: Int): Int = Ui.c(ctx, colorRes(level))
}
