package com.husarp.ankigo.study

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

    val isTerminal get() = this !is Retrying
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
 *  2  top(deck) == key?  no -> (look again after a real select, if the first select was skipped) NOT_TOP
 *  3  card still the same as shown?  no -> CHANGED;  unreadable -> FAIL
 *  4  hooks.expect; ANSWER (timed alone)
 *  5  read the card: saved -> Saved | unchanged reps -> n == 1 ? attempt 2 at once : FAIL | unreadable -> FAIL | else UNSURE
 *  FAIL(n): Retrying outcome, wait backoff(n) (parked after [parkAfter] attempts) -> attempt n + 1 (which starts with R)
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
) {
    class Restored(val count: Int, val problem: String?)

    private class Slot(val a: QueuedAnswer) {
        var n = 0
        var attempted = false // hooks.expect was called in this process: a write of ours may exist
        var answerMs = -1L
        @Volatile var discard = false
    }

    private sealed class Step {
        class Done(val outcome: AnswerOutcome) : Step()
        object Again : Step()
        class Fail(val reason: String) : Step()
    }

    private val lock = ReentrantLock() // slots, journal, selection
    private val runLock = ReentrantLock() // at most one writer, whatever the callers do
    private val slots = ArrayList<Slot>()
    private val seqs = AtomicLong(0)
    private var restoredOnce: Restored? = null
    private var selectedDeck: Long? = null
    private var selectedAt = 0L
    private var dirty = false // the journal file still holds more than the unresolved answers (a rewrite failed): tried again before the next answer starts

    /** Answers not yet ended, oldest first (a copy). */
    fun pending(): List<QueuedAnswer> = lock.withLock { slots.map { it.a } }

    val size: Int get() = lock.withLock { slots.size }

    /** A fresh, increasing sequence number for [QueuedAnswer.seq]. */
    fun nextSeq(): Long = seqs.incrementAndGet()

    /** Reads the journal once per process (a second call does nothing); the entries are processed first by [drain]. */
    fun restore(): Restored = lock.withLock {
        restoredOnce?.let { return Restored(0, it.problem) }
        val d = try { AnswerJournal.decode(store.read()) } catch (e: Exception) { AnswerJournal.Decoded(emptyList(), "the journal could not be read: ${e.message}") }
        val known = slots.map { it.a.seq }.toSet()
        val fresh = d.answers.filter { it.seq !in known }
        slots.addAll(0, fresh.map { Slot(it.copy(restored = true)) })
        for (a in fresh) seqs.updateAndGet { maxOf(it, a.seq) }
        Restored(fresh.size, d.problem).also { restoredOnce = it }
    }

    /**
     * Adds an answer. The journal (all entries) is written FIRST, then the entry becomes visible to [drain]. A journal that cannot
     * be written does not refuse the answer: it stays queued in memory ([AnswerHooks.journalFailed]). Returns false then.
     */
    fun enqueue(a: QueuedAnswer): Boolean = lock.withLock {
        require(a.ease in 1..4 && AnswerState.usable(a.before)) { "unusable answer" }
        seqs.updateAndGet { maxOf(it, a.seq) }
        val ok = persist(slots.map { it.a } + a)
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

    /** Io thread. Resolves every queued answer, oldest first, and returns their end outcomes. Blocks while one is retrying. */
    fun drain(): List<AnswerOutcome> = runLock.withLock {
        val out = ArrayList<AnswerOutcome>()
        while (true) {
            val s = lock.withLock { slots.firstOrNull() } ?: break
            out += resolve(s)
        }
        out
    }

    private fun resolve(s: Slot): AnswerOutcome {
        waiter.clear()
        lock.withLock { if (dirty) persist(slots.map { it.a }) } // a told answer must not stay in the journal: try the failed rewrite again before this answer starts
        while (true) {
            if (s.discard) return end(s, discarded(s))
            s.n++
            when (val r = attempt(s)) {
                is Step.Done -> return end(s, r.outcome)
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
            if (n > 1 || a.restored) {
                // an earlier attempt of this process may have applied: keep its expectation alive (30 s) while the card is read, so a
                // long back-off can never let a poll count our own answer as a live one as well as the game step
                if (s.attempted) guard { hooks.expect(a.before) }
                val now = provider.stats(key) ?: return Step.Fail("the card could not be read")
                if (StudyLoop.saved(a.before, now)) {
                    // only a write of ours (this process's, or a dead one's) can have made it look saved; else someone else answered it
                    return Step.Done(if (s.attempted || a.restored) wasSaved(s, now) else changed(a))
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
            if (top != key) return Step.Done(AnswerOutcome.NotWritten(a, AnswerOutcome.Reason.NOT_TOP, "the card is no longer first in AnkiDroid's queue"))
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
        lock.withLock {
            slots.remove(s)
            persist(slots.map { it.a })
        }
        post(o)
        return o
    }

    private fun post(o: AnswerOutcome) = guard { hooks.outcome(o) }

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
    }
}
