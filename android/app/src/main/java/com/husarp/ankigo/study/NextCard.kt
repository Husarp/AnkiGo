package com.husarp.ankigo.study

/**
 * A card ready to show without a provider call: [key], the card's state as AnkiDroid gave it ([before], the
 * [com.husarp.ankigo.CardState.COLS] row; it is what an answer is checked against) and [view], everything else 1b prepared
 * (the Review with the buttons and next times, html, note fields, model css, media check, tts…). [learning] (R10-X1): the deck's
 * learning cards read with a card prepared as the prediction ([PredictRule.check] decides with it; null = not read).
 */
data class Prepared<out T>(val key: StudyLoop.Key, val before: Map<String, String?>, val view: T, val learning: PredictRule.LearnRead? = null)

/** What the io thread does with a `Load(token, predicted)`, from AnkiDroid's queue (limit 3) read after every earlier write. */
object LoadPlan {
    sealed class Step {
        /** Nothing is due any more. */
        object Finished : Step()

        /** The top card is the one that was predicted: NO content reads, just `confirmed` with the fresh Review. */
        data class Confirm(val key: StudyLoop.Key) : Step()

        /** The top card is not the predicted one (or nothing was predicted): read it, then `loaded`. */
        data class Fetch(val key: StudyLoop.Key) : Step()
    }

    fun decide(queue: List<StudyLoop.Key>, predicted: StudyLoop.Key?): Step {
        val top = queue.firstOrNull() ?: return Step.Finished
        return if (predicted != null && top == predicted) Step.Confirm(top) else Step.Fetch(top)
    }
}

/** What the screen / the io thread must do after an event (see [NextCard]). */
sealed class Cmd<out T> {
    /** Io: [LoadPlan] on the queue, queued AFTER the write task of the answer just made. Its result carries [token]. */
    data class Load(val token: Int, val predicted: StudyLoop.Key?) : Cmd<Nothing>()

    /**
     * Io: prepare [key] (the card after [forKey]) and report `prepared(forKey, forToken, …)`. R18-A: up to [NextCard.maxAhead] + 1 of these
     * follow a confirmed card, in queue order (each one's [forKey] is the previous one's [key]); the screen can answer that many cards ahead.
     */
    data class Prepare(val key: StudyLoop.Key, val forKey: StudyLoop.Key, val forToken: Int) : Cmd<Nothing>()

    /** Main: `queue.enqueue(answer)` and then post `drain()` to io, BEFORE the [Load] that follows in the same list. */
    data class Enqueue(val answer: QueuedAnswer) : Cmd<Nothing>()

    /** Show [card] on its QUESTION side; [predicted] = AnkiDroid has not confirmed it yet (R18-A: it can be flipped; a tap on it is answered ahead of the write or held, see [NextCard]). */
    data class Show<T>(val card: Prepared<T>, val predicted: Boolean) : Cmd<T>()

    /** The predicted card on screen is the real one: refresh buttons / next times from [card], keep the side the user is on (R18-A: "Show answer" is never held, so the user may be on either side). */
    data class Confirm<T>(val card: Prepared<T>) : Cmd<T>()

    /** No card to show yet (nothing predicted): the usual waiting state. */
    object ShowWaiting : Cmd<Nothing>()

    /** A tap on a predicted card was held: show it as pressed and "saving"; it acts when the card is confirmed. */
    data class ShowHeld(val ease: Int) : Cmd<Nothing>()

    object ShowFinished : Cmd<Nothing>()
    data class ShowError(val message: String) : Cmd<Nothing>()

    /** The tap could not be turned into an answer (the card's state was unreadable); nothing was queued. */
    data class Refuse(val reason: String) : Cmd<Nothing>()
}

/**
 * The card slot of the study screen: which card is shown and whether AnkiDroid has confirmed it. Main thread only, pure.
 *
 * ```
 *  LOADING(token)              waiting for a load
 *  CONFIRMED(card, token)      AnkiDroid's real top card: answers and card actions act on it
 *  PREDICTED(card, token, afterSeq, held?)  the card expected after answer [afterSeq]; the confirm runs behind that write
 *  FINISHED / ERROR(msg)
 * ```
 * Every load result carries the token it was asked with; a result for an old token changes nothing. A swap always lands on the
 * question side. A prepared card (the prediction) is kept only for the card it was made for and only while that card is CONFIRMED,
 * and is used by the very next answer, only when [check] says it is safe (R10-X1, [PredictRule]); else that answer waits for the
 * real card. Card actions (bury, suspend, flag, move, delete, edit) only on [actionCard], which is null unless CONFIRMED.
 *
 * R18-A (278, "it lags while an answer is saved"): everything that needs AnkiDroid's CONFIRMATION waits for the confirming read,
 * and that read sits on the one io thread BEHIND the answer's write, which can take seconds in a slow phase (kernel throttling of
 * AnkiDroid's storage). So nothing the user does may wait for it:
 *  - "Show answer" on a PREDICTED card is never held: the answer side is in the prepared card. If the prediction turns out wrong, the
 *    swap puts the real card on its question side (PredictRule keeps that rare: a prediction is only shown when it is safe). An answer
 *    already made on that card is written to that card (never to another one) by the not-first path below, or not written, and told.
 *  - A tap on a PREDICTED card is answered at once when [maxAhead] allows and the card after it is prepared, safe to show ([PredictRule.check])
 *    and a main-queue card ([PredictRule.aheadOk]: the cards answered before it come back as learning cards, which may jump in front of a
 *    learning card read behind them):
 *    the answer is queued behind the earlier one, the card after it shows, and only the LAST answer's load confirms. The write of every
 *    answer still checks that its card is first in AnkiDroid's queue right before it is written ([AnswerQueue]); a card that is not (the
 *    prediction was wrong) is not written the usual way (PLAN 195's not-first path decides) and the final load swaps the screen to the real card.
 *    Else (the cap is reached, nothing safe to show next) the tap is held as before and acts when AnkiDroid confirms that card.
 * Up to [maxAhead] + 1 cards after the confirmed one are prepared (queue order, [Cmd.Prepare]) so the chain of answers has cards to show.
 * [maxAhead] = 0 is the old behaviour (every tap on a predicted card is held).
 */
class NextCard<T>(
    private val deckId: Long,
    /** Sequence numbers for answers: `queue::nextSeq`. */
    private val newSeq: () -> Long,
    private val now: () -> Long = { System.currentTimeMillis() },
    /** R18-A: how many answers may be made on cards AnkiDroid has not confirmed yet, one after the other (the app passes [AHEAD]; 0 = every such tap is held). */
    val maxAhead: Int = 0,
    /** R10-X1: may the prepared next card show at once (`PredictRule::check`)? Anything but SAFE makes the answer wait. Default: always. */
    private val check: (answered: Prepared<T>, next: Prepared<T>, now: Long) -> PredictRule.Why = { _, _, _ -> PredictRule.Why.SAFE },
) {
    sealed class Slot<out T> {
        data class Loading(val token: Int) : Slot<Nothing>()
        data class Confirmed<T>(val card: Prepared<T>, val token: Int) : Slot<T>()
        data class Predicted<T>(val card: Prepared<T>, val token: Int, val afterSeq: Long, val held: Held? = null) : Slot<T>()
        object Finished : Slot<Nothing>()
        data class Error(val message: String) : Slot<Nothing>()
    }

    data class Held(val ease: Int, val tookMs: Long)

    /** The cards prepared for the queue positions after [base] (the card on screen), in order; valid for load [token] only. */
    private class Chain<T>(val base: StudyLoop.Key, val token: Int, val cards: ArrayList<Prepared<T>>)

    var slot: Slot<T> = Slot.Loading(0)
        private set
    private var token = 0
    private var chain: Chain<T>? = null
    private var answered: StudyLoop.Key? = null // the card of the last answer (for the swap's cause)
    private var ahead = 0 // R18-A: answers made on unconfirmed cards since the last confirmed one
    private val burst = ArrayList<Long>() // R18-A: ids of the cards answered since the last confirmed one (their learning steps are not "due cards")

    /** For the STUDY line: predictions shown / confirmed / swapped away, answers with nothing prepared, held taps dropped. */
    var predicted = 0
        private set
    var hits = 0
        private set
    var misses = 0
        private set
    var noPrediction = 0
        private set
    var heldDropped = 0
        private set

    /** R18-A: answers made on a card AnkiDroid had not confirmed yet (shown ahead of the write instead of held). */
    var answeredAhead = 0
        private set

    /** R10-X1: answers whose prepared next card [check] held back, by reason. */
    val withheld: Map<PredictRule.Why, Int> get() = waitsBy
    private val waitsBy = LinkedHashMap<PredictRule.Why, Int>()

    /** Why the last answer waited for its next card ("" = it showed the prediction); the cause of the last swap. */
    var lastWait = ""
        private set
    var lastSwap = ""
        private set

    /** This visit's predictions and waits in one line, e.g. "predicted 12 (hit 11, swap 1), waited 3 (learning card due 2, nothing prepared 1)". */
    fun tally(): String {
        val waits = withheld.filterValues { it > 0 }.map { (why, n) -> why.label to n } + listOfNotNull(if (noPrediction > 0) NOTHING to noPrediction else null)
        return "predicted $predicted (hit $hits, swap $misses), waited ${waits.sumOf { it.second }}" +
            (if (waits.isEmpty()) "" else waits.joinToString(", ", " (", ")") { "${it.first} ${it.second}" }) +
            (if (answeredAhead > 0) ", answered before the confirm $answeredAhead" else "") +
            (if (heldDropped > 0) ", held taps dropped $heldDropped" else "")
    }

    /** The card the card actions may act on: only a CONFIRMED one. */
    fun actionCard(): Prepared<T>? = (slot as? Slot.Confirmed<T>)?.card

    /** The seq of the answer whose write the shown predicted card is waiting behind, else null. */
    fun waitingFor(): Long? = (slot as? Slot.Predicted<T>)?.afterSeq

    /** The screen starts, resumes, Retry, or a card was edited: forget everything and ask again. */
    fun reload(): List<Cmd<T>> {
        dropHeld()
        forgetChain()
        token++
        slot = Slot.Loading(token)
        return listOf(Cmd.Load(token, null))
    }

    /**
     * The screen is closing: the held tap is dropped (it was never an answer), the prediction is forgotten and the token moves on, so a
     * confirmation or a load that is still on its way changes nothing and can never queue an answer after the user left. Nothing is asked
     * of the io thread. Returns the new token (the screen's `generation` follows it).
     */
    fun leave(): Int {
        dropHeld()
        forgetChain()
        token++
        slot = Slot.Loading(token)
        return token
    }

    /** A load found the top card [p] (`LoadPlan.Fetch`). [queue] = the keys read with it ([maxAhead] + 2 of them), first = [p]. */
    fun loaded(token: Int, p: Prepared<T>, queue: List<StudyLoop.Key>): List<Cmd<T>> {
        if (token != this.token) return emptyList()
        when (val s = slot) {
            is Slot.Loading -> {}
            is Slot.Predicted -> { misses++; lastSwap = PredictRule.swapCause(answered, s.card.key, p, queue); dropHeld() } // the prediction was wrong: swap, the held tap is dropped
            else -> return emptyList()
        }
        forgetChain()
        slot = Slot.Confirmed(p, token)
        return listOf<Cmd<T>>(Cmd.Show(p, predicted = false)) + prepareNext(p, queue, 1)
    }

    /** Same as [loaded]: the swap from a wrong prediction (named for the io code that knows it predicted). */
    fun swapped(token: Int, p: Prepared<T>, queue: List<StudyLoop.Key>) = loaded(token, p, queue)

    /**
     * A load found the predicted card on top (`LoadPlan.Confirm`); [refresh] puts the fresh Review into the predicted card's view.
     * [next] (optional) is the card after it, already prepared by the io thread: it becomes the prediction at once, so a held tap that
     * acts right now still shows its next card instantly (1b fetches it only while a tap is held; else the Prepare command does it).
     * R18-A: with answers made ahead, this is the confirm of the LAST one (the loads of the earlier ones were replaced, by token).
     */
    fun confirmed(token: Int, key: StudyLoop.Key, queue: List<StudyLoop.Key>, next: Prepared<T>? = null, refresh: (T) -> T = { it }): List<Cmd<T>> {
        if (token != this.token) return emptyList()
        val s = slot as? Slot.Predicted<T> ?: return emptyList()
        if (s.card.key != key) { misses++; return reload() } // never happens with LoadPlan; never act on the wrong card if it does
        hits++
        val card = s.card.copy(view = refresh(s.card.view))
        slot = Slot.Confirmed(card, token)
        forgetChain() // the cards prepared earlier are not trusted past a confirm: the next ones are prepared from this fresh queue read
        val usable = next != null && queue.firstOrNull() == key && queue.getOrNull(1) == next.key && next.key != key
        if (usable && next != null) chain = Chain(key, token, arrayListOf(next))
        val held = s.held ?: return listOf<Cmd<T>>(Cmd.Confirm(card)) + prepareNext(card, queue, if (usable) 2 else 1)
        return listOf<Cmd<T>>(Cmd.Confirm(card)) + answerOn(card, held.ease, held.tookMs) // as if tapped now, on a confirmed card
    }

    /** Nothing is due any more. */
    fun finished(token: Int): List<Cmd<T>> {
        if (!pending(token)) return emptyList()
        dropHeld()
        forgetChain()
        slot = Slot.Finished
        return listOf(Cmd.ShowFinished)
    }

    fun failed(token: Int, message: String): List<Cmd<T>> {
        if (!pending(token)) return emptyList()
        dropHeld()
        forgetChain()
        slot = Slot.Error(message)
        return listOf(Cmd.ShowError(message))
    }

    /**
     * The prepared [card] for the card after [forKey] came back; kept only for the CONFIRMED card of load [forToken], in queue order:
     * [forKey] is the confirmed card itself (the first one) or the last card kept so far (the next one, R18-A); at most [maxAhead] + 1.
     */
    fun prepared(forKey: StudyLoop.Key, forToken: Int, card: Prepared<T>) {
        val s = slot as? Slot.Confirmed<T> ?: return
        if (forToken != token || s.token != forToken) return
        val c = chain?.takeIf { it.base == s.card.key && it.token == forToken } ?: Chain(s.card.key, forToken, ArrayList<Prepared<T>>()).also { chain = it }
        val tail = c.cards.lastOrNull()?.key ?: c.base
        if (forKey != tail || card.key == forKey || card.key == c.base || c.cards.any { it.key == card.key } || c.cards.size > maxAhead) return
        c.cards += card
    }

    /** An answer button was tapped (ease 1..4) after [tookMs] ms on the card. */
    fun tapAnswer(ease: Int, tookMs: Long): List<Cmd<T>> {
        if (ease !in 1..4) return emptyList()
        return when (val s = slot) {
            is Slot.Confirmed -> answerOn(s.card, ease, tookMs)
            is Slot.Predicted -> when {
                s.held != null -> emptyList()
                // R18-A: answered at once, behind the write of the earlier answer(s), while the card after it can be shown safely
                ahead < maxAhead -> makeAnswer(s.card, ease, tookMs, onPredicted = true) ?: hold(s, ease, tookMs)
                else -> hold(s, ease, tookMs)
            }
            else -> emptyList()
        }
    }

    /** A tap on a predicted card that cannot be answered ahead: NO write; it acts when AnkiDroid confirms this card. */
    private fun hold(s: Slot.Predicted<T>, ease: Int, tookMs: Long): List<Cmd<T>> {
        slot = s.copy(held = Held(ease, tookMs))
        return listOf(Cmd.ShowHeld(ease))
    }

    private fun pending(token: Int) = token == this.token && (slot is Slot.Loading || slot is Slot.Predicted<*>)

    private fun dropHeld() { if ((slot as? Slot.Predicted<T>)?.held != null) heldDropped++ }

    private fun forgetChain() { chain = null; ahead = 0; burst.clear() }

    /** The Prepare commands for the queue positions [from]..[maxAhead] + 1 after [p] (each for the card before it), as far as the read goes. */
    private fun prepareNext(p: Prepared<T>, queue: List<StudyLoop.Key>, from: Int): List<Cmd<T>> {
        if (queue.firstOrNull() != p.key) return emptyList()
        val out = ArrayList<Cmd<T>>(maxAhead + 1)
        for (i in from..maxAhead + 1) {
            val k = queue.getOrNull(i) ?: break
            val before = queue[i - 1]
            if (k == before || k == p.key) break
            out += Cmd.Prepare(k, before, token)
        }
        return out
    }

    /** An answer on the CONFIRMED [card]: queue it, show the predicted next card at once (or wait), ask for the real one behind the write. */
    private fun answerOn(card: Prepared<T>, ease: Int, tookMs: Long): List<Cmd<T>> = makeAnswer(card, ease, tookMs, onPredicted = false).orEmpty()

    /**
     * An answer on [card]: the CONFIRMED card, or (R18-A, [onPredicted]) a predicted one. Queue it, show the predicted next card at once
     * (or wait), ask for the real one behind the write. On a predicted card it is only made when a safe next card can be shown at once
     * (else null: nothing was changed, the caller holds the tap).
     */
    private fun makeAnswer(card: Prepared<T>, ease: Int, tookMs: Long, onPredicted: Boolean): List<Cmd<T>>? {
        val cardId = card.before["_id"]?.toLongOrNull()
        if (cardId == null || !AnswerState.usable(card.before)) return if (onPredicted) null else listOf(Cmd.Refuse("the card's state could not be read"))
        val at = now()
        val depth = if (onPredicted) ahead + 1 else 0
        val c = chain?.takeIf { it.base == card.key && it.token == token }
        val made = c?.cards?.firstOrNull()
        // each write still ahead of this one can move AnkiDroid's learning cutoff: look further into the future by [PredictRule.SLACK_MS] each
        val why = if (made == null) null else check(card, unburst(made), at + depth * PredictRule.SLACK_MS)
        val guess = made?.takeIf { why == PredictRule.Why.SAFE && (!onPredicted || PredictRule.aheadOk(it)) }   // R18-A: after an answer made ahead only a main-queue card may be shown ahead
        if (onPredicted && guess == null) return null
        val a = QueuedAnswer(newSeq(), deckId, card.key.nid, card.key.ord, cardId, card.before, ease, tookMs, at)
        val rest = if (guess != null && c != null) c.cards.drop(1) else emptyList()
        chain = null
        answered = card.key
        burst += cardId
        ahead = depth
        token++
        val out = ArrayList<Cmd<T>>(3)
        out += Cmd.Enqueue(a)
        if (guess != null) {
            predicted++
            if (onPredicted) answeredAhead++
            lastWait = ""
            slot = Slot.Predicted(guess, token, a.seq)
            if (rest.isNotEmpty()) chain = Chain(guess.key, token, ArrayList(rest))
            out += Cmd.Show(guess, predicted = true)
            out += Cmd.Load(token, guess.key)
        } else {
            if (why == null) noPrediction++ else waitsBy[why] = (waitsBy[why] ?: 0) + 1 // nothing prepared / prepared but not safe to show
            lastWait = why?.label ?: NOTHING
            slot = Slot.Loading(token)
            out += Cmd.ShowWaiting
            out += Cmd.Load(token, null)
        }
        return out
    }

    /**
     * [next] as the rule should see it: the cards answered earlier in this run of answers are not "learning cards that are due" (their
     * learning step moved when they were answered; the learning read in [next] is older than that).
     */
    private fun unburst(next: Prepared<T>): Prepared<T> {
        val read = next.learning ?: return next
        if (burst.isEmpty() || read.due == null) return next
        return next.copy(learning = read.copy(due = read.due.filter { it.cardId !in burst }))
    }

    companion object {
        /** The wait of an answer whose next card was not prepared yet (or there was none: the last card). */
        const val NOTHING = "nothing prepared"

        /**
         * R18-A (278): how many answers the study screen lets the user make on cards AnkiDroid has not confirmed yet, one after the other
         * (a slow write of the first answer must not hold the next ones). The io thread prepares [AHEAD] + 1 cards after the confirmed one.
         * Each one shown ahead is a guess the write checks again (card first in AnkiDroid's queue); two is what a few seconds of writing
         * and a quick user need, more would only pile up guesses.
         */
        const val AHEAD = 2
    }
}
