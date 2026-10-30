package com.husarp.ankigo.ui

import android.content.Context
import android.content.res.Configuration

/** Independent text settings; card zoom retains the existing study preference. */
object TextSizes {
    fun get(ctx: Context, kind: String): Int = if (kind == "card")
        ctx.getSharedPreferences("study", 0).getInt("zoom", 100).coerceIn(80, 150)
        else ctx.getSharedPreferences("textSizes", 0).getInt(kind, 100).coerceIn(80, 150)

    fun put(ctx: Context, kind: String, value: Int) {
        val prefs = ctx.getSharedPreferences(if (kind == "card") "study" else "textSizes", 0)
        prefs.edit().putInt(if (kind == "card") "zoom" else kind, value.coerceIn(80, 150)).apply()
        AppLook.textChanged()
    }

    /** Replace the app multiplier, retaining Android's font preference and interface density. */
    fun battleContext(base: Context): Context {
        val app = get(base, "app")
        val battle = get(base, "battle")
        if (app == battle) return base
        val cfg = Configuration(base.resources.configuration)
        cfg.fontScale = cfg.fontScale / (app / 100f) * (battle / 100f)
        return android.view.ContextThemeWrapper(base, base.theme).apply { applyOverrideConfiguration(cfg) }
    }
}

enum class DeckListLook(val label: String) {
    TODAY("Today"), BRANCHES("A · Connected branches"), FAMILIES("B · One card per family");
    companion object {
        fun get(ctx: Context): DeckListLook = entries.firstOrNull {
            it.name == ctx.getSharedPreferences("study", 0).getString("deckListLook", null)
        } ?: TODAY
        fun put(ctx: Context, value: DeckListLook) {
            ctx.getSharedPreferences("study", 0).edit().putString("deckListLook", value.name).apply()
        }
    }
}
