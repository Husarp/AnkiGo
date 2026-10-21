package com.husarp.ankigo.debug

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.RandomAccessFile
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * R13 Part E: one line of the debug log, "2026-10-21 14:03:22.123 [TAG] msg". A message with line breaks (a stack trace) goes on
 * continuation lines that start with four spaces and no date: they belong to the dated line above them. Pure Kotlin.
 */
object LogLine {
    val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private const val STAMP = 23 // "yyyy-MM-dd HH:mm:ss.SSS"

    /** The line (and its continuation lines) for [msg] logged at [at] under [tag]; no final line break. */
    fun format(at: LocalDateTime, tag: String, msg: String): String {
        val body = msg.replace("\r\n", "\n").replace('\r', '\n').trimEnd('\n').replace("\n", "\n    ")
        return "${TIME.format(at)} [$tag] $body"
    }

    /** When a dated line was written; null for a continuation line or a line of the old format ("HH:mm:ss [TAG] msg", no date). */
    fun timeOf(line: String): LocalDateTime? {
        if (line.length < STAMP || line[4] != '-' || line[10] != ' ') return null
        return try { LocalDateTime.parse(line.substring(0, STAMP), TIME) } catch (e: DateTimeParseException) { null }
    }

    /** [e] with its stack (at most [frames] lines per exception) and its causes, for a log line. */
    fun stack(e: Throwable, frames: Int = 30): String {
        val sb = StringBuilder()
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth < 5) {
            if (depth > 0) sb.append("\nCaused by: ")
            sb.append(t.javaClass.name).append(": ").append(t.message ?: "")
            val st = t.stackTrace
            for (f in st.take(frames)) sb.append("\n  at ").append(f)
            if (st.size > frames) sb.append("\n  ... ").append(st.size - frames).append(" more")
            t = t.cause?.takeIf { it !== t }
            depth++
        }
        return sb.toString()
    }
}

/**
 * The log on disk: [dir]/[name].txt (the current file) and [name].1.txt (the one before). When the current file would pass
 * [maxBytes] it becomes the older one (the older one is dropped) and a new one starts: at most ~2 x [maxBytes] in all, and always
 * the newest lines. A file left too big (the old unlimited report.txt) keeps only its last [maxBytes]. One writer at a time: the
 * caller serialises the calls (Report's worker, under its lock).
 */
class RotatingLog(private val dir: File, private val name: String = "report", val maxBytes: Long = 512L * 1024) {
    val current: File get() = File(dir, "$name.txt")
    val older: File get() = File(dir, "$name.1.txt")
    private var out: BufferedWriter? = null
    private var size = -1L

    init {
        dir.mkdirs()
        trimTo(older, maxBytes)
        if (current.length() > maxBytes) { trimTo(current, maxBytes); older.delete(); current.renameTo(older) }
    }

    /** Appends [text] (whole lines, ending with a line break). Buffered: [flush] writes it out. */
    fun append(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8).size.toLong()
        if (size < 0) size = current.length()
        if (size > 0 && size + bytes > maxBytes) rotate()
        val w = out ?: BufferedWriter(OutputStreamWriter(FileOutputStream(current, true), Charsets.UTF_8), 16 * 1024).also { out = it }
        w.write(text)
        size += bytes
    }

    fun flush() { out?.flush() }

    /** The older file, then the current one. */
    fun readAll(): String {
        flush()
        return (if (older.exists()) older.readText() else "") + (if (current.exists()) current.readText() else "")
    }

    fun clear() {
        close()
        older.delete(); current.delete()
        size = 0
    }

    fun close() { try { out?.close() } catch (e: Exception) {} ; out = null }

    private fun rotate() {
        close()
        older.delete()
        current.renameTo(older)
        size = 0
    }

    /** Keeps only the last [max] bytes of [f], from its first whole line on. */
    private fun trimTo(f: File, max: Long) {
        val len = f.length()
        if (len <= max) return
        val tail = RandomAccessFile(f, "r").use { raf ->
            raf.seek(len - max)
            val b = ByteArray(max.toInt())
            raf.readFully(b)
            b
        }
        val start = tail.indexOf('\n'.code.toByte()).let { if (it < 0) 0 else it + 1 }
        f.writeBytes(tail.copyOfRange(start, tail.size))
    }
}

/**
 * The writer behind Report: [RotatingLog] under one lock, and the crash path that must reach the disk before the process dies.
 * Pure Kotlin (the Android worker thread is Report's).
 */
class LogWriter(val files: RotatingLog, private val now: () -> LocalDateTime = { LocalDateTime.now() }) {
    private val lock = Any()

    fun write(text: String, flush: Boolean) = synchronized(lock) {
        try { files.append(text); if (flush) files.flush() } catch (e: Exception) { /* a full disk must not break the app */ }
    }

    fun flush() = synchronized(lock) { try { files.flush() } catch (e: Exception) {} }

    fun readAll(): String = synchronized(lock) { try { files.readAll() } catch (e: Exception) { "" } }

    fun clear() = synchronized(lock) { try { files.clear() } catch (e: Exception) {} }

    /** An uncaught [e] on [thread]: written and flushed at once, on the dying thread ([before] first: the lines still queued). */
    fun crash(thread: String, e: Throwable, before: () -> Unit = {}) = synchronized(lock) {
        try { before() } catch (x: Throwable) {}
        write(LogLine.format(now(), "CRASH", "uncaught on thread $thread: " + LogLine.stack(e, 60)) + "\n", flush = true)
    }

    /** The handler that writes the crash, then hands it on to [next] (Android's: the "app stopped" dialog and the process end). */
    fun handler(next: Thread.UncaughtExceptionHandler?, before: () -> Unit = {}): Thread.UncaughtExceptionHandler =
        Thread.UncaughtExceptionHandler { t, e ->
            try { crash(t.name, e, before) } catch (x: Throwable) {}
            next?.uncaughtException(t, e)
        }
}

/** R13 Part E: the ranges Diagnostics -> "Share log…" offers, and the filter (the dated lines make it simple). Pure Kotlin. */
object LogRange {
    enum class Kind(val label: String) { LAST_HOUR("Last hour"), TODAY("Today"), YESTERDAY("Yesterday"), DAY("Pick a day…"), EVERYTHING("Everything") }

    /** Lines written at [from] or later and before [to] (null = open). */
    data class Window(val from: LocalDateTime?, val to: LocalDateTime?) {
        fun has(t: LocalDateTime) = (from == null || !t.isBefore(from)) && (to == null || t.isBefore(to))
    }

    fun window(kind: Kind, now: LocalDateTime, day: LocalDate? = null): Window = when (kind) {
        Kind.LAST_HOUR -> Window(now.minusHours(1), null)
        Kind.TODAY -> Window(now.toLocalDate().atStartOfDay(), null)
        Kind.YESTERDAY -> now.toLocalDate().minusDays(1).let { Window(it.atStartOfDay(), it.plusDays(1).atStartOfDay()) }
        Kind.DAY -> (day ?: now.toLocalDate()).let { Window(it.atStartOfDay(), it.plusDays(1).atStartOfDay()) }
        Kind.EVERYTHING -> Window(null, null)
    }

    /**
     * The lines of [text] inside [w]; a continuation line goes with its dated line. Lines without any date before them (the old
     * format) only come with an open window ([Kind.EVERYTHING]).
     */
    fun filter(text: String, w: Window): String {
        if (w.from == null && w.to == null) return text
        val sb = StringBuilder()
        var keep = false
        for (line in text.lineSequence()) {
            val t = LogLine.timeOf(line)
            if (t != null) keep = w.has(t)
            else if (!line.startsWith("    ")) keep = false // an old undated line: not in a dated range
            if (keep) sb.append(line).append('\n')
        }
        return sb.toString()
    }

    /** The days the log has lines for, newest first ("Pick a day"). */
    fun days(text: String): List<LocalDate> =
        text.lineSequence().mapNotNull { LogLine.timeOf(it)?.toLocalDate() }.distinct().sortedDescending().toList()
}
