package com.husarp.ankigo.overlay

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.husarp.ankigo.Report
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.showCard

/** The "Display over other apps" permission (needed for floating windows): check, open Android's page, explain once. */
object Overlay {
    /** True when AnkiGo may draw over other apps. */
    fun canDraw(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    /** Opens Android's "Display over other apps" page for AnkiGo (NEW_TASK when [ctx] isn't an Activity). */
    fun openSettings(ctx: Context) {
        val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
        if (ctx !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(i)
        } catch (e: ActivityNotFoundException) {
            Report.fail("overlay", "no overlay settings page", e)
        }
    }

    /**
     * Explains what the permission is for and that it can be turned off any time; "Allow" opens the settings page.
     * Marks the permission as explained (so [askOnce] never asks again).
     */
    fun ask(activity: Activity, reason: String) {
        AppPrefs.setOverlayExplained(activity, true)
        if (activity.isFinishing) return
        AlertDialog.Builder(activity)
            .setTitle("Show AnkiGo over other apps?")
            .setMessage(
                "$reason Android calls this “Display over other apps”. " +
                    "You can turn it off any time in Android's settings or in AnkiGo's Settings."
            )
            .setPositiveButton("Allow") { _, _ -> openSettings(activity) }
            .setNegativeButton("Not now", null)
            .showCard()
    }

    /**
     * Asks only once ever: permission missing and never explained -> [ask] and true; otherwise false at once
     * (the caller falls back to a normal screen).
     */
    fun askOnce(activity: Activity, reason: String): Boolean {
        if (canDraw(activity) || AppPrefs.overlayExplained(activity)) return false
        ask(activity, reason)
        return true
    }
}
