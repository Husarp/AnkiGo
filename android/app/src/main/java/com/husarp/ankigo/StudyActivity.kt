package com.husarp.ankigo

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.live.LiveSession
import com.husarp.ankigo.overlay.FloatBattleService
import com.husarp.ankigo.study.StudyLoop
import com.husarp.ankigo.ui.*
import com.husarp.ankigo.update.Updates
import org.json.JSONObject
import java.util.concurrent.Executors

/** Study through AnkiDroid's scheduler, awarding game progress only after a verified answer. */
class StudyActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("study", 0) }
    private var deckId = 0L
    private var deckName = ""
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var cardColumn: LinearLayout
    private lateinit var side: LinearLayout
    private lateinit var web: WebView
    private lateinit var notice: LinearLayout
    private lateinit var buttons: LinearLayout
    private lateinit var header: TextView
    private lateinit var handle: TextView
    private lateinit var panel: BattlePanelView
    private lateinit var mini: MiniBattleView
    private var summaryView: View? = null
    private lateinit var events: EventPlayer
    private var card: Anki.Review? = null
    private var stats: Map<String, String?> = emptyMap()
    private var question = ""; private var answerHtml = ""; private var css = ""
    private var fields = emptyMap<String, String>()
    private var flipped = false
    private var locked = true
    private var mediaBlocked = false
    private var generation = 0
    private var shownAt = 0L
    private var timedCard: Pair<Long, Int>? = null
    private var elapsed = 0L
    private var background = true
    private var maxTime = 60_000L
    private var allowAt = Long.MAX_VALUE
    private var heightMode = 1
    private var audio: MediaPlayer? = null
    private var sounds = emptyList<String>()
    private val soundQueue = ArrayDeque<String>()
    private val counts = IntArray(4)
    private var totalTime = 0L
    private var coins = 0; private var xp = 0; private var met = 0; private var defeated = 0
    private var levels = 0; private var evolutions = 0
    private val caught = arrayListOf<String>()
    private val items = arrayListOf<String>()
    private var finished = false
    private var started = false
    private val sink = object : LiveSession.Sink {
        override fun onGameEvents(events: List<GameEvent>) { this@StudyActivity.events.enqueue(events); updateHandle() }
        override fun onLogLine(kind: LogKind, pill: String, text: String, redraw: Boolean) {
            events.enqueueAction { if (redraw) panel.renderAll(); panel.setLog(kind, pill, text) }
        }
        override fun onStatus(ok: Boolean, text: String, canGrant: Boolean) { panel.setStatus(ok, text, canGrant) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Anki.load(this)
        deckId = intent.getLongExtra("deckId", 0); deckName = intent.getStringExtra("deckName") ?: "Deck"
        prefs.edit().putLong("lastDeck", deckId).putString("lastName", deckName).apply()
        heightMode = prefs.getInt("height", 1).coerceIn(0, 2)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.c(this@StudyActivity, R.color.ag_base)) }
        val top = LinearLayout(this)
        top.addView(Form.secondary(this, "‹") { finish() }.apply { contentDescription = "Back to decks" })
        header = Ui.text(this, 14f, 800, R.color.ag_text).apply { text = deckName; gravity = android.view.Gravity.CENTER_VERTICAL }
        top.addView(header, Form.lp(0, -1, 1f))
        top.addView(Form.secondary(this, "⋮") { actions() }.apply { contentDescription = "Card actions" })
        root.addView(top, Form.lp(-1, Ui.dp(this, 48)))
        body = LinearLayout(this)
        cardColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        notice = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(Ui.dp(context, 12), Ui.dp(context, 6), Ui.dp(context, 12), Ui.dp(context, 6)) }
        web = WebView(this)
        cardColumn.addView(notice)
        cardColumn.addView(web, Form.lp(-1, 0, 1f))
        side = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        handle = Form.secondary(this, "Strip") { heightMode = (heightMode + 1) % 3; prefs.edit().putInt("height", heightMode).apply(); layout() }
        side.addView(handle, Form.lp(-1, Ui.dp(this, 48)))
        panel = BattlePanelView(this)
        events = EventPlayer(panel)
        panel.onMenu = { if (events.idle) AlertDialog.Builder(this).setItems(arrayOf("Team", "Bag / Shop", "Pokédex", "Settings")) { _, i ->
            startActivity(Intent(this, listOf(TeamActivity::class.java, ShopActivity::class.java, DexActivity::class.java, SettingsActivity::class.java)[i]))
        }.show() }
        panel.onBalls = { throwBall() }
        panel.arena.onCatchTagTap = {
            val game = GameRepo.game
            if (events.idle && game != null) {
                GameRepo.run(this) { it.setOverride(if (game.willCatch()) com.husarp.ankigo.game.Outcome.DEFEAT else com.husarp.ankigo.game.Outcome.CATCH) }
                panel.renderAll()
            }
        }
        panel.onGrant = { requestPermissions(arrayOf(Anki.permission), 1) }
        side.addView(panel)
        mini = MiniBattleView(this); side.addView(mini, Form.lp(-1, Ui.dp(this, 48)))
        buttons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(context, 6), Ui.dp(context, 4), Ui.dp(context, 6), Ui.dp(context, 6)) }
        side.addView(buttons)
        body.addView(cardColumn); body.addView(side)
        root.addView(body, Form.lp(-1, 0, 1f)); setContentView(root)
        setupWeb(); layout()
        if (savedInstanceState != null) {
            savedInstanceState.getIntArray("counts")?.copyInto(counts)
            totalTime = savedInstanceState.getLong("totalTime"); coins = savedInstanceState.getInt("coins"); xp = savedInstanceState.getInt("xp")
            met = savedInstanceState.getInt("met"); defeated = savedInstanceState.getInt("defeated"); levels = savedInstanceState.getInt("levels"); evolutions = savedInstanceState.getInt("evolutions")
            caught += savedInstanceState.getStringArrayList("caught").orEmpty(); items += savedInstanceState.getStringArrayList("items").orEmpty()
        }
        GameRepo.load(this) {
            if (!isDestroyed) { panel.renderAll(); if (GameRepo.game != null) { val initial = GameRepo.act(this) { g -> g.tick() + g.ensureEncounter() }; events.enqueue(initial); record(initial) }; if (started) LiveSession.hold(this, this); loadNext() }
        }
    }
    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putIntArray("counts", counts); out.putLong("totalTime", totalTime); out.putInt("coins", coins); out.putInt("xp", xp)
        out.putInt("met", met); out.putInt("defeated", defeated); out.putInt("levels", levels); out.putInt("evolutions", evolutions)
        out.putStringArrayList("caught", caught); out.putStringArrayList("items", items)
    }
    override fun onConfigurationChanged(config: Configuration) { super.onConfigurationChanged(config); layout() }
    private fun layout() {
        val wide = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        body.orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        cardColumn.layoutParams = if (wide) Form.lp(0, -1, 0.6f) else Form.lp(-1, 0, 1f)
        side.layoutParams = if (wide) Form.lp(0, -1, 0.4f) else Form.lp(-1, -2)
        panel.setCompact(heightMode != 0)
        panel.layoutParams = Form.lp(-1, Ui.dp(this, if (heightMode == 0) 220 else 150))
        panel.visibility = if(heightMode == 2) View.GONE else View.VISIBLE
        mini.visibility = if(heightMode == 2) View.VISIBLE else View.GONE
        mini.render()
        updateHandle()
    }
    private fun updateHandle() {
        if (::mini.isInitialized) mini.render()
        val pending = GameRepo.state?.battle?.answers ?: 0
        handle.text = "${listOf("Full", "Strip", "Mini")[heightMode]} · ${if (pending > 0) "● ○" else "○ ○"}"
    }
    override fun onStart() { super.onStart(); started = true; BattleScreen.setVisible(this, true); LiveSession.addSink(sink); LiveSession.hold(this, this) }
    override fun onStop() { started = false; LiveSession.removeSink(sink); LiveSession.release(this, this); BattleScreen.setVisible(this, false); super.onStop() }
    override fun onResume() {
        super.onResume(); background = false; Updates.onResume(this); FloatBattleService.startIfWanted(this)
        if (shownAt != 0L) shownAt = SystemClock.elapsedRealtime()
        if (::web.isInitialized && (finished || card != null && !locked)) loadNext()
    }
    override fun onPause() {
        pauseClock(); background = true; stopAudio(); Updates.onPause(this); super.onPause()
    }
    private fun pauseClock() { if (shownAt != 0L) elapsed += SystemClock.elapsedRealtime() - shownAt; shownAt = 0 }
    override fun onDestroy() { generation++; main.removeCallbacksAndMessages(null); events.clear(); stopAudio(); web.destroy(); io.shutdown(); super.onDestroy() }
    private fun setupWeb() {
        web.settings.javaScriptEnabled = true
        web.settings.allowFileAccess = false; web.settings.allowContentAccess = false
        web.settings.textZoom = prefs.getInt("zoom", 100).coerceIn(80, 150)
        web.addJavascriptInterface(object {
            @JavascriptInterface fun play(name: String) { main.post { playSounds(listOf(name)) } }
            @JavascriptInterface fun flip() { main.post { this@StudyActivity.flip() } }
            @JavascriptInterface fun actions() { main.post { this@StudyActivity.actions() } }
            @JavascriptInterface fun mathjax(s: String) {}
            @JavascriptInterface fun jsError(s: String) {}
        }, "AnkiGo")
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (!flipped && card != null && !background && shownAt == 0L) shownAt = SystemClock.elapsedRealtime()
            }
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                if (req.url.scheme in listOf("http", "https") && req.url.host != "ankigo.media") runCatching { startActivity(Intent(Intent.ACTION_VIEW, req.url)) }
                return true
            }
            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                if (req.url.host != "ankigo.media") return WebResourceResponse("text/plain", "UTF-8", "".byteInputStream())
                val name = req.url.path?.trimStart('/') ?: return null
                val stream = if (name.startsWith("mathjax/")) runCatching { assets.open(name) }.getOrNull() else Media.open(this@StudyActivity, name)
                if (stream == null) return WebResourceResponse("image/svg+xml", "UTF-8", "<svg xmlns='http://www.w3.org/2000/svg' width='180' height='90'><rect width='180' height='90' fill='#ddd'/><text x='10' y='48' fill='#555'>Picture unavailable</text></svg>".byteInputStream())
                return WebResourceResponse(android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.')) ?: "application/octet-stream", null, stream)
            }
        }
    }
    private fun loadNext() {
        stopAudio(); summaryView?.let { cardColumn.removeView(it) }; summaryView = null; web.visibility = View.VISIBLE; locked = true; card = null; buttons.removeAllViews(); notice.visibility = View.GONE
        val token = ++generation
        io.execute {
            val result = runCatching {
                check(Anki.studySupported(this)) { "Studying here needs AnkiDroid 2.24 or newer. Companion mode is still available." }
                Anki.selectDeck(this, deckId)
                var next: Anki.Review? = null
                var problem: Exception? = null
                repeat(3) {
                    if (next == null) try {
                        if (it > 0) { main.post { if(valid(token)) header.text = "$deckName · Waking AnkiDroid…" }; Thread.sleep(250) }
                        next = Anki.nextCards(this, deckId, 1).firstOrNull(); problem = null } catch (e: Exception) { problem = e }
                }
                problem?.let { throw it }
                val decks = Anki.decks(this)
                val n = next
                if (n == null) {
                    if ((decks.firstOrNull { it.id == deckId }?.total ?: 0) > 0) {
                        main.post { if(valid(token)) showError("AnkiDroid hasn't opened its study queue. Open AnkiDroid once, then retry.") }
                        return@runCatching
                    }
                    val queryDeck = "deck:\"${deckName.replace("\"", "") }\""
                    val learn = Anki.searchCards(this, "$queryDeck is:learn", arrayOf("_id")).size
                    val fresh = Anki.searchCards(this, "$queryDeck is:new", arrayOf("_id")).size
                    main.post { if (valid(token)) summary(decks, learn, fresh) }
                    return@runCatching
                }
                val html = Anki.card(this, n.nid, n.ord) ?: kotlin.error("Open AnkiDroid once, then retry.")
                val state = Anki.card(this, n.nid, n.ord, CardState.COLS + "ord") ?: kotlin.error("Couldn't read this card's scheduling state.")
                check(CardState.from(state) != null) { "AnkiDroid didn't provide the card's review count." }
                val note = Anki.note(this, n.nid)
                val model = note?.let { Anki.model(this, it.mid) }
                val q = html["question"] ?: ""; val a = html["answer"] ?: ""
                val fieldMap = if (model != null) model.fieldNames.zip(note.fields).toMap() else emptyMap()
                val files = CardMedia.files(q + a + (model?.css ?: ""), n.media)
                val missing = files.filter { Media.open(this, it)?.use { false } ?: true }
                val blocked = missing.filterNot { CardMedia.isSound(it) }
                val unsupported = CardMedia.unsupported(q + a)
                val options = JSONObject(Anki.deckOptions(this, deckId))
                main.post {
                    if (!valid(token)) return@post
                    finished = false; card = n; stats = state; question = q; answerHtml = a; css = model?.css ?: ""; fields = fieldMap
                    maxTime = options.optLong("maxTaken", 60).coerceAtLeast(1) * 1000
                    val d = decks.firstOrNull { it.id == deckId }
                    val line = android.text.SpannableStringBuilder("$deckName\n")
                    val queue = state["queue"]?.toIntOrNull()
                    listOf("New ${d?.new ?: 0}" to R.color.ag_blue, "Learn ${d?.learn ?: 0}" to R.color.ag_red, "Review ${d?.review ?: 0}" to R.color.ag_green).forEachIndexed { i, (label, color) ->
                        val from = line.length; line.append(label)
                        line.setSpan(android.text.style.ForegroundColorSpan(Ui.c(this, color)), from, line.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        val current = when(queue) { 0 -> 0; 1, 3 -> 1; else -> 2 }
                        if (i == current) line.setSpan(android.text.style.UnderlineSpan(), from, line.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        if (i != 2) line.append(" · ")
                    }
                    header.text = line
                    mediaBlocked = (blocked.isNotEmpty() || unsupported) && prefs.getString("missing", "Ask") != "Answer anyway"
                    showQuestion(); mediaNotice(blocked.size, missing.size - blocked.size, unsupported)
                    if (mediaBlocked && prefs.getString("missing", "Ask") == "Always open in AnkiDroid") openAnki()
                }
            }
            result.onFailure { main.post { if (valid(token)) showError(it.message ?: "Open AnkiDroid once, then retry.") } }
        }
    }
    private fun valid(token: Int) = !isDestroyed && token == generation
    private fun page(content: String, side: String, typed: String?) = CardHtml.build(content, css, card!!.ord, side, typed,
        dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) { fields[it] }
    private fun showQuestion() {
        val key = card!!.nid to card!!.ord
        if (timedCard != key) elapsed = 0
        timedCard = key
        flipped = false; shownAt = 0; locked = false
        val page = page(question, "q", null); sounds = page.sounds
        web.loadDataWithBaseURL(CardHtml.BASE, page.html, "text/html", "UTF-8", null)
        showButtons(); playSounds(sounds)
    }
    private fun flip() {
        if (locked || flipped || mediaBlocked || card == null) return
        locked = true
        web.evaluateJavascript("document.getElementById('typeans')?.value || ''") { raw ->
            if (isDestroyed || card == null) return@evaluateJavascript
            val typed = runCatching { org.json.JSONArray("[$raw]").getString(0) }.getOrDefault("")
            val page = page(answerHtml, "a", typed); sounds = page.sounds
            flipped = true; locked = false; allowAt = SystemClock.elapsedRealtime() + 300
            web.loadDataWithBaseURL(CardHtml.BASE, page.html, "text/html", "UTF-8", null)
            web.announceForAccessibility("Answer shown"); showButtons(); playSounds(sounds)
        }
    }
    private fun showButtons() {
        buttons.removeAllViews()
        if (!flipped) { buttons.addView(Form.primary(this, "Show answer") { flip() }.apply { minimumHeight = Ui.dp(context, 64); isEnabled = !locked && !mediaBlocked }); return }
        val n = card ?: return
        val labels = when (n.buttons) { 2 -> listOf("Again", "Good"); 3 -> listOf("Again", "Good", "Easy"); else -> listOf("Again", "Hard", "Good", "Easy") }
        val wide = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && resources.configuration.smallestScreenWidthDp < 600
        var row = LinearLayout(this)
        buttons.addView(row)
        labels.forEachIndexed { i, label ->
            if (wide && i == 2) { row = LinearLayout(this); buttons.addView(row) }
            val ease = i + 1
            val color = when(label) { "Again" -> R.color.ag_red; "Hard" -> R.color.ag_peach; "Good" -> R.color.ag_green; else -> R.color.ag_blue }
            val interval = n.nextTimes.getOrElse(i) { "" }
            row.addView(Form.secondary(this, "$label\n$interval", color) { answer(ease) }.apply {
                minimumHeight = Ui.dp(context, 64)
                setTextColor(Ui.c(context, if(resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) R.color.ag_crust else R.color.ag_card))
                background = Ui.rounded(Ui.c(context, color), 12, context)
                contentDescription = "$label, next in $interval"; isEnabled = !locked && !mediaBlocked
            }, Form.lp(0, -2, 1f))
        }
    }
    private fun answer(ease: Int) {
        if (locked || !flipped || mediaBlocked || ease !in 1..(card?.buttons ?: 4) || SystemClock.elapsedRealtime() < allowAt) return
        val c = card ?: return
        val before = stats
        val state = CardState.from(before) ?: return
        pauseClock(); val took = elapsed.coerceAtMost(maxTime)
        if (prefs.getBoolean("haptic", true)) web.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        locked = true; showButtons()
        io.execute {
            val result = runCatching {
                StudyLoop(object : StudyLoop.Provider {
                    override fun select() { Anki.selectDeck(this@StudyActivity, deckId) }
                    override fun top() = Anki.nextCards(this@StudyActivity, deckId, 1).firstOrNull()?.let { StudyLoop.Key(it.nid, it.ord) }
                    override fun stats(key: StudyLoop.Key) = Anki.card(this@StudyActivity, key.nid, key.ord, CardState.COLS)
                    override fun answer(key: StudyLoop.Key, ease: Int, ms: Long) { Anki.answer(this@StudyActivity, key.nid, key.ord, ease, ms) }
                }).answer(StudyLoop.Key(c.nid, c.ord), before, ease, took) { LiveSession.expectOwn(state) }
            }
            main.post {
                result.onSuccess { r ->
                    when (r.status) {
                        StudyLoop.Status.SAVED -> {
                            val button = com.husarp.ankigo.study.StudyRules.gameButton(c.buttons, ease)
                            counts[button - 1]++; totalTime += took; timedCard = null
                            Local.recordReview(this, c.nid, c.ord, ease, took, "app")
                            val fresh = CardState.from(r.row!!)!!
                            LiveSession.ownAnswer(this, fresh, button) { record(it); if (!isDestroyed) loadNext() }
                        }
                        StudyLoop.Status.MOVED -> { LiveSession.cancelOwn(state); if (!isDestroyed) loadNext() }
                        StudyLoop.Status.FAILED -> { LiveSession.cancelOwn(state); if (!isDestroyed) showError("This answer wasn't saved. Reload before retrying.") }
                    }
                }.onFailure { LiveSession.cancelOwn(state); if (!isDestroyed) showError("Couldn't confirm that this answer was saved. Reload before retrying.") }
            }
        }
    }
    private fun record(list: List<GameEvent>) {
        for (e in list) when (e) {
            is GameEvent.Cash -> coins += e.amount
            is GameEvent.XpGained -> xp += e.xp
            is GameEvent.LevelUp -> levels++
            is GameEvent.Evolved -> evolutions++
            is GameEvent.Caught -> caught += e.name
            is GameEvent.Defeated -> defeated++
            is GameEvent.Encounter -> met++
            is GameEvent.ItemFound -> items += "${e.count} ${e.item}"
            else -> {}
        }
    }
    private fun mediaNotice(pictures: Int, sound: Int, unsupported: Boolean) {
        notice.removeAllViews()
        notice.visibility = if (pictures > 0 || sound > 0 || unsupported) View.VISIBLE else View.GONE
        if (pictures > 0 || unsupported) {
            notice.addView(Form.body(this, if (unsupported) "This card needs AnkiDroid" else "$pictures picture${if (pictures == 1) " can't" else "s can't"} be shown here", 13, R.color.ag_red))
            notice.addView(Form.body(this, "Get AnkiDroid from F-Droid or GitHub and pictures work here. Sync in AnkiDroid, install the other build, sync back.", 12))
            val links = LinearLayout(this)
            links.addView(Form.secondary(this, "F-Droid") { openUrl("https://f-droid.org/packages/com.ichi2.anki/") })
            links.addView(Form.secondary(this, "GitHub") { openUrl("https://github.com/ankidroid/Anki-Android/releases") }); notice.addView(links)
            val row = LinearLayout(this)
            row.addView(Form.secondary(this, "Open in AnkiDroid") { openAnki() }, Form.lp(0, -2, 1f))
            row.addView(Form.secondary(this, "Answer anyway") { mediaBlocked = false; notice.visibility = View.GONE; showButtons() }, Form.lp(0, -2, 1f)); notice.addView(row)
        } else notice.addView(Form.body(this, "$sound sound file${if (sound == 1) "" else "s"} unavailable here", 12))
    }
    private fun showError(message: String) {
        locked = true; notice.removeAllViews(); notice.visibility = View.VISIBLE
        notice.addView(Form.body(this, message, 14, R.color.ag_red))
        notice.addView(Form.primary(this, "Retry / reload") { loadNext() })
        notice.addView(Form.secondary(this, "Open in AnkiDroid") { openAnki() })
    }
    private fun throwBall() {
        if (!events.idle) return
        val st = GameRepo.state ?: return
        val balls = st.bag.filter { (id, count) -> count > 0 && com.husarp.ankigo.game.Items.def(id)?.kind == com.husarp.ankigo.game.ItemKind.BALL }.keys.toList()
        if (balls.isEmpty()) { Ui.toast(this, "No Poké Balls. Buy some in the shop."); return }
        AlertDialog.Builder(this).setTitle("Throw a ball").setItems(balls.map { "${com.husarp.ankigo.game.Items.name(it)} ×${st.count(it)}" }.toTypedArray()) { _, i ->
            val result = GameRepo.act(this) { it.throwBall(balls[i]) }
            record(result); events.enqueue(result); LiveSession.refreshWidgetsFor(this, result)
        }.setNegativeButton("Cancel", null).show()
    }
    private fun actions() {
        if (locked || card == null) return
        val choices = arrayListOf("Bury card", "Suspend card", "Edit note", "Continue in AnkiDroid", "Replay sound", "Delete note…", "Undo…")
        if (Anki.flagsSupported(this)) choices += "Flag…"
        AlertDialog.Builder(this).setTitle("Card actions").setItems(choices.toTypedArray()) { _, i ->
            when(i) {
                0 -> writeAction("bury")
                1 -> writeAction("suspend")
                2 -> edit()
                3 -> openAnki()
                4 -> playSounds(sounds)
                5 -> confirmDelete()
                6 -> AlertDialog.Builder(this).setMessage("AnkiGo can't undo answers, bury, suspend or delete. Use Undo in AnkiDroid. The battle doesn't rewind; a redo won't count twice.").setPositiveButton("Open AnkiDroid") { _, _ -> openAnki() }.setNegativeButton("Back", null).show()
                7 -> AlertDialog.Builder(this).setItems(arrayOf("None", "Red", "Orange", "Green", "Blue", "Pink", "Turquoise", "Purple")) { _, flag -> writeAction("flag", flag) }.show()
            }
        }.show()
    }
    private fun writeAction(action: String, flag: Int = 0) {
        val c = card ?: return
        locked = true; showButtons()
        io.execute {
            val result = runCatching {
                Anki.selectDeck(this, deckId)
                when(action) {
                    "bury" -> { Anki.bury(this, c.nid, c.ord); com.husarp.ankigo.study.StudyRules.buried(Anki.cardStats(this, c.nid, c.ord)) }
                    "suspend" -> { Anki.suspend(this, c.nid, c.ord); com.husarp.ankigo.study.StudyRules.suspended(Anki.cardStats(this, c.nid, c.ord)) }
                    "flag" -> Anki.flag(this, c.nid, c.ord, flag)
                    else -> { Anki.deleteNote(this, c.nid); Anki.note(this, c.nid) == null }
                }
            }
            main.post { if (!isDestroyed) { if (result.getOrDefault(false)) loadNext() else showError("This change wasn't saved.") } }
        }
    }
    private fun confirmDelete() {
        val c = card ?: return
        locked = true
        io.execute {
            val count = runCatching { Anki.searchCards(this, "nid:${c.nid}", arrayOf("_id")).size }.getOrNull()
            main.post { if (!isDestroyed) {
                locked = false
                AlertDialog.Builder(this).setTitle("Delete note?").setMessage("Deletes the note and all its ${count ?: "associated"} cards. AnkiGo can't undo this.")
                    .setPositiveButton("Delete") { _, _ -> writeAction("delete") }.setNegativeButton("Cancel", null).show()
            } }
        }
    }
    private fun edit() {
        val c = card ?: return
        val did = stats["deck_id"]?.toLongOrNull() ?: deckId
        io.execute {
            val result = runCatching { Anki.selectDeck(this, did) }
            main.post { if (!isDestroyed) result.onSuccess { runCatching { startActivity(Anki.editIntent(c.nid)) }.onFailure { showError("Couldn't open AnkiDroid's browser") } } }
        }
    }
    private fun openAnki() {
        io.execute {
            val result = runCatching { Anki.selectDeck(this, deckId) }
            main.post { if (!isDestroyed) result.onSuccess {
                // Bring up the existing battle activity so its PiP / floating mode follows the reviewer.
                if (GameRepo.game == null) startActivity(Anki.reviewIntent(deckId))
                else startActivity(Intent(this, GameActivity::class.java).putExtra("reviewDeck", deckId).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            }.onFailure { showError("Couldn't select this deck") } }
        }
    }
    private fun summary(decks: List<Anki.Deck>, learn: Int, fresh: Int) {
        finished = true; card = null; locked = true; buttons.removeAllViews(); notice.visibility = View.GONE
        val n = counts.sum()
        val why = when { learn > 0 -> "Learning cards come back in a few minutes"; fresh > 0 -> "Daily new-card limit reached, $fresh new cards left"; else -> "Done for today" }
        val st = GameRepo.state
        web.visibility = View.GONE
        val summary = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(context, 16), Ui.dp(context, 12), Ui.dp(context, 16), Ui.dp(context, 12)) }
        summary.addView(Ui.text(this, 23f, 900, R.color.ag_mauve).apply { text = why })
        summary.addView(Form.body(this, "$n cards · ${totalTime / 60000} minutes · ${if(n > 0) (n-counts[0])*100/n else 0}% correct", 15), Form.lp(this, 12))
        val stack = LinearLayout(this)
        counts.forEachIndexed { i, value ->
            if (value > 0) stack.addView(View(this).apply { setBackgroundColor(Ui.c(context, listOf(R.color.ag_red, R.color.ag_peach, R.color.ag_green, R.color.ag_blue)[i])) }, Form.lp(0, Ui.dp(this, 8), value.toFloat()))
        }
        summary.addView(stack, Form.lp(this, 8))
        summary.addView(Form.body(this, "Again ${counts[0]} · Hard ${counts[1]} · Good ${counts[2]} · Easy ${counts[3]}", 12), Form.lp(this, 6))
        summary.addView(Form.sect(this, "Battle results", "$met met"))
        val sprites = LinearLayout(this)
        caught.forEach { name ->
            val sp = GameRepo.data?.species?.values?.firstOrNull { it.name == name }
            if (sp != null) sprites.addView(android.widget.ImageView(this).apply { Ui.staticSprite(context, sp.num, false, this); contentDescription = "Caught $name" }, Form.lp(Ui.dp(this, 48), Ui.dp(this, 48)))
        }
        summary.addView(android.widget.HorizontalScrollView(this).apply { addView(sprites) })
        summary.addView(Form.body(this, "$defeated defeated · ${caught.size} caught\n${caught.joinToString()}\n+$xp XP · $levels level-ups · $evolutions evolutions\n+$coins ₽\nFound: ${items.joinToString().ifEmpty { "No items" }}", 14))
        summary.addView(Form.sect(this, "Today's goal", "${st?.streakDays ?: 0} day streak"))
        val today = st?.reviewsToday ?: 0; val goal = st?.settings?.dailyAverage ?: 100
        summary.addView(Form.body(this, "$today / $goal"))
        summary.addView(BarView(this).apply { set(today.toFloat()/goal.coerceAtLeast(1), Ui.c(context, R.color.ag_mauve), false) }, Form.lp(-1, Ui.dp(this, 8)))
        summaryView = ScrollView(this).apply { addView(summary) }.also { cardColumn.addView(it, Form.lp(-1, 0, 1f)) }
        val next = decks.firstOrNull { it.id != deckId && it.total > 0 }
        if (next != null) buttons.addView(Form.primary(this, "Next deck · ${next.name} · ${next.total}") {
            startActivity(Intent(this, StudyActivity::class.java).putExtra("deckId", next.id).putExtra("deckName", next.name)); finish()
        })
        buttons.addView(Form.secondary(this, "Back to decks") { finish() })
        buttons.addView(Form.secondary(this, "Sync now") { runCatching { startActivity(Anki.syncIntent()) }.onFailure { Ui.toast(this, "Open AnkiDroid to sync") } })
        buttons.addView(Form.secondary(this, "Custom study in AnkiDroid") { openAnki() })
        if (learn > 0) main.postDelayed({ recheckLearning() }, 30_000)
    }
    private fun recheckLearning() {
        if (!finished || background || isDestroyed) return
        val token = generation
        io.execute {
            val available = runCatching { Anki.selectDeck(this, deckId); Anki.nextCards(this, deckId, 1).isNotEmpty() }.getOrDefault(false)
            main.post {
                if (!valid(token) || !finished || background) return@post
                if (available) buttons.addView(Form.primary(this, "Continue · learning cards are ready") { loadNext() })
                else main.postDelayed({ recheckLearning() }, 30_000)
            }
        }
    }
    private fun openUrl(url: String) { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
    private fun stopAudio() { soundQueue.clear(); audio?.release(); audio = null }
    private fun playSounds(names: List<String>) { stopAudio(); soundQueue.addAll(names); playNextSound() }
    private fun playNextSound() {
        val name = soundQueue.removeFirstOrNull() ?: return
        val found = Media.find(this, name) ?: return playNextSound()
        try {
            val p = MediaPlayer(); audio = p
            if (found.file != null) p.setDataSource(found.file.absolutePath) else p.setDataSource(this, found.uri!!)
            p.setOnPreparedListener { it.start() }
            p.setOnCompletionListener { it.release(); audio = null; playNextSound() }
            p.setOnErrorListener { mp, _, _ -> mp.release(); audio = null; playNextSound(); true }
            p.prepareAsync()
        } catch (_: Exception) { audio?.release(); audio = null; playNextSound() }
    }
    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.firstOrNull() == 0) { LiveSession.restart(this); loadNext() }
    }
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (web.hasFocus() && event.isPrintingKey && keyCode !in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_4) return super.onKeyUp(keyCode, event)
        if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER) { flip(); return true }
        if (keyCode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_4 && flipped) { answer(keyCode - KeyEvent.KEYCODE_1 + 1); return true }
        return super.onKeyUp(keyCode, event)
    }
}
