package com.husarp.ankigo.remind

import android.content.Context
import android.content.SharedPreferences
import com.husarp.ankigo.Local
import com.husarp.ankigo.Report
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** What the tick remembers between runs (SharedPreferences "reminders_state"): the sent log, snooze / "Not today", the "app picks" hour, the last problem. */
object ReminderState {
    private const val FILE = "reminders_state"
    private const val PROBLEM_REPEAT_MS = 6L * 3_600_000

    private fun prefs(ctx: Context): SharedPreferences = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun loadSent(ctx: Context): SentLog = SentLog.decode(prefs(ctx).getString("sent", null))

    fun saveSent(ctx: Context, log: SentLog) = prefs(ctx).edit().putString("sent", log.encode()).apply()

    private val mutedLock = Any()

    fun loadMuted(ctx: Context): Muted = Muted.decode(prefs(ctx).getString("muted", null))

    /** Runs [block] while no notification button can change the mute state (they wait for it): the tick's "check again, then post". Keep it short. */
    fun <T> withMutedLock(block: () -> T): T = synchronized(mutedLock) { block() }

    /** Changes what the notification buttons asked for (snooze, "Not today"). One at a time: the tick and the buttons both write it. */
    fun updateMuted(ctx: Context, change: (Muted) -> Muted) = synchronized(mutedLock) {
        val before = loadMuted(ctx)
        val after = change(before)
        if (after != before) prefs(ctx).edit().putString("muted", after.encode()).apply()
    }

    /** The "app picks" minute last worked out (the default until the first tick did). Cheap: reads no answer files. */
    fun pickMinute(ctx: Context): Int = prefs(ctx).getInt("pickMinute", ReminderRules.DEFAULT_PICK_MINUTE)

    /** The "app picks" minute, worked out from the answer files once per calendar day and kept. */
    fun computePick(ctx: Context, now: Long, zone: ZoneId): Int {
        val p = prefs(ctx)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString()
        if (p.getString("pickDay", null) == today) return pickMinute(ctx)
        val minute = ReminderRules.appPickedMinute(AnswerTimes.read(ctx, now), now, zone)
        p.edit().putString("pickDay", today).putInt("pickMinute", minute).apply()
        return minute
    }

    /** Writes a problem to the Report, but the same problem only once every 6 hours (the tick runs every 15 minutes). */
    fun problem(ctx: Context, message: String, now: Long = System.currentTimeMillis()) {
        val p = prefs(ctx)
        if (p.getString("problem", null) == message && now - p.getLong("problemAt", 0) < PROBLEM_REPEAT_MS) return
        p.edit().putString("problem", message).putLong("problemAt", now).apply()
        Report.fail("REMIND", message)
    }
}

/** Answer times (epoch ms) for "app picks": reviews.csv (answered in AnkiGo) and live-time.csv (answered in AnkiDroid), column 0 of both. */
object AnswerTimes {
    const val LIVE_FILE = "live-time.csv"

    /** The first column of each line that is a time in [from, to]; lines that can't be read are skipped. */
    fun parse(lines: Sequence<String>, from: Long, to: Long): List<Long> = lines.mapNotNull { line ->
        line.substringBefore(',').trim().toLongOrNull()?.takeIf { it in from..to }
    }.toList()

    /** The answer times of the last [ReminderRules.PICK_DAYS] days. A missing file counts as empty. */
    fun read(ctx: Context, now: Long): List<Long> {
        val from = now - ReminderRules.PICK_DAYS * 24 * 3_600_000L
        return readFile(Local.reviewsFile(ctx), from, now) + readFile(File(ctx.filesDir, LIVE_FILE), from, now)
    }

    private fun readFile(f: File, from: Long, to: Long): List<Long> = try {
        if (f.exists()) f.useLines { parse(it, from, to) } else emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}
