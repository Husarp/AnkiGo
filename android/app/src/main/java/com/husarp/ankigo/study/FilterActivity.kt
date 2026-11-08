package com.husarp.ankigo.study

import com.husarp.ankigo.ui.MotionScrollView

import android.app.Dialog
import android.database.ContentObserver
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import com.husarp.ankigo.Anki
import com.husarp.ankigo.R
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.ui.*
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** PLAN 153: one page; every picker searches its own existing values. Provider reads stay off the UI thread. */
class FilterActivity : AgActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("study", 0) }
    private var filter = StudyFilter()
    private var library = FilterLibrary()
    private var decks = emptyList<Anki.Deck>()
    private val tagWorker = Executors.newSingleThreadExecutor()
    private val tagCache = StudyTags()
    private val tagReads = mutableMapOf<String, MutableList<(Result<List<String>>) -> Unit>>()
    private var activeSheet: Dialog? = null
    private val tagErrors = mutableSetOf<String>()
    private var observed = false
    private val collectionObserver = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) { invalidateTags() }
    }
    private fun invalidateTags() {
        tagCache.invalidate()
        tagErrors.clear()
        activeSheet?.dismiss()
        render()
    }
    override fun onResume() {
        super.onResume()
        if (observed) invalidateTags()
    }

    private lateinit var content: LinearLayout
    private lateinit var count: TextView
    private lateinit var start: TextView
    private var generation = 0
    private var busy = false
    private var label = "Custom study"
    private var savedExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Anki.load(this)
        contentResolver.registerContentObserver(Anki.uri(""), true, collectionObserver)
        observed = true
        library = FilterLibrary.decode(prefs.getString("filterLibrary", null))
        savedInstanceState?.getString("filter")?.let { runCatching { filter = StudyFilter.decode(it) } }
        label = savedInstanceState?.getString("label") ?: label
        setContentView(buildContent())
        worker.execute {
            val result = runCatching {
                check(Anki.studySupported(this)) { "Choose what to study needs AnkiDroid 2.24 or newer." }
                Anki.decks(this)
            }
            main.post { if (!isDestroyed) result.onSuccess { decks = it; render() }.onFailure { count.text = it.message } }
        }
    }
    internal fun buildContent(): View {
        val root = StudyUi.column(this)
        root.addView(Ui.appBar(this, "Choose what to study", back = true, onBack = { finish() }).apply {
            addAction(R.drawable.ic_info, "About this screen") { showInfo() }
        })
        content = StudyUi.column(this).apply { setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0) }
        root.addView(MotionScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        count = StudyUi.label(this, "Loading decks…", 14).apply { gravity = Gravity.CENTER; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        start = StudyUi.primary(this, "Start") { launch() }.apply { isEnabled = false; alpha = .45f }
        root.addView(StudyUi.line(this))
        root.addView(StudyUi.column(this).apply {
            setPadding(Ui.dp(context, 16), Ui.dp(context, 12), Ui.dp(context, 16), Ui.dp(context, 12))
            addView(count); addView(start.apply { minimumHeight = Ui.dp(context, 48) })
        })
        return root
    }
    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); out.putString("filter", filter.encode()); out.putString("label", label) }
    override fun onDestroy() {
        generation++; activeSheet?.dismiss()
        if (observed) contentResolver.unregisterContentObserver(collectionObserver)
        worker.shutdownNow(); tagWorker.shutdownNow(); main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
    private fun persist() { prefs.edit().putString("filterLibrary", library.encode()).apply() }
    private fun heading(s: String, icon: Int = R.drawable.ic_book) {
        content.addView(StudyUi.section(this, s).apply { (getChildAt(0) as ImageView).setImageResource(icon) }, StudyUi.spaced(this, 16))
    }
    private fun row(s: String, action: () -> Unit) {
        content.addView(StudyUi.smallButton(this, s, action = action).apply {
            background = StudyUi.ripple(context, Ui.rounded(Ui.c(context, R.color.ag_card), 12, context))
            textSize = 14f; gravity = Gravity.CENTER_VERTICAL or Gravity.START; minHeight = Ui.dp(context, 48)
            val subtitle = s.indexOf('\n')
            if (subtitle >= 0) text = android.text.SpannableString(s).apply {
                setSpan(android.text.style.ForegroundColorSpan(Ui.c(context, R.color.ag_sub0)), subtitle + 1, length, 0)
                setSpan(android.text.style.RelativeSizeSpan(.86f), subtitle + 1, length, 0)
            }
        }, StudyUi.spaced(this, 4))
    }
    private fun savedLabel(saved: SavedFilter): String = saved.name + (if (saved.filter.newOnly) " · new cards" else "") + "\n" + saved.filter.decks.joinToString(" · ") { d ->
        val selectedTags = d.tags + if (d.ignoreGlobal) emptySet() else saved.filter.tags
        d.name + (if (selectedTags.isEmpty()) "" else " [${selectedTags.sorted().joinToString()}]") + if (saved.filter.newOnly) " · new cards" else if (d.free) " · free study" else " · due cards"
    }
    private fun load(saved: SavedFilter) { filter = saved.filter; label = saved.name; render() }
    private fun render(updateCount: Boolean = true) {
        if (busy) return
        content.removeAllViews()
        content.addView(StudyUi.label(this, "Choose a starting point, then adjust your cards.", 14))
        heading("1 · Starting point", R.drawable.ic_bolt)
        row("Most cards waiting\nDue cards in your busiest deck branch.") {
            // Parent counts already include children: choose the busiest top-level branch to avoid duplicate counts.
            val best = decks.filter { !it.name.contains("::") }.maxByOrNull { it.total }
            filter = StudyFilter(best?.let { listOf(DeckFilter(it.id, it.name)) }.orEmpty()); label = "Most cards waiting"; render()
        }
        row("All decks · due cards\nDue cards across every deck branch.") { filter = StudyFilter(decks.filter { !it.name.contains("::") }.map { DeckFilter(it.id, it.name) }); label = "All decks · due cards"; render() }
        row("New cards\nUnstudied cards across every deck.") {
            filter = StudyFilter(decks.filter { !it.name.contains("::") }.map { DeckFilter(it.id, it.name) }, newOnly = true)
            label = "New cards"; render()
        }
        heading("2 · Decks & tags")
        content.addView(StudyUi.label(this, "Each ready-made setup replaces the selection below."))
        if (filter.newOnly) content.addView(StudyUi.label(this, "New cards only", 14, R.color.ag_mauve, 900))
        val chips = FlowRow(this, Ui.dp(this, 6), centered = false)
        val chipInk = AppDialogRules.onFill(Ui.c(this, R.color.ag_mauve), Ui.c(this, R.color.ag_crust), TypeInk::contrast)
        filter.decks.forEach { d ->
            val chip = StudyUi.row(this).apply {
                background = Ui.rounded(Ui.c(context, R.color.ag_mauve), 12, context)
                setPadding(Ui.dp(context, 12), 0, 0, 0)
            }
            chip.addView(StudyUi.label(this, d.name, 14, R.color.ag_text, 900).apply {
                setTextColor(chipInk); maxWidth = maxOf(Ui.dp(context, 80), resources.displayMetrics.widthPixels - Ui.dp(context, 104))
                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            }, StudyUi.lp(-2, -2))
            chip.addView(StudyUi.iconButton(this, R.drawable.ic_close, "Remove ${d.name}") {
                filter = filter.copy(decks = filter.decks.filterNot { it.id == d.id }); render()
            }.apply { setColorFilter(chipInk) }, StudyUi.lp(Ui.dp(this, 48), Ui.dp(this, 48)))
            chips.addView(chip)
        }
        content.addView(chips, StudyUi.spaced(this, 8))
        row("+ Add decks · ${filter.decks.size} selected\nOpen the deck selection.") {
            picker("Decks", decks.map { it.id.toString() to it.name }, filter.decks.map { it.id.toString() }.toSet()) { selected ->
                filter = filter.copy(decks = decks.filter { it.id.toString() in selected }.map { d -> filter.decks.find { it.id == d.id } ?: DeckFilter(d.id, d.name) })
                render()
            }
        }
        row("Global tags · ${filter.tags.sorted().joinToString().ifEmpty { "Any" }}\nApplies to every deck unless ignored.") {
            tagsSheet("Global tags", filter.decks, filter.tags) { filter = filter.copy(tags = it); render() }
        }
        filter.decks.forEach { d ->
            val card = StudyUi.column(this).apply {
                background = Ui.rounded(Ui.c(context, R.color.ag_card), 14, context)
                setPadding(Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 12))
            }
            card.addView(StudyUi.label(this, d.name, 14, R.color.ag_text, 900))
            fun change(new: DeckFilter) { filter = filter.copy(decks = filter.decks.map { if (it.id == d.id) new else it }); render() }
            val tags = tagCache.get(d.name)
            val tagsText = when {
                d.name in tagErrors -> "Couldn’t load tags · Retry"
                tags == null -> "Tags · Loading tags…"
                tags.isEmpty() -> "This deck has no tags"
                else -> "Tags · ${d.tags.sorted().joinToString().ifEmpty { "Any" }}"
            }
            card.addView(StudyUi.secondary(this, tagsText) {
                tagsSheet("${d.name} tags", listOf(d), d.tags) { change(d.copy(tags = it)) }
            }.apply { gravity = Gravity.CENTER_VERTICAL or Gravity.START }, StudyUi.spaced(this, 6))
            if (!tags.isNullOrEmpty()) card.addView(StudyUi.label(this, "Only tags found in ${d.name}", 12))
            if (tags == null && d.name !in tagReads && d.name !in tagErrors) requestTags(d) {}
            card.addView(CheckBox(this).apply { text = "Ignore global tags"; minimumHeight = Ui.dp(context, 48); isChecked = d.ignoreGlobal; setOnCheckedChangeListener { _, on -> change(d.copy(ignoreGlobal = on)) } })
            card.addView(CheckBox(this).apply { text = "Free study · all matching cards, including cards not due"; minimumHeight = Ui.dp(context, 48); isChecked = d.free; setOnCheckedChangeListener { _, on -> change(d.copy(free = on)) } })
            content.addView(card, StudyUi.spaced(this, 8))
        }
        heading("3 · Keep this setup", R.drawable.ic_bookmark)
        row("☆ Star this filter\nSave a frozen copy.") {
            val name = EditText(this).apply { setText(label); hint = "Filter name" }
            AppDialog.Builder(this).setTitle("Star a frozen copy").setView(name).setPositiveButton("Star") { _, _ ->
                label = name.text.toString().trim().ifEmpty { "Custom study" }; library = library.star(label, filter); persist(); render()
            }.setNegativeButton("Cancel", null).showCard()
        }
        row("${if (savedExpanded) "▾" else "▸"} Starred & Recent") { savedExpanded = !savedExpanded; render(false) }
        if (savedExpanded) {
            if (library.starred.isEmpty() && library.recent.isEmpty()) content.addView(StudyUi.label(this, "No starred setups yet. Your last 10 used setups appear here."))
            if (library.starred.isNotEmpty()) heading("Starred · frozen")
            library.starred.forEachIndexed { i, saved ->
                row("★ ${savedLabel(saved)}") { load(saved) }
                // Explicit removal never modifies the stored setup.
                row("Unstar ${saved.name}") { library = library.unstar(i); persist(); render() }
            }
            if (library.recent.isNotEmpty()) heading("Recent")
            library.recent.forEach { saved -> row(savedLabel(saved)) { load(saved) } }
        }
        if (updateCount) refreshCount()
    }
    /** Main-thread request coalescing. Tags run separately from card counting and are read in one query per branch. */
    private fun requestTags(d: DeckFilter, done: (Result<List<String>>) -> Unit) {
        tagCache.get(d.name)?.let { done(Result.success(it)); return }
        tagReads[d.name]?.let { it.add(done); return }
        val revision = tagCache.revision
        val listeners = mutableListOf(done)
        tagReads[d.name] = listeners
        tagWorker.execute {
            val result = runCatching { Anki.deckTags(this, d.name) }
            main.post {
                if (isDestroyed) return@post
                if (tagReads[d.name] === listeners) tagReads.remove(d.name)
                if (revision != tagCache.revision) {
                    listeners.forEach { requestTags(d, it) }
                    return@post
                }
                result.onSuccess { tagErrors.remove(d.name); tagCache.put(d.name, it, revision) }
                    .onFailure { tagErrors.add(d.name) }
                render(false)
                listeners.toList().forEach { it(result) }
            }
        }
    }
    private fun openSheet(title: String): Pair<Dialog, FilterSheet>? {
        if (activeSheet?.isShowing == true) return null
        val dialog = Dialog(this).apply { requestWindowFeature(android.view.Window.FEATURE_NO_TITLE) }
        val sheet = FilterSheet(this, title) { dialog.dismiss() }
        dialog.setContentView(SheetKit.dialog(dialog, sheet.view))
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(.48f); setGravity(Gravity.BOTTOM)
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        activeSheet = dialog
        dialog.setOnDismissListener { if (activeSheet === dialog) activeSheet = null }
        dialog.show()
        dialog.window?.setLayout(minOf(resources.displayMetrics.widthPixels, Ui.dp(this, 600)), -2)
        return dialog to sheet
    }
    private fun showInfo() {
        val (dialog, sheet) = openSheet("Choose your cards") ?: return
        sheet.info { dialog.dismiss() }
    }
    private fun tagsSheet(title: String, ds: List<DeckFilter>, initial: Set<String>, done: (Set<String>) -> Unit) {
        val (dialog, sheet) = openSheet(title) ?: return
        fun load() {
            sheet.loading { dialog.dismiss() }
            val results = mutableListOf<String>()
            val targets = ds.distinctBy { it.name }
            var remaining = targets.size
            var failed = false
            fun choices() {
                sheet.choices(results.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER).map { it to it }, initial,
                    if (targets.size == 1) "This deck has no tags" else "These decks have no tags",
                    if (targets.size == 1) "Only tags found in ${targets.first().name}. Choose any matching tag." else "Tags in your selected decks. Choose any matching tag.",
                    { dialog.dismiss() }, apply = done)
            }
            if (targets.isEmpty()) choices()
            targets.forEach { d -> requestTags(d) { result ->
                if (dialog.isShowing && !failed) {
                    result.onSuccess { results.addAll(it); remaining--; if (remaining == 0) choices() }
                        .onFailure { failed = true; sheet.error({ dialog.dismiss() }) { load() } }
                }
            } }
        }
        load()
    }
    private fun picker(title: String, values: List<Pair<String, String>>, initial: Set<String>, done: (Set<String>) -> Unit) {
        val (dialog, sheet) = openSheet(title) ?: return
        sheet.choices(values, initial, "No decks available", "Choose the decks to study.", { dialog.dismiss() }, tags = false, apply = done)
    }
    private fun enableStart(enabled: Boolean) { start.isEnabled = enabled; start.alpha = if (enabled) 1f else .45f }
    private fun refreshCount() {
        val token = ++generation; val query = filter.search()
        enableStart(false); count.text = "Counting…"
        main.postDelayed({
            if (isDestroyed || token != generation || busy) return@postDelayed
            worker.execute {
                val result = runCatching { Anki.countCards(this, query) }
                main.post { if (!isDestroyed && token == generation && !busy) result.onSuccess { n -> count.text = if (n == 0) "No cards match. Change your filters." else "$n cards match"; enableStart(n > 0) }.onFailure { count.text = "Couldn't search: ${it.message}" } }
            }
        }, 180)
    }
    private fun launch() {
        if (busy) return
        busy = true; generation++; enableStart(false); count.text = "Collecting matching cards…"
        val setup = filter
        worker.execute {
            val result = runCatching {
                val rows = Anki.searchCards(this, setup.search(), arrayOf("_id", "note_id", "ord", "deck_id")).distinctBy { it["_id"] }
                check(rows.isNotEmpty()) { "No cards match. Change your filters." }
                val file = File(filesDir, "filter-session-${UUID.randomUUID()}.json")
                FilterSession(rows, query = setup.search()).save(file)
                file.name
            }
            main.post {
                if (isDestroyed) return@post
                busy = false
                result.onSuccess { file ->
                    library = library.used(label, setup); persist()
                    startActivity(Intent(this, StudyActivity::class.java).putExtra("filterSession", file).putExtra("deckId", setup.decks.first().id).putExtra("deckName", "Chosen cards"))
                    finish()
                }.onFailure { count.text = it.message; enableStart(false) }
            }
        }
    }
}
