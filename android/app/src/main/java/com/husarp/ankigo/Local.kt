package com.husarp.ankigo

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** AnkiGo's own record of reviews done here (fallback for stats / game). */
object Local {
    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun todayCount(ctx: Context): Int {
        val p = ctx.getSharedPreferences("spike", 0)
        return if (p.getString("countDay", "") == today()) p.getInt("count", 0) else 0
    }

    /** One line per review done in AnkiGo: `epochMillis,nid,ord,ease,ms,source` (ms = the answer time AnkiGo measured). */
    fun reviewsFile(ctx: Context) = File(ctx.filesDir, "reviews.csv")

    fun recordReview(ctx: Context, nid: Long, ord: Int, ease: Int, ms: Long, source: String) {
        val p = ctx.getSharedPreferences("spike", 0)
        p.edit().putString("countDay", today()).putInt("count", todayCount(ctx) + 1).apply()
        try {
            reviewsFile(ctx).appendText("${System.currentTimeMillis()},$nid,$ord,$ease,$ms,$source\n")
        } catch (_: Exception) {
        }
    }
}
