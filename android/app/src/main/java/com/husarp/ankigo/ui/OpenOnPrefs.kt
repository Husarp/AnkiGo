package com.husarp.ankigo.ui

import android.content.Context

/** What "Open AnkiGo on" can be (R18 / PLAN 263, Settings → Widgets). [key] is what is saved. */
enum class OpenOn(val key: String, val label: String) {
    STUDY("study", "Study"), BATTLE("battle", "Battle"), LAST("last", "Last tab");

    companion object {
        val DEFAULT = STUDY
        /** The choice saved as [key]; the default (Study) for nothing or an unknown value. */
        fun of(key: String?): OpenOn = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * "Open AnkiGo on" (R18 / PLAN 263): where the entry points that have no target of their own take you. Saved in "ankigo_ui"
 * ([OpenOn] choice + the bottom tab shown last). The rule is pure ([dest], [launcherStartUsesSetting]) so the JVM tests cover it.
 *
 * Follow the setting: the launcher icon's fresh start, the Buddy widget, the motivation widget when no cards are due.
 * Keep their own target: Study now widget, the study-widget "Open AnkiGo", the cards-due / goal reminders (Study) and shop reminder (Shop),
 * the floating battle window and Pop out (the battle), the old Study tab forwarder (Study).
 */
object OpenOnPrefs {
    private const val FILE = "ankigo_ui"
    private const val KEY_CHOICE = "openOn"
    private const val KEY_LAST_TAB = "lastTab"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun choice(ctx: Context): OpenOn = OpenOn.of(prefs(ctx).getString(KEY_CHOICE, null))
    fun setChoice(ctx: Context, c: OpenOn) = prefs(ctx).edit().putString(KEY_CHOICE, c.key).apply()

    /** Saved when a bottom tab is shown or tapped. */
    fun setLastTab(ctx: Context, tab: BottomNav.Tab) = setLastScreen(ctx, tab.dest)

    /**
     * The last SCREEN (R18 RV-5): a tab's [Dest], or [Dest.BATTLE] / [Dest.STUDY] for the home screen's two views (the Battle view
     * has no bottom bar and is a view of the Study tab, so "Last tab" would bring a user who left there back on Study).
     * A missing or failing prefs file is ignored: this is only a convenience.
     */
    fun setLastScreen(ctx: Context, dest: Dest) {
        try { prefs(ctx).edit()?.putString(KEY_LAST_TAB, dest.name)?.apply() } catch (_: Exception) {}
    }
    private fun lastTab(ctx: Context): String? = try { prefs(ctx).getString(KEY_LAST_TAB, null) } catch (_: Exception) { null }

    /** The screen the setting names right now. */
    fun dest(ctx: Context): Dest = dest(choice(ctx), lastTab(ctx))

    /** The Intent for it (what a widget tap starts). */
    fun intent(ctx: Context): android.content.Intent = Nav.intent(ctx, dest(ctx))

    /** Study → the Study view, Battle → the full-screen battle, Last tab → the screen saved as [lastTabName]: a [Dest] name,
     *  or an old saved tab name (Study when none or unknown). */
    fun dest(choice: OpenOn, lastTabName: String?): Dest = when (choice) {
        OpenOn.STUDY -> Dest.STUDY
        OpenOn.BATTLE -> Dest.BATTLE
        OpenOn.LAST -> Dest.entries.firstOrNull { it.name == lastTabName }
            ?: BottomNav.Tab.entries.firstOrNull { it.name == lastTabName }?.dest ?: Dest.STUDY
    }

    /** The target [intent] resolves to, for tests and checks: the Activity class and its extra. */
    fun target(choice: OpenOn, lastTabName: String?): Nav.Target = Nav.target(dest(choice, lastTabName))

    /** Only a plain launcher start (no view named, no Pop out) follows the setting; every other start of the home screen has its own destination. */
    fun launcherStartUsesSetting(isLauncher: Boolean, viewExtra: String?, popOut: Boolean): Boolean = isLauncher && viewExtra == null && !popOut

    /** The one-line summary on the Settings home. */
    fun summary(c: OpenOn): String = "Opens on ${c.label}"
}
