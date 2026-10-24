package com.husarp.ankigo.ui

import android.content.Intent
import android.os.Bundle
import com.husarp.ankigo.Anki
import com.husarp.ankigo.Report

/**
 * No-UI trampoline behind the motivation widget's tap: opens the Study tab when AnkiDroid has cards due,
 * otherwise (and on any problem: no permission, AnkiDroid
 * missing, error) the "Open AnkiGo on" choice (R18 263). Decided at tap time on a background thread. Translucent, out of recents, finishes itself.
 */
class WidgetTapActivity : AgActivity() {
    companion object {
        /** The Buddy widget's tap: no due check, straight to the "Open AnkiGo on" choice read now (R18 RV-1). */
        const val ACTION_OPEN = "com.husarp.ankigo.WIDGET_OPEN"
    }

    override val showsUpdateBanner = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == ACTION_OPEN) { startActivity(OpenOnPrefs.intent(this)); finish(); return }
        Anki.load(this)
        Thread {
            val study = try {
                Anki.hasPermission(this) && StudyWidgetPrefs.anyDue(Anki.decks(this))
            } catch (e: Exception) {
                Report.fail("C8", "widget tap: could not read decks", e); false
            }
            runOnUiThread {
                if (!isDestroyed) {
                    startActivity(if (study) Nav.intent(this, Dest.STUDY) else OpenOnPrefs.intent(this))
                    finish()
                }
            }
        }.start()
    }
}
