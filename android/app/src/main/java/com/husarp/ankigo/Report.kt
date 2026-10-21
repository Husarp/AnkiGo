package com.husarp.ankigo

import android.content.Context
import com.husarp.ankigo.debug.LogLine
import com.husarp.ankigo.debug.LogWriter
import com.husarp.ankigo.debug.RotatingLog
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * In-app debug log (R13 Part E): every line "2026-10-21 14:03:22.123 [TAG] msg", kept in `report.txt` + `report.1.txt` (512 KB each,
 * the oldest dropped: ~1 MB at most), shared by the activities, widgets and services. A call only formats the line and queues it:
 * the file is written on its own thread (buffered, flushed when nothing more is queued), so logging never slows the answer path.
 * An uncaught exception is written at once, before the process dies ([init] installs the handler).
 */
object Report {
    private const val MAX_BYTES = 512L * 1024
    private val worker = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue()) { r -> Thread(r, "ankigo-report").apply { isDaemon = true } }
    @Volatile private var writer: LogWriter? = null
    @Volatile var listener: ((String) -> Unit)? = null

    @Synchronized
    fun init(ctx: Context) {
        if (writer != null) return
        val dir = ctx.applicationContext.filesDir
        val w = LogWriter(RotatingLog(dir, "report", MAX_BYTES))
        writer = w
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(w.handler(prev) { drainQueued() })
        File(dir, "game-events.log").delete(); File(dir, "game-events.log.tmp").delete() // R13 Part E: the old ids-only ring is part of this log now
    }

    fun log(tag: String, msg: String) {
        val line = LogLine.format(LocalDateTime.now(), tag, msg)
        android.util.Log.i("AnkiGo", line)
        enqueue(line)
        listener?.invoke(line)
    }

    /**
     * Many lines of [tag] at once, built on the log's own thread ([lines] must only read what it captured: game events are
     * immutable). For the game events of an answer: the caller pays one queue call, not the formatting.
     */
    fun logLater(tag: String, lines: () -> List<String>) {
        val at = LocalDateTime.now()
        val w = writer ?: return
        try {
            worker.execute {
                val text = try { lines() } catch (e: Exception) { listOf("could not describe: ${e.javaClass.simpleName}") }
                if (text.isEmpty()) return@execute
                val formatted = text.map { LogLine.format(at, tag, it) }
                w.write(formatted.joinToString("\n", postfix = "\n"), flush = worker.queue.isEmpty())
                listener?.let { l -> formatted.forEach(l) }
            }
        } catch (e: Exception) { /* the log thread is gone: nothing to do */ }
    }

    fun ok(tag: String, msg: String) = log(tag, "OK   $msg")
    fun fail(tag: String, msg: String) = log(tag, "FAIL $msg")

    /** A caught error, with its stack trace (continuation lines). */
    fun fail(tag: String, msg: String, e: Throwable) = log(tag, "FAIL $msg -> " + LogLine.stack(e))

    /** Both files, the older first (waits for the lines still queued). */
    fun all(): String {
        val w = writer ?: return ""
        return try { worker.submit<String> { w.readAll() }.get(5, TimeUnit.SECONDS) } catch (e: Exception) { w.readAll() }
    }

    fun clear() {
        val w = writer ?: return
        try { worker.execute { w.clear() } } catch (e: Exception) {}
    }

    private fun enqueue(line: String) {
        val w = writer ?: return
        try { worker.execute { w.write(line + "\n", flush = worker.queue.isEmpty()) } } catch (e: Exception) {}
    }

    /** The crash path, on the dying thread: the lines still queued are written first. */
    private fun drainQueued() {
        val left = ArrayList<Runnable>()
        worker.queue.drainTo(left)
        for (r in left) try { r.run() } catch (e: Throwable) {}
        writer?.flush()
    }
}
