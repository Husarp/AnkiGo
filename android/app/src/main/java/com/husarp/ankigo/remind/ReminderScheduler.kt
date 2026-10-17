package com.husarp.ankigo.remind

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.husarp.ankigo.Report
import java.time.ZoneId

/**
 * The reminder alarm: one inexact one-shot ([AlarmManager.setAndAllowWhileIdle], no exact-alarm permission) that is set
 * again every tick. Nothing switched on: no alarm. Call [rearm] when the app starts, after a settings change (ReminderStore
 * does that on save) and after reboot / time changes ([ReminderRearmReceiver]).
 */
object ReminderScheduler {
    const val ACTION_TICK = "com.husarp.ankigo.remind.TICK"
    private const val REQUEST = 8500

    private fun tickIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, REQUEST, Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun rearm(ctx: Context, now: Long = System.currentTimeMillis()) {
        val app = ctx.applicationContext
        try {
            val am = app.getSystemService(AlarmManager::class.java) ?: return
            val pi = tickIntent(app)
            val prefs = ReminderStore.load(app)
            val delay = ReminderRules.nextDelayMs(now, ZoneId.systemDefault(), prefs, ReminderState.pickMinute(app), ReminderState.loadMuted(app))
            if (delay == null) {
                am.cancel(pi)
                return
            }
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now + delay, pi)
        } catch (e: Exception) {
            Report.fail("REMIND", "setting the reminder alarm", e)
        }
    }

    fun cancel(ctx: Context) {
        val app = ctx.applicationContext
        try {
            app.getSystemService(AlarmManager::class.java)?.cancel(tickIntent(app))
        } catch (e: Exception) {
            Report.fail("REMIND", "cancelling the reminder alarm", e)
        }
    }
}
