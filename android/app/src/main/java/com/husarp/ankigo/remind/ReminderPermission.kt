package com.husarp.ankigo.remind

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** The notification permission (Android 13+ asks for it; POST_NOTIFICATIONS is in the manifest). */
object ReminderPermission {
    const val REQUEST_CODE = 8501

    /** Pure rule: Android 13+ (API 33) without the permission must ask first. */
    fun mustAsk(sdk: Int, granted: Boolean): Boolean = sdk >= 33 && !granted

    private fun granted(ctx: Context) =
        Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** True when the app still has to ask the user (the settings screen does that when a reminder is switched on). */
    fun needed(ctx: Context): Boolean = mustAsk(Build.VERSION.SDK_INT, granted(ctx))

    /** Shows Android's permission dialog if it is needed; the answer comes to the activity's onRequestPermissionsResult. */
    fun ask(activity: Activity) {
        if (needed(activity)) activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
    }

    /** Can a notification be shown right now (permission given and notifications not switched off for the app)? */
    fun canPost(ctx: Context): Boolean = canPostOverride?.invoke(ctx) ?: (granted(ctx) &&
        (ctx.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() ?: false))

    /** Render tests only: the screenshot renderer has no notification service, so a test answers for it. */
    internal var canPostOverride: ((Context) -> Boolean)? = null
}
