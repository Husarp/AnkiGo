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
    data class Confirm<T>(val card: Prepared<T>, val onTop: Boolean = true) : Cmd<T>()

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
 * A short prepared queue, with token guards for every asynchronous result. A displayed prediction stays until answered:
 * a different scheduler top becomes the NEXT candidate, except for an unrevealed new card checked on return. Its answer keeps the displayed id/state and
 * AnswerQueue's NOT_TOP verification/waiting path. Confirmation can refresh buttons on the same card only.
 * Unsafe predictions and exhausted preparation still wait behind the write; maxAhead bounds speculative answers.
 */
class NextCard<T>(
    private val deckId: Long,
    /** Sequence numbers for answers: `queue::nextSeq`. */
    private val newSeq: () -> Long,
    private val now: () -> Long = { System.currentTimeMillis() },
    /** R18-A: how many answers may be made on cards AnkiDroid has not confirmed yet, one after the other (the app passes [AHEAD]; 0 = every such tap is held). */
    val maxAhead: Int = 0,
    /** Read the current due queue before showing each next card (R23: 349/350/352). */
    val freshNext: Boolean = false,
    /** R10-X1: may the prepared next card show at once (`PredictRule::check`)? Anything but SAFE makes the answer wait. Default: always. */
    private val check: (answered: Prepared<T>, next: Prepared<T>, now: Long) -> PredictRule.Why = { _, _, _ -> PredictRule.Why.SAFE },
) {
    sealed class Slot<out T> {
        data class Loading(val token: Int) : Slot<Nothing>()
        data class Confirmed<T>(val card: Prepared<T>, val token: Int, val onTop: Boolean = true) : Slot<T>()
        data class Predicted<T>(val card: Prepared<T>, val token: Int, val afterSeq: Long, val held: Held? = null) : Slot<T>()
        object Finished : Slot<Nothing>()
        data class Error(val message: String) : Slot<Nothing>()
    }

    data class Held(val ease: Int, val tookMs: Long)

    /**
     * The cards prepared for the queue positions after [base] (the card on screen), in order; valid for load [token] only. R19-R: [learn] =
     * the learning card that would jump in front of the first of them once it is due ([PredictRule.learningToPrepare]), prepared by id.
     */
    private class Chain<T>(val base: StudyLoop.Key, val token: Int, val cards: ArrayList<Prepared<T>>, var learn: Prepared<T>? = null)

    var slot: Slot<T> = Slot.Loading(0)
        private set
    private var token = 0
    private var chain: Chain<T>? = null
    private var answered: StudyLoop.Key? = null // the card of the last answer (for the swap's cause)
    private var ahead = 0 // R18-A: answers made on unconfirmed cards since the last confirmed one
    private val burst = ArrayList<Long>() // R18-A: ids of the cards answered since the last confirmed one (their learning steps are not "due cards")

    /** For the STUDY line: predictions shown / confirmed / disagreed with, answers with nothing prepared, held taps dropped on leaving. */
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

    /** R19-R: predictions that were the due learning card instead of a wait (counted in [predicted] too). */
    var predictedLearning = 0
        private set

    /** R10-X1: answers whose prepared next card [check] held back, by reason. */
    val withheld: Map<PredictRule.Why, Int> get() = waitsBy
    private val waitsBy = LinkedHashMap<PredictRule.Why, Int>()
    private var schedulerWaits = 0

    /** Why the last answer waited for its next card ("" = it showed the prediction); the cause of the last swap. */
    var lastWait = ""
        private set
    var lastSwap = ""
        private set

    /** This visit's predictions and waits in one line, e.g. "predicted 12 (hit 11, swap 1), waited 3 (learning card due 2, nothing prepared 1)". */
    fun tally(): String {
        val waits = withheld.filterValues { it > 0 }.map { (why, n) -> why.label to n } + listOfNotNull(if (noPrediction > 0) NOTHING to noPrediction else null, if (schedulerWaits > 0) "scheduler confirmation" to schedulerWaits else null)
        return "predicted $predicted (hit $hits, swap $misses), waited ${waits.sumOf { it.second }}" +
            (if (waits.isEmpty()) "" else waits.joinToString(", ", " (", ")") { "${it.first} ${it.second}" }) +
            (if (predictedLearning > 0) ", predicted learning $predictedLearning" else "") +
            (if (answeredAhead > 0) ", answered before the confirm $answeredAhead" else "") +
            (if (heldDropped > 0) ", held taps dropped on leaving $heldDropped" else "")
    }

    /** The card the card actions may act on: only a CONFIRMED one. */
    fun actionCard(): Prepared<T>? = (slot as? Slot.Confirmed<T>)?.takeIf { it.onTop }?.card

    /** The seq of the answer whose write the shown predicted card is waiting behind, else null. */
    fun waitingFor(): Long? = (slot as? Slot.Predicted<T>)?.afterSeq

    data class ReturnCheck(val key: StudyLoop.Key, val token: Int)

    /** Only a return to an unanswered NEW question may replace the displayed card. No load/wait or answer is emitted. */
    fun returnCheck(answerRevealed: Boolean): ReturnCheck? {
        if (answerRevealed) return null
        val card = when (val s = slot) {
            is Slot.Confirmed -> s.card
            is Slot.Predicted -> if (s.held == null) s.card else null
            else -> null
        } ?: return null
        return if (card.before["queue"] == "0") ReturnCheck(card.key, token) else null
    }

    /** The return's fresh scheduler top, prepared and checked as usual. The abandoned new card remains untouched in AnkiDroid. */
    fun returned(check: ReturnCheck, p: Prepared<T>?, queue: List<StudyLoop.Key>, answerRevealed: Boolean): List<Cmd<T>> {
        if (returnCheck(answerRevealed) != check || p == null || p.key == check.key || queue.firstOrNull() != p.key ||
            p.before["queue"] !in setOf("1", "2", "3", "4")) return emptyList()
        forgetChain()
        token++
        slot = Slot.Confirmed(p, token)
        return listOf<Cmd<T>>(Cmd.Show(p, predicted = false)) + prepareNext(p, queue, 1)
    }

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
            is Slot.Predicted -> {
                if (s.card.key == p.key) return confirmed(token, p.key, queue)
                misses++
                lastSwap = PredictRule.swapCause(answered, s.card.key, p, queue)
                forgetChain()
                slot = Slot.Confirmed(s.card, token, onTop = false)
                val remaining = queue.filter { it != s.card.key }
                if (remaining.firstOrNull() == p.key) chain = Chain(s.card.key, token, arrayListOf(p))
                val commands = listOf<Cmd<T>>(Cmd.Confirm(s.card, onTop = false))
                return if (s.held != null) commands + answerOn(s.card, s.held.ease, s.held.tookMs)
                else commands + prepareNext(p, remaining, 1).take(maxAhead)
            }
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
        if (s.card.key != key) return emptyList() // inconsistent result: never replace the display or discard its tap
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
        retainPrediction()?.let { return it }
        forgetChain()
        slot = Slot.Finished
        return listOf(Cmd.ShowFinished)
    }

    fun failed(token: Int, message: String): List<Cmd<T>> {
        if (!pending(token)) return emptyList()
        retainPrediction()?.let { return it }
        forgetChain()
        slot = Slot.Error(message)
        return listOf(Cmd.ShowError(message))
    }

    private fun retainPrediction(): List<Cmd<T>>? {
        val s = slot as? Slot.Predicted<T> ?: return null
        forgetChain()
        slot = Slot.Confirmed(s.card, token, onTop = false)
        val commands = listOf<Cmd<T>>(Cmd.Confirm(s.card, onTop = false))
        return if (s.held == null) commands else commands + answerOn(s.card, s.held.ease, s.held.tookMs)
    }

    /**
     * The prepared [card] for the card after [forKey] came back; kept only for the CONFIRMED card of load [forToken], in queue order:
     * [forKey] is the confirmed card itself (the first one) or the last card kept so far (the next one, R18-A); at most [maxAhead] + 1.
     */
    fun prepared(forKey: StudyLoop.Key, forToken: Int, card: Prepared<T>) {
        val s = slot as? Slot.Confirmed<T> ?: return
        if (forToken != token || s.token != forToken) return
        val c = chainOf(s, forToken)
        val tail = c.cards.lastOrNull()?.key ?: c.base
        if (forKey != tail || card.key == forKey || card.key == c.base || c.cards.any { it.key == card.key } || c.cards.size > maxAhead) return
        c.cards += card
    }

    private fun chainOf(s: Slot.Confirmed<T>, forToken: Int): Chain<T> =
        chain?.takeIf { it.base == s.card.key && it.token == forToken } ?: Chain(s.card.key, forToken, ArrayList<Prepared<T>>()).also { chain = it }

    /**
     * R19-R (278b): the learning card prepared by id for the CONFIRMED card [forKey] of load [forToken] ([PredictRule.learningToPrepare]).
     * It is shown instead of a wait only when a tap finds it the unique earliest due learning card ([PredictRule.firstLearning]); else it is never used.
     */
    fun preparedLearning(forKey: StudyLoop.Key, forToken: Int, card: Prepared<T>) {
        val s = slot as? Slot.Confirmed<T> ?: return
        if (forToken != token || s.token != forToken || s.card.key != forKey || card.key == forKey) return
        chainOf(s, forToken).learn = card
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
                else -> { lastWait = "ahead limit"; hold(s, ease, tookMs) }
            }
            else -> emptyList()
        }
    }

    /** A tap on a predicted card that cannot be answered ahead: NO write; it acts when AnkiDroid confirms this card. */
    private fun hold(s: Slot.Predicted<T>, ease: Int, tookMs: Long): List<Cmd<T>> {
        slot = s.copy(held = Held(ease, tookMs))
        return listOf(Cmd.ShowHeld(ease))
    }

    /** Persist an accepted tap before an explicit reload, Activity destruction or leaving the screen. */
    fun releaseHeld(): List<Cmd<T>> {
        val s = slot as? Slot.Predicted<T> ?: return emptyList()
        val tap = s.held ?: return emptyList()
        val id = s.card.before["_id"]?.toLongOrNull() ?: return emptyList()
        if (!AnswerState.usable(s.card.before)) return emptyList()
        slot = s.copy(held = null)
        lastWait = "leaving with held answer"
        return listOf(Cmd.Enqueue(QueuedAnswer(newSeq(), deckId, s.card.key.nid, s.card.key.ord, id,
            s.card.before, tap.ease, tap.tookMs, now())))
    }

    private fun pending(token: Int) = token == this.token && (slot is Slot.Loading || slot is Slot.Predicted<*>)

    private fun dropHeld() { if ((slot as? Slot.Predicted<T>)?.held != null) heldDropped++ }

    private fun forgetChain() { chain = null; ahead = 0; burst.clear() }

    /** The Prepare commands for the queue positions [from]..[maxAhead] + 1 after [p] (each for the card before it), as far as the read goes. */
    private fun prepareNext(p: Prepared<T>, queue: List<StudyLoop.Key>, from: Int): List<Cmd<T>> {
        if (queue.firstOrNull() != p.key) return emptyList()
        val out = ArrayList<Cmd<T>>(maxAhead + 1)
        for (i in from..(if (freshNext) 1 else maxAhead + 1)) {
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
        val seen = made?.let { unburst(it) }
        val why = if (seen == null) null else check(card, seen, at + depth * PredictRule.SLACK_MS)
        val guess = made?.takeIf { why == PredictRule.Why.SAFE && (!onPredicted || PredictRule.aheadOk(it)) }   // R18-A: after an answer made ahead only a main-queue card may be shown ahead
        if (onPredicted && guess == null) {
            lastWait = if (why == PredictRule.Why.SAFE) "learning card after ahead answer" else why?.label ?: NOTHING
            return null
        }
        // R19-R (278b): a learning card came due: instead of the wait, the due card itself when it is certainly the first (never after an answer made ahead: just returned above)
        val learn = if (seen != null && guess == null && why == PredictRule.Why.LEARNING_DUE) c?.learn?.takeIf { PredictRule.firstLearning(card, seen, it, at) } else null
        val shown = if (freshNext) null else guess ?: learn
        val a = QueuedAnswer(newSeq(), deckId, card.key.nid, card.key.ord, cardId, card.before, ease, tookMs, at)
        val rest = if (guess != null && c != null) c.cards.drop(1) else emptyList()
        chain = null
        answered = card.key
        burst += cardId
        ahead = depth
        token++
        val out = ArrayList<Cmd<T>>(3)
        out += Cmd.Enqueue(a)
        if (shown != null) {
            predicted++
            if (onPredicted) answeredAhead++
            if (learn != null) predictedLearning++
            lastWait = ""
            slot = Slot.Predicted(shown, token, a.seq)
            if (rest.isNotEmpty()) chain = Chain(shown.key, token, ArrayList(rest))
            out += Cmd.Show(shown, predicted = true)
            out += Cmd.Load(token, shown.key)
        } else {
            if (freshNext) schedulerWaits++ else if (why == null) noPrediction++ else waitsBy[why] = (waitsBy[why] ?: 0) + 1 // nothing prepared / prepared but not safe to show
            lastWait = if (freshNext) "scheduler confirmation" else why?.label ?: NOTHING
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
