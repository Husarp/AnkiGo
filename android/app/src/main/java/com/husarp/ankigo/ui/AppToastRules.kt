package com.husarp.ankigo.ui

/** Pure timing / placement rules of the in-app message ([AppToast]); no Android classes, so they are unit-tested on the JVM. */
object AppToastRules {
    const val FADE_MS = 150L
    const val SLIDE_DP = 12
    const val MAX_WIDTH_DP = 360
    const val SIDE_MARGIN_DP = 16
    const val BOTTOM_DP = 96
    private const val BASE_MS = 2200L
    private const val MAX_MS = 5000L
    private const val FREE_CHARS = 40
    private const val MS_PER_CHAR = 30L

    /** How long a message stays: 2.2 s, plus 30 ms for every character over 40, at most 5 s. */
    fun showMs(text: String): Long = (BASE_MS + maxOf(0, text.length - FREE_CHARS) * MS_PER_CHAR).coerceAtMost(MAX_MS)

    /** Width of the message in dp: at most 360, and 16 dp of margin on each side of a narrow window. */
    fun widthDp(windowWidthDp: Int): Int = (windowWidthDp - 2 * SIDE_MARGIN_DP).coerceIn(0, MAX_WIDTH_DP)

    /**
     * Distance from the bottom of the window in dp: 96 in a normal window (above the bottom bar / answer row), less in a
     * short one (floating card, split screen: 11 % of the height, at least 16). With the keyboard open ([imeDp] > 0) it
     * sits 16 dp above the keyboard when that is higher.
     */
    fun bottomDp(windowHeightDp: Int, imeDp: Int = 0): Int {
        val base = if (windowHeightDp >= 600) BOTTOM_DP else maxOf(16, windowHeightDp * 11 / 100)
        return if (imeDp > 0) maxOf(base, imeDp + 16) else base
    }

    /** No slide and no fade: animators are off in the system, or "Reduce motion" is on in the app. */
    fun still(animatorsEnabled: Boolean, reduceMotionPref: Boolean): Boolean = !animatorsEnabled || reduceMotionPref

    /** Where a message goes, see [place]. */
    enum class Place { CALLER, OTHER_ACTIVITY, FLOAT, WAIT, NONE }

    /**
     * Which window takes a message: the caller's own screen when it is alive; else (it is finishing, e.g. "saved, then finish()") any other
     * live host first: another AnkiGo screen in front, then an attached floating window; only when there is none the message waits for the
     * next screen that comes to the front ([WAIT]; the caller then falls back to the system Toast when nothing came in time). [NONE] = no
     * Activity at all and no floating window: the caller falls back to the system Toast at once.
     */
    fun place(hasActivity: Boolean, callerFinishing: Boolean, otherActivity: Boolean, floating: Boolean): Place = when {
        hasActivity && !callerFinishing -> Place.CALLER
        otherActivity -> Place.OTHER_ACTIVITY
        floating -> Place.FLOAT
        hasActivity -> Place.WAIT
        else -> Place.NONE
    }

    /** How long a message of a finishing screen waits for the next screen in front before it goes to the system Toast. */
    const val WAIT_MS = 3000L

    /** True when a waiting message asked for at [askedAt] is too old for a screen that comes to the front at [now] (ms, same clock). */
    fun waitOver(askedAt: Long, now: Long): Boolean = now - askedAt > WAIT_MS
}
