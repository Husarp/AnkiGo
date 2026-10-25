package com.husarp.ankigo.ui

import android.content.Context
import android.content.SharedPreferences
import com.husarp.ankigo.game.GameState

/** PLAN 280 / R19: last Box opening's owned IDs, bounded by the collection, never a history of released IDs. */
object BoxSeenPrefs {
    internal const val KEY = "box_seen_owned"

    fun open(ctx: Context, state: GameState) =
        open(ctx.getSharedPreferences("ankigo_ui", Context.MODE_PRIVATE), state)

    /** Apply the previous visit only; this visit's snapshot affects the next opening, including after a restart. */
    internal fun open(prefs: SharedPreferences, state: GameState) {
        val owned = state.mons.mapTo(linkedSetOf()) { it.uid }
        state.viewedMons.retainAll(owned)
        state.viewedMons += prefs.getStringSet(KEY, emptySet()).orEmpty().intersect(owned)
        prefs.edit().putStringSet(KEY, owned).apply()
    }
}
