package com.husarp.ankigo.study

/**
 * The study screen while the soft keyboard is open on a type-in card (PLAN 61, pick 10A): the card keeps the top and
 * gets all the room, the battle area is left out (the keyboard covers where it was), and a full-width Show answer
 * button sits right above the keyboard. Pure geometry, all numbers dp; StudyActivity applies it.
 */
object TypeInLayout {
    /** The single Show answer button and its padding (a 48 dp button plus 8 dp above and below). */
    const val BUTTON_DP = 64
    /** An input window shorter than this is no soft keyboard (floating or one-handed strips, gesture hints). */
    const val MIN_IME_DP = 80

    /** [compact] = the keyboard layout is on; the heights are what the three blocks get. [cardDp] may be tiny (or 0) in a very small window: the card scrolls inside it, the button still sits above the keyboard. */
    data class Plan(val compact: Boolean, val topBarDp: Int, val cardDp: Int, val battleDp: Int, val buttonDp: Int) {
        /** The card area (without its top bar) has less than its normal minimum: it scrolls. */
        val cardScrolls get() = compact && cardDp < PanelHeight.MIN_CARD
    }

    /** The keyboard layout applies to a type-in question side while the keyboard is up. */
    fun compact(typeIn: Boolean, imeDp: Int) = typeIn && imeDp >= MIN_IME_DP

    /**
     * [windowDp]: the window height with no keyboard (decor view). [imeDp]: the keyboard's height (0 = hidden).
     * [battleDp] / [buttonsDp]: what the normal layout gives the battle area (with its drag bar) and the answer row.
     */
    fun plan(windowDp: Int, topBarDp: Int, imeDp: Int, typeIn: Boolean, battleDp: Int, buttonsDp: Int): Plan {
        if (!compact(typeIn, imeDp)) return Plan(false, topBarDp, maxOf(0, windowDp - topBarDp - battleDp - buttonsDp), battleDp, buttonsDp)
        val visible = maxOf(0, windowDp - imeDp)
        return Plan(true, topBarDp, maxOf(0, visible - topBarDp - BUTTON_DP), 0, BUTTON_DP)
    }
}
