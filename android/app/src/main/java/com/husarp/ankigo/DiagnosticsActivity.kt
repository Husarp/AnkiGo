package com.husarp.ankigo

import com.husarp.ankigo.game.lookNum
import android.app.Activity
import com.husarp.ankigo.ui.AppDialog
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.InsetDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import com.husarp.ankigo.ui.Form
import com.husarp.ankigo.ui.FoldGroup
import com.husarp.ankigo.ui.Ui
import com.husarp.ankigo.ui.showCard
import com.husarp.ankigo.update.Updates
import com.husarp.ankigo.update.UpdatesActivity

/** The step-0 test screen (C-checks, LIVE watch, report), now reached from Settings -> Diagnostics. */
class DiagnosticsActivity : com.husarp.ankigo.ui.AgActivity() {
    companion object {
        private const val COPY_MAX = 400_000
        const val NOT_TOP_TITLE = "Save answers for cards that aren't first (test)"
        const val NOT_TOP_SUB = "When AnkiDroid puts another card first, AnkiGo still tries to save your answer, or saves it when that card is first again today. Turn off if answers go wrong."
    }

    private val bg = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var header: TextView
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Report.init(this)
        Anki.load(this)
        title = "Diagnostics"
        setContentView(buildContent())

        logView.text = Report.all().takeLast(20000)
        Report.listener = { line -> main.post { logView.append(line + "\n") } }
        Report.log("C12b", "=== AnkiGo started; Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        refreshHeader(log = true)
    }

    /**
     * The screen's views; onCreate sets them as the Activity's content. PLAN 220: under the app bar the whole page scrolls: the status card,
     * the log in a tall box of its own, then the option groups (folded to begin with, see [FoldGroup]).
     */
    internal fun buildContent(): LinearLayout {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.c(this@DiagnosticsActivity, R.color.ag_base)) }
        root.addView(Ui.appBar(this, "Diagnostics", back = true))
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@DiagnosticsActivity, 12), Ui.dp(this@DiagnosticsActivity, 4), Ui.dp(this@DiagnosticsActivity, 12), Ui.dp(this@DiagnosticsActivity, 12))
        }
        root.addView(ScrollView(this).apply { isFillViewport = false; addView(body, ViewGroup.LayoutParams(-1, -2)) }, LinearLayout.LayoutParams(-1, 0, 1f))

        // status card
        header = Ui.text(this, 12f, 700, R.color.ag_sub1).apply { setLineSpacing(0f, 1.15f) }
        body.addView(Ui.card(this).apply { setPadding(Ui.dp(context, 12), Ui.dp(context, 10), Ui.dp(context, 12), Ui.dp(context, 10)); addView(header) })

        val gap = Ui.dp(this, 6)
        // right under the status card, the log: its buttons, then a box about 70 % of the window tall (at least 320 dp) that scrolls inside the page
        val logActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf<Pair<String, () -> Unit>>("Full screen" to { showFullLog() }, "Share log…" to { pickRange() },
            "Clear log" to { Report.clear(); logView.text = "" }).forEachIndexed { i, (label, f) ->
            logActions.addView(Form.secondary(this, label, onPage = true) { f() }.apply { textSize = 12f; minHeight = Ui.dp(context, 48) },
                LinearLayout.LayoutParams(0, -1, 1f).also { if (i > 0) it.marginStart = gap })
        }
        body.addView(logActions, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(this, 10) })
        // not selectable: a focused log made fullScroll pan the whole window up, hiding the buttons (Copy report covers copying)
        logView = Ui.text(this, 11f, 600, R.color.ag_sub1).apply { typeface = Typeface.MONOSPACE; setPadding(Ui.dp(context, 10), Ui.dp(context, 8), Ui.dp(context, 10), Ui.dp(context, 8)) }
        logScroll = ScrollView(this).apply {
            background = Ui.rounded(Ui.c(this@DiagnosticsActivity, R.color.ag_mantle), 12, this@DiagnosticsActivity)
            isNestedScrollingEnabled = true
            // a drag that starts in the log scrolls the log, not the page
            setOnTouchListener { v, e ->
                v.parent?.requestDisallowInterceptTouchEvent(e.actionMasked != MotionEvent.ACTION_UP && e.actionMasked != MotionEvent.ACTION_CANCEL)
                false
            }
            addView(logView)
        }
        // the log box follows the newest line only while it is already at the end (the first fill counts as at the end)
        var follow = true
        logScroll.setOnScrollChangeListener { _, _, y, _, _ -> follow = y + logScroll.height >= logView.height - Ui.dp(this, 8) }
        logView.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (follow && bottom - top != oldBottom - oldTop) logScroll.scrollTo(0, bottom - top)
        }
        body.addView(logScroll, LinearLayout.LayoutParams(-1, logHeightPx()).also { it.topMargin = gap })
        fun row(vararg items: Pair<String, () -> Unit>): View {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            items.forEachIndexed { i, (label, f) ->
                r.addView(Form.secondary(this, label) { f() }.apply { textSize = 12f; minHeight = Ui.dp(context, 48) },
                    LinearLayout.LayoutParams(0, -1, 1f).also { if (i > 0) it.marginStart = gap })
            }
            r.layoutParams = LinearLayout.LayoutParams(-1, -2).also { it.topMargin = gap }
            return r
        }
        fun group(title: String, vararg rows: View) = body.addView(FoldGroup.build(this, title, rows.toList()), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = gap })
        group("AnkiDroid",
            row("Switch AnkiDroid target" to { pickTarget() }, "Grant AnkiDroid permission" to { askPermission() }))
        group("Checks",
            row("C1 Decks + counts" to { c1() }, "C2–C5 Study a deck…" to { pickDeckAndStudy() }),
            row("C6 Stats probe" to { c6() }, "C7 Snapshot" to { snapshot() }, "C7 Compare" to { compare() }),
            row("LIVE watch (study in AnkiDroid, split screen)" to { startActivity(Intent(this, LiveWatchActivity::class.java)) }),
            row("Pictures: owned Pokémon without one" to { checkPictures() }))
        group("Media",
            row("C3 Media: probe paths" to { bg.execute { Media.probe(this) } }, "C3 Media: All-files access" to { allFiles() }),
            row("C3 Media: pick media folder" to { pickTree() }, "C3 Media: import .apkg" to { pickApkg() }))
        group("Widgets",
            row("C8 Pin motivation widget" to { pin(MotivationWidget::class.java) }, "C8 Pin study widget" to { pin(StudyWidget::class.java) }))
        // PLAN 195: on by default; Adam turns it off if his phone shows trouble with answers to cards that aren't first
        val study = getSharedPreferences("study", 0)
        group("App",
            row("Updates (version ${Updates.current(this)})" to { startActivity(Intent(this, UpdatesActivity::class.java)) }),
            Form.switchRow(this, NOT_TOP_TITLE, NOT_TOP_SUB, study.getBoolean(com.husarp.ankigo.study.StudyIo.NOT_TOP_PREF, true)) { on ->
                study.edit().putBoolean(com.husarp.ankigo.study.StudyIo.NOT_TOP_PREF, on).apply()
                Report.log("SETTINGS", "${com.husarp.ankigo.study.StudyIo.NOT_TOP_PREF} -> $on")
            })

        return root
    }

    /** About 70 % of the window, but never under 320 dp. */
    private fun logHeightPx(): Int = maxOf(Ui.dp(this, 320), (resources.displayMetrics.heightPixels * 0.7f).toInt())

    /** "Full screen": the log text in a dialog that fills the window, scrolled to the end (a snapshot: new lines don't follow there). */
    private fun showFullLog() {
        val text = Ui.text(this, 11f, 600, R.color.ag_sub1).apply {
            typeface = Typeface.MONOSPACE
            this.text = logView.text
            setPadding(Ui.dp(context, 10), Ui.dp(context, 8), Ui.dp(context, 10), Ui.dp(context, 8))
        }
        val scroll = ScrollView(this).apply {
            val side = Ui.dp(context, 16)
            background = InsetDrawable(Ui.rounded(Ui.c(context, R.color.ag_mantle), 12, context), side, 0, side, 0)
            setPadding(side, 0, side, 0)
            minimumHeight = resources.displayMetrics.heightPixels - Ui.dp(context, 200)
            addView(text)
        }
        text.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ -> scroll.scrollTo(0, bottom - top) }
        AppDialog.Builder(this).setTitle("Log").setView(scroll).setPositiveButton("Close", null).showCard()
    }

    // Launch and every return to the app: check for updates (at most every 5 min) and carry on an install
    // that was waiting on Android's "install unknown apps" screen.
    override fun onResume() {
        super.onResume()
        refreshHeader(log = false)
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        Report.listener = null
        super.onDestroy()
    }

    // ---------- C12b header ----------
    private fun refreshHeader(log: Boolean) {
        bg.execute {
            val inst = Anki.installed(this)
            val sb = StringBuilder()
            if (inst.isEmpty()) sb.append("No AnkiDroid installed (looked for ${Anki.KNOWN.joinToString()}).\n")
            for (i in inst) {
                val perm = checkSelfPermission("${i.pkg}.permission.READ_WRITE_DATABASE") == 0
                sb.append("${if (i.pkg == Anki.pkg) "▶ " else "   "}${i.pkg}  v${i.versionName} (${i.versionCode})  provider=${if (i.providerFound) "yes" else "NO"} spec=${i.providerSpec}  permission=${if (perm) "granted" else "not granted"}\n")
            }
            var api = "API: not checked (no permission)"
            if (Anki.hasPermission(this)) {
                api = try {
                    val n = Anki.decks(this).size
                    "API: OK ($n decks readable)"
                } catch (e: Exception) {
                    "API: FAILED ${e.javaClass.simpleName}: ${e.message}"
                }
            }
            sb.append("Target: ${Anki.pkg}  ·  $api\nMedia: all-files=${Media.allFilesAccess()} folder=${Media.treeUri(this) != null} imported=${Media.ownDir(this).list()?.size ?: 0}  ·  AnkiGo reviews today: ${Local.todayCount(this)}")
            // PLAN 41: AnkiDroid build, collection folder, what AnkiGo can read (this block runs on the bg thread)
            try {
                com.husarp.ankigo.help.PictureSetup.diagnosticLines(com.husarp.ankigo.help.PictureFacts.read(this)).forEach { sb.append("\n").append(it) }
            } catch (e: Exception) {
                sb.append("\nPictures: check failed (${e.javaClass.simpleName})")
            }
            if (log) Report.log("C12b", sb.toString().replace("\n", " | "))
            main.post { header.text = sb.toString() }
        }
    }

    private fun pickTarget() {
        val inst = Anki.installed(this).map { it.pkg }
        val list = (inst + Anki.KNOWN.filter { it !in inst }).toTypedArray()
        AppDialog.Builder(this).setTitle("AnkiDroid package to test").setItems(list.map { if (it in inst) it else "$it (not installed)" }.toTypedArray()) { _, w ->
            Anki.select(this, list[w]); Report.log("C12b", "target switched to ${list[w]}"); refreshHeader(log = true)
        }.showCard()
    }

    private fun askPermission() {
        if (Anki.hasPermission(this)) {
            Report.ok("C1", "permission ${Anki.permission} already granted"); return
        }
        Report.log("C1", "requesting ${Anki.permission}")
        requestPermissions(arrayOf(Anki.permission), 1)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        perms.forEachIndexed { i, p ->
            if (results.getOrNull(i) == 0) Report.ok("C1", "permission granted: $p") else Report.fail("C1", "permission denied: $p (if no dialog appeared: is AnkiDroid installed BEFORE this app? reinstall AnkiGo)")
        }
        refreshHeader(log = true)
    }

    // ---------- pictures ----------
    /** Lists the owned Pokémon (box and team) with no usable Pokédex picture, then tries each again and says why it failed. */
    private fun checkPictures() = bg.execute {
        val tag = "PICS"
        try {
            val state = GameRepo.state
            val data = GameRepo.data
            if (state == null || data == null) { Report.log(tag, "the game isn't loaded yet; open the game once and try again"); return@execute }
            val keys = state.mons.flatMap { listOf(it.species to it.shiny, it.lookNum(data) to it.shiny) }.distinct()   // a chosen look needs its picture too
            val gaps = com.husarp.ankigo.data.Sprites.pictureGaps(this, keys)
            val name = { n: Int -> data.species[n]?.name ?: "?" }
            if (gaps.isEmpty()) { Report.ok(tag, "all ${keys.distinct().size} owned Pokémon (by kind) have a picture"); return@execute }
            Report.log(tag, "${gaps.size} of ${keys.distinct().size} owned kinds have no usable picture:")
            com.husarp.ankigo.data.Sprites.describeGaps(gaps, name).forEach { Report.log(tag, "  $it") }
            for (g in gaps) {
                val r = com.husarp.ankigo.data.Sprites.retryStatic(this, g.num, g.shiny)
                Report.log(tag, "  retry #${g.num} ${name(g.num)}${if (g.shiny) " (shiny)" else ""}: $r")
            }
        } catch (e: Exception) {
            Report.fail(tag, "picture check", e)
        }
    }

    // ---------- C1 ----------
    private fun c1() = bg.execute {
        try {
            val t0 = System.currentTimeMillis()
            val decks = Anki.decks(this)
            Report.ok("C1", "${decks.size} decks in ${System.currentTimeMillis() - t0} ms (counts = learn / review / new). Compare with AnkiDroid's deck list:")
            for (d in decks) Report.log("C1", "  ${d.name}  [${d.learn} / ${d.review} / ${d.new}]${if (d.dyn) " (filtered)" else ""}  id=${d.id}")
            Report.log("C1", "selected deck: ${Anki.selectedDeck(this).firstOrNull()?.let { "${it["deck_name"]} ${it["deck_count"]}" }}")
        } catch (e: Exception) {
            Report.fail("C1", "list decks", e)
        }
    }

    private fun pickDeckAndStudy() = bg.execute {
        try {
            val decks = Anki.decks(this)
            main.post {
                AppDialog.Builder(this).setTitle("Pick a deck")
                    .setItems(decks.map { "${it.name}  [${it.learn}/${it.review}/${it.new}]" }.toTypedArray()) { _, w ->
                        startActivity(Intent(this, StudyActivity::class.java).putExtra("deckId", decks[w].id).putExtra("deckName", decks[w].name))
                    }.showCard()
            }
        } catch (e: Exception) {
            Report.fail("C2", "deck list", e)
        }
    }

    // ---------- C3 media ----------
    private fun allFiles() {
        if (Build.VERSION.SDK_INT < 30) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 2); return
        }
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
        Report.log("MEDIA", "opened All-files-access settings; come back and tap 'probe paths'")
    }

    private fun pickTree() {
        Report.log("MEDIA", "opening folder picker: choose AnkiDroid/collection.media (Android/data folders are blocked by Android 11+)")
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 10)
    }

    private fun pickApkg() {
        Report.log("MEDIA", "pick an .apkg exported from AnkiDroid WITH media and 'Support older Anki versions' ticked")
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 11)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data
        if (res != RESULT_OK || uri == null) { Report.log("MEDIA", "picker cancelled"); return }
        when (req) {
            10 -> {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    Report.fail("MEDIA", "persist folder permission", e)
                }
                Media.setTree(this, uri)
                Report.ok("MEDIA", "media folder set: $uri")
                bg.execute { Media.probe(this) }
            }
            11 -> bg.execute { Media.importApkg(this, uri); refreshHeader(false) }
        }
    }

    // ---------- C6 ----------
    private fun c6() = bg.execute {
        val tag = "C6"
        try {
            val decks = Anki.rows(contentResolver.query(Anki.uri("decks"), null, null, null, null))
            Report.ok(tag, "decks: ${decks.size} rows, columns=${decks.firstOrNull()?.keys}")
            decks.firstOrNull()?.get("options")?.let { Report.log(tag, "deck options sample (${it.length} chars): ${it.take(700)}") }
            val models = Anki.rows(contentResolver.query(Anki.uri("models"), null, null, null, null))
            Report.ok(tag, "models: ${models.size}: " + models.joinToString { "${it["name"]}(notes=${it["note_count"]})" })
        } catch (e: Exception) {
            Report.fail(tag, "decks/models", e)
        }
        // official card search with extended columns
        try {
            val t0 = System.currentTimeMillis()
            val ids = Anki.searchCards(this, "deck:*", arrayOf("_id"))
            Report.ok(tag, "cards search 'deck:*': ${ids.size} cards (ids only, ${System.currentTimeMillis() - t0} ms)")
            val t1 = System.currentTimeMillis()
            val sample = Anki.searchCards(this, "rated:7", Anki.CARD_STAT_COLS)
            Report.ok(tag, "cards 'rated:7' with stats columns: ${sample.size} rows in ${System.currentTimeMillis() - t1} ms")
            sample.take(3).forEach { Report.log(tag, "  sample card: $it") }
            if (sample.isNotEmpty()) {
                val ivl = sample.mapNotNull { it["interval"]?.toDoubleOrNull() }
                val ease = sample.mapNotNull { it["sm2_factor"]?.toDoubleOrNull() }.filter { it > 0 }
                val stab = sample.mapNotNull { it["fsrs_stability"]?.toDoubleOrNull() }
                Report.log(tag, "  avg interval=${"%.1f".format(ivl.average())}d, avg ease=${if (ease.isEmpty()) "-" else "%.0f".format(ease.average() / 10) + "%"}, FSRS stability present on ${stab.size}/${sample.size}, total reps=${sample.sumOf { it["reps"]?.toIntOrNull() ?: 0 }}, lapses=${sample.sumOf { it["lapses"]?.toIntOrNull() ?: 0 }}")
            }
        } catch (e: Exception) {
            Report.fail(tag, "cards URI (needs AnkiDroid 2.24+)", e)
        }
        // per-day history through search syntax (cards, not reviews)
        try {
            val perDay = (1..7).map { d -> Anki.searchCards(this, "rated:$d", arrayOf("_id")).size }
            val days = perDay.mapIndexed { i, n -> n - (if (i == 0) 0 else perDay[i - 1]) }
            Report.ok(tag, "cards rated per day (today first, via 'rated:N' search): $days")
            val byEase = (1..4).map { e -> Anki.searchCards(this, "rated:7:$e", arrayOf("_id")).size }
            Report.log(tag, "  cards rated in 7 days by button Again/Hard/Good/Easy: $byEase; introduced today: ${Anki.searchCards(this, "introduced:1", arrayOf("_id")).size}")
        } catch (e: Exception) {
            Report.fail(tag, "rated:N searches", e)
        }
        // unofficial revlog probe
        try {
            val today = Anki.reviewsTodayViaRevlog(this)
            val last = Anki.revlogProbe(this, "SELECT id, cid, usn, ease, ivl, lastIvl, factor, time, type, 0, 0 FROM revlog ORDER BY 1 DESC LIMIT 5")
            Report.log(tag, "UNOFFICIAL revlog via notes_v2 selection: reviews today=$today; last 5 (id=ms,guid=cid,mid=usn,mod=ease,usn=ivl,tags=lastIvl,flds=factor,sfld=timeMs,csum=type): ${last.map { it.values.take(9) }}")
        } catch (e: Exception) {
            Report.fail(tag, "UNOFFICIAL revlog probe (expected to be blocked some day)", e)
        }
        try {
            val sel = Anki.selectedDeck(this).firstOrNull()
            val did = sel?.get("deck_id")?.toLongOrNull()
            if (did != null) {
                val q = Anki.nextCards(this, did, 5)
                Report.ok(tag, "schedule limit=5 for selected deck '${sel["deck_name"]}': ${q.map { "${it.nid}/${it.ord} ${it.nextTimes}" }}")
            }
        } catch (e: Exception) {
            Report.fail(tag, "schedule limit=5", e)
        }
        Report.log(tag, "AnkiGo's own log: ${File(filesDir, "reviews.csv").let { if (it.exists()) it.readLines().size else 0 }} reviews recorded here")
    }

    // ---------- C7 ----------
    private val snapFile get() = File(filesDir, "snapshot.json")
    private val snapQuery = "is:due OR is:learn OR rated:1"

    private fun snapshot() = bg.execute {
        try {
            val t0 = System.currentTimeMillis()
            val o = JSONObject()
            o.put("time", System.currentTimeMillis())
            o.put("ankigoToday", Local.todayCount(this))
            o.put("revlogToday", Anki.reviewsTodayViaRevlog(this) ?: -1)
            val d = JSONObject()
            Anki.decks(this).forEach { d.put(it.name, JSONArray(listOf(it.learn, it.review, it.new))) }
            o.put("decks", d)
            val cards = JSONObject()
            val rows = try {
                Anki.searchCards(this, snapQuery, arrayOf("_id", "reps", "due", "queue", "interval", "last_review_time_secs"))
            } catch (e: Exception) {
                Report.fail("C7", "card snapshot (cards URI)", e); emptyList()
            }
            rows.take(2000).forEach { cards.put(it["_id"] ?: "?", "${it["reps"]}|${it["due"]}|${it["queue"]}|${it["interval"]}|${it["last_review_time_secs"]}") }
            o.put("cards", cards)
            o.put("rated1", Anki.searchCards(this, "rated:1", arrayOf("_id")).size)
            snapFile.writeText(o.toString())
            Report.ok("C7", "snapshot saved: ${d.length()} decks, ${cards.length()} cards ('$snapQuery'), rated today=${o["rated1"]}, revlog today=${o["revlogToday"]}, AnkiGo today=${o["ankigoToday"]} (${System.currentTimeMillis() - t0} ms). Now study a few cards IN ANKIDROID, then tap Compare.")
        } catch (e: Exception) {
            Report.fail("C7", "snapshot", e)
        }
    }

    private fun compare() = bg.execute {
        try {
            if (!snapFile.exists()) { Report.fail("C7", "no snapshot yet"); return@execute }
            val o = JSONObject(snapFile.readText())
            val mins = (System.currentTimeMillis() - o.getLong("time")) / 60000
            Report.log("C7", "compare with snapshot from $mins min ago:")
            val old = o.getJSONObject("decks")
            for (d in Anki.decks(this)) {
                val a = old.optJSONArray(d.name) ?: continue
                val now = listOf(d.learn, d.review, d.new)
                val was = (0..2).map { a.getInt(it) }
                if (now != was) Report.log("C7", "  deck ${d.name}: $was -> $now")
            }
            val cards = o.getJSONObject("cards")
            val changed = ArrayList<String>()
            for (k in cards.keys()) {
                val r = try { Anki.rows(contentResolver.query(Anki.uri("cards/$k"), arrayOf("reps", "due", "queue", "interval", "last_review_time_secs"), null, null, null)).firstOrNull() } catch (_: Exception) { null }
                    ?: continue
                val now = "${r["reps"]}|${r["due"]}|${r["queue"]}|${r["interval"]}|${r["last_review_time_secs"]}"
                if (now != cards.getString(k)) changed.add(k)
            }
            val ankigoNow = Local.todayCount(this)
            val ankigoDelta = ankigoNow - o.getInt("ankigoToday")
            val revNow = Anki.reviewsTodayViaRevlog(this) ?: -1
            val revDelta = if (revNow >= 0 && o.getInt("revlogToday") >= 0) revNow - o.getInt("revlogToday") else null
            val rated = Anki.searchCards(this, "rated:1", arrayOf("_id")).size
            Report.ok("C7", "  cards changed since snapshot: ${changed.size} (of ${cards.length()}) ids=${changed.take(10)}")
            Report.ok("C7", "  rated-today cards: ${o.getInt("rated1")} -> $rated; AnkiGo reviews +$ankigoDelta; revlog reviews ${revDelta?.let { "+$it" } ?: "n/a"} => done elsewhere ≈ ${revDelta?.minus(ankigoDelta) ?: (changed.size - ankigoDelta)}")
        } catch (e: Exception) {
            Report.fail("C7", "compare", e)
        }
    }

    // ---------- C8 ----------
    private fun pin(cls: Class<*>) {
        val mgr = AppWidgetManager.getInstance(this)
        if (mgr.isRequestPinAppWidgetSupported) {
            val ok = mgr.requestPinAppWidget(ComponentName(this, cls), null, null)
            Report.log("C8", "pin ${cls.simpleName} requested=$ok")
        } else {
            Report.log("C8", "launcher can't pin from app: long-press home screen → Widgets → AnkiGo")
            Ui.toast(this, "Add it from the home screen widget list")
        }
        Widgets.refreshAll(this)
    }

    // ---------- the log (R13 Part E) ----------

    /** "Share log…": which part of the log (Last hour / Today / Yesterday / Pick a day / Everything), then Share or Copy. */
    private fun pickRange() {
        val kinds = com.husarp.ankigo.debug.LogRange.Kind.entries
        AppDialog.Builder(this).setTitle("Share log").setItems(kinds.map { it.label }.toTypedArray()) { _, i ->
            if (kinds[i] == com.husarp.ankigo.debug.LogRange.Kind.DAY) pickDay() else prepare(kinds[i], null)
        }.setNegativeButton("Cancel", null).showCard()
    }

    private fun pickDay() = bg.execute {
        val days = com.husarp.ankigo.debug.LogRange.days(Report.all())
        main.post {
            if (isFinishing) return@post
            if (days.isEmpty()) { Ui.toast(this, "The log has no dated lines yet"); return@post }
            AppDialog.Builder(this).setTitle("Pick a day").setItems(days.map { it.toString() }.toTypedArray()) { _, i ->
                prepare(com.husarp.ankigo.debug.LogRange.Kind.DAY, days[i])
            }.setNegativeButton("Cancel", null).showCard()
        }
    }

    /** Builds the chosen range (with the header) off the main thread, then offers Share (a .txt attachment) or Copy. */
    private fun prepare(kind: com.husarp.ankigo.debug.LogRange.Kind, day: java.time.LocalDate?) {
        val name = if (kind == com.husarp.ankigo.debug.LogRange.Kind.DAY) day.toString() else kind.label
        val head = logHeader(name)
        bg.execute {
            val body = com.husarp.ankigo.debug.LogRange.filter(Report.all(), com.husarp.ankigo.debug.LogRange.window(kind, java.time.LocalDateTime.now(), day))
            val text = head + "\n" + body
            val lines = body.lineSequence().count { it.isNotEmpty() }
            main.post {
                if (isFinishing) return@post
                AppDialog.Builder(this).setTitle("Log: $name").setMessage("$lines lines, ${(text.length + 1023) / 1024} KB")
                    .setPositiveButton("Share") { _, _ -> shareFile(name, text) }
                    .setNeutralButton("Copy") { _, _ -> copy(text) }
                    .setNegativeButton("Cancel", null).showCard()
            }
        }
    }

    /** App version, Android, device, AnkiDroid (the status card's lines) and the main settings, on top of a shared log. */
    private fun logHeader(range: String): String {
        val study = getSharedPreferences("study", 0).all.filterKeys { ':' !in it && !it.endsWith(".q") }.toSortedMap()
            .entries.joinToString(", ") { "${it.key}=${it.value.toString().take(40)}" }
        val game = GameRepo.state?.settings?.let { com.husarp.ankigo.debug.SettingsText.line(it) } ?: "game not loaded"
        return "AnkiGo log: $range (made ${java.time.LocalDateTime.now().withNano(0)})\n" +
            "App: AnkiGo ${Updates.current(this)} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            header.text + "\n" +
            "Game settings: $game\n" +
            "Study settings: $study\n"
    }

    private fun shareFile(range: String, text: String) {
        try {
            val dir = File(cacheDir, "logs").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() } // only the newest shared log is kept
            val file = File(dir, "ankigo-log-" + range.lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-') + ".txt")
            file.writeText(text)
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", file)
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, "AnkiGo log: $range").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, "Share log"))
            Report.log("C12b", "log shared: $range, ${text.length} chars")
        } catch (e: Exception) {
            Report.fail("C12b", "sharing the log", e)
            Ui.toast(this, "The log couldn't be shared: ${e.javaClass.simpleName}")
        }
    }

    /** The clipboard takes a few hundred KB at most (Android's limit for one transfer): a longer range is copied from its end. */
    private fun copy(text: String) {
        val cut = text.length > COPY_MAX
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("AnkiGo log", if (cut) text.takeLast(COPY_MAX) else text))
        Ui.toast(this, if (cut) "Copied the last ${COPY_MAX / 1000} KB (use Share for all of it)" else "Log copied")
    }
}
