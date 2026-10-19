package com.husarp.ankigo.study

import com.husarp.ankigo.game.GameEvent
import java.io.File
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
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
     * The game step of a saved answer is done ([absorbMs] / [gameMs] as `LiveSession.ownAnswer` measured them). [mine] = this screen
     * made the answer: only then do its events belong in the screen's session summary.
     */
    fun gameDone(answer: QueuedAnswer, events: List<GameEvent>, absorbMs: Long, gameMs: Long, mine: Boolean)
}

/**
 * The process-wide host of the study answers (R9 wave 6): ONE single-thread io executor runs EVERY study provider call in order,
 * and an answer's write task ([AnswerQueue.drain]) blocks it until the answer is resolved, so a load queued after it sees AnkiDroid's
 * real queue. It lives as long as the process, so a screen that is closed (or killed) never stops an answer from being written; the
 * game step of a saved answer runs here too, whether a screen is still there or not. Screens register a [StudyListener] (main thread).
 * Pure Kotlin: Android comes in through [Env] (tests use a fake and a real executor).
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
) {
    /** What the host needs from Android. [expect], [cancel] and [elapsed] may be called on any thread, the rest on the main thread. */
    interface Env {
        /** Runs [r] on the main thread. */
        fun post(r: Runnable)

        /** A monotonic clock in ms. */
        fun elapsed(): Long

        /**
         * `LiveSession.expectOwn`: right before each write attempt, [pinned] while the answer is unresolved (the detector must not
         * count it as a live answer however long that takes); a plain one (30 s) when it is saved, right before its game step.
         */
        fun expect(before: Map<String, String?>, pinned: Boolean)

        /** `LiveSession.cancelOwn`: the answer ended NOT written. */
        fun cancel(before: Map<String, String?>)

        /** The game step of a saved answer: recordReview, the Battle log's front, `LiveSession.ownAnswer`. [done] gets its events. */
        fun gameStep(saved: AnswerOutcome.Saved, front: String?, onTiming: (absorbMs: Long, gameMs: Long) -> Unit, done: (List<GameEvent>) -> Unit)

        fun log(text: String)
    }

    /** The state of the answers not resolved yet. [attempt] 0 = waiting / being written, > 0 = the last try failed ([reason]); [parked] = no more automatic tries. */
    data class Unresolved(val count: Int, val since: Long, val attempt: Int, val reason: String?, val parked: Boolean, val seq: Long) {
        val retrying get() = reason != null
    }

    /** [mine] = the screen that got it made the answer; [writeMs] = the whole write task (-1: unknown); [postedAt] = when the io thread posted it. */
    data class Info(val mine: Boolean, val writeMs: Long, val postedAt: Long)

    private class Meta(val front: String?, @Volatile var owner: StudyListener?)

    private val metas = ConcurrentHashMap<Long, Meta>() // in memory only; never persisted
    private val listeners = ArrayList<StudyListener>()
    private val orphans = ArrayList<AnswerOutcome>()
    private val owned = IdentityHashMap<Any, ArrayList<Future<*>>>()
    private var count = 0 // main thread: answers made or restored and not resolved
    private var since = 0L
    private var state: Unresolved? = null
    private var started = false

    @Volatile private var drainStart = 0L

    private val hooks = object : AnswerHooks {
        override fun expect(before: Map<String, String?>) = env.expect(before, true)
        override fun cancel(before: Map<String, String?>) = env.cancel(before)
        override fun journalFailed(reason: String) = env.log("the answer journal could not be written: $reason")
        override fun outcome(o: AnswerOutcome) {
            val at = env.elapsed()
            val writeMs = if (o.isTerminal) at - drainStart else -1L
            env.post(Runnable { onOutcome(o, writeMs, at) })
        }
    }

    val queue = AnswerQueue(provider, store, hooks, waiter, clock, backoff, parkAfter)

    private val drainTask = Runnable {
        drainStart = env.elapsed()
        try { queue.drain() } catch (e: Exception) { env.log("writing the answers failed: ${e.javaClass.simpleName} ${e.message}") }
    }

    // ---------------------------------------------------------------- start

    /** Main thread, once per process (a second call does nothing): reads the journal; the answers it holds are replayed before any other io task. */
    fun start() {
        if (started) return
        started = true
        val r = queue.restore()
        r.problem?.let { env.log("answer journal: $it") }
        if (r.count > 0) {
            env.log("replaying ${r.count} answer(s) from the journal")
            count = r.count
            since = env.elapsed()
            state = Unresolved(count, since, 0, null, false, queue.pending().firstOrNull()?.seq ?: 0)
            execute(drainTask)
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

    /** Main thread. The answer is written in the background, in order, however long it takes. False = the journal file could not be written (the answer is still queued in memory). */
    fun enqueue(a: QueuedAnswer, front: String?, owner: StudyListener?): Boolean {
        metas[a.seq] = Meta(front, owner)
        val persisted = queue.enqueue(a) // the journal first, then the entry can run
        if (count == 0) since = env.elapsed()
        count++
        state = Unresolved(count, since, 0, null, false, a.seq)
        broadcast()
        execute(drainTask) // before any load the caller queues next
        return persisted
    }

    fun retryNow() = queue.retryNow()

    fun discard(seq: Long) = queue.discard(seq)

    fun invalidateSelection() = queue.invalidateSelection()

    fun ensureSelected(deckId: Long, select: () -> Unit) = queue.ensureSelected(deckId, select)

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
            state = Unresolved(count, since, o.attempt, o.reason, o.waitMs < 0, o.answer.seq)
            env.log("answer ${o.answer.cardId} not saved yet (try ${o.attempt}): ${o.reason}${if (o.waitMs < 0) "; waiting for the user" else "; next try in ${o.waitMs} ms"}")
            broadcast()
            return
        }
        count = (count - 1).coerceAtLeast(0)
        state = if (count == 0) null else Unresolved(count, since, 0, null, false, queue.pending().firstOrNull()?.seq ?: 0)
        val meta = metas.remove(o.answer.seq)
        val (target, mine) = route(meta)
        env.log("answer ${o.answer.cardId} ${describe(o)}")
        broadcast()
        if (target != null) target.outcome(o, Info(mine, writeMs, postedAt))
        else if (o is AnswerOutcome.NotWritten) { orphans += o; while (orphans.size > MAX_ORPHANS) orphans.removeAt(0) }
        if (o is AnswerOutcome.Saved) {
            var absorb = -1L
            var game = -1L
            env.expect(o.answer.before, false) // resolved: the pin ends (30 s, or the game step's absorb settles it first)
            env.gameStep(o, meta?.front, { a, g -> absorb = a; game = g }) { events ->
                val (screen, ownAnswer) = route(meta)
                screen?.gameDone(o.answer, events, absorb, game, ownAnswer)
            }
        }
    }

    private fun describe(o: AnswerOutcome) = when (o) {
        is AnswerOutcome.Saved -> "saved after ${o.attempts} ${if (o.attempts == 1) "try" else "tries"}, ANSWER ${o.answerMs} ms"
        is AnswerOutcome.AlreadySaved -> "was already saved (an earlier run of the app wrote it)"
        is AnswerOutcome.NotWritten -> "NOT written: ${o.reason} (${o.detail})"
        is AnswerOutcome.Retrying -> "retrying"
    }

    companion object {
        private const val MAX_ORPHANS = 8
    }
}
