package com.husarp.ankigo.study

import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** What happened to a queued answer. [Retrying] is progress; everything else is the end of that answer. */
sealed class AnswerOutcome {
    abstract val answer: QueuedAnswer

    /** Attempt [attempt] failed ([reason]); the next one starts in [waitMs] ms, or on [AnswerQueue.retryNow] when [waitMs] < 0 (parked). */
    data class Retrying(override val answer: QueuedAnswer, val attempt: Int, val reason: String, val waitMs: Long) : AnswerOutcome()

    /** Written and verified in this process ([after] = the card as it is after the answer). The game counted it at the tap (R10-B23): this settles it. */
    data class Saved(override val answer: QueuedAnswer, val after: Map<String, String?>, val attempts: Int, val answerMs: Long) : AnswerOutcome()

    /** Found already written by a write of an earlier process (a journal replay; its game step was decided at the replay): this settles it. */
    data class AlreadySaved(override val answer: QueuedAnswer, val after: Map<String, String?>) : AnswerOutcome()

    /** NOT written, for a reason the user is told. */
    data class NotWritten(override val answer: QueuedAnswer, val reason: Reason, val detail: String) : AnswerOutcome()

    /**
     * PLAN 195: AnkiDroid refused the answer while another card was first ([reason]). It is kept (journal) and written when its card is
     * first again the same day ([AnswerQueue.writeWaiting]); its end (saved, or not written) comes later as a terminal outcome.
     */
    data class Waiting(override val answer: QueuedAnswer, val reason: String) : AnswerOutcome()

    enum class Reason {
        /** The card was not first in AnkiDroid's queue any more (a learning card came due, the queue changed). */
        NOT_TOP,

        /** The card was answered, edited, buried or deleted elsewhere. */
        CHANGED,

        /** AnkiDroid changed the card in a way we cannot explain (reps moved by more than one): never written again. */
        UNSURE,

        /** The user gave up on it (the write may still have happened: see [detail]). */
        DISCARDED,
    }

    val isTerminal get() = this !is Retrying && this !is Waiting
}

/** The provider calls the queue makes (1b implements them with `Anki.*`, all on the study io thread). */
interface AnswerProvider {
    fun select(deckId: Long)

    /** AnkiDroid's queue for [deckId], first = top. */
    fun top(deckId: Long, limit: Int): List<StudyLoop.Key>

    /** The card's [com.husarp.ankigo.CardState.COLS] row; null = unreadable. */
    fun stats(key: StudyLoop.Key): Map<String, String?>?

    fun answer(key: StudyLoop.Key, ease: Int, ms: Long)
}

/** Callbacks of the queue; all run on the io thread (1b posts to the main thread). Exceptions thrown here are swallowed. */
interface AnswerHooks {
    /**
     * Right before each write attempt (and before the read that follows an uncertain one): `LiveSession.expectOwn`. The expectation
     * must PIN: it holds for as long as the answer is unresolved, however long the calls and the back-off take, and is released by
     * [cancel] or by the saved-answer handoff (the host).
     */
    fun expect(before: Map<String, String?>)

    /** An answer ended NOT written: `LiveSession.cancelOwn`. */
    fun cancel(before: Map<String, String?>)

    /** The write call alone took [ms] (the "ANSWER n ms" of the STUDY line), also when it threw. */
    fun answerTimed(answer: QueuedAnswer, attempt: Int, ms: Long) {}

    /** Progress and the end of each answer, exactly one terminal outcome per answer. */
    fun outcome(o: AnswerOutcome)

    /** The journal file could not be written (the answer stays queued in memory only). */
    fun journalFailed(reason: String) {}

    /** PLAN 195: a line for the debug report ("NOT_TOP try", "saved late", "gave up"). */
    fun note(text: String) {}
}

/** Back-off between failed attempts of one answer: 1 s, 2 s, 5 s, 10 s, 30 s, then 30 s; park after [PARK_AFTER] attempts. */
object Backoff {
    private val STEPS = longArrayOf(1_000, 2_000, 5_000, 10_000, 30_000)
    const val PARK_AFTER = 20
    fun ms(attempt: Int): Long = STEPS[(attempt - 1).coerceIn(0, STEPS.lastIndex)]
}

/** Sleeps between attempts; [wake] (the Retry button, the screen resumed) ends a wait at once. Injectable so tests never sleep. */
interface Waiter {
    /** Waits up to [ms] (< 0: until woken). True when woken early, false when the time ran out. A wake nobody waited for makes the next wait return at once. */
    fun await(ms: Long): Boolean
    fun wake()

    /** Forgets a wake nobody waited for. */
    fun clear()
}

/** The real [Waiter]. */
class LockWaiter : Waiter {
    private val lock = ReentrantLock()
    private val cond = lock.newCondition()
    private var woken = false

    override fun await(ms: Long): Boolean = lock.withLock {
        var left = if (ms < 0) Long.MAX_VALUE else TimeUnit.MILLISECONDS.toNanos(ms)
        while (!woken) {
            if (ms >= 0 && left <= 0) return false
            left = if (ms < 0) { cond.await(); left } else cond.awaitNanos(left)
        }
        woken = false
        true
    }

    override fun wake() = lock.withLock { woken = true; cond.signalAll() }
    override fun clear() = lock.withLock { woken = false }
}

/**
 * The answer write queue. Everything that touches AnkiDroid ([drain]) runs on ONE process-wide io thread, in FIFO order with every
 * other study call, and an answer's task blocks that thread until the answer is RESOLVED, so a task queued after it (the confirm of
 * the next card, a bury) sees AnkiDroid's real state after the write. Pure Kotlin; no clock, sleep or thread is used except through
 * the injected [clock] and [waiter].
 *
 * Wiring (1b): `restore()` once at start, then `io.execute { drain() }` (restored answers first); per answer `enqueue(a)` on the
 * calling thread (the journal is written before the entry can run) and then `io.execute { drain() }` BEFORE the load of the next card.
 * Loads select the deck through [ensureSelected]; every other place that selects a deck, and every resume of the study screen, calls
 * [invalidateSelection] (or build the queue with `alwaysSelect = true`). A resume or a new study screen also calls [retryNow]. [drain]
 * resolves ALL queued answers, so a load queued behind it can never overtake an older restored one. Hooks run on the io thread.
 *
 * One answer (the head), attempt n:
 * ```
 *  R  (n > 1 or restored) read the card: saved -> Saved (a write of this process) | AlreadySaved (a dead process's) |
 *     CHANGED (neither could have written: it was answered elsewhere); same as before -> go on; unreadable -> FAIL; else CHANGED
 *  1  select the deck (unless known selected; always when n > 1)
 *  2  top(deck) == key?  no -> (look again after a real select, if the first select was skipped) NOT_TOP, or with [tryNotTop] N
 *  3  card still the same as shown?  no -> CHANGED;  unreadable -> FAIL
 *  4  hooks.expect; ANSWER (timed alone)
 *  5  read the card: saved -> Saved | unchanged reps -> n == 1 ? attempt 2 at once : FAIL | unreadable -> FAIL | else UNSURE
 *  FAIL(n): Retrying outcome, wait backoff(n) (parked after [parkAfter] attempts) -> attempt n + 1 (which starts with R)
 *  N  (PLAN 195) steps 3-5 for this exact card anyway, watching the first card: saved -> Saved | unchanged -> WAIT | unreadable -> FAIL |
 *     the first card was answered instead -> UNSURE and the try is off until the app restarts | else UNSURE
 *  WAIT: the answer leaves the queue for the waiting list (journal flag; the expectation stays pinned): Waiting outcome. A load that
 *     finds its card first the same day writes it then ([writeWaiting]: R, 1-5 again; a second refusal ends it NOT_TOP), and the card
 *     is never shown; a day over ends it NOT_TOP ([drain], [writeWaiting]). It survives a restart in the journal.
 * ```
 * The end of an answer: `cancel` (when not written), journal rewritten without it (the file is deleted when it was the last; a rewrite
 * that fails is tried again before the next answer starts), outcome posted. Never lost: it ends as saved,
 * as a stated NOT_TOP / CHANGED / UNSURE / DISCARDED, or stays in the journal. Never twice: every attempt after an uncertain one
 * starts with R. Never the wrong card: steps 2 and 3 run right before every write. A parked answer holds the io thread (and so every
 * later study call) until [retryNow] or [discard]: the screen must offer both.
 */
class AnswerQueue(
    private val provider: AnswerProvider,
    private val store: JournalStore,
    private val hooks: AnswerHooks,
    private val waiter: Waiter,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
    private val backoff: (Int) -> Long = Backoff::ms,
    private val parkAfter: Int = Backoff.PARK_AFTER,
    /** Select the deck before every write (set it when skipping the select is found unsafe on a device). */
    private val alwaysSelect: Boolean = false,
    /**
     * PLAN 195 (Settings -> Diagnostics, ON by default in the app): on NOT_TOP still try the write for that exact card, and keep an
     * answer AnkiDroid refuses waiting for its card. Off by default here, so a queue made without it keeps the old rule.
     */
    private val tryNotTop: () -> Boolean = { false },
    /** The local day of a wall-clock time: a waiting answer is only written on the day it was made. */
    private val dayOf: (Long) -> String = LOCAL_DAY,
    private val wallNow: () -> Long = { System.currentTimeMillis() },
) {
    /** [count] answers back in the queue, [waiting] back on the waiting list (PLAN 195). */
    class Restored(val count: Int, val problem: String?, val waiting: Int = 0)

    private class Slot(@Volatile var a: QueuedAnswer) {
        var n = 0
        var attempted = false // hooks.expect was called in this process: a write of ours may exist
        var answerMs = -1L
        var late = false // PLAN 195: back from the waiting list for its write (its earlier try was refused)
        @Volatile var discard = false
    }

    private sealed class Step {
        class Done(val outcome: AnswerOutcome) : Step()
        object Again : Step()
        class Fail(val reason: String) : Step()
        class Wait(val reason: String) : Step()
    }

    private val lock = ReentrantLock() // slots, journal, selection
    private val runLock = ReentrantLock() // at most one writer, whatever the callers do
    private val slots = ArrayList<Slot>()
    private val waiting = ArrayList<Slot>() // PLAN 195: refused while another card was first, kept for when theirs is first again
    @Volatile private var notTopOff = false // PLAN 195: a try answered ANOTHER card once: never again in this process
    private val seqs = AtomicLong(0)
    private var restoredOnce: Restored? = null
    private var selectedDeck: Long? = null
    private var selectedAt = 0L
    private var dirty = false // the journal file still holds more than the unresolved answers (a rewrite failed): tried again before the next answer starts

    /** Answers not yet ended, oldest first (a copy). The waiting ones (PLAN 195) are not in it: [waitingAnswers]. */
    fun pending(): List<QueuedAnswer> = lock.withLock { slots.map { it.a } }

    /** PLAN 195: the answers waiting for their card to be first again (a copy). */
    fun waitingAnswers(): List<QueuedAnswer> = lock.withLock { waiting.map { it.a } }

    /** Everything the journal must hold: the queue and the waiting list (with its flag). Called with [lock] held. */
    private fun journal(extra: QueuedAnswer? = null): List<QueuedAnswer> = slots.map { it.a } + listOfNotNull(extra) + waiting.map { it.a }

    val size: Int get() = lock.withLock { slots.size }

    /** A fresh, increasing sequence number for [QueuedAnswer.seq]. */
    fun nextSeq(): Long = seqs.incrementAndGet()

    /** Reads the journal once per process (a second call does nothing); the entries are processed first by [drain]. */
    fun restore(): Restored = lock.withLock {
        restoredOnce?.let { return Restored(0, it.problem) }
        val d = try { AnswerJournal.decode(store.read()) } catch (e: Exception) { AnswerJournal.Decoded(emptyList(), "the journal could not be read: ${e.message}") }
        val known = (slots + waiting).map { it.a.seq }.toSet()
        val fresh = d.answers.filter { it.seq !in known }
        val (wait, now) = fresh.partition { it.waiting }
        slots.addAll(0, now.map { Slot(it.copy(restored = true)) })
        waiting.addAll(wait.map { Slot(it.copy(restored = true)) })
        for (a in fresh) seqs.updateAndGet { maxOf(it, a.seq) }
        Restored(now.size, d.problem, wait.size).also { restoredOnce = it }
    }

    /**
     * Adds an answer. The journal (all entries) is written FIRST, then the entry becomes visible to [drain]. A journal that cannot
     * be written does not refuse the answer: it stays queued in memory ([AnswerHooks.journalFailed]). Returns false then.
     */
    fun enqueue(a: QueuedAnswer): Boolean = lock.withLock {
        require(a.ease in 1..4 && AnswerState.usable(a.before)) { "unusable answer" }
        seqs.updateAndGet { maxOf(it, a.seq) }
        val ok = persist(journal(a))
        slots.add(Slot(a))
        ok
    }

    /** Ends a back-off wait now (Retry button, screen resumed, a new study screen). */
    fun retryNow() = waiter.wake()

    /** The user gives up on answer [seq]: it ends DISCARDED (after a last look at whether it was written anyway). */
    fun discard(seq: Long): Boolean {
        val found = lock.withLock { slots.firstOrNull { it.a.seq == seq }?.also { it.discard = true } } != null
        if (found) waiter.wake()
        return found
    }

    /** The deck selection may have changed outside this queue (screen resumed, another place selected a deck). */
    fun invalidateSelection() = lock.withLock { selectedDeck = null }

    /** A load selected [deckId] itself. */
    fun markSelected(deckId: Long) = lock.withLock { selectedDeck = deckId; selectedAt = clock() }

    /** For loads: runs [select] unless this deck is known to be selected (only trusted for [SELECT_TRUST_MS]). */
    fun ensureSelected(deckId: Long, select: () -> Unit) {
        if (!trusted(deckId)) { select(); markSelected(deckId) }
    }

    private fun trusted(deckId: Long) = !alwaysSelect && lock.withLock { selectedDeck == deckId && clock() - selectedAt <= SELECT_TRUST_MS }

    /**
     * Io thread. Resolves every queued answer, oldest first, and returns their end outcomes (a [AnswerOutcome.Waiting] too). Blocks
     * while one is retrying. First ends the waiting answers whose day is over (PLAN 195).
     */
    fun drain(): List<AnswerOutcome> = runLock.withLock {
        val out = ArrayList<AnswerOutcome>()
        out += expireWaiting()
        while (true) {
            val s = lock.withLock { slots.firstOrNull() } ?: break
            out += resolve(s)
        }
        out
    }

    private fun resolve(s: Slot): AnswerOutcome {
        waiter.clear()
        lock.withLock { if (dirty) persist(journal()) } // a told answer must not stay in the journal: try the failed rewrite again before this answer starts
        while (true) {
            if (s.discard) return end(s, discarded(s))
            s.n++
            when (val r = attempt(s)) {
                is Step.Done -> return end(s, r.outcome)
                is Step.Wait -> return wait(s, r.reason)
                Step.Again -> continue
                is Step.Fail -> {
                    invalidateSelection() // the state of AnkiDroid is unknown now: the next load selects again
                    val wait = if (s.n >= parkAfter) -1L else backoff(s.n)
                    post(AnswerOutcome.Retrying(s.a, s.n, r.reason, wait))
                    waiter.await(wait)
                }
            }
        }
    }

    private fun attempt(s: Slot): Step {
        val a = s.a
        val key = a.key
        val n = s.n
        try {
            if (n > 1 || a.restored || s.late) {
                // an earlier attempt of this process may have applied: keep its expectation alive (30 s) while the card is read, so a
                // long back-off can never let a poll count our own answer as a live one as well as the game step
                if (s.attempted) guard { hooks.expect(a.before) }
                val now = provider.stats(key) ?: return Step.Fail("the card could not be read")
                if (StudyLoop.saved(a.before, now)) {
                    // only a write of ours (this process's, or a dead one's) can have made it look saved; else someone else answered it
                    // (a late answer's earlier try was refused: a saved-looking card was answered elsewhere, PLAN 195)
                    return Step.Done(if (s.attempted || (a.restored && !s.late)) wasSaved(s, now) else changed(a))
                }
                if (!AnswerState.same(a.before, now)) return Step.Done(changed(a))
            }
            var selected = false
            if (n > 1 || !trusted(a.deckId)) { provider.select(a.deckId); markSelected(a.deckId); selected = true }
            var top = provider.top(a.deckId, 1).firstOrNull()
            if (top != key && !selected) { // the skipped select may have been stale (AnkiDroid #18270): look again after a real one
                provider.select(a.deckId); markSelected(a.deckId)
                top = provider.top(a.deckId, 1).firstOrNull()
            }
            if (top != key) {
                if (notTopOff || !tryNotTop()) return Step.Done(AnswerOutcome.NotWritten(a, AnswerOutcome.Reason.NOT_TOP, "the card is no longer first in AnkiDroid's queue"))
                return notTopTry(s, top)
            }
            val fresh = provider.stats(key) ?: return Step.Fail("the card could not be read")
            if (!AnswerState.same(a.before, fresh)) return Step.Done(changed(a))
            guard { hooks.expect(a.before) }
            s.attempted = true
            val t0 = clock()
            try {
                provider.answer(key, a.ease, a.tookMs.coerceIn(0, 3_600_000))
            } catch (e: Exception) {
                s.answerMs = clock() - t0
                guard { hooks.answerTimed(a, n, s.answerMs) }
                throw e
            }
            s.answerMs = clock() - t0
            guard { hooks.answerTimed(a, n, s.answerMs) }
            val after = provider.stats(key)
            if (after != null && StudyLoop.saved(a.before, after)) return Step.Done(AnswerOutcome.Saved(a, after, n, s.answerMs))
            if (after == null) return Step.Fail("the write could not be confirmed")
            val reps = after["reps"]?.toIntOrNull() ?: return Step.Fail("the card could not be read")
            if (reps == a.before["reps"]?.toIntOrNull()) return if (n == 1) Step.Again else Step.Fail("AnkiDroid did not apply the answer")
            return Step.Done(AnswerOutcome.NotWritten(a, AnswerOutcome.Reason.UNSURE, "AnkiDroid changed the card in an unexpected way"))
        } catch (e: Exception) {
            return Step.Fail(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * PLAN 195, step N: [s]'s card is not first ([topKey] is, or nothing): steps 3-5 for exactly that card anyway. Saved -> Saved; AnkiDroid
     * refused it (the card unchanged after the call, or the call failed) -> WAIT; unreadable after the call -> FAIL (the next attempt starts
     * with R). The first card is read before and after: if IT was answered, AnkiDroid answered the wrong card: UNSURE, and the try is
     * off until the app restarts (never the wrong card again).
     */
    private fun notTopTry(s: Slot, topKey: StudyLoop.Key?): Step {
        val a = s.a
        val key = a.key
        note("NOT_TOP try: card ${a.cardId} is not first (first: ${topKey?.let { "${it.nid}/${it.ord}" } ?: "none"}); writing it anyway")
        val fresh = provider.stats(key) ?: return Step.Fail("the card could not be read")
        if (!AnswerState.same(a.before, fresh)) return Step.Done(changed(a))
        val topBefore = topKey?.let { k -> try { provider.stats(k) } catch (e: Exception) { null } }
        guard { hooks.expect(a.before) }
        s.attempted = true
        val t0 = clock()
        val failed = try { provider.answer(key, a.ease, a.tookMs.coerceIn(0, 3_600_000)); null } catch (e: Exception) { e }
        s.answerMs = clock() - t0
        guard { hooks.answerTimed(a, s.n, s.answerMs) }
        val after = provider.stats(key)
        if (topKey != null && topBefore != null) {
            val topAfter = try { provider.stats(topKey) } catch (e: Exception) { null }
            if (StudyLoop.saved(topBefore, topAfter)) {
                notTopOff = true
                note("NOT_TOP try: AnkiDroid answered the FIRST card (${topKey.nid}/${topKey.ord}) instead of card ${a.cardId}; the try is off until the app restarts")
                if (after != null && StudyLoop.saved(a.before, after)) return Step.Done(AnswerOutcome.Saved(a, after, s.n, s.answerMs))
                return Step.Done(AnswerOutcome.NotWritten(a, AnswerOutcome.Reason.UNSURE, "AnkiDroid answered another card"))
            }
        }
        if (after != null && StudyLoop.saved(a.before, after)) {
            note("NOT_TOP try: card ${a.cardId} saved")
            return Step.Done(AnswerOutcome.Saved(a, after, s.n, s.answerMs))
        }
        if (after == null) return Step.Fail("the write could not be confirmed")
        if (AnswerState.same(a.before, after)) {
            s.attempted = false // the call is over and did not apply: from now on a saved-looking card was answered elsewhere
            return Step.Wait(failed?.let { "AnkiDroid refused it: ${it.message ?: it.javaClass.simpleName}" } ?: "AnkiDroid did not apply it")
        }
        return Step.Done(AnswerOutcome.NotWritten(a, AnswerOutcome.Reason.UNSURE, "AnkiDroid changed the card in an unexpected way"))
    }

    /** PLAN 195, WAIT: [s] leaves the queue for the waiting list (journal flag). A late one (already back from it once) ends NOT_TOP instead. */
    private fun wait(s: Slot, reason: String): AnswerOutcome {
        if (s.late) return end(s, AnswerOutcome.NotWritten(s.a, AnswerOutcome.Reason.NOT_TOP, "AnkiDroid refused it even when its card was first ($reason)"))
        lock.withLock {
            slots.remove(s)
            s.a = s.a.copy(waiting = true)
            waiting.add(s)
            persist(journal())
        }
        note("NOT_TOP try: card ${s.a.cardId} not saved ($reason); it waits for its card to be first again today")
        val o = AnswerOutcome.Waiting(s.a, reason)
        post(o)
        return o
    }

    /** PLAN 195: the waiting answers made on another day than today end NOT_TOP (they are never written late across a day). Io thread. */
    private fun expireWaiting(): List<AnswerOutcome> {
        val today = dayOf(wallNow())
        val old = lock.withLock { waiting.filter { dayOf(it.a.madeAt) != today } }
        return old.map { end(it, AnswerOutcome.NotWritten(it.a, AnswerOutcome.Reason.NOT_TOP, "its card was not first again on the day it was made")) }
    }

    /**
     * PLAN 195, io thread, inside a load: [top] is AnkiDroid's first card for [deckId] right now. An answer waiting for exactly that card
     * is written now, with every check (R, select [deckId], top, unchanged, write, read back), as if it had just been made: the caller
     * reads the queue again, so the card is never shown. It does not wait a second time: refused again, it ends NOT_TOP. Ends the
     * waiting answers whose day is over first. True when an answer for [top] was handled (the queue may have changed).
     */
    fun writeWaiting(deckId: Long, top: StudyLoop.Key?): Boolean = runLock.withLock {
        expireWaiting()
        if (top == null) return false
        val s = lock.withLock {
            waiting.firstOrNull { it.a.key == top }?.also {
                waiting.remove(it)
                it.late = true
                it.n = 0
                it.a = it.a.copy(waiting = false, deckId = deckId)
                slots.add(0, it)
                persist(journal()) // back in the queue BEFORE the write: a kill during it replays it as a normal answer (R first)
            }
        } ?: return false
        note("card ${s.a.cardId} is first again: writing its waiting answer")
        drain()
        true
    }

    /** Found written by reconcile: an own write of this process (Saved), else an earlier process's (AlreadySaved). */
    private fun wasSaved(s: Slot, now: Map<String, String?>): AnswerOutcome =
        if (s.attempted) AnswerOutcome.Saved(s.a, now, s.n, s.answerMs) else AnswerOutcome.AlreadySaved(s.a, now)

    private fun changed(a: QueuedAnswer) = AnswerOutcome.NotWritten(a, AnswerOutcome.Reason.CHANGED, "the card was changed in AnkiDroid meanwhile")

    private fun discarded(s: Slot): AnswerOutcome {
        if (s.attempted || s.a.restored) { // a write of ours may exist: one last look, and report it as saved if so
            val now = try { provider.stats(s.a.key) } catch (e: Exception) { null }
            if (now != null && StudyLoop.saved(s.a.before, now)) return wasSaved(s, now)
            if (now == null) return AnswerOutcome.NotWritten(s.a, AnswerOutcome.Reason.DISCARDED, "discarded; the card could not be read, so it may have been written")
        }
        return AnswerOutcome.NotWritten(s.a, AnswerOutcome.Reason.DISCARDED, "discarded")
    }

    /** cancel (not written) -> journal without it -> outcome: a told answer can never come back from the journal. */
    private fun end(s: Slot, o: AnswerOutcome): AnswerOutcome {
        if (o is AnswerOutcome.NotWritten) guard { hooks.cancel(s.a.before) }
        val wasWaiting = lock.withLock {
            slots.remove(s)
            val w = waiting.remove(s)
            persist(journal())
            w
        }
        if (s.late || wasWaiting) note(
            if (o is AnswerOutcome.NotWritten) "gave up: card ${s.a.cardId}'s waiting answer ends ${o.reason} (${o.detail})"
            else "saved late: card ${s.a.cardId}'s waiting answer is in AnkiDroid",
        )
        post(o)
        return o
    }

    private fun post(o: AnswerOutcome) = guard { hooks.outcome(o) }

    private fun note(text: String) = guard { hooks.note(text) }

    private inline fun guard(f: () -> Unit) { try { f() } catch (e: Exception) { /* a hook must not break the queue */ } }

    /**
     * Called with [lock] held. An EMPTY list clears the file (that needs no free space, so the last answer can always leave the
     * journal, and a told "not written" answer is never replayed); a rewrite that still fails is remembered in [dirty].
     */
    private fun persist(all: List<QueuedAnswer>): Boolean {
        for (tryNo in 1..2) {
            try {
                if (all.isEmpty() && tryNo == 1) store.clear() else store.write(AnswerJournal.encode(all))
                dirty = false
                return true
            } catch (e: Exception) {
                if (tryNo == 2) { dirty = true; guard { hooks.journalFailed(e.message ?: e.javaClass.simpleName) } }
            }
        }
        return false
    }

    companion object {
        /** How long a deck selection made by us is trusted without selecting again. */
        const val SELECT_TRUST_MS = 60_000L

        /** The phone's local calendar day ("yyyy-MM-dd") of a wall-clock time: the window of a waiting answer (PLAN 195). */
        val LOCAL_DAY: (Long) -> String = { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString() }
    }
}
