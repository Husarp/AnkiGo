package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Type

// PLAN 192F2 / 192E, X9, X10 (Adam's version): the ONE control row of the Box and the Pokédex (legend "?", sort & filter button, the
// active-filter chip, quick chips for filters that are off), the words of the active chip and of the empty states, and the width plan.

/** The words of the filter row and the empty states. Pure Kotlin (no views), unit-tested. */
object FilterRowText {
    /** A piece of the active chip's label: a type symbol (the name follows as a [Txt]), plain text, the pink heart, or the yellow star. */
    sealed class Piece {
        class Sym(val type: Type) : Piece()
        class Txt(val s: String) : Piece()
        object Heart : Piece()
        object Star : Piece()
    }

    /** "Water + Ice · All of" (symbols before the names), 3 and more types "3 types · Any of". Nothing picked = no pieces. */
    fun typePieces(f: TypeFilter): List<Piece> {
        if (!f.active) return emptyList()
        if (f.types.size > 2) return listOf(Piece.Txt("${f.types.size} types · ${f.match.label}"))
        val out = ArrayList<Piece>()
        f.types.forEachIndexed { i, t ->
            if (i > 0) out += Piece.Txt(" + ")
            out += Piece.Sym(t); out += Piece.Txt(t.label)
        }
        out += Piece.Txt(" · ${f.match.label}")
        return out
    }

    /** "Gen 2", "Gen 1–3" (a run), "Gen 1, 3, 5". */
    fun gensLabel(gens: Set<Int>): String {
        val s = gens.sorted()
        return when {
            s.size == 1 -> "Gen ${s[0]}"
            s.last() - s.first() == s.size - 1 -> "Gen ${s.first()}–${s.last()}"
            else -> "Gen ${s.joinToString(", ")}"
        }
    }

    /** The label pieces of the active chip, filters joined with " · ". [show] = the Box "Show" choice that is on (null = All). Empty = no active chip. */
    fun activePieces(types: TypeFilter, show: BoxFilter? = null, wishlist: Boolean = false, gens: Set<Int> = emptySet()): List<Piece> {
        val groups = ArrayList<List<Piece>>()
        if (types.active) groups += typePieces(types)
        if (show != null && show != BoxFilter.ALL) groups += if (show == BoxFilter.FAVOURITES) listOf(Piece.Heart, Piece.Txt(show.label)) else listOf(Piece.Txt(show.label))
        if (wishlist) groups += listOf(Piece.Star, Piece.Txt("Wishlist"))
        if (gens.isNotEmpty()) groups += listOf(Piece.Txt(gensLabel(gens)))
        val out = ArrayList<Piece>()
        groups.forEachIndexed { i, g -> if (i > 0) out += Piece.Txt(" · "); out += g }
        return out
    }

    /** The label as plain words (the chip's description): "Water + Ice · All of · Favourites". */
    fun plain(pieces: List<Piece>): String = pieces.joinToString("") { if (it is Piece.Txt) it.s else "" }

    private fun orList(n: List<String>): String = if (n.size < 2) n.joinToString("") else n.dropLast(1).joinToString(", ") + " or " + n.last()

    /** The Pokémon the filters ask for, as the empty text says it: "pure Poison Pokémon", "Water or Ice Pokémon", "Pokémon with only Water + Ice types". */
    private fun subject(adjectives: List<String>, f: TypeFilter): String {
        val n = f.types.map { it.label }
        val multiOnly = f.active && f.match == TypeMatch.ONLY && n.size > 1
        val typeAdj = when {
            !f.active || multiOnly -> null
            f.match == TypeMatch.ANY -> orList(n)
            f.match == TypeMatch.ALL -> n.joinToString("/")
            else -> "pure ${n[0]}"
        }
        val a = (adjectives + listOfNotNull(typeAdj)).joinToString(" ")
        val base = if (a.isEmpty()) "Pokémon" else "$a Pokémon"
        return if (multiOnly) "$base with only ${n.joinToString(" + ")} types" else base
    }

    /** What an empty Box says and why, e.g. "No pure Poison Pokémon in your box.". With no filter on it is the plain "Your box is empty…" (no button). */
    fun emptyBox(show: BoxFilter, types: TypeFilter): String {
        if (show == BoxFilter.ALL && !types.active) return "Your box is empty. Pokémon you catch beyond six wait here."
        return when (show) {
            BoxFilter.ALL -> "No ${subject(emptyList(), types)} in your box."
            BoxFilter.FAVOURITES -> if (types.active) "No ${subject(listOf("favourite"), types)} in your box." else "No favourites yet."
            BoxFilter.SHINY -> "No ${subject(listOf("shiny"), types)} in your box."
            BoxFilter.IN_TEAM -> "No ${subject(emptyList(), types)} in your team."
            BoxFilter.HOLDS_ITEM -> if (types.active) "No ${subject(emptyList(), types)} holds an item." else show.none!!
            BoxFilter.HOLDS_XP_SHARE -> if (types.active) "No ${subject(emptyList(), types)} holds the Exp. Share." else show.none!!
            BoxFilter.CAN_EVOLVE -> if (types.active) "No ${subject(emptyList(), types)} can evolve right now." else show.none!!
        }
    }

    /** What an empty Pokédex says and why: "No pure Poison Pokémon in Gen 1–3.", "No Pokémon named “abc”.". */
    fun emptyDex(f: DexFilter): String {
        if (!f.active) return "No Pokémon to show."
        val adjectives = listOfNotNull(when (f.owned) { DexOwned.OWNED -> "owned"; DexOwned.NOT_OWNED -> "unowned"; DexOwned.ALL -> null })
        val q = f.query.trim().removePrefix("#")
        val parts = listOfNotNull(
            subject(adjectives, f.typeFilter),
            if (f.wishlistOnly) "on your wishlist" else null,
            if (f.gens.isNotEmpty()) "in ${gensLabel(f.gens)}" else null,
            if (q.isEmpty()) null else if (q.all { it.isDigit() }) "numbered “$q”" else "named “$q”",
        )
        return "No ${parts.joinToString(" ")}."
    }
}

/** The width plan of the filter row (pixels): who gets how much. Pure, unit-tested. */
object FilterRowPlan {
    /** [sortW] / [activeW]: the widths given to the sort chip and the active chip (0 = none); [quickShown]: how many quick chips fit, in order. */
    data class Plan(val sortW: Int, val activeW: Int, val quickShown: Int)

    /**
     * [avail] = the row's width. The legend chip ([legendW]) and the sort chip (natural [sortW], never below [sortMinW]) come first; the
     * active chip ([activeW], 0 = none) gets the rest but never less than [activeMinW] (its icons and the ✕), so the sort chip gives way
     * before an active filter is hidden; the quick chips ([quickW]) follow in order while they fit, the rest hide. [gap] between chips.
     */
    fun plan(avail: Int, legendW: Int, sortW: Int, sortMinW: Int, activeW: Int, activeMinW: Int, quickW: List<Int>, gap: Int): Plan {
        val reserve = if (activeW > 0) gap + activeMinW else 0
        val sort = minOf(sortW, maxOf(avail - legendW - gap - reserve, minOf(sortMinW, sortW)))
        var x = legendW + gap + sort
        var active = 0
        if (activeW > 0) {
            active = maxOf(activeMinW, minOf(activeW, avail - x - gap))
            x += gap + active
        }
        var shown = 0
        for (w in quickW) {
            if (x + gap + w > avail) break
            x += gap + w; shown++
        }
        return Plan(sort, active, shown)
    }
}

/**
 * The row: [legend], [sort], the optional [active] chip, then the [quick] chips. One line that never scrolls: the sort chip's name and the
 * active chip's label shrink, the quick chips hide (in order) when they don't fit.
 */
internal class FilterRow(ctx: Context) : ViewGroup(ctx) {
    private var legend: View? = null
    private var sort: View? = null
    private var active: ActiveChip? = null
    private var quick: List<View> = emptyList()
    private val gap = Ui.dp(ctx, 6)
    private var rowH = 0

    fun set(legend: View, sort: View, active: ActiveChip?, quick: List<View>) {
        removeAllViews()
        this.legend = legend; this.sort = sort; this.active = active; this.quick = quick
        addView(legend); addView(sort)
        active?.let { addView(it) }
        quick.forEach { addView(it) }
    }

    /** The quick chips shown (the width plan hides the rest); tests read it. */
    fun shownQuick(): List<View> = quick.filter { it.visibility == VISIBLE }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val lg = legend ?: return setMeasuredDimension(0, 0)
        val so = sort!!
        val unspec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val avail = if (MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE / 4 else MeasureSpec.getSize(widthSpec) - paddingLeft - paddingRight
        lg.measure(unspec, unspec); so.measure(unspec, unspec)
        quick.forEach { it.visibility = VISIBLE; it.measure(unspec, unspec) }
        val ac = active
        ac?.measure(unspec, unspec)
        val plan = FilterRowPlan.plan(
            avail, lg.measuredWidth, so.measuredWidth, minOf(so.measuredWidth, Ui.dp(context, SORT_MIN_DP)),
            ac?.measuredWidth ?: 0, ac?.minWidthPx ?: 0, quick.map { it.measuredWidth }, gap,
        )
        so.measure(MeasureSpec.makeMeasureSpec(plan.sortW, MeasureSpec.AT_MOST), unspec)
        ac?.measure(MeasureSpec.makeMeasureSpec(plan.activeW, MeasureSpec.EXACTLY), unspec)
        quick.forEachIndexed { i, v -> if (i >= plan.quickShown) v.visibility = GONE }
        rowH = children().filter { it.visibility != GONE }.maxOf { it.measuredHeight }
        val used = children().filter { it.visibility != GONE }.sumOf { it.measuredWidth } + gap * (children().count { it.visibility != GONE } - 1)
        setMeasuredDimension(if (MeasureSpec.getMode(widthSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(widthSpec) else used + paddingLeft + paddingRight, rowH + paddingTop + paddingBottom)
    }

    private fun children(): List<View> = (0 until childCount).map { getChildAt(it) }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var x = paddingLeft
        for (c in children()) {
            if (c.visibility == GONE) continue
            val y = paddingTop + (rowH - c.measuredHeight) / 2
            c.layout(x, y, x + c.measuredWidth, y + c.measuredHeight)
            x += c.measuredWidth + gap
        }
    }

    companion object {
        /** The sort chip never gets narrower than this (funnel, a short name, the arrow). */
        const val SORT_MIN_DP = 104

        /**
         * The row of a screen: [legend], [sort] and, while any filter is on ([pieces] not empty), the active chip (a tap opens the sheet
         * through [onOpen]; its ✕ runs [onClear]), then the [quick] chips of the filters that are off.
         */
        fun make(ctx: Context, legend: View, sort: View, pieces: List<FilterRowText.Piece>, onOpen: () -> Unit, onClear: () -> Unit, quick: List<View>): FilterRow =
            FilterRow(ctx).also { it.set(legend, sort, if (pieces.isEmpty()) null else ActiveChip(ctx, pieces, onOpen, onClear), quick) }
    }
}

/**
 * The active-filter chip (F2 look: a soft accent fill with an accent ring, no "Filters:" word): the filters that are on, then a ✕ that
 * clears them all. A tap on the body opens the sheet ([onOpen]). Its label shrinks with "…" ([minWidthPx] is the least it gets: the
 * first symbol and the ✕), so the chip and its ✕ are always visible.
 */
internal class ActiveChip(ctx: Context, pieces: List<FilterRowText.Piece>, onOpen: () -> Unit, onClear: () -> Unit) : LinearLayout(ctx) {
    val label: TextView
    val clearButton: ImageView
    private val minLabel = Ui.dp(ctx, 44)
    private val xW = Ui.dp(ctx, 48)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = Ui.dp(ctx, 48)
        val accent = Ui.c(ctx, R.color.ag_mauve)
        val card = Ui.c(ctx, R.color.ag_card)
        background = InsetDrawable(GradientDrawable().apply {
            cornerRadius = Ui.dp(ctx, 10f)
            setColor(Bits.blend(accent, card, 0.22f))
            setStroke(Ui.dp(ctx, 1.5f).toInt(), Bits.blend(accent, card, 0.6f))
        }, 0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 10))
        setPadding(0, 0, 0, 0) // the inset background adds its 10 dp to the padding: a 28 dp look in a 48 dp row, nothing more
        val words = FilterRowText.plain(pieces)
        contentDescription = "Filters on: $words. Tap to change."
        setOnClickListener { onOpen() }
        label = Ui.text(ctx, 12f, 800, R.color.ag_text).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            text = spannable(ctx, pieces, this)
            setPadding(Ui.dp(ctx, 10), 0, 0, 0)
        }
        addView(label, LayoutParams(0, -2, 1f))
        clearButton = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(accent)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val p = Ui.dp(ctx, 18)
            setPadding(p, p, p, p) // a 12 dp ✕ in a 48 x 48 dp touch area
            contentDescription = "Clear all filters"
            setOnClickListener { onClear() }
        }
        addView(clearButton, LayoutParams(xW, Ui.dp(ctx, 48)))
    }

    /** The least width: the first symbol, an ellipsis and the ✕. */
    val minWidthPx: Int get() = minLabel + xW

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        if (MeasureSpec.getMode(widthSpec) == MeasureSpec.EXACTLY) return super.onMeasure(widthSpec, heightSpec)
        // natural width: the whole label and the ✕ (the label is a weighted 0 dp child, so measure it by hand)
        label.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        setMeasuredDimension(maxOf(minWidthPx, label.measuredWidth + xW), maxOf(Ui.dp(context, 48), label.measuredHeight))
    }

    private fun spannable(ctx: Context, pieces: List<FilterRowText.Piece>, tv: TextView): CharSequence {
        val sb = SpannableStringBuilder()
        val size = Ui.dp(ctx, 13)
        for (p in pieces) when (p) {
            is FilterRowText.Piece.Txt -> sb.append(p.s)
            is FilterRowText.Piece.Sym -> sb.append("￼").also {
                sb.setSpan(IconSpan(TypeChips.tinted(ctx, p.type, TypeChips.ink(ctx, p.type)), size, Ui.dp(ctx, 4)), sb.length - 1, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            FilterRowText.Piece.Heart -> sb.append("￼").also {
                val d = ContextCompat.getDrawable(ctx, R.drawable.ic_heart)!!.mutate().also { h -> h.setTint(Ui.c(ctx, R.color.ag_pink)) }
                sb.setSpan(IconSpan(d, size, Ui.dp(ctx, 4)), sb.length - 1, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            FilterRowText.Piece.Star -> {
                val at = sb.length
                sb.append("★ ")
                sb.setSpan(ForegroundColorSpan(Ui.c(ctx, R.color.ag_yellow)), at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return sb
    }

    /** A small icon in a line of text, centred on the text's height ([padEnd] after it). */
    private class IconSpan(private val d: Drawable, private val size: Int, private val padEnd: Int) : ReplacementSpan() {
        init { d.setBounds(0, 0, size, size) }

        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int = size + padEnd

        override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            val fm = paint.fontMetricsInt
            val cy = y + (fm.ascent + fm.descent) / 2f
            canvas.save()
            canvas.translate(x, cy - size / 2f)
            d.draw(canvas)
            canvas.restore()
        }
    }
}

/**
 * PLAN 192E / X9: what an empty list says: why ([set]'s text, see [FilterRowText.emptyBox] / [FilterRowText.emptyDex]) and, while a filter is
 * on, a "Clear filters" button (accent text, 48 dp touch) that runs [onClear].
 */
internal class EmptyState(ctx: Context, topDp: Int, onClear: () -> Unit) : LinearLayout(ctx) {
    val message: TextView = Ui.text(ctx, 13f, 700, R.color.ag_sub0).apply { gravity = Gravity.CENTER }
    val clear: TextView = Ui.text(ctx, 14f, 900, R.color.ag_mauve).apply {
        text = "Clear filters"
        gravity = Gravity.CENTER
        minHeight = Ui.dp(ctx, 48)
        setPadding(Ui.dp(ctx, 16), 0, Ui.dp(ctx, 16), 0)
        setOnClickListener { onClear() }
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, topDp), Ui.dp(ctx, 12), Ui.dp(ctx, 12))
        addView(message, LayoutParams(-2, -2))
        addView(clear, LayoutParams(-2, -2))
    }

    fun set(text: String, canClear: Boolean) {
        message.text = text
        clear.visibility = if (canClear) View.VISIBLE else View.GONE
    }
}
