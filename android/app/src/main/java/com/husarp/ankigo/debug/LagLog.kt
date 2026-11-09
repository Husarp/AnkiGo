package com.husarp.ankigo.debug

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** This test build records study timing by default; payloads contain ids and timings only. */
object LagLog {
    const val PREF = "lagLog"
    @Volatile private var recorder: LagRecorder? = null
    @Volatile private var studies = 0
    private var watch: java.util.concurrent.ScheduledExecutorService? = null
    private var ping: Runnable? = null
    private val scope = ThreadLocal<Boolean>()

    @Synchronized fun init(ctx: Context) {
        if (recorder == null) recorder = LagRecorder(File(ctx.applicationContext.filesDir, "study-lag.txt")).also {
            it.enabled = ctx.getSharedPreferences("study", 0).getBoolean(PREF, true)
        }
    }
    fun enabled() = recorder?.enabled ?: true
    fun enable(ctx: Context, on: Boolean) {
        init(ctx); recorder?.enabled = on
        ctx.getSharedPreferences("study", 0).edit().putBoolean(PREF, on).apply()
    }
    fun record(kind: String, vararg fields: Pair<String, Any?>) { recorder?.record(kind, *fields) }
    fun snapshot(done: (String) -> Unit) { recorder?.snapshot(done) ?: done("") }
    fun clear(done: () -> Unit) { recorder?.clear(done) ?: done() }

    /** Marks study work on workers too, including a write that outlives its Activity. */
    fun <T> study(block: () -> T): T {
        val before = scope.get()
        scope.set(true)
        return try { block() } finally { scope.set(before) }
    }
    fun <T> provider(call: String, block: () -> T): T {
        if (recorder?.enabled != true || studies == 0 && scope.get() != true) return block()
        val start = SystemClock.elapsedRealtime()
        var ok = false
        return try { block().also { ok = true } } finally {
            record("PROVIDER", "call" to call, "ms" to SystemClock.elapsedRealtime() - start,
                "thread" to Thread.currentThread().name, "ok" to ok)
        }
    }

    /** Names costly UI/game work even when a watchdog sample lands between its frames. */
    fun <T> work(name: String, block: () -> T): T {
        if (recorder?.enabled != true || studies == 0 && scope.get() != true) return block()
        val start = SystemClock.elapsedRealtime()
        return try { block() } finally {
            val ms = SystemClock.elapsedRealtime() - start
            if (ms >= 16) record("WORK", "name" to name, "ms" to ms, "thread" to Thread.currentThread().name)
        }
    }

    /** One outstanding main-Looper ping. Sample stacks at 100 ms and again over 700 ms while main is still blocked. */
    @Synchronized fun start(ctx: Context) {
        init(ctx)
        studies++
        if (watch != null) return
        val main = Handler(Looper.getMainLooper())
        val mainThread = Looper.getMainLooper().thread
        var sent = 0L
        var logged = false
        var stacked = false
        var longLogged = false
        var pingId = ""
        val guard = Any()
        val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "study-lag-watch").apply { isDaemon = true } }
        watch = worker
        worker.scheduleAtFixedRate({
            synchronized(guard) {
                if (!enabled()) return@synchronized
                val now = SystemClock.uptimeMillis()
                if (sent == 0L) {
                    sent = now; logged = false; stacked = false; longLogged = false
                    pingId = "${System.currentTimeMillis()}/$now"
                    val ack = Runnable {
                        synchronized(guard) {
                            val gap = SystemClock.uptimeMillis() - sent
                            if (sent != 0L && gap > 100) record("STALL", "ping" to pingId, "ms" to gap)
                            sent = 0
                        }
                    }
                    ping = ack
                    main.post(ack)
                } else {
                    val gap = now - sent
                    if ((gap >= 100 && !stacked) || (gap > 700 && !longLogged)) {
                        stacked = true
                        record("STACK", "ping" to pingId, "ms" to gap, "main" to mainThread.stackTrace.joinToString(" | "))
                    }
                    // A stall still in progress must survive a process kill in the log.
                    if (gap > 100 && !logged) { logged = true; record("STALL_START", "ping" to pingId, "ms" to gap) }
                    if (gap > 700 && !longLogged) { longLogged = true; record("STALL_START", "ping" to pingId, "ms" to gap) }
                }
            }
        }, 0, 100, TimeUnit.MILLISECONDS)
    }
    @Synchronized fun stop() {
        studies = (studies - 1).coerceAtLeast(0)
        if (studies > 0) return
        watch?.shutdownNow(); watch = null
        ping?.let { Handler(Looper.getMainLooper()).removeCallbacks(it) }; ping = null
    }
}
