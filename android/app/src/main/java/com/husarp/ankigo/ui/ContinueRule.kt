package com.husarp.ankigo.ui

/**
 * The "Continue" after a fight result (R6-09, pure). While the result tiles hold the next wild one back, no card may be answered
 * "outside a battle": the Study screen's answer row shows Continue instead of Show answer / the answer buttons, and every other
 * way to flip or answer (keys, swipes, taps on the card, the card's script) acts as Continue first and leaves the card alone.
 * Only the auto-advance countdown does not act: it waits until Continue was tapped.
 * Round 9 C5: the hold starts when the winning / catching batch arrives ([NextFight.holdsEarly]), not only once the reward animations and
 * the tiles are done; a Continue tap during the animations ends them ([EventPlayer.continueBattle]).
 */
object ContinueRule {
    /** What the Study screen's answer row shows. */
    enum class Row { SHOW_ANSWER, CONTINUE, ANSWERS }

    /** [flipped] = the answer side is up; [holding] = a result holds the next fight ([holding]). */
    fun row(flipped: Boolean, holding: Boolean): Row = when {
        holding -> Row.CONTINUE
        flipped -> Row.ANSWERS
        else -> Row.SHOW_ANSWER
    }

    /** What a request to flip the card or to answer it (key, swipe, tap on the card, card script, button) does. */
    enum class Act { RUN, CONTINUE }

    fun onRequest(holding: Boolean): Act = if (holding) Act.CONTINUE else Act.RUN

    /** The auto-advance countdown (show answer / answer by itself) runs only when no Continue is pending; it starts again in full after it. */
    fun autoMayRun(holding: Boolean): Boolean = !holding

    /**
     * Whether the Study screen is holding: exactly while a result holds the next fight, whatever the panel's visibility, so a flip, an
     * answer or the auto timer can never get through while a hold stays parked. A hidden panel (the type-in keyboard layout, the
     * summary) shows no Continue, so its hold is never left parked: it is released at once ([mustRelease]).
     */
    fun holding(resultHolds: Boolean): Boolean = resultHolds

    /** True = the result holds but the panel is not on screen: release the hold now (EventPlayer.continueBattle), nobody could tap Continue. */
    fun mustRelease(resultHolds: Boolean, panelVisible: Boolean): Boolean = resultHolds && !panelVisible

    /** Text of the button and its second line. */
    const val LABEL = "Continue"
    const val HINT = "next battle"
}
