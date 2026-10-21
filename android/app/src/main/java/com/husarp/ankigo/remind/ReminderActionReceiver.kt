package com.husarp.ankigo.remind

import android.app.Activity
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.husarp.ankigo.Report
import com.husarp.ankigo.ui.Dest
import com.husarp.ankigo.ui.Nav
import java.time.ZoneId

/** The buttons of a reminder notification. */
enum class NoticeAction(val label: String, val intentAction: String?) {
    /** Opens AnkiGo on the Study tab (a trampoline Activity: it also takes the notification away). */
    STUDY_NOW("Study now", null),

    /** The same reminder again in an hour ([Muted.snooze]). */
    SNOOZE("In 1 hour", "com.husarp.ankigo.remind.SNOOZE"),

    /** No more reminders of its topic today ([Muted.notToday]). */
    NOT_TODAY("Not today", "com.husarp.ankigo.remind.NOT_TODAY"),
}

/** Which buttons and which screen a notification of each kind has (pure). */
object ReminderActions {
    /** Study notices: Study now, In 1 hour, Not today. Streak and daily goal: Study now, Not today. The shop news has none (a tap opens the Shop). */
    fun forKind(kind: ReminderKind): List<NoticeAction> = when (kind) {
        ReminderKind.CARDS_DUE, ReminderKind.OWN_GOAL -> listOf(NoticeAction.STUDY_NOW, NoticeAction.SNOOZE, NoticeAction.NOT_TODAY)
        ReminderKind.STREAK, ReminderKind.DAILY_GOAL -> listOf(NoticeAction.STUDY_NOW, NoticeAction.NOT_TODAY)
        ReminderKind.SHOP -> emptyList()
    }

    /** Where a tap on the notification (and Study now) goes: the Shop for the shop news, else the Study tab. */
    fun opensShop(kind: ReminderKind) = kind == ReminderKind.SHOP

    const val EXTRA_KIND = "kind"

    fun kindOf(name: String?): ReminderKind? = ReminderKind.entries.firstOrNull { it.name == name }
}

/**
 * "In 1 hour" and "Not today" from a notification: writes it into [Muted], takes the notification away and sets the alarm
 * again (a snoozed reminder has its own time). Not exported: only AnkiGo's own PendingIntents reach it.
 */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = ReminderActions.kindOf(intent.getStringExtra(ReminderActions.EXTRA_KIND)) ?: return
        val app = context.applicationContext
        com.husarp.ankigo.Report.log("REMIND", "notification action ${intent.action?.substringAfterLast('.')} for $kind")   // R13 Part E
        val now = System.currentTimeMillis()
        try {
            when (NoticeAction.entries.firstOrNull { it.intentAction == intent.action }) {
                NoticeAction.SNOOZE -> ReminderState.updateMuted(app) { it.snooze(kind, now) }
                NoticeAction.NOT_TODAY -> {
                    val day = ReminderRules.gameDate(now, ZoneId.systemDefault(), ReminderFacts.load(app).dayStartHour).toString()
                    ReminderState.updateMuted(app) { it.notToday(kind, day) }
                }
                else -> return
            }
            app.getSystemService(NotificationManager::class.java)?.cancel(ReminderNotifier.id(kind))
            ReminderScheduler.rearm(app)
        } catch (e: Exception) {
            Report.fail("REMIND", "notification button ${intent.action}", e)
        }
    }
}

/**
 * No-UI trampoline behind a tap on the notification and "Study now": takes the notification away (an action button does not
 * dismiss it by itself), opens the Study tab (the Shop for the shop news) and finishes. Not exported, out of recents.
 * An Activity, not a receiver: Android 12+ does not let a receiver started from a notification open screens.
 */
class ReminderOpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = ReminderActions.kindOf(intent.getStringExtra(ReminderActions.EXTRA_KIND))
        try {
            if (kind != null) getSystemService(NotificationManager::class.java)?.cancel(ReminderNotifier.id(kind))
            val dest = if (kind != null && ReminderActions.opensShop(kind)) Dest.SHOP else Dest.STUDY
            startActivity(Nav.intent(this, dest).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        } catch (e: Exception) {
            Report.fail("REMIND", "opening AnkiGo from a reminder", e)
        }
        finish()
    }
}
