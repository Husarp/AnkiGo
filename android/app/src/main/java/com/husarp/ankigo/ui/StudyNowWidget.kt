package com.husarp.ankigo.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

// R5-07: the 1x1 "Study now" widget (mockup 64I). Drawn by WidgetRender (Kind.STUDY_NOW); the due count comes from WidgetDue.
class StudyNowWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pr = goAsync()
        WidgetRender.async(pr) { WidgetRender.update(ctx, mgr, ids, WidgetRender.Kind.STUDY_NOW) }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle?) {
        val pr = goAsync()
        WidgetRender.async(pr) { WidgetRender.update(ctx, mgr, intArrayOf(id), WidgetRender.Kind.STUDY_NOW) }
    }
}

/** What the Study now widget shows for a due count (pure). */
object StudyNow {
    enum class State { DUE, DONE, UNKNOWN }

    /** null = AnkiDroid not readable. */
    fun state(due: Int?) = when {
        due == null -> State.UNKNOWN
        due > 0 -> State.DUE
        else -> State.DONE
    }

    /** Spoken name of the whole widget (TalkBack): "Study now, 87 cards due" / "Study now, all done" / "Study now". */
    fun description(due: Int?): String = when (state(due)) {
        State.DUE -> "Study now, $due ${if (due == 1) "card" else "cards"} due"
        State.DONE -> "Study now, all done"
        State.UNKNOWN -> "Study now"
    }

    fun label(s: State) = if (s == State.DONE) "All done" else "Study"

    /** The red badge's text: only while cards are due; more than 999 = "999+". */
    fun badge(due: Int?): String? = when {
        due == null || due <= 0 -> null
        due > 999 -> "999+"
        else -> "$due"
    }
}
