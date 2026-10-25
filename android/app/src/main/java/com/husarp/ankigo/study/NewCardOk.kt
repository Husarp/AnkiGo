package com.husarp.ankigo.study

/**
 * PLAN 155 "New cards: one OK button" (Settings -> Study, off by default). On a NEW card (AnkiDroid queue 0) the answer row is
 * [ OK (wide) | Good | Easy ]: OK takes the place of Again and Hard (of Again alone on a card with 2 or 3 buttons). OK sends Again
 * to AnkiDroid, so the card stays in its learning steps; in the game it is a neutral answer (R19-T, PLAN 284: counted, it fills a grey
 * turn dot as one of the round's answers but has no power; no combo change). PLAN 283 / R19: only the first showing as a new card gets OK. Every learning showing uses the normal row.
 * No per-card state is kept; old saved gentle entries are ignored, including after a restart ([dropOldGentle] removes the old key once).
 *
 * The one rule: every way to answer (buttons, keys, swipes, the auto timer, card JavaScript) ends in the study screen's answer
 * gate, which asks [settle] once; so an ease that the OK row has no button for (Hard on a 4-button card) counts as OK as well.
 */
object NewCardOk {
    /** "study" prefs: the setting. */
    const val PREF = "newCardOk"

    /** "study" prefs: the key of the old per-card "gentle" bookkeeping (before PLAN 283), written by older versions; nothing reads it any more. */
    const val OLD_GENTLE_PREF = "newCardOkGentle"

    /** R19-FIX (NICE-8): removes [OLD_GENTLE_PREF] from the "study" prefs [prefs] once (no write when it is not there). True when it was removed. */
    fun dropOldGentle(prefs: android.content.SharedPreferences): Boolean {
        if (!prefs.contains(OLD_GENTLE_PREF)) return false
        prefs.edit().remove(OLD_GENTLE_PREF).apply()
        return true
    }

    const val LABEL = "OK"
    /** The second line of the OK button, where the other buttons show their next time. */
    const val HINT = "again soon"
    const val DESCRIPTION = "OK, you will see this card again soon"
    /** The battle's line for an OK answer (R19-T: it fills a dot, so it no longer says that nothing happens). */
    const val LOG_LINE = "New card: OK. It counts as an answer, with no power."

    /** The Settings row's one line ("New cards: one OK button" in Study; the catalog's limit is 90 characters). */
    const val SETTING_NOTE = "OK instead of Again and Hard on a new card's first showing; it comes back soon"

    /** How many of the card's first buttons the OK replaces: Again + Hard on a 4-button card, Again on a 2- or 3-button one. */
    fun replaces(buttons: Int) = if (buttons >= 4) 2 else 1

    /** One button of the answer row. [ease] is what AnkiDroid is asked for; [ok] = the OK button (the next time is [HINT]). */
    data class Btn(val label: String, val ease: Int, val ok: Boolean = false)

    /** The row for a card with [buttons] answer buttons: the usual one, or with [ok] the OK row. */
    fun row(buttons: Int, ok: Boolean): List<Btn> {
        val labels = when (buttons) { 2 -> listOf("Again", "Good"); 3 -> listOf("Again", "Good", "Easy"); else -> listOf("Again", "Hard", "Good", "Easy") }
        val all = labels.mapIndexed { i, l -> Btn(l, i + 1) }
        return if (ok) listOf(Btn(LABEL, 1, ok = true)) + all.drop(replaces(buttons)) else all
    }

    /** PLAN 283: only queue 0 is new. Learning, review and unknown queues always use the normal row. */
    fun rowApplies(enabled: Boolean, queue: Int?) = enabled && queue == 0

    /** [ease] is what goes to AnkiDroid; [neutral] says the game gets a neutral answer. */
    data class Resolved(val ease: Int, val neutral: Boolean)

    /** What a tap of [ease] means when the OK row is [applies] or not: an ease the OK replaces is OK (ease 1, neutral). */
    fun resolve(applies: Boolean, buttons: Int, ease: Int) = if (applies && ease in 1..replaces(buttons)) Resolved(1, true) else Resolved(ease, false)

    /** PLAN 283: the shared answer gate uses the same queue-only rule as the row, with no gentle bookkeeping. */
    fun settle(enabled: Boolean, queue: Int?, buttons: Int, ease: Int): Resolved =
        resolve(rowApplies(enabled, queue), buttons, ease)

    /**
     * Companion mode (AnkiDroid shows its own buttons): with the setting on, an Again or Hard on a card that was new before that
     * answer is neutral. [wasNew] comes from the detector's ledger; there is no first-learning-step rule here.
     */
    fun companionNeutral(enabled: Boolean, wasNew: Boolean, button: Int) = enabled && wasNew && (button == 1 || button == 2)
}
