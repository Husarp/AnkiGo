package com.husarp.ankigo.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.Report
import com.husarp.ankigo.data.AnkimonFiles
import com.husarp.ankigo.data.DataStore
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.ankimon.AnkimonExport
import com.husarp.ankigo.game.ankimon.AnkimonRefs
import com.husarp.ankigo.game.ankimon.ExportException
import com.husarp.ankigo.game.ankimon.ExportReport
import com.husarp.ankigo.game.save.SaveCodec
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// TICKET T53 owns this file: "Export for Ankimon (PC)…" as seen by the player (warning -> file picker -> progress -> result).

/**
 * Writes a new Ankimon save (`.db`) from the running game, for Ankimon's menu Game -> Import Save File… on the PC.
 * The file itself is built by [AnkimonExport] (pure Kotlin) from the kept copy of the last import; this object only does
 * the Android parts: dialogs, the "save as" picker, writing + checking the chosen document. The game is never changed
 * and the kept copy is only read.
 */
object AnkimonExportFlow {
    private const val TAG = "ANKIMON"
    private const val WARNING = "On the PC, Ankimon replaces its save with this file at the next Anki restart. " +
        "Don't play Ankimon on the PC between importing to the phone and exporting back."
    private const val UNCHANGED = "Your AnkiGo game and your Ankimon files are as they were."
    private const val STEPS = "On the PC: in Anki open Ankimon → Game → Import Save File…, pick this file, then restart Anki."

    private val main by lazy { Handler(Looper.getMainLooper()) }

    /**
     * Starts the export: warning, "save as" picker via [launch] (it must start the intent for a result and call back with
     * the result code and data), then build -> write -> check. Call on the main thread. [ui] gives the context to show
     * dialogs on at that moment (the picker result can arrive after the screen was recreated). [onDone] gets true when
     * a file was written and checked.
     */
    fun start(
        ctx: Context,
        launch: (Intent, (Int, Intent?) -> Unit) -> Unit,
        ui: () -> Context? = { ctx },
        onDone: (Boolean) -> Unit = {},
    ) {
        val app = ctx.applicationContext
        val ready = GameRepo.data != null && GameRepo.state != null &&
            (GameRepo.status == GameRepo.Status.READY || GameRepo.status == GameRepo.Status.NEED_STARTER)
        if (!ready) {
            Ui.toast(app, "Load the game first")
            onDone(false)
            return
        }
        val c = liveUi(app, ui)
        if (c == null) { onDone(false); return }
        // Imported with an older AnkiGo (no kept copy): a fresh save would drop what only the PC has, so say it plainly.
        val noCopy = GameRepo.state?.mons?.any { it.ankimonId != null } == true
        val keptAt = AnkimonFiles.keptAt(app)
        // A new game was started on the phone after the import, and the kept file was not forgotten then.
        val oldGame = AnkimonFiles.keptBeforeGame(keptAt, GameRepo.state?.createdAt ?: 0)
        val dialog = warningDialog(c, keptAt, { choose(app, launch, ui, onDone) }, { onDone(false) }, noCopy, oldGame)
        Pickers.show(dialog)
    }

    private fun choose(app: Context, launch: (Intent, (Int, Intent?) -> Unit) -> Unit, ui: () -> Context?, onDone: (Boolean) -> Unit) {
        val trainer = GameRepo.state?.settings?.trainerName ?: ""
        val name = AnkimonFiles.exportName(trainer, SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, name) // octet-stream keeps the .db name
        try {
            launch(intent) { code, data ->
                val uri = data?.data
                if (code != Activity.RESULT_OK || uri == null) onDone(false) else run(app, uri, ui, onDone)
            }
        } catch (e: Exception) { // no file picker on this phone
            Ui.toast(app, "No file picker found on this phone")
            onDone(false)
        }
    }

    // ---- the export ----

    private sealed class Outcome {
        class Ok(val report: ExportReport) : Outcome()
        class Failed(val message: String, val wrote: Boolean = false) : Outcome() // wrote: the picked document may be half-written
    }

    /** The chosen document was opened for writing and something went wrong after that. */
    private class WriteFailed(message: String) : Exception(message)

    private fun run(app: Context, uri: Uri, ui: () -> Context?, onDone: (Boolean) -> Unit) {
        val data = GameRepo.data
        val state = GameRepo.state
        if (data == null || state == null) { Ui.toast(app, "Load the game first"); onDone(false); return }
        val snapshot: GameState
        try {
            snapshot = SaveCodec.decode(SaveCodec.encode(state)) // a private copy: the thread never sees the live game
        } catch (e: Exception) {
            Report.fail(TAG, "copying the game for the export", e)
            Ui.toast(app, "Couldn't export. Nothing was changed.")
            onDone(false)
            return
        }
        val c = liveUi(app, ui)
        if (c == null) { onDone(false); return }
        val progress = progressDialog(c)
        Pickers.show(progress.dialog)
        val now = System.currentTimeMillis()
        Thread {
            val out = try {
                Outcome.Ok(work(app, uri, data, snapshot, now) { f, text -> main.post { progress.set(f, text) } })
            } catch (e: WriteFailed) {
                Outcome.Failed(e.message ?: "Couldn't write the file.", wrote = true)
            } catch (e: ExportException) {
                Report.fail(TAG, "exporting for Ankimon", e)
                Outcome.Failed(e.message ?: "Couldn't build the Ankimon file.")
            } catch (e: OutOfMemoryError) {
                Outcome.Failed("There isn't enough memory on this phone to build the Ankimon file.")
            } catch (e: Throwable) { // anything else (incl. errors) must still close the progress dialog
                Report.fail(TAG, "exporting for Ankimon", e)
                Outcome.Failed("Couldn't write the Ankimon file.")
            }
            main.post { finish(app, progress, out, ui, onDone) }
        }.apply { name = "ankimon-export"; isDaemon = true }.start()
    }

    /** Background thread. Returns the report of a file that was written to [uri] AND read back identical; throws otherwise. */
    private fun work(app: Context, uri: Uri, data: GameData, state: GameState, now: Long, step: (Float, String) -> Unit): ExportReport {
        step(0.05f, "Reading your imported save…")
        val kept = AnkimonFiles.kept(app)?.readBytes() // read only
        val refs = AnkimonRefs.fromFiles { DataStore.rawText(app, it) }
        step(0.25f, "Building the Ankimon save…")
        val result = AnkimonExport.build(data, state, kept, refs, now)
        step(0.55f, "Checking the file…")
        val problems = AnkimonExport.verify(result)
        if (problems.isNotEmpty()) {
            Report.fail(TAG, "export check failed: " + problems.take(5).joinToString("; "))
            throw ExportException("The file didn't pass the checks, so it was not written.")
        }
        val tmp = File.createTempFile("ankimon-export-", ".db", app.cacheDir)
        try {
            tmp.writeBytes(result.bytes)
            step(0.7f, "Writing the file…")
            val cr = app.contentResolver
            // An older file in the way is kept first. A new document some providers can't open for reading yet has nothing to keep.
            val old = try { cr.openInputStream(uri) } catch (e: Exception) { null }
            try {
                old?.use { AnkimonFiles.saveReplaced(app, it, now) }
            } catch (e: IOException) {
                Report.fail(TAG, "saving the file being replaced", e)
                throw ExportException("Couldn't save the file you picked before replacing it, so it was left as it was.")
            }
            try {
                // "wt" truncates; a provider without it gets "w" (a longer old file would leave a tail: the read-back catches that).
                val o = (try { cr.openOutputStream(uri, "wt") } catch (e: IllegalArgumentException) { null } catch (e: FileNotFoundException) { null })
                    ?: cr.openOutputStream(uri, "w") ?: throw IOException("no output stream")
                o.use { out -> FileInputStream(tmp).use { it.copyTo(out) }; out.flush() }
                step(0.9f, "Checking what was written…")
                val back = cr.openInputStream(uri) ?: throw IOException("no input stream")
                if (!back.use { sha(it) }.contentEquals(sha(result.bytes))) throw IOException("read-back differs")
            } catch (e: IOException) {
                Report.fail(TAG, "writing the Ankimon file", e)
                throw WriteFailed("Couldn't write the file, or it didn't read back the same. Don't use the file you picked; it may be incomplete.")
            }
        } finally {
            tmp.delete()
        }
        step(1f, "Done")
        return result.report
    }

    private fun sha(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

    private fun sha(i: java.io.InputStream): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = i.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
        return md.digest()
    }

    private fun finish(app: Context, progress: ExportProgress, out: Outcome, ui: () -> Context?, onDone: (Boolean) -> Unit) {
        try { progress.dialog.dismiss() } catch (_: Exception) { } // its window may be gone with the screen
        val ok = out is Outcome.Ok
        val c = liveUi(app, ui)
        if (c == null) { // the result can't be shown: say it short
            Ui.toast(app, if (ok) "Ankimon file is ready" else "Ankimon export failed. Nothing was changed.")
            onDone(ok)
            return
        }
        when (out) {
            is Outcome.Ok -> {
                Report.ok(TAG, "exported for Ankimon: ${summary(out.report)}")
                Pickers.show(successDialog(c, out.report))
            }
            is Outcome.Failed -> Pickers.show(failureDialog(c, out.message, nothingChanged = !out.wrote))
        }
        onDone(ok)
    }

    /** The context to show dialogs on, or null (with a toast) when its screen is gone. */
    private fun liveUi(app: Context, ui: () -> Context?): Context? {
        val c = ui()
        val a = c?.let { Ui.findActivity(it) }
        val gone = c == null || (a != null && (a.isFinishing || a.isDestroyed))
        if (gone) {
            Ui.toast(app, "The screen was closed. Start the export again.")
            return null
        }
        return c
    }

    // ---- text (pure) ----

    /** "312 Pokémon (5 new from this phone, 2 released, 14 kept as they were) · team of 6 · ₽ 12,400 · Pokédex 210". */
    internal fun summary(r: ExportReport): String {
        val notes = buildList {
            if (r.added > 0 && !r.fresh) add("${r.added} new from this phone")
            if (r.released > 0) add("${r.released} released")
            if (r.untouched > 0) add("${r.untouched} kept as they were")
        }
        val mons = "${Bits.fmt(r.mons)} Pokémon" + if (notes.isEmpty()) "" else " (${notes.joinToString(", ")})"
        val team = if (r.team > 0) "team of ${r.team}" else "no team"
        return listOf(mons, team, "₽ ${Bits.fmt(r.cash)}", "Pokédex ${Bits.fmt(r.caught)}").joinToString(" · ")
    }

    /** Keeps "team of 6", "₽ 12,400", "Pokédex 210" in one piece when the line wraps (the first part may wrap). */
    private fun noBreaks(line: String): String =
        line.split(" · ").mapIndexed { i, p -> if (i == 0) p else p.replace(' ', '\u00A0') }.joinToString(" · ")

    private fun day(t: Long) = SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(t))

    internal fun baseLine(keptAt: Long?, noCopy: Boolean = false, oldGame: Boolean = false): String =
        if (keptAt != null && oldGame) "You started a new game after importing your Ankimon save on ${day(keptAt)}. " +
            "This file is still built on that save: the PC keeps the Pokémon it had and gets this game's Pokémon too, " +
            "but its money, items and team become this game's."
        else if (keptAt == null && noCopy) "AnkiGo has no copy of your Ankimon save yet (imports before version 0.7.0 didn't keep one). " +
            "Import your Ankimon save again first: otherwise this file has only what AnkiGo knows, and badges, trainer level, " +
            "settings and Pokémon AnkiGo can't show are lost on the PC."
        else if (keptAt != null) "Based on the Ankimon save you imported on ${day(keptAt)}, " +
            "with your AnkiGo Pokémon, team, items, money and Pokédex written in."
        else "You haven't imported an Ankimon save, so this makes a new Ankimon save with your AnkiGo Pokémon, team, items, money and Pokédex."

    // ---- dialogs (internal: the screenshot tests build them without a picker or a real export) ----

    private fun column(c: Context) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        val side = Ui.dp(c, 22)
        setPadding(side, Ui.dp(c, 8), side, Ui.dp(c, 4))
    }

    /** The warning: a tinted panel with the alert icon, so it can't be skimmed past. */
    private fun warningPanel(c: Context, text: String, size: Float): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.TOP
        background = Ui.rounded(Bits.blend(Ui.c(c, R.color.ag_peach), Ui.c(c, R.color.ag_card), 0.2f), 12, c)
        setPadding(Ui.dp(c, 12), Ui.dp(c, 11), Ui.dp(c, 12), Ui.dp(c, 11))
        addView(ImageView(c).apply {
            setImageResource(R.drawable.ic_alert)
            setColorFilter(Ui.c(c, R.color.ag_peach), PorterDuff.Mode.SRC_IN)
        }, LinearLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20)).also { it.topMargin = Ui.dp(c, 1) })
        addView(Ui.text(c, size, 800, R.color.ag_text).apply { this.text = text; setLineSpacing(0f, 1.12f) },
            LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(c, 10) })
    }

    internal fun warningDialog(
        c: Context, keptAt: Long?, onChoose: () -> Unit, onCancel: () -> Unit, noCopy: Boolean = false, oldGame: Boolean = false,
    ): AlertDialog {
        val col = column(c)
        val risky = (keptAt == null && noCopy) || (keptAt != null && oldGame)
        col.addView(warningPanel(c, WARNING, 13f))
        col.addView(Ui.text(c, 14f, if (risky) 800 else 700, if (risky) R.color.ag_text else R.color.ag_sub1).apply { text = baseLine(keptAt, noCopy, oldGame); setLineSpacing(0f, 1.15f) },
            LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(c, 14) })
        col.addView(Ui.text(c, 12f, 600, R.color.ag_sub0).apply { text = "Your AnkiGo game isn't changed." },
            LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(c, 10) })
        return AlertDialog.Builder(c).setTitle("Export for Ankimon PC?").setView(Bits.scroll(c, col))
            .setPositiveButton(if (risky) "Export anyway…" else "Choose file…") { _, _ -> onChoose() }
            .setNegativeButton("Cancel") { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }
            .create()
    }

    /**
     * The last step of "Start a new game". With a kept Ankimon file it also asks whether to forget it (default yes):
     * yes -> a later export builds a fresh Ankimon save; no -> it still builds on the old import (said here and in the
     * export warning). [onStart] gets the answer (false when there is no kept file).
     */
    internal fun startOverDialog(c: Context, keptAt: Long?, onStart: (forgetAnkimon: Boolean) -> Unit): AlertDialog {
        val b = AlertDialog.Builder(c).setTitle("Really start over?")
        val gone = "Your Pokémon, items and progress are gone from the game. Are you sure?"
        if (keptAt == null) return b.setMessage(gone).setPositiveButton("Start over") { _, _ -> onStart(false) }.setNegativeButton("Cancel", null).create()
        val col = column(c)
        col.addView(Ui.text(c, 14f, 700, R.color.ag_sub1).apply { text = gone; setLineSpacing(0f, 1.15f) })
        val box = CheckBox(c).apply {
            text = "Also forget the imported Ankimon save"
            textSize = 14f
            typeface = Ui.font(c, 800)
            setTextColor(Ui.c(c, R.color.ag_text))
            isChecked = true
            Ui.tintCheck(this)
        }
        col.addView(box, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(c, 12) })
        val note = Ui.text(c, 12f, 600, R.color.ag_sub0).apply { setLineSpacing(0f, 1.12f) }
        fun fill(on: Boolean) {
            note.text = if (on) "A later export for Ankimon then makes a fresh Ankimon save from the new game only."
            else "A later export still builds on the save you imported on ${day(keptAt)}: the PC keeps those Pokémon, but its money, items and team become the new game's."
        }
        fill(true)
        box.setOnCheckedChangeListener { _, on -> fill(on) }
        col.addView(note, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(c, 4); it.marginStart = Ui.dp(c, 4) })
        return b.setView(Bits.scroll(c, col))
            .setPositiveButton("Start over") { _, _ -> onStart(box.isChecked) }
            .setNegativeButton("Cancel", null).create()
    }

    /** The progress dialog (not cancelable); [set] moves the bar and the one-line label. */
    internal class ExportProgress(val dialog: AlertDialog, private val bar: BarView, private val label: TextView) {
        fun set(fraction: Float, text: String) { bar.setMeaning(BarMeaning.PROGRESS, fraction, true); label.text = text }
    }

    internal fun progressDialog(c: Context): ExportProgress {
        val col = column(c)
        val label = Ui.text(c, 14f, 700, R.color.ag_sub1).apply { text = "Starting…" }
        val bar = BarView(c)
        col.addView(label)
        col.addView(bar, LinearLayout.LayoutParams(-1, Ui.dp(c, 8)).also { it.topMargin = Ui.dp(c, 14); it.bottomMargin = Ui.dp(c, 12) })
        val d = AlertDialog.Builder(c).setTitle("Exporting…").setView(col).setCancelable(false).create()
        return ExportProgress(d, bar, label)
    }

    internal fun successDialog(c: Context, r: ExportReport): AlertDialog {
        val col = column(c)
        col.addView(Ui.text(c, 14f, 800, R.color.ag_text).apply { text = noBreaks(summary(r)); setLineSpacing(0f, 1.1f) })
        col.addView(Ui.text(c, 14f, 700, R.color.ag_sub1).apply { text = STEPS; setLineSpacing(0f, 1.15f) },
            LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(c, 12) })
        col.addView(warningPanel(c, "Don't play Ankimon on the PC until you've imported this file.", 12f),
            LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(c, 14) })
        return AlertDialog.Builder(c).setTitle("Ready for Ankimon").setView(Bits.scroll(c, col)).setPositiveButton("OK", null).create()
    }

    internal fun failureDialog(c: Context, message: String, nothingChanged: Boolean = true): AlertDialog =
        AlertDialog.Builder(c).setTitle("Export failed")
            .setMessage(message + "\n\n" + (if (nothingChanged) "Nothing was changed. " else "") + UNCHANGED)
            .setPositiveButton("OK", null).create()
}
