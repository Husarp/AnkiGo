package com.husarp.ankigo.study

/**
 * PLAN 155 "New cards: one OK button" (Settings -> Study, off by default). On a NEW card (AnkiDroid queue 0) the answer row is
 * [ OK (wide) | Good | Easy ]: OK takes the place of Again and Hard (of Again alone on a card with 2 or 3 buttons). OK sends Again
 * to AnkiDroid, so the card stays in its learning steps; in the game it is a neutral answer (counted, no battle round, no combo
 * change). The card's NEXT showing (its first learning step) is gentle too; after that Again is normal. Pure: the screen asks
 * these functions and the bookkeeping ([GentleCards]) lives in memory plus the "study" prefs.
 *
 * The one rule: every way to answer (buttons, keys, swipes, the auto timer, card JavaScript) ends in the study screen's answer
 * gate, which asks [settle] once; so an ease that the OK row has no button for (Hard on a 4-button card) counts as OK as well.
 */
object NewCardOk {
    /** "study" prefs: the setting. */
    const val PREF = "newCardOk"
    /** "study" prefs: the gentle cards (see [GentleCards]). */
    const val GENTLE_PREF = "newCardOkGentle"

    const val LABEL = "OK"
    /** The second line of the OK button, where the other buttons show their next time. */
    const val HINT = "again soon"
    const val DESCRIPTION = "OK, you will see this card again soon"
    /** The battle's line for an OK answer. */
    const val LOG_LINE = "New card: OK. No attacks this time."

    /** The Settings row's one line ("New cards: one OK button" in Study; the catalog's limit is 90 characters, no final period). */
    const val SETTING_NOTE = "OK instead of Again and Hard on a new card; it comes back soon and nobody attacks"

    /** queue 1 = learning, 3 = day learning: where the first learning step of a card sits. */
    private val LEARNING = setOf(1, 3)

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

    /** True when the card shows the OK row: the setting is on and it is a new card ([queue] 0), or the gentle first learning step of one. */
    fun rowApplies(enabled: Boolean, queue: Int?, gentle: Boolean) = enabled && (queue == 0 || (gentle && queue in LEARNING))

    /** [ease] is what goes to AnkiDroid; [neutral] says the game gets a neutral answer. */
    data class Resolved(val ease: Int, val neutral: Boolean)

    /** What a tap of [ease] means when the OK row is [applies] or not: an ease the OK replaces is OK (ease 1, neutral). */
    fun resolve(applies: Boolean, buttons: Int, ease: Int) = if (applies && ease in 1..replaces(buttons)) Resolved(1, true) else Resolved(ease, false)

    /**
     * The whole decision for one answer made on the card [nid]/[ord] ([queue] = its queue when it was shown), and the bookkeeping:
     * an OK on a new card makes the card gentle for its next showing; any answer on a gentle card uses that up. Off: nothing at all.
     */
    fun settle(enabled: Boolean, queue: Int?, nid: Long, ord: Int, buttons: Int, ease: Int, gentle: GentleCards): Resolved {
        if (!enabled) return Resolved(ease, false)
        val wasGentle = gentle.has(nid, ord)
        val r = resolve(rowApplies(true, queue, wasGentle), buttons, ease)
        if (r.neutral && queue == 0) gentle.add(nid, ord) else if (wasGentle) gentle.remove(nid, ord)
        return r
    }

    /**
     * Companion mode (AnkiDroid shows its own buttons): with the setting on, an Again or Hard on a card that was new before that
     * answer is neutral. [wasNew] comes from the detector's ledger; there is no first-learning-step rule here.
     */
    fun companionNeutral(enabled: Boolean, wasNew: Boolean, button: Int) = enabled && wasNew && (button == 1 || button == 2)
}

/**
 * The cards (note id + ord) whose next showing is gentle: set when a new card got OK, used up by the next answer on it, dropped
 * after a day, at most [MAX] of them (the oldest go first). [raw] is the saved text; [save] gets the new text after each change.
 */
class GentleCards(raw: String?, private val save: (String) -> Unit, private val now: () -> Long = System::currentTimeMillis) {
    private val at = LinkedHashMap<Pair<Long, Int>, Long>()

    init {
        for (part in (raw ?: "").split(',')) {
            val f = part.split(':')
            val nid = f.getOrNull(0)?.toLongOrNull() ?: continue
            val ord = f.getOrNull(1)?.toIntOrNull() ?: continue
            val ms = f.getOrNull(2)?.toLongOrNull() ?: continue
            at[nid to ord] = ms
        }
    }

    fun has(nid: Long, ord: Int): Boolean { drop(); return (nid to ord) in at }

    fun add(nid: Long, ord: Int) {
        drop()
        at.remove(nid to ord)   // a card added again goes to the end (the newest)
        at[nid to ord] = now()
        while (at.size > MAX) at.remove(at.keys.first())
        save(text())
    }

    fun remove(nid: Long, ord: Int) { if (at.remove(nid to ord) != null) save(text()) }

    val size: Int get() { drop(); return at.size }

    fun text(): String = at.entries.joinToString(",") { "${it.key.first}:${it.key.second}:${it.value}" }

    /** Entries older than a day (or from the future: a clock that went back) are gone. */
    private fun drop() {
        val t = now()
        if (at.entries.removeAll { t - it.value > DAY_MS || it.value > t + DAY_MS }) save(text())
    }

    companion object {
        const val MAX = 40
        const val DAY_MS = 24 * 3_600_000L
    }
}
