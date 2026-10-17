package com.husarp.ankigo.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import com.husarp.ankigo.R

/**
 * Which Pokémon each Buddy widget shows, per widget id in the "spike" prefs (`b<id>.mon`): a Pokémon's uid, or [MAIN]
 * (the default: whoever is the main now). A choice for a Pokémon that is gone is left in place and harmless: the widget
 * falls back to the main ([WidgetSnapshot.forBuddy]). Same idea as [StudyWidgetPrefs]; the SharedPreferences wrappers are thin.
 *
 * The widget's look is per id too: `b<id>.ring` ([BuddyRing]: "app" | "type" | "pick:<colour>") and `b<id>.anim`
 * (the two-frame hop); a missing key means the default (app colours, no animation).
 */
object BuddyWidgetPrefs {
    /** Follow the current main (the default). */
    const val MAIN = "main"

    fun key(id: Int) = "b$id.mon"
    fun ringKey(id: Int) = "b$id.ring"
    fun animKey(id: Int) = "b$id.anim"

    fun choice(p: SharedPreferences, id: Int): String = p.getString(key(id), null)?.takeIf { it.isNotEmpty() } ?: MAIN

    fun set(p: SharedPreferences, id: Int, choice: String) { p.edit().putString(key(id), choice).apply() }

    /** Sets the same choice on several widgets at once. */
    fun setAll(p: SharedPreferences, ids: IntArray, choice: String) {
        val ed = p.edit()
        for (id in ids) ed.putString(key(id), choice)
        ed.apply()
    }

    fun ring(p: SharedPreferences, id: Int): BuddyRing = BuddyRing.parse(p.getString(ringKey(id), null))

    fun setRing(p: SharedPreferences, id: Int, ring: BuddyRing) { p.edit().putString(ringKey(id), ring.format()).apply() }

    fun anim(p: SharedPreferences, id: Int): Boolean = p.getBoolean(animKey(id), false)

    fun setAnim(p: SharedPreferences, id: Int, on: Boolean) { p.edit().putBoolean(animKey(id), on).apply() }

    fun look(p: SharedPreferences, id: Int) = BuddyLook(ring(p, id), anim(p, id))

    /** Called when widgets are deleted. */
    fun forget(p: SharedPreferences, ids: IntArray) {
        val ed = p.edit()
        for (id in ids) { ed.remove(key(id)); ed.remove(ringKey(id)); ed.remove(animKey(id)) }
        ed.apply()
    }

    /** The ids of the Buddy widgets on the home screens (empty: none placed). */
    fun widgetIds(ctx: Context): IntArray =
        runCatching { AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, BuddyWidget::class.java)) }.getOrDefault(IntArray(0))
}

/** How one Buddy widget looks besides its Pokémon: the [ring] colour and whether the Pokémon [hop]s (two frames, see WidgetRender). */
data class BuddyLook(val ring: BuddyRing = BuddyRing.APP, val hop: Boolean = false)

/**
 * The ring colour setting: [Mode.APP] keeps the goal colours of Settings → Look → Progress bars (the default),
 * [Mode.TYPE] takes the shown Pokémon's first type colour, [Mode.PICK] one of [PICKS] (a name, see [pickRes]).
 */
data class BuddyRing(val mode: Mode, val pick: String = DEFAULT_PICK) {
    enum class Mode { APP, TYPE, PICK }

    /** "app", "type" or "pick:<colour>". */
    fun format() = when (mode) { Mode.APP -> "app"; Mode.TYPE -> "type"; Mode.PICK -> "pick:$pick" }

    /**
     * The one solid ring colour for this setting, or null = follow the app's goal colours (also when a type colour is
     * wanted but the Pokémon's type is unknown). [typeColor] is the Pokémon's type colour; [pickColor] maps a [PICKS] name to a colour.
     */
    fun solid(typeColor: Int?, pickColor: (String) -> Int): Int? = when (mode) {
        Mode.APP -> null
        Mode.TYPE -> typeColor
        Mode.PICK -> pickColor(pick)
    }

    companion object {
        val APP = BuddyRing(Mode.APP)
        const val DEFAULT_PICK = "mauve"

        /** The app's own colours offered by "Pick a colour", in the mockup's order. */
        val PICKS = listOf("mauve", "blue", "pink", "green", "peach", "teal", "yellow", "red")

        /** The colour resource of a [PICKS] name (an unknown name gives the default). */
        fun pickRes(name: String): Int = when (name) {
            "blue" -> R.color.ag_blue; "pink" -> R.color.ag_pink; "green" -> R.color.ag_green; "peach" -> R.color.ag_peach
            "teal" -> R.color.ag_teal; "yellow" -> R.color.ag_yellow; "red" -> R.color.ag_red
            else -> R.color.ag_mauve
        }

        /** Missing, empty or unknown text gives the default (app colours); an unknown pick name gives the default pick. */
        fun parse(s: String?): BuddyRing = when {
            s == null -> APP
            s == "type" -> BuddyRing(Mode.TYPE)
            s.startsWith("pick:") -> BuddyRing(Mode.PICK, s.removePrefix("pick:").takeIf { it in PICKS } ?: DEFAULT_PICK)
            else -> APP
        }
    }
}
