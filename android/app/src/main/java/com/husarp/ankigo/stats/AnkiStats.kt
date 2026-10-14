package com.husarp.ankigo.stats

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TreeMap

// TICKET L2-07: the Stats screen's study numbers from AnkiDroid's official API (card searches), cached, plus the time
// AnkiGo measured itself. Pure Kotlin (no android.*): every AnkiDroid call is the `count` function the caller passes in.

/** The Anki searches used, exactly as the Anki manual spells them. Count cards with them; never load the cards. */
object AnkiQueries {
    private const val LIVE = " -is:suspended -is:buried"

    const val DUE_NOW = "is:due"
    // AnkiDroid's card-count buckets
    const val NEW = "is:new$LIVE"
    const val LEARNING = "is:learn -is:review$LIVE"
    const val RELEARNING = "is:learn is:review$LIVE"
    const val YOUNG = "is:review -is:learn prop:ivl<21$LIVE"
    const val MATURE = "is:review -is:learn prop:ivl>=21$LIVE"
    const val SUSPENDED = "is:suspended"
    const val BURIED = "is:buried -is:suspended"

    /** Cards answered on ONE day: 0 = today, 1 = yesterday ... (not `rated:n` minus `rated:n-1`: that only counts a card on its last day). */
    fun ratedOn(daysAgo: Int) = "prop:rated=${-daysAgo}"

    /** Cards answered in the last [days] days, with button 1 Again .. 4 Easy, or with any button when [button] is null. */
    fun rated(days: Int, button: Int? = null) = if (button == null) "rated:$days" else "rated:$days:$button"

    /** Cards answered for the first time in the last [days] days (new cards introduced). */
    fun introduced(days: Int) = "introduced:$days"

    /** Cards due [days] days from now (1 = tomorrow). */
    fun due(days: Int) = "prop:due=$days"

    /** Retention estimate: cards answered in the last 30 days that were not new in that period, and of those the ones that got Again. */
    const val RETENTION_ANSWERED = "rated:30 -introduced:30"
    const val RETENTION_AGAIN = "rated:30:1 -introduced:30"
}

data class Counts(
    val new: Int = 0, val learning: Int = 0, val relearning: Int = 0,
    val young: Int = 0, val mature: Int = 0, val suspended: Int = 0, val buried: Int = 0,
)

/**
 * What the last read of AnkiDroid found. Cards, not reviews: AnkiDroid's API has no review log.
 * [perDay] maps game dates ("yyyy-MM-dd") to the cards answered that day, at most [AnkiStatsReader.HISTORY_DAYS] days back.
 * [buttons] and [introduced] are keyed by the window in days (1, 7, 30, 365); a button list is [Again, Hard, Good, Easy].
 * [forecast] has 30 entries: cards due in 1..30 days. [historyCheckedAt] is 0 until a history pass has finished.
 */
data class AnkiStats(
    val pkg: String,
    val fetchedAt: Long,
    val today: String,
    val perDay: Map<String, Int>,
    val buttons: Map<Int, List<Int>>,
    val introduced: Map<Int, Int>,
    val counts: Counts,
    val dueNow: Int,
    val forecast: List<Int>,
    val retentionAnswered: Int,
    val retentionAgain: Int,
    val historyCheckedAt: Long,
) {
    fun studied(day: String): Int = perDay[day] ?: 0
    fun buttonsIn(days: Int): List<Int> = buttons[days] ?: listOf(0, 0, 0, 0)
    fun newIn(days: Int): Int = introduced[days] ?: 0
    val dueTomorrow: Int get() = forecast.firstOrNull() ?: 0
    val dueNext7: Int get() = forecast.take(7).sum()

    /** Percent of the cards answered in the last 30 days (not new then) that you didn't forget; null when there were none. */
    val retention30: Int? get() = retentionPercent(retentionAnswered, retentionAgain)

    companion object {
        /** 1 - again / answered as a whole percent; null without any answered card. */
        fun retentionPercent(answered: Int, again: Int): Int? {
            if (answered <= 0) return null
            val passed = (answered - again.coerceIn(0, answered)).toDouble()
            return Math.round(100.0 * passed / answered).toInt()
        }
    }
}

/**
 * Builds [AnkiStats] from a `count` function (one Anki search -> number of matching cards). A QUICK pass reads everything
 * that changes often; a HISTORY pass fills the per-day counts of days 7..364 (only the missing ones, or all of them again
 * once a week, because a sync from another device can add old reviews).
 *
 * Anki's day starts at its rollover hour (default 4:00); `prop:rated=-k` is mapped to the date k days before AnkiGo's game
 * day, which starts at the same default 4:00. Good enough: only a user who changed one of the two hours sees a shifted day.
 */
class AnkiStatsReader(private val count: (String) -> Int) {
    /**
     * QUICK pass: counts, due, forecast, buttons, introduced, retention and the per-day counts of the last [QUICK_DAYS] days.
     * [prev] is kept (per-day history) only when it came from the same AnkiDroid package; days older than 365 are dropped.
     */
    fun quick(prev: AnkiStats?, pkg: String, today: String, now: Long): AnkiStats {
        val t = LocalDate.parse(today)
        val base = prev?.takeIf { it.pkg == pkg }
        val perDay = HashMap(base?.perDay ?: emptyMap())
        for (k in 0 until QUICK_DAYS) perDay[t.minusDays(k.toLong()).toString()] = count(AnkiQueries.ratedOn(k))
        val counts = Counts(
            new = count(AnkiQueries.NEW), learning = count(AnkiQueries.LEARNING), relearning = count(AnkiQueries.RELEARNING),
            young = count(AnkiQueries.YOUNG), mature = count(AnkiQueries.MATURE),
            suspended = count(AnkiQueries.SUSPENDED), buried = count(AnkiQueries.BURIED),
        )
        val dueNow = count(AnkiQueries.DUE_NOW)
        val buttons = WINDOWS.associateWith { d -> (1..4).map { b -> count(AnkiQueries.rated(d, b)) } }
        val introduced = WINDOWS.associateWith { d -> count(AnkiQueries.introduced(d)) }
        val answered = count(AnkiQueries.RETENTION_ANSWERED)
        val again = count(AnkiQueries.RETENTION_AGAIN)
        val forecast = (1..FORECAST_DAYS).map { count(AnkiQueries.due(it)) }
        return AnkiStats(
            pkg = pkg, fetchedAt = now, today = today, perDay = trim(perDay, t), buttons = buttons, introduced = introduced,
            counts = counts, dueNow = dueNow, forecast = forecast, retentionAnswered = answered, retentionAgain = again,
            historyCheckedAt = base?.historyCheckedAt ?: 0L,
        )
    }

    /** Does [s] still need a HISTORY pass: never finished one, a day of 7..364 is missing, or the last full check is over a week old. */
    fun historyNeeded(s: AnkiStats, now: Long): Boolean = s.historyCheckedAt == 0L || historyDays(s, now).isNotEmpty()

    /**
     * HISTORY pass, recent days first. [onProgress] gets the result so far after every [PROGRESS_EVERY] days (so the
     * heatmap fills in while it runs); [AnkiStats.historyCheckedAt] is set only on the returned, finished result.
     */
    fun history(s: AnkiStats, now: Long, onProgress: (AnkiStats) -> Unit = {}): AnkiStats {
        val t = LocalDate.parse(s.today)
        val days = historyDays(s, now)
        val perDay = HashMap(s.perDay)
        var done = 0
        for (k in days) {
            perDay[t.minusDays(k.toLong()).toString()] = count(AnkiQueries.ratedOn(k))
            if (++done % PROGRESS_EVERY == 0 && done < days.size) onProgress(s.copy(perDay = trim(perDay, t)))
        }
        return s.copy(perDay = trim(perDay, t), historyCheckedAt = now)
    }

    /**
     * A whole refresh: QUICK, then HISTORY when it is needed. [publish] gets every result (stats, history still running):
     * the quick result first, progress while the history pass runs, the final result last. Exceptions from [count] go to
     * the caller; results published before that stay valid.
     */
    fun refresh(prev: AnkiStats?, pkg: String, today: String, now: () -> Long, publish: (AnkiStats, Boolean) -> Unit): AnkiStats {
        val quick = quick(prev, pkg, today, now())
        val needed = historyNeeded(quick, now())
        publish(quick, needed)
        if (!needed) return quick
        val full = history(quick, now()) { publish(it, true) }
        publish(full, false)
        return full
    }

    /** Which k (days ago) of 7..364 the history pass has to read: the missing ones, or all when the last full check is over a week old. */
    private fun historyDays(s: AnkiStats, now: Long): List<Int> {
        val t = LocalDate.parse(s.today)
        val all = (QUICK_DAYS until HISTORY_DAYS).toList()
        val stale = s.historyCheckedAt > 0 && now - s.historyCheckedAt > RECHECK_MS
        return if (stale) all else all.filter { t.minusDays(it.toLong()).toString() !in s.perDay }
    }

    private fun trim(perDay: Map<String, Int>, today: LocalDate): Map<String, Int> {
        val oldest = today.minusDays((HISTORY_DAYS - 1).toLong()).toString()
        val newest = today.toString()
        return perDay.filterKeys { it in oldest..newest }.toSortedMap()
    }

    companion object {
        const val QUICK_DAYS = 7
        const val HISTORY_DAYS = 365
        const val FORECAST_DAYS = 30
        const val PROGRESS_EVERY = 40
        const val RECHECK_MS = 7L * 24 * 3_600_000
        /** The windows (in days) of the button and "introduced" counts; Anki's `rated:n` takes up to 365. */
        val WINDOWS = listOf(1, 7, 30, 365)
    }
}

/** [AnkiStats] <-> JSON for the cache file. Decoding never throws: anything unreadable or from another version is null. */
object AnkiStatsCodec {
    private const val VERSION = 1

    fun encode(s: AnkiStats): String = JSONObject().apply {
        put("v", VERSION)
        put("pkg", s.pkg); put("fetchedAt", s.fetchedAt); put("today", s.today); put("historyCheckedAt", s.historyCheckedAt)
        put("perDay", JSONObject().also { o -> s.perDay.toSortedMap().forEach { (d, n) -> o.put(d, n) } })
        put("buttons", JSONObject().also { o -> TreeMap(s.buttons).forEach { (d, l) -> o.put(d.toString(), JSONArray(l)) } })
        put("introduced", JSONObject().also { o -> TreeMap(s.introduced).forEach { (d, n) -> o.put(d.toString(), n) } })
        val c = s.counts
        put("counts", JSONArray(listOf(c.new, c.learning, c.relearning, c.young, c.mature, c.suspended, c.buried)))
        put("dueNow", s.dueNow)
        put("forecast", JSONArray(s.forecast))
        put("retention", JSONArray(listOf(s.retentionAnswered, s.retentionAgain)))
    }.toString()

    fun decode(text: String): AnkiStats? = try {
        val o = JSONObject(text)
        if (o.getInt("v") != VERSION) null else {
            val counts = o.getJSONArray("counts").ints(7)
            val forecast = o.getJSONArray("forecast").ints(AnkiStatsReader.FORECAST_DAYS)
            val retention = o.getJSONArray("retention").ints(2)
            val perDay = o.getJSONObject("perDay").let { p -> p.keys().asSequence().associateWith { p.getInt(it) } }
            if (perDay.keys.any { runCatching { LocalDate.parse(it) }.isFailure }) null else {
                AnkiStats(
                    pkg = o.getString("pkg"), fetchedAt = o.getLong("fetchedAt"), today = LocalDate.parse(o.getString("today")).toString(),
                    perDay = perDay.toSortedMap(),
                    buttons = o.getJSONObject("buttons").let { b -> b.keys().asSequence().associate { it.toInt() to b.getJSONArray(it).ints(4) } },
                    introduced = o.getJSONObject("introduced").let { n -> n.keys().asSequence().associate { it.toInt() to n.getInt(it) } },
                    counts = Counts(counts[0], counts[1], counts[2], counts[3], counts[4], counts[5], counts[6]),
                    dueNow = o.getInt("dueNow"), forecast = forecast,
                    retentionAnswered = retention[0], retentionAgain = retention[1],
                    historyCheckedAt = o.getLong("historyCheckedAt"),
                )
            }
        }
    } catch (_: Exception) {
        null
    }

    /** The array as a list of exactly [size] ints, else an exception (caught by decode). */
    private fun JSONArray.ints(size: Int): List<Int> {
        require(length() == size) { "expected $size numbers" }
        return (0 until size).map { getInt(it) }
    }
}

/** Time AnkiGo measured itself (the answer time of reviews done in AnkiGo): the AnkiDroid API has no review log. */
object AnkiGoTime {
    /** One answer counts at most this long (the widget measures from "show" to the tap, which can be hours). */
    const val MAX_ANSWER_MS = 10L * 60_000

    /** The game day of an instant: a day starts at [startHour] (phone time zone). */
    fun dayKey(epochMs: Long, startHour: Int, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).minusHours(startHour.coerceIn(0, 23).toLong()).toLocalDate().toString()

    /** Sum of [days] game days ending with [today] (inclusive). */
    fun lastDays(perDay: Map<String, Long>, today: String, days: Int): Long {
        val t = runCatching { LocalDate.parse(today) }.getOrNull() ?: return 0
        return (0 until days).sumOf { perDay[t.minusDays(it.toLong()).toString()] ?: 0L }
    }
}

/**
 * ms spent per game day from Local's reviews.csv lines `epochMillis,nid,ord,ease,ms,source`. [dayKey] maps an instant to its
 * game day. Lines that can't be read (also a half-written last line) are skipped; one answer counts at most [AnkiGoTime.MAX_ANSWER_MS].
 */
fun timeInAnkiGo(lines: Sequence<String>, dayKey: (Long) -> String): Map<String, Long> {
    val out = HashMap<String, Long>()
    for (line in lines) {
        val p = line.split(',')
        if (p.size < 5) continue
        val at = p[0].trim().toLongOrNull() ?: continue
        val ms = p[4].trim().toLongOrNull() ?: continue
        if (at <= 0 || ms < 0) continue
        val day = try { dayKey(at) } catch (_: Exception) { continue }
        out[day] = (out[day] ?: 0L) + minOf(ms, AnkiGoTime.MAX_ANSWER_MS)
    }
    return out
}
