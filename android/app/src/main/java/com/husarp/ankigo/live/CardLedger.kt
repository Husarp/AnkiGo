package com.husarp.ankigo.live

import com.husarp.ankigo.CardState
import com.husarp.ankigo.GUESS
import com.husarp.ankigo.Guess
import com.husarp.ankigo.Steps
import com.husarp.ankigo.inferButton

/**
 * The detector's memory: the state each card had when it was last read, and the answers AnkiGo made itself ([own]).
 * It is the one place that decides whether a rep change is an own answer (the study screen counts those itself) or
 * one to report, so the two orders "a poll sees the rep change first" and "[absorb] runs first" can be tested without
 * a phone. Only the detector's worker thread touches [cards] and [dirty]; [own] is thread-safe.
 */
class CardLedger(val own: OwnAnswers = OwnAnswers()) {
    val cards = HashMap<Long, CardState>()

    /** The card map changed since the snapshot was last encoded. */
    var dirty = false

    /**
     * A poll read [n]. Reports what changed against the remembered state into [out]: an undo, or the answers that are
     * not own ones. [ratedToday] = the ids rated today (a card never seen before only counts when it is in there).
     * [nowMs] is the clock of [OwnAnswers] (elapsedRealtime), [nowSec] the wall clock in seconds for the button guess.
     */
    fun compare(n: CardState, ratedToday: Set<Long>, steps: (CardState) -> Steps?, nowMs: Long, nowSec: Long, out: MutableList<LiveDetector.Event>) {
        val old = cards[n.id]
        if (old != n) dirty = true
        cards[n.id] = n
        // No earlier state: a new card answered for the first time (reps=1) starts from "new";
        // anything else (e.g. review-ahead) counts as one answer with an unknown button.
        val p = old ?: when {
            n.id !in ratedToday || n.reps < 1 -> return
            n.reps == 1 -> CardState.fresh(n)
            else -> n.copy(reps = n.reps - 1)
        }
        val d = n.reps - p.reps
        if (d == 0) return
        if (d < 0) { out += LiveDetector.Event.Undo(n.id, -d); return }
        val suppressed = own.consume(n.id, p.reps, n.reps, nowMs)
        val remaining = d - suppressed
        if (remaining <= 0) return
        val g0 = if (old == null && n.reps > 1) Guess(0, GUESS, "no earlier state for this card")
        else if (suppressed > 0) Guess(0, GUESS, "own and external answers in the same poll")
        else inferButton(p, n, steps(n), nowSec)
        // Several answers between polls: only the first can be inferred, and only as a guess.
        val guess = if (d > 1) g0.copy(conf = GUESS) else g0
        // PLAN 155: a card whose state before was new; with an own answer in the same poll the reported one came after it
        out += LiveDetector.Event.Answer(n.id, guess.button, guess.conf, false, wasNew = suppressed == 0 && p.queue == 0 && (old != null || n.reps == 1))
        repeat(remaining - 1) { out += LiveDetector.Event.Answer(n.id, 0, GUESS, false) }
    }

    /**
     * An own answer of the study screen was saved and verified: [row] is the card's state after it. Settles the
     * expectation (when a poll has not already) and remembers the new state, so no later poll reports it again; a card
     * that already moved further (another answer meanwhile) is never taken back.
     */
    fun absorb(row: CardState, nowMs: Long) {
        own.consume(row.id, row.reps - 1, row.reps, nowMs)
        val old = cards[row.id]
        if (old == null || old.reps <= row.reps) cards[row.id] = row
    }

    /** Today's rated cards only, as saved with the game. */
    fun snapshot(ratedToday: Set<Long>): String {
        dirty = false
        return encode(cards.filterKeys { it in ratedToday })
    }
}
