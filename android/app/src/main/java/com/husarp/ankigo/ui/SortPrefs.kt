package com.husarp.ankigo.ui

import android.content.Context
import android.content.SharedPreferences

/** Remembers the sort choice (PLAN 48/55) separately for the Box, the Team picker and the Pokédex. Thin wrapper; the string is [SortSpec.encode]. */
object SortPrefs {
    enum class Where(val key: String) { BOX("sort.box"), TEAM_PICKER("sort.teamPicker"), DEX("sort.dex") }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("ankigo_ui", Context.MODE_PRIVATE)

    fun load(p: SharedPreferences, where: Where): SortSpec = SortSpec.decode(p.getString(where.key, null))
    fun save(p: SharedPreferences, where: Where, spec: SortSpec) { p.edit().putString(where.key, spec.encode()).apply() }

    fun load(ctx: Context, where: Where): SortSpec = load(prefs(ctx), where)
    fun save(ctx: Context, where: Where, spec: SortSpec) = save(prefs(ctx), where, spec)
}
