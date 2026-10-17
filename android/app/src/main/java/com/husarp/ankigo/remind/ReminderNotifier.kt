package com.husarp.ankigo.remind

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import com.husarp.ankigo.R
import com.husarp.ankigo.Report
import com.husarp.ankigo.ui.Ui
import java.io.File

/**
 * Posts a [Notice] (pick 4A in the compact form of 4C): the title is the count, the collapsed view shows the first line, the
 * expanded one all of them (one per deck, the goal line) with a goal bar; the large icon is the main Pokémon's cached picture
 * (none cached: none shown). Buttons: [ReminderActions.forKind]. One id per kind: a new notice replaces the old one.
 */
object ReminderNotifier {
    private const val CHANNEL = "reminders"
    private const val ID_BASE = 8510
    private const val REQUEST_BASE = 8530
    private const val ICON_DP = 64

    fun id(kind: ReminderKind) = ID_BASE + kind.ordinal

    /** True when it was handed to Android. Without the permission nothing is posted (no crash). */
    fun post(ctx: Context, n: Notice): Boolean {
        if (!ReminderPermission.canPost(ctx)) return false
        return try {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Study and Pokémon reminders you switched on in Settings."
                },
            )
            nm.notify(id(n.kind), build(ctx, n, ReminderFacts.load(ctx).mainSprite))
            true
        } catch (e: Exception) {
            Report.fail("REMIND", "posting the ${n.kind} reminder", e)
            false
        }
    }

    /** The notification for [n]; [spritePath] = the main Pokémon's cached picture (null or unreadable: no large icon). */
    internal fun build(ctx: Context, n: Notice, spritePath: String?): Notification {
        val b = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_swords)
            .setColor(Ui.c(ctx, R.color.ag_mauve))
            .setContentTitle(n.title)
            .setContentIntent(open(ctx, n.kind, 0))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
        val lines = n.lines
        lines.firstOrNull()?.let { b.setContentText(it) }
        if (lines.size > 1) b.setStyle(Notification.InboxStyle().also { s -> lines.forEach { s.addLine(it) } })
        if (n.showBar) b.setProgress(n.goalTarget, n.goalDone.coerceIn(0, n.goalTarget), false)
        sprite(ctx, spritePath)?.let { b.setLargeIcon(it) }
        for ((i, a) in ReminderActions.forKind(n.kind).withIndex()) {
            val pi = if (a.intentAction == null) open(ctx, n.kind, i + 1) else button(ctx, n.kind, a, i + 1)
            b.addAction(Notification.Action.Builder(Icon.createWithResource(ctx, icon(a)), a.label, pi).build())
        }
        return b.build()
    }

    private fun icon(a: NoticeAction) = when (a) {
        NoticeAction.STUDY_NOW -> R.drawable.ic_study
        NoticeAction.SNOOZE -> R.drawable.ic_pause
        NoticeAction.NOT_TODAY -> R.drawable.ic_close
    }

    private fun request(kind: ReminderKind, slot: Int) = REQUEST_BASE + kind.ordinal * 4 + slot

    /** The tap on the notification and "Study now": the trampoline Activity (cancels the notification, opens the screen). */
    private fun open(ctx: Context, kind: ReminderKind, slot: Int): PendingIntent = PendingIntent.getActivity(
        ctx, request(kind, slot),
        Intent(ctx, ReminderOpenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(ReminderActions.EXTRA_KIND, kind.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** "In 1 hour" / "Not today": a broadcast to AnkiGo's own, not exported [ReminderActionReceiver]. */
    private fun button(ctx: Context, kind: ReminderKind, a: NoticeAction, slot: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx, request(kind, slot),
        Intent(ctx, ReminderActionReceiver::class.java).setAction(a.intentAction).putExtra(ReminderActions.EXTRA_KIND, kind.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The picture at [ICON_DP], scaled up crisp (pixel art); null when there is none or it can't be read. */
    private fun sprite(ctx: Context, path: String?): Bitmap? {
        if (path.isNullOrEmpty()) return null
        return try {
            val f = File(path)
            if (!f.isFile) return null
            val bmp = BitmapFactory.decodeFile(path) ?: return null
            val px = (ICON_DP * ctx.resources.displayMetrics.density).toInt()
            val longest = maxOf(bmp.width, bmp.height)
            if (longest <= 0 || longest == px) bmp
            else Bitmap.createScaledBitmap(bmp, maxOf(1, bmp.width * px / longest), maxOf(1, bmp.height * px / longest), false)
        } catch (e: Exception) {
            null
        }
    }
}
