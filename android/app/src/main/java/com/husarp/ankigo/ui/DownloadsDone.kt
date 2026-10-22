package com.husarp.ankigo.ui

import android.content.Context
import android.content.SharedPreferences
import com.husarp.ankigo.data.Sprites

// R4-X1 item 5: what Settings -> Downloads remembers, so a finished download shows a green "Downloaded ✓" instead of the
// Download button. The rule is pure (unit-tested); the prefs part is a thin wrapper.

object DownloadsDone {
    const val PICTURES = "dl_done_pictures"
    /** PLAN 167: the "Download everything" button finished for these generations (it replaced the animations button). */
    const val EVERYTHING = "dl_done_everything"

    /** The old animations button's memory: nothing reads it any more (that phone just sees the new button), [clear] still wipes it. */
    private const val LEGACY_ANIMATIONS = "dl_done_animations"

    /** True when a finished download covered every generation in [wanted] (and there is something to cover). */
    fun complete(saved: Set<Int>, wanted: Set<Int>): Boolean = wanted.isNotEmpty() && saved.containsAll(wanted)

    /**
     * X3-3: true when every picture in [nums] (the species numbers of the enabled generations) is already on the phone ([has]), and there
     * is something to cover. Strict on purpose (the first-run check lets 5 % go): a green "Downloaded ✓" disables the button.
     */
    fun picturesComplete(nums: Collection<Int>, has: (Int) -> Boolean): Boolean = nums.isNotEmpty() && nums.all(has)

    fun encode(gens: Set<Int>): String = gens.sorted().joinToString(",")

    fun decode(s: String?): Set<Int> = s.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("firstrun", Context.MODE_PRIVATE)

    /** Remembers that a download of [key] finished (not stopped, not failed) for [gens]. */
    fun mark(ctx: Context, key: String, gens: Set<Int>) = markIn(prefs(ctx), key, gens)

    /** PLAN 167: the "Download everything" button was pressed and nothing is missing: done for [gens], and "Download pictures as needed" goes on. */
    fun markNothingMissing(ctx: Context, gens: Set<Int>) = markNothingMissingIn(prefs(ctx), gens)

    internal fun markNothingMissingIn(p: SharedPreferences, gens: Set<Int>) {
        com.husarp.ankigo.data.Sprites.setEnabledIn(p, true)
        markIn(p, EVERYTHING, gens)
    }

    fun isDone(ctx: Context, key: String, wanted: Set<Int>): Boolean = isDoneIn(prefs(ctx), key, wanted)

    // the same on a given preferences file (a fake one in the unit tests)
    internal fun markIn(p: SharedPreferences, key: String, gens: Set<Int>) {
        p.edit().putString(key, encode(decode(p.getString(key, null)) + gens)).apply()
    }

    internal fun isDoneIn(p: SharedPreferences, key: String, wanted: Set<Int>): Boolean = complete(decode(p.getString(key, null)), wanted)

    /**
     * X3-3: for someone who downloaded the pictures before this memory existed. When the Downloads page opens and the pictures of [gens]
     * ([nums]) are all in the cache already, remembers them as downloaded (the cache is cheap to ask: one file check per species).
     * The battle animations are left to a real download: some species have none upstream, so a gap in the cache proves nothing.
     */
    fun adoptPictures(ctx: Context, nums: Collection<Int>, gens: Set<Int>) {
        if (isDone(ctx, PICTURES, gens)) return
        if (picturesComplete(nums) { Sprites.staticFile(ctx, it).isFile }) mark(ctx, PICTURES, gens)
    }

    /** The picture cache was cleared: nothing counts as downloaded any more. */
    fun clear(ctx: Context) = clearIn(prefs(ctx))

    internal fun clearIn(p: SharedPreferences) = p.edit().remove(PICTURES).remove(EVERYTHING).remove(LEGACY_ANIMATIONS).apply()
}
