package com.husarp.ankigo.live

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.husarp.ankigo.GameLog
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.Report
import com.husarp.ankigo.Widgets
import com.husarp.ankigo.game.Game
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.stats.LiveTimeFile
import com.husarp.ankigo.stats.LiveTimer
import com.husarp.ankigo.ui.LogKind
import java.util.Collections
import java.util.IdentityHashMap
import com.husarp.ankigo.game.Answer as GameAnswer

/**
 * Live answer detection for the whole app process, so it can outlive the battle activity (picture-in-picture, the
 * floating battle window). One [LiveDetector] turns each AnkiDroid answer into game events; whoever shows the battle
 * attaches a [Sink]. Events reach the game (and the save) even when no sink is attached.
 * Everyone who wants detection [hold]s the session; it stops when the last one [release]s it. Main thread only.
 */
object LiveSession {
    interface Sink {
        /** Game events of one answer / undo / catch-up batch, to animate in order. */
        fun onGameEvents(events: List<GameEvent>)

        /** A log line to show AFTER the events queued so far; when [redraw] the panel first redraws from the state. */
        fun onLogLine(kind: LogKind, pill: String, text: String, redraw: Boolean)

        /** Watching state for the strip. canGrant = the AnkiDroid permission is missing (show "Grant"). */
        fun onStatus(ok: Boolean, text: String, canGrant: Boolean)
    }

    private const val WIDGET_EVERY_MS = 20_000L
    private const val PAUSED_TEXT = "Paused. Answers you make meanwhile count when you resume."

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var app: Context

    /** Created by the first start. Once it exists its snapshot means something (it is what the game saves). */
    @Volatile private var detector: LiveDetector? = null
    private val holders: MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap())
    private val sinks = ArrayList<Sink>()
    private val pauseListeners = ArrayList<(Boolean) -> Unit>()
    private var lastStatus: Pair<Boolean, String>? = null

    private val pendingCatchUp = ArrayList<GameAnswer>()

    /** Times live answers for live-time.csv (stats): reset whenever the watch stops, pauses or starts. */
    private val timer = LiveTimer()

    /**
     * Own answers (the card as it is after the answer) whose game step has not run yet: added in [ownAnswer], removed when
     * the step starts. Saves leave them out of the snapshot (the game has not counted them), and a detector that starts
     * meanwhile gets them as already known, so a restart neither loses nor counts them twice.
     */
    private val pendingOwn = ArrayList<com.husarp.ankigo.CardState>()
    private var flushPosted = false
    private var lastWidgets = 0L
    private var widgetsPosted = false

    /** Paused = detector stopped although held; answers made meanwhile are caught up on resume. In memory only. */
    var paused = false
        private set

    /** The detector runs. */
    val running: Boolean get() = detector?.running == true

    /** Who paused the detector's polls ([hush]). */
    private val hushers = HushGate()

    /** Polls the detector started so far (Diagnostics: the study screen's timing line counts those during one answer). */
    val polls: Int get() = detector?.polls ?: 0

    /**
     * [owner] (the in-app study screen, while it is resumed) talks to AnkiDroid for every answer and card: no regular poll
     * may compete with it there meanwhile. Own answers still reach the game at once (absorbing them reads nothing). When
     * the last owner lets go the polls go on where they stopped; the first one after a pause of 30 s or more is a full read
     * (LivePolicy.isFull), so an answer made in AnkiDroid meanwhile still counts, once. Main thread.
     */
    fun hush(ctx: Context, owner: Any, on: Boolean) {
        app = ctx.applicationContext
        if (!(if (on) hushers.hold(owner) else hushers.release(owner))) return
        detector?.hushed = hushers.active // start() hands the state to a detector made later
    }

    // ---------------------------------------------------------------- holding

    /**
     * [owner] wants detection (GameActivity while started, FloatBattleService while running). Starts it when the game
     * is READY and not paused. Idempotent; call again after the game finished loading.
     */
    fun hold(ctx: Context, owner: Any) {
        app = ctx.applicationContext
        holders += owner
        start()
    }

    /**
     * [owner] no longer needs it. When nobody holds it any more: stop the detector, flush pending catch-up answers,
     * copy the snapshot into the state and save it.
     */
    fun release(ctx: Context, owner: Any) {
        app = ctx.applicationContext
        if (!holders.remove(owner) || holders.isNotEmpty()) return
        lastStatus = null
        val d = detector ?: return  // never started: nothing to stop or save
        d.stop()
        timer.stop()
        flushCatchUp()
        saveSnapshot()
        GameRepo.saveNow(app)
    }

    /**
     * The player entered or left AnkiGo: read AnkiDroid once more now, so nothing done a moment ago is missed.
     * Running detector: one full read (coalesced). Held but not running (stopped by an error, a permission problem
     * or a finished retry): start it again. Nobody holds the session: nothing (release() already flushed and saved;
     * answers made later are caught up by the next start).
     */
    fun recheck(ctx: Context) {
        app = ctx.applicationContext
        if (holders.isEmpty()) return
        val d = detector
        if (d != null && d.running) d.recheck() else start() // start() itself ignores paused / game not READY
    }

    /** Stop + start again (after the AnkiDroid permission was granted). */
    fun restart(ctx: Context) {
        app = ctx.applicationContext
        detector?.stop()
        timer.stop()
        start()
    }

    private fun start() {
        if (paused || holders.isEmpty() || GameRepo.status != GameRepo.Status.READY) return
        val st = GameRepo.state ?: return
        val g = GameRepo.game ?: return
        val d = detector ?: LiveDetector(app) { onLive(it) }.also { detector = it }
        d.hushed = hushers.active
        if (d.running) return
        timer.start(SystemClock.elapsedRealtime())
        val today = g.dayKey()
        if (pendingOwn.isEmpty()) d.start(st.liveSnapshot, st.liveDay, today)
        else d.start(withPendingOwn(st.liveSnapshot.takeIf { st.liveDay == today }, pendingOwn), today, today)
    }

    fun expectOwn(row: com.husarp.ankigo.CardState) { detector?.expectOwn(row.id, row.reps + 1) }
    fun cancelOwn(row: com.husarp.ankigo.CardState) { detector?.cancelOwn(row.id, row.reps + 1) }

    /**
     * Verified own answers use the exact button and absorb their state before saving the game: the game step runs once
     * the detector has settled the answer (so a poll cannot count it again), and [done] gets its events. The caller does
     * not have to wait for [done] to go on (the study screen loads the next card at once). [onTiming] gets the
     * milliseconds this call waited for the detector and the game step took, just before [done].
     */
    fun ownAnswer(ctx: Context, row: com.husarp.ankigo.CardState, button: Int, onTiming: (absorbMs: Long, gameMs: Long) -> Unit = { _, _ -> }, done: (List<GameEvent>) -> Unit) {
        app = ctx.applicationContext
        val asked = SystemClock.elapsedRealtime()
        val finish = {
            val absorbed = SystemClock.elapsedRealtime()
            flushCatchUp()
            timer.touch(SystemClock.elapsedRealtime()) // reviews.csv times own answers; the next live one starts from here
            pendingOwn.remove(row) // from here on the snapshot saved below contains this answer, and the step counts it
            GameLog.answer("study", row.id, button)
            val events = act { g ->
                saveSnapshotInto(g)
                if (detector == null) {
                    val map = runCatching { decode(g.state.liveSnapshot ?: "[]") }.getOrDefault(emptyMap())
                    g.state.liveSnapshot = encode(map + (row.id to row)); g.state.liveDay = g.dayKey()
                }
                g.tick() + g.onAnswer(GameAnswer(row.id, button, System.currentTimeMillis(), false))
            }
            deliver(events)
            onTiming(absorbed - asked, SystemClock.elapsedRealtime() - absorbed)
            done(events)
        }
        pendingOwn += row
        detector?.absorbOwn(row, finish) ?: finish()
    }

    // ---------------------------------------------------------------- pause

    /**
     * Pause stops watching (answers made meanwhile are caught up on resume, through the detector's normal start);
     * resume starts it again if somebody still holds the session.
     */
    fun setPaused(ctx: Context, p: Boolean) {
        app = ctx.applicationContext
        if (paused == p) return
        paused = p
        if (p) {
            detector?.stop()
            timer.stop()
            flushCatchUp()
            saveSnapshot()
            GameRepo.scheduleSave(app)
            sendStatus(false, PAUSED_TEXT)
        } else {
            start() // the detector's own status follows
        }
        for (l in pauseListeners.toList()) l(p)
    }

    fun addPauseListener(l: (Boolean) -> Unit) { if (l !in pauseListeners) pauseListeners += l }
    fun removePauseListener(l: (Boolean) -> Unit) { pauseListeners -= l }

    // ---------------------------------------------------------------- sinks

    /** The new sink immediately gets the last status. */
    fun addSink(s: Sink) {
        if (s !in sinks) sinks += s
        val shown = if (paused) false to PAUSED_TEXT else lastStatus
        if (shown != null) s.onStatus(shown.first, shown.second, canGrant(shown.first, shown.second))
    }

    fun removeSink(s: Sink) { sinks -= s }

    private fun canGrant(ok: Boolean, text: String) = !ok && text.contains("permission", ignoreCase = true)

    private fun sendStatus(ok: Boolean, text: String) {
        for (s in sinks.toList()) s.onStatus(ok, text, canGrant(ok, text))
    }

    private fun sendLog(kind: LogKind, pill: String, text: String, redraw: Boolean) {
        for (s in sinks.toList()) s.onLogLine(kind, pill, text, redraw)
    }

    private fun deliver(events: List<GameEvent>) {
        if (events.isEmpty()) return
        for (s in sinks.toList()) s.onGameEvents(events)
        refreshWidgetsFor(app, events)
    }

    // ---------------------------------------------------------------- snapshot

    /** Copies the detector's card map into the state, so the save and the game agree on what was counted. */
    fun saveSnapshot() {
        val d = detector ?: return
        val st = GameRepo.state ?: return
        if (pendingCatchUp.isNotEmpty()) return // not counted yet: flushCatchUp saves the snapshot together with them
        st.liveSnapshot = withoutPendingOwn(d.snapshot(), pendingOwn)
        st.liveDay = GameRepo.game?.dayKey()
    }

    private fun saveSnapshotInto(g: Game) {
        val d = detector ?: return
        g.state.liveSnapshot = withoutPendingOwn(d.snapshot(), pendingOwn)
        g.state.liveDay = g.dayKey()
    }

    // ---------------------------------------------------------------- detector events

    private fun onLive(e: LiveDetector.Event) {
        if (GameRepo.game == null) return
        when (e) {
            is LiveDetector.Event.Status -> {
                lastStatus = e.ok to e.text
                if (!paused) sendStatus(e.ok, e.text) // a poll that was already running may still report while paused
            }
            is LiveDetector.Event.Answer -> onAnswer(e)
            is LiveDetector.Event.Undo -> {
                val done = try {
                    GameRepo.run(app) { g -> saveSnapshotInto(g); g.onUndo(e.cardId, e.count) }
                    true
                } catch (t: Throwable) {
                    Report.fail("LIVE", "undo", t)
                    false
                }
                if (done) sendLog(LogKind.NEUTRAL, "UNDO", "Undo in AnkiDroid: the redo won't count twice", redraw = false)
            }
        }
    }

    private fun onAnswer(e: LiveDetector.Event.Answer) {
        GameLog.answer(if (e.catchUp) "catch-up" else "live", e.cardId, e.button)
        val a = GameAnswer(e.cardId, e.button, System.currentTimeMillis(), e.catchUp)
        recordLiveTime(e.catchUp)
        if (e.catchUp) {
            // Answers made while AnkiGo wasn't watching arrive in one burst: no animation, one summary line.
            pendingCatchUp += a
            if (!flushPosted) { flushPosted = true; handler.post { flushPosted = false; flushCatchUp() } }
            return
        }
        flushCatchUp()
        deliver(act { g -> saveSnapshotInto(g); g.tick() + g.onAnswer(a) })
        // The detector refreshes its map a moment after delivering; save the fresh one too.
        handler.removeCallbacks(snapshotLater)
        handler.postDelayed(snapshotLater, 1500)
    }

    /** Live answers (not catch-up, not own) add their time to live-time.csv on a background thread; a failure never reaches the game. */
    private fun recordLiveTime(catchUp: Boolean) {
        try {
            val ms = timer.onAnswer(catchUp, SystemClock.elapsedRealtime()) ?: return
            LiveTimeFile.appendAsync(app.filesDir, System.currentTimeMillis(), ms) { Report.fail("LIVE", "writing the live time", it) }
        } catch (t: Throwable) {
            Report.fail("LIVE", "timing a live answer", t)
        }
    }

    private val snapshotLater = Runnable {
        saveSnapshot()
        GameRepo.scheduleSave(app)
    }

    private fun flushCatchUp() {
        if (pendingCatchUp.isEmpty()) return
        val batch = pendingCatchUp.toList()
        pendingCatchUp.clear()
        val events = act { g -> saveSnapshotInto(g); g.tick() + batch.flatMap { g.onAnswer(it) } }
        var won = 0; var caught = 0; var cash = 0
        for (ev in events) when (ev) {
            is GameEvent.Defeated -> won++
            is GameEvent.Caught -> caught++
            is GameEvent.Cash -> cash += ev.amount
            else -> {}
        }
        val n = batch.size
        val text = "Caught up $n answer${if (n == 1) "" else "s"} made while AnkiGo was closed: $won won, $caught caught, +$cash ₽"
        sendLog(LogKind.SYS, "CAUGHT UP", text, redraw = true)
        refreshWidgetsFor(app, events)
    }

    /** Runs a game action; a failure is logged (it must never crash a study session) and gives no events. */
    private fun act(f: (Game) -> List<GameEvent>): List<GameEvent> = try {
        GameRepo.act(app, f)
    } catch (t: Throwable) {
        Report.fail("LIVE", "game action", t)
        emptyList()
    }

    // ---------------------------------------------------------------- widgets

    /** Refreshes the widgets when [events] change what they show (throttled, see [refreshWidgetsSoon]). */
    fun refreshWidgetsFor(ctx: Context, events: List<GameEvent>) {
        val changes = events.any {
            it is GameEvent.Caught || it is GameEvent.Defeated || it is GameEvent.MainHealed || it is GameEvent.Evolved || it is GameEvent.DayChanged
        }
        if (changes) refreshWidgetsSoon(ctx.applicationContext)
    }

    /** Widgets refresh at most once per 20 s; a refresh that is too early is postponed, not dropped. */
    private fun refreshWidgetsSoon(ctx: Context) {
        val wait = WIDGET_EVERY_MS - (SystemClock.elapsedRealtime() - lastWidgets)
        if (wait <= 0) {
            lastWidgets = SystemClock.elapsedRealtime()
            Widgets.refreshAll(ctx)
        } else if (!widgetsPosted) {
            widgetsPosted = true
            handler.postDelayed({
                widgetsPosted = false
                lastWidgets = SystemClock.elapsedRealtime()
                Widgets.refreshAll(ctx)
            }, wait)
        }
    }
}
