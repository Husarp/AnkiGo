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
 */
object PredictRule {
    enum class Why(val label: String) {
        SAFE("safe"),
        LEARNING_DUE("learning card due"),
        OLD("old read"),
        UNREAD("learning cards unread"),
    }

    /** An intraday learning card ([cardId]) and when it is due (epoch seconds, AnkiDroid's raw `due` of queue 1). */
    data class Due(val cardId: Long, val dueSecs: Long)

    /** The studied deck's intraday learning cards as read at [at] (epoch ms) with a prepared card; [due] null = they could not be read. */
    data class LearnRead(val at: Long, val due: List<Due>?)

    /** AnkiDroid applies the answer (and moves its cutoff) a moment after the tap: up to a few seconds in a slow phase. */
    const val SLACK_MS = 5_000L
    const val MAX_AGE_MS = 10 * 60_000L

    /** The columns of the learning read (the official `cards` URI, AnkiDroid 2.24+). */
    val LEARN_COLS = arrayOf("_id", "queue", "due", "deck_id", "original_deck_id")

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
            Due(id, r["due"]?.toLongOrNull() ?: return@mapNotNull null)
        }
        return LearnRead(at, due)
    }

    /** May [next] (prepared, with its [Prepared.learning] read) show at once after [answered] is answered at [now] (epoch ms)? */
    fun check(answered: Prepared<*>, next: Prepared<*>, now: Long): Why {
        val read = next.learning ?: return Why.UNREAD
        val due = read.due ?: return Why.UNREAD
        if (now - read.at !in 0..MAX_AGE_MS) return Why.OLD // also a clock that went back
        val skip = setOf(answered.before["_id"]?.toLongOrNull(), next.before["_id"]?.toLongOrNull())
        // a learning card read second keeps its place before cards due after it; a main-queue card goes behind every one that comes due
        val nextDue = if (next.before["queue"] in INTRADAY) next.before["due"]?.toLongOrNull() else null
        val by = now + SLACK_MS
        val first = due.any { it.cardId !in skip && it.dueSecs * 1000 <= by && (nextDue == null || it.dueSecs < nextDue) }
        return if (first) Why.LEARNING_DUE else Why.SAFE
    }

    /** Why a prediction was swapped away, for the STUDY line: [top] is AnkiDroid's real next card, [queue] the read it came with. */
    fun swapCause(answered: StudyLoop.Key?, predicted: StudyLoop.Key, top: Prepared<*>, queue: List<StudyLoop.Key>): String = when {
        top.key == answered -> "the answered card came back"
        top.before["queue"] in INTRADAY -> "a learning card came due"
        predicted !in queue -> "the predicted card left the queue"
        else -> "AnkiDroid changed the order"
    }
}
