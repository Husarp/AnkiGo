package com.husarp.ankigo.study

import com.husarp.ankigo.CardState
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.live.CardLedger
import com.husarp.ankigo.live.LiveDetector
import com.husarp.ankigo.live.OwnAhead
import com.husarp.ankigo.live.baselineAnswers
import com.husarp.ankigo.live.decode
import com.husarp.ankigo.live.encode
import com.husarp.ankigo.live.withPendingOwn
import com.husarp.ankigo.live.withoutPendingOwn
import java.util.ArrayDeque
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/** An io "thread" the test runs one task at a time; a [Die] thrown by a task reaches the test. */
class ManualIo : AbstractExecutorService() {
    val tasks = ArrayDeque<Runnable>()
    override fun execute(command: Runnable) { tasks.add(command) }
    fun runOne(): Boolean { val t = tasks.poll() ?: return false; t.run(); return true }
    override fun shutdown() {}
    override fun shutdownNow(): MutableList<Runnable> = ArrayList(tasks).also { tasks.clear() }
    override fun isShutdown() = false
    override fun isTerminated() = false
    override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
}

/**
 * R10-B23 in one deterministic, single-threaded world: the REAL [AnswerHost] + [AnswerQueue] (io on a [ManualIo], the main thread a
 * queue of posts), the game (its counts and the card map saved with it), the detector built from the REAL [CardLedger] and
 * [baselineAnswers], and LiveSession's own-answer rules on the REAL [OwnAhead] / [withPendingOwn] / [withoutPendingOwn] (the few
 * lines of glue LiveSession has are mirrored in [Proc]). A pretend AnkiDroid ([FakeAnki]) holds the cards. [die] ends the process:
 * the journal, the game as last SAVED ([disk]) and AnkiDroid stay; the next process restores the journal.
 *
 * Every answer has its own id (its tap time): the game counts game steps per answer and detector answers per card, so [check] can
 * tell that every written answer counted exactly once (its step or, when the step died unsaved after the journal let it go, the
 * detector's catch-up), and an answer that was not written at most once (its step: a battle is never undone).
 */
class InstantSim(cards: Int = 4) {
    val deck = 7L
    val fake = FakeAnki().also { f -> for (i in 1..cards) f.add(deck, 100L + i); f.selected = deck }
    val journal = MemStore()

    /** The game: game steps per answer id, detector answers per card, the card map saved with it. */
    class Game(val steps: HashMap<Long, Int> = HashMap(), val detected: HashMap<Long, Int> = HashMap(), var snapshot: String? = null) {
        fun copy() = Game(HashMap(steps), HashMap(detected), snapshot)
        fun differs(o: Game) = steps != o.steps || detected != o.detected || snapshot != o.snapshot
    }

    var disk = Game()
    val made = ArrayList<QueuedAnswer>() // every answer tapped, in order (ground truth, survives a death)
    val outcomes = HashMap<Long, AnswerOutcome>() // terminal outcome by answer id
    val ends = HashMap<Long, Int>() // settles + drops by answer id: exactly one per answer that ended in this world
    val violations = ArrayList<String>()
    val trace = ArrayList<String>() // what happened, for a failing check
    var deaths = 0
    private var taps = 0L

    val QueuedAnswer.id get() = madeAt

    lateinit var p: Proc

    /** The card's row as AnkiGo reads it (without the pretend faults). */
    fun row(c: FakeAnki.Card): Map<String, String?> = mapOf(
        "_id" to c.id.toString(), "note_id" to c.nid.toString(), "deck_id" to c.deck.toString(), "reps" to c.reps.toString(),
        "due" to c.due.toString(), "queue" to c.queue.toString(), "last_review_time_secs" to c.last?.toString(),
    )

    fun state(c: FakeAnki.Card) = CardState.from(row(c))!!

    /** One process: the main thread's posts, the io thread, the game in memory, LiveSession's own answers and (maybe) a detector. */
    inner class Proc : AnswerHost.Env {
        val main = ArrayDeque<Runnable>()
        val io = ManualIo()
        val game = disk.copy()
        val ahead = OwnAhead()
        var ledger: CardLedger? = null
        var worker = "[]" // LiveDetector.snapshotJson: the detector's newest map
        var delivered = "[]" // LiveDetector.snapshot(): the map as handed to the game
        var rated = emptySet<Long>()
        /** What the game decides for replayed answers runs at once unless a test holds it (the game still loading). */
        var holdRestore = false
        var heldRestore: (() -> Unit)? = null

        private val waiter = object : Waiter {
            override fun await(ms: Long): Boolean {
                fake.clock.ms += maxOf(ms, 0)
                if (ms < 0) { fake.plans.clear(); fake.chaos = null; return true } // parked: the user fixed AnkiDroid and taps Retry
                return false
            }
            override fun wake() {}
            override fun clear() {}
        }

        val host = AnswerHost(fake, journal, this, io, waiter, { fake.clock.ms }, { 10L }, 3)

        private fun now() = fake.clock.ms
        private fun before(a: QueuedAnswer) = CardState.from(a.before)!!

        // ---- AnswerHost.Env (the main thread except expect / cancel)
        override fun post(r: Runnable) { main.add(r) }
        override fun elapsed() = fake.clock.ms
        override fun expect(before: Map<String, String?>, pinned: Boolean) { CardState.from(before)?.let { ledger?.own?.expect(it.id, it.reps + 1, now(), pinned) } }
        override fun cancel(before: Map<String, String?>) { CardState.from(before)?.let { ledger?.own?.cancel(it.id, it.reps + 1) } }
        override fun log(text: String) {}

        override fun gameStep(answer: QueuedAnswer, front: String?, done: (List<GameEvent>, Long) -> Unit) { ownAnswer(answer); done(emptyList(), 0) }

        override fun restored(answers: List<QueuedAnswer>, done: () -> Unit) {
            val decide = { ownRestored(answers); done() }
            if (holdRestore) heldRestore = decide else decide()
        }

        override fun settled(answer: QueuedAnswer, after: Map<String, String?>, saved: Boolean) {
            if (ends.merge(answer.id, 1, Int::plus)!! > 1) violations += "answer ${answer.id} ended twice"
            ownSettled(before(answer), CardState.from(after)!!)
        }

        override fun dropped(answer: QueuedAnswer) {
            if (ends.merge(answer.id, 1, Int::plus)!! > 1) violations += "answer ${answer.id} ended twice"
            ownDropped(before(answer))
        }

        // ---- LiveSession's own-answer glue (mirrored line by line)
        fun ownAnswer(a: QueuedAnswer) {
            val row = ahead.add(before(a))
            ledger?.own?.expect(row.id, row.reps, now(), pinned = true)
            if (ledger != null) game.snapshot = ahead.into(delivered) // saveSnapshotInto
            else game.snapshot = ahead.into(game.snapshot)
            game.steps.merge(a.id, 1, Int::plus) // g.onAnswer
        }

        fun ownSettled(before: CardState, after: CardState) {
            val l = ledger
            if (l == null) {
                ahead.remove(before)
                val map = runCatching { decode(game.snapshot ?: "[]") }.getOrDefault(emptyMap())
                game.snapshot = ahead.into(encode(map + (after.id to after)))
                return
            }
            l.absorb(after, now()) // LiveDetector.drainAbsorbs
            rated = rated + after.id
            worker = l.snapshot(rated)
            delivered = worker
            ahead.remove(before)
            game.snapshot = ahead.into(delivered) // saveSnapshot
        }

        fun ownDropped(before: CardState) {
            val row = ahead.remove(before) ?: return
            if (ledger == null) game.snapshot = game.snapshot?.let { withoutPendingOwn(it, listOf(row)) }
            else game.snapshot = ahead.into(delivered)
        }

        fun ownRestored(answers: List<QueuedAnswer>) {
            for (a in answers) {
                val b = before(a)
                val counted = OwnAhead.counted(b, game.snapshot, ledger?.let { delivered })
                trace += "replay ${a.id} card ${b.id}@${b.reps}: ${if (counted) "already counted" else "step now"}"
                if (counted) {
                    val row = ahead.add(b)
                    ledger?.own?.expect(row.id, row.reps, now(), pinned = true)
                } else ownAnswer(a)
            }
        }

        // ---- the detector (LiveSession.start + LiveDetector.startSession / pollOnce)
        fun startDetector() {
            if (ledger != null) return
            val l = CardLedger()
            ledger = l
            for (r in ahead.pending) l.own.expect(r.id, r.reps, now(), pinned = true)
            val start = if (ahead.isEmpty()) game.snapshot else withPendingOwn(game.snapshot, ahead.pending)
            val restored = start?.let { runCatching { decode(it) }.getOrNull() }
            delivered = if (restored != null) start else "[]"
            val base = fake.cards.values.map { state(it) }
            base.forEach { l.cards[it.id] = it }
            rated = ratedNow()
            val missed = baselineAnswers(restored, base, rated, { null }, 2_000_000L, l.own, now())
            trace += "detector start: catch-up ${missed.map { it.cardId }}"
            worker = l.snapshot(rated)
            if (missed.isNotEmpty()) { delivered = worker; deliver(missed) }
        }

        /** A full poll (it reads every card rated today). */
        fun poll() {
            val l = ledger ?: return
            val now = ratedNow()
            if (now != rated) l.dirty = true
            rated = now
            val out = ArrayList<LiveDetector.Event>()
            for (id in rated) fake.cards[id]?.let { l.compare(state(it), rated, { null }, now(), 2_000_000L, out) }
            if (l.dirty) worker = l.snapshot(rated)
            if (out.isNotEmpty()) { trace += "poll: $out"; delivered = worker; deliver(out) }
        }

        private fun deliver(events: List<LiveDetector.Event>) {
            for (e in events) when (e) {
                is LiveDetector.Event.Answer -> game.detected.merge(e.cardId, 1, Int::plus)
                is LiveDetector.Event.Undo -> violations += "the detector saw an undo of card ${e.cardId} (nobody undid anything)"
                else -> {}
            }
            game.snapshot = ahead.into(delivered) // saveSnapshotInto, with the answers just counted
        }

        private fun ratedNow() = fake.revlog.map { it.cardId }.toSet()

        fun pumpMain() { while (true) (main.poll() ?: return).run() }
    }

    // ---- the world

    /**
     * A new process: restores the journal; [detector] = a detector starts, [detectorFirst] = before the replay is decided;
     * [holdRestore] = the game is still loading, so the replay waits for [Proc.heldRestore].
     */
    fun boot(detectorFirst: Boolean, detector: Boolean = true, holdRestore: Boolean = false) {
        trace += "boot (detector ${if (!detector) "none" else if (detectorFirst) "first" else "after the replay"}), journal ${AnswerJournal.decode(journal.text).answers.map { it.madeAt }}"
        p = Proc()
        p.holdRestore = holdRestore
        p.host.register(recorder)
        if (detector && detectorFirst) p.startDetector()
        p.host.start()
        if (detector && !detectorFirst) p.startDetector()
    }

    fun die(detectorFirst: Boolean = false, detector: Boolean = true, holdRestore: Boolean = false) { deaths++; boot(detectorFirst, detector, holdRestore) }

    /** The 300 ms coalesced save: the game as it is now goes to disk. */
    fun save() { disk = p.game.copy(); trace += "save" }

    /**
     * The user answers the card on top (only possible once the last answer is resolved: a card AnkiDroid confirmed). The game
     * changed since the last save is saved first: GameRepo saves 300 ms after every change, and nobody reads and answers a card
     * that fast (a death can still come between a tap and its save, or anywhere on the io thread).
     */
    fun tap(ease: Int = 3, holdSession: Boolean = true): QueuedAnswer? {
        if (p.host.queue.size > 0 || p.main.isNotEmpty()) return null
        val c = fake.queueOf(deck).firstOrNull() ?: return null
        if (holdSession) p.startDetector() // the study screen holds the live session before its first card (a no-op when running)
        if (p.game.differs(disk)) save()
        val id = 10_000L + taps++
        val a = QueuedAnswer(p.host.nextSeq(), deck, c.nid, c.ord, c.id, row(c), ease, id, id, buttons = 4)
        made += a
        trace += "tap ${a.id} card ${c.id}@${c.reps} ease $ease"
        p.host.enqueue(a, "front", recorder)
        return a
    }

    /** The study screen of every process: records the terminal outcomes. */
    private val recorder = object : StudyListener {
        override fun unresolved(u: AnswerHost.Unresolved?) {}
        override fun outcome(o: AnswerOutcome, info: AnswerHost.Info) { if (o.isTerminal) { outcomes[o.answer.id] = o; trace += "end ${o.answer.id}: ${o.javaClass.simpleName}" } }
        override fun gameDone(answer: QueuedAnswer, events: List<GameEvent>, absorbMs: Long, gameMs: Long, mine: Boolean) {}
    }

    /** One io task; a [Die] inside it ends the process (a new one boots with [detectorFirst]). */
    fun io(detectorFirst: Boolean = false): Boolean {
        val proc = p
        return try { proc.io.runOne() } catch (d: Die) { trace += "died on the io thread"; die(detectorFirst); true }
    }

    /** Io and main until nothing is left (a death on the way boots the next process with [detectorFirst]). */
    fun settle(detectorFirst: Boolean = false, maxSteps: Int = 5000) {
        var n = 0
        while (true) {
            p.pumpMain()
            if (!io(detectorFirst) && p.main.isEmpty()) return
            if (++n > maxSteps) throw AssertionError("did not settle")
        }
    }

    /** Own writes of answer [a] in AnkiDroid. */
    fun written(a: QueuedAnswer) = fake.revlog.count { !it.external && it.ms == a.tookMs }

    /** How often the game counted card [cardId]: the game steps of its answers plus the detector's answers. */
    fun counted(cardId: Long) = made.filter { it.cardId == cardId }.sumOf { p.game.steps[it.id] ?: 0 } + (p.game.detected[cardId] ?: 0)

    /**
     * Every written own answer counted exactly once, every external answer once, an answer that was not written at most once (its
     * step); never a step twice; never an undo; never a write twice.
     */
    fun check(external: Map<Long, Int> = emptyMap()) {
        val errs = ArrayList<String>(violations)
        for (a in made) {
            val s = p.game.steps[a.id] ?: 0
            if (s > 1) errs += "answer ${a.id} had $s game steps"
            if (written(a) > 1) errs += "answer ${a.id} written ${written(a)} times"
        }
        for (cardId in made.map { it.cardId }.toSet() + external.keys) {
            val mine = made.filter { it.cardId == cardId }
            val w = mine.count { written(it) == 1 }
            val notW = mine.size - w
            val ext = external[cardId] ?: 0
            val stepsOfWritten = mine.filter { written(it) == 1 }.sumOf { p.game.steps[it.id] ?: 0 }
            val det = p.game.detected[cardId] ?: 0
            if (stepsOfWritten + det != w + ext) errs += "card $cardId: $w written own + $ext external answers, but counted ${stepsOfWritten + det} (steps $stepsOfWritten, detector $det)"
            val stepsOfOthers = mine.filter { written(it) == 0 }.sumOf { p.game.steps[it.id] ?: 0 }
            if (stepsOfOthers > notW) errs += "card $cardId: not-written answers counted $stepsOfOthers times"
        }
        if (p.host.queue.size != 0) errs += "${p.host.queue.size} answers still unresolved"
        if (AnswerJournal.decode(journal.text).answers.isNotEmpty()) errs += "the journal still holds answers"
        if (!p.ahead.isEmpty()) errs += "rows still ahead: ${p.ahead.pending.map { it.id to it.reps }}"
        if (errs.isNotEmpty()) throw AssertionError(errs.joinToString("\n") + "\n" + dump())
    }

    fun dump(): String {
        val sb = StringBuilder("--- answers\n")
        for (a in made) sb.append("id ${a.id} card ${a.cardId} reps ${a.before["reps"]} steps ${p.game.steps[a.id]} written ${written(a)} ${outcomes[a.id]?.javaClass?.simpleName}\n")
        sb.append("--- detected ${p.game.detected}\n--- revlog\n")
        for (r in fake.revlog) sb.append("card ${r.cardId} ease ${r.ease} repsAfter ${r.repsAfter} ms ${r.ms} ${if (r.external) "EXTERNAL" else ""}\n")
        sb.append("--- saved map ${p.game.snapshot}\ndeaths $deaths\n--- trace\n").append(trace.takeLast(80).joinToString("\n"))
        return sb.toString()
    }

    init { boot(detectorFirst = false, detector = false) } // last: everything above is set up
}
