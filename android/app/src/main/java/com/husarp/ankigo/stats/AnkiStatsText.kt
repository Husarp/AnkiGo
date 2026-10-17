package com.husarp.ankigo.stats

import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

// TICKET L2-07: what the Stats screen can say about the AnkiDroid numbers (pure Kotlin, tested).

/** Why AnkiGo can or can't read AnkiDroid's numbers. */
enum class AnkiAccess {
    OK, NO_PERMISSION, ANKIDROID_TOO_OLD, NOT_INSTALLED, ERROR;

    companion object {
        /** [supported] = AnkiDroid 2.24+ with the provider; checked in this order so the most useful reason wins. */
        fun decide(installed: Boolean, supported: Boolean, permission: Boolean): AnkiAccess = when {
            !installed -> NOT_INSTALLED
            !supported -> ANKIDROID_TOO_OLD
            !permission -> NO_PERMISSION
            else -> OK
        }
    }
}

/** Everything the Stats screen shows besides the game's own numbers. [access] is null until the first check has finished. */
data class StatsSnapshot(
    val stats: AnkiStats? = null,
    val access: AnkiAccess? = null,
    /** Short reason for [AnkiAccess.ERROR]. */
    val message: String? = null,
    /** A HISTORY pass is running. */
    val historyRunning: Boolean = false,
    /** ms per game day AnkiGo measured itself ([timeInAnkiGo]). */
    val timeByDay: Map<String, Long> = emptyMap(),
    /** ms per game day AnkiGo measured for answers detected live (split screen, PiP, floating); [timeLive]. */
    val liveTimeByDay: Map<String, Long> = emptyMap(),
    /** Per-deck / on-demand AnkiDroid reads for the chart ([AnkiSeriesCache]). */
    val series: Map<SeriesKey, SeriesData> = emptyMap(),
) {
    /** Time studied on [day]: in AnkiGo's study screen + live detection. */
    fun timeTotalOn(day: String): Long = (timeByDay[day] ?: 0L) + (liveTimeByDay[day] ?: 0L)

    /** [timeByDay] and [liveTimeByDay] added per day. */
    val timeTotalByDay: Map<String, Long> get() = (timeByDay.keys + liveTimeByDay.keys).associateWith { timeTotalOn(it) }
}

object StatsText {
    fun reason(access: AnkiAccess?, message: String? = null): String = when (access) {
        AnkiAccess.NO_PERMISSION -> "Allow AnkiGo to read AnkiDroid to see its stats"
        AnkiAccess.ANKIDROID_TOO_OLD -> "AnkiDroid 2.24 or newer is needed to see its stats"
        AnkiAccess.NOT_INSTALLED -> "Install AnkiDroid to see its stats"
        AnkiAccess.ERROR -> "Couldn't read AnkiDroid's stats" + (message?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
        else -> ""
    }

    /** The line under the heatmap: where the numbers come from, or why they are AnkiGo's own count. */
    fun heatmapNote(s: StatsSnapshot, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val st = s.stats
        val failed = s.access != null && s.access != AnkiAccess.OK
        if (st != null) {
            if (s.historyRunning && st.historyCheckedAt == 0L) return "Reading AnkiDroid's history…"
            val base = "From AnkiDroid · updated ${updated(st.fetchedAt, now, zone)}"
            return if (failed) "$base · ${reason(s.access, s.message)}" else base
        }
        return if (failed) "${reason(s.access, s.message)}. This is AnkiGo's own count, kept from version 0.5.0 on."
        else "Reading AnkiDroid's stats… Meanwhile this is AnkiGo's own count."
    }

    /** "just now", "5 min ago", "14:32", "yesterday 14:32" or "Oct 12". */
    fun updated(at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val age = now - at
        if (age < 60_000) return "just now"
        if (age < 3_600_000) return "${age / 60_000} min ago"
        val a = Instant.ofEpochMilli(at).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val time = String.format(Locale.US, "%02d:%02d", a.hour, a.minute)
        return when (a.toLocalDate()) {
            today -> time
            today.minusDays(1) -> "yesterday $time"
            else -> "${a.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${a.dayOfMonth}"
        }
    }

    /** Short study time for a tile: "0 min", "<1 min", "14 min", "1h 05m". */
    fun duration(ms: Long): String {
        if (ms <= 0) return "0 min"
        val min = (ms + 30_000) / 60_000
        if (min < 1) return "<1 min"
        if (min < 60) return "$min min"
        return String.format(Locale.US, "%dh %02dm", min / 60, min % 60)
    }
}
