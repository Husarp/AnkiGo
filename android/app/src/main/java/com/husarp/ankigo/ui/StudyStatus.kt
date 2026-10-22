package com.husarp.ankigo.ui

import android.content.Context
import com.husarp.ankigo.Anki
import com.husarp.ankigo.update.UpdateLogic

/**
 * PLAN 202: what is wrong with AnkiDroid, as far as studying in AnkiGo goes. The Study screen shows a warning card only for a
 * [Problem]; the version and "OK" live in Settings > About ([line]).
 */
object StudyStatus {
    enum class Action { GET_ANKIDROID, OPEN_ANKIDROID, ALLOW_ACCESS }

    class Problem(val message: String, val action: Action, val button: String)

    /** The 2.24 provider is the first that AnkiGo can study with. */
    private const val MIN = "2.24"

    /** Null when all is fine, else what is wrong and the one action that helps. [version] null = AnkiDroid is not installed. */
    fun warning(installed: Boolean, version: String?, supported: Boolean, permission: Boolean): Problem? = when {
        !installed -> Problem("AnkiDroid isn't installed. AnkiGo studies your AnkiDroid decks.", Action.GET_ANKIDROID, "Get AnkiDroid")
        !supported -> Problem(
            if (version != null && UpdateLogic.compare(version, MIN) < 0) "AnkiDroid $version is too old. Update it to $MIN or newer."
            else "AnkiDroid's card service isn't available. Open AnkiDroid once, or update it.",
            Action.OPEN_ANKIDROID, "Open AnkiDroid")
        !permission -> Problem("AnkiGo isn't allowed to use AnkiDroid yet.", Action.ALLOW_ACCESS, "Allow AnkiDroid access")
        else -> null
    }

    fun warning(ctx: Context): Problem? {
        val inst = Anki.installed(ctx).firstOrNull { it.pkg == Anki.pkg }
        return warning(inst != null, inst?.versionName, Anki.studySupported(ctx), Anki.hasPermission(ctx))
    }

    /** The Settings row's short value: "2.25.1 · OK", "2.22.4 · problem" or "Not installed"; the problem's words are [warning]'s message. */
    fun line(ctx: Context): String {
        val inst = Anki.installed(ctx).firstOrNull { it.pkg == Anki.pkg }
        val w = warning(ctx)
        return when {
            inst == null -> "Not installed"
            w == null -> "${inst.versionName} · OK"
            else -> "${inst.versionName} · problem"
        }
    }
}
