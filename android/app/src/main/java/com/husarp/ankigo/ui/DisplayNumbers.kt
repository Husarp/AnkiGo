package com.husarp.ankigo.ui

import java.util.Locale

object DisplayNumbers {
    fun hp(current: Int, max: Int) = "${current.coerceAtLeast(0)} / ${max.coerceAtLeast(0)}"
    /** The HP number of a battle card: "61 / 61 HP". */
    fun hpLabel(current: Int, max: Int) = "${hp(current, max)} HP"
    fun xp(current: Int, needed: Int) = if (needed <= 0) "XP MAX" else "XP ${current.coerceAtLeast(0)} / $needed"
    fun size(bytes: Long): String = when {
        bytes < 0 -> "Size unknown"
        bytes < 1_000_000 -> "${bytes.coerceAtLeast(0) / 1000} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    }
    fun progress(done: Long, total: Long, elapsedMs: Long): String {
        val speed = done.coerceAtLeast(0) * 1000 / elapsedMs.coerceAtLeast(1)
        val amounts = if (total >= 1_000_000) String.format(Locale.US, "%.1f / %.1f MB", done / 1_000_000.0, total / 1_000_000.0)
            else "${size(done)} / ${size(total)}"
        return "$amounts · ${size(speed)}/s"
    }
}
