package com.husarp.ankigo.study

import com.husarp.ankigo.game.GameEvent
import java.io.File
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException

/** The journal as a file (`filesDir/study-answers.json`): written to a temp file and renamed over it, no fsync (the page cache survives the death of the process). */
class FileJournalStore(private val file: File) : JournalStore {
    override fun read(): String? = if (file.exists()) file.readText() else null

    override fun write(text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            tmp.writeText(text)
            if (!tmp.renameTo(file)) { // a file system that refuses to rename over a file: replace it in two steps
                file.delete()
                if (!tmp.renameTo(file)) throw java.io.IOException("could not replace ${file.name}")
            }
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    override fun clear() {
        File(file.parentFile, file.name + ".tmp").delete()
        if (file.exists() && !file.delete()) throw java.io.IOException("could not delete ${file.name}")
    }
}

/** A study screen's view of the host (all calls on the main thread). */
interface StudyListener {
    /** The answers still being saved changed; null = all resolved. Called once on register too. */
    fun unresolved(u: AnswerHost.Unresolved?)

    /** A terminal outcome (saved, already saved, or not written for a stated reason). */
    fun outcome(o: AnswerOutcome, info: AnswerHost.Info)

    /**
     * The game step of an answer is done (R10-B23: at the tap, not waiting for AnkiDroid; [absorbMs] is -1 now, nothing waits for
     * the detector; [gameMs] = how long the step took). [mine] = this screen made the answer: only then do its events belong in the
     * screen's session summary.
     */
    fun gameDone(answer: QueuedAnswer, events: List<GameEvent>, absorbMs: Long, gameMs: Long, mine: Boolean)
}

/**
 * The process-wide host of the study answers (R9 wave 6): ONE single-thread io executor runs EVERY study provider call in order,
 * and an answer's write task ([AnswerQueue.drain]) blocks it until the answer is resolved, so a load queued after it sees AnkiDroid's
 * real queue. It lives as long as the process, so a screen that is closed (or killed) never stops an answer from being written.
 * Screens register a [StudyListener] (main thread). Pure Kotlin: Android comes in through [Env] (tests use a fake and a real executor).
 *
 * The game step (R10-B23) runs at the TAP, in [enqueue], from the button and the card as shown: the battle never waits for
 * AnkiDroid. The write's end only settles it: saved / already saved -> [Env.settled] (the detector learns the card's real state,
 * the game is not touched), not written -> [Env.dropped] (the game keeps its result). Answers replayed from the journal after a
 * kill go through [Env.restored] first (the game counts the ones its save does not hold yet); nothing reaches AnkiDroid before.
 *
 * Routing: an outcome goes to the screen that made the answer while it is registered, else to the newest registered screen. A
 * terminal "not written" outcome nobody could be told about (no screen, e.g. an answer replayed from the journal after a restart)
 * waits for the next screen that registers. Whoever hears an outcome is told whether it made the answer ([Info.mine], and for
 * [StudyListener.gameDone]): the screen toasts every outcome but counts only its own answers in its session.
 */
class AnswerHost(
    provider: AnswerProvider,
    store: JournalStore,
    private val env: Env,
    private val io: ExecutorService,
    waiter: Waiter = LockWaiter(),
    clock: () -> Long = { System.nanoTime() / 1_000_000L },
    backoff: (Int) -> Long = Backoff::ms,
    parkAfter: Int = Backoff.PARK_AFTER,
    /** PLAN 195: try the write of a card that is not first, and keep a refused answer waiting for its card ([AnswerQueue]). */
    tryNotTop: () -> Boolean = { false },
    dayOf: (Long) -> String = AnswerQueue.LOCAL_DAY,
    wallNow: () -> Long = { System.currentTimeMillis() },
) {
    /** What the host needs from Android. [expect], [cancel] and [elapsed] may be called on any thread, the rest on the main thread. */
    interface Env {
        /** Runs [r] on the main thread. */
        fun post(r: Runnable)

        /** A monotonic clock in ms. */
        fun elapsed(): Long

        /**
         * `LiveSession.expectOwn`: right before each write attempt, [pinned] while the answer is unresolved (the detector must not
         * count it as a live answer however long that takes); a plain one (30 s) when it is saved, right before it is settled.
         */
        fun expect(before: Map<String, String?>, pinned: Boolean)

        /** `LiveSession.cancelOwn`: the answer ended NOT written. */
        fun cancel(before: Map<String, String?>)

        /** The game step of an answer, at the tap: the Battle log's front, `LiveSession.ownAnswer`. [done] gets its events and how long it took. */
        fun gameStep(answer: QueuedAnswer, front: String?, done: (events: List<GameEvent>, gameMs: Long) -> Unit)

        /** Answers replayed from the journal: `LiveSession.ownRestored` (the game counts each one its save does not hold yet, once). [done] when that is decided. */
        fun restored(answers: List<QueuedAnswer>, done: () -> Unit)

        /** The answer is in AnkiDroid ([after] = the card now; [saved] = written by this process): reviews.csv, `LiveSession.ownSettled`. */
        fun settled(answer: QueuedAnswer, after: Map<String, String?>, saved: Boolean)

        /** The answer ended NOT written: `LiveSession.ownDropped` (the game keeps its result). */
        fun dropped(answer: QueuedAnswer)

        fun log(text: String)
    }

    /** The state of the answers not resolved yet. [attempt] 0 = waiting / being written, > 0 = the last try failed ([reason]); [parked] = no more automatic tries. */
    data class Unresolved(val count: Int, val since: Long, val attempt: Int, val reason: String?, val parked: Boolean, val seq: Long) {
        val retrying get() = reason != null
    }

    /**
     * [mine] = the screen that got it made the answer; [writeMs] = the whole write task (-1: unknown); [postedAt] = when the io thread posted it;
     * [late] (PLAN 195) = the end of an answer that waited for its card (its [AnswerOutcome.Waiting] came earlier, or it was restored waiting).
     */
    data class Info(val mine: Boolean, val writeMs: Long, val postedAt: Long, val late: Boolean = false)

    private class Meta(val front: String?, @Volatile var owner: StudyListener?)

    private val metas = ConcurrentHashMap<Long, Meta>() // in memory only; never persisted
    private val listeners = ArrayList<StudyListener>()
    private val orphans = ArrayList<AnswerOutcome>()
    private val owned = IdentityHashMap<Any, ArrayList<Future<*>>>()
    private var count = 0 // main thread: answers made or restored and not resolved (not the waiting ones)
    private val waitingSeqs = HashSet<Long>() // main thread (PLAN 195): answers waiting for their card; their end does not count down [count]
    private var since = 0L
    private var state: Unresolved? = null
    private var started = false

    @Volatile private var drainStart = 0L

    private val hooks = object : AnswerHooks {
        override fun expect(before: Map<String, String?>) = env.expect(before, true)
        override fun cancel(before: Map<String, String?>) = env.cancel(before)
        override fun journalFailed(reason: String) = env.log("the answer journal could not be written: $reason")
        override fun note(text: String) = env.log(text)
        override fun outcome(o: AnswerOutcome) {
            val at = env.elapsed()
            val writeMs = if (o.isTerminal || o is AnswerOutcome.Waiting) at - drainStart else -1L
            if (writeMs >= 0) drainStart = at   // R18-A: answers made ahead are written one after the other in one drain: each one's time starts where the one before it ended
            env.post(Runnable { onOutcome(o, writeMs, at) })
        }
    }

    val queue = AnswerQueue(provider, store, hooks, waiter, clock, backoff, parkAfter, tryNotTop = tryNotTop, dayOf = dayOf, wallNow = wallNow)

    private val drainTask = Runnable {
        drainStart = env.elapsed()
        try { queue.drain() } catch (e: Exception) { env.log("writing the answers failed: ${e.javaClass.simpleName} ${e.message}") }
    }

    // ---------------------------------------------------------------- start

    /**
     * Main thread, once per process (a second call does nothing): reads the journal; the answers it holds are replayed before any
     * other io task, and only once the game has decided which of them it already counted ([Env.restored]): no write of them can
     * reach AnkiDroid (and the detector) before that.
     */
    fun start() {
        if (started) return
        started = true
        val r = queue.restore()
        r.problem?.let { env.log("answer journal: $it") }
        if (r.count > 0 || r.waiting > 0) {
            env.log("replaying ${r.count} answer(s) from the journal" + if (r.waiting > 0) "; ${r.waiting} still wait for their card (PLAN 195)" else "")
            count = r.count
            since = env.elapsed()
            state = if (count == 0) null else Unresolved(count, since, 0, null, false, queue.pending().firstOrNull()?.seq ?: 0)
            waitingSeqs += queue.waitingAnswers().map { it.seq }
            val decided = CountDownLatch(1)
            execute(Runnable { decided.await(); drainTask.run() }) // first on the io thread: everything queued later waits behind it
            env.restored((queue.pending() + queue.waitingAnswers()).filter { it.restored }) { decided.countDown() }
        }
    }

    // ---------------------------------------------------------------- the io thread

    /** Queues [task] behind everything before it (every study provider call goes through here). [owner]'s queued tasks are dropped by [cancelOwner]. */
    fun submit(owner: Any?, task: Runnable) {
        val f = try {
            io.submit(Runnable { try { task.run() } catch (e: Exception) { env.log("a study task failed: ${e.javaClass.simpleName} ${e.message}") } })
        } catch (e: RejectedExecutionException) { return }
        if (owner != null) synchronized(owned) {
            val list = owned.getOrPut(owner) { ArrayList() }
            list.removeAll { it.isDone }
            list += f
        }
    }

    /** A screen is gone: what it queued and the io thread has not started yet never runs (and stops holding the screen). */
    fun cancelOwner(owner: Any) {
        val list = synchronized(owned) { owned.remove(owner) } ?: return
        for (f in list) f.cancel(false)
    }

    private fun execute(r: Runnable) {
        try { io.execute(r) } catch (e: RejectedExecutionException) { env.log("the study io thread is gone") }
    }

    // ---------------------------------------------------------------- answers

    fun nextSeq() = queue.nextSeq()

    /** Answers not resolved yet, as the io thread sees them (exact; the counts read checks it). */
    fun unresolvedNow(): Boolean = queue.size > 0

    /**
     * Main thread. The answer is written in the background, in order, however long it takes; its game step runs right here, at the
     * tap (R10-B23). False = the journal file could not be written (the answer is still queued in memory).
     */
    fun enqueue(a: QueuedAnswer, front: String?, owner: StudyListener?): Boolean {
        val meta = Meta(front, owner)
        metas[a.seq] = meta
        val persisted = queue.enqueue(a) // the journal first, then the entry can run
        if (count == 0) since = env.elapsed()
        count++
        state = Unresolved(count, since, 0, null, false, a.seq)
        broadcast()
        execute(drainTask) // before any load the caller queues next
        env.gameStep(a, front) { events, gameMs ->
            val (screen, mine) = route(meta)
            screen?.gameDone(a, events, -1, gameMs, mine)
        }
        return persisted
    }

    fun retryNow() = queue.retryNow()

    fun discard(seq: Long) = queue.discard(seq)

    fun invalidateSelection() = queue.invalidateSelection()

    fun ensureSelected(deckId: Long, select: () -> Unit) = queue.ensureSelected(deckId, select)

    /** PLAN 195, io thread (a load): an answer waiting for [top] is written now; true = read the queue again (the card must not show). */
    fun writeWaiting(deckId: Long, top: StudyLoop.Key?): Boolean = queue.writeWaiting(deckId, top)

    /** Main thread: the current state (null = nothing unresolved). */
    fun unresolved(): Unresolved? = state

    // ---------------------------------------------------------------- screens

    /** Main thread. A new screen: waiting answers try again at once, the deck is selected again by its first load, it hears the state and what could not be told. */
    fun register(l: StudyListener) {
        if (l !in listeners) listeners += l
        queue.invalidateSelection()
        queue.retryNow()
        l.unresolved(state)
        if (orphans.isNotEmpty()) {
            val told = orphans.toList()
            orphans.clear()
            val now = env.elapsed()
            for (o in told) l.outcome(o, Info(false, -1, now))
        }
    }

    fun unregister(l: StudyListener) {
        listeners.remove(l)
        for (m in metas.values) if (m.owner === l) m.owner = null
    }

    private fun broadcast() { for (l in listeners.toList()) l.unresolved(state) }

    private fun route(meta: Meta?): Pair<StudyListener?, Boolean> {
        val owner = meta?.owner
        if (owner != null && owner in listeners) return owner to true
        return listeners.lastOrNull() to false
    }

    private fun onOutcome(o: AnswerOutcome, writeMs: Long, postedAt: Long) {
        if (o is AnswerOutcome.Retrying) {
            val n = if (o.answer.seq in waitingSeqs) count + 1 else count // a waiting answer back for its write is unresolved again
            state = Unresolved(n, since, o.attempt, o.reason, o.waitMs < 0, o.answer.seq)
            env.log("answer ${o.answer.cardId} not saved yet (try ${o.attempt}): ${o.reason}${if (o.waitMs < 0) "; waiting for the user" else "; next try in ${o.waitMs} ms"}")
            broadcast()
            return
        }
        if (o is AnswerOutcome.Waiting) { // PLAN 195: not written for now, not unresolved either: it ends later, told then
            count = (count - 1).coerceAtLeast(0)
            waitingSeqs += o.answer.seq
            state = if (count == 0) null else Unresolved(count, since, 0, null, false, queue.pending().firstOrNull()?.seq ?: 0)
            env.log("answer ${o.answer.cardId} ${describe(o)}")
            broadcast()
            val (target, mine) = route(metas[o.answer.seq])
            target?.outcome(o, Info(mine, writeMs, postedAt))
            return
        }
        val late = waitingSeqs.remove(o.answer.seq)
        if (!late) count = (count - 1).coerceAtLeast(0)
        state = if (count == 0) null else Unresolved(count, since, 0, null, false, queue.pending().firstOrNull()?.seq ?: 0)
        val meta = metas.remove(o.answer.seq)
        val (target, mine) = route(meta)
        env.log("answer ${o.answer.cardId} ${describe(o)}")
        broadcast()
        if (target != null) target.outcome(o, Info(mine, writeMs, postedAt, late))
        else if (o is AnswerOutcome.NotWritten) { orphans += o; while (orphans.size > MAX_ORPHANS) orphans.removeAt(0) }
        when (o) { // the game counted it at the tap (or at the replay): the end only settles it
            is AnswerOutcome.Saved -> settle(o.answer, o.after, true)
            is AnswerOutcome.AlreadySaved -> settle(o.answer, o.after, false)
            is AnswerOutcome.NotWritten -> env.dropped(o.answer)
            is AnswerOutcome.Retrying, is AnswerOutcome.Waiting -> {}
        }
    }

    private fun settle(a: QueuedAnswer, after: Map<String, String?>, saved: Boolean) {
        env.expect(a.before, false) // resolved: the pin ends (30 s, or the settle's absorb takes it first)
        env.settled(a, after, saved)
    }

    private fun describe(o: AnswerOutcome) = when (o) {
        is AnswerOutcome.Saved -> "saved after ${o.attempts} ${if (o.attempts == 1) "try" else "tries"}, ANSWER ${o.answerMs} ms"
        is AnswerOutcome.AlreadySaved -> "was already saved (an earlier run of the app wrote it)"
        is AnswerOutcome.NotWritten -> "NOT written: ${o.reason} (${o.detail})"
        is AnswerOutcome.Retrying -> "retrying"
        is AnswerOutcome.Waiting -> "waits for its card to be first again (${o.reason})"
    }

    companion object {
        private const val MAX_ORPHANS = 8
    }
}
