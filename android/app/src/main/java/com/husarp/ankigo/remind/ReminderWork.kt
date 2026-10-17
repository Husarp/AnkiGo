package com.husarp.ankigo.remind

import android.content.Context
import com.husarp.ankigo.Anki
import com.husarp.ankigo.Report
import java.time.ZoneId
import java.util.concurrent.locks.ReentrantLock

/** One reminder check (runs on a background thread from the alarm tick): reads the facts, asks the rules, posts, remembers. */
object ReminderWork {
    private val lock = ReentrantLock()

    /**
     * Returns what was posted. Nothing is posted without the notification permission; AnkiDroid problems skip the card reminders quietly.
     * [guard] is the receiver's time box: once it has expired nothing is posted or saved (the receiver is already finished).
     * The notification buttons ("In 1 hour", "Not today") can come in while AnkiDroid is being read: the posting step looks at the
     * mute state again and holds [ReminderState.withMutedLock], so a button pressed during the read wins, and the snoozes used up
     * afterwards are only the ones this tick saw.
     */
    fun tick(ctx: Context, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault(), guard: TickGuard = TickGuard.NONE): List<Notice> {
        // a tick still stuck in a slow query: this one doesn't queue behind it (the next alarm tries again)
        if (!lock.tryLock()) return emptyList()
        try {
            return run(ctx, now, zone, guard)
        } finally {
            lock.unlock()
        }
    }

    private fun run(ctx: Context, now: Long, zone: ZoneId, guard: TickGuard): List<Notice> {
        val prefs = ReminderStore.load(ctx)
        if (!prefs.anyOn) {
            ReminderScheduler.cancel(ctx)
            return emptyList()
        }
        if (!ReminderPermission.canPost(ctx)) return emptyList()
        val pick = if (prefs.dueOn && prefs.dueMode == DueMode.APP_PICKS) ReminderState.computePick(ctx, now, zone) else ReminderRules.DEFAULT_PICK_MINUTE
        val before = ReminderState.loadSent(ctx)
        val facts = ReminderFacts.load(ctx)
        val muted = ReminderState.loadMuted(ctx) // what this tick decides with; the buttons may change it while AnkiDroid is read
        val notices = ReminderRules.decide(now, zone, prefs, facts, { readDue(ctx, prefs, now, guard) }, before, pick, muted)
        val today = ReminderRules.gameDate(now, zone, facts.dayStartHour).toString()
        return guard.whileLive {
            val posted = ArrayList<Notice>()
            var log = before
            ReminderState.withMutedLock {
                val wanted = ReminderRules.stillWanted(notices, now, today, ReminderState.loadMuted(ctx))
                for (n in wanted) {
                    if (!ReminderNotifier.post(ctx, n)) continue
                    posted += n
                    log = log.with(n.kind, now)
                }
            }
            for (n in posted) Report.ok("REMIND", "posted ${n.kind}: ${n.title}")
            if (log != before) ReminderState.saveSent(ctx, log)
            // snoozes that were looked at (or whose reminder was posted anyway) are used up, but only the ones seen; old marks go
            val used = ReminderRules.snoozesUsed(now, zone, prefs, muted) + posted.map { it.kind }
            ReminderState.updateMuted(ctx) { it.consume(used, muted).tidy(now, today) }
            posted.toList()
        } ?: emptyList()
    }

    /** Due counts per deck, and today's count for the own goal. Null (and a line in the Report, once in a while) when AnkiDroid can't be read. */
    private fun readDue(ctx: Context, prefs: ReminderPrefs, now: Long, guard: TickGuard): DueSnapshot? {
        if (guard.expired) return null
        Anki.load(ctx)
        if (!Anki.hasPermission(ctx)) {
            ReminderState.problem(ctx, "Reminders: no permission to read AnkiDroid, card reminders skipped", now)
            return null
        }
        val decks = try {
            Anki.decks(ctx).map { DueDeck(it.id, it.name, it.learn, it.review) }
        } catch (e: Exception) {
            ReminderState.problem(ctx, "Reminders: could not read the AnkiDroid decks (${e.javaClass.simpleName}: ${e.message}), card reminders skipped", now)
            return null
        }
        var reviewed: Int? = null
        var introduced: Int? = null
        if (guard.expired) return null // the receiver is gone: no more questions to AnkiDroid
        if (prefs.goalOn) {
            try {
                if (Anki.studySupported(ctx)) {
                    if (prefs.goalKind == GoalKind.REVIEWS) reviewed = Anki.countCards(ctx, "rated:1") else introduced = Anki.countCards(ctx, "introduced:1")
                }
            } catch (e: Exception) {
                ReminderState.problem(ctx, "Reminders: could not count today's cards (${e.javaClass.simpleName}: ${e.message}), own goal skipped", now)
            }
        }
        return DueSnapshot(decks, reviewed, introduced)
    }
}
