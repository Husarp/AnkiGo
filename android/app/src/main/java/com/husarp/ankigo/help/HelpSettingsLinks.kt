package com.husarp.ankigo.help

import android.content.Context
import android.content.Intent
import com.husarp.ankigo.DiagnosticsActivity
import com.husarp.ankigo.ui.SettingsActivity

/** Only AnkiGo destinations; Android and AnkiDroid instructions keep their plain boxed paths. */
internal object HelpSettingsLinks {
    data class Link(val start: Int, val end: Int, val setting: String)
    private val paths = linkedMapOf(
        "Settings → Ankimon PC sync → Import Ankimon save (.db)…" to "ankimonImport",
        "Settings → Ankimon PC sync → Export for Ankimon PC…" to "ankimonExport",
        "Settings → Battle panel & windows → On leaving" to "leaveMode",
        "Settings → Game → \"When you beat a wild Pokémon\"" to "catchNew",
        "Settings → Game → When you beat a wild Pokémon" to "catchNew",
        "Settings → Game → Battle" to "moveChoiceSmart",
        "Settings → Ankimon PC sync" to "@ANKIMON",
        "Settings → Diagnostics" to "@DIAGNOSTICS",
        "Settings → Study" to "@STUDY",
        "Settings → Game" to "@GAME",
    )
    fun find(text: String): List<Link> {
        val result = mutableListOf<Link>()
        for ((path, target) in paths) {
            var pos = text.indexOf(path)
            while (pos >= 0) {
                val prefix = text.substring(0, pos).substringAfterLast('.').substringAfterLast('\n')
                val external = prefix.endsWith("Android ") || prefix.contains("In AnkiDroid", true) || prefix.contains("in AnkiDroid", true)
                val start = if (text.substring(0, pos).endsWith("AnkiGo → ")) pos - "AnkiGo → ".length else pos
                if (!external && result.none { start < it.end && pos + path.length > it.start }) result += Link(start, pos + path.length, target)
                pos = text.indexOf(path, pos + path.length)
            }
        }
        return result.sortedBy { it.start }
    }
    fun intent(ctx: Context, target: String): Intent =
        (if (target == "@DIAGNOSTICS") Intent(ctx, DiagnosticsActivity::class.java)
        else Intent(ctx, SettingsActivity::class.java).putExtra("helpSetting", target))
            .addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)

    fun open(ctx: Context, target: String) {
        val intent = intent(ctx, target)
        if (ctx is com.husarp.ankigo.ui.GamePage) ctx.startScreen(intent) else {
            var owner = ctx
            while (owner is android.content.ContextWrapper && owner !is android.app.Activity) owner = owner.baseContext
            if (owner !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        }
    }
}
