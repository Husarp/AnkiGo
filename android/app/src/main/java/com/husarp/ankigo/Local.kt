package com.husarp.ankigo

import android.content.Context
import java.io.File

/** AnkiGo's own record of reviews done here (fallback for stats / game). */
object Local {
    /** Reviews AnkiGo recorded today (local day), counted from today's lines of reviews.csv; no prefs write per answer. */
    fun todayCount(ctx: Context): Int = countToday(reviewsFile(ctx))

    /** Lines of [file] (first column = epoch ms) that fall on the local day of [now]; a missing file is 0. */
    internal fun countToday(file: File, now: Long = System.currentTimeMillis()): Int {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now; set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }
        val start = cal.timeInMillis
        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        val end = cal.timeInMillis
        return try {
            if (!file.isFile) 0 else file.useLines { ls -> ls.count { l -> l.substringBefore(',').toLongOrNull()?.let { it in start until end } == true } }
        } catch (_: Exception) { 0 }
    }

    /** One line per review done in AnkiGo: `epochMillis,nid,ord,ease,ms,source` (ms = the answer time AnkiGo measured). */
    fun reviewsFile(ctx: Context) = File(ctx.filesDir, "reviews.csv")

    fun recordReview(ctx: Context, nid: Long, ord: Int, ease: Int, ms: Long, source: String) {
        try {
            reviewsFile(ctx).appendText("${System.currentTimeMillis()},$nid,$ord,$ease,$ms,$source\n")
        } catch (_: Exception) {
        }
    }
}
