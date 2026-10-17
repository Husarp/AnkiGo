package com.husarp.ankigo.remind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.husarp.ankigo.Report

/**
 * The alarm tick: sets the next alarm first (so a slow or failing check can't stop the chain), then checks and posts on a worker
 * thread with a hard deadline ([TickDeadline.LIMIT_MS]). When the deadline passes the receiver is finished without posting
 * (a stuck AnkiDroid query can't keep it open), after setting the next alarm again.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_TICK) return
        val app = context.applicationContext
        ReminderScheduler.rearm(app)
        val pending = goAsync()
        TickDeadline.run(
            TickDeadline.LIMIT_MS,
            work = { guard -> ReminderWork.tick(app, guard = guard) },
            onError = { e -> Report.fail("REMIND", "reminder check", e) },
            onEnd = { timedOut ->
                try {
                    // once in a while, not every tick
                    if (timedOut) ReminderState.problem(app, "Reminders: the check took longer than ${TickDeadline.LIMIT_MS / 1000} s (AnkiDroid slow?), nothing was posted this time")
                    ReminderScheduler.rearm(app) // the sent log changed: the next delay may too
                } finally {
                    pending.finish()
                }
            },
        )
    }
}

/** Sets the alarm again when Android forgot it: after reboot, app update, clock or time zone change. */
class ReminderRearmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED ->
                ReminderScheduler.rearm(context)
        }
    }
}
