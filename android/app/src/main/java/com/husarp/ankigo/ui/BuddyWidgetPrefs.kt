package com.husarp.ankigo.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences

/**
 * Which Pokémon each Buddy widget shows, per widget id in the "spike" prefs (`b<id>.mon`): a Pokémon's uid, or [MAIN]
 * (the default: whoever is the main now). A choice for a Pokémon that is gone is left in place and harmless: the widget
 * falls back to the main ([WidgetSnapshot.forBuddy]). Same idea as [StudyWidgetPrefs]; the SharedPreferences wrappers are thin.
 */
object BuddyWidgetPrefs {
    /** Follow the current main (the default). */
    const val MAIN = "main"

    fun key(id: Int) = "b$id.mon"

    fun choice(p: SharedPreferences, id: Int): String = p.getString(key(id), null)?.takeIf { it.isNotEmpty() } ?: MAIN

    fun set(p: SharedPreferences, id: Int, choice: String) { p.edit().putString(key(id), choice).apply() }

    /** Sets the same choice on several widgets at once. */
    fun setAll(p: SharedPreferences, ids: IntArray, choice: String) {
        val ed = p.edit()
        for (id in ids) ed.putString(key(id), choice)
        ed.apply()
    }

    /** Called when widgets are deleted. */
    fun forget(p: SharedPreferences, ids: IntArray) {
        val ed = p.edit()
        for (id in ids) ed.remove(key(id))
        ed.apply()
    }

    /** The ids of the Buddy widgets on the home screens (empty: none placed). */
    fun widgetIds(ctx: Context): IntArray =
        runCatching { AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, BuddyWidget::class.java)) }.getOrDefault(IntArray(0))
}
