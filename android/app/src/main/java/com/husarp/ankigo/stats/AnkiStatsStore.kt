package com.husarp.ankigo.stats

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.husarp.ankigo.Anki
import com.husarp.ankigo.Local
import com.husarp.ankigo.Report
import java.io.File
import java.time.LocalDate
import java.util.Collections
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
    /** On-demand chart reads (deck filter, new cards, retention windows) run here so they never hold up the normal refresh. */
    private val seriesIo by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "ankidroid-series").apply { isDaemon = true } } }
    private val seriesInFlight = Collections.synchronizedSet(HashSet<SeriesKey>())
    /**
     * When a chart read last failed (no AnkiDroid / no permission / provider error). The page asks again on every render and
     * every failure re-renders it, so without this pause a failing read would loop forever (round-3 manager fix).
     */
    private val seriesFailedAt = java.util.concurrent.ConcurrentHashMap<SeriesKey, Long>()
    private const val SERIES_RETRY_MS = 60_000L
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
        val live = readLiveTime(app, dayStartHour)
        update { it.copy(timeByDay = time, liveTimeByDay = live) }
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

    /**
     * Reads the AnkiDroid numbers the chart needs for [keys] ([ChartData.seriesKeys]) on the store's second thread and
     * publishes each through the listener ([StatsSnapshot.series]). A key read less than [AnkiSeriesCache.MAX_AGE_MS] ago, or
     * already being read, is skipped unless [force]. Failures end in [StatsSnapshot.access] / message, never a crash.
     */
    fun requestSeries(ctx: Context, keys: List<SeriesKey>, force: Boolean = false) {
        if (!enabled) return
        val app = ctx.applicationContext
        for (key in keys.distinct()) {
            if (runCatching { LocalDate.parse(key.today) }.isFailure) continue
            val now = System.currentTimeMillis()
            if (!force && AnkiSeriesCache.fresh(snapshot.series, key, now)) continue
            if (!force && now - (seriesFailedAt[key] ?: Long.MIN_VALUE / 2) < SERIES_RETRY_MS) continue
            if (!seriesInFlight.add(key)) continue
            seriesIo.execute {
                try {
                    readSeries(app, key)
                } catch (t: Throwable) { // a background thread must never take the app down
                    seriesFailedAt[key] = System.currentTimeMillis()
                    Report.fail("STATS", "chart read", t)
                    update { it.copy(access = AnkiAccess.ERROR, message = failureText(t)) }
                } finally {
                    seriesInFlight.remove(key)
                }
            }
        }
    }

    private fun readSeries(app: Context, key: SeriesKey) {
        Anki.load(app)
        val access = AnkiAccess.decide(
            installed = Anki.installed(app).any { it.pkg == Anki.pkg },
            supported = Anki.studySupported(app),
            permission = Anki.hasPermission(app),
        )
        if (access != AnkiAccess.OK) {
            seriesFailedAt[key] = System.currentTimeMillis()
            update { it.copy(access = access, message = null) }
            return
        }
        try {
            val data = AnkiSeriesReader { q -> Anki.countCards(app, q) }.read(key, System.currentTimeMillis())
            seriesFailedAt.remove(key)
            update { it.copy(series = AnkiSeriesCache.put(it.series, key, data)) }
        } catch (e: SecurityException) {
            seriesFailedAt[key] = System.currentTimeMillis()
            Report.fail("STATS", "AnkiDroid permission", e)
            update { it.copy(access = AnkiAccess.NO_PERMISSION, message = null) }
        } catch (e: Exception) {
            seriesFailedAt[key] = System.currentTimeMillis()
            Report.fail("STATS", "reading a chart from AnkiDroid", e)
            update { it.copy(access = AnkiAccess.ERROR, message = failureText(e)) }
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

    /** ms per game day of the answers AnkiGo timed itself while watching AnkiDroid live (`live-time.csv`). */
    private fun readLiveTime(app: Context, dayStartHour: Int): Map<String, Long> = try {
        val f = LiveTimeFile.file(app.filesDir)
        if (!f.exists()) emptyMap() else f.useLines { timeLive(it) { ms -> AnkiGoTime.dayKey(ms, dayStartHour) } }
    } catch (e: Exception) {
        Report.fail("STATS", "reading the live time log", e)
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
