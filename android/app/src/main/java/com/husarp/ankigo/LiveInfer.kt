package com.husarp.ankigo

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min

/** One card's scheduling state, read from the OFFICIAL `cards` URI (AnkiDroid 2.24+). */
data class CardState(
    val id: Long, val nid: Long, val did: Long, val odid: Long,
    val reps: Int, val lapses: Int, val type: Int, val queue: Int, val due: Long,
    val ivl: Int, val factor: Int, val left: Int,
    val stab: Double?, val diff: Double?, val lastRev: Long?,
) {
    fun short() = "${TYPES.getOrElse(type) { "t$type" }} q$queue ivl=$ivl f=$factor left=$left due=$due" +
        (if (stab != null) " S=${"%.2f".format(stab)} D=${"%.3f".format(diff ?: 0.0)}" else "")

    companion object {
        val COLS = arrayOf(
            "_id", "note_id", "deck_id", "original_deck_id", "reps", "lapses", "type", "queue", "due",
            "interval", "sm2_factor", "left", "fsrs_stability", "fsrs_difficulty", "last_review_time_secs",
        )

        fun from(m: Map<String, String?>): CardState? {
            val id = m["_id"]?.toLongOrNull() ?: return null
            val reps = m["reps"]?.toIntOrNull() ?: return null // card deleted after the search
            fun l(k: String) = m[k]?.toLongOrNull() ?: 0L
            fun i(k: String) = m[k]?.toIntOrNull() ?: 0
            return CardState(
                id, l("note_id"), l("deck_id"), l("original_deck_id"), reps, i("lapses"), i("type"), i("queue"), l("due"),
                i("interval"), i("sm2_factor"), i("left"), m["fsrs_stability"]?.toDoubleOrNull(), m["fsrs_difficulty"]?.toDoubleOrNull(),
                m["last_review_time_secs"]?.toLongOrNull(),
            )
        }

        /** A card that had never been studied (used when a card first shows up in rated:1 with reps=1). */
        fun fresh(n: CardState) = n.copy(reps = 0, lapses = 0, type = 0, queue = 0, ivl = 0, factor = 0, left = 0, stab = null, diff = null, lastRev = null)
    }
}

/** Deck options that matter for inference: learning/relearning steps (minutes), graduating/easy interval (days). */
data class Steps(val learn: List<Double>, val relearn: List<Double>, val gradIvl: Int, val easyIvl: Int)

/** button: 1 Again, 2 Hard, 3 Good, 4 Easy, 0 unknown. conf: SURE / LIKELY / GUESS. */
data class Guess(val button: Int, val conf: String, val why: String)

const val SURE = "sure"
const val LIKELY = "likely"
const val GUESS = "guess"
val BUTTONS = arrayOf("?", "Again", "Hard", "Good", "Easy")
val TYPES = arrayOf("new", "learning", "review", "relearning")

// ---- Tuning knobs ----
/** FSRS: |normalised difficulty change| above this = Hard/Easy, below = Good. Default FSRS-6 w6 ≈ 3.0; users' w6 rarely < 0.5. */
private const val FSRS_D_THRESHOLD = 0.25
/** FSRS-6 default w7 (mean reversion) and D0(Easy); only used to subtract the tiny drift every answer gets. */
private const val FSRS_W7 = 0.001
private const val FSRS_D0_EASY = -4.77
/** SM-2: ease change that counts as Hard (−150) / Easy (+150). */
private const val SM2_EASE_STEP = 100
/** SM-2 at the 130% ease floor: Hard grows the interval ~1.2×, Good ≥1.3×. */
private const val SM2_FLOOR_RATIO = 1.25

/**
 * Infers which button was pressed from the card's state before (p) and after (n) ONE answer.
 * Official API data only. nowSec is used when the card has no last_review_time.
 *
 * Signals, strongest first:
 *  1. lapses +1 → Again (a review card lapsed). Always true.
 *  2. Review cards: back to (re)learning → Again; else SM-2 ease change (−150 Hard, 0 Good, +150 Easy),
 *     or FSRS difficulty change (up Hard, ~0 Good, down Easy).
 *  3. New/learning/relearning cards: remaining steps (`left`): one fewer → Good, reset → Again,
 *     same (not first step) → Hard, graduated early → Easy. On the first step Again and Hard both stay put;
 *     then the next due time tells them apart (Again = step 1, Hard = avg(step 1, step 2) or 1.5× step 1).
 */
fun inferButton(p: CardState, n: CardState, steps: Steps?, nowSec: Long): Guess {
    if (n.lapses > p.lapses) return Guess(1, SURE, "lapses ${p.lapses}→${n.lapses}")
    return if (p.type == 2) inferReview(p, n) else inferLearning(p, n, steps, nowSec)
}

private fun inferReview(p: CardState, n: CardState): Guess {
    if (n.type == 1 || n.type == 3) return Guess(1, SURE, "review → relearning")
    if (n.type != 2) return Guess(0, GUESS, "review → type ${n.type} (reset?)")
    val ratio = n.ivl.toDouble() / maxOf(1, p.ivl)
    val pS = p.stab; val nS = n.stab; val pD = p.diff; val nD = n.diff
    val fsrs = nS != null && nS != pS
    if (fsrs) {
        if (pS == null || pD == null || nD == null) return Guess(3, GUESS, "FSRS, no earlier memory state; ivl ×${"%.2f".format(ratio)}")
        // FSRS difficulty: D' = D − w6·(G−3)·(10−D)/9, then a tiny pull toward D0(Easy) (w7).
        // Remove the pull, divide by the damping (10−D)/9 → about +w6 for Hard, 0 for Good, −w6 for Easy.
        val damping = (10.0 - pD) / 9.0
        if (damping < 0.01) return Guess(3, GUESS, "FSRS difficulty saturated at ${"%.2f".format(pD)}; ivl ×${"%.2f".format(ratio)}")
        val drift = FSRS_W7 * (FSRS_D0_EASY - pD)
        val x = (nD - pD - drift) / damping
        val why = "FSRS D ${"%.3f".format(pD)}→${"%.3f".format(nD)} (x=${"%.2f".format(x)}), S ${"%.1f".format(pS)}→${"%.1f".format(nS)}"
        return when {
            x > FSRS_D_THRESHOLD -> Guess(2, LIKELY, why)
            x < -FSRS_D_THRESHOLD -> Guess(4, LIKELY, why)
            else -> Guess(3, LIKELY, why)
        }
    }
    // SM-2
    val dF = n.factor - p.factor
    return when {
        dF <= -SM2_EASE_STEP -> Guess(2, SURE, "ease ${p.factor}→${n.factor}")
        dF >= SM2_EASE_STEP -> Guess(4, SURE, "ease ${p.factor}→${n.factor}")
        p.factor <= 1300 -> // at the ease floor Hard can't lower ease; fall back on interval growth
            if (ratio < SM2_FLOOR_RATIO) Guess(2, GUESS, "ease floor, ivl ×${"%.2f".format(ratio)}") else Guess(3, GUESS, "ease floor, ivl ×${"%.2f".format(ratio)}")
        else -> Guess(3, SURE, "ease unchanged ${n.factor}, ivl ${p.ivl}→${n.ivl}")
    }
}

private fun inferLearning(p: CardState, n: CardState, steps: Steps?, nowSec: Long): Guess {
    val list = if (p.type == 3) steps?.relearn else steps?.learn
    val total = list?.size
    // Steps left before this answer. A new card has all of them left.
    val pl = if (p.type == 0) total else p.left % 1000
    val nl = n.left % 1000
    val fsrsUsed = n.stab != null && n.stab != p.stab

    if (n.type == 2) { // graduated to review
        if (pl != null && pl > 1) return Guess(4, SURE, "graduated with $pl steps left")
        if (!fsrsUsed && steps != null) {
            if (p.type == 3) { // relearning: Good keeps the lapse interval, Easy adds a day
                return if (n.ivl > p.ivl) Guess(4, LIKELY, "relearn graduated ivl ${p.ivl}→${n.ivl}") else Guess(3, LIKELY, "relearn graduated ivl ${p.ivl}→${n.ivl}")
            }
            if (steps.easyIvl > steps.gradIvl) {
                val easy = n.ivl >= (steps.gradIvl + steps.easyIvl) / 2.0
                return Guess(if (easy) 4 else 3, LIKELY, "graduated ivl=${n.ivl} (good=${steps.gradIvl}, easy=${steps.easyIvl})")
            }
        }
        return Guess(3, GUESS, "graduated from last step (Good or Easy) ivl=${n.ivl}")
    }
    if (n.type == 0) return Guess(0, GUESS, "card is new again (reset?)")

    if (pl != null) {
        if (nl < pl) return Guess(3, SURE, "steps left $pl→$nl")
        if (nl > pl) return Guess(1, SURE, "steps left $pl→$nl (back to start)")
        if (total != null && pl < total) return Guess(2, SURE, "same step ($pl of $total left)")
    }
    // First step (or steps unknown): Again and Hard both keep the card there. Use the delay Anki picked.
    if (!list.isNullOrEmpty() && n.queue == 1) {
        val reviewed = n.lastRev ?: nowSec
        val delayMin = (n.due - reviewed) / 60.0
        val again = list[0]
        val hard = if (list.size >= 2) (list[0] + list[1]) / 2 else min(list[0] * 1.5, list[0] + 1440)
        if (delayMin > 0 && again != hard) {
            val isAgain = abs(ln(delayMin / again)) <= abs(ln(delayMin / hard))
            return Guess(if (isAgain) 1 else 2, LIKELY, "first step, next in ${"%.1f".format(delayMin)} min (Again≈$again, Hard≈$hard)")
        }
    }
    return Guess(1, GUESS, "Again or Hard on first step (can't tell)")
}
