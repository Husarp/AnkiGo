package com.husarp.ankigo.ui

import kotlin.math.max

/**
 * The two designs of the battle's info boxes (Round 9 wave 3, D12; docs/design/round9d.html `D12.A` / `D12.B`). Settings → Battle →
 * "Info box style". [ABOVE] (the default) writes the HP number on the line above the HP bar, which then runs the whole width;
 * [INSIDE] writes it inside a thicker bar. Both put the status badge in the name line and have no row for it.
 */
enum class InfoBoxStyle(val label: String, val sub: String) {
    ABOVE("Number above the bar", "The HP number on the line above a full-width bar; the type names on the left of it"),
    INSIDE("Number inside the bar", "A thicker bar with the HP number written in it; the shortest boxes");

    companion object {
        /** The preference key (an [InfoBoxStyle] name). */
        const val KEY = "infoBoxStyle"

        val DEFAULT = ABOVE

        /** The saved choice (the enum name); unknown or missing = [DEFAULT]. */
        fun parse(name: String?): InfoBoxStyle = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * The LOOK of the battle's info boxes (Round 10 W7, docs mock D8): colours, edge and background of the two boxes, the other axis next to
 * the layout [InfoBoxStyle] (above / inside), which every look combines with. Settings → Battle → "Info box style" and the battle's
 * "View settings" sheet. [TODAY] (the default, "Card") is the card colour at 92 %, exactly as the boxes always were. What each look paints
 * is one pure spec, [InfoBoxSkin]; the real boxes ([ArenaView]) and the small previews ([BoxLookPreview]) both draw from it.
 */
enum class InfoBoxLook(val label: String, val sub: String) {
    TODAY("Card", "The card colour, as it always was"),
    GLASS("Glass", "The picture behind the box, blurred, under a light tint and a thin bright edge"),
    FRAME("Thin frame", "A barely-there dark veil and a thin white frame, white text; the same in light and dark"),
    PLATE("Dark plate", "Deep navy with a gold trim and light text; the same in light and dark"),
    HUD("HUD in the corners", "No box: outlined text and bars straight on the picture, yours in one corner and the wild one in the opposite one"),
    TYPE("Type colour", "Washed with the Pokémon's first type colour, with a stripe on the left");

    companion object {
        /** The preference key (an [InfoBoxLook] name). */
        const val KEY = "infoBoxLook"

        val DEFAULT = TODAY

        /** The saved choice (the enum name); unknown or missing = [DEFAULT]. */
        fun parse(name: String?): InfoBoxLook = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * The look of the battle info boxes' HP and XP bars (Round 10 W8, docs mock D9-E). Settings → Battle → "HP / XP bars" and the battle's
 * "View settings" sheet. [TODAY] (the default) is the flat rounded bar; [PIXEL] has hard pixel edges, a cut-corner outline and a fill
 * that moves in 5 % steps ([PixelBar]). Not the same as Look → "Progress bars" ([BarStyle], `barStyle`): that one is about the colours.
 */
enum class HpBarLook(val label: String, val sub: String) {
    TODAY("Today", "Flat rounded bars on a grey track"),
    PIXEL("Pixel", "Hard pixel edges, a cut-corner outline, filled in 5 % steps; matches the pixel sprites");

    companion object {
        /** The preference key (a [HpBarLook] name). */
        const val KEY = "hpBarLook"

        val DEFAULT = TODAY

        /** The saved choice (the enum name); unknown or missing = [DEFAULT]. */
        fun parse(name: String?): HpBarLook = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * The info boxes' design sizes (pure maths; virtual dp / sp at box scale 1, the CSS of the D12 mockup) and what their text needs
 * on screen. [ArenaGeo.cards] builds the boxes' sizes from them, [ArenaView] the views.
 *
 * A box is 160 dp wide. Its parts: the name line (name 14 sp, the status badge 13 dp, "Lv" 12 sp), then
 * - [InfoBoxStyle.ABOVE]: the number line (wild: the type chips on the left, 8.5 sp; yours: "HP"; the HP number right, 10.5 sp), the
 *   7 dp HP bar, and yours the XP line (a 3 dp bar and "XP a / b", 9 sp);
 * - [InfoBoxStyle.INSIDE]: the HP bar 14 dp with its number inside (9.5 sp) and yours the XP bar 10 dp with its number inside (8 sp);
 *   the wild type symbols (15 dp squares) sit in the name line.
 *
 * "Reduced" boxes drop what is the smallest text: the wild type chips become the symbols and the XP number goes (its bar stays), so a
 * box can be drawn smaller before any text falls under [PanelFit.MIN_SP] on screen ([smallestSp]).
 */
internal object InfoBoxGeo {
    const val W = 160f

    const val NAME = 14f
    const val LV = 12f
    /** The HP number (above style) and the "HP" label. */
    const val NUM = 10.5f
    /** The wild type chips (above style). */
    const val CHIP = 8.5f
    /** The XP number beside the XP bar (above style). */
    const val XP = 9f
    /** The HP / XP numbers written inside their bars (inside style). */
    const val IN_HP = 9.5f
    const val IN_XP = 8f

    /** The status badge and the type symbols (dp, grown with the raised text like the bars). */
    const val BADGE = 13f
    const val SQUARE = 15f

    private const val PAD_TOP = 6f
    private const val PAD_BOTTOM = 7f
    private const val GAP = 3f
    private const val GAP_INSIDE = 5f

    /** The name line: the name's text line, or the badge / the type symbols when those are taller. */
    fun nameRow(floor: Float, squares: Boolean): Float {
        val b = TextMath.bar(floor)
        return max(TextMath.line(TextMath.at(NAME, floor)), max(BADGE * b, if (squares) SQUARE * b else 0f))
    }

    /**
     * The height of a box (dp). [wild]: the wild one (type chips / symbols, no XP). [xpText]: the XP number shows (yours only);
     * [reduced]: the optional parts are dropped (wild A: symbols instead of chips; no XP number).
     */
    fun height(style: InfoBoxStyle, wild: Boolean, floor: Float, xpText: Boolean, reduced: Boolean): Float {
        val b = TextMath.bar(floor)
        val num = TextMath.line(TextMath.at(NUM, floor))
        return when (style) {
            InfoBoxStyle.ABOVE -> {
                val name = nameRow(floor, squares = false)
                if (wild) {
                    val chips = if (reduced) SQUARE * b else TextMath.line(TextMath.at(CHIP, floor)) + 2f
                    PAD_TOP + name + GAP + max(num, chips) + GAP + 7f * b + PAD_BOTTOM
                } else {
                    val xp = if (xpText && !reduced) max(3f * b, TextMath.line(TextMath.at(XP, floor))) else 3f * b
                    PAD_TOP + name + GAP + num + GAP + 7f * b + GAP + xp + PAD_BOTTOM
                }
            }
            InfoBoxStyle.INSIDE -> {
                val name = nameRow(floor, squares = wild)
                val base = PAD_TOP + name + GAP_INSIDE + 14f * b + PAD_BOTTOM
                if (wild) base else base + GAP + (if (xpText && !reduced) 10f else 4f) * b
            }
        }
    }

    /** The compact card (name + a thin bar with the small HP number beside it): the pref / the small windows. */
    fun compactHeight(floor: Float): Float =
        5f + nameRow(floor, squares = false) + 3f + max(5f * TextMath.bar(floor), TextMath.line(TextMath.at(10f, floor))) + 6f

    /** The width of a box (dp) at text floor [floor]: wider with the raised text. */
    fun width(floor: Float): Float = W * max(1f, floor / 12f)

    /**
     * The smallest design text (sp) a box of [style] draws: what the readability floor ([StageGeo.boxScales]) is worked out from.
     * [xpText]: the XP number shows; [reduced]: the optional parts are dropped.
     */
    fun smallestSp(style: InfoBoxStyle, xpText: Boolean, reduced: Boolean): Float = when (style) {
        InfoBoxStyle.ABOVE -> if (reduced) NUM else minOf(CHIP, if (xpText) XP else NUM)
        InfoBoxStyle.INSIDE -> if (reduced || !xpText) IN_HP else IN_XP
    }

    /** The compact card's smallest text: the HP number beside the bar. */
    const val COMPACT_SP = 10f
}
