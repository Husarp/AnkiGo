package com.husarp.ankigo.study

/**
 * A card ready to show without a provider call: [key], the card's state as AnkiDroid gave it ([before], the
 * [com.husarp.ankigo.CardState.COLS] row; it is what an answer is checked against) and [view], everything else 1b prepared
 * (the Review with the buttons and next times, html, note fields, model css, media check, tts…).
 */
data class Prepared<out T>(val key: StudyLoop.Key, val before: Map<String, String?>, val view: T)

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

    /** Io: prepare [key] (the card after [forKey]) and report `prepared(forKey, forToken, …)`. */
    data class Prepare(val key: StudyLoop.Key, val forKey: StudyLoop.Key, val forToken: Int) : Cmd<Nothing>()

    /** Main: `queue.enqueue(answer)` and then post `drain()` to io, BEFORE the [Load] that follows in the same list. */
    data class Enqueue(val answer: QueuedAnswer) : Cmd<Nothing>()

    /** Show [card] on its QUESTION side; [predicted] = AnkiDroid has not confirmed it yet (buttons only hold the tap). */
    data class Show<T>(val card: Prepared<T>, val predicted: Boolean) : Cmd<T>()

    /** The predicted card on screen is the real one: refresh buttons / next times from [card], keep the side the user is on. */
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
 * Every load result carries the token it was asked with; a result for an old token changes nothing. A tap on a PREDICTED card is
 * only held: it acts when `confirmed` arrives and is dropped (quietly, counted) when `loaded` brings a different card, a reload, a
 * finish or an error. A swap always lands on the question side. A prepared card (the prediction) is kept only for the card it was
 * made for and only while that card is CONFIRMED, and is used by the very next answer. Card actions (bury, suspend, flag, move,
 * delete, edit) only on [actionCard], which is null unless CONFIRMED.
 */
class NextCard<T>(
    private val deckId: Long,
    /** Sequence numbers for answers: `queue::nextSeq`. */
    private val newSeq: () -> Long,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    sealed class Slot<out T> {
        data class Loading(val token: Int) : Slot<Nothing>()
        data class Confirmed<T>(val card: Prepared<T>, val token: Int) : Slot<T>()
        data class Predicted<T>(val card: Prepared<T>, val token: Int, val afterSeq: Long, val held: Held? = null) : Slot<T>()
        object Finished : Slot<Nothing>()
        data class Error(val message: String) : Slot<Nothing>()
    }

    data class Held(val ease: Int, val tookMs: Long)

    private class Pred<T>(val forKey: StudyLoop.Key, val forToken: Int, val card: Prepared<T>)

    var slot: Slot<T> = Slot.Loading(0)
        private set
    private var token = 0
    private var prediction: Pred<T>? = null

    /** For the STUDY line: predictions confirmed / swapped away, answers that had no prediction, held taps dropped. */
    var hits = 0
        private set
    var misses = 0
        private set
    var noPrediction = 0
        private set
    var heldDropped = 0
        private set

    /** The card the card actions may act on: only a CONFIRMED one. */
    fun actionCard(): Prepared<T>? = (slot as? Slot.Confirmed<T>)?.card

    /** The seq of the answer whose write the shown predicted card is waiting behind, else null. */
    fun waitingFor(): Long? = (slot as? Slot.Predicted<T>)?.afterSeq

    /** The screen starts, resumes, Retry, or a card was edited: forget everything and ask again. */
    fun reload(): List<Cmd<T>> {
        dropHeld()
        prediction = null
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
        prediction = null
        token++
        slot = Slot.Loading(token)
        return token
    }

    /** A load found the top card [p] (`LoadPlan.Fetch`). [queue] = the keys read with it (limit 3), first = [p]. */
    fun loaded(token: Int, p: Prepared<T>, queue: List<StudyLoop.Key>): List<Cmd<T>> {
        if (token != this.token) return emptyList()
        when (slot) {
            is Slot.Loading -> {}
            is Slot.Predicted -> { misses++; dropHeld() } // the prediction was wrong: swap, the held tap is dropped
            else -> return emptyList()
        }
        prediction = null
        slot = Slot.Confirmed(p, token)
        return listOf<Cmd<T>>(Cmd.Show(p, predicted = false)) + prepareNext(p, queue)
    }

    /** Same as [loaded]: the swap from a wrong prediction (named for the io code that knows it predicted). */
    fun swapped(token: Int, p: Prepared<T>, queue: List<StudyLoop.Key>) = loaded(token, p, queue)

    /**
     * A load found the predicted card on top (`LoadPlan.Confirm`); [refresh] puts the fresh Review into the predicted card's view.
     * [next] (optional) is the card after it, already prepared by the io thread: it becomes the prediction at once, so a held tap that
     * acts right now still shows its next card instantly (1b fetches it only while a tap is held; else the Prepare command does it).
     */
    fun confirmed(token: Int, key: StudyLoop.Key, queue: List<StudyLoop.Key>, next: Prepared<T>? = null, refresh: (T) -> T = { it }): List<Cmd<T>> {
        if (token != this.token) return emptyList()
        val s = slot as? Slot.Predicted<T> ?: return emptyList()
        if (s.card.key != key) { misses++; return reload() } // never happens with LoadPlan; never act on the wrong card if it does
        hits++
        val card = s.card.copy(view = refresh(s.card.view))
        slot = Slot.Confirmed(card, token)
        prediction = null
        val usable = next != null && queue.firstOrNull() == key && queue.getOrNull(1) == next.key && next.key != key
        if (usable && next != null) prediction = Pred(key, token, next)
        val held = s.held ?: return listOf<Cmd<T>>(Cmd.Confirm(card)) + (if (usable) emptyList() else prepareNext(card, queue))
        return listOf<Cmd<T>>(Cmd.Confirm(card)) + answerOn(card, held.ease, held.tookMs) // as if tapped now, on a confirmed card
    }

    /** Nothing is due any more. */
    fun finished(token: Int): List<Cmd<T>> {
        if (!pending(token)) return emptyList()
        dropHeld()
        prediction = null
        slot = Slot.Finished
        return listOf(Cmd.ShowFinished)
    }

    fun failed(token: Int, message: String): List<Cmd<T>> {
        if (!pending(token)) return emptyList()
        dropHeld()
        prediction = null
        slot = Slot.Error(message)
        return listOf(Cmd.ShowError(message))
    }

    /** The prepared [card] for the card after [forKey] came back; kept only for a CONFIRMED [forKey] of load [forToken]. */
    fun prepared(forKey: StudyLoop.Key, forToken: Int, card: Prepared<T>) {
        val s = slot as? Slot.Confirmed<T> ?: return
        if (forToken == token && s.token == forToken && s.card.key == forKey && card.key != forKey) prediction = Pred(forKey, forToken, card)
    }

    /** An answer button was tapped (ease 1..4) after [tookMs] ms on the card. */
    fun tapAnswer(ease: Int, tookMs: Long): List<Cmd<T>> {
        if (ease !in 1..4) return emptyList()
        return when (val s = slot) {
            is Slot.Confirmed -> answerOn(s.card, ease, tookMs)
            is Slot.Predicted -> if (s.held != null) emptyList() else {
                slot = s.copy(held = Held(ease, tookMs)) // NO write: it acts when AnkiDroid confirms this card
                listOf(Cmd.ShowHeld(ease))
            }
            else -> emptyList()
        }
    }

    private fun pending(token: Int) = token == this.token && (slot is Slot.Loading || slot is Slot.Predicted<*>)

    private fun dropHeld() { if ((slot as? Slot.Predicted<T>)?.held != null) heldDropped++ }

    private fun prepareNext(p: Prepared<T>, queue: List<StudyLoop.Key>): List<Cmd<T>> {
        val next = queue.getOrNull(1)
        return if (queue.firstOrNull() == p.key && next != null && next != p.key) listOf(Cmd.Prepare(next, p.key, token)) else emptyList()
    }

    /** An answer on the CONFIRMED [card]: queue it, show the predicted next card at once (or wait), ask for the real one behind the write. */
    private fun answerOn(card: Prepared<T>, ease: Int, tookMs: Long): List<Cmd<T>> {
        val cardId = card.before["_id"]?.toLongOrNull()
        if (cardId == null || !AnswerState.usable(card.before)) return listOf(Cmd.Refuse("the card's state could not be read"))
        val a = QueuedAnswer(newSeq(), deckId, card.key.nid, card.key.ord, cardId, card.before, ease, tookMs, now())
        val guess = prediction?.takeIf { it.forKey == card.key && it.forToken == token }?.card
        prediction = null
        token++
        val out = ArrayList<Cmd<T>>(3)
        out += Cmd.Enqueue(a)
        if (guess != null) {
            slot = Slot.Predicted(guess, token, a.seq)
            out += Cmd.Show(guess, predicted = true)
            out += Cmd.Load(token, guess.key)
        } else {
            noPrediction++
            slot = Slot.Loading(token)
            out += Cmd.ShowWaiting
            out += Cmd.Load(token, null)
        }
        return out
    }
}
