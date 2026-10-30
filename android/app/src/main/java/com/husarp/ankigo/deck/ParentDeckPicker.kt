package com.husarp.ankigo.deck

import android.app.Activity
import android.app.Dialog
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ScrollView
import com.husarp.ankigo.Anki
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.DeckTree
import com.husarp.ankigo.ui.DeckListLook
import com.husarp.ankigo.ui.StudyUi
import com.husarp.ankigo.ui.Ui

/** Parent selection never writes to AnkiDroid. Closing it leaves the name and parent untouched. */
internal object ParentDeckPicker {
    fun show(act: Activity, decks: List<Anki.Deck>, selected: Anki.Deck?, onPick: (Anki.Deck?) -> Unit) {
        lateinit var dialog: Dialog
        dialog = DeckFlows.showSheet(act, content(act, decks, selected) { onPick(it); dialog.dismiss() })
    }

    fun content(act: Activity, decks: List<Anki.Deck>, selected: Anki.Deck?, onPick: (Anki.Deck?) -> Unit): android.view.View {
        val prefs = act.getSharedPreferences("study", 0)
        var expanded = DeckTree.decode(prefs.getStringSet("expandedDecks", null))
        val available = decks.filter { !it.dyn }.sortedBy { it.name }
        val box = DeckViews.box(act, "Inside")
        val search = EditText(act).apply {
            hint = "Search decks"; setSingleLine(); Ui.tintEdit(this)
            typeface = Ui.font(act, 700)
            setTextColor(Ui.c(act, R.color.ag_text)); setHintTextColor(Ui.c(act, R.color.ag_sub0))
        }
        box.addView(search)
        val list = StudyUi.column(act)
        box.addView(ScrollView(act).apply { addView(list) }, StudyUi.lp(-1, Ui.dp(act, 320)))
        fun choose(deck: Anki.Deck?) { onPick(deck) }
        fun bind() {
            list.removeAllViews()
            list.addView(DeckViews.button(act, (if (selected == null) "● " else "○ ") + "Top level") { choose(null) })
            val query = search.text.toString().trim()
            val rows = if (query.isEmpty()) DeckTree.rows(available, expanded) else available.filter { it.name.contains(query, true) }.map { DeckTree.Row(it, 0, false, false) }
            val indent = when (DeckListLook.get(act)) { DeckListLook.TODAY -> 16; DeckListLook.BRANCHES -> 18; DeckListLook.FAMILIES -> 14 }
            for (r in rows) {
                val row = StudyUi.row(act)
                row.setPadding(Ui.dp(act, r.depth * indent), 0, 0, 0)
                val arrow = StudyUi.iconButton(act, if (r.expanded) R.drawable.ic_expand else R.drawable.ic_chevron, "Fold or expand ${r.deck.name}") {
                    expanded = DeckTree.toggle(expanded, r.deck.id); bind()
                }
                if (!r.hasChildren) arrow.visibility = android.view.View.INVISIBLE
                row.addView(arrow, StudyUi.lp(Ui.dp(act, 48), Ui.dp(act, 48)))
                row.addView(DeckViews.button(act, (if (r.deck.id == selected?.id) "● " else "○ ") + if (query.isEmpty()) r.deck.name.substringAfterLast("::") else r.deck.name.replace("::", " › ")) { choose(r.deck) }, StudyUi.lp(0, -2, 1f))
                list.addView(row)
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { bind() }
            override fun afterTextChanged(s: Editable?) {}
        })
        bind()
        return box
    }
}
