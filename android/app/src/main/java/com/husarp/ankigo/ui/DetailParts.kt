package com.husarp.ankigo.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.Stat

// TICKET U-09: pieces shared by the Pokémon details (MonDetail) and the Pokédex entry (DexDetail).

/** Pure helpers of the details screens (unit-tested). */
object DetailMath {
    /** Top of a Pokémon's stat bars at [level]: a good stat of that level fills most of the bar. */
    fun monStatMax(level: Int): Int = 100 + level.coerceIn(1, 100) * 3

    /** Bar fill 0..1 of [value] against [max]. */
    fun fraction(value: Int, max: Int): Float = if (max <= 0) 0f else (value.toFloat() / max).coerceIn(0f, 1f)

    /** +1 when the nature raises [s], -1 when it lowers it, else 0. */
    fun natureDir(n: Nature, s: Stat): Int = when {
        n.plus == null || n.minus == null -> 0
        n.plus == s -> 1
        n.minus == s -> -1
        else -> 0
    }

    /** "▲", "▼" or "" for [natureDir]. */
    fun arrow(dir: Int): String = when { dir > 0 -> "▲"; dir < 0 -> "▼"; else -> "" }

    fun statName(s: Stat): String = when (s) {
        Stat.HP -> "HP"; Stat.ATK -> "Attack"; Stat.DEF -> "Defense"
        Stat.SPA -> "Sp. Atk"; Stat.SPD -> "Sp. Def"; Stat.SPE -> "Speed"
    }

    /** "Raises Speed, lowers Sp. Atk", or "Neutral" for the five natures that change nothing. */
    fun natureEffect(n: Nature): String {
        val p = n.plus
        val m = n.minus
        return if (p == null || m == null) "Neutral" else "Raises ${statName(p)}, lowers ${statName(m)}"
    }

    @ColorRes fun statColor(s: Stat): Int = when (s) {
        Stat.HP -> R.color.ag_green; Stat.ATK -> R.color.ag_peach; Stat.DEF -> R.color.ag_yellow
        Stat.SPA -> R.color.ag_blue; Stat.SPD -> R.color.ag_teal; Stat.SPE -> R.color.ag_pink
    }

    val LABELS = listOf("HP", "Atk", "Def", "SpA", "SpD", "Spe")
}

/** R9 (C2): where a flow of boxes breaks into lines (pure, unit-tested): a box goes on the line while the line stays within [maxW]. */
object FlowMath {
    /** One line of a flow: the boxes [start] until [end] (exclusive indexes) and the line's width. */
    class Line(val start: Int, val end: Int, val width: Int)

    /**
     * Breaks boxes of [widths] (margins included) into lines at most [maxW] wide with [gap] between boxes: a box starts a new line
     * only when it does not fit in what is left of the current one (a box wider than [maxW] stands alone on its line).
     */
    fun lines(widths: List<Int>, gap: Int, maxW: Int): List<Line> {
        val out = ArrayList<Line>()
        var start = 0
        var w = 0
        for (i in widths.indices) {
            val add = widths[i] + if (i > start) gap else 0
            if (i > start && w + add > maxW) {
                out += Line(start, i, w)
                start = i; w = widths[i]
            } else w += add
        }
        if (widths.isNotEmpty()) out += Line(start, widths.size, w)
        return out
    }
}

/**
 * Wrapping row (centred lines unless [centered] is false) for the tag pills and the matchup chips. A child goes on the line while
 * it fits in what is left of it (children's margins count); [gapPx] is between children, [lineGapPx] between lines.
 */
class FlowRow(ctx: Context, private val gapPx: Int, private val centered: Boolean = true, private val lineGapPx: Int = gapPx) : ViewGroup(ctx) {
    private val kids = ArrayList<View>()
    private var lines: List<FlowMath.Line> = emptyList()
    private val lineH = ArrayList<Int>()

    private fun mlp(c: View) = c.layoutParams as MarginLayoutParams

    override fun generateDefaultLayoutParams(): LayoutParams = MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    override fun generateLayoutParams(attrs: android.util.AttributeSet?): LayoutParams = MarginLayoutParams(context, attrs)
    override fun generateLayoutParams(p: LayoutParams): LayoutParams = MarginLayoutParams(p)
    override fun checkLayoutParams(p: LayoutParams?): Boolean = p is MarginLayoutParams

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val unbounded = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED
        val maxW = if (unbounded) Int.MAX_VALUE / 4 else (MeasureSpec.getSize(widthSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        kids.clear()
        val widths = ArrayList<Int>()
        val heights = ArrayList<Int>()
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            val lp = mlp(c)
            val room = (maxW - lp.leftMargin - lp.rightMargin).coerceAtLeast(0)
            // measured on its own (not measureChild: that would take this row's padding off a second time)
            c.measure(MeasureSpec.makeMeasureSpec(room, if (unbounded) MeasureSpec.UNSPECIFIED else MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            kids += c
            widths += c.measuredWidth + lp.leftMargin + lp.rightMargin
            heights += c.measuredHeight + lp.topMargin + lp.bottomMargin
        }
        lines = FlowMath.lines(widths, gapPx, maxW)
        lineH.clear()
        for (l in lines) lineH += (l.start until l.end).maxOf { heights[it] }
        val total = lineH.sum() + lineGapPx * maxOf(0, lines.size - 1) + paddingTop + paddingBottom
        val w = if (unbounded) (lines.maxOfOrNull { it.width } ?: 0) + paddingLeft + paddingRight else MeasureSpec.getSize(widthSpec)
        setMeasuredDimension(w, total)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var y = paddingTop
        val inner = r - l - paddingLeft - paddingRight
        lines.forEachIndexed { n, line ->
            var x = paddingLeft + if (centered) ((inner - line.width) / 2).coerceAtLeast(0) else 0
            val h = lineH[n]
            for (i in line.start until line.end) {
                val c = kids[i]
                val lp = mlp(c)
                x += lp.leftMargin
                val top = y + (h - (c.measuredHeight + lp.topMargin + lp.bottomMargin)) / 2 + lp.topMargin
                c.layout(x, top, x + c.measuredWidth, top + c.measuredHeight)
                x += c.measuredWidth + lp.rightMargin + gapPx
            }
            y += h + lineGapPx
        }
    }
}

/** Builders of the details look. [compact] = small window: tighter rows. */
object DetailParts {
    /** Small window (floating card, split screen): by width or height. */
    fun compact(ctx: Context): Boolean {
        val c = ctx.resources.configuration
        return c.screenWidthDp in 1..379 || c.screenHeightDp in 1..559
    }

    /** Section header: tinted icon + caps title. */
    fun header(a: Context, @DrawableRes icon: Int, title: String, compact: Boolean): View = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, Ui.dp(a, if (compact) 10 else 14), 0, Ui.dp(a, 4))
        val img = ImageView(a).apply {
            setImageResource(icon)
            setColorFilter(Ui.c(a, R.color.ag_mauve), PorterDuff.Mode.SRC_IN)
        }
        addView(img, LinearLayout.LayoutParams(Ui.dp(a, 15), Ui.dp(a, 15)))
        addView(Ui.text(a, 11f, 900, R.color.ag_sub0).apply {
            text = title.uppercase(); letterSpacing = 0.06f
        }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(a, 6) })
    }

    /** Popup with the explanation [i] (reuses the app's small info dialog). */
    fun showInfo(a: Context, i: StatInfoText) {
        val body = Ui.text(a, 14f, 700, R.color.ag_text).apply {
            text = i.text
            setPadding(Ui.dp(a, 24), Ui.dp(a, 4), Ui.dp(a, 24), Ui.dp(a, 8))
        }
        Pickers.info(a, i.title, body)
    }

    /** Makes [v] open the popup for [i] (touch area at least 30 dp tall). */
    fun tapInfo(v: View, i: StatInfoText): View = v.apply {
        minimumHeight = Ui.dp(context, 30)
        contentDescription = "${i.title}: ${i.text}"
        setOnClickListener { showInfo(context, i) }
    }

    /** The touch feedback of a row that fills its width: a soft grey ripple inside the row's own bounds. */
    fun rowRipple(a: Context): android.graphics.drawable.Drawable =
        RippleDrawable(ColorStateList.valueOf(Ui.c(a, R.color.ag_s1) and 0x00FFFFFF or 0x55000000), null, ColorDrawable(Color.BLACK))

    /** The touch feedback of a bare icon button (no tile behind it): a round ripple over its whole 48 dp touch area. */
    fun bareRipple(a: Context): android.graphics.drawable.Drawable =
        RippleDrawable(ColorStateList.valueOf(Ui.c(a, R.color.ag_s1) and 0x00FFFFFF or 0x55000000), null,
            GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.BLACK) })

    /**
     * R18-G (PLAN 241): the WHOLE row [v] (title and value) opens the popup for [i], at least [DetailFormat.TAP_MIN_DP] dp tall, with a
     * ripple. For a row whose title is only a small part of it (the title alone was a tiny target).
     */
    fun tapRow(v: View, i: StatInfoText): View = v.apply {
        minimumHeight = Ui.dp(context, DetailFormat.TAP_MIN_DP)
        isClickable = true
        val l = paddingLeft; val t = paddingTop; val r = paddingRight; val b = paddingBottom
        background = rowRipple(context)
        setPadding(l, t, r, b) // a new background can reset the padding
        tag = i // the tests find the tappable rows by it
        // no contentDescription: a screen reader reads the row's own texts (title and value); the action says what a tap does
        accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, "explain ${i.title}"))
            }
        }
        setOnClickListener { showInfo(context, i) }
    }

    /** Small grey "Tap a stat name ..." line at the top of a stats block. */
    fun statHint(a: Context): View = Ui.text(a, 11f, 700, R.color.ag_ov).apply {
        text = StatInfo.HINT
        setPadding(0, 0, 0, Ui.dp(a, 2))
    }

    /** "Label   value" line for the Info / Evolution sections. */
    fun line(a: Context, label: String, value: CharSequence, labelDp: Int = 84, compact: Boolean = false): LinearLayout = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, Ui.dp(a, if (compact) 1 else 2), 0, Ui.dp(a, if (compact) 1 else 2))
        addView(Ui.text(a, 12f, 800, R.color.ag_sub0).apply { text = label }, LinearLayout.LayoutParams(Ui.dp(a, labelDp), -2))
        addView(Ui.text(a, 12f, 800, R.color.ag_text).apply { text = value }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    /**
     * One stat row: label, value (+ ▲/▼ [dir] in green/red), coloured bar of [frac], and the small grey [base] at the end
     * (null = none, the Pokédex). [wholeRow] (R18-G, PLAN 241): the whole row, at least 48 dp tall, opens the explanation, not just the label.
     */
    fun statRow(a: Context, stat: Stat, value: Int, frac: Float, dir: Int, base: Int?, compact: Boolean, wholeRow: Boolean = false): View = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val v = Ui.dp(a, if (compact) 2 else 3)
        setPadding(0, v, 0, v)
        val label = Ui.text(a, 12f, 800, R.color.ag_sub0).apply { text = DetailMath.LABELS[stat.ordinal]; gravity = Gravity.CENTER_VERTICAL }
        if (wholeRow) tapRow(this, StatInfo.of(stat))
        addView(if (wholeRow) label else tapInfo(label, StatInfo.of(stat)), LinearLayout.LayoutParams(Ui.dp(a, 32), -2))
        addView(Ui.text(a, 13f, 900, R.color.ag_text).apply { text = value.toString(); gravity = Gravity.END }, LinearLayout.LayoutParams(Ui.dp(a, 34), -2))
        addView(Ui.text(a, 11f, 900, if (dir > 0) R.color.ag_green else R.color.ag_red).apply {
            text = DetailMath.arrow(dir); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(Ui.dp(a, 16), -2))
        val bar = BarView(a)
        bar.set(frac, Ui.c(a, DetailMath.statColor(stat)), false)
        addView(bar, LinearLayout.LayoutParams(0, Ui.dp(a, 7), 1f).also { it.marginEnd = Ui.dp(a, 8) })
        if (base != null) addView(Ui.text(a, 11f, 700, R.color.ag_ov).apply { text = base.toString(); gravity = Gravity.END },
            LinearLayout.LayoutParams(Ui.dp(a, 28), -2))
    }

    /**
     * "Total   NNN" row under the stat bars; [base] is the grey base total at the end ([wholeRow]: the whole row, 48 dp tall, is the tap target). The label and the number sit in the same
     * 66 dp (label + value columns of [statRow]) and the number ends exactly where the stat numbers above end.
     */
    fun totalRow(a: Context, total: Int, base: Int?, wholeRow: Boolean = false): View = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, Ui.dp(a, 4), 0, 0)
        if (wholeRow) tapRow(this, StatInfo.TOTAL)
        addView(android.widget.FrameLayout(a).apply {
            val big = total < 1000 // a 4-digit total (high levels) gets the normal sizes, so the label and the number never touch
            val label = Ui.text(a, if (big) 14f else 12f, 800, R.color.ag_sub0).apply { text = "Total"; gravity = Gravity.CENTER_VERTICAL }
            addView(if (wholeRow) label else tapInfo(label, StatInfo.TOTAL),
                android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL))
            addView(Ui.text(a, if (big) 15f else 13f, 900, R.color.ag_text).apply { text = total.toString(); gravity = Gravity.END },
                android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL))
        }, LinearLayout.LayoutParams(Ui.dp(a, 32 + 34), -2))
        addView(View(a), LinearLayout.LayoutParams(0, 1, 1f))
        if (base != null) addView(Ui.text(a, 11f, 700, R.color.ag_ov).apply { text = base.toString(); gravity = Gravity.END },
            LinearLayout.LayoutParams(Ui.dp(a, 28), -2))
    }

    /** Move line for small windows: type chip, then the name over the small power / accuracy line (nothing gets cut). */
    fun compactMoveRow(a: Context, data: com.husarp.ankigo.game.GameData, id: String): View {
        val m = data.move(id) ?: return Ui.text(a, 13f, 800, R.color.ag_text).apply { text = id }
        return LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Ui.dp(a, 3), 0, Ui.dp(a, 3))
            addView(Ui.typeChip(a, m.type), LinearLayout.LayoutParams(Ui.dp(a, 62), -2))
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(a, 14f, 800, R.color.ag_text).apply { text = m.name; maxLines = 1 })
                addView(Ui.text(a, 11f, 700, R.color.ag_sub0).apply { text = Bits.moveStats(data, id); maxLines = 1 })
            }, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(a, 8) })
        }
    }

    /** Full-width secondary button (48 dp). */
    fun secondaryButton(a: Context, label: String, onClick: () -> Unit): TextView = Ui.text(a, 14f, 900, R.color.ag_text).apply {
        text = label
        gravity = Gravity.CENTER
        minHeight = Ui.dp(a, 48)
        background = Ui.rounded(Ui.c(a, R.color.ag_s0), 12, a)
        setOnClickListener { onClick() }
    }
}
