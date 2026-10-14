package com.husarp.ankigo.sound

import android.content.Context
import android.content.SharedPreferences

/** The sound settings (their own file, not part of the game save). */
object SoundPrefs {
    private fun prefs(ctx: Context): SharedPreferences = ctx.applicationContext.getSharedPreferences("ankigo_sound", Context.MODE_PRIVATE)

    fun battleSounds(ctx: Context) = prefs(ctx).getBoolean("battle", true)
    fun setBattleSounds(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("battle", on).apply()

    fun cries(ctx: Context) = prefs(ctx).getBoolean("cries", true)
    fun setCries(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("cries", on).apply()

    /** 0..100, default 70. */
    fun volume(ctx: Context) = prefs(ctx).getInt("volume", 70).coerceIn(0, 100)
    fun setVolume(ctx: Context, pct: Int) = prefs(ctx).edit().putInt("volume", pct.coerceIn(0, 100)).apply()

    fun quietOnCardAudio(ctx: Context) = prefs(ctx).getBoolean("quietCard", true)
    fun setQuietOnCardAudio(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("quietCard", on).apply()
}
