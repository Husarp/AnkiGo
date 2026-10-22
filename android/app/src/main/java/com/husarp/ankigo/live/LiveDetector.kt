package com.husarp.ankigo.live

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import com.husarp.ankigo.Anki
import com.husarp.ankigo.CardState
import com.husarp.ankigo.Report
import com.husarp.ankigo.Steps
import com.husarp.ankigo.game.Tuning
import org.json.JSONObject

// TICKET T09 owns this package. Public signatures are fixed; add private helpers freely.

/**
 * Live answer detection for the game (official API only; the revlog back door is diagnostics-only and never used here).
 * Same polling and inference as LiveWatchActivity (proven 16/16 on the device): ~1 s ticks on its own thread,
 * light/full scans, LiveInfer.inferButton, undo = reps going down.
 * Run it while the game panel is visible (onStart/onStop). Events arrive on the main thread.
 */
class LiveDetector(private val ctx: Context, private val onEvent: (Event) -> Unit) {
    sealed class Event {
        /**
         * One answer. button 0 unknown, 1..4; conf = LiveInfer SURE/LIKELY/GUESS. Several answers between polls = several events.
         * [wasNew] (PLAN 155): the card was new before this answer (the ledger's state before it); only the first of several answers can say so.
         */
        data class Answer(val cardId: Long, val button: Int, val conf: String, val catchUp: Boolean, val wasNew: Boolean = false) : Event()
        data class Undo(val cardId: Long, val count: Int) : Event()
        /** Watching state for the panel: ok=false means a problem the user should see (no permission, old AnkiDroid...). */
        data class Status(val ok: Boolean, val text: String) : Event()
    }

    private val ledger = CardLedger()
    fun expectOwn(id: Long, rep: Int, pinned: Boolean = false) = ledger.own.expect(id, rep, SystemClock.elapsedRealtime(), pinned)
    fun cancelOwn(id: Long, rep: Int) = ledger.own.cancel(id, rep)

    /** Own answers waiting to be absorbed, oldest first: they keep their order whatever else is queued on the worker. */
    private val absorbQueue = java.util.concurrent.ConcurrentLinkedQueue<Pair<CardState, () -> Unit>>()

    /**
     * An own answer was saved and verified. On the worker thread (so the card map is never touched from two threads) the
     * detector settles it and remembers [row]; then [done] runs on the main thread, and only then may the snapshot be saved
     * with the card's real state (R10-B23: the game counted the answer at the tap; until here its predicted row stands in,
     * see OwnAhead, so a restart cannot count the answer a second time). The work goes to
     * the FRONT of the worker's queue: it waits only for a poll that is already running, not for ticks or rechecks
     * queued behind it. Several waiting answers are absorbed in the order they were made. Before the session's baseline
     * is read it waits for it (the baseline's catch-up settles own answers itself), and a stop() or start() clearing the
     * queue does not lose it: the answer is already saved in AnkiDroid, so its row must still be settled once. [done]
     * runs with [snapshot] set to the map right after this answer (and not yet any later one).
     */
    fun absorbOwn(row: CardState, done: () -> Unit) {
        val w = worker
        if (w == null) { main.post(done); return }
        absorbQueue.add(row to done)
        w.postAtFrontOfQueue(drain)
    }

    private val drain = Runnable { drainAbsorbs() }

    /**
     * While true no regular poll starts (one already running finishes). Set while the in-app study screen is in front: each
     * poll makes AnkiDroid load today's learning cards one by one (a full poll all of today's rated cards), on the same
     * collection the study screen needs for every answer and card, so polls slowed the next card down. Own answers are
     * still absorbed at once (that reads nothing from AnkiDroid) and [recheck] still runs; an answer made in AnkiDroid
     * meanwhile is found by the polls after the pause (the first is a full read when the last one is 30 s old). Any thread.
     */
    @Volatile var hushed = false

    /** Polls started so far (Diagnostics: how many ran while an answer was on its way). */
    @Volatile var polls = 0
        private set

    /** Worker thread. */
    private fun drainAbsorbs() {
        if (!ready) return // the baseline of this session is not read yet: startSession drains right after it
        while (true) {
            val (row, done) = absorbQueue.poll() ?: return
            ledger.absorb(row, SystemClock.elapsedRealtime())
            prevRated = prevRated + row.id
            refreshSnapshot()
            val snap = snapshotJson // the map as of this answer: the game step saves exactly this
            main.post { deliveredSnapshot = snap; done() }
        }
    }

    // ---- state below is only touched on the worker thread (except the @Volatile ones) ----
    private val main = Handler(Looper.getMainLooper())
    private var thread: HandlerThread? = null
    private var worker: Handler? = null

    @Volatile private var active = false
    /** Bumped by start() and stop(): a loop from an older generation ends itself. */
    @Volatile private var gen = 0
    /** Worker thread: the newest card map. Never read by the game directly, see [deliveredSnapshot]. */
    private var snapshotJson = encode(emptyMap())
    /**
     * Main thread: the card map as of the last thing handed to the game (a poll's events, the baseline's catch-up, an own
     * answer's `done`), or the snapshot this session started from. Each worker->main post carries the map as it was when
     * the post was made and sets this before delivering, so a save never contains an answer whose game step has not run
     * (the worker may already have absorbed or polled further).
     */
    @Volatile private var deliveredSnapshot = encode(emptyMap())
    private var lastStatus: Pair<Boolean, String>? = null
    private var problem = false
    private var prevRated: Set<Long> = emptySet()
    private var prevHot: Set<Long> = emptySet()
    private val stepsCache = HashMap<Long, Steps?>()
    private var tickNo = 0
    private var errors = 0
    /** elapsedRealtime of the last successful full read (baseline or full poll); LivePolicy.isFull uses it. */
    private var lastFullMs = 0L
    /** The baseline of this session is done: only then may a poll compare cards (it needs the baseline to know "before"). */
    @Volatile private var ready = false
    private val recheckGate = RecheckGate()

    /**
     * Starts watching. [snapshot] is the card map saved with the game ([snapshotDay] its game day): when it is from
     * [today], answers made while AnkiGo wasn't watching are emitted first as catchUp answers (cards whose reps grew;
     * cards rated today that the snapshot didn't know count once). With no snapshot for today, every card rated today
     * counts once (catchUp). Safe to call again after stop(). Own answers the game counted ahead of their write must be in
     * [snapshot] already (the caller adds them with withPendingOwn, see OwnAhead): the baseline then does not count them.
     */
    fun start(snapshot: String?, snapshotDay: String?, today: String) {
        Report.init(ctx)
        Anki.load(ctx)
        val restored = if (snapshot != null && snapshotDay == today) {
            try { decode(snapshot) } catch (e: Exception) { Report.fail("LIVE", "saved snapshot unreadable", e); null }
        } else null
        // Until the first delivery, hand back what we were given so a save can't wipe it.
        deliveredSnapshot = if (restored != null) snapshot!! else encode(emptyMap())
        ready = false // an absorb arriving before this session's baseline waits for it, not for the old session's map
        val w = synchronized(this) {
            if (worker == null) {
                thread = HandlerThread("live-detector").also { it.start() }
                worker = Handler(thread!!.looper)
            }
            worker!!
        }
        val g = ++gen
        active = true
        w.removeCallbacksAndMessages(null)
        w.post { startSession(g, restored) }
        if (absorbQueue.isNotEmpty()) w.post(drain) // after the session's baseline: an answer made before the restart is still counted
    }

    /**
     * Reads all of today's rated cards once, now (on the worker thread, through the same compare() path as every poll,
     * so nothing is counted twice). Does nothing while stopped or before the baseline is done. Several requests before
     * it runs are one. Safe from any thread.
     */
    fun recheck() {
        val w = worker ?: return
        val g = gen
        if (!active || !recheckGate.tryQueue(g)) return
        w.post {
            recheckGate.done()
            if (alive(g) && ready) pollOnce(true, SystemClock.elapsedRealtime())
        }
    }

    /** Stops the loop; the thread stays for reuse. A poll already running finishes and still delivers its events. */
    fun stop() {
        active = false
        gen++
        worker?.let {
            it.removeCallbacksAndMessages(null)
            if (absorbQueue.isNotEmpty()) it.postAtFrontOfQueue(drain) // an own answer already saved must still reach the game
        }
    }

    /** The card map (today's rated cards) as JSON that matches the answers handed to the game so far, for saving with it. */
    fun snapshot(): String = deliveredSnapshot

    val running: Boolean get() = active

    // ---------- session ----------
    private fun alive(g: Int) = active && g == gen

    private fun status(ok: Boolean, text: String) {
        if (lastStatus == ok to text) return
        lastStatus = ok to text
        main.post { onEvent(Event.Status(ok, text)) }
    }

    private fun startSession(g: Int, restored: Map<Long, CardState>?) {
        if (!alive(g)) return
        val w = worker!!
        ledger.cards.clear(); stepsCache.clear(); lastStatus = null; problem = false
        prevRated = emptySet(); prevHot = emptySet(); tickNo = 0; errors = 0; ledger.dirty = false; ready = false
        if (Anki.installed(ctx).none { it.pkg == Anki.pkg }) {
            status(false, "AnkiDroid not found"); w.postDelayed({ startSession(g, restored) }, Tuning.LIVE_RETRY_MS); return
        }
        if (!Anki.hasPermission(ctx)) {
            status(false, "Allow AnkiGo to use AnkiDroid (permission missing)"); w.postDelayed({ startSession(g, restored) }, Tuning.LIVE_RETRY_MS); return
        }
        try {
            val t0 = SystemClock.elapsedRealtime()
            // Baseline: everything that can be answered today, so we know each card's state BEFORE its answer.
            val base = Anki.searchCards(ctx, "rated:1 OR is:due OR is:learn", CardState.COLS).mapNotNull { CardState.from(it) }
            // A newer start() redoes this baseline from its own snapshot: nothing of this one may reach the game or settle an own
            // answer (the new baseline and the absorb still waiting do it, once).
            if (active && g != gen) return
            base.forEach { ledger.cards[it.id] = it }
            prevRated = Anki.searchCards(ctx, "rated:1", arrayOf("_id")).mapNotNull { it["_id"]?.toLongOrNull() }.toSet()
            prevHot = base.filter { it.id in prevRated && it.queue in LEARN_QUEUES }.map { it.id }.toSet()
            val missed = baselineAnswers(restored, base, prevRated, ::stepsFor, System.currentTimeMillis() / 1000, ledger.own, SystemClock.elapsedRealtime())
            refreshSnapshot()
            Report.log("LIVE", "watching: baseline ${base.size} cards in ${SystemClock.elapsedRealtime() - t0} ms; rated today=${prevRated.size}; catch-up answers=${missed.size}; AnkiDroid=${Anki.pkg}")
            if (missed.isNotEmpty()) { val snap = snapshotJson; main.post { deliveredSnapshot = snap; missed.forEach(onEvent) } }
            status(true, OK_TEXT)
            lastFullMs = SystemClock.elapsedRealtime() // the baseline read everything
            ready = true
            drainAbsorbs()
            if (alive(g)) w.post { tick(g) }
        } catch (e: SecurityException) {
            status(false, "Allow AnkiGo to use AnkiDroid (permission missing)")
            w.postDelayed({ startSession(g, restored) }, Tuning.LIVE_RETRY_MS)
        } catch (e: Exception) {
            Report.fail("LIVE", "baseline failed (cards URI needs AnkiDroid 2.24+)", e)
            status(false, "AnkiGo needs AnkiDroid 2.24 or newer")
            w.postDelayed({ startSession(g, restored) }, Tuning.LIVE_RETRY_SLOW_MS)
        }
    }

    // ---------- polling ----------
    private fun tick(g: Int) {
        if (!alive(g)) return
        if (hushed) { worker?.postDelayed({ tick(g) }, Tuning.LIVE_POLL_MS); return } // paused: look again in a second, read nothing
        val t0 = SystemClock.elapsedRealtime()
        tickNo++
        pollOnce(LivePolicy.isFull(tickNo, t0, lastFullMs), t0)
        val dt = SystemClock.elapsedRealtime() - t0
        if (alive(g)) worker?.postDelayed({ tick(g) }, LivePolicy.nextDelay(dt))
    }

    /** One poll (light or full) with its event delivery and error handling; used by tick() and recheck(). Worker thread. */
    private fun pollOnce(full: Boolean, nowMs: Long) {
        polls++
        try {
            val out = ArrayList<Event>()
            poll(full, out)
            if (full) lastFullMs = nowMs
            // Snapshot first: the game saves it together with the answers these events produce (no double catch-up).
            if (ledger.dirty) refreshSnapshot()
            if (out.isNotEmpty()) { val snap = snapshotJson; main.post { deliveredSnapshot = snap; out.forEach(onEvent) } }
            errors = 0
            if (problem) { problem = false; status(true, OK_TEXT) }
        } catch (e: Exception) {
            if (errors++ % 60 == 0) {
                Report.fail("LIVE", "poll error #$errors", e)
                problem = true
                status(false, "AnkiDroid poll error: ${e.javaClass.simpleName}")
            }
        }
    }

    /**
     * Light tick (default): rated:1 ids only (provider fast path) + full columns for `rated:1 is:learn`
     * (cards that can come back today) + single reads for cards that entered/left those sets.
     * Full tick (every LIVE_FULL_EVERY-th, or LIVE_RECHECK_MS since the last one, or a recheck()): full columns for all of rated:1.
     */
    private fun poll(full: Boolean, out: MutableList<Event>) {
        val rated = Anki.searchCards(ctx, "rated:1", arrayOf("_id")).mapNotNull { it["_id"]?.toLongOrNull() }.toSet()
        val read = ArrayList<CardState>()
        if (full) {
            read += Anki.searchCards(ctx, "rated:1", CardState.COLS).mapNotNull { CardState.from(it) }
        } else {
            read += Anki.searchCards(ctx, "rated:1 is:learn", CardState.COLS).mapNotNull { CardState.from(it) }
        }
        val have = read.map { it.id }.toSet()
        val extra = ((rated - prevRated) + (prevRated - rated) + (if (full) emptySet() else prevHot)) - have
        for (id in extra) {
            Anki.cardById(ctx, id)?.let { CardState.from(it) }?.let { read += it }
        }
        if (rated != prevRated) ledger.dirty = true
        prevRated = rated
        prevHot = read.filter { it.id in rated && it.queue in LEARN_QUEUES }.map { it.id }.toSet()
        val nowMs = SystemClock.elapsedRealtime(); val nowSec = System.currentTimeMillis() / 1000
        for (n in read) ledger.compare(n, prevRated, ::stepsFor, nowMs, nowSec, out)
    }

    /** Today's rated cards only; encoded off the hot path (only when something changed). */
    private fun refreshSnapshot() {
        snapshotJson = ledger.snapshot(prevRated)
    }

    private fun stepsFor(c: CardState): Steps? {
        val did = if (c.odid != 0L) c.odid else c.did
        if (stepsCache.containsKey(did)) return stepsCache[did]
        val s = try {
            val o = JSONObject(Anki.deckOptions(ctx, did))
            fun delays(k: String) = o.optJSONObject(k)?.optJSONArray("delays")?.let { a -> (0 until a.length()).map { a.getDouble(it) } }
            val learn = delays("new")
            val ints = o.optJSONObject("new")?.optJSONArray("ints")
            if (learn == null) null else Steps(learn, delays("lapse") ?: emptyList(), ints?.optInt(0) ?: 1, ints?.optInt(1) ?: 4)
        } catch (e: Exception) {
            Report.fail("LIVE", "deck options for $did", e); null
        }
        stepsCache[did] = s
        return s
    }

    private companion object {
        /** queue 1 = learning, 3 = day-learning/relearning: cards that may come back today. */
        val LEARN_QUEUES = setOf(1, 3)
        const val OK_TEXT = "Watching AnkiDroid"
    }
}
