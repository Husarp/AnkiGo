package com.husarp.ankigo.remind

import com.husarp.ankigo.game.Items
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** One deck as AnkiDroid reports it: cards still to learn and to review today (new cards are not "due"). */
data class DueDeck(val id: Long, val name: String, val learn: Int, val review: Int) {
    val due: Int get() = learn.coerceAtLeast(0) + review.coerceAtLeast(0)
}

/**
 * What the tick found out from AnkiDroid. [reviewedToday] = cards reviewed today (`rated:1`), [newToday] = new cards
 * started today (`introduced:1`); null = could not be counted.
 */
data class DueSnapshot(val decks: List<DueDeck>, val reviewedToday: Int? = null, val newToday: Int? = null)

/**
 * One notification the rules decided to post, with the facts it was built from. [title] and [text] are the compact notification
 * words ([ReminderText]); [text] holds one line per row, the collapsed notification shows the first.
 */
data class Notice(
    val kind: ReminderKind,
    val title: String,
    val text: String,
    /** CARDS_DUE: cards due in all counted decks, and the names of the counted decks that have some (most first). */
    val dueCount: Int = 0,
    val deckNames: List<String> = emptyList(),
    /** STREAK: days in the streak. */
    val streakDays: Int = 0,
    /** OWN_GOAL / DAILY_GOAL / STREAK: done so far and the target. */
    val goalDone: Int = 0,
    val goalTarget: Int = 0,
    /** OWN_GOAL: counts new cards instead of reviews. */
    val newCards: Boolean = false,
    /** SHOP: display names of the items worth having. */
    val itemNames: List<String> = emptyList(),
) {
    /** The rows under the title, one per line of [text]. */
    val lines: List<String> get() = text.split('\n').filter { it.isNotBlank() }

    /** A goal progress bar goes under the text (the streak notice has none: it only says how many reviews are missing). */
    val showBar: Boolean get() = goalTarget > 0 && kind != ReminderKind.STREAK
}

/**
 * The reminder rules (PLAN 85), pure: given the clock, the settings, the game facts, what AnkiDroid says is due and
 * what was already sent, which notifications go out now? "Today" is always the game day (day-start hour of the game).
 */
object ReminderRules {
    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 24 * HOUR_MS

    /** The tick period: the alarm never waits longer than this. */
    const val TICK_MS = 15L * 60_000
    private const val MIN_ARM_MS = 30_000L

    /** A time-of-day reminder still goes out up to this long after its time (phone off, Doze); later it is skipped. */
    const val CATCH_UP_MS = 3 * HOUR_MS

    /** "App picks": the reminder time without enough history (and the minimum number of answers that count as history). */
    const val DEFAULT_PICK_MINUTE = 18 * 60
    const val PICK_MIN_ANSWERS = 5
    const val PICK_DAYS = 14

    // ---- days and times ----

    /** The game day of an instant: a day starts at [startHour] (phone time zone). */
    fun gameDate(at: Long, zone: ZoneId, startHour: Int): LocalDate =
        Instant.ofEpochMilli(at).atZone(zone).minusHours(startHour.coerceIn(0, 23).toLong()).toLocalDate()

    /** When [minute] (minutes of the day, as on the wall clock) happens inside game day [day]. */
    fun timeInGameDay(day: LocalDate, minute: Int, startHour: Int, zone: ZoneId): Long {
        val m = minute.coerceIn(0, 1439)
        val date = if (m >= startHour.coerceIn(0, 23) * 60) day else day.plusDays(1)
        return ZonedDateTime.of(date, LocalTime.of(m / 60, m % 60), zone).toInstant().toEpochMilli()
    }

    /** Is [at] inside the quiet hours? They wrap over midnight (22:00 to 08:00); from == to means no quiet hours. */
    fun inQuiet(prefs: ReminderPrefs, at: Long, zone: ZoneId): Boolean {
        if (!prefs.quietOn || prefs.quietFrom == prefs.quietTo) return false
        val t = Instant.ofEpochMilli(at).atZone(zone).let { it.hour * 60 + it.minute }
        return if (prefs.quietFrom < prefs.quietTo) t in prefs.quietFrom until prefs.quietTo else t >= prefs.quietFrom || t < prefs.quietTo
    }

    // ---- decks ----

    /**
     * The decks whose counts add up. Parent counts already contain their children, so "all decks" are the top-level
     * decks; of chosen decks, one whose ancestor is chosen too is left out (it is inside that ancestor's count).
     */
    fun countedDecks(prefs: ReminderPrefs, decks: List<DueDeck>): List<DueDeck> {
        val byName = decks.associateBy { it.name }
        fun hasAncestor(d: DueDeck, test: (DueDeck) -> Boolean): Boolean {
            val parts = d.name.split("::")
            if (parts.size < 2) return false
            var name = parts[0]
            for (i in 0 until parts.size - 1) {
                if (i > 0) name += "::" + parts[i]
                if (byName[name]?.let(test) == true) return true
            }
            return false
        }
        return if (prefs.allDecks) decks.filter { !hasAncestor(it) { true } }
        else decks.filter { it.id in prefs.deckIds && !hasAncestor(it) { a -> a.id in prefs.deckIds } }
    }

    // ---- the decision ----

    /**
     * Which notifications to post at [now]. [due] is asked at most once, and only when a study reminder passed all its
     * checks that don't need AnkiDroid (so the tick does not wake AnkiDroid for nothing); null = AnkiDroid not reachable
     * (the card reminders are skipped quietly, the game ones still work). [pickMinute] = the minute of the day "app picks"
     * chose (see [appPickedMinute]). [muted] = what the notification buttons asked for: "Not today" silences a topic for the game
     * day; a snoozed study reminder waits for its hour (nothing of that kind comes before) and then comes back (it ignores the time,
     * the gap and the daily cap, but not the quiet hours or the minimum count). The result has at most one notice per kind.
     */
    fun decide(
        now: Long,
        zone: ZoneId,
        prefs: ReminderPrefs,
        facts: ReminderFacts,
        due: () -> DueSnapshot?,
        sentLog: SentLog,
        pickMinute: Int = DEFAULT_PICK_MINUTE,
        muted: Muted = Muted(),
    ): List<Notice> {
        if (!prefs.anyOn || inQuiet(prefs, now, zone)) return emptyList()
        val start = facts.dayStartHour
        val today = gameDate(now, zone, start)
        val todayKey = today.toString()
        val yesterdayKey = today.minusDays(1).toString()
        fun dayOf(at: Long) = gameDate(at, zone, start).toString()
        var sent = sentLog.upTo(now)
        fun sentToday(kind: ReminderKind) = sent.last(kind)?.let { dayOf(it) == todayKey } == true
        /** The given time of day has come (not later than [CATCH_UP_MS] ago) and was not itself inside the quiet hours. */
        fun timeIsNow(minute: Int): Boolean {
            val at = timeInGameDay(today, minute, start, zone)
            return now >= at && now - at <= CATCH_UP_MS && !inQuiet(prefs, at, zone)
        }
        fun studyOpen(): Boolean {
            val studyToday = sent.entries.count { it.kind.study && dayOf(it.at) == todayKey }
            if (studyToday >= prefs.dailyCap) return false
            val last = sent.lastStudy()
            return last == null || now - last >= prefs.minGapHours * HOUR_MS
        }
        fun cadenceOver(kind: ReminderKind) = sent.last(kind).let { it == null || now - it >= prefs.dueEveryHours * HOUR_MS }
        // "Not today" silences a kind; while its snooze is still ahead it waits for that time and can't fire the normal way
        fun off(kind: ReminderKind) = muted.silenced(kind, todayKey) || muted.snoozing(kind, now)
        fun again(kind: ReminderKind) = muted.snoozeReady(kind, now)

        var asked = false
        var snapshot: DueSnapshot? = null
        fun snap(): DueSnapshot? {
            if (!asked) { asked = true; snapshot = due() }
            return snapshot
        }

        val out = ArrayList<Notice>()
        fun post(n: Notice) { out += n; sent = sent.with(n.kind, now) }

        // Cards due
        if (prefs.dueOn && !off(ReminderKind.CARDS_DUE)) {
            val snoozed = again(ReminderKind.CARDS_DUE)
            if (snoozed || studyOpen()) {
                val timing = snoozed || when (prefs.dueMode) {
                    DueMode.FIXED -> !sentToday(ReminderKind.CARDS_DUE) && timeIsNow(prefs.dueTime)
                    DueMode.APP_PICKS -> !sentToday(ReminderKind.CARDS_DUE) && timeIsNow(pickMinute)
                    DueMode.EVERY_HOURS -> cadenceOver(ReminderKind.CARDS_DUE)
                }
                if (timing) snap()?.let { s ->
                    val counted = countedDecks(prefs, s.decks)
                    val total = counted.sumOf { it.due }
                    if (total >= prefs.dueMin) post(cardsDue(total, counted, facts, todayKey, yesterdayKey))
                }
            }
        }

        // Own reminder goal: keeps going on the "every X hours" cadence until today's count reaches the goal
        if (prefs.goalOn && !off(ReminderKind.OWN_GOAL) && (again(ReminderKind.OWN_GOAL) || (studyOpen() && cadenceOver(ReminderKind.OWN_GOAL)))) {
            snap()?.let { s ->
                val done = if (prefs.goalKind == GoalKind.REVIEWS) s.reviewedToday else s.newToday
                if (done != null && done < prefs.goalCount) post(ownGoal(done, prefs.goalCount, prefs.goalKind == GoalKind.NEW_CARDS))
            }
        }

        if (facts.valid) {
            val reviews = if (facts.day == todayKey) facts.reviewsToday else 0
            val goalReachedToday = facts.lastGoalDay == todayKey
            val goal = facts.goalFor(todayKey) // the facts may be from an earlier day: today's weekday goal

            // Streak about to end: the goal was reached yesterday and not yet today
            if (prefs.streakOn && !off(ReminderKind.STREAK) && facts.streakDays > 0 && facts.lastGoalDay == yesterdayKey && goal > 0 &&
                reviews < goal && !sentToday(ReminderKind.STREAK) && timeIsNow(prefs.streakTime)
            ) post(streak(facts.streakDays, reviews, goal))

            // Daily goal not reached yet
            if (prefs.dailyGoalOn && !off(ReminderKind.DAILY_GOAL) && goal > 0 && reviews < goal && !goalReachedToday &&
                !sentToday(ReminderKind.DAILY_GOAL) && timeIsNow(prefs.dailyGoalTime)
            ) post(dailyGoal(reviews, goal, aliveStreak(facts, todayKey, yesterdayKey)))

            // Shop news: once per game day, as soon as the quiet hours allow
            if (prefs.shopOn && !off(ReminderKind.SHOP) && !sentToday(ReminderKind.SHOP)) {
                val stock = when (facts.day) {
                    todayKey -> facts.shopToday
                    yesterdayKey -> facts.shopTomorrow // the app was not opened today: the new day's stock is what yesterday's facts foresaw
                    else -> emptyList()
                }
                if (stock.isNotEmpty()) post(shop(stock))
            }
        }
        return out
    }

    // ---- the texts (compact, see ReminderText) ----

    /** The streak as the game counts it: the stored one while the goal was reached today or yesterday, else none. */
    private fun aliveStreak(facts: ReminderFacts, todayKey: String, yesterdayKey: String): Int =
        if (facts.valid && (facts.lastGoalDay == todayKey || facts.lastGoalDay == yesterdayKey)) facts.streakDays else 0

    private fun cardsDue(total: Int, counted: List<DueDeck>, facts: ReminderFacts, todayKey: String, yesterdayKey: String): Notice {
        val names = counted.filter { it.due > 0 }.sortedByDescending { it.due }
        val lines = ArrayList<String>()
        if (names.isEmpty()) lines += "Time for a study session." else lines += ReminderText.deckLines(names.map { it.name to it.due })
        var done = 0
        var target = 0
        if (facts.valid && facts.dailyGoal > 0) {
            done = if (facts.day == todayKey) facts.reviewsToday else 0
            target = facts.goalFor(todayKey)
            lines += ReminderText.goalLine(done, target, aliveStreak(facts, todayKey, yesterdayKey))
        }
        return Notice(
            ReminderKind.CARDS_DUE, ReminderText.cardsTitle(total), lines.joinToString("\n"),
            dueCount = total, deckNames = names.map { it.name }, goalDone = done, goalTarget = target,
        )
    }

    private fun ownGoal(done: Int, target: Int, newCards: Boolean) = Notice(
        ReminderKind.OWN_GOAL, ReminderText.ownGoalTitle((target - done).coerceAtLeast(1), newCards),
        ReminderText.ownGoalLine(done, target, newCards), goalDone = done, goalTarget = target, newCards = newCards,
    )

    private fun streak(days: Int, done: Int, target: Int) = Notice(
        ReminderKind.STREAK, ReminderText.streakTitle(days), ReminderText.streakLine((target - done).coerceAtLeast(1)),
        streakDays = days, goalDone = done, goalTarget = target,
    )

    private fun dailyGoal(done: Int, target: Int, streakDays: Int) = Notice(
        ReminderKind.DAILY_GOAL, ReminderText.dailyGoalTitle((target - done).coerceAtLeast(1)),
        ReminderText.dailyGoalLine(done, target, streakDays), goalDone = done, goalTarget = target,
    )

    private fun shop(items: List<String>): Notice {
        val names = items.map { Items.name(it) }
        return Notice(ReminderKind.SHOP, "New in the shop", ReminderText.shopLine(names), itemNames = names)
    }

    // ---- "app picks" ----

    /**
     * The minute of the day for "app picks": the start of the hour in which the answers of the last [PICK_DAYS] days
     * cluster most (the earliest of equally busy hours), or [DEFAULT_PICK_MINUTE] with fewer than [PICK_MIN_ANSWERS] answers.
     * [answerTimes] = epoch ms of answers (reviews.csv column 0 and live-time.csv column 0).
     */
    fun appPickedMinute(answerTimes: Collection<Long>, now: Long, zone: ZoneId): Int {
        val from = now - PICK_DAYS * DAY_MS
        val perHour = IntArray(24)
        var n = 0
        for (t in answerTimes) {
            if (t < from || t > now) continue
            perHour[Instant.ofEpochMilli(t).atZone(zone).hour]++
            n++
        }
        if (n < PICK_MIN_ANSWERS) return DEFAULT_PICK_MINUTE
        val best = perHour.max()
        return perHour.indexOfFirst { it == best } * 60
    }

    // ---- right before posting ----

    /**
     * The [notices] [decide] chose with an older mute state, minus those the player has silenced or snoozed since ([fresh] = the mute
     * state read just now, [today] = the game day key). The tick may have waited a long time for AnkiDroid: a "Not today" or an
     * "In 1 hour" pressed meanwhile must win over what was decided before.
     */
    fun stillWanted(notices: List<Notice>, now: Long, today: String, fresh: Muted): List<Notice> =
        notices.filter { !fresh.silenced(it.kind, today) && !fresh.snoozing(it.kind, now) }

    // ---- the alarm ----

    /**
     * How long until the next tick: [TICK_MS], or less when a time-of-day reminder (fixed or app-picked card reminder,
     * streak, daily goal) or a snoozed one ([muted]) comes sooner; never under 30 s. Null when no reminder is on (no alarm at all).
     */
    fun nextDelayMs(now: Long, zone: ZoneId, prefs: ReminderPrefs, pickMinute: Int = DEFAULT_PICK_MINUTE, muted: Muted = Muted()): Long? {
        if (!prefs.anyOn) return null
        val minutes = buildList {
            if (prefs.dueOn && prefs.dueMode == DueMode.FIXED) add(prefs.dueTime)
            if (prefs.dueOn && prefs.dueMode == DueMode.APP_PICKS) add(pickMinute)
            if (prefs.streakOn) add(prefs.streakTime)
            if (prefs.dailyGoalOn) add(prefs.dailyGoalTime)
        }
        var delay = TICK_MS
        val local = Instant.ofEpochMilli(now).atZone(zone)
        for (m in minutes) {
            val c = m.coerceIn(0, 1439)
            var at = ZonedDateTime.of(local.toLocalDate(), LocalTime.of(c / 60, c % 60), zone)
            if (!at.toInstant().isAfter(Instant.ofEpochMilli(now))) at = ZonedDateTime.of(local.toLocalDate().plusDays(1), LocalTime.of(c / 60, c % 60), zone)
            delay = minOf(delay, at.toInstant().toEpochMilli() - now)
        }
        muted.nextSnooze(now)?.let { delay = minOf(delay, it - now) } // "In 1 hour" comes back on time, not at the next 15-minute tick
        return delay.coerceIn(MIN_ARM_MS, TICK_MS)
    }

    /**
     * The snoozes this tick has dealt with: their time has come and it is not quiet (so [decide] looked at them), whether a notice
     * went out or not (below the minimum count, AnkiDroid unreachable). They are used up. Inside the quiet hours they wait.
     */
    fun snoozesUsed(now: Long, zone: ZoneId, prefs: ReminderPrefs, muted: Muted): Set<ReminderKind> =
        if (!prefs.anyOn || inQuiet(prefs, now, zone)) emptySet() else muted.snoozed.filterValues { it <= now }.keys
}
