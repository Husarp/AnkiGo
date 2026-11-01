package com.husarp.ankigo.deck

import com.husarp.ankigo.ui.MotionScrollView

import android.app.Activity
import android.app.Dialog
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import com.husarp.ankigo.Anki
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.Ui
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CancellationException

/** Long-press destination, with deck/subdeck search and opt-out of descendant cards. */
internal object DeckStatusSheet {
    private val io = Executors.newSingleThreadExecutor()
    fun show(act: Activity, initial: Anki.Deck) {
        var selected = initial
        var include = true
        val generation = AtomicInteger(0)
        var closed = false
        lateinit var dialog: Dialog
        val box = DeckViews.box(act, "Deck status")
        val deck = DeckViews.button(act, initial.name + " ▾") {}
        box.addView(deck)
        val sub = CheckBox(act).apply {
            text = "Include subdecks"; isChecked = true
            typeface = Ui.font(act, 700); setTextColor(Ui.c(act, R.color.ag_text))
        }
        box.addView(sub)
        val result = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        box.addView(result)
        fun line(text: String, size: Float = 14f, color: Int = R.color.ag_sub1) {
            result.addView(Ui.text(act, size, 700, color).apply {
                this.text = text; setPadding(0, Ui.dp(act, 6), 0, Ui.dp(act, 6))
            })
        }
        fun load() {
            val token = generation.incrementAndGet()
            val name = selected.name
            val descendants = include
            result.removeAllViews(); line("Loading…")
            io.execute {
                val data = runCatching {
                    check(Anki.studySupported(act)) { "Deck status needs AnkiDroid 2.24 or newer." }
                    DeckStatus.read(name, descendants) {
                        if (token != generation.get()) throw CancellationException()
                        Anki.countCards(act, it)
                    }
                }
                act.runOnUiThread {
                    if (closed || act.isDestroyed || act.isFinishing || token != generation.get()) return@runOnUiThread
                    result.removeAllViews()
                    val s = data.getOrElse {
                        line(when {
                            it is SecurityException -> DeckOps.NO_PERMISSION
                            it is IllegalStateException && it.message == "Deck status needs AnkiDroid 2.24 or newer." -> it.message.orEmpty()
                            else -> "Could not load deck status. Check AnkiDroid and try again."
                        })
                        result.addView(DeckViews.button(act, "Retry") { load() })
                        return@runOnUiThread
                    }
                    result.addView(statusView(act, s))
                }
            }
        }
        deck.setOnClickListener {
            val picker = DeckViews.box(act, "Pick a deck or subdeck")
            val search = EditText(act).apply { hint = "Search decks"; Ui.tintEdit(this); setSingleLine() }
            picker.addView(search)
            val list = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
            picker.addView(MotionScrollView(act).apply { addView(list) }, LinearLayout.LayoutParams(-1, Ui.dp(act, 320)))
            val pickDialog = DeckFlows.showSheet(act, picker)
            io.execute {
                val decks = runCatching { Anki.decks(act).sortedBy { it.name } }
                act.runOnUiThread {
                    if (closed || act.isDestroyed || !pickDialog.isShowing) return@runOnUiThread
                    fun bind() {
                        list.removeAllViews()
                        decks.onSuccess { all ->
                            val matches = all.filter { it.name.contains(search.text.toString().trim(), true) }
                            if (matches.isEmpty()) list.addView(DeckViews.message(act, "No matching decks"))
                            matches.forEach { d -> list.addView(DeckViews.button(act, d.name.replace("::", " › ")) {
                                selected = d; deck.text = d.name + " ▾"; pickDialog.dismiss(); load()
                            }) }
                        }.onFailure { list.addView(DeckViews.message(act, "Could not load decks. Check AnkiDroid and try again.")) }
                    }
                    search.addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { bind() }
                        override fun afterTextChanged(s: Editable?) {}
                    })
                    bind()
                }
            }
        }
        sub.setOnCheckedChangeListener { _, on -> include = on; load() }
        box.addView(DeckViews.button(act, "Close") { dialog.dismiss() })
        dialog = DeckFlows.showSheet(act, MotionScrollView(act).apply { addView(box) })
        dialog.setOnDismissListener { closed = true; generation.incrementAndGet() }
        load()
    }
    /** Shared rendering for the live sheet and small/light/dark layout checks. */
    internal fun statusView(act: android.content.Context, s: DeckStatus.Snapshot): LinearLayout {
        val result = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        fun line(text: String, size: Float = 14f, color: Int = R.color.ag_sub1) {
            result.addView(Ui.text(act, size, 700, color).apply {
                this.text = text; setPadding(0, Ui.dp(act, 6), 0, Ui.dp(act, 6))
            })
        }
        line(s.known?.let { "$it%" } ?: "—", 32f, R.color.ag_text)
        line("known · ${s.states[3]} of ${s.active} active cards")
        val colors = listOf(R.color.ag_blue, R.color.ag_red, R.color.ag_peach, R.color.ag_green)
        val bar = LinearLayout(act).apply {
            clipToOutline = true
            contentDescription = "Card states: " + listOf("New", "Learning", "Young", "Mature").zip(s.states).joinToString { "${it.first} ${it.second}" }
            background = Ui.rounded(Ui.c(act, R.color.ag_mantle), 4, act)
        }
        if (s.active > 0) s.states.forEachIndexed { i, n ->
            if (n > 0) bar.addView(View(act).apply { setBackgroundColor(Ui.c(act, colors[i])) }, LinearLayout.LayoutParams(0, -1, n.toFloat()))
        }
        result.addView(bar, LinearLayout.LayoutParams(-1, Ui.dp(act, 12)))
        line("Known = mature / active cards; interval ≥21 days. A maturity estimate, not a test score. Learning includes relearning; empty deck shows —.", 12f)
        listOf("New", "Learning", "Young", "Mature").forEachIndexed { i, label -> line("$label · ${s.states[i]}", color = colors[i]) }
        line("Study rhythm", 16f, R.color.ag_text)
        line("${if (s.streak == 365) "365+" else s.streak} ${if (s.streak == 1) "day" else "days"} in a row")
        line(if (s.todayIncluded) "This deck · today included" else "This deck · through yesterday (not studied today)", 12f)
        line("Due now · ${s.due}\nDue tomorrow · ${s.tomorrow}\nIntroduced · 7 days · ${s.introduced} cards")
        line("${s.suspended} suspended · ${s.buried} buried · excluded above", 12f)
        return result
    }

}
