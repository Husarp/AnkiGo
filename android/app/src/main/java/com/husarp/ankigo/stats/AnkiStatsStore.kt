package com.husarp.ankigo.stats

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.husarp.ankigo.Anki
import com.husarp.ankigo.Local
import com.husarp.ankigo.Report
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// TICKET L2-07: the Stats screen's AnkiDroid numbers. A cache file (not the game save) shows them at once; one background
// thread refreshes them through the Anki adapter and posts every result to the main thread.

/**
 * Holds the latest [StatsSnapshot]. Read [snapshot] and [cached] on the main thread; [refresh] can be called from there
 * and never blocks it. Every AnkiDroid call runs on the store's own thread and any failure ends in the snapshot's
 * [StatsSnapshot.access] (and Report), never in a crash.
 */
object AnkiStatsStore {
    private const val FILE = "ankidroid-stats.json"
    /** A refresh that finished OK is not repeated within this time (the page asks again every time it comes to the front). */
    private const val MIN_GAP_MS = 20_000L

    /** Tests switch this off so a page doesn't go and read a phone's AnkiDroid. */
    internal var enabled = true

    /** The latest state; assigned on the main thread only (tests may set it directly). */
    @Volatile var snapshot = StatsSnapshot()
        internal set

    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val io by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "ankidroid-stats").apply { isDaemon = true } } }
    private val running = AtomicBoolean(false)
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private var loaded = false
    @Volatile private var lastStartedAt = 0L

    /** [l] is called on the main thread after every change of [snapshot]. */
    fun addListener(l: () -> Unit) { listeners.addIfAbsent(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    /** The snapshot with the cached AnkiDroid numbers filled in (read from the cache file the first time, only if it is from the selected AnkiDroid). */
    fun cached(ctx: Context): StatsSnapshot {
        if (enabled && !loaded) {
            loaded = true
            if (snapshot.stats == null) {
                val app = ctx.applicationContext
                try {
                    Anki.load(app)
                    val c = readCache(app)
                    if (c != null && c.pkg == Anki.pkg) snapshot = snapshot.copy(stats = c)
                } catch (e: Exception) {
                    Report.fail("STATS", "reading the stats cache", e)
                }
            }
        }
        return snapshot
    }

    /**
     * Starts a refresh (QUICK, then HISTORY when needed) unless one is running, or one finished OK a moment ago ([force] skips
     * that wait). [today] is AnkiGo's game day, [dayStartHour] its day start (for "Time in AnkiGo").
     */
    fun refresh(ctx: Context, today: String, dayStartHour: Int, force: Boolean = false) {
        if (!enabled || runCatching { LocalDate.parse(today) }.isFailure) return
        val now = System.currentTimeMillis()
        if (!force && snapshot.access == AnkiAccess.OK && now - lastStartedAt < MIN_GAP_MS) return
        if (!running.compareAndSet(false, true)) return
        lastStartedAt = now
        val app = ctx.applicationContext
        io.execute {
            try {
                work(app, today, dayStartHour)
            } catch (t: Throwable) { // a background thread must never take the app down
                Report.fail("STATS", "refresh", t)
                update { it.copy(access = AnkiAccess.ERROR, message = failureText(t), historyRunning = false) }
            } finally {
                running.set(false)
            }
        }
    }

    private fun work(app: Context, today: String, dayStartHour: Int) {
        val time = readTime(app, dayStartHour)
        update { it.copy(timeByDay = time) }
        Anki.load(app)
        val access = AnkiAccess.decide(
            installed = Anki.installed(app).any { it.pkg == Anki.pkg },
            supported = Anki.studySupported(app),
            permission = Anki.hasPermission(app),
        )
        if (access != AnkiAccess.OK) {
            update { it.copy(access = access, message = null, historyRunning = false) }
            return
        }
        val pkg = Anki.pkg
        val prev = snapshot.stats?.takeIf { it.pkg == pkg }
        try {
            AnkiStatsReader { q -> Anki.countCards(app, q) }.refresh(prev, pkg, today, System::currentTimeMillis) { stats, historyRunning ->
                writeCache(app, stats)
                update { it.copy(stats = stats, access = AnkiAccess.OK, message = null, historyRunning = historyRunning) }
            }
        } catch (e: SecurityException) { // the permission was taken away while reading
            Report.fail("STATS", "AnkiDroid permission", e)
            update { it.copy(access = AnkiAccess.NO_PERMISSION, message = null, historyRunning = false) }
        } catch (e: Exception) {
            Report.fail("STATS", "reading AnkiDroid's stats", e)
            update { it.copy(access = AnkiAccess.ERROR, message = failureText(e), historyRunning = false) }
        }
    }

    private fun failureText(t: Throwable): String = (t.message ?: t.javaClass.simpleName).take(60)

    /** ms per game day from AnkiGo's own review log; AnkiDroid is not involved. */
    private fun readTime(app: Context, dayStartHour: Int): Map<String, Long> = try {
        val f = Local.reviewsFile(app)
        if (!f.exists()) emptyMap() else f.useLines { timeInAnkiGo(it) { ms -> AnkiGoTime.dayKey(ms, dayStartHour) } }
    } catch (e: Exception) {
        Report.fail("STATS", "reading AnkiGo's own review log", e)
        emptyMap()
    }

    private fun readCache(app: Context): AnkiStats? {
        val f = File(app.filesDir, FILE)
        return if (f.exists()) AnkiStatsCodec.decode(f.readText()) else null
    }

    /** Written to a temp file and renamed, so a killed app never leaves half a cache. */
    private fun writeCache(app: Context, s: AnkiStats) {
        try {
            val tmp = File(app.filesDir, "$FILE.tmp")
            tmp.writeText(AnkiStatsCodec.encode(s))
            if (!tmp.renameTo(File(app.filesDir, FILE))) Report.fail("STATS", "stats cache could not be saved")
        } catch (e: Exception) {
            Report.fail("STATS", "saving the stats cache", e)
        }
    }

    private fun update(f: (StatsSnapshot) -> StatsSnapshot) {
        main.post {
            snapshot = f(snapshot)
            for (l in listeners) l()
        }
    }
}
