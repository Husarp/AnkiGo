package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.husarp.ankigo.R

/** PLAN 559: palette C, lifted by 7% in HSL; light surfaces cap the lift to keep icons at 3:1. */
internal object Multicolor {
    enum class Tone(val base: Int) {
        GREEN(0xff36964e.toInt()), BLUE(0xff397fdf.toInt()), ORANGE(0xffcb6720.toInt()),
        RED(0xffd95158.toInt()), AMBER(0xff9e7c05.toInt()), PINK(0xffcb528d.toInt()),
        TEAL(0xff188d8a.toInt()), PURPLE(0xff885dd4.toInt());

        val light: Int by lazy { lifted(base, listOf(0xffe6e9ef.toInt(), Color.WHITE)) }
        val dark: Int by lazy { lifted(base, listOf(0xff181825.toInt(), 0xff262637.toInt())) }
    }

    fun enabled(ctx: Context) = AppLook.accent(ctx) == Accent.MULTICOLOR

    fun color(ctx: Context, tone: Tone, fallback: Int = R.color.ag_mauve): Int =
        if (enabled(ctx)) { if (TypeChips.isDark(ctx)) tone.dark else tone.light } else Ui.c(ctx, fallback)

    fun section(s: DetailSection): Tone = when (s) {
        DetailSection.EVOLUTION -> Tone.GREEN
        DetailSection.STATS -> Tone.BLUE
        DetailSection.MOVES -> Tone.ORANGE
        DetailSection.MATCHUPS -> Tone.RED
        DetailSection.TRAINING -> Tone.AMBER
        DetailSection.BOND -> Tone.PINK
        DetailSection.INFO -> Tone.TEAL
        DetailSection.HELD_ITEM -> Tone.PURPLE
    }

    /** Adam's exact C lighter colours for detail section icons in both themes. */
    fun sectionColor(ctx: Context, section: DetailSection): Int = if (!enabled(ctx)) Ui.c(ctx, R.color.ag_mauve) else when (section) {
        DetailSection.HELD_ITEM -> 0xff9d79db.toInt()
        DetailSection.EVOLUTION -> 0xff3fb05c.toInt()
        DetailSection.STATS -> 0xff5893e4.toInt()
        DetailSection.MOVES -> 0xffde7930.toInt()
        DetailSection.MATCHUPS -> 0xffdf6e74.toInt()
        DetailSection.TRAINING -> 0xffc19706.toInt()
        DetailSection.BOND -> 0xffd36d9f.toInt()
        DetailSection.INFO -> 0xff1daca8.toInt()
    }

    fun tab(tab: BottomNav.Tab): Tone = when (tab) {
        BottomNav.Tab.STUDY -> Tone.BLUE
        BottomNav.Tab.POKEMON -> Tone.GREEN
        BottomNav.Tab.SHOP -> Tone.ORANGE
        BottomNav.Tab.STATS -> Tone.PURPLE
        BottomNav.Tab.SETTINGS -> Tone.TEAL
    }

    fun place(label: String): Tone = when (label) {
        "Team", "In team", "Evolution", "Can evolve", "Owned", "Healing", "Economy" -> Tone.GREEN
        "Box", "Stats", "Statistics", "Level", "Recent", "Study", "Content", "TMs" -> Tone.BLUE
        "Shop", "Battle", "Attack" -> Tone.ORANGE
        "Bag", "Worn", "Held", "Held item", "Holds an item", "Achievements", "Badges", "Not owned" -> Tone.PURPLE
        "Favourites", "Favorites", "Bond", "Special" -> Tone.PINK
        "Training", "Shiny", "Item collections", "Seen", "Current levels", "Fossils" -> Tone.AMBER
        "Defend", "Fainted", "Secret", "Balls" -> Tone.RED
        else -> Tone.TEAL
    }

    fun resource(res: Int): Tone = when (res) {
        R.color.ag_green -> Tone.GREEN
        R.color.ag_blue, R.color.ag_sky -> Tone.BLUE
        R.color.ag_peach -> Tone.ORANGE
        R.color.ag_red -> Tone.RED
        R.color.ag_yellow -> Tone.AMBER
        R.color.ag_pink -> Tone.PINK
        R.color.ag_teal -> Tone.TEAL
        else -> Tone.PURPLE
    }

    fun bar(meaning: BarMeaning): Tone = when (meaning) {
        BarMeaning.XP -> Tone.BLUE
        BarMeaning.GOAL -> Tone.ORANGE
        BarMeaning.COLLECTION -> Tone.TEAL
        BarMeaning.PROGRESS -> Tone.GREEN
    }

    /** Small coloured labels need more contrast than their icons. */
    fun ink(ctx: Context, tone: Tone, background: Int): Int = readable(color(ctx, tone), background, 4.5)

    fun icon(ctx: Context, tone: Tone, background: Int): Int = readable(color(ctx, tone), background, 3.0)

    fun onFill(fill: Int): Int = AppDialogRules.onFill(fill, Color.BLACK, ColorUtils::calculateContrast)

    private fun lifted(base: Int, backgrounds: List<Int>): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(base, hsl)
        val original = hsl[2]
        var low = original
        var high = (original * 1.07f).coerceAtMost(1f)
        repeat(16) {
            hsl[2] = (low + high) / 2f
            if (backgrounds.all { ColorUtils.calculateContrast(ColorUtils.HSLToColor(hsl), it) >= 3.02 }) low = hsl[2]
            else high = hsl[2]
        }
        hsl[2] = low
        return ColorUtils.HSLToColor(hsl)
    }

    private fun readable(color: Int, background: Int, minimum: Double): Int {
        if (ColorUtils.calculateContrast(color, background) >= minimum) return color
        val end = if (ColorUtils.calculateLuminance(background) > .5) Color.BLACK else Color.WHITE
        for (step in 1..100) {
            val ink = ColorUtils.blendARGB(color, end, step / 100f)
            if (ColorUtils.calculateContrast(ink, background) >= minimum) return ink
        }
        return end
    }
}
