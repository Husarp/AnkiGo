package com.husarp.ankigo.remind

/** The five reminders. The two study kinds share the minimum gap and the daily cap. */
enum class ReminderKind(val study: Boolean) {
    CARDS_DUE(true), OWN_GOAL(true), STREAK(false), DAILY_GOAL(false), SHOP(false)
}

/** One notification that was posted: which reminder and when (epoch ms). */
data class SentEntry(val kind: ReminderKind, val at: Long)

/**
 * What the reminders sent in the last days, so the rules can keep their promises (once a day, minimum gap, daily cap)
 * without asking Android. Only entries of the last [KEEP_MS] are kept.
 */
data class SentLog(val entries: List<SentEntry> = emptyList()) {
    fun with(kind: ReminderKind, at: Long): SentLog =
        SentLog((entries + SentEntry(kind, at)).filter { it.at > at - KEEP_MS }.sortedBy { it.at })

    /** Without entries from the future (the clock was set back): they must not block anything. */
    fun upTo(now: Long): SentLog = if (entries.all { it.at <= now }) this else SentLog(entries.filter { it.at <= now })

    fun last(kind: ReminderKind): Long? = entries.filter { it.kind == kind }.maxOfOrNull { it.at }

    fun lastStudy(): Long? = entries.filter { it.kind.study }.maxOfOrNull { it.at }

    /** `KIND:epochMillis;KIND:epochMillis` */
    fun encode(): String = entries.joinToString(";") { "${it.kind.name}:${it.at}" }

    companion object {
        const val KEEP_MS = 3L * 24 * 3_600_000

        /** Bad or unknown entries are skipped. */
        fun decode(s: String?): SentLog = SentLog(
            s.orEmpty().split(';').mapNotNull { part ->
                val i = part.indexOf(':')
                if (i <= 0) return@mapNotNull null
                val kind = ReminderKind.entries.firstOrNull { it.name == part.substring(0, i) } ?: return@mapNotNull null
                val at = part.substring(i + 1).toLongOrNull() ?: return@mapNotNull null
                SentEntry(kind, at)
            }.sortedBy { it.at },
        )
    }
}
