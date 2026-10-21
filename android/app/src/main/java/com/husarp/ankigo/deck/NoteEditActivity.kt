package com.husarp.ankigo.deck

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.Anki
import com.husarp.ankigo.CardHtml
import com.husarp.ankigo.Media
import com.husarp.ankigo.R
import com.husarp.ankigo.Report
import com.husarp.ankigo.study.TtsRules
import com.husarp.ankigo.ui.AgActivity
import com.husarp.ankigo.ui.AppBar
import com.husarp.ankigo.ui.StudyUi
import com.husarp.ankigo.ui.Ui
import java.util.concurrent.Executors

/**
 * Edit a note's fields (PLAN 70): one box per field with its name, Edit / Preview (the card rendered as in study, from the texts in the
 * boxes now), Save. The fields are the raw texts AnkiDroid keeps (HTML), so tags such as `<img>` and `[sound:]` can be seen and kept.
 * Opened from the study card menu with the note id, the card's number (for Preview) and its deck. Saving goes through
 * [DeckOps.editNote]: it refuses when the note changed in AnkiDroid meanwhile, writes, reads back and only then says "saved".
 * Everything that talks to AnkiDroid runs off the main thread. Leaving with unsaved text asks first.
 */
class NoteEditActivity : AgActivity() {
    companion object {
        const val EXTRA_NID = "nid"
        const val EXTRA_ORD = "ord"
        const val EXTRA_DECK = "deckId"
        private const val KEY_BEFORE = "draftBefore"
        private const val KEY_TEXTS = "draftTexts"
        private const val KEY_STALE = "draftStale"
        private const val KEY_STATUS = "draftStatus"
        internal const val PREVIEW_FAILED = "The preview can't be shown on this phone right now. Your text is still here."
    }

    /** Everything the page needs once the note is read. */
    internal class Loaded(
        val note: NoteForEdit, val deck: String?, val cards: Int?, val css: String, val cloze: Boolean, val templates: List<TtsRules.Template>,
    )

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var nid = 0L
    private var ord = 0
    private var deckId = 0L
    private lateinit var appBar: AppBar
    private lateinit var save: TextView
    private lateinit var segEdit: TextView
    private lateinit var segPreview: TextView
    private lateinit var status: TextView
    private lateinit var editScroll: ScrollView
    private lateinit var column: LinearLayout
    private lateinit var previewBox: LinearLayout
    private lateinit var previewCard: FrameLayout
    private lateinit var sideChips: LinearLayout
    private var web: WebView? = null
    private val edits = ArrayList<EditText>()
    private var loaded: Loaded? = null
    private var before: List<String> = emptyList()
    private var saving = false
    private var stale = false           // a save went out and could not be confirmed: stop, the note must be looked at in AnkiDroid
    private var draft: NoteDraft.Saved? = null     // the draft + original texts of a recreated page, waiting for the note to load
    private var draftStale = false
    private var draftStatus: String? = null
    private var preview = false
    private var answerSide = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nid = intent.getLongExtra(EXTRA_NID, 0L)
        ord = intent.getIntExtra(EXTRA_ORD, 0)
        deckId = intent.getLongExtra(EXTRA_DECK, 0L)
        savedInstanceState?.let { b ->
            val was = b.getStringArrayList(KEY_BEFORE); val typed = b.getStringArrayList(KEY_TEXTS)
            if (was != null && typed != null) { draft = NoteDraft.Saved(was, typed); draftStale = b.getBoolean(KEY_STALE); draftStatus = b.getString(KEY_STATUS) }
        }
        Anki.load(this)
        setContentView(buildContent())
        showMessage("Loading the note…")
        io.execute {
            val result = load()
            main.post { if (!isDestroyed) result.fold({ bind(it) }, { showMessage(it.message ?: "AnkiDroid did not answer properly.", true) }) }
        }
    }

    override fun onDestroy() { io.shutdown(); web?.destroy(); super.onDestroy() }

    /**
     * A recreated page (turn, split screen) must not lose what was typed: the texts in the boxes AND the texts the editor started from
     * go into the state, and [bind] brings both back once the note is read again, so a change made in AnkiDroid meanwhile is still caught.
     */
    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        val d = if (loaded != null) NoteDraft.toSave(before, texts()) else draft   // still loading: carry the waiting draft on
        if (d == null) return
        out.putStringArrayList(KEY_BEFORE, ArrayList(d.before)); out.putStringArrayList(KEY_TEXTS, ArrayList(d.texts))
        out.putBoolean(KEY_STALE, if (loaded != null) stale else draftStale)
        out.putString(KEY_STATUS, if (loaded != null) status.text.toString().takeIf { it.isNotEmpty() } else draftStatus)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() { leave() }

    /** Reads the note, its type (field names, CSS, templates), the card count and the deck name. Off the main thread. */
    private fun load(): Result<Loaded> {
        val ctx = applicationContext
        val ops = deckOps(ctx)
        val l = ops.loadNote(nid)
        val note = l.note ?: return Result.failure(IllegalStateException(l.message.ifEmpty { "That note can't be opened." }))
        val mid = runCatching { Anki.note(ctx, nid)?.mid }.getOrNull()
        val model = mid?.let { runCatching { Anki.model(ctx, it) }.getOrNull() }
        val templates = mid?.let { runCatching { Anki.templates(ctx, it) }.getOrNull() }.orEmpty()
        val cards = runCatching { Anki.countCards(ctx, "nid:$nid") }.getOrNull()
        val deck = runCatching { Anki.decks(ctx).firstOrNull { it.id == deckId }?.name }.getOrNull()
        return Result.success(Loaded(note, deck, cards, model?.css.orEmpty(), model?.type == 1, templates))
    }

    // ---- the page ---------------------------------------------------------------------------------------------------

    /** The page's views (app bar, Edit / Preview, content); the screenshots build it and call [bind] with a made-up note. */
    internal fun buildContent(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.c(this@NoteEditActivity, R.color.ag_base)) }
        appBar = Ui.appBar(this, "Edit note", back = true, onBack = { leave() })
        save = StudyUi.smallButton(this, "Save", primary = true) { save() }
        appBar.addView(save, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(this, 10) })
        DeckViews.enable(save, false)
        root.addView(appBar)

        val seg = LinearLayout(this).apply {
            background = Ui.rounded(Ui.c(context, R.color.ag_mantle), 12, context)
            setPadding(Ui.dp(context, 3), Ui.dp(context, 3), Ui.dp(context, 3), Ui.dp(context, 3))
        }
        segEdit = segment("Edit") { showPreview(false) }
        segPreview = segment("Preview") { showPreview(true) }
        seg.addView(segEdit, LinearLayout.LayoutParams(0, -2, 1f)); seg.addView(segPreview, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(seg, LinearLayout.LayoutParams(-1, -2).also { it.setMargins(Ui.dp(this, 16), Ui.dp(this, 2), Ui.dp(this, 16), Ui.dp(this, 6)) })

        status = StudyUi.label(this, "", 13, R.color.ag_red, 800).apply {
            visibility = View.GONE
            background = Ui.rounded((Ui.c(context, R.color.ag_red) and 0x00ffffff) or 0x22000000, 10, context)
            setPadding(Ui.dp(context, 12), Ui.dp(context, 8), Ui.dp(context, 12), Ui.dp(context, 8))
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2).also { it.setMargins(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 6)) })

        val body = FrameLayout(this)
        column = StudyUi.column(this).apply { setPadding(Ui.dp(context, 16), Ui.dp(context, 2), Ui.dp(context, 16), Ui.dp(context, 20)) }
        editScroll = ScrollView(this).apply {
            isFillViewport = false
            addView(column, FrameLayout.LayoutParams(StudyUi.pageWidth(context), -2, Gravity.CENTER_HORIZONTAL))
        }
        body.addView(editScroll, FrameLayout.LayoutParams(-1, -1))

        previewBox = StudyUi.column(this).apply { visibility = View.GONE; setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), Ui.dp(context, 16)) }
        sideChips = LinearLayout(this)
        previewBox.addView(sideChips, StudyUi.lp(-1, -2))
        previewCard = FrameLayout(this).apply {
            background = Ui.rounded(Ui.c(context, R.color.ag_card), 14, context)
            clipToOutline = true; outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        previewBox.addView(previewCard, StudyUi.spaced(this, 8).also { it.height = 0; it.weight = 1f })
        body.addView(previewBox, FrameLayout.LayoutParams(StudyUi.pageWidth(this), -1, Gravity.CENTER_HORIZONTAL))
        root.addView(body, StudyUi.lp(-1, 0, 1f))
        styleSegments()
        return root
    }

    /** Edit / Preview pill (as the other screens' segmented controls: the picked one lifts onto `ag_card`). */
    private fun segment(label: String, onTap: () -> Unit) = Ui.text(this, 12.5f, 900, R.color.ag_sub0).apply {
        text = label; gravity = Gravity.CENTER; maxLines = 1
        setPadding(0, Ui.dp(context, 11), 0, Ui.dp(context, 11))
        setOnClickListener { onTap() }
    }

    private fun styleSegments() {
        for ((v, on) in listOf(segEdit to !preview, segPreview to preview)) {
            v.setTextColor(Ui.c(this, if (on) R.color.ag_text else R.color.ag_sub0))
            v.background = if (on) Ui.rounded(Ui.c(this, R.color.ag_card), 10, this) else null
            v.isSelected = on
        }
    }

    private fun showMessage(text: String, problem: Boolean = false) {
        column.removeAllViews()
        column.addView(StudyUi.label(this, text, 14, if (problem) R.color.ag_red else R.color.ag_sub1, 800), StudyUi.spaced(this, 12))
        if (problem) column.addView(StudyUi.row(this).apply {
            addView(StudyUi.smallButton(context, "Back", true) { finish() })
            addView(StudyUi.smallButton(context, "Open in AnkiDroid") { runCatching { startActivity(Anki.editIntent(nid)) } }, StudyUi.lp(-2).also { it.marginStart = Ui.dp(context, 8) })
        }, StudyUi.spaced(this, 12))
    }

    /** Puts the note into the page: info line, one box per field, the warning. */
    internal fun bind(l: Loaded) {
        val shown = NoteDraft.resolve(draft, l.note.values)
        if (shown.restored) { stale = draftStale; draftStatus?.let { setStatus(it) } }
        draft = null; draftStale = false; draftStatus = null
        loaded = l; before = shown.before
        column.removeAllViews(); edits.clear()
        column.addView(StudyUi.row(this).apply {
            addView(StudyUi.icon(context, R.drawable.ic_info, R.color.ag_sub0), StudyUi.lp(Ui.dp(context, 15), Ui.dp(context, 15)).also { it.marginEnd = Ui.dp(context, 8) })
            addView(StudyUi.label(context, DeckTexts.editInfo(l.note.noteTypeName, l.deck), 12, R.color.ag_sub0, 700), StudyUi.lp(0, -2, 1f))
        }, StudyUi.lp(-1, -2).also { it.bottomMargin = Ui.dp(this, 4) })
        for ((i, name) in l.note.fieldNames.withIndex()) {
            column.addView(StudyUi.label(this, name.uppercase(), 11, R.color.ag_sub0, 900).apply { letterSpacing = .06f }, StudyUi.spaced(this, 12))
            val box = EditText(this).apply {
                typeface = Ui.font(context, 600)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(Ui.c(context, R.color.ag_text))
                Ui.tintEdit(this)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                minLines = 1; maxLines = 12
                gravity = Gravity.TOP or Gravity.START
                setPadding(Ui.dp(context, 14), Ui.dp(context, 11), Ui.dp(context, 14), Ui.dp(context, 11))
                background = fieldBackground(false); backgroundTintList = null
                setOnFocusChangeListener { v, focus -> v.background = fieldBackground(focus); v.backgroundTintList = null }
                setText(shown.texts[i])
                contentDescription = name
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) { refreshSave() }
                    override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
                })
            }
            edits += box
            column.addView(box, StudyUi.spaced(this, 6).also { it.height = -2 })
        }
        column.addView(StudyUi.row(this).apply {
            gravity = Gravity.TOP
            addView(StudyUi.icon(context, R.drawable.ic_alert, R.color.ag_sub0), StudyUi.lp(Ui.dp(context, 14), Ui.dp(context, 14)).also { it.marginEnd = Ui.dp(context, 8); it.topMargin = Ui.dp(context, 1) })
            addView(StudyUi.label(context, DeckTexts.editWarning(l.cards), 12, R.color.ag_sub0, 700), StudyUi.lp(0, -2, 1f))
        }, StudyUi.spaced(this, 16))
        refreshSave()
    }

    private fun fieldBackground(focused: Boolean) = GradientDrawable().apply {
        setColor(Ui.c(this@NoteEditActivity, R.color.ag_card))
        cornerRadius = Ui.dp(this@NoteEditActivity, 12f)
        setStroke(Ui.dp(this@NoteEditActivity, if (focused) 2 else 1), Ui.c(this@NoteEditActivity, if (focused) R.color.ag_mauve else R.color.ag_s0))
    }

    private fun texts() = edits.map { it.text.toString() }
    private fun dirty() = loaded != null && texts() != before

    private fun refreshSave() = DeckViews.enable(save, dirty() && !saving && !stale)

    /** While a save runs nothing can be typed (a later edit would be lost when the save closes the page) and Preview waits too. */
    private fun setEditing(on: Boolean) {
        if (!on) {
            window?.currentFocus?.clearFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)?.hideSoftInputFromWindow(editScroll.windowToken, 0)
        }
        for (e in edits) e.isEnabled = on
        column.alpha = if (on) 1f else .55f
        segEdit.alpha = if (on) 1f else .55f; segPreview.alpha = if (on) 1f else .55f
    }

    private fun setStatus(text: String?) {
        status.text = text.orEmpty()
        status.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    // ---- save / leave -------------------------------------------------------------------------------------------------

    private fun save() {
        val values = texts()
        if (saving || stale || loaded == null || values == before) return
        saving = true; refreshSave(); setStatus(null); setEditing(false)
        val was = before
        io.execute {
            val r = deckOps(applicationContext).editNote(nid, values, was)
            main.post {
                if (isDestroyed) return@post
                saving = false
                if (r.ok) { setResult(RESULT_OK); finish(); Ui.toast(this, r.message) } // the message waits for the screen behind (AppToast)
                else { stale = r.changed; setStatus(r.message); setEditing(true); refreshSave() }
            }
        }
    }

    private fun leave() {
        if (dirty() && !saving) DeckDialogs.confirm(this, "Discard your changes?", "The text you changed in this note isn't saved.", "Discard", danger = true) { finish() }
        else finish()
    }

    // ---- preview ------------------------------------------------------------------------------------------------------

    private fun showPreview(on: Boolean) {
        if (loaded == null || saving) return
        preview = on; styleSegments()
        editScroll.visibility = if (on) View.GONE else View.VISIBLE
        previewBox.visibility = if (on) View.VISIBLE else View.GONE
        if (on) {
            window?.currentFocus?.clearFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)?.hideSoftInputFromWindow(editScroll.windowToken, 0)
            drawPreview()
        }
    }

    private fun drawPreview() {
        val l = loaded ?: return
        sideChips.removeAllViews()
        val hasTemplates = NotePreview.build(l.templates, l.cloze, l.note.fieldNames, texts(), ord) != null
        if (hasTemplates) {
            for ((i, label) in listOf("Question", "Answer").withIndex()) {
                val on = (i == 1) == answerSide
                sideChips.addView(StudyUi.smallButton(this, label, primary = on) { answerSide = i == 1; drawPreview() }.apply {
                    if (!on) setTextColor(Ui.c(context, R.color.ag_sub1))
                }, StudyUi.lp(-2).also { if (i > 0) it.marginStart = Ui.dp(this, 6) })
            }
        }
        sideChips.visibility = if (hasTemplates) View.VISIBLE else View.GONE
        previewSink?.let { it(previewHtml()); return }
        // PLAN 161 (Preview closed the app; the cause could not be seen without the phone's crash log): nothing here may take the
        // app down. A page that can't be built or shown is logged (the debug report names it) and the preview says so instead.
        try {
            val html = previewHtml()
            val w = webView()
            if (w.parent == null) { previewCard.removeAllViews(); previewCard.addView(w, FrameLayout.LayoutParams(-1, -1)) }
            w.loadDataWithBaseURL(CardHtml.BASE, html, "text/html", "UTF-8", null)
        } catch (t: Throwable) {
            Report.fail("EDIT", "preview", t)
            previewFailed()
        }
    }

    /** PLAN 161: the page could not be shown: the WebView goes (it can't be used again), a short line takes its place. */
    private fun previewFailed() {
        web?.let { (it.parent as? android.view.ViewGroup)?.removeView(it); runCatching { it.destroy() } }
        web = null
        previewCard.removeAllViews()
        previewCard.addView(StudyUi.label(this, PREVIEW_FAILED, 13, R.color.ag_sub1, 800).apply {
            gravity = Gravity.CENTER; setPadding(Ui.dp(context, 18), Ui.dp(context, 18), Ui.dp(context, 18), Ui.dp(context, 18))
        }, FrameLayout.LayoutParams(-1, -1))
    }

    /** Screenshot / unit tests set this to take the preview page's HTML instead of loading it (a renderer has no working WebView). Null in the app. */
    internal var previewSink: ((String) -> Unit)? = null

    /** The card page for the side on show, from the texts in the boxes (not the saved ones). */
    internal fun previewHtml(): String {
        val l = loaded ?: return ""
        val values = texts()
        val sides = NotePreview.build(l.templates, l.cloze, l.note.fieldNames, values, ord)
        val side = if (answerSide && sides != null) "a" else "q"
        val body = when {
            sides == null -> NotePreview.fieldsOnly(l.note.fieldNames, values)
            side == "a" -> sides.answer
            else -> sides.question
        }
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return CardHtml.build(body, l.css, ord, side, null, dark = night, tapShow = false, tapHide = false, enterShows = false) { name ->
            l.note.fieldNames.indexOf(name).let { i -> if (i >= 0) values[i] else null }
        }.html
    }

    private fun webView(): WebView = web ?: WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false; settings.allowContentAccess = false
        setBackgroundColor(Color.TRANSPARENT)
        // The card page calls these (sound buttons, tap to flip, long press menu); in a preview they do nothing.
        addJavascriptInterface(object {
            @JavascriptInterface fun play(name: String) {}
            @JavascriptInterface fun speak(i: Int) {}
            @JavascriptInterface fun flip() {}
            @JavascriptInterface fun unflip() {}
            @JavascriptInterface fun actions() {}
            @JavascriptInterface fun mathjax(s: String) {}
            @JavascriptInterface fun jsError(s: String) {}
        }, "AnkiGo")
        addJavascriptInterface(object { @JavascriptInterface fun call(page: Int, endpoint: String, data: String): String = "" }, "AnkiGoJs")
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest) = true
            // PLAN 161: a page renderer that crashed or was killed takes the whole app with it unless this says it was handled
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                Report.fail("EDIT", "preview page renderer gone (crash=${detail.didCrash()})")
                if (view === web) previewFailed()
                return true
            }
            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                if (req.url.host != "ankigo.media") return WebResourceResponse("text/plain", "UTF-8", "".byteInputStream())
                val name = req.url.path?.trimStart('/') ?: return null
                val stream = (if (name.startsWith("mathjax/") || name == "fonts/Nunito.ttf") runCatching { assets.open(name) }.getOrNull() else Media.open(this@NoteEditActivity, name))
                    ?: return WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), "".byteInputStream())
                return WebResourceResponse(MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.')) ?: "application/octet-stream", null, stream)
            }
        }
    }.also { web = it }

}
