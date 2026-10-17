package com.husarp.ankigo.deck

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.Anki
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.StudyUi
import com.husarp.ankigo.ui.Ui

/**
 * The "Move to deck" bottom sheet: the deck tree (every deck under its parent) with a radio per deck, a "+" for a new deck and one big
 * button. Filtered decks are greyed ("filtered": a tap says why), the deck the card is in is marked "now" (for "Move all cards": "from",
 * not pickable). The button stays dimmed until another deck is picked. What the pick does is up to [onPick].
 */
internal class DeckPickerSheet(
    private val ctx: Context,
    private val title: String,
    private var decks: List<Anki.Deck>,
    private val currentId: Long,
    private val mode: DeckPick.Mode,
    private val action: String,
    private val onPick: (Anki.Deck) -> Unit,
    private val onNewDeck: (() -> Unit)? = null,
) {
    private var selected: Long? = if (mode == DeckPick.Mode.CARD) currentId else null
    private val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private lateinit var go: TextView

    /** The sheet (rounded top corners, handle, title with "+", scrolling tree, button). Never taller than 85 % of the window. */
    fun content(): View {
        val sheet = object : LinearLayout(ctx) {
            override fun onMeasure(w: Int, h: Int) {
                val cap = (resources.displayMetrics.heightPixels * .85f).toInt()
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(if (MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED) cap else minOf(MeasureSpec.getSize(h), cap), MeasureSpec.AT_MOST))
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Ui.c(ctx, R.color.ag_card))
                val r = Ui.dp(ctx, 22f); cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 6), Ui.dp(ctx, 16), Ui.dp(ctx, 14))
        }
        sheet.addView(View(ctx).apply { background = Ui.rounded(Ui.c(ctx, R.color.ag_ov), 2, ctx) },
            LinearLayout.LayoutParams(Ui.dp(ctx, 40), Ui.dp(ctx, 4)).also { it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = Ui.dp(ctx, 4); it.bottomMargin = Ui.dp(ctx, 8) })
        val head = StudyUi.row(ctx)
        head.addView(Ui.text(ctx, 17f, 900, R.color.ag_text).apply { text = title }, StudyUi.lp(0, -2, 1f))
        if (onNewDeck != null) head.addView(StudyUi.iconButton(ctx, R.drawable.ic_plus, "New deck") { onNewDeck.invoke() }.apply {
            setColorFilter(Ui.c(context, R.color.ag_mauve))
            setPadding(Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12))
        }, StudyUi.lp(Ui.dp(ctx, 48), Ui.dp(ctx, 48)).also { it.setMargins(0, -Ui.dp(ctx, 6), -Ui.dp(ctx, 10), -Ui.dp(ctx, 6)) })
        sheet.addView(head, StudyUi.lp(-1, -2))
        val scroll = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false; addView(list) }
        sheet.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).also { it.topMargin = Ui.dp(ctx, 4) })
        go = DeckViews.button(ctx, action, R.color.ag_mauve) { pick() }
        sheet.addView(go, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 10) })
        bind()
        return sheet
    }

    /** A new deck list (after "+" made a deck); [select] = the deck to pick now. */
    fun reload(newDecks: List<Anki.Deck>, select: Long? = null) {
        decks = newDecks
        if (select != null) selected = select
        bind()
    }

    private fun pick() {
        val d = decks.firstOrNull { it.id == selected } ?: return
        if (d.id == currentId || d.dyn) return
        onPick(d)
    }

    private fun canGo() = decks.firstOrNull { it.id == selected }?.let { !it.dyn && it.id != currentId } == true

    private fun bind() {
        list.removeAllViews()
        for (r in DeckPick.rows(decks, currentId, mode)) list.addView(row(r))
        if (::go.isInitialized) DeckViews.enable(go, canGo())
    }

    private fun row(r: DeckPick.Row): View {
        val on = r.deck.id == selected
        val line = StudyUi.row(ctx).apply {
            minimumHeight = Ui.dp(ctx, 46)
            setPadding(Ui.dp(ctx, 2 + 22 * r.depth), 0, Ui.dp(ctx, 4), 0)
            background = StudyUi.ripple(ctx, Ui.rounded(android.graphics.Color.TRANSPARENT, 10, ctx))
            contentDescription = r.deck.name + (r.tag?.let { ", $it" } ?: "") + if (on) ", selected" else ""
            if (!r.pickable && r.deck.dyn) alpha = .45f
            else if (!r.pickable) alpha = .85f
            setOnClickListener {
                if (r.pickable) { selected = r.deck.id; bind() } else Ui.toast(ctx, r.reason ?: "")
            }
        }
        line.addView(radio(on, r.pickable), StudyUi.lp(Ui.dp(ctx, 22), Ui.dp(ctx, 22)).also { it.marginEnd = Ui.dp(ctx, 12) })
        line.addView(StudyUi.ellipsis(Ui.text(ctx, 14f, if (on) 900 else 800, R.color.ag_text).apply {
            text = r.label + if (r.deck.dyn) " (filtered)" else ""
        }), StudyUi.lp(0, -2, 1f))
        if (r.tag != null && !r.deck.dyn) line.addView(Ui.text(ctx, 11f, 800, R.color.ag_sub0).apply { text = r.tag; setPadding(Ui.dp(ctx, 8), 0, Ui.dp(ctx, 4), 0) })
        return line
    }

    /** A radio ring; the picked one has the accent ring and a filled dot. Drawn by hand (the platform's radio button takes the system colours). */
    private fun radio(on: Boolean, enabled: Boolean): View = FrameLayout(ctx).apply {
        addView(View(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(0)
                setStroke(Ui.dp(ctx, 2), Ui.c(ctx, if (on) R.color.ag_mauve else R.color.ag_ov))
            }
        }, FrameLayout.LayoutParams(Ui.dp(ctx, 20), Ui.dp(ctx, 20), Gravity.CENTER))
        if (on) addView(View(ctx).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Ui.c(ctx, R.color.ag_mauve)) }
        }, FrameLayout.LayoutParams(Ui.dp(ctx, 10), Ui.dp(ctx, 10), Gravity.CENTER))
        if (!enabled && !on) alpha = .6f
    }

}
