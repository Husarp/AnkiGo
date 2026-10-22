package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.PorterDuff
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.husarp.ankigo.R

/**
 * PLAN 220: a titled card whose bar (title, chevron, 48 dp touch) folds and unfolds its [rows]. Starts folded unless [open];
 * the fold state is not saved. Built for the Diagnostics page; the bar is tagged [HEADER_TAG] and the rows' holder [BODY_TAG].
 */
internal object FoldGroup {
    const val HEADER_TAG = "fold-header"
    const val BODY_TAG = "fold-body"

    fun build(ctx: Context, title: String, rows: List<View>, open: Boolean = false): LinearLayout {
        val card = Ui.card(ctx)
        val chevron = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_chevron)
            setColorFilter(Ui.c(ctx, R.color.ag_sub0), PorterDuff.Mode.SRC_IN)
        }
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = Ui.dp(ctx, 48)
            setPadding(Ui.dp(ctx, 14), 0, Ui.dp(ctx, 8), 0)
            tag = HEADER_TAG
            isClickable = true
            addView(Ui.text(ctx, 14f, 900, R.color.ag_text).apply { text = title }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(chevron, LinearLayout.LayoutParams(Ui.dp(ctx, 24), Ui.dp(ctx, 24)))
        }
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            tag = BODY_TAG
            setPadding(Ui.dp(ctx, 10), 0, Ui.dp(ctx, 10), Ui.dp(ctx, 10))
            rows.forEach { addView(it) }
        }
        fun apply(isOpen: Boolean) {
            body.visibility = if (isOpen) View.VISIBLE else View.GONE
            chevron.rotation = if (isOpen) 90f else 0f
            bar.contentDescription = "$title, ${if (isOpen) "open" else "closed"}"
        }
        apply(open)
        bar.setOnClickListener { apply(body.visibility != View.VISIBLE) }
        card.addView(bar, LinearLayout.LayoutParams(-1, -2))
        card.addView(body, LinearLayout.LayoutParams(-1, -2))
        return card
    }

    fun isOpen(group: View): Boolean = group.findViewWithTag<View>(BODY_TAG)?.visibility == View.VISIBLE
}
