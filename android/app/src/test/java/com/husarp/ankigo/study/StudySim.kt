package com.husarp.ankigo.study

import java.util.ArrayDeque
import kotlin.random.Random

/** What the sim calls an answer: every tap has its own time, and sequence numbers restart in a new process. */
val QueuedAnswer.id: Long get() = tookMs

/**
 * The study screen, the io thread and a pretend AnkiDroid wired together the way ticket 1b wires them, deterministic and single
 * threaded: ONE io FIFO runs every provider call (answer writes, loads, prepares) in order; the "main thread" is a second queue of
 * posts. User actions can happen between io tasks, inside a slow write ([FakeAnki.duringWrite]) and inside a back-off wait.
 * [die] ends the pretend process (engine, reducer, io FIFO and main queue are dropped; the journal store and the pretend AnkiDroid
 * stay) and boots a new one that restores the journal.
 *
 * The ground truth is kept apart from the code under test and [check] tests the invariants against it.
 */
class StudySim(val fake: FakeAnki, val deckId: Long, val store: MemStore = MemStore(), val maxAhead: Int = 0, val tryNotTop: Boolean = false) {
    class Shown(val key: StudyLoop.Key, val predicted: Boolean)

    // ---- ground truth (survives a death)
    val made = ArrayList<QueuedAnswer>() // every answer the reducer accepted, in tap order
    val outcomes = LinkedHashMap<Long, ArrayList<AnswerOutcome>>() // keyed by [id]: sequence numbers restart in a new process
    val gameSteps = HashMap<Long, Int>()
    val detected = HashMap<Long, Int>() // counted by the new process's detector instead of a game step
    val expects = HashMap<Long, Int>()
    val cancels = HashMap<Long, Int>()
    val violations = ArrayList<String>()
    val timings = ArrayList<Long>()
    val restoreProblems = ArrayList<String>()
    var deaths = 0
    var journalFailed = 0
    var prepares = 0 // R18 RV-9: Prepare tasks the io thread ran
    var learnReads = 0 // R18 RV-9: the deck's learning-card reads (AnkiDroid `searchCards`) the io thread made for them

    // ---- the pretend screen
    var displayed: Shown? = null
    var side = "question"
    var finished = false
    var errors = 0
    var refused = 0
    var heldShown = 0
    val shows = ArrayList<String>() // "P:name" predicted, "C:name" loaded, "K:name" confirmed, for assertions
    val timeline = ArrayList<String>() // "end:<id>" when a write is resolved, "confirm:<name>" when a card becomes the real one
    val log = ArrayList<Pair<Long, String>>() // R18-A: (virtual ms, what the screen did) for every reducer command: "show:P:c101/0", "show:C:..", "confirm:..", "enqueue:<ease>"

    var policy: ((StudySim, Boolean) -> Unit)? = null // (sim, idle)
    var meddle: ((StudySim) -> Unit)? = null // events outside the app, between io tasks only

    val waiter = SimWaiter()

    inner class Proc {
        val io = ArrayDeque<() -> Unit>()
        val main = ArrayDeque<() -> Unit>()
        val queue = AnswerQueue(fake, store, HookImpl(this), waiter, { fake.clock.ms }, tryNotTop = { tryNotTop })
        val nc = NextCard<String>(deckId, queue::nextSeq, { fake.clock.ms }, maxAhead = maxAhead)
        /** R18 RV-9: like the screen, the Prepares and the held tap's next card of one load share ONE learning read ([LearnCache]). */
        val learn = LearnCache { learnReads++; PredictRule.learnRead(emptyList(), deckId, fake.clock.ms) }
    }

    lateinit var p: Proc


    private inner class HookImpl(val proc: Proc) : AnswerHooks {
        private fun idOf(before: Map<String, String?>) = proc.queue.pending().firstOrNull { it.before === before }?.id ?: -1L
        override fun expect(before: Map<String, String?>) { expects.merge(idOf(before), 1, Int::plus) }
        override fun cancel(before: Map<String, String?>) { cancels.merge(idOf(before), 1, Int::plus) }
        override fun answerTimed(answer: QueuedAnswer, attempt: Int, ms: Long) { timings += ms }
        override fun journalFailed(reason: String) { this@StudySim.journalFailed++ }
        override fun outcome(o: AnswerOutcome) {
            outcomes.getOrPut(o.answer.id) { ArrayList() } += o
            if (o.isTerminal) timeline += "end:${o.answer.id}"
            if (o is AnswerOutcome.Saved) {
                val reps = o.answer.before["reps"]!!.toInt()
                if (o.after["reps"]?.toIntOrNull() != reps + 1) violations += "Saved without the card's new state for seq ${o.answer.seq}"
                proc.main += { gameSteps.merge(o.answer.id, 1, Int::plus) } // a Saved outcome of this process (R10-B23: the game counts at the tap, see InstantBattleSimTest; here it checks Saved is reported once per own write)
            }
        }
    }

    inner class SimWaiter : Waiter {
        var woken = false
        val waits = ArrayList<Long>()
        var script: ((Long) -> Boolean)? = null

        override fun await(ms: Long): Boolean {
            waits += ms
            if (woken) { woken = false; return true }
            fake.clock.ms += maxOf(ms, 0)
            pumpMain() // the main thread runs while the io thread waits
            policy?.invoke(this@StudySim, false)
            pumpMain()
            script?.let { s -> val r = s(ms); if (r) woken = false; return r }
            if (ms < 0) { fake.plans.clear(); fake.chaos = null; return true } // parked, nobody scripted a wake: heal and retry
            return false
        }

        override fun wake() { woken = true }
        override fun clear() { woken = false }
    }

    // ---- process

    fun boot() {
        p = Proc()
        waiter.woken = false
        val r = p.queue.restore()
        r.problem?.let { restoreProblems += it }
        // the new process's detector reads AnkiDroid once: every answer already there that no game step counted is counted now
        for (m in made) if (applied(m) >= 1 && (gameSteps[m.id] ?: 0) == 0) detected[m.id] = 1
        if (r.count > 0) p.io += { p.queue.drain() }
        cmds(p.nc.reload())
    }

    fun die() {
        deaths++
        displayed = null; side = "question"; finished = false
        boot()
    }

    // ---- the reducer's commands

    fun cmds(list: List<Cmd<String>>) {
        for (c in list) log += fake.clock.ms to when (c) {
            is Cmd.Show -> "show:${if (c.predicted) "P" else "C"}:${c.card.view}"; is Cmd.Confirm -> "confirm:${c.card.view}"; is Cmd.Enqueue -> "enqueue:${c.answer.ease}:c${c.answer.nid}/${c.answer.ord}"
            is Cmd.ShowHeld -> "held:${c.ease}"; Cmd.ShowWaiting -> "waiting"; Cmd.ShowFinished -> "finished"; is Cmd.ShowError -> "error"; is Cmd.Refuse -> "refuse"
            is Cmd.Load -> "load:${c.token}"; is Cmd.Prepare -> "prepare:${c.key.nid}"
        }
        for (c in list) when (c) {
            is Cmd.Load -> { val proc = p; proc.io += { load(proc, c.token, c.predicted) } }
            is Cmd.Prepare -> { val proc = p; proc.io += { prepare(proc, c) } }
            is Cmd.Enqueue -> {
                val a = c.answer
                if (maxAhead == 0 && p.queue.size != 0) violations += "answer ${a.seq} made while another answer was unresolved" // R18-A: with maxAhead > 0 answers are made ahead of the write on purpose
                val d = displayed
                if (d == null || (d.predicted && maxAhead == 0) || d.key != a.key) violations += "answer ${a.seq} acted on ${if (d == null) "no card" else if (d.predicted) "a predicted card" else "another card"}"
                p.queue.enqueue(a)
                made += a
                val proc = p
                proc.io += { proc.queue.drain() }
            }
            is Cmd.Show -> { displayed = Shown(c.card.key, c.predicted); side = "question"; shows += (if (c.predicted) "P:" else "C:") + c.card.view; if (!c.predicted) timeline += "confirm:${c.card.view}" }
            is Cmd.Confirm -> { displayed = Shown(c.card.key, false); shows += "K:" + c.card.view; timeline += "confirm:${c.card.view}" }
            Cmd.ShowWaiting -> displayed = null
            is Cmd.ShowHeld -> heldShown++
            Cmd.ShowFinished -> { finished = true; displayed = null }
            is Cmd.ShowError -> { errors++; displayed = null }
            is Cmd.Refuse -> refused++
        }
    }

    private fun post(proc: Proc, f: () -> Unit) { proc.main += f }

    private fun prepared(key: StudyLoop.Key): Prepared<String> {
        val st = fake.stats(key) ?: throw IllegalStateException("card unreadable")
        return Prepared(key, st, "c${key.nid}/${key.ord}")
    }

    /** The io side of `Load(token, predicted)`. */
    private fun load(proc: Proc, token: Int, predicted: StudyLoop.Key?) {
        try {
            proc.queue.ensureSelected(deckId) { fake.select(deckId) }
            val q = fake.top(deckId, maxOf(3, maxAhead + 2))
            when (val step = LoadPlan.decide(q, predicted)) {
                LoadPlan.Step.Finished -> post(proc) { cmds(proc.nc.finished(token)) }
                is LoadPlan.Step.Confirm -> {
                    // while a tap is held the card after it is prepared right here, so the held tap's own next card shows at once
                    val heldNow = (proc.nc.slot as? NextCard.Slot.Predicted<*>)?.held != null
                    val next = if (heldNow && q.size > 1) try { prepared(q[1]).copy(learning = proc.learn.get(token)) } catch (e: Exception) { null } else null
                    post(proc) { cmds(proc.nc.confirmed(token, step.key, q, next) { "$it*" }) }
                }
                is LoadPlan.Step.Fetch -> { val pr = prepared(step.key); post(proc) { cmds(if (predicted != null) proc.nc.swapped(token, pr, q) else proc.nc.loaded(token, pr, q)) } }
            }
        } catch (e: Exception) {
            post(proc) { cmds(proc.nc.failed(token, e.message ?: "error")) }
        }
    }

    private fun prepare(proc: Proc, c: Cmd.Prepare) {
        try {
            val pr = prepared(c.key).copy(learning = proc.learn.get(c.forToken))
            prepares++
            post(proc) { proc.nc.prepared(c.forKey, c.forToken, pr) }
        } catch (e: Exception) { /* no prediction */ }
    }

    // ---- running

    fun pumpMain() { while (true) { (p.main.poll() ?: return)() } }

    /** Runs one io task; false when the FIFO is empty. */
    fun runOneIo(): Boolean {
        val t = p.io.poll() ?: return false
        try { t() } catch (d: Die) { die() }
        return true
    }

    /** Runs io and main to quiet, no policy. */
    fun pump(maxSteps: Int = 5000) {
        var n = 0
        while (true) {
            pumpMain()
            if (!runOneIo() && p.main.isEmpty()) return
            if (++n > maxSteps) throw AssertionError("did not settle")
        }
    }

    /** Runs with [policy] (the user) and [meddle] until the deck is finished or the user stops; fails if it never settles. */
    fun run(maxSteps: Int = 20000) {
        var n = 0
        while (n++ < maxSteps) {
            pumpMain()
            meddle?.invoke(this)
            policy?.invoke(this, false)
            pumpMain()
            if (runOneIo()) continue
            if (p.main.isNotEmpty()) continue
            policy?.invoke(this, true)
            if (p.io.isEmpty() && p.main.isEmpty()) return
        }
        throw AssertionError("did not settle in $maxSteps steps")
    }

    private var taps = 0L

    /** Every tap gets its own time, which the pretend AnkiDroid keeps in its revlog: that is how a write is traced to the answer it came from. */
    fun tap(ease: Int) { cmds(p.nc.tapAnswer(ease, 1000 + taps++)) }
    fun flip() { if (displayed != null) side = "answer" }
    fun reload() { cmds(p.nc.reload()) }

    // ---- the checks

    fun applied(m: QueuedAnswer): Int = fake.revlog.count { !it.external && it.ms == m.tookMs }

    fun terminal(m: QueuedAnswer): AnswerOutcome? = outcomes[m.id].orEmpty().lastOrNull { it.isTerminal }

    /** Fails with every broken invariant (a)-(e) listed. [allowNotTopWrites] for scenarios that race the queue on purpose. */
    fun check(allowNotTopWrites: Boolean = false) {
        val errs = ArrayList<String>(violations)
        val pendingIds = p.queue.pending().map { it.id }.toSet()
        val journal = AnswerJournal.decode(store.text).answers.map { it.id }.toSet()
        if (journalFailed == 0 && journal != pendingIds) errs += "journal $journal is not the queue $pendingIds"
        // (a) + (b)
        for (m in made) {
            val terms = outcomes[m.id].orEmpty().filter { it.isTerminal }
            val n = applied(m)
            if (terms.size > 1) errs += "answer ${m.seq} ended ${terms.size} times: $terms"
            val t = terms.firstOrNull()
            when {
                t == null -> {
                    if (m.id !in pendingIds && n == 0) errs += "answer ${m.seq} vanished: no outcome, not queued, not written"
                    if (n > 1) errs += "answer ${m.seq} written $n times"
                }
                t is AnswerOutcome.Saved || t is AnswerOutcome.AlreadySaved -> if (n != 1) errs += "answer ${m.seq} reported saved but written $n times"
                t is AnswerOutcome.NotWritten -> when (t.reason) {
                    AnswerOutcome.Reason.NOT_TOP, AnswerOutcome.Reason.CHANGED -> if (n != 0) errs += "answer ${m.seq} reported ${t.reason} but written $n times"
                    else -> if (n > 1) errs += "answer ${m.seq} (${t.reason}) written $n times"
                }
                else -> {}
            }
            // (e) the game counts a written answer exactly once: a game step in this process, or the new process's detector
            val counted = (gameSteps[m.id] ?: 0) + (detected[m.id] ?: 0)
            val unclear = t is AnswerOutcome.NotWritten && (t.reason == AnswerOutcome.Reason.UNSURE || t.reason == AnswerOutcome.Reason.DISCARDED)
            // (an UNSURE / DISCARDED answer may or may not be in AnkiDroid; the live detector counts what it finds after cancelOwn)
            if (n == 1 && counted != 1 && !unclear) errs += "answer ${m.seq} written but counted $counted times (game ${gameSteps[m.id]}, detector ${detected[m.id]})"
            if (n == 0 && counted != 0) errs += "answer ${m.seq} not written but counted $counted times"
            if ((gameSteps[m.id] ?: 0) > 1) errs += "answer ${m.seq} had ${gameSteps[m.id]} game steps"
            if (t is AnswerOutcome.AlreadySaved && (gameSteps[m.id] ?: 0) != 0) errs += "game step for ALREADY_SAVED ${m.seq}"
            val cancelled = cancels[m.id] ?: 0
            if (t is AnswerOutcome.NotWritten && cancelled != 1) errs += "answer ${m.seq} ended not written but cancelOwn ran $cancelled times"
            if ((t is AnswerOutcome.Saved || t is AnswerOutcome.AlreadySaved) && cancelled != 0) errs += "answer ${m.seq} saved but cancelOwn ran"
            if (t is AnswerOutcome.Saved && (expects[m.id] ?: 0) < 1) errs += "answer ${m.seq} saved without expectOwn"
        }
        // every own write belongs to an answer, on the card and with the button it was made with, once, in tap order
        val own = fake.revlog.filter { !it.external }
        val byTook = made.associateBy { it.tookMs }
        val order = ArrayList<Int>()
        for (r in own) {
            val m = byTook[r.ms]
            if (m == null) errs += "AnkiDroid holds a write (card ${r.cardId}) that no answer asked for"
            else {
                if (m.cardId != r.cardId || m.ease != r.ease) errs += "answer ${m.seq} (card ${m.cardId}, ease ${m.ease}) was written to card ${r.cardId} with ease ${r.ease}"
                order += made.indexOf(m)
            }
        }
        if (order != order.sorted()) errs += "writes are out of tap order: $order"
        if (order.toSet().size != order.size) errs += "a write happened twice: $order"
        // (c)
        if (!allowNotTopWrites && fake.notTopWrites != 0) errs += "${fake.notTopWrites} write(s) to a card that was not on top"
        if (errs.isNotEmpty()) throw AssertionError(errs.joinToString("\n") + "\n" + dump())
    }

    /** Everything that happened to the answers, for a failing check. */
    fun dump(): String {
        val sb = StringBuilder("--- answers\n")
        for (m in made) sb.append("id ${m.id} seq ${m.seq} card ${m.cardId} ease ${m.ease} reps ${m.before["reps"]}: ").append(outcomes[m.id].orEmpty().joinToString(" > ") { it.javaClass.simpleName + (if (it is AnswerOutcome.NotWritten) "(${it.reason})" else "") }).append('\n')
        sb.append("--- revlog\n")
        for (r in fake.revlog) sb.append("card ${r.cardId} ease ${r.ease} repsAfter ${r.repsAfter} ms ${r.ms} ${if (r.external) "EXTERNAL" else ""}\n")
        sb.append("--- timeline ").append(timeline.takeLast(40)).append("\ndeaths $deaths")
        return sb.toString()
    }

    companion object {
        /** A deck of [notes] notes with [ords] cards each, in this order; returns the fake. */
        fun deck(deckId: Long, notes: Int, ords: Int = 1, fake: FakeAnki = FakeAnki()): FakeAnki {
            for (n in 1..notes) for (o in 0 until ords) fake.add(deckId, 100L + n, o)
            return fake
        }

        fun randomUser(rng: Random, easeFor: (Int) -> Int = { n -> if (n > 80) 3 else listOf(1, 2, 3, 3, 3, 4)[rng.nextInt(6)] }): (StudySim, Boolean) -> Unit = { sim, idle ->
            if (sim.p.nc.slot is NextCard.Slot.Error) {
                if (idle || rng.nextInt(3) == 0) sim.reload()
            } else if (sim.displayed != null && (idle || rng.nextInt(100) < 35)) {
                if (rng.nextBoolean()) sim.flip()
                sim.tap(easeFor(sim.made.size))
            }
        }
    }
}
