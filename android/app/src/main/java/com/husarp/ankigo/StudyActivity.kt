package com.husarp.ankigo

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

/** C2/C3/C4/C5/C11: study loop on our own screen. */
class StudyActivity : Activity() {
    private val bg = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var deckId = 0L
    private var deckName = ""

    private lateinit var battle: BattleView
    private lateinit var status: TextView
    private lateinit var web: WebView
    private lateinit var showBtn: Button
    private lateinit var answerRow: LinearLayout
    private val easeBtns = ArrayList<Button>()

    private var card: Anki.Review? = null
    private var q = ""; private var a = ""
    private var css = ""
    private var fields: Map<String, String> = emptyMap()
    private var shownAt = 0L
    private var onAnswer = false
    private var questionSounds: List<String> = emptyList()
    private var answerSounds: List<String> = emptyList()
    private var player: MediaPlayer? = null
    private val queue = ArrayDeque<String>()
    private var editPending: Pair<Long, Int>? = null
    private var editQuestionBefore = ""
    private var reviewedHere = 0
    private var mathjaxLogged = false
    private val sessionStart = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Report.init(this)
        deckId = intent.getLongExtra("deckId", 0)
        deckName = intent.getStringExtra("deckName") ?: "?"
        getSharedPreferences("spike", 0).edit().putLong("studyDeck", deckId).putString("studyDeckName", deckName).apply()
        title = "Study: $deckName"

        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        battle = BattleView(this)
        root.addView(battle, LinearLayout.LayoutParams(-1, (130 * dp).toInt()))
        status = TextView(this).apply { textSize = 11f; setPadding(12, 4, 12, 4); setBackgroundColor(Color.rgb(235, 235, 235)) }
        root.addView(status)
        web = WebView(this)
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))

        showBtn = Button(this).apply { text = "Show answer"; setOnClickListener { flip() } }
        root.addView(showBtn)
        answerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; visibility = View.GONE }
        listOf("Again", "Hard", "Good", "Easy").forEachIndexed { i, label ->
            val b = Button(this).apply { text = label; setOnClickListener { answer(i + 1) } }
            easeBtns.add(b)
            answerRow.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(answerRow)

        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        fun tool(label: String, f: () -> Unit) = tools.addView(Button(this).apply { text = label; textSize = 11f; setOnClickListener { f() } })
        tool("Bury") { act("bury") }
        tool("Suspend") { act("suspend") }
        tool("Delete") { confirmDelete() }
        tool("Undo") { undo() }
        tool("Edit") { edit() }
        tool("Replay") { replay() }
        tool("Battle fps") { Report.log("C11", "battle panel: ${battle.statsLine()}") }
        root.addView(HorizontalScrollView(this).apply { addView(tools) })
        setContentView(root)

        setupWebView()
        Report.log("C2", "study screen opened for deck '$deckName' ($deckId) on ${Anki.pkg}")
        loadNext()
    }

    private fun setupWebView() {
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.addJavascriptInterface(object {
            @JavascriptInterface fun play(name: String) = main.post { playList(listOf(name)) }.let { }
            @JavascriptInterface fun mathjax(s: String) { if (s != "loaded" || !mathjaxLogged) Report.log("C3", "MathJax $s"); mathjaxLogged = true }
            @JavascriptInterface fun jsError(s: String) = Report.fail("C3", "card JS error: $s")
        }, "AnkiGo")
        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) Report.fail("C3", "console: ${m.message()}")
                return true
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                val u = req.url
                if (u.host != "ankigo.media") return null
                val name = u.path?.trimStart('/') ?: return null
                if (name.isEmpty() || name == "favicon.ico") return null
                val ins = Media.open(this@StudyActivity, name)
                val decoded = Uri.decode(name)
                if (ins == null) {
                    Report.fail("C3", "media '$decoded' not readable (tried direct paths, SAF folder, imported copy)")
                    return WebResourceResponse("text/plain", "utf-8", 404, "Not found", emptyMap(), "".byteInputStream())
                }
                Report.ok("C3", "media '$decoded' via ${Media.lastSource[decoded]}")
                return WebResourceResponse(mime(decoded), null, ins)
            }

            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                Report.log("C3", "link tapped: ${req.url}")
                return true
            }
        }
    }

    private fun mime(n: String) = when (n.substringAfterLast('.').lowercase()) {
        "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"; "webp" -> "image/webp"
        "svg" -> "image/svg+xml"; "mp3" -> "audio/mpeg"; "ogg", "oga" -> "audio/ogg"; "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"; "mp4" -> "video/mp4"; "webm" -> "video/webm"; "css" -> "text/css"; "js" -> "application/javascript"
        "ttf" -> "font/ttf"; "woff" -> "font/woff"; "woff2" -> "font/woff2"
        else -> "application/octet-stream"
    }

    private fun setStatus(s: String) = main.post { status.text = s }

    private fun loadNext() {
        stopAudio()
        showBtn.isEnabled = false
        bg.execute {
            try {
                val t0 = System.currentTimeMillis()
                val next = Anki.nextCards(this, deckId, 1).firstOrNull()
                if (next == null) {
                    Report.log("C2", "no more cards due in '$deckName' (schedule returned 0 rows)")
                    main.post { card = null; web.loadData("<h3 style='font-family:sans-serif'>Done! No more cards due in this deck.</h3>", "text/html", "utf-8"); answerRow.visibility = View.GONE; showBtn.visibility = View.GONE }
                    return@execute
                }
                val c = Anki.card(this, next.nid, next.ord) ?: throw IllegalStateException("card query empty")
                val note = Anki.note(this, next.nid)
                val model = note?.let { Anki.model(this, it.mid) }
                val ms = System.currentTimeMillis() - t0
                val deck = Anki.decks(this).firstOrNull { it.id == deckId }
                q = c["question"] ?: ""; a = c["answer"] ?: ""
                css = model?.css ?: ""
                fields = if (note != null && model != null) model.fieldNames.zip(note.fields).toMap() else emptyMap()
                Report.ok("C2", "next card nid=${next.nid} ord=${next.ord} model='${model?.name}' buttons=${next.buttons} next=${next.nextTimes} media=${next.media} (fetch ${ms}ms)")
                setStatus("$deckName · learn ${deck?.learn} / review ${deck?.review} / new ${deck?.new} · nid ${next.nid} ord ${next.ord} · ${model?.name} · AnkiGo reviews today: ${Local.todayCount(this)}")
                main.post {
                    card = next
                    easeBtns.forEachIndexed { i, b -> b.text = listOf("Again", "Hard", "Good", "Easy")[i] + "\n" + next.nextTimes.getOrElse(i) { "" } }
                    showQuestion()
                }
            } catch (e: Exception) {
                Report.fail("C2", "load next card", e)
                setStatus("Error: ${e.message}")
            }
        }
    }

    private fun showQuestion() {
        val c = card ?: return
        val page = CardHtml.build(q, css, c.ord, "q", null) { fields[it] }
        if (page.notes.isNotEmpty()) Report.log("C3", "Q nid=${c.nid}: ${page.notes.joinToString("; ")}")
        questionSounds = page.sounds
        web.loadDataWithBaseURL(CardHtml.BASE, page.html, "text/html", "utf-8", null)
        onAnswer = false
        shownAt = System.currentTimeMillis()
        showBtn.visibility = View.VISIBLE; showBtn.isEnabled = true
        answerRow.visibility = View.GONE
        playList(questionSounds)
    }

    private fun flip() {
        val c = card ?: return
        web.evaluateJavascript("(function(){var e=document.getElementById('typeans');return e?e.value:null})()") { v ->
            val typed = if (v == null || v == "null") null else v.trim('"')
            val page = CardHtml.build(a, css, c.ord, "a", typed) { fields[it] }
            answerSounds = page.sounds
            web.loadDataWithBaseURL(CardHtml.BASE, page.html, "text/html", "utf-8", null)
            onAnswer = true
            showBtn.visibility = View.GONE
            answerRow.visibility = View.VISIBLE
            stopAudio()
            playList(answerSounds)
        }
    }

    private fun answer(ease: Int) {
        val c = card ?: return
        val took = System.currentTimeMillis() - shownAt
        answerRow.visibility = View.GONE
        battle.hit(ease)
        bg.execute {
            try {
                val before = Anki.cardStats(this, c.nid, c.ord)
                val n = Anki.answer(this, c.nid, c.ord, ease, took, deckId)
                val after = Anki.cardStats(this, c.nid, c.ord)
                val changed = before != null && after != null &&
                    (before["reps"] != after["reps"] || before["due"] != after["due"] || before["queue"] != after["queue"])
                val verify = if (before == null) "verify n/a (old API)" else
                    "reps ${before["reps"]}->${after?.get("reps")}, due ${before["due"]}->${after?.get("due")}, ivl ${before["interval"]}->${after?.get("interval")}, queue ${before["queue"]}->${after?.get("queue")}, lastReview ${after?.get("last_review_time_secs")}"
                if (n > 0 && (before == null || changed)) Report.ok("C2", "answer ease=$ease took=${took}ms rows=$n; $verify")
                else Report.fail("C2", "answer ease=$ease rows=$n but card did not change; $verify")
                Local.recordReview(this, c.nid, c.ord, ease, took, "app")
                reviewedHere++
                Widgets.refreshAll(this)
            } catch (e: Exception) {
                Report.fail("C2", "answer ease=$ease", e)
            }
            main.post { loadNext() }
        }
    }

    private fun act(what: String) {
        val c = card ?: return
        bg.execute {
            try {
                val n = if (what == "bury") Anki.bury(this, c.nid, c.ord) else Anki.suspend(this, c.nid, c.ord)
                val st = Anki.cardStats(this, c.nid, c.ord)
                val q = st?.get("queue")
                val expected = if (what == "bury") q == "-3" || q == "-2" else q == "-1"
                val next = Anki.nextCards(this, deckId, 1).firstOrNull()
                val moved = next == null || next.nid != c.nid || next.ord != c.ord
                if (n > 0 && (st == null || expected) && moved) Report.ok("C4", "$what nid=${c.nid} ord=${c.ord}: rows=$n queue=$q, next card is different=$moved")
                else Report.fail("C4", "$what nid=${c.nid} ord=${c.ord}: rows=$n queue=$q, next card is different=$moved")
            } catch (e: Exception) {
                Report.fail("C4", what, e)
            }
            main.post { loadNext() }
        }
    }

    private fun confirmDelete() {
        val c = card ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete note?")
            .setMessage("This deletes the whole note ${c.nid} (all its cards) in AnkiDroid. The API has no single-card delete and no undo. Use a test card!")
            .setPositiveButton("Delete") { _, _ ->
                bg.execute {
                    try {
                        val n = Anki.deleteNote(this, c.nid)
                        val still = try { Anki.note(this, c.nid) != null } catch (_: Exception) { false }
                        if (n > 0 && !still) Report.ok("C4", "delete note nid=${c.nid}: rows=$n, note gone=true")
                        else Report.fail("C4", "delete note nid=${c.nid}: rows=$n, note still there=$still")
                    } catch (e: Exception) {
                        Report.fail("C4", "delete note", e)
                    }
                    main.post { loadNext() }
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun undo() {
        bg.execute {
            val probes = listOf<Pair<String, () -> Any?>>(
                "ContentResolver.call(\"undo\")" to { contentResolver.call(Anki.uri(""), "undo", null, null) },
                "update(content://…/undo)" to { contentResolver.update(Anki.uri("undo"), android.content.ContentValues(), null, null) },
            )
            for ((name, f) in probes) {
                try {
                    Report.log("C4", "undo probe $name -> returned ${f()}")
                } catch (e: Exception) {
                    Report.fail("C4", "undo probe $name", e)
                }
            }
            Report.log("C4", "undo: the API has no undo endpoint (checked AnkiDroid source); probes above show what this version does")
        }
    }

    private fun edit() {
        val c = card ?: return
        val pkg = Anki.pkg
        val search = "nid:${c.nid}"
        val options = listOf<Pair<String, () -> Intent>>(
            "1. NoteEditorActivity (explicit, likely not exported)" to {
                Intent().setComponent(ComponentName(pkg, "com.ichi2.anki.NoteEditorActivity"))
                    .putExtra("CALLER", 1).putExtra("NOTE_ID", c.nid).putExtra("EXTRA_EDIT_FROM_CARD_ID", c.nid)
            },
            "2. Browser deep link anki://x-callback-url/browser?search=nid:…" to {
                Intent(Intent.ACTION_VIEW, Uri.parse("anki://x-callback-url/browser?search=" + Uri.encode(search))).setPackage(pkg)
            },
            "3. PROCESS_TEXT → CardBrowserContextMenuAction (needs AnkiDroid setting)" to {
                Intent(Intent.ACTION_PROCESS_TEXT).setComponent(ComponentName(pkg, "com.ichi2.anki.CardBrowserContextMenuAction"))
                    .setType("text/plain").putExtra(Intent.EXTRA_PROCESS_TEXT, search).putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
            },
            "4. Open AnkiDroid reviewer on this deck (IntentHandler EXTRA_DECK_ID)" to {
                Intent().setComponent(ComponentName(pkg, "com.ichi2.anki.IntentHandler")).putExtra("EXTRA_DECK_ID", deckId)
            },
            "5. Just open AnkiDroid" to { packageManager.getLaunchIntentForPackage(pkg) ?: Intent() },
        )
        AlertDialog.Builder(this).setTitle("Edit: how to open AnkiDroid? (auto = try 1,2,3 in order)")
            .setItems((listOf("AUTO (1→2→3)") + options.map { it.first }).toTypedArray()) { _, which ->
                val toTry = if (which == 0) options.take(3) else listOf(options[which - 1])
                for ((name, mk) in toTry) {
                    try {
                        val i = mk()
                        editPending = c.nid to c.ord
                        editQuestionBefore = q + "\u0000" + a
                        startActivity(i)
                        Report.ok("C5", "edit via '$name' launched (check if it is the editor / browser at nid:${c.nid})")
                        return@setItems
                    } catch (e: Exception) {
                        editPending = null
                        Report.fail("C5", "edit via '$name'", e)
                    }
                }
            }.show()
    }

    override fun onResume() {
        super.onResume()
        com.husarp.ankigo.update.Updates.onResume(this)
        battle.running = true; battle.invalidate()
        val p = editPending ?: return
        editPending = null
        bg.execute {
            try {
                val c = Anki.card(this, p.first, p.second)
                if (c == null) {
                    Report.log("C5", "on return: card nid=${p.first} no longer exists (deleted?)")
                    main.post { loadNext() }
                    return@execute
                }
                val changed = (c["question"] ?: "") + "\u0000" + (c["answer"] ?: "") != editQuestionBefore
                Report.log("C5", "on return: reloaded nid=${p.first}; content changed=$changed")
                val note = Anki.note(this, p.first)
                val model = note?.let { Anki.model(this, it.mid) }
                q = c["question"] ?: ""; a = c["answer"] ?: ""
                fields = if (note != null && model != null) model.fieldNames.zip(note.fields).toMap() else fields
                main.post { if (onAnswer) { onAnswer = false; showQuestion(); flip() } else showQuestion() }
            } catch (e: Exception) {
                Report.fail("C5", "reload on return", e)
            }
        }
    }

    override fun onPause() {
        com.husarp.ankigo.update.Updates.onPause(this)
        super.onPause()
        battle.running = false
        stopAudio()
    }

    private fun replay() = playList(if (onAnswer) answerSounds else questionSounds)

    private fun playList(names: List<String>) {
        stopAudio()
        queue.clear(); queue.addAll(names)
        playNextInQueue()
    }

    private fun playNextInQueue() {
        val name = queue.removeFirstOrNull() ?: return
        val f = Media.find(this, name)
        if (f == null) {
            Report.fail("C3", "sound '$name' not readable")
            playNextInQueue(); return
        }
        try {
            val mp = MediaPlayer()
            if (f.file != null) mp.setDataSource(f.file.absolutePath) else mp.setDataSource(this, f.uri!!)
            mp.setOnCompletionListener { it.release(); if (player === it) player = null; playNextInQueue() }
            mp.setOnErrorListener { m, what, extra -> Report.fail("C3", "sound '$name' error $what/$extra"); m.release(); player = null; true }
            mp.prepare(); mp.start()
            player = mp
            Report.ok("C3", "sound '$name' playing via ${f.source}")
        } catch (e: Exception) {
            Report.fail("C3", "sound '$name'", e)
            playNextInQueue()
        }
    }

    private fun stopAudio() {
        queue.clear()
        player?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
        player = null
    }

    override fun onDestroy() {
        val mins = (System.currentTimeMillis() - sessionStart) / 60000.0
        Report.log("C11", "study session ended: ${"%.1f".format(mins)} min, $reviewedHere answers; battle ${battle.statsLine()}")
        stopAudio()
        web.destroy()
        bg.shutdown()
        super.onDestroy()
    }
}
