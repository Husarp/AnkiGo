package com.husarp.ankigo.ui

import android.app.Activity
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.husarp.ankigo.Anki
import com.husarp.ankigo.R
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.deck.DeckFlows
import java.util.concurrent.Executors

/**
 * The deck list of the home screen's Study view (moved 1:1 from the old Study tab, StudyDecksActivity): app bar buttons
 * ([buildAppBar]), the CONTINUE card and the deck rows with pull-to-refresh ([buildBody]), the permission / access problem
 * view and the deck menus. AnkiDroid's own tree counts already include child decks and daily limits.
 * The [host] (the home screen) owns what leaves the list: "Study in AnkiDroid" and the permission request.
 * Life cycle: [refresh] when the view shows (and on every resume), [resumeFlows] once at creation, [onDestroy] at the end.
 */
internal class StudyHome(private val act: Activity, private val host: Host) {
    interface Host {
        /** "Study in AnkiDroid": AnkiDroid has selected [deckId]; start its reviewer (and track the way back). */
        fun studyInAnkiDroid(deckId: Long)
        /** The "Allow AnkiDroid access" button was tapped. */
        fun askAnkiAccess()
    }

    private val io = Executors.newSingleThreadExecutor()
    private lateinit var content: LinearLayout
    /** The CONTINUE card's place above the scrolling list: it stays on top while the decks scroll. */
    private lateinit var resumeSlot: LinearLayout
    private lateinit var appBar: AppBar
    private lateinit var pull: PullRefreshLayout
    private var deckScroll: ScrollView? = null
    private var newDeckButton: View? = null
    private val prefs by lazy { act.getSharedPreferences("study", 0) }
    private var decks = emptyList<Anki.Deck>()
    private var expanded = DeckTree.decode(prefs.getStringSet("expandedDecks", null))
    private var generation = 0

    /** Off in screenshots: they fill [decks] themselves and a background read must not replace it. */
    internal var loadsInBackground = true

    init {
        if (prefs.contains("folded")) prefs.edit().remove("folded").apply() // old by-name folding, replaced by expandedDecks
    }

    /** A "Move all cards" that was running when the screen turned or closed: its progress card comes back. */
    fun resumeFlows() = DeckFlows.resume(act) { refresh() }

    /** The Study app bar: title and New deck (the AnkiDroid version is in Settings, PLAN 202). */
    fun buildAppBar(): AppBar {
        appBar = Ui.appBar(act, "Study")
        // "New deck" (PLAN 70): shown once AnkiDroid is known to take changes (see render)
        newDeckButton = StudyUi.iconButton(act, R.drawable.ic_plus, "New deck") { DeckFlows.newDeck(act, null) { refresh() } }.apply {
            setColorFilter(Ui.c(context, R.color.ag_mauve))
            setPadding(Ui.dp(context, 11), Ui.dp(context, 11), Ui.dp(context, 11), Ui.dp(context, 11)); visibility = View.GONE
        }.also {
            // last in the bar; a 46 dp touch area that keeps the bar's height, pulled right so the glyph lines up with the bar's edge
            appBar.addView(it, LinearLayout.LayoutParams(Ui.dp(act, 46), Ui.dp(act, 46)).also { lp -> lp.setMargins(Ui.dp(act, 4), -Ui.dp(act, 9), -Ui.dp(act, 11), -Ui.dp(act, 9)) })
        }
        return appBar
    }

    /** The sticky CONTINUE slot above the scrolling deck list with pull-to-refresh (both are filled by [render] / [refresh]). */
    fun buildBody(): View {
        content = StudyUi.column(act).apply { setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 12), Ui.dp(context, 12)) }
        val scroll = ScrollView(act).apply { addView(content, FrameLayout.LayoutParams(StudyUi.pageWidth(context), -2, Gravity.CENTER_HORIZONTAL)) }
        deckScroll = scroll
        pull = PullRefreshLayout(act, scroll).apply { onRefresh = { refresh(true) } }
        resumeSlot = StudyUi.column(act).apply { setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 12), Ui.dp(context, 4)); visibility = View.GONE }
        return StudyUi.column(act).apply {
            addView(resumeSlot, LinearLayout.LayoutParams(StudyUi.pageWidth(context), -2).apply { gravity = Gravity.CENTER_HORIZONTAL })
            addView(pull, StudyUi.lp(-1, 0, 1f))
        }
    }

    /** PLAN 218: the Study tab was tapped while it is shown: the deck list goes back to its top. */
    fun scrollToTop() { deckScroll?.scrollTo(0, 0) }

    fun onDestroy() { generation++; DeckFlows.detach(); io.shutdown() }

    /** [byUser]: pulled down: the spinner shows. Otherwise (screen came back) a shown list stays until the new one is there. */
    fun refresh(byUser: Boolean = false) {
        if (!::content.isInitialized || act.isDestroyed || !loadsInBackground) return
        val token = ++generation
        if (byUser) pull.setRefreshing(true)
        if (decks.isEmpty()) { content.removeAllViews(); content.addView(StudyUi.label(act, "Loading decks…")) }
        io.execute {
            val result = runCatching { if (!Anki.hasPermission(act)) error("Allow AnkiGo to use AnkiDroid first."); Anki.decks(act) }
            act.runOnUiThread {
                if (act.isDestroyed || token != generation) return@runOnUiThread
                pull.setRefreshing(false)
                result.onSuccess { decks = it.sortedBy { d -> d.name }; pruneExpanded(); render() }.onFailure { showAccessProblem(it.message) }
            }
        }
    }

    fun showAccessProblem(message: String?) {
        newDeckButton?.visibility = View.GONE
        resumeSlot.removeAllViews(); resumeSlot.visibility = View.GONE
        content.removeAllViews()
        // PLAN 202: a known problem (not installed, too old, no permission) is one warning card; anything else keeps the old message + buttons
        val problem = StudyStatus.warning(act)
        if (problem != null) { content.addView(warningCard(problem), StudyUi.lp().also { it.topMargin = Ui.dp(act, 4) }); return }
        content.addView(StudyUi.label(act, message ?: "Open AnkiDroid once, then refresh.", 13).apply { setPadding(Ui.dp(context, 4), Ui.dp(context, 8), Ui.dp(context, 4), 0) })
        content.addView(StudyUi.primary(act, "Allow AnkiDroid access") { host.askAnkiAccess() }, StudyUi.spaced(act, 12))
        content.addView(StudyUi.smallButton(act, "Open AnkiDroid") { Anki.launchIntent(act)?.let { act.startActivity(it) } }, StudyUi.spaced(act, 6))
    }

    /** The warning card at the top of the list: one short line and the one button that helps. */
    internal fun warningCard(p: StudyStatus.Problem): View {
        val card = StudyUi.column(act).apply {
            background = StudyUi.gradient(context, R.color.ag_yellow, .16f, 16)
            setPadding(Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 12))
        }
        card.addView(StudyUi.label(act, p.message, 13, R.color.ag_text, 700))
        card.addView(StudyUi.primary(act, p.button) { onWarningAction(p.action) }, StudyUi.spaced(act, 10))
        return card
    }

    private fun onWarningAction(a: StudyStatus.Action) = when (a) {
        StudyStatus.Action.ALLOW_ACCESS -> host.askAnkiAccess()
        StudyStatus.Action.OPEN_ANKIDROID -> { Anki.launchIntent(act)?.let { act.startActivity(it) }; Unit }
        StudyStatus.Action.GET_ANKIDROID -> {
            val id = Anki.pkg
            try { act.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$id"))) }
            catch (_: Exception) { runCatching { act.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=$id"))) } }
        }
    }

    /** Forget expanded decks that no longer exist (only after a successful, non-empty read). */
    private fun pruneExpanded() {
        val kept = DeckTree.prune(expanded, decks)
        if (kept != expanded) { expanded = kept; prefs.edit().putStringSet("expandedDecks", DeckTree.encode(kept)).apply() }
    }

    fun render() {
        if (!::content.isInitialized) return
        content.removeAllViews(); resumeSlot.removeAllViews(); resumeSlot.visibility = View.GONE
        StudyStatus.warning(act)?.let { content.addView(warningCard(it), StudyUi.lp().also { lp -> lp.topMargin = Ui.dp(act, 4); lp.bottomMargin = Ui.dp(act, 8) }) }
        newDeckButton?.visibility = if (canManage()) View.VISIBLE else View.GONE
        val last = decks.firstOrNull { it.id == prefs.getLong("lastDeck", 0) }
        if (last != null) {
            val resume = StudyUi.row(act).apply {
                background = StudyUi.gradient(context, R.color.ag_mauve, .24f, 16)
                setPadding(Ui.dp(context, 12), Ui.dp(context, 10), Ui.dp(context, 12), Ui.dp(context, 10))
                setOnClickListener { open(last) }; contentDescription = "Continue ${last.name}, ${last.total} due"
            }
            val text = StudyUi.column(act)
            text.addView(StudyUi.label(act, "CONTINUE", 11, R.color.ag_sub0, 900).apply { letterSpacing = .055f })
            text.addView(StudyUi.ellipsis(StudyUi.label(act, last.name.substringAfterLast("::"), 18, R.color.ag_text, 900)))
            val whenStudied = prefs.getLong("lastStudied:${last.id}", 0)
            val ago = ((System.currentTimeMillis() - whenStudied).coerceAtLeast(0) / 60000)
            val recency = when { whenStudied == 0L -> ""; ago == 0L -> " · last studied just now"; ago < 60 -> " · last studied $ago min ago"; ago < 1440 -> " · last studied ${ago / 60} h ago"; else -> " · last studied ${ago / 1440} days ago" }
            text.addView(StudyUi.label(act, "${last.total} due$recency", 12, R.color.ag_sub0), StudyUi.spaced(act, 2))
            resume.addView(text, StudyUi.lp(0, -2, 1f))
            resume.addView(StudyUi.iconButton(act, R.drawable.ic_play, "Continue ${last.name}") { open(last) }.apply {
                setColorFilter(Ui.c(context, R.color.ag_base))
                setPadding(Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12))
                background = Ui.rounded(Ui.c(context, R.color.ag_mauve), 14, context)
            }, StudyUi.lp(Ui.dp(act, 46), Ui.dp(act, 46)).also { it.marginStart = Ui.dp(act, 10) })
            resumeSlot.addView(resume); resumeSlot.visibility = View.VISIBLE
        }
        content.addView(StudyUi.section(act, "Decks"))
        val columns = StudyUi.row(act).apply { gravity = Gravity.END; setPadding(0, 0, Ui.dp(context, DeckCounts.END_PAD_DP), Ui.dp(context, 3)) }
        val countWidth = DeckCounts.columnWidth(act, decks)
        DeckCounts.addHeader(columns, act, countWidth)
        content.addView(columns)
        if (decks.isEmpty()) content.addView(StudyUi.label(act, "No decks found. Open AnkiDroid once, then refresh."))
        for (r in DeckTree.rows(decks, expanded)) {
            val deck = r.deck
            val row = StudyUi.row(act).apply {
                background = Ui.rounded(Ui.c(context, R.color.ag_card), 12, context)
                setPadding(0, 0, Ui.dp(context, DeckCounts.END_PAD_DP), 0)
                minimumHeight = Ui.dp(context, 52)
                alpha = if (deck.total == 0) .55f else 1f
                setOnClickListener { open(deck) }
                setOnLongClickListener { deckMenu(deck, this); true }
            }
            // left part: arrow (parents) or empty space (leaves, so names line up); the whole 48 dp-wide strip toggles, never studies
            val zone = FrameLayout(act)
            if (r.hasChildren) {
                zone.contentDescription = "${if (r.expanded) "Fold" else "Expand"} ${deck.name}"
                zone.setOnClickListener {
                    expanded = DeckTree.toggle(expanded, deck.id)
                    prefs.edit().putStringSet("expandedDecks", DeckTree.encode(expanded)).apply(); render()
                }
                zone.addView(StudyUi.icon(act, if (r.expanded) R.drawable.ic_expand else R.drawable.ic_chevron, R.color.ag_sub1),
                    FrameLayout.LayoutParams(Ui.dp(act, 30), Ui.dp(act, 30), Gravity.CENTER))
            }
            row.addView(zone, StudyUi.lp(Ui.dp(act, 48), -1))
            val name = StudyUi.row(act)
            name.addView(StudyUi.ellipsis(StudyUi.label(act, deck.name.substringAfterLast("::"), 14, R.color.ag_text, 800)), StudyUi.lp(0, -2, 1f))
            if (deck.dyn) name.addView(StudyUi.label(act, "FILTERED", 9, R.color.ag_teal, 900).apply {
                background = Ui.rounded((Ui.c(context, R.color.ag_teal) and 0x00ffffff) or 0x29000000, 5, context)
                setPadding(Ui.dp(context, 5), Ui.dp(context, 1), Ui.dp(context, 5), Ui.dp(context, 1))
            }, StudyUi.lp(-2).also { it.marginStart = Ui.dp(act, 5) })
            row.addView(name, StudyUi.lp(0, -2, 1f).also { it.marginEnd = Ui.dp(act, 6); it.topMargin = Ui.dp(act, 8); it.bottomMargin = Ui.dp(act, 8) })
            DeckCounts.addTo(row, act, deck, countWidth)
            content.addView(row, StudyUi.lp().also { it.marginStart = Ui.dp(act, r.depth * 16); it.bottomMargin = Ui.dp(act, 5) })
        }
    }

    /** AnkiDroid takes changes (permission given, version new enough): New deck, New subdeck and Move all cards are offered. */
    private fun canManage() = Anki.hasPermission(act) && Anki.studySupported(act)

    /**
     * Long-press on a deck (PLAN 70): the usual AnkiGo popup menu. New subdeck and Move all cards are only offered where AnkiDroid
     * can do them (not in a filtered deck, which can hold no subdeck and no cards of its own). Renaming or deleting a deck is not
     * possible through AnkiDroid's API, so those are simply not offered.
     */
    fun deckMenu(deck: Anki.Deck, anchor: View): AppMenu {
        val menu = AppMenu(anchor)
        if (canManage() && !deck.dyn) {
            menu.item(MENU_SUBDECK, "New subdeck…", R.drawable.ic_plus)
            menu.item(MENU_MOVE_ALL, "Move all cards…", R.drawable.ic_move)
        }
        menu.item(MENU_ANKI, "Study in AnkiDroid", R.drawable.ic_open)
        menu.show { id ->
            when (id) {
                MENU_SUBDECK -> DeckFlows.newDeck(act, deck) {
                    expanded = expanded + deck.id
                    prefs.edit().putStringSet("expandedDecks", DeckTree.encode(expanded)).apply(); refresh()
                }
                MENU_MOVE_ALL -> DeckFlows.moveAll(act, deck) { refresh() }
                MENU_ANKI -> companion(deck)
            }
        }
        return menu
    }

    private fun open(deck: Anki.Deck) {
        if (prefs.getBoolean("inAnkiDroid", false)) { companion(deck); return }
        if (deck.total == 0) {
            AppDialog.Builder(act).setMessage("Nothing due. Custom study is in AnkiDroid.")
                .setPositiveButton("Open in AnkiDroid") { _, _ -> companion(deck) }.setNegativeButton("Back", null).showCard(); return
        }
        act.startActivity(Intent(act, StudyActivity::class.java).putExtra("deckId", deck.id).putExtra("deckName", deck.name))
    }

    private fun companion(deck: Anki.Deck) {
        io.execute {
            val result = runCatching { Anki.selectDeck(act, deck.id) }
            act.runOnUiThread { if (!act.isDestroyed) result.onSuccess { host.studyInAnkiDroid(deck.id) }.onFailure { Ui.toast(act, "Couldn't select this deck") } }
        }
    }

    private companion object {
        const val MENU_SUBDECK = 1
        const val MENU_MOVE_ALL = 2
        const val MENU_ANKI = 3
    }
}
