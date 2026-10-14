package com.husarp.ankigo.ui

import android.content.Intent
import android.os.Bundle
import com.husarp.ankigo.Anki
import com.husarp.ankigo.Report

/**
 * No-UI trampoline behind the motivation widget's tap: opens the Study tab when AnkiDroid has cards due,
 * otherwise the battle. Decided at tap time on a background thread; any problem (no permission, AnkiDroid
 * missing, error) opens the battle. Translucent, out of recents, finishes itself.
 */
class WidgetTapActivity : AgActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Anki.load(this)
        Thread {
            val study = try {
                Anki.hasPermission(this) && StudyWidgetPrefs.anyDue(Anki.decks(this))
            } catch (e: Exception) {
                Report.fail("C8", "widget tap: could not read decks", e); false
            }
            runOnUiThread {
                if (!isDestroyed) {
                    startActivity(Intent(this, if (study) StudyDecksActivity::class.java else GameActivity::class.java))
                    finish()
                }
            }
        }.start()
    }
}
