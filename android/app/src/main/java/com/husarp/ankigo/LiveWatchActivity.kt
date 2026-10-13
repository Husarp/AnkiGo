package com.husarp.ankigo

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Live watch: Adam studies IN AnkiDroid (split screen), this screen detects each answer by polling the
 * OFFICIAL cards URI (~1 s) and infers the button. Ground truth comes from the UNOFFICIAL revlog probe.
 * All logs use tag "LIVE".
 */
class LiveWatchActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("live-poll").apply { start() }
    private val worker = Handler(thread.looper)

    private lateinit var big: TextView
    private lateinit var sub: TextView
    private lateinit var statsView: TextView
    private lateinit var listView: TextView
    private lateinit var status: TextView
    private lateinit var pauseBtn: Button
    private lateinit var scanBtn: Button

    // ---- state below is only touched on the worker thread ----
    @Volatile private var running = false
    @Volatile private var paused = false
    @Volatile private var fullEveryTick = false
    private val cards = HashMap<Long, CardState>()
    private var prevRated: Set<Long> = emptySet()
    private var prevHot: Set<Long> = emptySet()
    private val stepsCache = HashMap<Long, Steps?>()
    private val noteCache = HashMap<Long, String>()
    private val events = ArrayList<Ev>()
    private val lines = ArrayList<String>() // newest first, for the list view
    private val sessions = ArrayList<LongArray>() // [start, end] ms; end = 0 while open
    private var tickNo = 0
    private val lightMs = Stat(); private val fullMs = Stat()
    private var truthError: String? = null
    private var errors = 0
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    data class Rev(val id: Long, val ease: Int, val ivl: Long, val lastIvl: Long, val factor: Long, val type: Int)
    class Ev(
        val no: Int, val at: Long, val cid: Long, val label: String, val prevType: Int, val guess: Guess, val repsDelta: Int,
        val truth: Rev?, val delayMs: Long?, val delayExact: Boolean, var undone: Boolean = false,
    ) {
        val ok get() = truth != null && truth.ease == guess.button
    }
    class Stat { var n = 0; var sum = 0L; var max = 0L
        fun add(v: Long) { n++; sum += v; if (v > max) max = v }
        override fun toString() = if (n == 0) "-" else "avg ${sum / n} / max $max ms (n=$n)"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Report.init(this)
        Anki.load(this)
        title = "Live watch"
        val dp = resources.displayMetrics.density
        val pad = (6 * dp).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun btn(label: String, f: () -> Unit) = Button(this).apply {
            text = label; textSize = 11f; isAllCaps = false; minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
            setPadding(pad, pad / 2, pad, pad / 2); setOnClickListener { f() }
        }.also { bar.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        pauseBtn = btn("Pause") { togglePause() }
        scanBtn = btn("Scan: light") { fullEveryTick = !fullEveryTick; scanBtn.text = if (fullEveryTick) "Scan: full" else "Scan: light"; Report.log("LIVE", "scan mode -> ${if (fullEveryTick) "full every tick" else "light (full every 10th tick)"}") }
        btn("Summary") { worker.post { val s = summary(); Report.log("LIVE", "SUMMARY $s"); main.post { statsView.text = s } } }
        btn("Share") { share() }
        root.addView(bar)

        big = TextView(this).apply { textSize = 34f; typeface = Typeface.DEFAULT_BOLD; text = "waiting…"; setTextColor(Color.DKGRAY) }
        sub = TextView(this).apply { textSize = 13f; text = "Study in AnkiDroid; answers show up here." }
        statsView = TextView(this).apply { textSize = 11f; typeface = Typeface.MONOSPACE; setBackgroundColor(Color.rgb(235, 242, 255)); setPadding(pad, pad / 2, pad, pad / 2) }
        status = TextView(this).apply { textSize = 10f; setTextColor(Color.GRAY) }
        root.addView(big); root.addView(sub); root.addView(statsView); root.addView(status)
        listView = TextView(this).apply { textSize = 12f; typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }
        root.addView(ScrollView(this).apply { addView(listView) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    // Poll while VISIBLE (onStart/onStop), not only while focused: in split screen on Android ≤ 9
    // the half you are not touching is paused but still visible.
    // Every return to the app also checks for updates (at most every 5 min; the banner is on the main screen).
    override fun onResume() { super.onResume(); com.husarp.ankigo.update.Updates.onResume(this) }
    override fun onPause() { com.husarp.ankigo.update.Updates.onPause(this); super.onPause() }

    override fun onStart() {
        super.onStart()
        running = true
        worker.post { if (!paused) startSession() }
    }

    override fun onStop() {
        running = false
        worker.removeCallbacks(tick)
        worker.post { endSession("screen hidden") }
        super.onStop()
    }

    override fun onDestroy() {
        thread.quitSafely()
        super.onDestroy()
    }

    private fun togglePause() {
        paused = !paused
        pauseBtn.text = if (paused) "Resume" else "Pause"
        worker.removeCallbacks(tick)
        worker.post { if (paused) endSession("paused") else if (running) startSession() }
    }

    // ---------- session ----------
    private fun startSession() {
        if (sessions.lastOrNull()?.get(1) == 0L) return
        worker.removeCallbacks(tick)
        if (!Anki.hasPermission(this)) { show("No permission", "Grant the AnkiDroid permission on the main screen first."); return }
        try {
            val t0 = SystemClock.elapsedRealtime()
            // Baseline: everything that can be answered today, so we know each card's state BEFORE its answer.
            val base = Anki.searchCards(this, "rated:1 OR is:due OR is:learn", CardState.COLS).mapNotNull { CardState.from(it) }
            base.forEach { cards[it.id] = it }
            prevRated = Anki.searchCards(this, "rated:1", arrayOf("_id")).mapNotNull { it["_id"]?.toLongOrNull() }.toSet()
            prevHot = base.filter { it.id in prevRated && it.queue in LEARN_QUEUES }.map { it.id }.toSet()
            val ms = SystemClock.elapsedRealtime() - t0
            truthError = try { revlogCount(0, Long.MAX_VALUE); null } catch (e: Exception) { "${e.javaClass.simpleName}: ${e.message}" }
            sessions.add(longArrayOf(System.currentTimeMillis(), 0))
            Report.log("LIVE", "watch started (session ${sessions.size}): baseline ${base.size} cards ('rated:1 OR is:due OR is:learn') in $ms ms; rated today=${prevRated.size}; AnkiDroid=${Anki.pkg}; ground truth ${truthError?.let { "UNAVAILABLE ($it)" } ?: "via revlog probe OK"}")
            main.post { status.text = "watching… baseline ${base.size} cards in $ms ms" }
            refreshStats()
            worker.post(tick)
        } catch (e: Exception) {
            Report.fail("LIVE", "baseline failed (cards URI needs AnkiDroid 2.24+)", e)
            show("Can't watch", "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun endSession(why: String) {
        val s = sessions.lastOrNull() ?: return
        if (s[1] != 0L) return
        s[1] = System.currentTimeMillis()
        Report.log("LIVE", "watch stopped ($why). SUMMARY ${summary()}")
    }

    // ---------- polling ----------
    private val tick: Runnable = object : Runnable {
        override fun run() {
            if (!running || paused) return
            val t0 = SystemClock.elapsedRealtime()
            tickNo++
            val full = fullEveryTick || tickNo % 10 == 0
            try {
                poll(full)
            } catch (e: Exception) {
                if (errors++ % 60 == 0) Report.fail("LIVE", "poll error #$errors", e)
                main.post { status.text = "poll error: ${e.javaClass.simpleName}: ${e.message}" }
            }
            val dt = SystemClock.elapsedRealtime() - t0
            (if (full) fullMs else lightMs).add(dt)
            if (tickNo % 5 == 0) main.post { status.text = "tick $tickNo · ${cards.size} cards known · poll light ${lightMs} · full ${fullMs}" }
            if (running && !paused) worker.postDelayed(this, (1000 - dt).coerceAtLeast(100))
        }
    }

    /**
     * Light tick (default): rated:1 ids only (provider fast path) + full columns for `rated:1 is:learn`
     * (cards that can come back today) + single reads for cards that entered/left those sets.
     * Full tick (every 10th, or "Scan: full"): full columns for all of rated:1.
     */
    private fun poll(full: Boolean) {
        val rated = Anki.searchCards(this, "rated:1", arrayOf("_id")).mapNotNull { it["_id"]?.toLongOrNull() }.toSet()
        val read = ArrayList<CardState>()
        if (full) {
            read += Anki.searchCards(this, "rated:1", CardState.COLS).mapNotNull { CardState.from(it) }
        } else {
            read += Anki.searchCards(this, "rated:1 is:learn", CardState.COLS).mapNotNull { CardState.from(it) }
        }
        val have = read.map { it.id }.toSet()
        val extra = ((rated - prevRated) + (prevRated - rated) + (if (full) emptySet() else prevHot)) - have
        for (id in extra) {
            Anki.rows(contentResolver.query(Anki.uri("cards/$id"), CardState.COLS, null, null, null)).firstOrNull()?.let { CardState.from(it) }?.let { read += it }
        }
        prevRated = rated
        prevHot = read.filter { it.id in rated && it.queue in LEARN_QUEUES }.map { it.id }.toSet()
        for (n in read) compare(n)
    }

    private fun compare(n: CardState) {
        val old = cards[n.id]
        cards[n.id] = n
        // No earlier state: a new card answered for the first time (reps=1) starts from "new";
        // anything else (e.g. review-ahead) counts as one answer with an unknown button.
        val p = old ?: when {
            n.id !in prevRated || n.reps < 1 -> return
            n.reps == 1 -> CardState.fresh(n)
            else -> n.copy(reps = n.reps - 1)
        }
        val d = n.reps - p.reps
        if (d == 0) return
        if (d < 0) { undo(n, p, -d); return }
        val noticed = System.currentTimeMillis()
        val g0 = if (old == null && n.reps > 1) Guess(0, GUESS, "no earlier state for this card")
        else inferButton(p, n, stepsFor(n), noticed / 1000)
        val guess = if (d > 1) g0.copy(conf = GUESS, why = "${d} answers between polls; " + g0.why) else g0
        val truth = if (truthError == null) try {
            revlogFor(n.id)
        } catch (e: Exception) {
            truthError = "${e.javaClass.simpleName}: ${e.message}"; Report.fail("LIVE", "revlog probe failed; ground truth unavailable from now", e); null
        } else null
        val delay = truth?.let { noticed - it.id } ?: n.lastRev?.let { noticed - it * 1000 }
        val ev = Ev(events.size + 1, noticed, n.id, label(n.nid), p.type, guess, d, truth, delay, truth != null)
        events += ev
        val mark = if (truth == null) "" else if (ev.ok) " ✓" else " ✗ real ${BUTTONS.getOrElse(truth.ease) { "e${truth.ease}" }}"
        val line = "${fmt.format(Date(noticed))} ${BUTTONS[guess.button]} (${guess.conf})$mark · ${TYPES.getOrElse(p.type) { "?" }} · ${delay?.let { "${if (ev.delayExact) "" else "~"}${"%.1f".format(it / 1000.0)}s" } ?: "?"} · ${ev.label}"
        Report.log(
            "LIVE",
            "ev#${ev.no} cid=${n.id} nid=${n.nid} '${ev.label}' reps ${p.reps}→${n.reps} | before: ${p.short()} | after: ${n.short()} | " +
                "guess=${BUTTONS[guess.button]} ${guess.conf} (${guess.why}) | truth=${truth?.let { "${BUTTONS.getOrElse(it.ease) { i -> "e$i" }} ${if (ev.ok) "✓" else "✗"} (revlog ease=${it.ease} ivl=${it.ivl} lastIvl=${it.lastIvl} factor=${it.factor} type=${it.type})" } ?: "n/a"} | " +
                "delay=${delay ?: "?"}ms ${if (ev.delayExact) "(revlog time)" else "(last_review_time_secs, ±1s)"}",
        )
        addLine(line)
        val color = when { truth == null -> Color.DKGRAY; ev.ok -> Color.rgb(0, 140, 60); else -> Color.rgb(200, 30, 30) }
        val bigText = BUTTONS[guess.button].uppercase() + (if (truth == null) "" else if (ev.ok) "  ✓" else "  ✗")
        val subText = "${truth?.let { "real: ${BUTTONS.getOrElse(it.ease) { "?" }} · " } ?: if (truthError != null) "no ground truth · " else ""}${guess.conf} · ${TYPES.getOrElse(p.type) { "?" }} · ${delay?.let { "%.1f s".format(it / 1000.0) } ?: ""}\n${guess.why}\n${ev.label}"
        main.post { big.text = bigText; big.setTextColor(color); sub.text = subText }
        refreshStats()
        Widgets.refreshAll(this) // C8: keep widgets current while the companion watches
    }

    private fun undo(n: CardState, p: CardState, k: Int) {
        val undone = events.filter { it.cid == n.id && !it.undone }.takeLast(k)
        undone.forEach { it.undone = true }
        Report.log("LIVE", "UNDO detected cid=${n.id} reps ${p.reps}→${n.reps}; marked ${undone.map { "ev#${it.no}" }} as undone (excluded from accuracy)")
        addLine("${fmt.format(Date())} UNDO detected · ${label(n.nid)}")
        main.post { big.text = "UNDO"; big.setTextColor(Color.rgb(200, 120, 0)); sub.text = "AnkiDroid undo: card back to reps ${n.reps}\n${label(n.nid)}" }
        refreshStats()
    }

    private fun addLine(s: String) {
        lines.add(0, s)
        if (lines.size > 300) lines.removeAt(lines.size - 1)
        val text = lines.joinToString("\n")
        main.post { listView.text = text }
    }

    private fun refreshStats() {
        val s = summary()
        main.post { statsView.text = s }
    }

    // ---------- stats ----------
    private fun summary(): String {
        val ev = events.filter { !it.undone }
        val detected = ev.sumOf { it.repsDelta }
        val sb = StringBuilder()
        val real = if (truthError == null) try {
            sessions.sumOf { revlogCount(it[0], if (it[1] == 0L) Long.MAX_VALUE else it[1]) }
        } catch (_: Exception) { null } else null
        sb.append("Answers detected $detected").append(real?.let { " / revlog $it (${pct(detected, it)})" } ?: " (ground truth unavailable${truthError?.let { ": $it" } ?: ""})")
        val withTruth = ev.filter { it.truth != null }
        if (withTruth.isNotEmpty()) {
            val exact = withTruth.count { it.ok }
            val againOk = withTruth.count { (it.guess.button == 1) == (it.truth!!.ease == 1) }
            sb.append("\nExact button ${pct(exact, withTruth.size)} ($exact/${withTruth.size}) · Again-vs-pass ${pct(againOk, withTruth.size)}")
            sb.append("\nBy type: ").append((0..3).mapNotNull { t -> withTruth.filter { it.prevType == t }.takeIf { it.isNotEmpty() }?.let { l -> "${TYPES[t]} ${l.count { it.ok }}/${l.size}" } }.joinToString(" · "))
            sb.append("\nBy conf: ").append(listOf(SURE, LIKELY, GUESS).mapNotNull { c -> withTruth.filter { it.guess.conf == c }.takeIf { it.isNotEmpty() }?.let { l -> "$c ${l.count { it.ok }}/${l.size}" } }.joinToString(" · "))
            val wrong = withTruth.filter { !it.ok }.groupingBy { "${BUTTONS[it.guess.button]}→${BUTTONS.getOrElse(it.truth!!.ease) { "?" }}" }.eachCount()
            if (wrong.isNotEmpty()) sb.append("\nMistakes (guess→real): ").append(wrong.entries.joinToString(" · ") { "${it.key} ×${it.value}" })
        }
        val delays = ev.mapNotNull { it.delayMs }
        if (delays.isNotEmpty()) sb.append("\nDelay avg ${"%.2f".format(delays.average() / 1000)} s / max ${"%.2f".format(delays.max() / 1000.0)} s")
        sb.append("\nPoll light $lightMs · full $fullMs · undos ${events.count { it.undone }}")
        return sb.toString()
    }

    private fun pct(a: Int, b: Int) = if (b == 0) "-" else "${a * 100 / b}%"

    // ---------- helpers ----------
    /** UNOFFICIAL: latest revlog row for a card. */
    private fun revlogFor(cid: Long): Rev? =
        Anki.revlogProbe(this, "SELECT id, cid, ease, ivl, lastIvl, factor, type, 0, 0, 0, 0 FROM revlog WHERE cid = $cid AND type < 4 ORDER BY 1 DESC LIMIT 1")
            .firstOrNull()?.values?.toList()?.let { v ->
                Rev(v[0]!!.toLong(), v[2]!!.toInt(), v[3]!!.toLong(), v[4]!!.toLong(), v[5]!!.toLong(), v[6]!!.toInt())
            }

    /** UNOFFICIAL: real answers (learn/review/relearn/filtered; not manual reschedules) in [from, to). */
    private fun revlogCount(from: Long, to: Long): Int =
        Anki.revlogProbe(this, "SELECT count(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 FROM revlog WHERE id >= $from AND id < $to AND type < 4")
            .firstOrNull()?.values?.firstOrNull()?.toInt() ?: 0

    private fun stepsFor(c: CardState): Steps? {
        val did = if (c.odid != 0L) c.odid else c.did
        if (stepsCache.containsKey(did)) return stepsCache[did]
        val s = try {
            val row = Anki.rows(contentResolver.query(Anki.uri("decks/$did"), arrayOf("deck_id", "options"), null, null, null)).firstOrNull()
            val o = JSONObject(row?.get("options") ?: "{}")
            fun delays(k: String) = o.optJSONObject(k)?.optJSONArray("delays")?.let { a -> (0 until a.length()).map { a.getDouble(it) } }
            val learn = delays("new")
            val ints = o.optJSONObject("new")?.optJSONArray("ints")
            if (learn == null) null else Steps(learn, delays("lapse") ?: emptyList(), ints?.optInt(0) ?: 1, ints?.optInt(1) ?: 4)
        } catch (e: Exception) {
            Report.fail("LIVE", "deck options for $did", e); null
        }
        Report.log("LIVE", "deck $did steps: $s")
        stepsCache[did] = s
        return s
    }

    private fun label(nid: Long): String = noteCache.getOrPut(nid) {
        try {
            (Anki.note(this, nid)?.fields?.firstOrNull() ?: "").replace(Regex("<[^>]*>"), " ").replace("&nbsp;", " ").replace(Regex("\\s+"), " ").trim().take(40)
        } catch (_: Exception) {
            "nid $nid"
        }
    }

    private fun show(a: String, b: String) = main.post { big.text = a; big.setTextColor(Color.rgb(200, 30, 30)); sub.text = b }

    private fun share() {
        val text = "AnkiGo report\n\n" + Report.all()
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "AnkiGo report").putExtra(Intent.EXTRA_TEXT, text.takeLast(400_000)), "Share report"))
    }

    companion object {
        /** queue 1 = learning, 3 = day-learning/relearning: cards that may come back today. */
        val LEARN_QUEUES = setOf(1, 3)
    }
}
