package com.husarp.ankigo.ui

import android.content.Intent
import android.os.Bundle
import com.husarp.ankigo.Anki
import com.husarp.ankigo.Report

/**
 * No-UI trampoline behind the motivation widget's tap: opens the Study tab when AnkiDroid has cards due,
 * otherwise (and on any problem: no permission, AnkiDroid
 * missing, error) the "Open AnkiGo on" choice (R18 263). Decided at tap time on a background thread. PLAN 286: isolated from the app task, out of Recents, finishes itself.
 */
class WidgetTapActivity : AgActivity() {
    companion object {
        /** The Buddy widget's tap: no due check, straight to the "Open AnkiGo on" choice read now (R18 RV-1). */
        const val ACTION_OPEN = "com.husarp.ankigo.WIDGET_OPEN"

        /** PLAN 286: reuse the normal app task and its destination, never the excluded trampoline task. */
        const val APP_TASK_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

    /** PLAN 286: like the launcher, keep Home behind a saved tab on a cold start. */
    private fun openScreen(target: Intent) {
        target.addFlags(APP_TASK_FLAGS)
        if (target.component?.className == GameActivity::class.java.name) startActivity(target)
        else startActivities(arrayOf(Nav.intent(this, Dest.STUDY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), target))
    }

    override val showsUpdateBanner = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == ACTION_OPEN) { openScreen(OpenOnPrefs.intent(this)); finish(); return }
        Anki.load(this)
        Thread {
            val study = try {
                Anki.hasPermission(this) && StudyWidgetPrefs.anyDue(Anki.decks(this))
            } catch (e: Exception) {
                Report.fail("C8", "widget tap: could not read decks", e); false
            }
            runOnUiThread {
                if (!isDestroyed) {
                    openScreen(if (study) Nav.intent(this, Dest.STUDY) else OpenOnPrefs.intent(this))
                    finish()
                }
            }
        }.start()
    }
}
