package com.husarp.ankigo.ui

import android.content.Context

/** Independent of the app accent; the AnkiDroid option keeps white text in both themes. */
enum class ShowAnswerColor(val label: String, val fill: Int?) {
    DEFAULT("Default", null), // AnkiDroid common/android/src/main/res/values/theme_dark.xml: showAnswerButtonBackground
    ANKIDROID("AnkiDroid dark blue", 0xFF22333F.toInt());
    companion object {
        fun get(ctx: Context): ShowAnswerColor = entries.firstOrNull {
            it.name == ctx.getSharedPreferences("study", 0).getString("showAnswerColor", null)
        } ?: DEFAULT
        fun put(ctx: Context, value: ShowAnswerColor) {
            ctx.getSharedPreferences("study", 0).edit().putString("showAnswerColor", value.name).apply()
        }
    }
}
