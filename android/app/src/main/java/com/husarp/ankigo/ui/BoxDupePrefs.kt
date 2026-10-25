package com.husarp.ankigo.ui

import android.content.Context
import android.content.SharedPreferences

/** PLAN 282 / R19: independent Box duplicate looks, saved in the usual UI preferences. */
object BoxDupePrefs {
    internal const val STACKED = "box_stacked_cards"
    internal const val TWO = "box_two_pokemon"
    data class Look(val stacked: Boolean = true, val two: Boolean = false)
    fun get(ctx: Context): Look = get(ctx.getSharedPreferences("ankigo_ui", Context.MODE_PRIVATE))
    internal fun get(prefs: SharedPreferences) = Look(prefs.getBoolean(STACKED, true), prefs.getBoolean(TWO, false))
    fun setStacked(ctx: Context, on: Boolean) = set(ctx.getSharedPreferences("ankigo_ui", 0), STACKED, on)
    fun setTwo(ctx: Context, on: Boolean) = set(ctx.getSharedPreferences("ankigo_ui", 0), TWO, on)
    internal fun set(prefs: SharedPreferences, key: String, on: Boolean) { prefs.edit().putBoolean(key, on).apply() }
}
