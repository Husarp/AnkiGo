package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import com.husarp.ankigo.R
import com.husarp.ankigo.game.*

/** PLAN 279A: full-width rows, indented by stage; conditions keep their own readable space. */
internal class EvolutionRows(
    a: Context, private val data: GameData, root: EvoNode, private val current: Species,
    private val seen: Set<Int>, private val onSpecies: ((Species) -> Unit)?,
    private val state: GameState? = com.husarp.ankigo.GameRepo.state.takeIf { com.husarp.ankigo.GameRepo.data === data },
    private val mon: Mon? = null,
) : LinearLayout(a) {
    private var bubble: PopupWindow? = null
    private data class Branch(val row: View, val parent: View, val depth: Int)
    private val branches = ArrayList<Branch>()

    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Ui.c(context, R.color.ag_s1); strokeWidth = Ui.dp(context, 1.5f)
        }
        branches.forEach { branch ->
            val x = Ui.dp(context, 6 + branch.depth * 16 - 8).toFloat()
            val y = branch.row.top + branch.row.height / 2f
            canvas.drawLine(x, branch.parent.bottom.toFloat(), x, y, paint)
            canvas.drawLine(x, y, x + Ui.dp(context, 6), y, paint)
        }
        super.dispatchDraw(canvas)
    }

    init {
        orientation = VERTICAL
        if (root.children.isEmpty() && root.via == null) {
            addView(Ui.text(a, 12f, 800, R.color.ag_sub0).apply { text = "Does not evolve" })
        } else append(root, null, 0)
        addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) { bubble?.dismiss(); bubble = null }
        })
    }

    private fun append(node: EvoNode, parent: Species?, depth: Int, parentRow: View? = null) {
        val a = context
        val caught = node.species.num in state?.caught.orEmpty()
        val visible = caught || EvoLine.isSeen(node.species, seen)
        val here = node.species.num == current.num
        val row = LinearLayout(a).apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = Ui.dp(a, 48)
            setPadding(Ui.dp(a, 6 + depth * 16), Ui.dp(a, 4), Ui.dp(a, 6), Ui.dp(a, 4))
            if (here) background = Ui.rounded(Bits.blend(Ui.c(a, R.color.ag_mauve), Ui.c(a, R.color.ag_mantle), .22f), 8, a)
        }
        row.addView(ImageView(a).apply {
            MonPreview.show(a, node.species.num, false, this, silhouette = !visible)
            if (visible && !caught) {
                colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) })
                alpha = 0.55f
            }
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(Ui.dp(a, 32), Ui.dp(a, 32)))
        val names = LinearLayout(a).apply {
            orientation = VERTICAL
            addView(Ui.text(a, 12f, if (here) 900 else 800, if (!caught) R.color.ag_sub0 else R.color.ag_text).apply {
                text = if (visible) node.species.name.removeSuffix("-${node.species.form}") else "???"
            })
            if (visible) node.species.form?.let { form ->
                addView(Ui.text(a, 10f, 700, R.color.ag_sub0).apply { text = form })
            }
            contentDescription = EvoLine.shownName(node.species, seen) + if (here) " (this one)" else ""
            if (visible && !here && onSpecies != null) setOnClickListener { onSpecies.invoke(node.species) }
        }
        row.addView(names, LayoutParams(0, -2, 1f).apply { marginStart = Ui.dp(a, 6); marginEnd = Ui.dp(a, 6) })
        node.via?.let { e ->
            val condition = LinearLayout(a).apply {
                orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = Ui.rounded(Ui.c(a, R.color.ag_s0), 5, a)
                minimumHeight = Ui.dp(a, 40)
                setPadding(Ui.dp(a, 6), Ui.dp(a, 5), Ui.dp(a, 6), Ui.dp(a, 5))
            }
            val item = Progression.evolutionItem(e)
            if (item != null) condition.addView(ImageView(a).apply {
                Ui.itemIcon(a, item, this)
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(Ui.dp(a, 24), Ui.dp(a, 24)))
            else if (e.kind == EvoKind.FRIENDSHIP) condition.addView(Ui.text(a, 18f, 800, R.color.ag_pink).apply { text = "♥" })
            condition.addView(Ui.text(a, 11f, 800, R.color.ag_mauve).apply {
                text = EvolutionRowText.condition(data, e)
            }, LayoutParams(0, -2, 1f).apply { marginStart = Ui.dp(a, 4) })
            condition.contentDescription = "Explain ${EvolutionRowText.condition(data, e)}"
            condition.setOnClickListener { showExplanation(condition, e, parent) }
            row.addView(condition, LayoutParams(0, -2, 1.35f))
        }
        addView(row, LayoutParams(-1, -2).apply { topMargin = Ui.dp(a, 3) })
        if (parentRow != null) branches += Branch(row, parentRow, depth)
        node.children.forEach { append(it, node.species, depth + 1, row) }
    }

    private fun showExplanation(anchor: View, e: Evolution, parent: Species?) {
        bubble?.dismiss()
        val a = context
        val body = explanationContent(a, data, e, parent, seen, state, mon) { bag ->
            bubble?.dismiss()
            if (bag) ShopActivity.openBag(a) else Nav.open(a, Dest.SHOP)
        }
        val width = minOf(Ui.dp(a, 300), a.resources.displayMetrics.widthPixels - Ui.dp(a, 24))
        body.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val popup = PopupWindow(ScrollView(a).apply { addView(body) }, width,
            minOf(body.measuredHeight, Ui.dp(a, 320), a.resources.displayMetrics.heightPixels / 2), true)
        popup.setBackgroundDrawable(ColorDrawable(0))
        popup.isOutsideTouchable = true
        popup.elevation = Ui.dp(a, 8).toFloat()
        bubble = popup
        popup.showAsDropDown(anchor, anchor.width - width, Ui.dp(a, 4))
        AwayWindows.track(popup) { it.dismiss() }
    }

    companion object {
        internal fun explanationContent(a: Context, data: GameData, e: Evolution, parent: Species?, seen: Set<Int>,
                                        state: GameState?, mon: Mon?, open: (Boolean) -> Unit): View = LinearLayout(a).apply {
            orientation = VERTICAL
            background = Ui.rounded(Ui.c(a, R.color.ag_base), 12, a)
            setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12))
            addView(Ui.text(a, 14f, 900, R.color.ag_mauve).apply { text = EvolutionRowText.condition(data, e) })
            val item = Progression.evolutionItem(e)
            val text = EvolutionRowText.explanation(data, e, parent, seen, state, mon)
            addView(Ui.text(a, 13f, 700, R.color.ag_text).apply {
                val linked = SpannableString(text)
                for ((label, bag) in listOf("open your Bag" to true, "Shop" to false)) {
                    val start = text.indexOf(label)
                    if (start >= 0) linked.setSpan(object : ClickableSpan() {
                        override fun onClick(widget: View) { open(bag) }
                        override fun updateDrawState(ds: TextPaint) {
                            ds.color = Ui.c(a, R.color.ag_mauve); ds.isUnderlineText = true
                        }
                    }, start, start + label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                this.text = linked
                movementMethod = LinkMovementMethod.getInstance()
            })
        }
    }
}

internal object EvolutionRowText {
    fun condition(data: GameData, e: Evolution): String = EvoLine.method(data, e)
        .replace(Regex("^Lv "), "Level ")
        .replace("Friendship", "Friendship ${e.minFriendship ?: Tuning.DEFAULT_MIN_FRIENDSHIP}")
        .replace(", day", "\nin the daytime").replace(", night", "\nat night")
        .replace(", Fairy-type move", "\nknows a Fairy move")

    fun explanation(data: GameData, e: Evolution, parent: Species?, seen: Set<Int>, state: GameState?, mon: Mon?): String {
        val item = Progression.evolutionItem(e)
        val extra = if (item != null) {
            val who = parent?.let { EvoLine.shownName(it, seen) } ?: "your Pokémon"
            "To use it, open your Bag, tap ${Items.name(item)} and choose $who." +
                (state?.let { " You own ${it.count(item)}." } ?: "") +
                "\n\nThe Shop sells evolution items on some days; they also drop while you study.\n\n"
        } else if (e.kind == EvoKind.FRIENDSHIP && mon != null && parent?.num == mon.species) {
            "Friendship now: ${mon.friendship} of ${e.minFriendship ?: Tuning.DEFAULT_MIN_FRIENDSHIP}.\n\n"
        } else ""
        val details = EvoLine.explanation(data, e)
        // The item paragraph already explains use; retain all extra restrictions from the existing rules text.
        return if (item == null) extra + details else extra + details.split("\n\n").drop(1).dropLast(1).joinToString("\n\n")
    }
}
