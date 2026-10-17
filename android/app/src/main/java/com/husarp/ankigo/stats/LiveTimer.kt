package com.husarp.ankigo.stats

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors

// TICKET R3-04 (PLAN 26): time studied while AnkiGo only watches AnkiDroid (split screen, picture-in-picture, floating
// window). The AnkiDroid API has no review log, so the time of a live-detected answer is measured here. Own answers (made
// on AnkiGo's study screen) stay in reviews.csv; catch-up answers (made while AnkiGo wasn't watching) have no time.

/** Anki's default "maximum answer seconds": one live answer never counts longer than this. */
const val LIVE_ANSWER_CAP_MS = 60_000L

/**
 * The time of each live answer = time since the previous answer of the same watch session, or since the watch (re)started
 * for the first one, capped at [cap]. Pausing or stopping the watch resets it. [now] is any steady clock in ms. Main thread
 * only (LiveSession); no android.* so it is unit-tested on the JVM.
 */
class LiveTimer(private val cap: Long = LIVE_ANSWER_CAP_MS) {
    private var mark = NONE

    /** The watch started or restarted (detector running). */
    fun start(now: Long) { mark = now }

    /** The watch was paused or stopped: the next [start] begins a new session. */
    fun stop() { mark = NONE }

    /** An answer that is not counted here (own answer in AnkiGo) happened: the next live answer is timed from now. No watch: nothing. */
    fun touch(now: Long) { if (mark != NONE) mark = now }

    /**
     * An answer was detected. Returns the ms to write to live-time.csv, or null when it must not be written: a catch-up
     * answer (it leaves the mark alone) or no watch session. A clock that went backwards counts 0.
     */
    fun onAnswer(catchUp: Boolean, now: Long): Long? {
        if (catchUp || mark == NONE) return null
        val ms = (now - mark).coerceIn(0L, cap)
        mark = now
        return ms
    }

    private companion object { const val NONE = Long.MIN_VALUE }
}

/** `<filesDir>/live-time.csv`: one line per live answer, `epochMillis,ms` (ms already capped). Written on its own thread. */
object LiveTimeFile {
    const val NAME = "live-time.csv"

    fun file(filesDir: File) = File(filesDir, NAME)

    fun line(at: Long, ms: Long) = "$at,$ms\n"

    private val io by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "ankigo-live-time").apply { isDaemon = true } } }

    /** Appends on the background thread; a failure is passed to [onError] (never thrown into the game step). */
    fun appendAsync(filesDir: File, at: Long, ms: Long, onError: (Throwable) -> Unit = {}) {
        try {
            io.execute {
                try { file(filesDir).appendText(line(at, ms)) } catch (t: Throwable) { onError(t) }
            }
        } catch (t: Throwable) {
            onError(t)
        }
    }
}

/**
 * ms spent per game day from live-time.csv lines `epochMillis,ms`. [dayKey] maps an instant to its game day. Lines that
 * can't be read (also a half-written last line) are skipped; one answer counts at most [LIVE_ANSWER_CAP_MS].
 */
fun timeLive(lines: Sequence<String>, dayKey: (Long) -> String): Map<String, Long> {
    val out = HashMap<String, Long>()
    for (line in lines) {
        val p = line.split(',')
        if (p.size != 2) continue
        val at = p[0].trim().toLongOrNull() ?: continue
        val ms = p[1].trim().toLongOrNull() ?: continue
        if (at <= 0 || ms < 0) continue
        val day = try { dayKey(at) } catch (_: Exception) { continue }
        out[day] = (out[day] ?: 0L) + minOf(ms, LIVE_ANSWER_CAP_MS)
    }
    return out
}
