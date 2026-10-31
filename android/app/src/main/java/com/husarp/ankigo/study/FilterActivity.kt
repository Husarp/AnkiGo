package com.husarp.ankigo.study

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
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
    private var expanded = emptySet<Long>()
    private lateinit var content: LinearLayout
    private lateinit var count: TextView
    private lateinit var start: TextView
    private var generation = 0
    private var busy = false
    private var label = "Custom study"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Anki.load(this)
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
        root.addView(Ui.appBar(this, "Choose what to study", back = true, onBack = { finish() }))
        content = StudyUi.column(this).apply { setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0) }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        count = StudyUi.label(this, "Loading decks…", 14).apply { gravity = Gravity.CENTER }
        start = StudyUi.primary(this, "Start") { launch() }.apply { isEnabled = false }
        root.addView(count); root.addView(start)
        return root
    }
    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); out.putString("filter", filter.encode()); out.putString("label", label) }
    override fun onDestroy() { generation++; worker.shutdownNow(); super.onDestroy() }
    private fun persist() { prefs.edit().putString("filterLibrary", library.encode()).apply() }
    private fun heading(s: String) { content.addView(StudyUi.label(this, s, 14, R.color.ag_text, 900)) }
    private fun row(s: String, action: () -> Unit) {
        content.addView(StudyUi.smallButton(this, s, action = action).apply {
            textSize = 14f; gravity = Gravity.CENTER_VERTICAL or Gravity.START; minHeight = Ui.dp(context, 48)
        }, StudyUi.spaced(this, 4))
    }
    private fun savedLabel(saved: SavedFilter): String = saved.name + "\n" + saved.filter.decks.joinToString(" · ") { d ->
        val selectedTags = d.tags + if (d.ignoreGlobal) emptySet() else saved.filter.tags
        d.name + (if (selectedTags.isEmpty()) "" else " [${selectedTags.sorted().joinToString()}]") + if (d.free) " · free study" else " · due cards"
    }
    private fun load(saved: SavedFilter) { filter = saved.filter; label = saved.name; render() }
    private fun render(updateCount: Boolean = true) {
        if (busy) return
        content.removeAllViews()
        if (library.starred.isNotEmpty()) heading("Starred · frozen")
        library.starred.forEachIndexed { i, saved ->
            row("★ ${savedLabel(saved)}") { load(saved) }
            // Explicit removal never modifies the stored setup.
            row("Unstar ${saved.name}") { library = library.unstar(i); persist(); render() }
        }
        if (library.recent.isNotEmpty()) heading("Recent")
        library.recent.forEach { saved -> row(savedLabel(saved)) { load(saved) } }
        heading("Ready-made")
        row("Most cards waiting") {
            // Parent counts already include children: choose the busiest top-level branch to avoid duplicate counts.
            val best = decks.filter { !it.name.contains("::") }.maxByOrNull { it.total }
            filter = StudyFilter(best?.let { listOf(DeckFilter(it.id, it.name)) }.orEmpty()); label = "Most cards waiting"; render()
        }
        row("All decks · due cards") { filter = StudyFilter(decks.filter { !it.name.contains("::") }.map { DeckFilter(it.id, it.name) }); label = "All decks · due cards"; render() }
        heading("Cards")
        row("Decks · ${filter.decks.size} selected ▾") {
            picker("Decks", decks.map { it.id.toString() to it.name }, filter.decks.map { it.id.toString() }.toSet()) { selected ->
                filter = filter.copy(decks = decks.filter { it.id.toString() in selected }.map { d -> filter.decks.find { it.id == d.id } ?: DeckFilter(d.id, d.name) })
                render()
            }
        }
        row("Global tags · ${filter.tags.sorted().joinToString().ifEmpty { "Any" }} ▾") {
            readTags(filter.decks) { values -> picker("Global tags", values.map { it to it }, filter.tags) { filter = filter.copy(tags = it); render() } }
        }
        heading("Per-deck choices")
        filter.decks.forEach { d ->
            row("${d.name} · ${if (d.free) "free study" else "due cards"} ▾") { expanded = if (d.id in expanded) expanded - d.id else expanded + d.id; render() }
            if (d.id in expanded) {
                fun change(new: DeckFilter) { filter = filter.copy(decks = filter.decks.map { if (it.id == d.id) new else it }); render() }
                content.addView(CheckBox(this).apply { text = "Ignore global tags"; isChecked = d.ignoreGlobal; setOnCheckedChangeListener { _, on -> change(d.copy(ignoreGlobal = on)) } })
                row("This deck’s tags · ${d.tags.sorted().joinToString().ifEmpty { "Any" }} ▾") {
                    readTags(listOf(d)) { values ->
                        if (values.isEmpty()) AppDialog.Builder(this).setMessage("This deck has no tags").setPositiveButton("OK") { _, _ ->
                            if (d.tags.isNotEmpty()) change(d.copy(tags = emptySet()))
                        }.showCard()
                        else picker("${d.name} tags", values.map { it to it }, d.tags) { change(d.copy(tags = it)) }
                    }
                }
                content.addView(CheckBox(this).apply { text = "Free study · all matching cards, including cards not due"; isChecked = d.free; setOnCheckedChangeListener { _, on -> change(d.copy(free = on)) } })
            }
        }
        row("★ Star this filter") {
            val name = EditText(this).apply { setText(label); hint = "Filter name" }
            AppDialog.Builder(this).setTitle("Star a frozen copy").setView(name).setPositiveButton("Star") { _, _ ->
                label = name.text.toString().trim().ifEmpty { "Custom study" }; library = library.star(label, filter); persist(); render()
            }.setNegativeButton("Cancel", null).showCard()
        }
        if (updateCount) refreshCount()
    }
    private fun readTags(ds: List<DeckFilter>, done: (List<String>) -> Unit) {
        count.text = "Loading tags…"
        worker.execute {
            val result = runCatching {
                val noteTags = mutableMapOf<Long, String?>()
                ds.flatMap { d ->
                    val rows = Anki.searchCards(this, "deck:${StudyFilter.quote(d.name)}", arrayOf("note_id"))
                    StudyFilter.deckTags(rows.mapNotNull { it["note_id"]?.toLongOrNull() }) { nid ->
                        if (nid !in noteTags) noteTags[nid] = Anki.note(this, nid)?.tags
                        noteTags[nid]
                    }
                }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
            }
            main.post { if (!isDestroyed) { result.onSuccess(done).onFailure { Ui.toast(this, "Couldn't read tags: ${it.message}") }; refreshCount() } }
        }
    }
    private fun picker(title: String, values: List<Pair<String, String>>, initial: Set<String>, done: (Set<String>) -> Unit) {
        val selected = initial.intersect(values.map { it.first }.toSet()).toMutableSet()
        val box = StudyUi.column(this)
        val search = EditText(this).apply { hint = "Search $title"; isSingleLine = true }
        val list = StudyUi.column(this)
        fun fill(q: String) {
            list.removeAllViews()
            values.filter { it.second.contains(q, true) }.forEach { (key, name) ->
                list.addView(CheckBox(this).apply { text = name; isChecked = key in selected; setOnCheckedChangeListener { _, on -> if (on) selected.add(key) else selected.remove(key) } })
            }
        }
        box.addView(search); box.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, Ui.dp(this, 300)))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { fill(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })
        fill("")
        AppDialog.Builder(this).setTitle(title).setView(box).setPositiveButton("Apply") { _, _ -> done(selected.toSet()) }.setNegativeButton("Cancel", null).showCard()
    }
    private fun refreshCount() {
        val token = ++generation; val query = filter.search()
        start.isEnabled = false; count.text = "Counting…"
        main.postDelayed({
            if (isDestroyed || token != generation || busy) return@postDelayed
            worker.execute {
                val result = runCatching { Anki.countCards(this, query) }
                main.post { if (!isDestroyed && token == generation && !busy) result.onSuccess { n -> count.text = "$n cards match"; start.isEnabled = n > 0 }.onFailure { count.text = "Couldn't search: ${it.message}" } }
            }
        }, 180)
    }
    private fun launch() {
        if (busy) return
        busy = true; generation++; start.isEnabled = false; count.text = "Collecting matching cards…"
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
                }.onFailure { count.text = it.message; start.isEnabled = true }
            }
        }
    }
}
