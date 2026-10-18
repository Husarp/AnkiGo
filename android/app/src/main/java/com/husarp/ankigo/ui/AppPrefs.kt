package com.husarp.ankigo.ui

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.CopyOnWriteArrayList

/** What AnkiGo shows when you leave its battle screen: nothing, picture-in-picture, or the floating battle window. Only one at a time. */
enum class LeaveMode { OFF, PIP, FLOAT }

/** One-time move of "PiP on leave" to "nothing" (PLAN 24): the old key "leaveMode" is only read while "leaveModeV2" is missing. */
object LeaveModeMigration {
    const val KEY = "leaveModeV2"

    private fun parse(s: String?): LeaveMode? = LeaveMode.entries.firstOrNull { it.name == s }

    /** The mode to use: V2 when set; else the old value with PIP turned into OFF (FLOAT and OFF stay; nothing stored = OFF). */
    fun resolve(v2: String?, old: String?): LeaveMode =
        parse(v2) ?: parse(old)?.takeIf { it != LeaveMode.PIP } ?: LeaveMode.OFF

    /** True while V2 is missing or unreadable: the resolved value is to be written. */
    fun needsWrite(v2: String?): Boolean = parse(v2) == null
}

/** Which window "Pop out" opens (R4-X5): the floating battle window or Android's picture-in-picture. */
enum class PopOutMode { PIP, FLOAT }

/**
 * Pop out (PLAN 24, R4-X5): the player's own choice ("popOutMode"). Never chosen: the floating window when the leave mode is
 * FLOAT, else picture-in-picture (the behaviour before the choice existed). A device without PiP always gets the floating window.
 */
object PopOutPlan {
    const val KEY = "popOutMode"

    fun resolve(stored: String?, leave: LeaveMode): PopOutMode =
        PopOutMode.entries.firstOrNull { it.name == stored } ?: if (leave == LeaveMode.FLOAT) PopOutMode.FLOAT else PopOutMode.PIP

    fun useFloat(mode: PopOutMode, pipAvailable: Boolean): Boolean = mode == PopOutMode.FLOAT || !pipAvailable
}

/** App theme: follow Android, or always light / dark. */
enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/**
 * Small app/UI preferences that are not part of the game save (SharedPreferences "ankigo_ui"):
 * leave mode, floating screens, whether the overlay permission was explained, floating window bounds.
 * Main thread or any thread; reads are cheap.
 */
object AppPrefs {
    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("ankigo_ui", Context.MODE_PRIVATE)

    /**
     * Default: nothing (picture-in-picture is no longer automatic; the battle has a "Pop out" button instead).
     * Existing installs that had PiP on leave are moved to nothing once (key "leaveModeV2", see [LeaveModeMigration]);
     * after that the player's choice, PiP included, sticks.
     */
    fun leaveMode(ctx: Context): LeaveMode {
        val p = prefs(ctx)
        val v2 = p.getString(LeaveModeMigration.KEY, null)
        val mode = LeaveModeMigration.resolve(v2, p.getString("leaveMode", null))
        if (LeaveModeMigration.needsWrite(v2)) p.edit().putString(LeaveModeMigration.KEY, mode.name).apply()
        return mode
    }

    fun setLeaveMode(ctx: Context, mode: LeaveMode) = prefs(ctx).edit().putString(LeaveModeMigration.KEY, mode.name).apply()

    /** Which window Pop out opens (Settings → Battle → Pop out opens); see [PopOutPlan]. */
    fun popOutMode(ctx: Context): PopOutMode = PopOutPlan.resolve(prefs(ctx).getString(PopOutPlan.KEY, null), leaveMode(ctx))

    fun setPopOutMode(ctx: Context, mode: PopOutMode) = prefs(ctx).edit().putString(PopOutPlan.KEY, mode.name).apply()

    /** Team, Pokédex, Shop and Settings open as floating windows while AnkiGo is in split screen (needs the overlay permission). */
    fun floatScreens(ctx: Context): Boolean = prefs(ctx).getBoolean("floatScreens", true)

    fun setFloatScreens(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("floatScreens", on).apply()

    /** True once the "Display over other apps" explanation was shown (it is asked only once; Settings can ask again). */
    fun overlayExplained(ctx: Context): Boolean = prefs(ctx).getBoolean("overlayExplained", false)

    fun setOverlayExplained(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("overlayExplained", v).apply()

    /** Saved position and size of the floating window [key] in px: x, y, width, height; null = never moved. */
    fun bounds(ctx: Context, key: String): IntArray? {
        val s = prefs(ctx).getString("bounds.$key", null) ?: return null
        val p = s.split(',').mapNotNull { it.trim().toIntOrNull() }
        return if (p.size == 4) p.toIntArray() else null
    }

    fun setBounds(ctx: Context, key: String, x: Int, y: Int, w: Int, h: Int) =
        prefs(ctx).edit().putString("bounds.$key", "$x,$y,$w,$h").apply()

    /** Small windows (picture-in-picture, floating battle) show the stats bar (goal, combo, streak, coins...). Default on. */
    fun statsBarSmall(ctx: Context): Boolean = prefs(ctx).getBoolean("statsBarSmall", true)

    fun setStatsBarSmall(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("statsBarSmall", on).apply()

    /** Compact stat cards on every battle panel: name + a thin HP bar with a small HP number. Default off. */
    fun compactCards(ctx: Context): Boolean = prefs(ctx).getBoolean("compactCards", false)

    fun setCompactCards(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("compactCards", on).apply()

    /** Each battle stat card sits under (or next to) its own Pokémon; off = the classic game layout (opposite sides). Default on. */
    fun cardsUnderOwn(ctx: Context): Boolean = prefs(ctx).getBoolean("cardsUnderOwn", true)

    fun setCardsUnderOwn(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("cardsUnderOwn", on).apply()

    /**
     * The facing of our Pokémon in every battle view: true = its front sprite, mirrored to face the wild one; false = the back
     * sprite. Set by a swipe across the Pokémon (no Settings row since 0.15; installs that had the old switch on start facing
     * front). Default off.
     */
    fun playerFront(ctx: Context): Boolean = prefs(ctx).getBoolean("playerFront", false)

    fun setPlayerFront(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("playerFront", on).apply()

    /** Calls [onChange] when the facing changes (a swipe in another battle view). The prefs hold [listener] weakly: the caller keeps it. */
    fun watchPlayerFront(ctx: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) = prefs(ctx).registerOnSharedPreferenceChangeListener(listener)

    fun unwatchPlayerFront(ctx: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) = prefs(ctx).unregisterOnSharedPreferenceChangeListener(listener)

    /** Sparks fly from the defeated Pokémon into ours and a "+N XP" number counts up when our Pokémon gains XP (Settings → Battle). Default on. */
    fun xpAnimation(ctx: Context): Boolean = prefs(ctx).getBoolean("xpAnimation", true)

    fun setXpAnimation(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("xpAnimation", on).apply()

    /** Each move plays its own attack animation (round 7, docs/design/attack-anims-v2.html; Settings → Battle). Off: the old step + shake. Default on. */
    fun attackAnimations(ctx: Context): Boolean = prefs(ctx).getBoolean("attackAnimations", true)

    fun setAttackAnimations(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("attackAnimations", on).apply()

    /** The arena shows a battle background picture chosen by the wild Pokémon's type (round 7, PLAN 32A; Settings → Battle). Off: the plain background. Default on. */
    fun backgroundPictures(ctx: Context): Boolean = prefs(ctx).getBoolean("backgroundPictures", true)

    fun setBackgroundPictures(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("backgroundPictures", on).apply()

    /** The Box collapses copies of one species into one "×N" tile (Team → Box "Group duplicates" switch). Default on. */
    fun boxGrouped(ctx: Context): Boolean = prefs(ctx).getBoolean("boxGrouped", true)

    fun setBoxGrouped(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("boxGrouped", on).apply()

    /** Details sections (R4-V3): whether the section [key] is open; null = never answered (see [DetailOpen]). */
    fun detailOpen(ctx: Context, key: String): Boolean? = prefs(ctx).let { if (it.contains("detailOpen_$key")) it.getBoolean("detailOpen_$key", false) else null }

    fun setDetailOpen(ctx: Context, key: String, open: Boolean) = prefs(ctx).edit().putBoolean("detailOpen_$key", open).apply()

    /** App theme (Settings → Look). Default: follow the system. Applied by [AppLook]; change it through [AppLook.setTheme]. */
    fun theme(ctx: Context): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == prefs(ctx).getString("theme", null) } ?: ThemeMode.SYSTEM

    /** Accent colour (Settings → Look). Default: mauve, the mockup's accent. Change it through [AppLook.setAccent]. */
    fun accent(ctx: Context): Accent =
        Accent.entries.firstOrNull { it.name == prefs(ctx).getString("accent", null) } ?: Accent.MAUVE

    /** Progress bar colours (Settings → Look): by meaning (default), accent or gradient. Change it through [AppLook.setBarStyle]; read by [BarTones]. */
    fun barStyle(ctx: Context): BarStyle =
        BarStyle.entries.firstOrNull { it.name == prefs(ctx).getString("barStyle", null) } ?: BarStyle.MEANING

    /**
     * How type chips are drawn (Settings → Look → "Show types as"): icon + text (default), text or icon. Change it
     * through [AppLook.setTypeStyle]; drawn by [TypeChips].
     */
    fun typeStyle(ctx: Context): TypeStyle =
        TypeStyle.entries.firstOrNull { it.name == prefs(ctx).getString("typeStyle", null) } ?: TypeStyle.ICON_TEXT

    /** Interface size in percent ([AppLook.SCALES], default 100): text, cards and sprites together. Change it through [AppLook.setScale]. */
    fun uiScale(ctx: Context): Int = prefs(ctx).getInt("uiScale", 100).let { if (it in AppLook.SCALES) it else 100 }

    internal fun putLook(ctx: Context, key: String, value: String?) = prefs(ctx).edit().putString(key, value).apply()

    internal fun putScale(ctx: Context, percent: Int) = prefs(ctx).edit().putInt("uiScale", percent).apply()
}

/**
 * Whether one of AnkiGo's own screens is in front (the battle screen, full, split or picture-in-picture, or Team,
 * Pokédex, Shop, Settings, setup, updates and diagnostics). AnkiGoApp tracks all Activity starts/stops; legacy screen hooks are idempotent. The floating battle ends while
 * any is visible, so it never covers AnkiGo itself. Listeners run on the main thread.
 */
object BattleScreen {
    private val owners: MutableSet<Any> = java.util.Collections.newSetFromMap(java.util.IdentityHashMap())
    private val listeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    val visible: Boolean get() = owners.isNotEmpty()

    /** [owner] (an Activity) is visible / no longer visible. Calling it twice with the same value changes nothing. */
    fun setVisible(owner: Any, v: Boolean) {
        val before = visible
        if (v) owners += owner else owners -= owner
        if (visible != before) listeners.forEach { it(visible) }
    }

    fun addListener(l: (Boolean) -> Unit) { listeners.addIfAbsent(l) }
    fun removeListener(l: (Boolean) -> Unit) { listeners.remove(l) }
}
