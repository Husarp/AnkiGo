package com.husarp.ankigo.ui

import android.content.Context

/**
 * "Pokémon movement" (R18-C / PLAN 247, Settings → Battle panel & windows): how often the battle Pokémon move while idle. Attacks, hits
 * and faints always play (they are `ArenaView` transforms); only the idle playback of the two arena sprites changes.
 *
 * [ALWAYS] = as before. [OFTEN] .. [RARELY]: an animated sprite plays its animation once, then holds its first frame for a pause
 * ([pauseMs], changed by up to 30 % at random for every pause so the two sides do not move in step), then plays once again; a still
 * picture's idle float follows the same pauses. [NEVER]: the first frame only, no float. Reduced motion (the system's animators off or
 * the app's own setting) starts at [NEVER] until the slider is moved. Saved in "ankigo_ui" under its own key (not AppPrefs); the rules
 * are pure so the JVM tests cover them.
 */
object MotionPrefs {
    const val ALWAYS = 0
    const val OFTEN = 1
    const val NOW_AND_THEN = 2
    const val RARELY = 3
    const val NEVER = 4

    /** The slider's steps, in order. */
    val LABELS = listOf("Always", "Often", "Now and then", "Rarely", "Never")
    const val LAST = NEVER

    /** The pause between two plays at each step, in ms ([ALWAYS] = no pause, [NEVER] = never plays again: -1). */
    private val PAUSE_MS = longArrayOf(0L, 2_000L, 5_000L, 12_000L, -1L)

    /** Every pause is its step's pause changed by up to this share (random, per pause). */
    const val JITTER = 0.30f

    private const val FILE = "ankigo_ui"
    private const val KEY = "pokemonMovement"

    /** [step] kept inside the slider. */
    fun clamp(step: Int): Int = step.coerceIn(ALWAYS, LAST)

    /** The step at the start: Never in reduced motion, Always elsewhere. */
    fun defaultStep(reduceMotion: Boolean): Int = if (reduceMotion) NEVER else ALWAYS

    /** The pause at [step] before the random change; 0 for Always; -1 for Never (no next play). */
    fun pauseMs(step: Int): Long = PAUSE_MS[clamp(step)]

    /** The pause to wait at [step], with [r] (0..1, a random number) deciding the change: base x (1 - [JITTER] .. 1 + [JITTER]). 0 / -1 stay as they are. */
    fun jitteredPauseMs(step: Int, r: Float): Long {
        val base = pauseMs(step)
        if (base <= 0L) return base
        val f = 1f + JITTER * (2f * r.coerceIn(0f, 1f) - 1f)
        return (base * f).toLong()
    }

    /** True when the idle motion plays at all at [step] (every step but Never). */
    fun moves(step: Int): Boolean = clamp(step) != NEVER

    /** True when the sprite pauses between plays ([OFTEN] .. [RARELY]). */
    fun pauses(step: Int): Boolean = clamp(step) in OFTEN..RARELY

    /** The saved step for [saved] (null = never chosen): the default for [reduceMotion] when nothing is saved. */
    fun resolve(saved: Int?, reduceMotion: Boolean): Int = if (saved == null) defaultStep(reduceMotion) else clamp(saved)

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The same test as `ArenaView.reduceMotion` / `SpriteView`: animators off or the app's own reduced-motion switch. */
    fun reduceMotion(ctx: Context): Boolean =
        !android.animation.ValueAnimator.areAnimatorsEnabled() || ctx.getSharedPreferences("study", 0).getBoolean("reduceMotion", false)

    /** The step now: what was chosen, else the default for reduced motion. */
    fun step(ctx: Context): Int {
        val p = prefs(ctx)
        return resolve(if (p.contains(KEY)) p.getInt(KEY, ALWAYS) else null, reduceMotion(ctx))
    }

    fun setStep(ctx: Context, step: Int) = prefs(ctx).edit().putInt(KEY, clamp(step)).apply()

    /** The slider label of [step]. */
    fun label(step: Int): String = LABELS[clamp(step)]
}
