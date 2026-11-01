package com.husarp.ankigo.study

import com.husarp.ankigo.ui.MotionScrollView

import android.content.Context
import android.view.View
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.*

/** Shared bottom sheet body; selection stays local until Apply. */
internal class FilterSheet(private val ctx: Context, title: String, close: () -> Unit) {
    val view = object : LinearLayout(ctx) {
        override fun onMeasure(w: Int, h: Int) {
            val cap = (resources.displayMetrics.heightPixels * .89f).toInt()
            super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(
                minOf(cap, if (View.MeasureSpec.getMode(h) == View.MeasureSpec.UNSPECIFIED) cap else View.MeasureSpec.getSize(h)), View.MeasureSpec.AT_MOST))
        }
    }.apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 22, ctx)
        setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 8), Ui.dp(ctx, 16), Ui.dp(ctx, 16))
    }
    private val body = StudyUi.column(ctx)
    private val footer = StudyUi.column(ctx)
    init {
        val head = StudyUi.row(ctx)
        head.addView(StudyUi.label(ctx, title, 17, R.color.ag_text, 900), StudyUi.lp(0, -2, 1f))
        head.addView(StudyUi.iconButton(ctx, R.drawable.ic_close, "Close sheet", close), StudyUi.lp(Ui.dp(ctx, 48), Ui.dp(ctx, 48)))
        view.addView(head)
        view.addView(MotionScrollView(ctx).apply { addView(body) }, StudyUi.lp(-1, -2, 1f))
        view.addView(footer)
    }
    fun loading(close: () -> Unit = {}) {
        body.removeAllViews(); footer.removeAllViews()
        body.addView(StudyUi.row(ctx).apply {
            minimumHeight = Ui.dp(ctx, 64)
            addView(ProgressBar(ctx).apply { isIndeterminate = true; indeterminateTintList = android.content.res.ColorStateList.valueOf(Ui.c(ctx, R.color.ag_mauve)) }, StudyUi.lp(Ui.dp(ctx, 24), Ui.dp(ctx, 24)))
            addView(StudyUi.label(ctx, "Loading tags…").apply { setPadding(Ui.dp(ctx, 12), 0, 0, 0) })
        })
        footer.addView(StudyUi.secondary(ctx, "Cancel", close))
    }
    fun error(close: () -> Unit = {}, retry: () -> Unit) {
        body.removeAllViews(); footer.removeAllViews()
        body.addView(StudyUi.label(ctx, "Couldn’t load tags."))
        footer.addView(StudyUi.secondary(ctx, "Retry", retry))
        footer.addView(StudyUi.secondary(ctx, "Cancel", close), StudyUi.spaced(ctx, 6))
    }
    fun info(close: () -> Unit) {
        body.removeAllViews(); footer.removeAllViews()
        body.addView(StudyUi.label(ctx, "Ready-made setups replace your current choices. Remove a deck with ×, or add more decks.\n\nTags narrow the selection. Free study includes cards not due; those answers leave their Anki schedule unchanged.\n\nThe count updates as you change choices. Start studies the matching cards.", 14))
        footer.addView(StudyUi.finishedPrimary(ctx, "Got it", close), StudyUi.spaced(ctx, 12))
    }
    fun choices(values: List<Pair<String, String>>, initial: Set<String>, empty: String,
                description: String, close: () -> Unit, tags: Boolean = true, apply: (Set<String>) -> Unit) {
        body.removeAllViews(); footer.removeAllViews()
        val selected = initial.intersect(values.map { it.first }.toSet()).toMutableSet()
        body.addView(StudyUi.label(ctx, description))
        val search = EditText(ctx).apply { hint = if (tags) "Search tags" else "Search decks"; contentDescription = hint; isSingleLine = true; minimumHeight = Ui.dp(ctx, 48) }
        body.addView(search)
        val list = StudyUi.column(ctx)
        fun fill(query: String) {
            list.removeAllViews()
            val matches = values.filter { it.second.contains(query, true) }
            if (matches.isEmpty()) list.addView(StudyUi.label(ctx, if (values.isEmpty()) empty else if (tags) "No tags match your search." else "No decks match your search."))
            matches.forEach { (key, name) -> list.addView(CheckBox(ctx).apply {
                text = name; minimumHeight = Ui.dp(ctx, 48); isChecked = key in selected
                setOnCheckedChangeListener { _, on -> if (on) selected.add(key) else selected.remove(key) }
            }) }
        }
        body.addView(list)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { fill(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })
        fill("")
        if (tags) body.addView(StudyUi.label(ctx, "No tags selected means any tag."))
        footer.addView(StudyUi.finishedPrimary(ctx, "Apply") { apply(selected.toSet()); close() }, StudyUi.spaced(ctx, 8))
        footer.addView(StudyUi.secondary(ctx, "Cancel", close), StudyUi.spaced(ctx, 6))
    }
}
