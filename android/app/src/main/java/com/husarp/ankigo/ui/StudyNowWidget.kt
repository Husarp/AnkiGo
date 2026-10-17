package com.husarp.ankigo.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.SharedPreferences
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

    override fun onDeleted(ctx: Context, appWidgetIds: IntArray) {
        StudyNowPrefs.forget(ctx.getSharedPreferences("spike", 0), appWidgetIds)
    }
}

/** R6-08: what the 1x1 widget's background follows. Old widgets have no saved value = [SYSTEM]. */
enum class StudyNowBg(val key: String, val label: String) {
    SYSTEM("system", "System"), WHITE("white", "White"), BLACK("black", "Black");

    /** Is the widget's surface dark? [systemNight] = the colour scheme this bitmap is drawn for (the light and the dark bitmap are both drawn). */
    fun dark(systemNight: Boolean) = when (this) { SYSTEM -> systemNight; WHITE -> false; BLACK -> true }

    companion object {
        /** Missing or unknown text gives [SYSTEM]. */
        fun parse(s: String?) = values().firstOrNull { it.key == s } ?: SYSTEM
    }
}

/** The 1x1 widget's background per widget id in the "spike" prefs (`sn<id>.bg`). */
object StudyNowPrefs {
    fun bgKey(id: Int) = "sn$id.bg"
    fun bg(p: SharedPreferences, id: Int) = StudyNowBg.parse(p.getString(bgKey(id), null))
    fun setBg(p: SharedPreferences, id: Int, bg: StudyNowBg) { p.edit().putString(bgKey(id), bg.key).apply() }
    fun forget(p: SharedPreferences, ids: IntArray) {
        val ed = p.edit()
        for (id in ids) ed.remove(bgKey(id))
        ed.apply()
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

    /** Spoken name of the whole widget (TalkBack): "Study now, 87 cards due, 32 of 50 today" / "Study now, all done" / "Study now". [goal] 0 or less = unknown. */
    fun description(due: Int?, today: Int = 0, goal: Int = 0): String {
        val base = when (state(due)) {
            State.DUE -> "Study now, $due ${if (due == 1) "card" else "cards"} due"
            State.DONE -> "Study now, all done"
            State.UNKNOWN -> "Study now"
        }
        return if (goal > 0) "$base, $today of $goal today" else base
    }

    /**
     * The colour of the book icon and the "Study" text: today's goal progress, red at 0, orange in the middle, green at the
     * goal ([GoalTone]). Nothing left to study ([State.DONE]) is green too. [progress] is 0..1 (or more).
     */
    fun tone(state: State, progress: Float, p: GoalPalette): Int =
        if (state == State.DONE) p.green else GoalTone.color(progress, p)

    fun label(s: State) = if (s == State.DONE) "All done" else "Study"

    /** The due number under "Study" (R6-08: no badge any more): only while cards are due; more than 999 = "999+". */
    fun badge(due: Int?): String? = when {
        due == null || due <= 0 -> null
        due > 999 -> "999+"
        else -> "$due"
    }
}
