package com.husarp.ankigo.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

// TICKET T15 owns this file: the small "buddy" widget (main Pokémon, progress ring, streak). Drawn by WidgetRender.
class BuddyWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pr = goAsync()
        WidgetRender.async(pr) { WidgetRender.update(ctx, mgr, ids, WidgetRender.Kind.BUDDY) }
    }

    override fun onDeleted(ctx: Context, appWidgetIds: IntArray) {
        BuddyWidgetPrefs.forget(ctx.getSharedPreferences("spike", 0), appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle?) {
        val pr = goAsync()
        WidgetRender.async(pr) { WidgetRender.update(ctx, mgr, intArrayOf(id), WidgetRender.Kind.BUDDY) }
    }
}
