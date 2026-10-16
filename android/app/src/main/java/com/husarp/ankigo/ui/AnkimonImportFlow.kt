package com.husarp.ankigo.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.CheckBox
import android.widget.LinearLayout
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.Report
import com.husarp.ankigo.data.AnkimonFiles
import com.husarp.ankigo.data.DataStore
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.ankimon.AnkimonMerge
import com.husarp.ankigo.game.ankimon.AnkimonSave
import com.husarp.ankigo.game.ankimon.AnkimonSaveReader
import com.husarp.ankigo.game.ankimon.ImportReport
import com.husarp.ankigo.game.ankimon.NotAnkimonException
import com.husarp.ankigo.game.ankimon.SqliteFormatException
import com.husarp.ankigo.game.ankimon.SqliteReader
import com.husarp.ankigo.game.save.SaveCodec
import java.io.File
import java.io.IOException

// TICKET T23 owns this file: the Ankimon import as seen by the player (picker -> read -> preview -> backup + apply).

/**
 * Imports an Ankimon save (`.db`, from Ankimon's menu Game -> Export Save File...) into the running game.
 * The merge itself is [AnkimonMerge] (pure Kotlin, never removes anything); this object only does the Android parts:
 * the file picker, copying the file out of the document provider, the preview dialog and the safe apply.
 * Everything here runs on the main thread except the copy + read, which use the application context only.
 */
object AnkimonImportFlow {
    private const val TAG = "ANKIMON"
    private const val MAX_BYTES = 64L shl 20
    private const val SHOW_SKIPPED = 8
    private const val NOT_ANKIMON = "This is not an Ankimon save file. In Anki use Ankimon's menu Game → Export Save File… and pick that file."

    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** What the background read found. */
    private sealed class Read {
        class Ok(val save: AnkimonSave, val formBase: Map<Int, Int>) : Read() {
            /** The cache copy the save was read from; it lives until kept or discarded (null in tests). */
            var file: File? = null
            fun discard() { file?.delete() }
        }
        class Problem(val title: String, val message: String) : Read()
    }

    /**
     * Starts the import: file picker via [launch] (it must start the intent for a result and call back with the result
     * code and data), then read -> preview -> apply. Call on the main thread. [ui] gives the context to show dialogs on
     * at that moment (a picker result can arrive after the screen was recreated); if it is gone, a toast is shown instead
     * and nothing changes. [onDone] gets true when the game was changed.
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
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        try {
            launch(intent) { code, data ->
                val uri = data?.data
                if (code != Activity.RESULT_OK || uri == null) onDone(false) else read(app, uri, ui, onDone)
            }
        } catch (e: Exception) { // no file picker on this phone
            Ui.toast(app, "No file picker found on this phone")
            onDone(false)
        }
    }

    // ---- step 1: copy + read, off the main thread ----

    private fun read(app: Context, uri: Uri, ui: () -> Context?, onDone: (Boolean) -> Unit) {
        Thread {
            val result = readFile(app, uri)
            main.post { afterRead(app, result, ui, onDone) }
        }.apply { name = "ankimon-import"; isDaemon = true }.start()
    }

    private fun readFile(app: Context, uri: Uri): Read {
        sweepStale(app)
        val copy = File.createTempFile("ankimon-import-", ".db", app.cacheDir)
        var keep = false
        try {
            var size = 0L
            val input = app.contentResolver.openInputStream(uri) ?: throw IOException("no input stream")
            input.use { i ->
                copy.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = i.read(buf)
                        if (n < 0) break
                        size += n
                        if (size > MAX_BYTES) return Read.Problem("Import failed", "That file is too big to be an Ankimon save.\n\nNothing was changed.")
                        o.write(buf, 0, n)
                    }
                }
            }
            val save = AnkimonSaveReader.read(SqliteReader.open(copy, MAX_BYTES))
            keep = true
            return Read.Ok(save, DataStore.formBaseMap(app)).also { it.file = copy }
        } catch (e: NotAnkimonException) {
            return Read.Problem("Import failed", NOT_ANKIMON)
        } catch (e: SqliteFormatException) {
            return Read.Problem("Import failed", NOT_ANKIMON)
        } catch (e: Exception) {
            Report.fail(TAG, "reading the Ankimon file", e)
            return Read.Problem("Import failed", "Couldn't read that file.\n\nNothing was changed.")
        } catch (e: OutOfMemoryError) {
            return Read.Problem("Import failed", "That file is too big to be an Ankimon save.\n\nNothing was changed.")
        } finally {
            if (!keep) copy.delete()
        }
    }

    /** Cache copies left behind by a screen that died mid-import (older than an hour). */
    private fun sweepStale(app: Context) {
        val old = System.currentTimeMillis() - 3_600_000L
        app.cacheDir.listFiles { f -> f.name.startsWith("ankimon-import-") && f.lastModified() < old }?.forEach { it.delete() }
    }

    // ---- step 2: preview ----

    /** The context to show dialogs on, or null (with a toast) when its screen is gone. */
    private fun liveUi(app: Context, ui: () -> Context?): Context? {
        val c = ui()
        val a = c?.let { Ui.findActivity(it) }
        val gone = c == null || (a != null && (a.isFinishing || a.isDestroyed))
        if (gone) {
            Ui.toast(app, "The screen was closed. Start the import again.")
            return null
        }
        return c
    }

    private fun afterRead(app: Context, result: Read, ui: () -> Context?, onDone: (Boolean) -> Unit) {
        val c = liveUi(app, ui)
        if (c == null) { (result as? Read.Ok)?.discard(); onDone(false); return }
        when (result) {
            is Read.Problem -> {
                info(c, result.title, result.message)
                onDone(false)
            }
            is Read.Ok -> preview(app, c, result, ui, onDone)
        }
    }

    private fun info(c: Context, title: String, message: String) {
        Pickers.show(AlertDialog.Builder(c).setTitle(title).setMessage(message).setPositiveButton("OK", null).create())
    }

    /** Merges [read] into a throw-away copy of [state] (the real state is untouched). Null when the merge itself fails. */
    private fun trial(data: GameData, state: GameState, read: Read.Ok, useTeam: Boolean, now: Long): ImportReport? = try {
        val copy = SaveCodec.decode(SaveCodec.encode(state))
        AnkimonMerge.merge(data, copy, read.save, { read.formBase[it] }, useTeam, now)
    } catch (e: Exception) {
        Report.fail(TAG, "merging the Ankimon save", e)
        null
    }

    private fun preview(app: Context, c: Context, read: Read.Ok, ui: () -> Context?, onDone: (Boolean) -> Unit) {
        val data = GameRepo.data
        val state = GameRepo.state
        if (data == null || state == null) { read.discard(); Ui.toast(app, "Load the game first"); onDone(false); return }
        val now = System.currentTimeMillis()
        val defaultTeam = state.mons.size <= 1
        val first = trial(data, state, read, defaultTeam, now)
        val other = trial(data, state, read, !defaultTeam, now)
        if (first == null || other == null) {
            read.discard()
            info(c, "Import failed", "Couldn't read that file.\n\nNothing was changed.")
            onDone(false)
            return
        }
        if (first.summary() == "nothing new" && other.summary() == "nothing new") {
            keepCopy(app, read, "Couldn't keep a copy for exporting back") // the PC's newest state is already in the game
            info(c, "Nothing new in this file", "Everything in it is already in your game." + (notInFileLine(first.notInFile)?.let { "\n\n$it" } ?: ""))
            onDone(false)
            return
        }
        showPreview(app, c, read, defaultTeam, first, now, ui, onDone)
    }

    private fun showPreview(
        app: Context, c: Context, read: Read.Ok, defaultTeam: Boolean, firstReport: ImportReport, now: Long,
        ui: () -> Context?, onDone: (Boolean) -> Unit,
    ) {
        val col = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val side = Ui.dp(c, 22)
            setPadding(side, Ui.dp(c, 8), side, Ui.dp(c, 4))
        }
        val summary = Ui.text(c, 15f, 800, R.color.ag_text)
        val skipped = Ui.text(c, 12f, 600, R.color.ag_sub1)
        val missing = Ui.text(c, 13f, 700, R.color.ag_sub1).apply { setLineSpacing(0f, 1.1f) }
        col.addView(summary)
        col.addView(missing, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(c, 8) })
        col.addView(skipped, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(c, 8) })
        val box = CheckBox(c).apply {
            text = "Use my Ankimon team (your current team goes to the box)"
            textSize = 13f
            setTextColor(Ui.c(c, R.color.ag_text))
            isChecked = defaultTeam
            Ui.tintCheck(this)
        }
        col.addView(box, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(c, 10) })
        col.addView(Ui.text(c, 12f, 600, R.color.ag_sub0).apply {
            text = "Nothing is removed. Stats are recalculated by AnkiGo, so some may look a little different. " +
                "Your current game is backed up first."
        }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(c, 10) })

        fun fill(r: ImportReport) {
            summary.text = r.summary().replaceFirstChar { it.uppercase() }
            val gone = notInFileLine(r.notInFile)
            missing.visibility = if (gone == null) android.view.View.GONE else android.view.View.VISIBLE
            missing.text = gone ?: ""
            val lines = r.skipped + read.save.problems
            fun block(title: String, l: List<String>) = if (l.isEmpty()) "" else
                "$title\n" + l.take(SHOW_SKIPPED).joinToString("\n") + if (l.size > SHOW_SKIPPED) "\nand ${l.size - SHOW_SKIPPED} more" else ""
            val text = listOf(
                block("Kept as Not supported yet (they stay safe and turn into Pokémon when AnkiGo knows them):", r.unsupported),
                block("Skipped:", lines),
            ).filter { it.isNotEmpty() }.joinToString("\n\n")
            skipped.visibility = if (text.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            skipped.text = text
        }
        fill(firstReport)
        box.setOnCheckedChangeListener { _, on ->
            val data = GameRepo.data
            val state = GameRepo.state
            val r = if (data != null && state != null) trial(data, state, read, on, now) else null
            if (r != null) fill(r)
        }

        var taken = false // apply() owns the cache copy from the Import tap on
        val dialog = AlertDialog.Builder(c).setTitle("Import from Ankimon?").setView(Bits.scroll(c, col))
            .setPositiveButton("Import") { _, _ ->
                val live = liveUi(app, ui)
                if (live != null) { taken = true; apply(app, read, box.isChecked, now, onDone) } else onDone(false)
            }
            .setNegativeButton("Cancel") { _, _ -> onDone(false) }
            .setOnCancelListener { onDone(false) }
            .setOnDismissListener { if (!taken) read.discard() }
            .create()
        Pickers.show(dialog)
    }

    /** The preview line for Pokémon the phone has but the PC file doesn't (the import never removes them); null for none. */
    internal fun notInFileLine(n: Int): String? = when {
        n <= 0 -> null
        n == 1 -> "1 Pokémon you have on the phone is not in this PC save (released on the PC?). It stays on the phone."
        else -> "$n Pokémon you have on the phone are not in this PC save (released on the PC?). They stay on the phone."
    }

    // ---- step 3: apply ----

    /**
     * Merges into a FRESH copy of the current state (answers may have come in while the dialog was open) and swaps it in
     * through GameRepo.replaceState, which keeps the old game as save.before-ankimon-<time>.json first.
     */
    private fun apply(app: Context, read: Read.Ok, useTeam: Boolean, now: Long, onDone: (Boolean) -> Unit) {
        val data = GameRepo.data
        val state = GameRepo.state
        if (data == null || state == null) { read.discard(); Ui.toast(app, "Load the game first"); onDone(false); return }
        val report: ImportReport
        val merged: GameState
        try {
            merged = SaveCodec.decode(SaveCodec.encode(state))
            report = AnkimonMerge.merge(data, merged, read.save, { read.formBase[it] }, useTeam, now)
        } catch (e: Exception) {
            Report.fail(TAG, "merging the Ankimon save", e)
            read.discard()
            Ui.toast(app, "Couldn't import. Nothing was changed.")
            onDone(false)
            return
        }
        try { GameRepo.replaceState(app, merged, backupTag = "ankimon") }
        catch (e: Exception) {
            Report.fail(TAG, "backing up before import", e)
            read.discard()
            Ui.toast(app, "Couldn't back up the current game. Nothing was changed.")
            onDone(false)
            return
        }
        keepCopy(app, read, "Imported, but couldn't keep a copy for exporting back")
        Ui.toast(app, "Imported: ${report.added} new, ${report.upgraded} upgraded")
        onDone(true)
    }

    /**
     * Keeps the cache copy as the file the export builds on ([AnkimonFiles.keep], atomic) and deletes the cache copy,
     * off the main thread. A failure only shows [failToast]; it never fails the import.
     */
    private fun keepCopy(app: Context, read: Read.Ok, failToast: String) {
        val file = read.file ?: return
        Thread {
            val ok = AnkimonFiles.keep(app, file)
            file.delete()
            if (!ok) main.post { Ui.toast(app, failToast) }
        }.apply { name = "ankimon-keep"; isDaemon = true }.start()
    }
}
