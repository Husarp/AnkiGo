package com.husarp.ankigo.ui

import android.content.Context

/** Stable saved names; older saves default to the soft rectangle. */
enum class TabBarStyle(val label: String) {
    A3("A-3"), C1("C-1"), D3("D-3"), E1("E-1");
    companion object {
        fun get(ctx: Context): TabBarStyle = entries.firstOrNull {
            it.name == ctx.applicationContext.getSharedPreferences("ankigo_ui", 0).getString("tabBarStyle", null)
        } ?: C1
        fun put(ctx: Context, style: TabBarStyle) {
            AppPrefs.putLook(ctx, "tabBarStyle", style.name)
        }
    }
}
