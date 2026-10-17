package com.husarp.ankigo.ui

import android.content.Context
import com.husarp.ankigo.data.Sprites

// R4-X1 item 5: what Settings -> Downloads remembers, so a finished download shows a green "Downloaded ✓" instead of the
// Download button. The rule is pure (unit-tested); the prefs part is a thin wrapper.

object DownloadsDone {
    const val PICTURES = "dl_done_pictures"
    const val ANIMATIONS = "dl_done_animations"

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
    fun mark(ctx: Context, key: String, gens: Set<Int>) {
        val p = prefs(ctx)
        p.edit().putString(key, encode(decode(p.getString(key, null)) + gens)).apply()
    }

    fun isDone(ctx: Context, key: String, wanted: Set<Int>): Boolean = complete(decode(prefs(ctx).getString(key, null)), wanted)

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
    fun clear(ctx: Context) = prefs(ctx).edit().remove(PICTURES).remove(ANIMATIONS).apply()
}
