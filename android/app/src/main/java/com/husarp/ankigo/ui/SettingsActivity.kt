package com.husarp.ankigo.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.DiagnosticsActivity
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.data.DataStore
import com.husarp.ankigo.data.SpriteKind
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Settings as GameSettings
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.save.SaveCodec
import com.husarp.ankigo.game.save.SaveFormatException
import com.husarp.ankigo.update.Updates
import com.husarp.ankigo.update.UpdatesActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/** The two long Settings downloads. They live here so rotating or resizing the screen doesn't stop them. */
internal object SettingsJobs {
    val anim = BgJob()
    val data = BgJob()

    /** Front + back battle sprites of [species] with 4 parallel workers; [BgJob.cancel] stops it early. */
    fun startAnimations(ctx: Context, species: List<Species>) {
        if (anim.running) return
        val app = ctx.applicationContext
        anim.begin("Starting…")
        Thread {
            try {
                val queue = ConcurrentLinkedQueue(species.flatMap { listOf(it to SpriteKind.FRONT, it to SpriteKind.BACK) })
                val total = queue.size
                val done = AtomicInteger()
                val workers = 4
                val latch = CountDownLatch(workers)
                repeat(workers) {
                    Thread {
                        try {
                            while (!anim.cancelled) {
                                val (sp, kind) = queue.poll() ?: break
                                Sprites.fetchBlocking(app, sp, kind, false)
                                val d = done.incrementAndGet()
                                anim.progress(d.toFloat() / total, "Downloaded $d / $total")
                            }
                        } finally { latch.countDown() }
                    }.apply { isDaemon = true; name = "settings-anim" }.start()
                }
                latch.await()
                if (anim.cancelled) anim.done("Stopped at ${done.get()} / $total. What was downloaded is kept.")
                else anim.done("All battle animations downloaded.")
            } catch (e: Throwable) {
                anim.fail("Download stopped: ${e.message ?: e.javaClass.simpleName}")
            }
        }.apply { isDaemon = true; name = "settings-anim-main" }.start()
    }

    /** Deletes the data, downloads and rebuilds it. */
    fun redownloadData(ctx: Context) {
        if (data.running) return
        val app = ctx.applicationContext
        data.begin("Starting…")
        Thread {
            try {
                DataStore.clear(app)
                val r = DataStore.download(app) { d, t, name ->
                    data.progress(if (t > 0) d.toFloat() / t else 0f, if (name == "Building") "Building the Pokémon list…" else "Downloading game data…")
                }
                if (r.isSuccess) data.done() else data.fail(r.exceptionOrNull()?.message ?: "The download failed.")
            } catch (e: Throwable) {
                data.fail("Download stopped: ${e.message ?: e.javaClass.simpleName}")
            }
        }.apply { isDaemon = true; name = "settings-data" }.start()
    }
}

/** Game options, data and pictures, save backups, Updates, Diagnostics and About. */
class SettingsActivity : Activity() {
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private var dataCard: LinearLayout? = null
    private var nameEdit: EditText? = null
    private var dialog: AlertDialog? = null
    private var handledDataDone = false

    // views of the data card that the download jobs update
    private var animBar: ProgressBlock? = null
    private var dataBar: ProgressBlock? = null

    private fun dp(v: Int) = Ui.dp(this, v)
    private val state: GameState? get() = GameRepo.state

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(Ui.appBar(this, "Settings", back = true))
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(28))
        }
        scroll = ScrollView(this).apply { addView(content) }
        root.addView(scroll, Form.lp(h = 0, weight = 1f))
        setContentView(root)
        render()
        if (state == null) GameRepo.load(this) { render() }
    }

    override fun onStart() {
        super.onStart()
        SettingsJobs.anim.listener = { onAnim() }
        SettingsJobs.data.listener = { onData() }
        if (SettingsJobs.data.state == BgJob.State.DONE) onData() // finished while this screen was away
    }

    override fun onResume() {
        super.onResume()
        Updates.onResume(this)
    }

    override fun onPause() {
        saveName()
        Updates.onPause(this)
        super.onPause()
    }

    override fun onStop() {
        SettingsJobs.anim.listener = null
        SettingsJobs.data.listener = null
        super.onStop()
    }

    override fun onDestroy() {
        dialog?.dismiss()
        super.onDestroy()
    }

    // ---------- page ----------

    private fun render() {
        saveName()
        content.removeAllViews()
        animBar = null; dataBar = null; nameEdit = null
        val st = state
        if (st != null) {
            content.addView(Form.sect(this, "Game"))
            content.addView(gameCard(st.settings))
            content.addView(Form.sect(this, "When a wild Pokémon faints"))
            content.addView(faintCard(st.settings))
            content.addView(Form.sect(this, "Always catch", "even with \"Defeat\""))
            content.addView(protectCard(st.settings))
        } else {
            content.addView(Form.sect(this, "Game"))
            content.addView(Form.cardOf(this, listOf(
                Form.row(this, "The game isn't loaded", "Its options show here once it is. Data and backups below still work.", null),
            )))
        }
        content.addView(Form.sect(this, "Data and pictures"))
        val dc = buildDataCard()
        dataCard = dc
        content.addView(dc)
        content.addView(Form.sect(this, "Save"))
        content.addView(saveCard())
        content.addView(Form.sect(this, "App"))
        content.addView(appCard())
        content.addView(Form.sect(this, "About"))
        content.addView(aboutCard())
    }

    // ---------- game ----------

    /** Changes a setting through the game (main thread) and saves. */
    private fun edit(f: (GameSettings) -> Unit) {
        GameRepo.run(this) { g -> f(g.state.settings) }
        GameRepo.scheduleSave(this)
    }

    private fun gameCard(s: GameSettings): View {
        val rows = mutableListOf<View>()

        // generations
        val gensBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        gensBox.addView(Form.row(this, "Generations", "At least one. Default 1–8, like Ankimon.", null))
        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), 0, dp(14), dp(12))
        }
        for (g in 1..9) {
            lateinit var chip: TextView
            chip = Form.toggleChip(this, "$g", g in s.gens) {
                val on = g in s.gens
                if (on && s.gens.size <= 1) { Ui.toast(this, "At least one generation must stay on"); return@toggleChip }
                edit { if (on) it.gens.remove(g) else it.gens.add(g) }
                Form.setChip(chip, !on)
            }
            chipRow.addView(chip, Form.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { if (g > 1) it.marginStart = dp(4) })
        }
        gensBox.addView(chipRow)
        rows += gensBox

        rows += Form.stepperRow(this, "Answers per attack", "How many answers make one attack. Ankimon: 2.", s.cardsPerRound, 1, 4, 1) { v ->
            edit { it.cardsPerRound = v }
        }
        rows += Form.stepperRow(this, "Daily goal", "Reviews a day. A bigger goal also makes rare Pokémon more likely.", s.dailyAverage, 20, 500, 10) { v ->
            edit { it.dailyAverage = v }
        }
        rows += Form.stepperRow(this, "New day starts at", "Answers before this hour count for the day before.", s.dayStartHour, 0, 12, 1, { "$it:00" }) { v ->
            edit { it.dayStartHour = v }
        }

        // trainer name
        val field = EditText(this).apply {
            setText(s.trainerName)
            typeface = Ui.font(this@SettingsActivity, 700)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Ui.c(this@SettingsActivity, R.color.ag_text))
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setSingleLine()
            filters = arrayOf(InputFilter.LengthFilter(16))
            imeOptions = EditorInfo.IME_ACTION_DONE
            background = Ui.rounded(Ui.c(this@SettingsActivity, R.color.ag_mantle), 10, this@SettingsActivity)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            minWidth = dp(130)
            setOnEditorActionListener { v, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) { saveName(); v.clearFocus(); true } else false
            }
            setOnFocusChangeListener { _, focus -> if (!focus) saveName() }
        }
        nameEdit = field
        rows += Form.row(this, "Trainer name", null, field)

        val holder = state?.mon(s.xpShareUid)
        rows += Form.row(this, "Exp. Share holder", "Change it in Team.", Ui.text(this, 14f, 800, R.color.ag_sub1).apply {
            text = holder?.let { "${it.name} Lv ${it.level}" } ?: "Nobody"
        })
        rows += Form.row(this, "Wishlist", "Always caught. Tap ☆ in the Pokédex to add or remove.", Ui.text(this, 14f, 800, R.color.ag_sub1).apply {
            text = "${s.wishlist.size}"
        })
        return Form.cardOf(this, rows)
    }

    private fun saveName() {
        val field = nameEdit ?: return
        val s = state?.settings ?: return
        val name = field.text.toString().trim().ifEmpty { "Trainer" }
        if (name != s.trainerName) edit { it.trainerName = name }
        if (field.text.toString() != name) field.setText(name)
    }

    private fun faintCard(s: GameSettings): View {
        val options = listOf(
            1 to "Always catch",
            2 to "Defeat (catch shinies and protected kinds)",
            3 to "Catch species you don't have yet",
        )
        val ticks = HashMap<Int, ImageView>()
        val rows = options.map { (mode, label) ->
            val tick = ImageView(this).apply {
                setImageResource(R.drawable.ic_check)
                setColorFilter(Ui.c(this@SettingsActivity, R.color.ag_mauve), PorterDuff.Mode.SRC_IN)
                visibility = if (s.autoMode == mode) View.VISIBLE else View.INVISIBLE
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            }
            ticks[mode] = tick
            Form.row(this, label, if (mode == 3) "Default (Ankimon's automatic mode 3)" else null, tick) {
                edit { it.autoMode = mode }
                ticks.forEach { (m, t) -> t.visibility = if (m == mode) View.VISIBLE else View.INVISIBLE }
            }
        }
        return Form.cardOf(this, rows)
    }

    private fun protectCard(s: GameSettings): View = Form.cardOf(this, listOf(
        Form.switchRow(this, "Legendary", null, s.protectLegendary) { v -> edit { it.protectLegendary = v } },
        Form.switchRow(this, "Mythical", null, s.protectMythical) { v -> edit { it.protectMythical = v } },
        Form.switchRow(this, "Ultra Beast", null, s.protectUltra) { v -> edit { it.protectUltra = v } },
        Form.switchRow(this, "Starter", null, s.protectStarter) { v -> edit { it.protectStarter = v } },
    ))

    // ---------- data and pictures ----------

    private fun buildDataCard(): LinearLayout {
        val anim = SettingsJobs.anim
        val dat = SettingsJobs.data
        animBar = null; dataBar = null
        val box = Ui.card(this)
        box.addView(Form.row(this, "Game data", null, Ui.text(this, 14f, 800, R.color.ag_sub1).apply {
            text = sizeText(DataStore.size(this@SettingsActivity))
        }))
        box.addView(Form.divider(this))
        box.addView(Form.row(this, "Pictures", null, Ui.text(this, 14f, 800, R.color.ag_sub1).apply {
            text = sizeText(Sprites.size(this@SettingsActivity))
        }))
        box.addView(Form.divider(this))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
        }
        // battle animations
        val animBtn = Form.secondary(this, "Download all battle animations (about 80 MB)") { startAnimations() }
        Form.enable(animBtn, !anim.running && !dat.running)
        actions.addView(animBtn)
        when (anim.state) {
            BgJob.State.RUNNING -> {
                val p = ProgressBlock(this).also { it.show(anim.fraction, anim.label) }
                animBar = p
                actions.addView(p, Form.lp(this, 10))
                actions.addView(Form.secondary(this, "Cancel", R.color.ag_red) { anim.cancel() }, Form.lp(this, 8))
            }
            BgJob.State.DONE -> actions.addView(Form.body(this, anim.label, 12), Form.lp(this, 8))
            BgJob.State.FAILED -> actions.addView(Form.body(this, anim.error ?: "Failed.", 12, R.color.ag_red), Form.lp(this, 8))
            BgJob.State.IDLE -> {}
        }
        // data again
        val dataBtn = Form.secondary(this, "Download game data again") { confirmRedownload() }
        Form.enable(dataBtn, !dat.running && !anim.running)
        actions.addView(dataBtn, Form.lp(this, 10))
        when (dat.state) {
            BgJob.State.RUNNING -> {
                val p = ProgressBlock(this).also { it.show(dat.fraction, dat.label) }
                dataBar = p
                actions.addView(p, Form.lp(this, 10))
            }
            BgJob.State.FAILED -> actions.addView(
                Form.body(this, (dat.error ?: "Failed.") + " Tap the button to try again.", 12, R.color.ag_red), Form.lp(this, 8))
            else -> {}
        }
        // clear
        val clearBtn = Form.secondary(this, "Clear picture cache", R.color.ag_red) { confirmClear() }
        Form.enable(clearBtn, !anim.running)
        actions.addView(clearBtn, Form.lp(this, 10))
        actions.addView(Form.body(this, "Pictures download again as you need them.", 12, R.color.ag_sub0), Form.lp(this, 6))
        box.addView(actions)
        return box
    }

    private fun rebuildData() {
        val old = dataCard ?: return
        val parent = old.parent as? ViewGroup ?: return
        val i = parent.indexOfChild(old)
        parent.removeViewAt(i)
        val fresh = buildDataCard()
        dataCard = fresh
        parent.addView(fresh, i)
    }

    private fun onAnim() {
        val j = SettingsJobs.anim
        val b = animBar
        if (j.state == BgJob.State.RUNNING && b != null) b.show(j.fraction, j.label) else rebuildData()
    }

    private fun onData() {
        val j = SettingsJobs.data
        val b = dataBar
        if (j.state == BgJob.State.RUNNING && b != null) { b.show(j.fraction, j.label); return }
        if (j.state == BgJob.State.DONE && !handledDataDone) {
            handledDataDone = true
            j.reset()
            Ui.toast(this, "Game data downloaded")
            GameRepo.load(this) { handledDataDone = false; rebuildData() }
            return
        }
        rebuildData()
    }

    private fun startAnimations() {
        val data = GameRepo.data ?: DataStore.load(this)
        if (data == null) { Ui.toast(this, "Download the game data first"); return }
        val gens = state?.settings?.gens?.toSet() ?: com.husarp.ankigo.game.Tuning.DEFAULT_GENS
        val list = data.species.values.filter { it.gen in gens }.sortedBy { it.num }
        SettingsJobs.startAnimations(this, list)
    }

    private fun confirmRedownload() {
        confirm("Download game data again?", "The Pokémon data (about 4.3 MB) is deleted and downloaded fresh. Your game isn't touched.", "Download") {
            SettingsJobs.redownloadData(this)
        }
    }

    private fun confirmClear() {
        confirm("Clear picture cache?", "All downloaded pictures and animations are deleted (${sizeText(Sprites.size(this))}). They download again when needed.", "Clear") {
            Sprites.clear(this)
            Ui.toast(this, "Picture cache cleared")
            rebuildData()
        }
    }

    private fun sizeText(bytes: Long): String = when {
        bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> "${bytes / 1_000} KB"
        else -> "$bytes B"
    }

    // ---------- save ----------

    private val dateFmt get() = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

    private fun saveCard(): View {
        val st = state
        val last = if (st == null || st.savedAt == 0L) "Not saved yet" else "${dateFmt.format(Date(st.savedAt))} (#${st.seq})"
        val box = Ui.card(this)
        box.addView(Form.row(this, "Last saved", null, Ui.text(this, 13f, 800, R.color.ag_sub1).apply { text = last }))
        box.addView(Form.divider(this))
        val btns = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
        }
        val export = Form.secondary(this, "Export save") { startExport() }
        Form.enable(export, st != null)
        btns.addView(export)
        btns.addView(Form.secondary(this, "Import save…") { startImport() }, Form.lp(this, 8))
        btns.addView(Form.secondary(this, "Restore a backup…") { listBackups() }, Form.lp(this, 8))
        btns.addView(Form.body(this, "Importing or restoring keeps your current game as a backup first.", 12, R.color.ag_sub0), Form.lp(this, 8))
        box.addView(btns)
        return box
    }

    private fun startExport() {
        if (state == null) return
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
            .putExtra(Intent.EXTRA_TITLE, "ankigo-save-$day.json")
        try { startActivityForResult(i, REQ_EXPORT) } catch (_: Exception) { Ui.toast(this, "No file picker found on this phone") }
    }

    private fun startImport() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        try { startActivityForResult(i, REQ_IMPORT) } catch (_: Exception) { Ui.toast(this, "No file picker found on this phone") }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQ_EXPORT -> {
                val st = state ?: return
                val text = SaveCodec.encode(st) // on the main thread, which owns the state
                val app = applicationContext
                Thread {
                    val ok = try {
                        app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        true
                    } catch (e: Exception) { false }
                    runOnUiThread { Ui.toast(app, if (ok) "Save exported" else "Couldn't write the file") }
                }.start()
            }
            REQ_IMPORT -> readImport(uri)
        }
    }

    /** Reads and checks the picked file off the main thread; shows what is in it before anything changes. */
    private fun readImport(uri: android.net.Uri) {
        val app = applicationContext
        Thread {
            var text: String? = null
            var parsed: GameState? = null
            var problem: String? = null
            try {
                val bytes = app.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                if (bytes.size > MAX_IMPORT_BYTES) throw SaveFormatException("the file is too big to be an AnkiGo save")
                text = String(bytes, Charsets.UTF_8)
                parsed = SaveCodec.decode(text)
            } catch (e: SaveFormatException) {
                problem = if (e.newerSchema) "This save is from a newer AnkiGo. Update AnkiGo first." else "That file isn't a valid AnkiGo save (${e.message})."
            } catch (e: Exception) {
                problem = "Couldn't read that file."
            }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                val st = parsed
                val body = text
                if (st == null || body == null) {
                    info("Import failed", (problem ?: "Couldn't read that file.") + "\n\nNothing was changed.")
                } else {
                    confirm("Import this save?", describe(st) + "\n\nYour current game is kept as a backup.", "Import") { applyRestore(body, null) }
                }
            }
        }.start()
    }

    private fun describe(st: GameState): String {
        val name = st.settings.trainerName
        val date = if (st.savedAt > 0) dateFmt.format(Date(st.savedAt)) else "unknown date"
        return "Trainer: $name\nPokémon: ${st.mons.size}\nSaved: $date (#${st.seq})"
    }

    private class Backup(val file: File, val state: GameState)

    private fun listBackups() {
        val app = applicationContext
        Thread {
            val store = GameRepo.store(app)
            val found = store.backups().mapNotNull { f -> store.read(f)?.let { Backup(f, it) } }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (found.isEmpty()) {
                    info("No backups yet", "Backups appear after the game has been saved a few times.")
                    return@runOnUiThread
                }
                val labels = found.map { b ->
                    val kind = if (b.file.name == "save.bak") "previous save" else "daily copy"
                    val date = if (b.state.savedAt > 0) dateFmt.format(Date(b.state.savedAt)) else b.file.name
                    "$date · #${b.state.seq} · ${b.state.mons.size} Pokémon\n($kind)"
                }.toTypedArray<CharSequence>()
                dialog?.dismiss()
                dialog = AlertDialog.Builder(this).setTitle("Restore a backup").setItems(labels) { _, which ->
                    val b = found[which]
                    confirm("Restore this backup?", describe(b.state) + "\n\nYour current game is kept as a backup.", "Restore") { applyRestore(null, b.file) }
                }.setNegativeButton("Cancel", null).show()
            }
        }.start()
    }

    /**
     * Makes the chosen text (import) or backup file the current game. Both go through SaveStore.restore so the new state's
     * seq is above everything on disk (otherwise the next start would load the newer file again).
     */
    private fun applyRestore(importText: String?, file: File?) {
        val app = applicationContext
        Thread {
            var restored: GameState? = null
            try {
                val store = GameRepo.store(app)
                val source = file ?: File(app.cacheDir, "import.json").also { it.writeText(importText!!, Charsets.UTF_8) }
                restored = store.restore(source)
                if (file == null) source.delete()
            } catch (_: Exception) { }
            runOnUiThread {
                val st = restored
                if (st == null) {
                    if (!isFinishing) info("Couldn't restore", "The file couldn't be read. Nothing was changed.")
                    return@runOnUiThread
                }
                GameRepo.state?.let { cur -> if (st.seq < cur.seq) st.seq = cur.seq }
                GameRepo.replaceState(app, st)
                Ui.toast(app, "Restored: ${st.settings.trainerName}, ${st.mons.size} Pokémon")
                if (!isFinishing) render()
            }
        }.start()
    }

    // ---------- app + about ----------

    private fun appCard(): View {
        val chevron = { ImageView(this).apply {
            setImageResource(R.drawable.ic_back)
            rotation = 180f
            setColorFilter(Ui.c(this@SettingsActivity, R.color.ag_sub0), PorterDuff.Mode.SRC_IN)
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
        } }
        return Form.cardOf(this, listOf(
            Form.row(this, "Updates · version ${Updates.current(this)}", "Check for a new AnkiGo", chevron()) {
                startActivity(Intent(this, UpdatesActivity::class.java))
            },
            Form.row(this, "Diagnostics", "LIVE watch, checks and the report", chevron()) {
                startActivity(Intent(this, DiagnosticsActivity::class.java))
            },
        ))
    }

    private fun aboutCard(): View {
        val box = Ui.card(this).apply { setPadding(dp(14), dp(12), dp(14), dp(14)) }
        box.addView(Ui.text(this, 14f, 800, R.color.ag_text).apply { text = "AnkiGo ${Updates.current(this@SettingsActivity)}" })
        box.addView(Form.body(this,
            "Pokémon and Pokémon names © Nintendo / Creatures / GAME FREAK. Data: PokeAPI, Pokémon Showdown. " +
                "Game rules from Ankimon (GPL-3). Font: Nunito (SIL OFL).", 12, R.color.ag_sub0), Form.lp(this, 6))
        return box
    }

    // ---------- dialogs ----------

    private fun confirm(title: String, message: String, yes: String, onYes: () -> Unit) {
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton(yes) { _, _ -> onYes() }.setNegativeButton("Cancel", null).show()
    }

    private fun info(title: String, message: String) {
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }

    companion object {
        private const val REQ_EXPORT = 11
        private const val REQ_IMPORT = 12
        private const val MAX_IMPORT_BYTES = 64 * 1024 * 1024
    }
}
