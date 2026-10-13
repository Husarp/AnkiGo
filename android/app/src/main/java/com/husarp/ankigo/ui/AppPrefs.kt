package com.husarp.ankigo.ui

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.CopyOnWriteArrayList

/** What AnkiGo shows when you leave its battle screen: nothing, picture-in-picture, or the floating battle window. Only one at a time. */
enum class LeaveMode { OFF, PIP, FLOAT }

/**
 * Small app/UI preferences that are not part of the game save (SharedPreferences "ankigo_ui"):
 * leave mode, floating screens, whether the overlay permission was explained, floating window bounds.
 * Main thread or any thread; reads are cheap.
 */
object AppPrefs {
    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("ankigo_ui", Context.MODE_PRIVATE)

    /** Default: picture-in-picture (needs no extra permission). */
    fun leaveMode(ctx: Context): LeaveMode =
        LeaveMode.entries.firstOrNull { it.name == prefs(ctx).getString("leaveMode", null) } ?: LeaveMode.PIP

    fun setLeaveMode(ctx: Context, mode: LeaveMode) = prefs(ctx).edit().putString("leaveMode", mode.name).apply()

    /** Team, Pokédex, Shop and Settings open as floating windows while AnkiGo is in split screen (needs the overlay permission). */
    fun floatScreens(ctx: Context): Boolean = prefs(ctx).getBoolean("floatScreens", true)

    fun setFloatScreens(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("floatScreens", on).apply()

    /** True once the "Display over other apps" explanation was shown (it is asked only once; Settings can ask again). */
    fun overlayExplained(ctx: Context): Boolean = prefs(ctx).getBoolean("overlayExplained", false)

    fun setOverlayExplained(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("overlayExplained", v).apply()

    /** Saved position and size of the floating window [key] in px: x, y, width, height; null = never moved. */
    fun bounds(ctx: Context, key: String): IntArray? {
        val s = prefs(ctx).getString("bounds.$key", null) ?: return null
        val p = s.split(',').mapNotNull { it.trim().toIntOrNull() }
        return if (p.size == 4) p.toIntArray() else null
    }

    fun setBounds(ctx: Context, key: String, x: Int, y: Int, w: Int, h: Int) =
        prefs(ctx).edit().putString("bounds.$key", "$x,$y,$w,$h").apply()
}

/**
 * Whether one of AnkiGo's own screens is in front (the battle screen, full, split or picture-in-picture, or Team,
 * Pokédex, Shop, Settings). Each screen marks itself in onStart/onStop; the floating battle window hides itself while
 * any is visible, so it never covers AnkiGo itself. Listeners run on the main thread.
 */
object BattleScreen {
    private val owners: MutableSet<Any> = java.util.Collections.newSetFromMap(java.util.IdentityHashMap())
    private val listeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    val visible: Boolean get() = owners.isNotEmpty()

    /** [owner] (an Activity) is visible / no longer visible. Calling it twice with the same value changes nothing. */
    fun setVisible(owner: Any, v: Boolean) {
        val before = visible
        if (v) owners += owner else owners -= owner
        if (visible != before) listeners.forEach { it(visible) }
    }

    fun addListener(l: (Boolean) -> Unit) { listeners.addIfAbsent(l) }
    fun removeListener(l: (Boolean) -> Unit) { listeners.remove(l) }
}
