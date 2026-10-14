package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.ankimon.ExportReport
import com.husarp.ankigo.ui.AnkimonExportFlow
import com.husarp.ankigo.ui.SettingsPage
import org.junit.Rule
import org.junit.Test

/* T53: the export for Ankimon (PC): warning (with a kept import / without), progress, result (plain / with released and kept), failure, and the Settings save card. */
class AnkimonExportShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    // 12 Oct 2026, midday UTC
    private val keptAt = 1_791_806_400_000L

    private fun dialog(name: String, open: (Context) -> android.app.AlertDialog) {
        paparazzi.shootPage(name) { ctx ->
            val d = DialogShots.capture { com.husarp.ankigo.ui.Pickers.show(open(ctx)) } ?: error("no dialog was shown")
            DialogShots.scene(ctx, d)
        }
    }

    private fun report(fresh: Boolean = false, added: Int = 5, released: Int = 0, untouched: Int = 0) =
        ExportReport(fresh, 312, added, 20, untouched, released, 6, 12400, 210, 41)

    @Test fun dialogs() {
        dialog("export-warning-kept") { AnkimonExportFlow.warningDialog(it, keptAt, {}, {}) }
        dialog("export-warning-fresh") { AnkimonExportFlow.warningDialog(it, null, {}, {}) }
        dialog("export-warning-nocopy") { AnkimonExportFlow.warningDialog(it, null, {}, {}, noCopy = true) }
        dialog("export-warning-oldgame") { AnkimonExportFlow.warningDialog(it, keptAt, {}, {}, oldGame = true) }
        dialog("export-progress") { ctx -> AnkimonExportFlow.progressDialog(ctx).also { it.set(0.55f, "Checking the file…") }.dialog }
        dialog("export-success") { AnkimonExportFlow.successDialog(it, report()) }
        dialog("export-success-notes") { AnkimonExportFlow.successDialog(it, report(released = 2, untouched = 14)) }
        dialog("export-success-fresh") { AnkimonExportFlow.successDialog(it, report(fresh = true, added = 312)) }
        dialog("export-failed") { AnkimonExportFlow.failureDialog(it, "The file didn't pass the checks, so it was not written.") }
        dialog("export-failed-write") {
            AnkimonExportFlow.failureDialog(it, "Couldn't write the file, or it didn't read back the same. Don't use the file you picked; it may be incomplete.", nothingChanged = false)
        }
    }

    /** "Start a new game" last step: with a kept Ankimon file (box on, box off) and without one. */
    @Test fun startOverDialogs() {
        dialog("startover-kept-on") { AnkimonExportFlow.startOverDialog(it, keptAt) {} }
        dialog("startover-nokept") { AnkimonExportFlow.startOverDialog(it, null) {} }
        paparazzi.shootPage("startover-kept-off") { ctx ->
            val d = DialogShots.capture { com.husarp.ankigo.ui.Pickers.show(AnkimonExportFlow.startOverDialog(ctx, keptAt) {}) } ?: error("no dialog was shown")
            val scene = DialogShots.scene(ctx, d)
            (find(scene) { it is android.widget.CheckBox } as android.widget.CheckBox).isChecked = false
            scene
        }
    }

    /** The Settings page cut down to the save card (its header + the card), so the new button and note are in the picture. */
    @Test fun settingsSaveCard() {
        paparazzi.shootPage("export-settings-save", listOf(Variants.PHONE)) { ctx ->
            val sp = SettingsPage(ShotCtx(ctx), NoHost)
            val screen = pageScreen(ShotCtx(ctx), sp)
            sp.open(com.husarp.ankigo.ui.SettingsSection.ANKIMON)
            val button = find(screen) { it is TextView && it.text.toString().startsWith("Export for Ankimon") }!!
            var card: View = button
            while (card.parent !is LinearLayout || (card.parent as View).parent !is ScrollView) card = card.parent as View
            val content = card.parent as LinearLayout
            val keep = listOf(card)
            content.removeAllViews()
            keep.forEach { content.addView(it) }
            screen
        }
    }

    private fun find(v: View, match: (View) -> Boolean): View? {
        if (match(v)) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), match)?.let { return it }
        return null
    }
}
