package com.husarp.ankigo.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.husarp.ankigo.*
import java.util.concurrent.Executors

/**
 * Deck picker for one study widget: shown when the widget is added (APPWIDGET_CONFIGURE) and from the widget's
 * "Change deck" button / long-press Reconfigure. One row per deck with the same New / Lrn / Due counts as the Study tab;
 * the default (most cards due) is marked. Tapping a row saves the deck for this widget id, redraws the widget and finishes.
 */
class StudyWidgetSetupActivity : AgActivity() {
    override val showsUpdateBanner = false
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var content: LinearLayout
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var decks = emptyList<Anki.Deck>()
    private var currentDeck = 0L
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        // Backing out must cancel the add (the launcher then drops the widget when it was required).
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        Anki.load(this)
        currentDeck = StudyWidgetPrefs.deckId(getSharedPreferences("spike", 0), widgetId)
        setContentView(buildContent())
        load()
    }

    override fun onDestroy() { generation++; io.shutdown(); super.onDestroy() }

    /** App bar plus the scrolling list; split out of onCreate so the design screenshots can build it. */
    internal fun buildContent(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.c(this@StudyWidgetSetupActivity, R.color.ag_base)) }
        root.addView(Ui.appBar(this, "Study widget", "Pick a deck", back = true))
        content = StudyUi.column(this).apply { setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 12), Ui.dp(context, 16)) }
        val scroll = ScrollView(this).apply { addView(content, android.widget.FrameLayout.LayoutParams(StudyUi.pageWidth(context), -2, Gravity.CENTER_HORIZONTAL)) }
        root.addView(scroll, StudyUi.lp(-1, 0, 1f))
        return root
    }

    private fun load() {
        content.removeAllViews(); content.addView(StudyUi.label(this, "Loading decks…"))
        val token = ++generation
        io.execute {
            val result = runCatching { if (!Anki.hasPermission(this)) error("no permission"); Anki.decks(this) }
            runOnUiThread {
                if (isDestroyed || token != generation) return@runOnUiThread
                result.onSuccess { decks = it.sortedBy { d -> d.name }; render() }.onFailure { showNoAccess() }
            }
        }
    }

    internal fun showNoAccess() {
        content.removeAllViews()
        content.addView(StudyUi.label(this, "AnkiGo can't read your AnkiDroid decks yet. Open AnkiGo and allow AnkiDroid access, then pick a deck here (long-press the widget, Reconfigure).", 13)
            .apply { setPadding(Ui.dp(context, 4), Ui.dp(context, 8), Ui.dp(context, 4), 0) })
        content.addView(StudyUi.primary(this, "Open AnkiGo") { startActivity(Intent(this, StudyDecksActivity::class.java)); finish() }, StudyUi.spaced(this, 12))
    }

    internal fun render() {
        content.removeAllViews()
        val default = StudyWidgetPrefs.mostDue(decks)
        content.addView(StudyUi.label(this, "Which deck should this widget study? You can change it later with the swap button on the widget.", 13)
            .apply { setPadding(Ui.dp(context, 4), Ui.dp(context, 4), Ui.dp(context, 4), 0) })
        content.addView(StudyUi.section(this, "Decks", if (default != null) "default = most cards due" else null))
        val columns = StudyUi.row(this).apply { gravity = Gravity.END; setPadding(0, 0, Ui.dp(context, 8), Ui.dp(context, 3)) }
        val countWidth = DeckCounts.columnWidth(this, decks)
        DeckCounts.addHeader(columns, this, countWidth)
        content.addView(columns)
        if (decks.isEmpty()) content.addView(StudyUi.label(this, "No decks found. Open AnkiDroid once, then try again."))
        for (deck in decks) {
            val isCurrent = deck.id == currentDeck
            val row = StudyUi.row(this).apply {
                minimumHeight = Ui.dp(context, 48)
                background = if (isCurrent) StudyUi.gradient(context, R.color.ag_mauve, .24f, 12) else Ui.rounded(Ui.c(context, R.color.ag_card), 12, context)
                setPadding(Ui.dp(context, 12), Ui.dp(context, 8), Ui.dp(context, 8), Ui.dp(context, 8))
                alpha = if (deck.total == 0) .55f else 1f
                isClickable = true; isFocusable = true
                contentDescription = "${deck.name}, ${deck.total} due${if (deck.id == default?.id) ", default" else ""}"
                setOnClickListener { choose(deck) }
            }
            val text = StudyUi.column(this)
            val titleRow = StudyUi.row(this)
            titleRow.addView(StudyUi.ellipsis(StudyUi.label(this, deck.name.substringAfterLast("::"), 14, R.color.ag_text, 800)), StudyUi.lp(-2, -2, 0f))
            if (deck.id == default?.id) titleRow.addView(tag("DEFAULT", R.color.ag_mauve), StudyUi.lp(-2).also { it.marginStart = Ui.dp(this, 6) })
            if (isCurrent) titleRow.addView(tag("SELECTED", R.color.ag_green), StudyUi.lp(-2).also { it.marginStart = Ui.dp(this, 6) })
            if (deck.dyn) titleRow.addView(tag("FILTERED", R.color.ag_teal), StudyUi.lp(-2).also { it.marginStart = Ui.dp(this, 6) })
            text.addView(titleRow)
            val parent = deck.name.substringBeforeLast("::", "")
            if (parent.isNotEmpty()) text.addView(StudyUi.ellipsis(StudyUi.label(this, parent.replace("::", " › "), 11, R.color.ag_sub0, 700)), StudyUi.spaced(this, 1))
            row.addView(text, StudyUi.lp(0, -2, 1f).also { it.marginEnd = Ui.dp(this, 6) })
            DeckCounts.addTo(row, this, deck, countWidth)
            content.addView(row, StudyUi.lp().also { it.bottomMargin = Ui.dp(this, 5) })
        }
    }

    private fun tag(text: String, tone: Int) = StudyUi.label(this, text, 9, tone, 900).apply {
        background = Ui.rounded((Ui.c(context, tone) and 0x00ffffff) or 0x29000000, 5, context)
        setPadding(Ui.dp(context, 5), Ui.dp(context, 1), Ui.dp(context, 5), Ui.dp(context, 1))
    }

    private fun choose(deck: Anki.Deck) {
        StudyWidgetPrefs.setDeck(getSharedPreferences("spike", 0), widgetId, deck.id, deck.name)
        StudyWidget.requestUpdate(this, widgetId)
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
