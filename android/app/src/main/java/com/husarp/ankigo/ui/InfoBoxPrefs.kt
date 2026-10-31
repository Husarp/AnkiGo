package com.husarp.ankigo.ui

import android.content.Context
import android.content.SharedPreferences

/**
 * "Bigger info boxes" (R19-A / PLAN 276, Settings → Battle → Info box style): both battle info boxes start [BIG] times today's full
 * size, but only where the arena has room ([factor]): the tall Battle tab. The box rule is unchanged (a gap from the sprites, boxes
 * shrink before the Pokémon, the text floor, the frame's safe area), so a panel without room keeps today's boxes. Off by default.
 * Saved in "ankigo_ui" under its own key (not AppPrefs); every open arena follows the setting live ([watch]). The rule is pure so
 * the JVM tests cover it.
 */
object InfoBoxPrefs {
    const val WIDTH_KEY = "wideInfoBoxes"
    fun wide(ctx: Context): Boolean = prefs(ctx).getBoolean(WIDTH_KEY, false)
    fun setWide(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(WIDTH_KEY, on).apply()
    fun width(normal: Float, wide: Boolean): Float = normal * if (wide) 1.4f else 1f

    const val KEY = "bigInfoBoxes"
    private const val FILE = "ankigo_ui"

    /** How much bigger the roomy box scale starts (today's full size, the arena's k0, times this). Picked from the shots (R19-A). */
    const val BIG = 1.3f

    /** The arena has room from this on-screen size (dp): a phone's Battle tab (360 x 640 and up) has, the Study panel's presets, the strip, picture-in-picture and the floating window do not. */
    const val ROOM_H = 440f
    const val ROOM_W = 340f

    private fun prefs(ctx: Context): SharedPreferences = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun big(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY, false)

    fun setBig(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(KEY, on).apply()

    /** Calls [listener] when the setting changes. The prefs hold the listener weakly: the caller keeps it. */
    fun watch(ctx: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) = prefs(ctx).registerOnSharedPreferenceChangeListener(listener)

    fun unwatch(ctx: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) = prefs(ctx).unregisterOnSharedPreferenceChangeListener(listener)

    /**
     * What the roomy box scale is multiplied by in an arena that is [onScreenW] x [onScreenH] dp on screen: [BIG] when the setting is on and
     * the arena has room, else 1 (nothing changes). The arena's own size, not the screen's: a small window never has room.
     */
    fun factor(on: Boolean, onScreenW: Float, onScreenH: Float): Float = if (on && onScreenW >= ROOM_W && onScreenH >= ROOM_H) BIG else 1f
}
