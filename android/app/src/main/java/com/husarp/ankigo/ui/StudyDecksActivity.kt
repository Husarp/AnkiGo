package com.husarp.ankigo.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.Gravity
import android.view.View
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.husarp.ankigo.*
import com.husarp.ankigo.overlay.FloatBattleService
import com.husarp.ankigo.update.Updates
import java.util.concurrent.Executors

/** AnkiDroid's own tree counts already include child decks and daily limits. */
class StudyDecksActivity : AgActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var content: LinearLayout
    private lateinit var appBar: AppBar
    private val prefs by lazy { getSharedPreferences("study", 0) }
    private var decks = emptyList<Anki.Deck>()
    private var expanded = emptySet<Long>()
    private var ankiIcon: Drawable? = null
    private var ankiIconLoaded = false
    private var generation = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Anki.load(this)
        expanded = DeckTree.decode(prefs.getStringSet("expandedDecks", null))
        if (prefs.contains("folded")) prefs.edit().remove("folded").apply() // old by-name folding, replaced by expandedDecks
        setContentView(buildContent())
        GameRepo.load(this) { if (!isDestroyed) render() }
    }
    /** The screen's views (app bar, deck list, bottom bar); split out of onCreate so the design screenshots can build them. */
    internal fun buildContent(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.c(this@StudyDecksActivity, R.color.ag_base)) }
        appBar = Ui.appBar(this, "Study", "AnkiDroid")
        appBar.addView(StudyUi.iconButton(this, R.drawable.ic_sync, "Refresh decks") { refresh() }, StudyUi.lp(Ui.dp(this, 30), Ui.dp(this, 30)))
        appBar.addView(StudyUi.iconButton(this, R.drawable.ic_settings, "Settings") { startActivity(Intent(this, SettingsActivity::class.java)) }, StudyUi.lp(Ui.dp(this, 30), Ui.dp(this, 30)))
        root.addView(appBar)
        content = StudyUi.column(this).apply { setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 12), Ui.dp(context, 12)) }
        val scroll = ScrollView(this).apply { addView(content, android.widget.FrameLayout.LayoutParams(StudyUi.pageWidth(context), -2, Gravity.CENTER_HORIZONTAL)) }
        var down = 0f
        scroll.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN) down = e.y
            if (e.actionMasked == MotionEvent.ACTION_UP && scroll.scrollY == 0 && e.y - down > Ui.dp(this, 80)) refresh()
            false
        }
        root.addView(scroll, StudyUi.lp(-1, 0, 1f))
        root.addView(BottomNav.create(this, BottomNav.Tab.STUDY))
        return root
    }
    override fun onStart() { super.onStart(); BattleScreen.setVisible(this, true) }
    override fun onStop() { BattleScreen.setVisible(this, false); super.onStop() }
    override fun onResume() { super.onResume(); Updates.onResume(this); FloatBattleService.startIfWanted(this); refresh() }
    override fun onPause() { Updates.onPause(this); super.onPause() }
    override fun onDestroy() { generation++; io.shutdown(); super.onDestroy() }
    private fun refresh() {
        val token = ++generation
        content.removeAllViews(); content.addView(StudyUi.label(this, "Loading decks…"))
        io.execute {
            val result = runCatching { if (!Anki.hasPermission(this)) error("Allow AnkiGo to use AnkiDroid first."); Anki.decks(this) }
            runOnUiThread {
                if (isDestroyed || token != generation) return@runOnUiThread
                result.onSuccess { decks = it.sortedBy { d -> d.name }; pruneExpanded(); render() }.onFailure { showAccessProblem(it.message) }
            }
        }
    }
    internal fun showAccessProblem(message: String?) {
        content.removeAllViews(); content.addView(StudyUi.label(this, message ?: "Open AnkiDroid once, then refresh.", 13).apply { setPadding(Ui.dp(context, 4), Ui.dp(context, 8), Ui.dp(context, 4), 0) })
        content.addView(StudyUi.primary(this, "Allow AnkiDroid access") { requestPermissions(arrayOf(Anki.permission), 1) }, StudyUi.spaced(this, 12))
        content.addView(StudyUi.smallButton(this, "Open AnkiDroid") { Anki.launchIntent(this)?.let { startActivity(it) } }, StudyUi.spaced(this, 6))
    }
    /** Forget expanded decks that no longer exist (only after a successful, non-empty read). */
    private fun pruneExpanded() {
        val kept = DeckTree.prune(expanded, decks)
        if (kept != expanded) { expanded = kept; prefs.edit().putStringSet("expandedDecks", DeckTree.encode(kept)).apply() }
    }
    /** AnkiDroid's own launcher icon, read once per screen; null when it is not installed or the icon can't be read. */
    private fun ankiDroidIcon(): Drawable? {
        if (!ankiIconLoaded) { ankiIconLoaded = true; ankiIcon = runCatching { packageManager.getApplicationIcon(Anki.pkg) }.getOrNull() }
        return ankiIcon
    }
    private fun render() {
        if (!::content.isInitialized) return
        content.removeAllViews()
        val installed = Anki.installed(this).firstOrNull { it.pkg == Anki.pkg }
        appBar.setPill(installed?.let { "AnkiDroid ${it.versionName}${if (Anki.studySupported(this)) " ✓" else ""}" } ?: "AnkiDroid needed")
        appBar.pill?.apply {
            setTextColor(Ui.c(this@StudyDecksActivity, if (Anki.studySupported(this@StudyDecksActivity)) R.color.ag_green else R.color.ag_sub0))
        }
        val last = decks.firstOrNull { it.id == prefs.getLong("lastDeck", 0) }
        if (last != null) {
            val resume = StudyUi.row(this).apply {
                background = StudyUi.gradient(context, R.color.ag_mauve, .24f, 16)
                setPadding(Ui.dp(context, 12), Ui.dp(context, 10), Ui.dp(context, 12), Ui.dp(context, 10))
                setOnClickListener { open(last) }; contentDescription = "Continue ${last.name}, ${last.total} due"
            }
            val text = StudyUi.column(this)
            text.addView(StudyUi.label(this, "CONTINUE", 11, R.color.ag_sub0, 900).apply { letterSpacing = .055f })
            text.addView(StudyUi.ellipsis(StudyUi.label(this, last.name.substringAfterLast("::"), 18, R.color.ag_text, 900)))
            val whenStudied = prefs.getLong("lastStudied:${last.id}", 0)
            val ago = ((System.currentTimeMillis() - whenStudied).coerceAtLeast(0) / 60000)
            val recency = when { whenStudied == 0L -> ""; ago == 0L -> " · last studied just now"; ago < 60 -> " · last studied $ago min ago"; ago < 1440 -> " · last studied ${ago / 60} h ago"; else -> " · last studied ${ago / 1440} days ago" }
            text.addView(StudyUi.label(this, "${last.total} due$recency", 12, R.color.ag_sub0), StudyUi.spaced(this, 2))
            resume.addView(text, StudyUi.lp(0, -2, 1f))
            resume.addView(StudyUi.iconButton(this, R.drawable.ic_play, "Continue ${last.name}") { open(last) }.apply {
                setColorFilter(Ui.c(context, R.color.ag_base))
                setPadding(Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12))
                background = Ui.rounded(Ui.c(context, R.color.ag_mauve), 14, context)
            }, StudyUi.lp(Ui.dp(this, 46), Ui.dp(this, 46)).also { it.marginStart = Ui.dp(this, 10) })
            content.addView(resume)
        }
        GameRepo.state?.takeIf { GameRepo.data != null }?.let { st ->
            val teaser = StudyUi.row(this).apply {
                background = Ui.rounded(Ui.c(context, R.color.ag_card), 14, context)
                setPadding(Ui.dp(context, 10), Ui.dp(context, 6), Ui.dp(context, 10), Ui.dp(context, 6))
            }
            val num = st.nextSpecies ?: st.battle?.wild?.species
            teaser.addView(ImageView(this).apply {
                background = Ui.rounded(Ui.c(context, R.color.ag_mantle), 12, context)
                if (num != null) Ui.staticSprite(context, num, false, this, true)
                contentDescription = "Waiting wild Pokémon"
            }, StudyUi.lp(Ui.dp(this, 44), Ui.dp(this, 44)).also { it.marginEnd = Ui.dp(this, 8) })
            val goal = st.settings.dailyAverage.coerceAtLeast(1)
            val text = StudyUi.column(this)
            text.addView(StudyUi.label(this, "A wild Pokémon is waiting · ${(goal - st.reviewsToday).coerceAtLeast(0)} answers left today", 12, R.color.ag_sub1, 800).apply {
                val rich = android.text.SpannableStringBuilder(this.text)
                rich.setSpan(android.text.style.ForegroundColorSpan(Ui.c(context, R.color.ag_text)), 0, "A wild Pokémon is waiting".length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                this.text = rich
            })
            text.addView(BarView(this).apply {
                setGradient(Ui.c(context, R.color.ag_mauve), Ui.c(context, R.color.ag_pink))
                set(st.reviewsToday.toFloat() / goal, Ui.c(context, R.color.ag_mauve), false)
            }, StudyUi.spaced(this, 4).also { it.height = Ui.dp(this, 6) })
            teaser.addView(text, StudyUi.lp(0, -2, 1f))
            content.addView(teaser, StudyUi.spaced(this, 8))
        }
        if (GameRepo.data == null) content.addView(StudyUi.label(this, "Study works without game data. Download battle data in Settings to play.", 12), StudyUi.spaced(this, 8))
        content.addView(StudyUi.section(this, "Decks", "tap = study here · ↗ = in AnkiDroid"))
        val columns = StudyUi.row(this).apply { gravity = Gravity.END; setPadding(0, 0, Ui.dp(context, 50), Ui.dp(context, 3)) }
        listOf("New" to R.color.ag_blue, "Lrn" to R.color.ag_red, "Due" to R.color.ag_green).forEach { (name, tone) ->
            columns.addView(StudyUi.label(this, name, 9.5f, tone, 900).apply { gravity = Gravity.END }, StudyUi.lp(Ui.dp(this, 28), -2))
        }
        content.addView(columns)
        if (decks.isEmpty()) content.addView(StudyUi.label(this, "No decks found. Open AnkiDroid once, then refresh."))
        for (r in DeckTree.rows(decks, expanded)) {
            val deck = r.deck
            val row = StudyUi.row(this).apply {
                background = Ui.rounded(Ui.c(context, R.color.ag_card), 12, context)
                setPadding(0, 0, Ui.dp(context, 2), 0)
                minimumHeight = Ui.dp(context, 52)
                alpha = if (deck.total == 0) .55f else 1f
                setOnClickListener { open(deck) }
            }
            // left part: arrow (parents) or empty space (leaves, so names line up); the whole 48 dp-wide strip toggles, never studies
            val zone = FrameLayout(this)
            if (r.hasChildren) {
                zone.contentDescription = "${if (r.expanded) "Fold" else "Expand"} ${deck.name}"
                zone.setOnClickListener {
                    expanded = DeckTree.toggle(expanded, deck.id)
                    prefs.edit().putStringSet("expandedDecks", DeckTree.encode(expanded)).apply(); render()
                }
                zone.addView(StudyUi.icon(this, if (r.expanded) R.drawable.ic_expand else R.drawable.ic_chevron, R.color.ag_sub1),
                    FrameLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30), Gravity.CENTER))
            }
            row.addView(zone, StudyUi.lp(Ui.dp(this, 48), -1))
            val name = StudyUi.row(this)
            name.addView(StudyUi.ellipsis(StudyUi.label(this, deck.name.substringAfterLast("::"), 14, R.color.ag_text, 800)), StudyUi.lp(0, -2, 1f))
            if (deck.dyn) name.addView(StudyUi.label(this, "FILTERED", 9, R.color.ag_teal, 900).apply {
                background = Ui.rounded((Ui.c(context, R.color.ag_teal) and 0x00ffffff) or 0x29000000, 5, context)
                setPadding(Ui.dp(context, 5), Ui.dp(context, 1), Ui.dp(context, 5), Ui.dp(context, 1))
            }, StudyUi.lp(-2).also { it.marginStart = Ui.dp(this, 5) })
            row.addView(name, StudyUi.lp(0, -2, 1f).also { it.marginEnd = Ui.dp(this, 6); it.topMargin = Ui.dp(this, 8); it.bottomMargin = Ui.dp(this, 8) })
            listOf(deck.new to R.color.ag_blue, deck.learn to R.color.ag_red, deck.review to R.color.ag_green).forEachIndexed { i, (n, color) ->
                row.addView(StudyUi.label(this, n.coerceAtLeast(0).toString(), 13, if (n == 0) R.color.ag_ov else color, if (n == 0) 700 else 900).apply {
                    gravity = Gravity.END; contentDescription = "${listOf("New", "Learn", "Review")[i]} $n"
                }, StudyUi.lp(Ui.dp(this, 28), -2))
            }
            row.addView(companionButton(deck), StudyUi.lp(Ui.dp(this, 48), Ui.dp(this, 48)))
            content.addView(row, StudyUi.lp().also { it.marginStart = Ui.dp(this, r.depth * 16); it.bottomMargin = Ui.dp(this, 5) })
        }
    }
    /** "Study in AnkiDroid": AnkiDroid's own icon (fallback: a neutral book glyph) with a small "opens another app" badge. */
    private fun companionButton(deck: Anki.Deck): View = FrameLayout(this).apply {
        contentDescription = "Study ${deck.name} in AnkiDroid"
        tooltipText = "Study in AnkiDroid"
        background = StudyUi.ripple(context, Ui.rounded(Color.TRANSPARENT, 12, context))
        setOnClickListener { companion(deck) }
        val icon = ankiDroidIcon()
        if (icon != null) {
            addView(ImageView(context).apply {
                setImageDrawable(icon.constantState?.newDrawable(resources)?.mutate() ?: icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, FrameLayout.LayoutParams(Ui.dp(context, 26), Ui.dp(context, 26), Gravity.CENTER))
        } else {
            addView(FrameLayout(context).apply {
                background = Ui.rounded(Ui.c(context, R.color.ag_mantle), 8, context)
                addView(StudyUi.icon(context, R.drawable.ic_study, R.color.ag_sub1), FrameLayout.LayoutParams(Ui.dp(context, 18), Ui.dp(context, 18), Gravity.CENTER))
            }, FrameLayout.LayoutParams(Ui.dp(context, 26), Ui.dp(context, 26), Gravity.CENTER))
        }
        addView(FrameLayout(context).apply {
            background = Ui.rounded(Ui.c(context, R.color.ag_mantle), 8, context)
            addView(StudyUi.icon(context, R.drawable.ic_open, R.color.ag_text), FrameLayout.LayoutParams(Ui.dp(context, 10), Ui.dp(context, 10), Gravity.CENTER))
        }, FrameLayout.LayoutParams(Ui.dp(context, 16), Ui.dp(context, 16), Gravity.TOP or Gravity.START).also { it.leftMargin = Ui.dp(context, 29); it.topMargin = Ui.dp(context, 3) })
    }
    private fun open(deck: Anki.Deck) {
        if (prefs.getBoolean("inAnkiDroid", false)) { companion(deck); return }
        if (deck.total == 0) {
            AlertDialog.Builder(this).setMessage("Nothing due. Custom study is in AnkiDroid.")
                .setPositiveButton("Open in AnkiDroid") { _, _ -> companion(deck) }.setNegativeButton("Back", null).showCard(); return
        }
        startActivity(Intent(this, StudyActivity::class.java).putExtra("deckId", deck.id).putExtra("deckName", deck.name))
    }
    private fun companion(deck: Anki.Deck) {
        io.execute {
            val result = runCatching { Anki.selectDeck(this, deck.id) }
            runOnUiThread { if (!isDestroyed) result.onSuccess { runCatching { if (GameRepo.game == null) startActivity(Anki.reviewIntent(deck.id)) else startActivity(Intent(this, GameActivity::class.java).putExtra("reviewDeck", deck.id).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }.onFailure { Ui.toast(this, "Open AnkiDroid once first") } }.onFailure { Ui.toast(this, "Couldn't select this deck") } }
        }
    }
}
