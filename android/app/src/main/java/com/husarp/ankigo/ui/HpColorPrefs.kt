package com.husarp.ankigo.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import java.util.WeakHashMap

/** PLAN 163 / R19: saved separately from AppPrefs; the arena follows picks immediately. */
object HpColorPrefs {
    const val KEY = "hpColours"
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("ankigo_ui", 0)
    fun mode(ctx: Context): HpColors.Mode = HpColors.mode(try { prefs(ctx).getString(KEY, null) } catch (_: ClassCastException) { null })
    private val watches = WeakHashMap<View, (View) -> Unit>()

    fun set(ctx: Context, mode: HpColors.Mode) {
        prefs(ctx).edit().putString(KEY, mode.name).apply()
        // R19: picks happen on the UI thread; repaint now, including paused arenas behind Settings.
        watches.entries.toList().forEach { (view, refresh) -> refresh(view) }
    }

    internal fun watch(view: View, refresh: (View) -> Unit) { watches[view] = refresh }

    internal fun color(view: View, frac: Float, type: Int?, refresh: (View) -> Unit): Int {
        watch(view, refresh) // the callback takes the weak key as an argument; it never retains the arena
        return HpColors.color(frac, mode(view.context), TypeChips.isDark(view.context), type)
    }

    internal fun row(ctx: Context, keepOpen: MutableSet<String>? = null): View = LookChooser(
        ctx, "HP bar colours", HpColors.Mode.entries, mode(ctx), { it.label },
        { mode -> LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            for (f in listOf(0.9f, 0.45f, 0.15f)) addView(BarView(ctx).apply {
                set(f, HpColors.color(f, mode, TypeChips.isDark(ctx), Ui.typeColor(com.husarp.ankigo.game.Type.GRASS)), false)
            }, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 7)).also { it.topMargin = Ui.dp(ctx, 6) })
        } }, 48, keepOpen = keepOpen, onPick = { set(ctx, it) },
    )
}
