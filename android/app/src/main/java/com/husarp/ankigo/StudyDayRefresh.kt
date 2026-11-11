package com.husarp.ankigo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.time.Instant
import java.time.ZoneId

internal fun nextStudyDayAt(now: Long, hour: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
    val local = Instant.ofEpochMilli(now).atZone(zone)
    val hours = hour.coerceIn(0, 23).toLong()
    // Match Game.dayKey's hour offset on both sides of a daylight-saving transition.
    var next = local.toLocalDate().atStartOfDay(zone).plusHours(hours)
    if (next.toInstant().toEpochMilli() <= now) next = local.toLocalDate().plusDays(1).atStartOfDay(zone).plusHours(hours)
    return next.toInstant().toEpochMilli()
}

/** The game day uses a local rollover hour, including days shortened/lengthened by a time-zone change. */
object StudyDayRefresh {
    internal var clock: () -> Long = { System.currentTimeMillis() }
    private val main = Handler(Looper.getMainLooper())
    private var wake: Runnable? = null
    private var armedAt: Long? = null
    private const val HOUR = "rolloverHour"

    fun schedule(ctx: Context, force: Boolean = false) {
        val app = ctx.applicationContext
        val prefs = app.getSharedPreferences("spike", 0)
        val hour = GameRepo.state?.settings?.dayStartHour ?: prefs.getInt(HOUR, 4)
        if (prefs.getInt(HOUR, -1) != hour) prefs.edit().putInt(HOUR, hour).apply()
        val now = clock()
        val at = nextStudyDayAt(now, hour)
        if (!force && armedAt == at) return
        armedAt = at
        wake?.let { main.removeCallbacks(it) }
        wake = Runnable { armedAt = null; refresh(app) }.also { main.postDelayed(it, at - now) }
        try {
            val alarm = app.getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(app, 590, Intent(app, StudyDayReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            // The foreground callback is punctual. Android may defer the background alarm in Doze.
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: Exception) { Report.fail("DAY", "scheduling the study day", e) }
    }

    fun refresh(ctx: Context, done: () -> Unit = {}) {
        val app = ctx.applicationContext
        val wasDay = GameRepo.state?.day
        schedule(app, force = true)
        GameRepo.load(app) { status ->
            try {
                if (status == GameRepo.Status.READY) {
                    val game = GameRepo.game
                    if (game != null && game.state.day != game.dayKey()) GameRepo.act(app) { it.tick() }
                }
                if (wasDay == null || GameRepo.state?.day != wasDay) GameRepo.widgetRefresh(app)
                schedule(app, force = true)
            } finally { done() }
        }
    }
}

/** Alarm, reboot, app update and clock changes also work while no Activity is open. */
class StudyDayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        StudyDayRefresh.refresh(context) { pending.finish() }
    }
}
