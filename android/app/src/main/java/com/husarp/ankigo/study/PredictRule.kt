package com.husarp.ankigo.study

/**
 * R10-X1: may the card prepared as "next" show at once after an answer, or must the answer wait for AnkiDroid's real next card?
 * Pure rules, from how AnkiDroid's v3 scheduler builds and keeps its queue (Anki rslib `scheduler/queue`):
 *
 * - The order is: intraday learning cards due by the learning CUTOFF, then the main queue (new, review and interday learning cards,
 *   mixed once, when the queue was built), then the learning cards due within the learn-ahead limit (only once the main queue is empty).
 * - The cutoff moves to "now" only when a card is answered (or the queue is rebuilt). A learning card that comes due while a card is on
 *   screen therefore stays behind the main queue in every read, and jumps to the very front when the answer is applied: the card read
 *   second (the prediction) is not next any more. That is the swap, made long by the slow write of a lag phase.
 * - The answered card never comes back first while another card was read after it: a learning step puts it behind the main queue, and
 *   with the main queue empty AnkiDroid places it after the next learning card. So Again / Hard / learning answers still predict.
 * - Siblings: the queue is gathered with the same bury rule the answer applies, so a sibling read second is not buried by the answer.
 * - The read behind a prediction is never older than the previous answer (the load runs behind its write); only time can age it.
 *
 * So the answer waits when an intraday learning card other than the answered and the predicted one is due by the time AnkiDroid applies
 * the answer ([SLACK_MS] after the tap) and would sort before the predicted card; also when the learning cards could not be read, or
 * that read is older than [MAX_AGE_MS] (a day rollover or a change made in AnkiDroid rebuilds the queue: nothing is safe then).
 *
 * R19-R (278b): the wait is the one visible lag left (the card comes only with the real queue read, behind a write that can take seconds),
 * and in that case the learning read already says which card jumps to the front: the due learning card with the earliest due time. When
 * exactly one such card is the earliest, due by the tap or within [SLACK_MS] after it ([firstLearning]; Adam's 278b addition: a card due
 * seconds after the tap counts as next), it is prepared by id beside the next card ([learningToPrepare]) and shown at once as a
 * predicted card, like any other prediction; the confirm behind the write swaps it away if it was wrong (a write faster than the due
 * time), and nothing is written to the wrong card. A tie for the earliest time stays a wait.
 */
object PredictRule {
    enum class Why(val label: String) {
        SAFE("safe"),
        WAITING_DISABLED("waiting answers disabled"),
        LEARNING_DUE("learning card due"),
        OLD("old read"),
        UNREAD("learning cards unread"),
    }

    /**
     * An intraday learning card ([cardId]) and when it is due (epoch seconds, AnkiDroid's raw `due` of queue 1). R19-R: [key] = its note and
     * card number, when the read gave them (the card can be prepared by them).
     */
    data class Due(val cardId: Long, val dueSecs: Long, val key: StudyLoop.Key? = null)

    /** The studied deck's intraday learning cards as read at [at] (epoch ms) with a prepared card; [due] null = they could not be read. */
    data class LearnRead(val at: Long, val due: List<Due>?)

    /** AnkiDroid applies the answer (and moves its cutoff) a moment after the tap: up to a few seconds in a slow phase. */
    const val SLACK_MS = 5_000L
    const val MAX_AGE_MS = 10 * 60_000L

    /** The columns of the learning read (the official `cards` URI, AnkiDroid 2.24+). */
    val LEARN_COLS = arrayOf("_id", "queue", "due", "deck_id", "original_deck_id", "note_id", "ord")

    /** Queues whose `due` is a time of day in seconds: learning / relearning (1) and filtered-deck preview repeats (4). */
    private val INTRADAY = setOf("1", "4")

    /** Anki search syntax inside quotes: `\` and `"` escaped, `*` and `_` (deck-name wildcards) escaped too, so the name matches itself. */
    fun learnQuery(deckName: String): String {
        val esc = buildString { for (ch in deckName) { if (ch == '\\' || ch == '"' || ch == '*' || ch == '_') append('\\'); append(ch) } }
        return "deck:\"$esc\" is:learn"
    }

    /**
     * The learning read from the rows of [learnQuery] ([rows] null = the read failed). Only intraday cards count; a card of this deck that
     * sits in a filtered deck (an original deck and another deck than [deckId]) is not in this deck's queue. Unreadable rows are skipped.
     */
    fun learnRead(rows: List<Map<String, String?>>?, deckId: Long, at: Long): LearnRead {
        if (rows == null) return LearnRead(at, null)
        val due = rows.mapNotNull { r ->
            if (r["queue"] !in INTRADAY) return@mapNotNull null
            val odid = r["original_deck_id"]?.toLongOrNull() ?: 0L
            if (odid != 0L && r["deck_id"]?.toLongOrNull() != deckId) return@mapNotNull null
            val id = r["_id"]?.toLongOrNull() ?: return@mapNotNull null
            val nid = r["note_id"]?.toLongOrNull(); val ord = r["ord"]?.toIntOrNull()
            Due(id, r["due"]?.toLongOrNull() ?: return@mapNotNull null, if (nid != null && ord != null) StudyLoop.Key(nid, ord) else null)
        }
        return LearnRead(at, due)
    }

    /** May [next] (prepared, with its [Prepared.learning] read) show at once after [answered] is answered at [now] (epoch ms)? */
    fun check(answered: Prepared<*>, next: Prepared<*>, now: Long): Why {
        val read = next.learning ?: return Why.UNREAD
        val due = read.due ?: return Why.UNREAD
        if (now - read.at !in 0..MAX_AGE_MS) return Why.OLD // also a clock that went back
        return if (comingFirst(due, answered.before["_id"]?.toLongOrNull(), next, now + SLACK_MS).isNotEmpty()) Why.LEARNING_DUE else Why.SAFE
    }

    /**
     * The learning cards of [due] (the read of [next]) that would sort before [next] when AnkiDroid applies the answer by [by] (epoch ms):
     * all but the answered card ([answeredId]) and [next] itself, due by [by]. A learning card read second keeps its place before cards due
     * after it; a main-queue card goes behind every one that comes due.
     */
    private fun comingFirst(due: List<Due>, answeredId: Long?, next: Prepared<*>, by: Long): List<Due> {
        val skip = setOf(answeredId, next.before["_id"]?.toLongOrNull())
        val nextDue = if (next.before["queue"] in INTRADAY) next.before["due"]?.toLongOrNull() else null
        return due.filter { it.cardId !in skip && it.dueSecs * 1000 <= by && (nextDue == null || it.dueSecs < nextDue) }
    }

    /** The earliest of [cards] by due time; null when there is none or two share the earliest time (the order of equals is not certain). */
    private fun earliest(cards: List<Due>): Due? {
        val first = cards.minByOrNull { it.dueSecs } ?: return null
        return first.takeIf { cards.count { c -> c.dueSecs == it.dueSecs } == 1 }
    }

    /**
     * R19-R: is [learn] (a prepared learning card) the first card AnkiDroid gives after [answered] is answered at [now], in the case
     * [check] says LEARNING_DUE for [next]? When it is the earliest of the learning cards due by the tap or within [SLACK_MS] after it
     * (R19-FIX RV-1, Adam's 278b addition: AnkiDroid moves its cutoff to a moment at or after the tap, so a card due by the tap is in and a
     * card due seconds later most likely is; a wrong guess is swapped by the confirm), and alone at that time. Also its own state has to
     * agree with the read (still an intraday learning card with that due time).
     */
    fun firstLearning(answered: Prepared<*>, next: Prepared<*>, learn: Prepared<*>, now: Long): Boolean {
        if (check(answered, next, now) != Why.LEARNING_DUE) return false
        val due = next.learning?.due ?: return false
        val first = earliest(comingFirst(due, answered.before["_id"]?.toLongOrNull(), next, now + SLACK_MS)) ?: return false
        return first.cardId == learn.before["_id"]?.toLongOrNull() &&
            learn.before["queue"] in INTRADAY && learn.before["due"]?.toLongOrNull() == first.dueSecs
    }

    /**
     * R19-R: the learning card to prepare by id while [answered] (the confirmed card) is on screen and [next] is prepared behind it: the
     * earliest intraday learning card of the read, alone at that time, that can still be due while the read is valid (a tap later than
     * [MAX_AGE_MS] after the read waits anyway). Null = nothing to prepare, or no card id to prepare it by.
     */
    fun learningToPrepare(answered: StudyLoop.Key, next: Prepared<*>): Due? {
        val read = next.learning ?: return null
        val due = read.due ?: return null
        val nextId = next.before["_id"]?.toLongOrNull()
        val nextDue = if (next.before["queue"] in INTRADAY) next.before["due"]?.toLongOrNull() else null
        val able = due.filter { it.key != answered && it.cardId != nextId && it.dueSecs * 1000 <= read.at + MAX_AGE_MS && (nextDue == null || it.dueSecs < nextDue) }
        return earliest(able)?.takeIf { it.key != null }
    }

    /**
     * R18-A: may a card be shown AHEAD of a write that is not applied yet, after an answer that was itself made ahead? Only a main-queue card
     * (new, review, interday learning): the cards answered before it come back as learning cards, and when no main-queue card is left AnkiDroid
     * puts such a card right after the next learning card, so it jumps in front of a card read behind it (Again on A, then on B: A is
     * back before C). An intraday learning card ([INTRADAY] queue) as the next card is not safe then; that tap waits for the confirm.
     */
    fun aheadOk(next: Prepared<*>): Boolean = next.before["queue"] !in INTRADAY

    /** Why a prediction was swapped away, for the STUDY line: [top] is AnkiDroid's real next card, [queue] the read it came with. */
    fun swapCause(answered: StudyLoop.Key?, predicted: StudyLoop.Key, top: Prepared<*>, queue: List<StudyLoop.Key>): String = when {
        top.key == answered -> "the answered card came back"
        top.before["queue"] in INTRADAY -> "a learning card came due"
        predicted !in queue -> "the predicted card left the queue"
        else -> "AnkiDroid changed the order"
    }
}
