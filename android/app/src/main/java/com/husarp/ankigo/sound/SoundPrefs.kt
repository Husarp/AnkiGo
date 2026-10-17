package com.husarp.ankigo.sound

import android.content.Context
import android.content.SharedPreferences

/** The sound settings (their own file, not part of the game save). Keys and defaults: [SoundDefaults]. */
object SoundPrefs {
    private fun prefs(ctx: Context): SharedPreferences = ctx.applicationContext.getSharedPreferences("ankigo_sound", Context.MODE_PRIVATE)

    // ---- 7 groups on 3 volume channels (pick 84B) ----

    fun groupOn(ctx: Context, g: SoundGroup): Boolean {
        val p = prefs(ctx)
        return SoundDefaults.groupOn(g) { k -> if (p.contains(k)) p.getBoolean(k, true) else null }
    }
    fun setGroupOn(ctx: Context, g: SoundGroup, on: Boolean) {
        val p = prefs(ctx)
        val e = p.edit()
        if (g == SoundGroup.BATTLE) { // MOMENTS follows the old "battle" value while its own key is absent: keep it where it is
            SoundDefaults.momentsToPersist { k -> if (p.contains(k)) p.getBoolean(k, true) else null }
                ?.let { e.putBoolean(SoundDefaults.groupKey(SoundGroup.MOMENTS), it) }
        }
        e.putBoolean(SoundDefaults.groupKey(g), on).apply()
    }

    /** 0..100. Defaults: buttons 40, battle 70, cries 70 (the old single volume, when it was set). */
    fun volume(ctx: Context, ch: SoundChannel): Int {
        val p = prefs(ctx)
        return SoundDefaults.volume(ch) { k -> if (p.contains(k)) p.getInt(k, 0) else null }
    }
    fun setVolume(ctx: Context, ch: SoundChannel, pct: Int) = prefs(ctx).edit().putInt(SoundDefaults.volumeKey(ch), pct.coerceIn(0, 100)).apply()

    // ---- the old single switches and slider (thin aliases until the grouped settings replace them) ----

    fun battleSounds(ctx: Context) = groupOn(ctx, SoundGroup.BATTLE)
    fun setBattleSounds(ctx: Context, on: Boolean) = setGroupOn(ctx, SoundGroup.BATTLE, on)

    fun cries(ctx: Context) = groupOn(ctx, SoundGroup.CRIES)
    fun setCries(ctx: Context, on: Boolean) = setGroupOn(ctx, SoundGroup.CRIES, on)

    /** The old one slider: the battle volume. Setting it moves the battle and the cries volume together, as it did before. */
    fun volume(ctx: Context) = volume(ctx, SoundChannel.BATTLE)
    fun setVolume(ctx: Context, pct: Int) { setVolume(ctx, SoundChannel.BATTLE, pct); setVolume(ctx, SoundChannel.CRIES, pct) }

    fun quietOnCardAudio(ctx: Context) = prefs(ctx).getBoolean("quietCard", true)
    fun setQuietOnCardAudio(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("quietCard", on).apply()
}
