package com.husarp.ankigo.ui

import android.app.Activity
import com.husarp.ankigo.Anki

// TICKET R3-X2 owns this file: the "Sync AnkiDroid" button of the Study tab.

/**
 * What AnkiDroid does with the official "Tasker" intent (action `com.ichi2.anki.DO_SYNC` on its exported IntentHandler
 * activity; checked in AnkiDroid's source, IntentHandler.kt and AndroidManifest.xml, v2.17 to v2.25.2 and main):
 * it brings AnkiDroid to the front and syncs with AnkiWeb, but only if the user is signed in to AnkiWeb and online, and
 * not within 2 minutes of AnkiDroid's last sync (`INTENT_SYNC_MIN_INTERVAL`); otherwise it only posts its own notification.
 * AnkiGo gets no answer back. These are the pure parts.
 */
object AnkiSyncRules {
    /** AnkiDroid's `INTENT_SYNC_MIN_INTERVAL`: it ignores a DO_SYNC this soon after a sync. */
    const val MIN_GAP_MS = 2 * 60_000L

    /** Seconds to wait before asking again (0 = ask now). [lastAskedAt] 0 = never; a clock that went back counts as never. */
    fun waitSeconds(now: Long, lastAskedAt: Long): Int {
        if (lastAskedAt <= 0 || now < lastAskedAt) return 0
        val left = MIN_GAP_MS - (now - lastAskedAt)
        return if (left <= 0) 0 else ((left + 999) / 1000).toInt()
    }
}

object AnkiSync {
    private const val ASKED = "syncAsked" // in the "study" prefs: when AnkiGo last sent the intent

    /**
     * Asks AnkiDroid to sync (the official DO_SYNC intent) and says so in a short toast. If AnkiDroid can't take the intent
     * it is opened instead, with a toast saying to tap its own Sync button. Never throws.
     */
    fun ask(a: Activity) {
        val prefs = a.getSharedPreferences("study", 0)
        val now = System.currentTimeMillis()
        val wait = AnkiSyncRules.waitSeconds(now, prefs.getLong(ASKED, 0))
        if (wait > 0) { Ui.toast(a, "AnkiDroid only syncs when asked every 2 minutes. Try again in $wait s."); return }
        if (Anki.installed(a).none { it.pkg == Anki.pkg }) { Ui.toast(a, "AnkiDroid isn't installed"); return }
        try {
            a.startActivity(Anki.syncIntent())
            prefs.edit().putLong(ASKED, now).apply()
            Ui.toast(a, "Asked AnkiDroid to sync with AnkiWeb")
        } catch (e: Exception) { // no such activity (very old build) or not allowed
            val open = Anki.launchIntent(a)
            if (open != null && runCatching { a.startActivity(open) }.isSuccess) Ui.toast(a, "AnkiDroid can't be asked to sync from here. It is open now: tap its Sync button.")
            else Ui.toast(a, "Open AnkiDroid to sync")
        }
    }
}
