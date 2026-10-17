package com.husarp.ankigo.remind

/**
 * What the buttons of a reminder notification asked for (pure; [ReminderState] keeps it between ticks):
 * - "In 1 hour": that reminder kind may come back once its time has come ([snoozed], epoch ms per kind);
 * - "Not today": no more reminders of that topic on that game day ([offDay], game day "yyyy-MM-dd" per kind).
 * A topic is what belongs together: the two study reminders (cards due, own goal), the two goal reminders (streak, daily goal), the shop.
 */
data class Muted(
    val snoozed: Map<ReminderKind, Long> = emptyMap(),
    val offDay: Map<ReminderKind, String> = emptyMap(),
) {
    /** [kind] comes back [minutes] after [now] (a second press moves the time). */
    fun snooze(kind: ReminderKind, now: Long, minutes: Int = SNOOZE_MINUTES): Muted =
        copy(snoozed = snoozed + (kind to now + minutes * 60_000L))

    /** Silences the whole topic of [kind] for game day [day]; what was snoozed in that topic is forgotten. */
    fun notToday(kind: ReminderKind, day: String): Muted {
        val topic = topic(kind)
        return Muted(snoozed - topic.toSet(), offDay + topic.associateWith { day })
    }

    /** True when "Not today" silenced [kind] on game day [day]. */
    fun silenced(kind: ReminderKind, day: String): Boolean = offDay[kind] == day

    /** The snoozed time of [kind] has come, and not longer ago than [ReminderRules.CATCH_UP_MS] (an old snooze is dropped, not made up for). */
    fun snoozeReady(kind: ReminderKind, now: Long): Boolean =
        snoozed[kind]?.let { now >= it && now - it <= ReminderRules.CATCH_UP_MS } == true

    /** [kind] is snoozed and its time is still ahead of [now]: it must not come back through the normal rules before that. */
    fun snoozing(kind: ReminderKind, now: Long): Boolean = snoozed[kind]?.let { it > now } == true

    /** The earliest snoozed time still ahead of [now], or null. */
    fun nextSnooze(now: Long): Long? = snoozed.values.filter { it > now }.minOrNull()

    /** Forgets the snoozes of [kinds] (used up, or the reminder was posted anyway). */
    fun without(kinds: Collection<ReminderKind>): Muted = if (kinds.isEmpty()) this else copy(snoozed = snoozed - kinds.toSet())

    /**
     * Forgets the snoozes of [kinds] that are still the ones in [seen] (what a tick looked at). A snooze the player pressed
     * meanwhile has another time and stays, so a running tick can't swallow it.
     */
    fun consume(kinds: Collection<ReminderKind>, seen: Muted): Muted {
        val gone = kinds.filter { snoozed[it] != null && snoozed[it] == seen.snoozed[it] }
        return without(gone)
    }

    /** Drops snoozes that are over [ReminderRules.CATCH_UP_MS] past their time and "Not today" marks of other game days. */
    fun tidy(now: Long, today: String): Muted = Muted(
        snoozed.filterValues { now - it <= ReminderRules.CATCH_UP_MS },
        offDay.filterValues { it == today },
    )

    /** `S:KIND:epochMillis;O:KIND:day` */
    fun encode(): String =
        (snoozed.map { "S:${it.key.name}:${it.value}" } + offDay.map { "O:${it.key.name}:${it.value}" }).joinToString(";")

    companion object {
        const val SNOOZE_MINUTES = 60

        /** What belongs to the same topic as [kind]. */
        fun topic(kind: ReminderKind): List<ReminderKind> = when (kind) {
            ReminderKind.CARDS_DUE, ReminderKind.OWN_GOAL -> listOf(ReminderKind.CARDS_DUE, ReminderKind.OWN_GOAL)
            ReminderKind.STREAK, ReminderKind.DAILY_GOAL -> listOf(ReminderKind.STREAK, ReminderKind.DAILY_GOAL)
            ReminderKind.SHOP -> listOf(ReminderKind.SHOP)
        }

        /** Bad or unknown entries are skipped. */
        fun decode(s: String?): Muted {
            val snoozed = LinkedHashMap<ReminderKind, Long>()
            val off = LinkedHashMap<ReminderKind, String>()
            for (part in s.orEmpty().split(';')) {
                val f = part.split(':', limit = 3)
                if (f.size != 3) continue
                val kind = ReminderKind.entries.firstOrNull { it.name == f[1] } ?: continue
                when (f[0]) {
                    "S" -> f[2].toLongOrNull()?.let { snoozed[kind] = it }
                    "O" -> if (f[2].isNotEmpty()) off[kind] = f[2]
                }
            }
            return Muted(snoozed, off)
        }
    }
}
