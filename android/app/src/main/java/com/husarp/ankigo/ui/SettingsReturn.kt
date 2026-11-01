package com.husarp.ankigo.ui

import android.content.Context
import android.os.SystemClock

/** Short-lived section memory, shared by recreated Settings pages. */
internal object SettingsReturn {
    internal var now: () -> Long = { SystemClock.elapsedRealtime() }
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings_return", 0)
    internal fun withinMinute(left: Long, now: Long) = left >= 0 && now >= left && now - left < 60_000
    fun recent(ctx: Context) = withinMinute(prefs(ctx).getLong("left", -1), now())
    fun section(ctx: Context): SettingsSection? = if (recent(ctx))
        SettingsSection.entries.firstOrNull { it.name == prefs(ctx).getString("section", null) } else null
    fun remember(ctx: Context, section: SettingsSection?) {
        prefs(ctx).edit().putString("section", section?.name).putLong("left", now()).apply()
    }
}
