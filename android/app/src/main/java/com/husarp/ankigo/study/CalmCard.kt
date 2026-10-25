package com.husarp.ankigo.study

/**
 * R19-N (PLAN 130, pick A "Quiet words"): while a fight result holds the next battle ("Continue" on the answer row), the next card is not
 * shown. The card area says so calmly instead, and nothing of that card runs: its answer clock (the top pill, the time sent with the
 * answer), its auto-advance countdown, its spoken text / sounds and the type-in keyboard all start when Continue was tapped and the card
 * shows. Pure rule, no Android.
 */
object CalmCard {
    /** The placeholder's words: the headline and the hint, which names the real button ([com.husarp.ankigo.ui.ContinueRule.LABEL]). */
    const val TITLE = "Battle over"
    const val HINT = "Tap Continue for the next card"
    const val DESCRIPTION = "$TITLE. $HINT."

    /**
     * Whether the calm card replaces the card: a result [holding] the next battle, with a card that was not seen yet. Not once its answer
     * side is up ([flipped]: the hold came late and the user is already reading it, it stays), not on the summary ([finished]) and not
     * while the deck's end waits for "Finish deck" ([finishWait]).
     */
    fun on(holding: Boolean, hasCard: Boolean, flipped: Boolean, finished: Boolean, finishWait: Boolean) =
        holding && hasCard && !flipped && !finished && !finishWait

    /** The answer clock may run only while the card is on screen (and the screen in front): [calm] and a screen in the [background] stop it. */
    fun clockMayRun(calm: Boolean, background: Boolean) = !calm && !background

    /** The time the visible pill shows: 0 while the calm card waits, else the clock's [takenMs]. */
    fun pillMs(calm: Boolean, takenMs: Long) = if (calm) 0L else takenMs
}
