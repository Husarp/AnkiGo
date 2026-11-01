package com.husarp.ankigo.ui

import android.content.Context

/** Stable saved names; older saves default to the soft rectangle. */
enum class TabBarStyle(val label: String) {
    A3("Bold name"), C1("Sliding box"), D3("Top line"), E1("Name on selected");
    companion object {
        fun get(ctx: Context): TabBarStyle = entries.firstOrNull {
            it.name == ctx.applicationContext.getSharedPreferences("ankigo_ui", 0).getString("tabBarStyle", null)
        } ?: C1
        fun put(ctx: Context, style: TabBarStyle) {
            AppPrefs.putLook(ctx, "tabBarStyle", style.name)
        }
    }
}
