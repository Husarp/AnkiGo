package com.husarp.ankigo.ui

import android.content.Context
import com.husarp.ankigo.R

/** What a progress bar counts. HP is not here: HP bars keep green / yellow / red ([Ui.hpColor]). */
enum class BarMeaning {
    /** Pokémon XP and trainer XP. */
    XP,
    /** Daily and weekly review goals. */
    GOAL,
    /** Pokédex progress (caught of all). */
    COLLECTION,
    /** Any other task progress (downloads, exports, update): no special meaning, so it follows the accent. */
    PROGRESS,
}

/** Settings → Look → "Progress bars". */
enum class BarStyle(val label: String) {
    MEANING("By meaning"),
    ACCENT("Accent"),
    GRADIENT("Gradient"),
}

/** The palette colours the resolver picks from (resolved for the current theme and accent). */
class BarPalette(val sky: Int, val peach: Int, val teal: Int, val accent: Int, val pink: Int, val goal: GoalPalette? = null)

/** A bar's fill: [color], or a horizontal gradient from [color] to [gradientTo] when that is not null. */
data class BarFill(val color: Int, val gradientTo: Int? = null)

object BarTones {
    /** (meaning, style) -> fill. By meaning: XP sky, goal red-orange-green by [fraction] (peach without a goal palette), collection teal, other = accent. Accent: all accent. Gradient: accent to pink. */
    fun resolve(meaning: BarMeaning, style: BarStyle, p: BarPalette, fraction: Float = 0f): BarFill = when (style) {
        BarStyle.ACCENT -> BarFill(p.accent)
        BarStyle.GRADIENT -> BarFill(p.accent, p.pink)
        BarStyle.MEANING -> BarFill(when (meaning) {
            BarMeaning.XP -> p.sky
            BarMeaning.GOAL -> p.goal?.let { GoalTone.color(fraction, it) } ?: p.peach
            BarMeaning.COLLECTION -> p.teal
            BarMeaning.PROGRESS -> p.accent
        })
    }

    fun palette(ctx: Context) = BarPalette(
        Ui.c(ctx, R.color.ag_sky), Ui.c(ctx, R.color.ag_peach), Ui.c(ctx, R.color.ag_teal),
        Ui.c(ctx, R.color.ag_mauve), Ui.c(ctx, R.color.ag_pink),
        GoalPalette(Ui.c(ctx, R.color.ag_red), Ui.c(ctx, R.color.ag_peach), Ui.c(ctx, R.color.ag_green)),
    )

    /** The fill for [meaning] in the player's chosen style. */
    fun fill(ctx: Context, meaning: BarMeaning, fraction: Float = 0f): BarFill = resolve(meaning, AppPrefs.barStyle(ctx), palette(ctx), fraction)
}

/** Fills this bar [fraction] (0..1) in the colour or gradient its [meaning] has in the chosen style. */
fun BarView.setMeaning(meaning: BarMeaning, fraction: Float, animate: Boolean, durationMs: Long = 450) {
    val f = BarTones.fill(context, meaning, fraction)
    val to = f.gradientTo
    if (to != null) setGradient(f.color, to) else clearGradient()
    set(fraction, f.color, animate, durationMs)
}
