package com.husarp.ankigo.ui

import android.content.ClipData
import android.content.Context
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.husarp.ankigo.R

/** One editable draft. Done persists it; Cancel and the back button discard it. */
object DetailOrderSettings {
    fun show(a: Context) {
        val editor = Editor(a, AppPrefs.detailOrder(a))
        Pickers.show(AppDialog.Builder(a).setTitle("Details sections order")
            .setView(ScrollView(a).apply { addView(editor) }, true)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Done") { _, _ -> AppPrefs.setDetailOrder(a, editor.order) }.create())
    }

    internal class Editor(a: Context, initial: List<DetailSection>) : LinearLayout(a) {
        var order: List<DetailSection> = DetailOrder.resolve(initial.map { it.key })
            private set
        private val rows = LinearLayout(a).apply { orientation = VERTICAL }

        init {
            orientation = VERTICAL
            setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 12), Ui.dp(a, 12))
            addView(Ui.text(a, 13f, 700, R.color.ag_sub0).apply {
                text = "Hold a drag handle, or use the arrows.\nPokémon header and HP/XP stay at the top."
            })
            addView(rows, LayoutParams(-1, -2).also { it.topMargin = Ui.dp(a, 10) })
            addView(Form.secondary(a, "Reset to default") { order = DetailOrder.default; render() }, LayoutParams(-1, -2))
            render()
        }

        internal fun move(section: DetailSection, index: Int) {
            order = DetailOrder.move(order, section, index)
            render()
            announceForAccessibility("${DetailOrder.label(section)}, ${order.indexOf(section) + 1} of ${order.size}")
        }

        private fun render() {
            rows.removeAllViews()
            order.forEachIndexed { index, section ->
                val row = LinearLayout(context).apply {
                    orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = Ui.dp(context, 56)
                    background = DetailParts.rowRipple(context)
                }
                val handle = Ui.text(context, 24f, 800, R.color.ag_sub0).apply {
                    text = "⠿"; gravity = Gravity.CENTER
                    contentDescription = "Drag ${DetailOrder.label(section)}"
                    setOnLongClickListener {
                        startDragAndDrop(ClipData.newPlainText("Details section", section.key), View.DragShadowBuilder(row), section, 0)
                    }
                }
                row.addView(handle, LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)))
                row.addView(Ui.text(context, 13f, 800, R.color.ag_text).apply { text = DetailOrder.label(section) }, LayoutParams(0, -2, 1f))
                fun arrow(text: String, delta: Int) = Ui.text(context, 20f, 900, R.color.ag_mauve).apply {
                    this.text = text; gravity = Gravity.CENTER
                    isEnabled = index + delta in order.indices
                    alpha = if (isEnabled) 1f else .3f
                    contentDescription = "Move ${DetailOrder.label(section)} ${if (delta < 0) "up" else "down"}"
                    setOnClickListener { move(section, index + delta) }
                    background = DetailParts.bareRipple(context)
                }
                row.addView(arrow("↑", -1), LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)))
                row.addView(arrow("↓", 1), LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)))
                row.setOnDragListener { v, event ->
                    val dragged = event.localState as? DetailSection
                    when (event.action) {
                        DragEvent.ACTION_DRAG_STARTED -> dragged != null
                        DragEvent.ACTION_DRAG_ENTERED -> { v.alpha = .6f; true }
                        DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> { v.alpha = 1f; true }
                        DragEvent.ACTION_DROP -> { v.alpha = 1f; if (dragged != null) move(dragged, index); dragged != null }
                        else -> dragged != null
                    }
                }
                rows.addView(row, LayoutParams(-1, -2))
            }
        }
    }
}
