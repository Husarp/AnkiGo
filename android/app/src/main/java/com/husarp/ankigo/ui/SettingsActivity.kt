package com.husarp.ankigo.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
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
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Settings as GameSettings
import com.husarp.ankigo.game.save.SaveCodec
import com.husarp.ankigo.game.save.SaveFormatException
import com.husarp.ankigo.game.shownName
import com.husarp.ankigo.overlay.FloatBattleService
import com.husarp.ankigo.overlay.Overlay
import com.husarp.ankigo.update.Updates
import com.husarp.ankigo.update.UpdatesActivity
import java.io.File
import java.lang.ref.WeakReference
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

    /**
     * Front + back battle sprites of every species of [gens] with 4 parallel workers; [BgJob.cancel] stops it early.
     * [preloaded] is the game data when it is already in memory; otherwise it is read here, off the main thread.
     */
    fun startAnimations(ctx: Context, gens: Set<Int>, preloaded: GameData?) {
        if (anim.running) return
        val app = ctx.applicationContext
        Sprites.setEnabled(app, true)
        anim.begin("Starting…")
        Thread {
            try {
                val data = preloaded ?: DataStore.load(app)
                if (data == null) { anim.fail("Download the game data first."); return@Thread }
                val species = data.species.values.filter { it.gen in gens }.sortedBy { it.num }
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
                else { DownloadsDone.mark(app, DownloadsDone.ANIMATIONS, gens); anim.done("All battle animations downloaded.") }
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
                val begun = android.os.SystemClock.elapsedRealtime()
                val r = DataStore.download(app) { d, t, name ->
                    data.progress(if (t > 0) d.toFloat() / t else 0f, if (name == "Building") "Building the Pokémon list…" else "Game data (total estimated) · ${DisplayNumbers.progress(d, t, android.os.SystemClock.elapsedRealtime() - begun)}")
                }
                if (r.isSuccess) data.done() else data.fail(r.exceptionOrNull()?.message ?: "The download failed.")
            } catch (e: Throwable) {
                data.fail("Download stopped: ${e.message ?: e.javaClass.simpleName}")
            }
        }.apply { isDaemon = true; name = "settings-data" }.start()
    }
}

/**
 * The Settings tab: a home list of sections (icon, title, one summary line); a tap opens the section in place
 * (back arrow in the app bar). Works without a loaded game.
 */
class SettingsPage(base: Context, host: PageHost) : GamePage(base, host) {
    override val tab: BottomNav.Tab? = BottomNav.Tab.SETTINGS
    override val needsGame = false

    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var barHolder: FrameLayout
    private var dataCard: LinearLayout? = null
    private var windowsCard: LinearLayout? = null
    private var nameEdit: EditText? = null
    private var dialog: AppDialog? = null
    private var handledDataDone = false

    /** The open section, or null on the home list. */
    var section: SettingsSection? = null
        private set

    /** What the search box holds (kept across opening a result and coming back), and the setting to scroll to after a render. */
    private var query = ""
    private var pendingFocus: SettingInfo? = null

    // views of the data card that the download jobs update
    private var animBar: ProgressBlock? = null
    private var dataBar: ProgressBlock? = null
    private var picBar: ProgressBlock? = null

    private fun dp(v: Int) = Ui.dp(this, v)
    private val loaded: GameState? get() = GameRepo.state
    private val main = Handler(Looper.getMainLooper())

    // Attached in onShow, detached in onGone only if still ours (a recreated page may have taken over the job screens).
    private val animListener: () -> Unit = { onAnim() }
    private val dataListener: () -> Unit = { onData() }
    private val picListener: () -> Unit = { onPics() }

    // interface size: applied once the player stops pressing + / -
    private var pendingScale: Int? = null
    private val applyScale = Runnable { flushScale(recreate = true) }

    override fun buildBar(): View {
        barHolder = FrameLayout(this)
        rebuildBar()
        return barHolder
    }

    private fun rebuildBar() {
        val s = section
        barHolder.removeAllViews()
        barHolder.addView(Ui.appBar(this, s?.title ?: "Settings", back = s != null, onBack = { goHome() }),
            FrameLayout.LayoutParams(-1, -2))
    }

    /** Back from a section to the list. False when already on the list (the caller goes on with its own back). */
    fun goHome(): Boolean {
        if (section == null) return false
        open(null)
        return true
    }

    /** Opens [s] (null = the list). */
    fun open(s: SettingsSection?) {
        saveName()
        section = s
        render()
        if (::scroll.isInitialized) scroll.scrollTo(0, 0)
    }

    override fun buildBody(): View {
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(28))
        }
        scroll = ScrollView(this).apply { addView(content) }
        live = WeakReference(this)
        render()
        if (loaded == null) GameRepo.load(this) { if (alive) render() }
        return scroll
    }

    override fun onShow() {
        super.onShow()
        SettingsJobs.anim.listener = animListener
        SettingsJobs.data.listener = dataListener
        if (SettingsJobs.anim.state != BgJob.State.IDLE) onAnim() // progress / result that changed while this screen was away
        FirstRunJob.job.listener = picListener
        if (SettingsJobs.data.state == BgJob.State.DONE) onData() // finished while this screen was away
        if (picsFromSettings && FirstRunJob.job.state != BgJob.State.RUNNING) onPics()
        rebuildWindows() // the overlay permission may have changed in Android's settings meanwhile
    }

    override fun onHide() {
        saveName()
        flushScale(recreate = false)
        super.onHide()
    }

    override fun onGone() {
        if (SettingsJobs.anim.listener === animListener) SettingsJobs.anim.listener = null
        if (SettingsJobs.data.listener === dataListener) SettingsJobs.data.listener = null
        if (FirstRunJob.job.listener === picListener) FirstRunJob.job.listener = null
        if (live?.get() === this) live = null
        main.removeCallbacks(applyScale)
        dialog?.dismiss()
        super.onGone()
    }

    /** Runs [f] on the main thread now (or posts it from another thread). */
    private fun runOnUi(f: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) f() else main.post(f)
    }

    // ---------- page ----------

    override fun render() {
        saveName()
        content.removeAllViews()
        animBar = null; dataBar = null; picBar = null; nameEdit = null; dataCard = null; windowsCard = null
        rebuildBar()
        val st = loaded
        when (section) {
            null -> home()
            SettingsSection.LOOK -> content.addView(lookCard(), Form.lp(this, 12))
            SettingsSection.GAME -> if (st != null) {
                gameGroups(gameRows(st.settings) + faintRows(st.settings) + duplicatesRows(st.settings) + protectRows(st.settings))
            } else {
                content.addView(Form.cardOf(this, listOf(
                    Form.row(this, "The game isn't loaded", "Its options show here once it is. Data and backups still work.", null),
                )), Form.lp(this, 12))
            }
            SettingsSection.STUDY -> content.addView(studyCard(), Form.lp(this, 12))
            SettingsSection.BATTLE -> battleSection()
            SettingsSection.REMINDERS -> {
                // a search result for a reminder setting: its sheet opens (the rows here are custom views, there is no row to highlight)
                val openFor = pendingFocus?.takeIf { it.section == CatalogSection.REMINDERS }?.id
                content.addView(com.husarp.ankigo.remind.ReminderSettingsView.build(this, openFor), Form.lp(this, 12))
            }
            SettingsSection.SOUND -> content.addView(soundCard(this), Form.lp(this, 12))
            SettingsSection.DOWNLOADS -> {
                val dc = buildDataCard()
                dataCard = dc
                content.addView(dc, Form.lp(this, 12))
            }
            SettingsSection.ANKIMON -> content.addView(ankimonCard(), Form.lp(this, 12))
            SettingsSection.SAVE -> content.addView(saveCard(), Form.lp(this, 12))
            SettingsSection.ABOUT -> content.addView(aboutCard(), Form.lp(this, 12))
            SettingsSection.HELP, SettingsSection.UPDATES, SettingsSection.DIAGNOSTICS -> home() // these start their own screens; never open in place
        }
        explain(content)
        pendingFocus?.let { focusOn(it); pendingFocus = null }
    }

    // ---------- home ----------

    private val dateFmt get() = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

    private fun summaryOf(s: SettingsSection): String {
        val st = loaded?.settings
        val study = getSharedPreferences("study", 0)
        return when (s) {
            SettingsSection.LOOK -> SettingsSummary.look(AppPrefs.theme(this), AppPrefs.accent(this), AppPrefs.uiScale(this))
            SettingsSection.GAME -> if (st == null) "The game isn't loaded" else SettingsSummary.game(st.gens, st.cardsPerRound, com.husarp.ankigo.game.DailyGoal.forSettings(st, loaded?.day), st.goalByWeekday != null)
            SettingsSection.STUDY -> SettingsSummary.study(study.getBoolean("inAnkiDroid", false), study.getBoolean("swipe", false), study.getInt("zoom", 100))
            SettingsSection.BATTLE -> SettingsSummary.battle(AppPrefs.leaveMode(this), AppPrefs.statsBarSmall(this), AppPrefs.compactCards(this), AppPrefs.cardsUnderOwn(this))
            SettingsSection.REMINDERS -> SettingsSummary.reminders(com.husarp.ankigo.remind.ReminderStore.load(this))
            SettingsSection.HELP -> "How the game works · fix a problem"
            SettingsSection.SOUND -> soundSummary(this)
            SettingsSection.DOWNLOADS -> SettingsSummary.downloads(DataStore.size(this), Sprites.size(this),
                SettingsJobs.anim.running || SettingsJobs.data.running || FirstRunJob.job.running)
            SettingsSection.ANKIMON -> "Import a PC save · export one back"
            SettingsSection.SAVE -> loaded.let { SettingsSummary.save(it?.savedAt, it?.seq) { t -> dateFmt.format(Date(t)) } }
            SettingsSection.UPDATES -> SettingsSummary.updates(Updates.current(this))
            SettingsSection.DIAGNOSTICS -> "LIVE watch, checks and the report"
            SettingsSection.ABOUT -> "AnkiGo ${Updates.current(this)} · credits"
        }
    }

    private lateinit var homeBox: LinearLayout
    private lateinit var resultsBox: LinearLayout

    /** The home list: a search box on top, then the section cards; while the box has words, the matching settings replace the cards. */
    private fun home() {
        content.addView(SettingsHomeSearch.box(this, query) { q -> query = q; showResults() }, Form.lp(this, 4))
        homeBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        SettingsSection.GROUPS.forEachIndexed { i, group ->
            homeBox.addView(Form.sect(this, SettingsSection.GROUP_TITLES[i]), Form.lp(this, 0))
            homeBox.addView(SettingsHome.group(this, group.map { s ->
                SettingsHome.row(this, s, summaryOf(s)) { choose(s) }
            }), Form.lp(this, 0))
        }
        homeBox.addView(Ui.text(this, 11f, 700, R.color.ag_sub0).apply { text = "AnkiGo ${Updates.current(this@SettingsPage)}"; gravity = Gravity.CENTER }, Form.lp(this, 16))
        resultsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(homeBox)
        content.addView(resultsBox)
        showResults()
    }

    private fun showResults() {
        val q = query.trim()
        resultsBox.removeAllViews()
        homeBox.visibility = if (q.isEmpty()) View.VISIBLE else View.GONE
        if (q.isEmpty()) return
        resultsBox.addView(SettingsHomeSearch.results(this, SettingsSearch.match(q), q) { openSetting(it) }, Form.lp(this, 12))
    }

    /** Puts [q] into the search box of the home list (restores a search, and lets the render tests show one). */
    internal fun showSearch(q: String) {
        query = q
        if (section == null && ::content.isInitialized) render()
    }

    /** Opens the section that holds [info] and scrolls to its row (a section that starts its own screen just starts it). */
    fun openSetting(info: SettingInfo) {
        val target = SettingsSearch.sectionOf(info)
        pendingFocus = info.takeIf { target.opens }
        choose(target)
    }

    /** Scrolls to the row of [info] in the open section and tints it for a moment; nothing happens when the section has no such row. */
    private fun focusOn(info: SettingInfo) {
        val row = Form.rowsIn(content).firstOrNull { Form.titleOf(it) == info.title } ?: return
        scroll.post {
            var y = 0
            var v: View = row
            while (v !== content && v.parent is View) { y += v.top; v = v.parent as View }
            scroll.smoothScrollTo(0, (y - dp(80)).coerceAtLeast(0))
        }
        SettingsHomeSearch.flash(row)
    }

    /** Gives every settings row inside [root] its one-line explanation from [SettingsCatalog] (rows without an entry keep what they have). */
    private fun explain(root: View) {
        val s = section ?: return
        val here = SettingsSearch.entriesOf(s)
        for (row in Form.rowsIn(root)) {
            val title = Form.titleOf(row) ?: continue
            here.firstOrNull { it.title == title }?.let { Form.setExplain(row, it.explain) }
        }
    }

    /** Game section: its rows in small groups named like the catalog's (Battle, Difficulty, Pokémon, Always catch). */
    private fun gameGroups(rows: List<View>) {
        val game = SettingsSearch.entriesOf(SettingsSection.GAME)
        val subs = rows.map { r -> game.firstOrNull { it.title == Form.titleOf(r) }?.sub ?: "" }
        for (g in SettingsSearch.groups(subs, GAME_GROUPS)) {
            if (g.title.isNotEmpty()) content.addView(Form.sect(this, g.title, if (g.title == "Always catch") "even with \"Defeat\"" else null))
            content.addView(Form.cardOf(this, g.rows.map { rows[it] }), if (g.title.isEmpty()) Form.lp(this, 12) else Form.lp())
        }
    }

    private fun choose(s: SettingsSection) {
        when (s) {
            SettingsSection.HELP -> com.husarp.ankigo.help.HelpActivity.open(host.hostActivity ?: this)
            SettingsSection.UPDATES -> startScreen(Intent(this, UpdatesActivity::class.java))
            SettingsSection.DIAGNOSTICS -> startScreen(Intent(this, DiagnosticsActivity::class.java))
            else -> open(s)
        }
    }

    // ---------- look ----------

    private fun lookCard(): View {
        val themes = ThemeMode.entries
        val accents = Accent.entries
        val theme = Form.stackRow(this, "Theme", null, Form.segmented(this, themes.map { it.label }, themes.indexOf(AppPrefs.theme(this))) { i ->
            AppLook.setTheme(this, themes[i]); looked()
        })
        val current = AppPrefs.accent(this)
        val accent = Form.stackRow(this, "Accent", null,
            Form.swatches(this, accents.map { it.label to getColor(it.res) }, accents.indexOf(current)) { i ->
                AppLook.setAccent(this, accents[i]); looked()
            },
            right = Ui.text(this, 13f, 800, R.color.ag_sub1).apply { text = current.label })
        val scales = AppLook.SCALES
        val size = Form.stepperRow(this, "Interface size", "Text, cards and Pokémon get bigger or smaller together.",
            scales.indexOf(AppPrefs.uiScale(this)).coerceAtLeast(0), 0, scales.lastIndex, 1, { "${scales[it]} %" }) { i ->
            pendingScale = scales[i]
            main.removeCallbacks(applyScale)
            main.postDelayed(applyScale, SCALE_DELAY_MS)
        }
        val bars = BarStyle.entries
        val barStyle = Form.stackRow(this, "Progress bars", "XP blue, goals orange, Pokédex teal; or all in the accent, or a gradient.",
            Form.segmented(this, bars.map { it.label }, bars.indexOf(AppPrefs.barStyle(this))) { i ->
                AppLook.setBarStyle(this, bars[i]); looked()
            })
        val typeStyles = TypeStyle.entries
        val typeStyle = Form.stackRow(this, "Show types as", "Type names, small type icons, or both.",
            Form.segmented(this, typeStyles.map { it.label }, typeStyles.indexOf(AppPrefs.typeStyle(this))) { i ->
                AppLook.setTypeStyle(this, typeStyles[i]); looked()
            })
        return Form.cardOf(this, listOf(theme, accent, barStyle, typeStyle, size))
    }

    /** The look changed: this screen recreates itself (Activity) or tells the player the window needs reopening (floating card). */
    private fun looked() {
        val a = host.hostActivity
        if (a != null) main.post { if (alive) a.recreate() }
        else {
            Ui.toast(this, "Applies when you reopen the window")
            render()
        }
    }

    private fun flushScale(recreate: Boolean) {
        val v = pendingScale ?: return
        pendingScale = null
        main.removeCallbacks(applyScale)
        AppLook.setScale(this, v)
        if (recreate) looked()
    }

    /** Remembers the open section across an Activity recreation (look change, rotation). */
    fun restoreSection(name: String?) {
        section = SettingsSection.byName(name)?.takeIf { it.opens }
        if (::content.isInitialized) render()
    }

    private fun studyCard(): View = Form.cardOf(this, listOf(
            Form.switchRow(this, "Tapping a deck opens AnkiDroid", "Off: study inside AnkiGo", getSharedPreferences("study", 0).getBoolean("inAnkiDroid", false)) {
                getSharedPreferences("study", 0).edit().putBoolean("inAnkiDroid", it).apply()
            },
            Form.switchRow(this, "Remove battle animations", "Also follows Android's Remove animations setting", getSharedPreferences("study", 0).getBoolean("reduceMotion", false)) {
                getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", it).apply()
            },
            Form.switchRow(this, "Tap the card to show the answer", "Off: use the Show answer button, a key or a swipe", getSharedPreferences("study", 0).getBoolean("tapShow", true)) {
                getSharedPreferences("study", 0).edit().putBoolean("tapShow", it).apply(); render()
            },
            Form.switchRow(this, "Tap again to hide the answer", "Tap the answer to read the question again", getSharedPreferences("study", 0).getBoolean("tapHide", false)) {
                getSharedPreferences("study", 0).edit().putBoolean("tapHide", it).apply()
            }.also { Form.enable(it, getSharedPreferences("study", 0).getBoolean("tapShow", true)) },
            Form.switchRow(this, "Enter = Show answer", "Pressing Enter in a type-in answer shows the answer", getSharedPreferences("study", 0).getBoolean(com.husarp.ankigo.study.TypeInRules.PREF_ENTER, com.husarp.ankigo.study.TypeInRules.ENTER_DEFAULT)) {
                getSharedPreferences("study", 0).edit().putBoolean(com.husarp.ankigo.study.TypeInRules.PREF_ENTER, it).apply()
            },
            Form.switchRow(this, "Vibrate on answer", null, getSharedPreferences("study", 0).getBoolean("haptic", true)) {
                getSharedPreferences("study", 0).edit().putBoolean("haptic", it).apply()
            },
            Form.switchRow(this, "Swipe to answer", "Answer side: ← Again · ↓ Hard · → Good · ↑ Easy", getSharedPreferences("study", 0).getBoolean("swipe", false)) {
                getSharedPreferences("study", 0).edit().putBoolean("swipe", it).apply()
            },
            Form.switchRow(this, "Read battle log with TalkBack", "Turn off to keep the card reading uninterrupted", getSharedPreferences("study", 0).getBoolean("speakBattle", true)) {
                getSharedPreferences("study", 0).edit().putBoolean("speakBattle", it).apply()
            },
            Form.stepperRow(this, "Card text size", null, getSharedPreferences("study", 0).getInt("zoom", 100), 80, 150, 10, { "$it%" }) {
                getSharedPreferences("study", 0).edit().putInt("zoom", it).apply()
            },
            Form.row(this, "Answer timer", null, valueText(getSharedPreferences("study", 0).getString("timer", com.husarp.ankigo.study.StudyTiming.AS_DECK))) {
                Pickers.show(AppDialog.Builder(this).setItems(com.husarp.ankigo.study.StudyTiming.TIMER_MODES.toTypedArray()) { _, i ->
                    getSharedPreferences("study", 0).edit().putString("timer", com.husarp.ankigo.study.StudyTiming.TIMER_MODES[i]).apply(); render()
                }.create())
            },
            Form.switchRow(this, "Auto-advance", "Uses the deck's auto-advance seconds and action; if the deck has none, the times below", getSharedPreferences("study", 0).getBoolean("autoAdvance", false)) {
                getSharedPreferences("study", 0).edit().putBoolean("autoAdvance", it).apply(); render()
            },
            Form.stepperRow(this, "Show the answer after", null, getSharedPreferences("study", 0).getInt("autoQuestionSecs", com.husarp.ankigo.study.StudyTiming.OWN_QUESTION),
                com.husarp.ankigo.study.StudyTiming.MIN_SECS, com.husarp.ankigo.study.StudyTiming.MAX_SECS, 1, { "$it s" }) {
                getSharedPreferences("study", 0).edit().putInt("autoQuestionSecs", it).apply()
            }.also { Form.enable(it, getSharedPreferences("study", 0).getBoolean("autoAdvance", false)) },
            Form.stepperRow(this, "Then act after", "The deck's answer action: bury (default), Again, Hard, Good or a reminder", getSharedPreferences("study", 0).getInt("autoAnswerSecs", com.husarp.ankigo.study.StudyTiming.OWN_ANSWER),
                com.husarp.ankigo.study.StudyTiming.MIN_SECS, com.husarp.ankigo.study.StudyTiming.MAX_SECS, 1, { "$it s" }) {
                getSharedPreferences("study", 0).edit().putInt("autoAnswerSecs", it).apply()
            }.also { Form.enable(it, getSharedPreferences("study", 0).getBoolean("autoAdvance", false)) },
            Form.row(this, "Cards with missing media", null, valueText(getSharedPreferences("study", 0).getString("missing", "Ask"))) {
                Pickers.show(AppDialog.Builder(this).setItems(arrayOf("Ask", "Always open in AnkiDroid", "Answer anyway")) { _, i ->
                    getSharedPreferences("study", 0).edit().putString("missing", listOf("Ask", "Always open in AnkiDroid", "Answer anyway")[i]).apply(); render()
                }.create())
            },
            Form.row(this, "Set up pictures", "Checks AnkiDroid's pictures and shows what to fix", null) {
                com.husarp.ankigo.help.HelpActivity.openSetup(host.hostActivity ?: this)
            },
            Form.row(this, "Import a media copy (.apkg)", "Optional; use a legacy-format export with media", null) {
                host.startForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)) { code, intent ->
                    val uri = intent?.data
                    if (code == android.app.Activity.RESULT_OK && uri != null) Thread { com.husarp.ankigo.Media.importApkg(applicationContext, uri) }.start()
                }
            },
            Form.row(this, "Pick AnkiDroid media folder", "Choose collection.media in the public AnkiDroid folder", null) {
                host.startForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)) { code, intent ->
                    val uri = intent?.data
                    if (code == android.app.Activity.RESULT_OK && uri != null) {
                        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); com.husarp.ankigo.Media.setTree(this, uri) }
                    }
                }
            },
    ))

    /** The current value of a pick-one row, shown on its right (the grey line under the title is the explanation). */
    private fun valueText(v: String?): View = Ui.text(this, 13f, 800, R.color.ag_sub1).apply { text = v ?: "" }

    // ---------- game ----------

    /** Changes a setting through the game (main thread) and saves. */
    private fun edit(f: (GameSettings) -> Unit) {
        GameRepo.run(this) { g -> f(g.state.settings) }
        GameRepo.scheduleSave(this)
    }

    private fun gameRows(s: GameSettings): List<View> {
        val rows = mutableListOf<View>()
        rows += Form.switchRow(this, "Faster start", "Shorter wild battles while your lead is below Lv 15. Off by default. Applies to the next encounter; off keeps Ankimon's HP.", s.fasterStart) { v -> edit { it.fasterStart = v } }

        // generations
        val gensBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        gensBox.addView(Form.row(this, "Generations", "At least one. Default 1–8, like Ankimon. Gen 9 uses still pictures.", null))
        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), 0, dp(14), dp(4)) // chips are 48 dp touch height around a 32 dp look
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
        val goalDay = loaded?.day
        val goalValue = valueText(DailyGoalPicker.valueText(s, goalDay)) as TextView
        rows += Form.row(this, "Daily goal", "Reviews a day, the same every day or different per weekday. A bigger goal also makes rare Pokémon more likely.", goalValue) {
            DailyGoalPicker.show(this, s, goalDay, { f -> edit(f) }) { goalValue.text = DailyGoalPicker.valueText(s, goalDay) }
        }
        rows += Form.stepperRow(this, "New day starts at", "Answers before this hour count for the day before.", s.dayStartHour, 0, 12, 1, { "$it:00" }) { v ->
            edit { it.dayStartHour = v }
        }

        // trainer name
        val field = EditText(this).apply {
            setText(s.trainerName)
            typeface = Ui.font(this@SettingsPage, 700)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Ui.c(this@SettingsPage, R.color.ag_text))
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setSingleLine()
            filters = arrayOf(InputFilter.LengthFilter(16))
            imeOptions = EditorInfo.IME_ACTION_DONE
            Ui.tintEdit(this) // accent cursor, selection and handles
            background = Ui.rounded(Ui.c(this@SettingsPage, R.color.ag_mantle), 10, this@SettingsPage)
            backgroundTintList = null // the field keeps its own fill; tintEdit's underline tint would paint it
            setPadding(dp(10), dp(8), dp(10), dp(8))
            minWidth = dp(130)
            setOnEditorActionListener { v, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) { saveName(); v.clearFocus(); true } else false
            }
            setOnFocusChangeListener { _, focus -> if (!focus) saveName() }
        }
        nameEdit = field
        rows += Form.row(this, "Trainer name", null, field)
        return rows
    }

    private fun saveName() {
        val field = nameEdit ?: return
        val s = loaded?.settings ?: return
        val name = field.text.toString().trim().ifEmpty { "Trainer" }
        if (name != s.trainerName) edit { it.trainerName = name }
        if (field.text.toString() != name) field.setText(name)
    }

    private fun faintRows(s: GameSettings): List<View> {
        val options = listOf(
            1 to "Always catch",
            2 to "Defeat (catch shinies and protected kinds)",
            3 to "Catch species you don't have yet",
        )
        val ticks = HashMap<Int, ImageView>()
        val rows = options.map { (mode, label) ->
            val tick = ImageView(this).apply {
                setImageResource(R.drawable.ic_check)
                setColorFilter(Ui.c(this@SettingsPage, R.color.ag_mauve), PorterDuff.Mode.SRC_IN)
                visibility = if (s.autoMode == mode) View.VISIBLE else View.INVISIBLE
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            }
            ticks[mode] = tick
            Form.row(this, label, if (mode == 3) "Default (Ankimon's automatic mode 3)" else null, tick) {
                edit { it.autoMode = mode }
                ticks.forEach { (m, t) -> t.visibility = if (m == mode) View.VISIBLE else View.INVISIBLE }
            }
        }
        return rows
    }

    private fun duplicatesRows(s: GameSettings): List<View> = listOf(
        Form.switchRow(this, "Auto-release duplicates",
            "Off by default. A wild Pokémon of a species you own is defeated instead of caught, unless it is a higher level than your best copy: then it is caught and that copy is released. " +
                "Favourites, shinies, team members, Pokémon holding an item, protected kinds, the Exp. Share holder and renamed Pokémon are never released (you keep both). " +
                "Balls and the one-off Catch/Defeat tap are not affected.",
            s.autoReleaseDuplicates) { v -> edit { it.autoReleaseDuplicates = v } },
    )

    private fun protectRows(s: GameSettings): List<View> = listOf(
        Form.switchRow(this, "Legendary", null, s.protectLegendary) { v -> edit { it.protectLegendary = v } },
        Form.switchRow(this, "Mythical", null, s.protectMythical) { v -> edit { it.protectMythical = v } },
        Form.switchRow(this, "Ultra Beast", null, s.protectUltra) { v -> edit { it.protectUltra = v } },
        Form.switchRow(this, "Starter", null, s.protectStarter) { v -> edit { it.protectStarter = v } },
        Form.switchRow(this, "Regional form", null, s.protectRegional) { v -> edit { it.protectRegional = v } },
    )

    // ---------- battle panel & windows ----------

    private fun battleSection() {
        content.addView(Form.sect(this, "When you leave AnkiGo"))
        content.addView(leaveCard())
        content.addView(Form.sect(this, "Pop out opens"))
        content.addView(popOutCard())
        content.addView(Form.sect(this, "Battle panel"))
        content.addView(Form.cardOf(this, listOf(
            Form.switchRow(this, "Stat card under its own Pokémon", "Off: the classic game layout, each card on the opposite side",
                AppPrefs.cardsUnderOwn(this)) { AppPrefs.setCardsUnderOwn(this, it) },
            Form.switchRow(this, "Same size as the wild one", "Your Pokémon is not drawn bigger than the wild one",
                AppPrefs.playerSameSize(this)) { AppPrefs.setPlayerSameSize(this, it) },
            Form.switchRow(this, "XP animation", "Sparks fly into your Pokémon when it gains XP",
                AppPrefs.xpAnimation(this)) { AppPrefs.setXpAnimation(this, it) },
        )))
        content.addView(Form.sect(this, "Small battle windows"))
        content.addView(Form.cardOf(this, listOf(
            Form.switchRow(this, "Show stats bar in small windows", "Picture-in-picture and the floating battle; off gives the battle more room",
                AppPrefs.statsBarSmall(this)) { AppPrefs.setStatsBarSmall(this, it) },
            Form.switchRow(this, "Compact stat cards", "Only name and HP on the battle cards",
                AppPrefs.compactCards(this)) { AppPrefs.setCompactCards(this, it) },
        )))
        content.addView(Form.sect(this, "Floating screens"))
        val wc = buildWindowsCard()
        windowsCard = wc
        content.addView(wc)
    }

    /** Leave mode (one at a time). */
    private fun leaveCard(): LinearLayout {
        val mode = AppPrefs.leaveMode(this)
        val options = listOf(
            Triple(LeaveMode.OFF, "Nothing", "Default. Use Pop out on the battle any time."),
            Triple(LeaveMode.PIP, "Picture-in-picture", "A small battle window; or use Pop out on the battle any time."),
            Triple(LeaveMode.FLOAT, "Floating battle window",
                "Move and resize it over AnkiDroid. Needs “Display over other apps” and shows a notification while it runs."),
        )
        val ticks = HashMap<LeaveMode, ImageView>()
        val rows = mutableListOf<View>()
        for ((m, label, sub) in options) {
            val tick = ImageView(this).apply {
                setImageResource(R.drawable.ic_check)
                setColorFilter(Ui.c(this@SettingsPage, R.color.ag_mauve), PorterDuff.Mode.SRC_IN)
                visibility = if (m == mode) View.VISIBLE else View.INVISIBLE
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            }
            ticks[m] = tick
            rows += Form.row(this, label, sub, tick) {
                setLeaveMode(m)
                ticks.forEach { (k, t) -> t.visibility = if (k == m) View.VISIBLE else View.INVISIBLE }
            }
        }
        return Form.cardOf(this, rows)
    }

    /** What Pop out opens (R4-X5): the floating battle window or picture-in-picture; separate from the leave mode. */
    private fun popOutCard(): LinearLayout {
        val mode = AppPrefs.popOutMode(this)
        val options = listOf(
            Triple(PopOutMode.FLOAT, "Floating window", "Stays on top and you can tap it; needs “Display over other apps”"),
            Triple(PopOutMode.PIP, "Picture-in-picture window", "Android's own small window; view only"),
        )
        val ticks = HashMap<PopOutMode, ImageView>()
        val rows = mutableListOf<View>()
        for ((m, label, sub) in options) {
            val tick = ImageView(this).apply {
                setImageResource(R.drawable.ic_check)
                setColorFilter(Ui.c(this@SettingsPage, R.color.ag_mauve), PorterDuff.Mode.SRC_IN)
                visibility = if (m == mode) View.VISIBLE else View.INVISIBLE
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            }
            ticks[m] = tick
            rows += Form.row(this, label, sub, tick) {
                AppPrefs.setPopOutMode(this, m)
                ticks.forEach { (k, t) -> t.visibility = if (k == m) View.VISIBLE else View.INVISIBLE }
                if (m == PopOutMode.FLOAT && !Overlay.canDraw(this)) askOverlay()
            }
        }
        return Form.cardOf(this, rows)
    }

    /** Floating screens in split screen, and the overlay permission. */
    private fun buildWindowsCard(): LinearLayout {
        val rows = mutableListOf<View>()
        rows += Form.switchRow(this, "Floating screens in split screen",
            "Team, Pokédex, Shop and Settings open in a small window over AnkiDroid, so the battle stays visible.",
            AppPrefs.floatScreens(this)) { v -> AppPrefs.setFloatScreens(this, v) }
        val allowed = Overlay.canDraw(this)
        val state = Ui.text(this, 13f, 800, if (allowed) R.color.ag_green else R.color.ag_mauve).apply {
            text = if (allowed) "Allowed" else "Allow…"
        }
        rows += Form.row(this, "Display over other apps", "Needed for the floating windows. Picture-in-picture works without it.", state) {
            askOverlay()
        }
        return Form.cardOf(this, rows)
    }

    private fun rebuildWindows() {
        val old = windowsCard ?: return
        val parent = old.parent as? ViewGroup ?: return
        val i = parent.indexOfChild(old)
        parent.removeViewAt(i)
        val fresh = buildWindowsCard()
        explain(fresh)
        windowsCard = fresh
        parent.addView(fresh, i)
    }

    private fun setLeaveMode(m: LeaveMode) {
        AppPrefs.setLeaveMode(this, m)
        if (m != LeaveMode.FLOAT) { FloatBattleService.stop(this); return }
        if (!Overlay.canDraw(this)) { askOverlay(); return }
        // Android 13+: the floating battle's notification (with Pause and Stop) needs this permission.
        val a = host.hostActivity
        if (a != null && Build.VERSION.SDK_INT >= 33 &&
            a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFY)
        }
        FloatBattleService.startIfWanted(this)
    }

    private fun askOverlay() {
        if (Overlay.canDraw(this)) { Ui.toast(this, "Already allowed. You can turn it off in Android's settings."); return }
        val a = host.hostActivity
        if (a != null) Overlay.ask(a, "AnkiGo can show the battle and its screens in small windows over AnkiDroid.")
        else Overlay.openSettings(this)
    }

    // ---------- data and pictures ----------

    private fun buildDataCard(): LinearLayout {
        val anim = SettingsJobs.anim
        val dat = SettingsJobs.data
        animBar = null; dataBar = null
        val box = Ui.card(this)
        box.addView(Form.row(this, "Game data", null, Ui.text(this, 14f, 800, R.color.ag_sub1).apply {
            text = sizeText(DataStore.size(this@SettingsPage))
        }))
        box.addView(Form.divider(this))
        box.addView(Form.row(this, "Pictures", null, Ui.text(this, 14f, 800, R.color.ag_sub1).apply {
            text = sizeText(Sprites.size(this@SettingsPage))
        }))
        box.addView(Form.divider(this))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
        }
        val picJob = FirstRunJob.job
        val picBusy = picJob.running
        val gensNow = loaded?.settings?.gens?.toSet() ?: com.husarp.ankigo.game.Tuning.DEFAULT_GENS
        if (!picBusy) GameRepo.data?.let { DownloadsDone.adoptPictures(this, speciesNums(it, gensNow), gensNow) } // X3-3: pictures that were all there before 0.13.0
        val picsDone = !picBusy && DownloadsDone.isDone(this, DownloadsDone.PICTURES, gensNow)
        val picBtn = Form.secondary(this, if (picJob.state == BgJob.State.FAILED && picsFromSettings) "Try again: download pictures" else "Download pictures (about 1 MB)") { startPictures() }
        Form.enable(picBtn, !picBusy && !anim.running && !dat.running)
        if (picsDone) downloadedLook(picBtn, "Pictures")
        actions.addView(picBtn)
        if (picsFromSettings) when (picJob.state) {
            BgJob.State.RUNNING -> {
                val p = ProgressBlock(this).also { it.show(picJob.fraction, picJob.label) }
                picBar = p
                actions.addView(p, Form.lp(this, 10))
                actions.addView(Form.secondary(this, "Cancel", R.color.ag_red) { picJob.cancel() }, Form.lp(this, 8))
            }
            BgJob.State.FAILED -> actions.addView(Form.body(this, picJob.error ?: "Failed.", 12, R.color.ag_red), Form.lp(this, 8))
            else -> {}
        }
        // the row has its own 14 dp padding: pull it out of the buttons' padding so its text lines up with the other rows
        actions.addView(Form.switchRow(this, "Download pictures as needed", "Off: use placeholders", Sprites.enabled(this)) { Sprites.setEnabled(this, it) },
            LinearLayout.LayoutParams(-1, -2).also { it.setMargins(-dp(14), 0, -dp(14), 0) })
        // battle animations
        val animBtn = Form.secondary(this, "Download all battle animations (about 80 MB)") { startAnimations() }
        Form.enable(animBtn, !anim.running && !dat.running && !picBusy)
        if (anim.state != BgJob.State.RUNNING && DownloadsDone.isDone(this, DownloadsDone.ANIMATIONS, gensNow)) downloadedLook(animBtn, "Battle animations")
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
        Form.enable(dataBtn, !dat.running && !anim.running && !picBusy)
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

    /** R4-X1 item 5: a finished download turns its button into a green, tap-less "Downloaded ✓". */
    private fun downloadedLook(b: TextView, what: String) {
        val green = Ui.c(this, R.color.ag_green)
        b.text = "$what: Downloaded ✓"
        b.setTextColor(green)
        b.background = Ui.rounded(Bits.blend(green, Ui.c(this, R.color.ag_mantle), 0.18f), 11, this)
        b.isEnabled = false
        b.isClickable = false
        b.alpha = 1f
    }

    private fun rebuildData() {
        val old = dataCard ?: return
        val parent = old.parent as? ViewGroup ?: return
        val i = parent.indexOfChild(old)
        parent.removeViewAt(i)
        val fresh = buildDataCard()
        explain(fresh)
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

    private fun onPics() {
        val j = FirstRunJob.job
        if (!picsFromSettings) { rebuildData(); return }
        val b = picBar
        if (j.state == BgJob.State.RUNNING && b != null) { b.show(j.fraction, j.label); return }
        if (j.state == BgJob.State.DONE) {
            picsFromSettings = false
            val stopped = j.label.startsWith("Stopped")
            j.reset()
            Ui.toast(applicationContext, if (stopped) "Picture download stopped" else "Pictures downloaded")
        }
        rebuildData()
    }

    /** The Downloads button: the first-run download job (data is skipped when present), run from here, no first-run screen. */
    private fun startPictures() {
        if (FirstRunJob.job.running) return
        if (FirstRunJob.job.state == BgJob.State.FAILED) FirstRunJob.job.reset()
        Sprites.setEnabled(this, true)
        getSharedPreferences("firstrun", 0).edit().putBoolean("studyOnly", false).apply()
        picsFromSettings = true
        val gens = loaded?.settings?.gens?.toSet() ?: com.husarp.ankigo.game.Tuning.DEFAULT_GENS
        FirstRunJob.start(this, gens) // its first update re-draws the card with the progress
    }

    private fun startAnimations() {
        val gens = loaded?.settings?.gens?.toSet() ?: com.husarp.ankigo.game.Tuning.DEFAULT_GENS
        SettingsJobs.startAnimations(this, gens, GameRepo.data) // reads the data off the main thread when it isn't in memory
    }

    private fun confirmRedownload() {
        confirm("Download game data again?", "The Pokémon data (about 4.3 MB) is deleted and downloaded fresh. Your game isn't touched.", "Download") {
            SettingsJobs.redownloadData(this)
        }
    }

    private fun confirmClear() {
        confirm("Clear picture cache?", "All downloaded pictures and animations are deleted (${sizeText(Sprites.size(this))}). They download again when needed.", "Clear") {
            Sprites.clear(this)
            DownloadsDone.clear(this)
            Ui.toast(this, "Picture cache cleared")
            rebuildData()
        }
    }

    private fun sizeText(bytes: Long): String = SettingsSummary.bytes(bytes)

    // ---------- save ----------

    private fun saveCard(): View {
        val st = loaded
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
        btns.addView(Form.body(this, "Importing or restoring keeps your current game as a backup first.",
            12, R.color.ag_sub0), Form.lp(this, 8))
        box.addView(btns)
        return box
    }

    private fun ankimonCard(): View {
        val st = loaded
        val box = Ui.card(this)
        val btns = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        val ankimon = Form.secondary(this, "Import Ankimon save (.db)…") { startAnkimonImport() }
        Form.enable(ankimon, st != null)
        btns.addView(ankimon)
        val ankimonOut = Form.secondary(this, "Export for Ankimon PC…") { startAnkimonExport() }
        Form.enable(ankimonOut, st != null)
        btns.addView(ankimonOut, Form.lp(this, 8))
        btns.addView(Form.body(this, "Import your PC save here (it only adds, and your current game is kept as a backup first). " +
            "Export writes a file for Ankimon's Game → Import Save File… on the PC.",
            12, R.color.ag_sub0), Form.lp(this, 8))
        if (st == null) btns.addView(Form.body(this, "Both need the game to be loaded.", 12, R.color.ag_sub0), Form.lp(this, 4))
        btns.addView(com.husarp.ankigo.help.HelpViews.ankimonGuide(this), Form.lp(this, 14))
        box.addView(btns)
        return box
    }

    private fun startExport() {
        if (loaded == null) return
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
            .putExtra(Intent.EXTRA_TITLE, "ankigo-save-$day.json")
        try {
            host.startForResult(i) { code, data -> picked(REQ_EXPORT, code, data) }
        } catch (_: Exception) { Ui.toast(this, "No file picker found on this phone") }
    }

    /** Ankimon (PC) save: picker -> preview -> backup + merge. Dialogs go to the newest live Settings page (rotation-safe). */
    private fun startAnkimonImport() {
        AnkimonImportFlow.start(this, { i, cb -> host.startForResult(i, cb) },
            ui = { live?.get()?.takeIf { it.alive } },
            onDone = { imported -> if (imported) live?.get()?.takeIf { it.alive }?.render() })
    }

    /** Ankimon (PC) export: warning -> "save as" picker -> build + write + check. Same rotation-safe dialogs as the import. */
    private fun startAnkimonExport() {
        AnkimonExportFlow.start(this, { i, cb -> host.startForResult(i, cb) }, ui = { live?.get()?.takeIf { it.alive } })
    }

    private fun startImport() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        try {
            host.startForResult(i) { code, data -> picked(REQ_IMPORT, code, data) }
        } catch (_: Exception) { Ui.toast(this, "No file picker found on this phone") }
    }

    private fun export(uri: android.net.Uri) {
        val st = loaded ?: return
        val text = SaveCodec.encode(st) // on the main thread, which owns the state
        val app = applicationContext
        Thread {
            val ok = try {
                app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                true
            } catch (e: Exception) { false }
            runOnUi { Ui.toast(app, if (ok) "Save exported" else "Couldn't write the file") }
        }.start()
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
            runOnUi {
                if (!alive) return@runOnUi
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
            runOnUi {
                if (!alive) return@runOnUi
                if (found.isEmpty()) {
                    info("No backups yet", "Backups appear after the game has been saved a few times.")
                    return@runOnUi
                }
                val labels = found.map { b ->
                    val kind = when {
                        b.file.name == "save.bak" -> "previous save"
                        b.file.name.startsWith("save.before-ankimon") -> "before the Ankimon import"
                        b.file.name.startsWith("save.before-") -> "before a restore or import"
                        else -> "daily copy"
                    }
                    val date = if (b.state.savedAt > 0) dateFmt.format(Date(b.state.savedAt)) else b.file.name
                    "$date · #${b.state.seq} · ${b.state.mons.size} Pokémon\n($kind)"
                }.toTypedArray<CharSequence>()
                dialog?.dismiss()
                dialog = AppDialog.Builder(this).setTitle("Restore a backup").setItems(labels) { _, which ->
                    val b = found[which]
                    confirm("Restore this backup?", describe(b.state) + "\n\nYour current game is kept as a backup.", "Restore") { applyRestore(null, b.file) }
                }.setNegativeButton("Cancel", null).create().also { Pickers.show(it) }
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
            runOnUi {
                val st = restored
                if (st == null) {
                    if (alive) info("Couldn't restore", "The file couldn't be read. Nothing was changed.")
                    return@runOnUi
                }
                GameRepo.state?.let { cur -> if (st.seq < cur.seq) st.seq = cur.seq }
                try { GameRepo.replaceState(app, st) }
                catch (e: Exception) {
                    com.husarp.ankigo.Report.fail("GAME", "backing up before restore", e)
                    if (alive) info("Couldn't restore", "Couldn't back up the current game. Nothing was changed.")
                    return@runOnUi
                }
                Ui.toast(app, "Restored: ${st.settings.trainerName}, ${st.mons.size} Pokémon")
                if (alive) render()
            }
        }.start()
    }

    // ---------- about ----------

    private fun aboutCard(): View {
        val box = Ui.card(this).apply { setPadding(dp(14), dp(12), dp(14), dp(14)) }
        box.addView(Ui.text(this, 14f, 800, R.color.ag_text).apply { text = "AnkiGo ${Updates.current(this@SettingsPage)}" })
        box.addView(Form.body(this,
            "Pokémon and Pokémon names © Nintendo / Creatures / GAME FREAK. Data: PokeAPI, Pokémon Showdown. " +
                "Game rules from Ankimon (GPL-3). Font: Nunito (SIL OFL). Some icons: Material Symbols by Google (Apache 2.0).", 12, R.color.ag_sub0), Form.lp(this, 6))
        return box
    }

    // ---------- dialogs ----------

    private fun confirm(title: String, message: String, yes: String, onYes: () -> Unit) {
        dialog?.dismiss()
        dialog = AppDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton(yes) { _, _ -> onYes() }.setNegativeButton("Cancel", null).create().also { Pickers.show(it) }
    }

    private fun info(title: String, message: String) {
        dialog?.dismiss()
        dialog = AppDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).create().also { Pickers.show(it) }
    }

    companion object {
        /** The newest Settings page, for picker results that arrive after the Activity was recreated. */
        private var live: WeakReference<SettingsPage>? = null

        /** True while the picture download (the first-run job) was started from Settings, so the first-run screen keeps its own job state. */
        @Volatile internal var picsFromSettings = false

        private const val SCALE_DELAY_MS = 700L

        /** The Game section's groups in this order (the catalog's group names); others follow. */
        private val GAME_GROUPS = listOf("Battle", "Difficulty", "Pokémon", "Always catch")

        /**
         * File picker result. Lives here (captures no page) and looks the newest page up in [live], so it still works when
         * the Activity was recreated (rotation, split-screen resize) while the picker was open.
         */
        private fun picked(request: Int, resultCode: Int, data: Intent?) {
            val uri = data?.data ?: return
            if (resultCode != Activity.RESULT_OK) return
            val page = live?.get()?.takeIf { it.alive } ?: return
            when (request) {
                REQ_EXPORT -> page.export(uri)
                REQ_IMPORT -> page.readImport(uri)
            }
        }

        private const val REQ_EXPORT = 11
        private const val REQ_IMPORT = 12
        private const val REQ_NOTIFY = 13
        private const val MAX_IMPORT_BYTES = 64 * 1024 * 1024
    }
}

/** The Settings screen as its own Activity (the Settings tab): a thin shell around [SettingsPage]. */
class SettingsActivity : PageActivity() {
    private var restore: String? = null
    private var settingsPage: SettingsPage? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        restore = savedInstanceState?.getString("section")
        super.onCreate(savedInstanceState)
    }

    override fun createPage(): GamePage = SettingsPage(this, this).also {
        it.restoreSection(restore)
        settingsPage = it
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        settingsPage?.section?.let { outState.putString("section", it.name) }
    }

    /** Back inside a section goes to the list first. */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (settingsPage?.goHome() != true) super.onBackPressed()
    }
}
