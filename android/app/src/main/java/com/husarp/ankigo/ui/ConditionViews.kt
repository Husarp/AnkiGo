package com.husarp.ankigo.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.husarp.ankigo.R
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/*
 * R19-K (PLAN 269 A): the look of the condition chips and of the list a tap opens. Pure maths is in ConditionMarks.kt.
 */

/** Sizes and colours of one chip, for a text size [textSp] (already raised to the panel's floor). */
internal class ChipMetrics(private val ctx: Context, textSp: Float) {
    private val dm = ctx.resources.displayMetrics
    val textPx: Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSp, dm)
    val word = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font(ctx, 800); textSize = textPx; color = ctx.getColor(R.color.ag_text) }
    val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font(ctx, 900); textSize = textPx; color = ctx.getColor(R.color.ag_sub0) }
    private val fm = word.fontMetrics
    private val padH = textPx * 0.7f
    val padV = textPx * 0.28f
    val height: Float = padV * 2f + (fm.descent - fm.ascent)
    val radius: Float = height * 0.42f

    /** Space between two chips of a row. */
    val gap: Float = textPx * 0.4f

    /** Space between a row and the box or sprite it sits by. */
    val lift: Float = textPx * 0.25f

    fun width(m: ConditionMarks.Mark): Float {
        val n = m.number
        return padH * 2f + iconWidth(m.key) + word.measureText(m.word) + if (n != null) num.measureText(" · $n") else 0f
    }

    fun iconWidth(key: String): Float = if (FieldIcons.of(key) != null || key in setOf("switchtrap", "spikes", "toxicspikes", "stealthrock", "stickyweb")) textPx * 1.35f else 0f

    fun moreWidth(n: Int): Float = padH * 2f + num.measureText("+$n")

    fun hue(g: ConditionMarks.Group): Int = ctx.getColor(when (g) {
        ConditionMarks.Group.HINDER -> R.color.ag_mauve
        ConditionMarks.Group.DRAIN -> R.color.ag_green
        ConditionMarks.Group.GUARD -> R.color.ag_blue
        ConditionMarks.Group.BOOST -> R.color.ag_peach
        ConditionMarks.Group.WEATHER -> R.color.ag_sky
        ConditionMarks.Group.TERRAIN -> R.color.ag_teal
        ConditionMarks.Group.ROOM -> R.color.ag_lav
    })

    /** The chip's fill: the card colour washed with its hue (opaque, so it reads on any picture). */
    fun fill(g: ConditionMarks.Group): Int = ColorUtils.blendARGB(ctx.getColor(R.color.ag_card), hue(g), if (isDark(ctx)) 0.3f else 0.24f)

    fun moreFill(): Int = ColorUtils.blendARGB(ctx.getColor(R.color.ag_card), ctx.getColor(R.color.ag_text), 0.12f)

    fun stroke(): Int = ColorUtils.setAlphaComponent(ctx.getColor(R.color.ag_text), 0x24)

    fun baseline(top: Float): Float = top + padV - fm.ascent

    companion object {
        fun isDark(ctx: Context) = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}

/**
 * The chips of both sides and the field, drawn in one layer as big as the arena. It draws only the chips, catches touches only on a chip
 * (everything else goes on to the views under it) and tells [onTap] which row was tapped.
 */
internal class ConditionMarksView(ctx: Context) : View(ctx) {
    /** One chip on screen: its rectangle (this view's px) and what it says. */
    class Chip(val rect: RectF, val mark: ConditionMarks.Mark?, val more: Int)

    /** One placed row. */
    class Row(val slot: ConditionMarks.Slot, val chips: List<Chip>) {
        val bounds: RectF get() = RectF(chips.minOf { it.rect.left }, chips.minOf { it.rect.top }, chips.maxOf { it.rect.right }, chips.maxOf { it.rect.bottom })
    }

    var metrics: ChipMetrics? = null
    var onTap: (ConditionMarks.Slot) -> Unit = {}
    var onStatTap: (ConditionMarks.Mark) -> Unit = {}
    private var downMark: ConditionMarks.Mark? = null
    internal fun markAt(x: Float, y: Float): ConditionMarks.Mark? = slotAt(x, y)?.let { rows[it]?.chips?.firstOrNull { c -> x >= c.rect.left && x < c.rect.right }?.mark }
    private val rows = LinkedHashMap<ConditionMarks.Slot, Row>()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var downSlot: ConditionMarks.Slot? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO   // the battle log lines are spoken already; the list is a touch extra
        isClickable = false
    }

    val shownRows: Collection<Row> get() = rows.values

    fun rowOf(slot: ConditionMarks.Slot): Row? = rows[slot]

    /** Every chip rectangle on screen (tests, obstacles for the toasts). */
    fun chipRects(): List<RectF> = rows.values.flatMap { r -> r.chips.map { it.rect } }

    /** Puts [placed] (a spot from [ConditionSpot]) with its first [ConditionRow.Fit.shown] of [marks] in the row of [slot]; null removes the row. */
    fun setRow(slot: ConditionMarks.Slot, placed: ConditionSpot.Placed?, marks: List<ConditionMarks.Mark>, m: ChipMetrics) {
        if (placed == null) { if (rows.remove(slot) != null) invalidate(); return }
        var x = placed.rect.l
        val top = placed.rect.t
        val chips = ArrayList<Chip>()
        for (i in 0 until placed.fit.shown) {
            val w = m.width(marks[i])
            chips += Chip(RectF(x, top, x + w, top + m.height), marks[i], 0)
            x += w + m.gap
        }
        if (placed.fit.more > 0) {
            val w = m.moreWidth(placed.fit.more)
            chips += Chip(RectF(x, top, x + w, top + m.height), null, placed.fit.more)
        }
        rows[slot] = Row(slot, chips)
        metrics = m
        invalidate()
    }

    fun clearRows() { if (rows.isNotEmpty()) { rows.clear(); invalidate() } }

    override fun onDraw(canvas: Canvas) {
        val m = metrics ?: return
        line.strokeWidth = max(1f, m.textPx * 0.07f)
        line.color = m.stroke()
        for (row in rows.values) for (c in row.chips) {
            fill.color = c.mark?.let {
                if (it.key.startsWith("stat.")) ColorUtils.blendARGB(Ui.c(context, R.color.ag_card),
                    Ui.c(context, if (it.value > 0) R.color.ag_green else R.color.ag_red), if (ChipMetrics.isDark(context)) .22f else .13f)
                else m.fill(it.def.group)
            } ?: m.moreFill()
            canvas.drawRoundRect(c.rect, m.radius, m.radius, fill)
            canvas.drawRoundRect(c.rect, m.radius, m.radius, line)
            if (downSlot == row.slot) {
                fill.color = ColorUtils.setAlphaComponent(context.getColor(R.color.ag_text), 32)
                canvas.drawRoundRect(c.rect, m.radius, m.radius, fill)
            }
            val base = m.baseline(c.rect.top)
            val mk = c.mark
            if (mk == null) {
                canvas.drawText("+${c.more}", c.rect.left + (c.rect.width() - m.num.measureText("+${c.more}")) / 2f, base, m.num)
            } else {
                var x0 = c.rect.left + (c.rect.width() - m.width(mk)) / 2f + m.textPx * 0.7f
                if (m.iconWidth(mk.key) > 0f) {
                    val fieldIcon = FieldIcons.of(mk.key)
                    val icon = fieldIcon ?: when (mk.key) {
                        "switchtrap" -> R.drawable.ic_mu_ban
                        "toxicspikes" -> R.drawable.ic_type_poison
                        "stickyweb" -> R.drawable.ic_type_bug
                        else -> R.drawable.ic_type_rock
                    }
                    context.getDrawable(icon)?.mutate()?.let {
                        val size = m.textPx.toInt()
                        val top = (c.rect.centerY() - size / 2f).toInt()
                        if (fieldIcon == null) it.setTint(m.hue(mk.def.group)); it.setBounds(x0.toInt(), top, x0.toInt() + size, top + size)
                        it.draw(canvas)
                    }
                    x0 += m.iconWidth(mk.key)
                }
                m.word.color = if (mk.key.startsWith("stat.")) Ui.c(context, if (mk.value > 0) R.color.ag_green else R.color.ag_red) else Ui.c(context, R.color.ag_text)
                canvas.drawText(mk.word, x0, base, m.word)
                mk.number?.let { canvas.drawText(" · $it", x0 + m.word.measureText(mk.word), base, m.num) }
            }
        }
    }

    internal fun slotAt(x: Float, y: Float): ConditionMarks.Slot? {
        val half = Ui.dp(context, 24f)
        return rows.values.firstOrNull { row ->
            val center = row.bounds.centerY()
            var top = center - half
            var bottom = center + half
            for (other in rows.values) {
                if (other === row) continue
                val oy = other.bounds.centerY()
                if (oy < center) top = max(top, (center + oy) / 2f)
                else if (oy > center) bottom = min(bottom, (center + oy) / 2f)
            }
            y >= max(0f, top) && y < min(height.toFloat(), bottom) && row.chips.any { x >= it.rect.left && x < it.rect.right }
        }?.slot
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downSlot = slotAt(e.x, e.y); downMark = markAt(e.x, e.y); invalidate(); return downSlot != null }
            MotionEvent.ACTION_UP -> {
                val s = downSlot; downSlot = null; invalidate()
                if (s != null && slotAt(e.x, e.y) == s) {
                    performClick()
                    val mark = downMark
                    if (mark?.id == markAt(e.x, e.y)?.id) {
                        if (mark != null && mark.key.startsWith("stat.")) onStatTap(mark) else onTap(s)
                    }
                }
                downMark = null
                return s != null
            }
            MotionEvent.ACTION_CANCEL -> { downSlot = null; invalidate() }
        }
        return downSlot != null
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}

/**
 * The list a tap on a chip row, its "+N" or an info box opens: every condition of the fight with its turns left, in sections (the tapped one
 * first). A bubble in the app's card style; it lays itself out in as many columns as the arena's height needs, and scrolls only when even the
 * smallest text in four columns is taller than the arena.
 */
internal class ConditionList(ctx: Context, private val floorSp: Float) : FrameLayout(ctx) {
    private val m = ChipMetrics(ctx, TextMath.at(11f, floorSp))
    private val body = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    private var scroll: ScrollView? = null
    private var sections: List<ConditionSections.Section> = emptyList()

    /** The number of columns the last [build] used (tests). */
    var columns = 1
        private set

    /** True when the list is taller than the arena and scrolls (tests). */
    val scrolls: Boolean get() = scroll != null

    init {
        val pad = Ui.dp(ctx, 8)
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            setColor(ctx.getColor(R.color.ag_card))
            cornerRadius = Ui.dp(ctx, 10f)
            setStroke(max(1, Ui.dp(ctx, 1)), m.stroke())
        }
        elevation = Ui.dp(ctx, 8f)
        isClickable = true
        addView(body, LayoutParams(-2, -2))
    }

    /** What a screen reader says for the whole list. */
    val spoken: String get() = sections.joinToString(" ") { s -> (s.title?.let { "$it. " } ?: "") + s.marks.joinToString(". ") { it.spoken + FieldExplanation.current(it) } + "." }

    private class Item(val title: String?, val mark: ConditionMarks.Mark?)

    private fun items(): List<Item> = sections.flatMap { s -> (if (s.title != null) listOf(Item(s.title, null)) else emptyList()) + s.marks.map { Item(null, it) } }

    private fun line(i: Item, wrap: Boolean, sp: Float): TextView = TextView(context).apply {
        typeface = Ui.font(context, if (i.mark == null) 900 else 700)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TextMath.at(if (i.mark == null) sp - 0.5f else sp, floorSp))
        includeFontPadding = false
        setLineSpacing(0f, 1.1f)
        maxLines = if (i.mark?.let { FieldIcons.of(it.key) } != null) Int.MAX_VALUE else if (wrap) 2 else 1
        val mk = i.mark
        if (mk == null) { text = i.title; setTextColor(context.getColor(R.color.ag_sub1)) }
        else {
            val dot = "\u25CF "
            val s = SpannableStringBuilder(dot).append(mk.line + FieldExplanation.current(mk))
            s.setSpan(ForegroundColorSpan(m.hue(mk.def.group)), 0, dot.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), dot.length, dot.length + mk.def.word.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text = s
            setTextColor(context.getColor(R.color.ag_text))
        }
        setPadding(0, if (i.mark == null) Ui.dp(context, 3) else Ui.dp(context, 1), 0, Ui.dp(context, 1))
    }

    /** [all] cut into [cols] columns of about the same length; a section title never ends a column. */
    private fun split(all: List<Item>, cols: Int): List<List<Item>> {
        val per = ceil(all.size / cols.toDouble()).toInt().coerceAtLeast(1)
        val out = ArrayList<MutableList<Item>>()
        var cur = ArrayList<Item>()
        for (it in all) {
            if (cur.size >= per && out.size < cols - 1) { out += cur; cur = ArrayList() }
            cur += it
        }
        out += cur
        for (i in 0 until out.size - 1) { val c = out[i]; if (c.size > 1 && c.last().mark == null) out[i + 1].add(0, c.removeAt(c.size - 1)) }
        return out
    }

    /** The columns of [all]: [cols] of them, text [sp], each line on one line or ([wrap]) up to two; [colW] px wide each (0 = as wide as its text). */
    private fun fill(all: List<Item>, cols: Int, sp: Float, wrap: Boolean, colW: Int = 0) {
        body.removeAllViews()
        val parts = split(all, cols)
        columns = parts.size
        for ((n, part) in parts.withIndex()) {
            val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            for (it in part) col.addView(line(it, wrap, sp), LinearLayout.LayoutParams(-2, -2))
            body.addView(col, LinearLayout.LayoutParams(if (colW > 0) colW else -2, -2).also { lp -> if (n > 0) lp.marginStart = Ui.dp(context, 10) })
        }
    }

    private fun unscroll() {
        val sc = scroll ?: return
        sc.removeView(body); removeView(sc); scroll = null
        addView(body, LayoutParams(-2, -2))
    }

    /**
     * Fills the list with [sections] and measures it so it fits [maxW] x [maxH] px: every line on one line, in as few columns (up to
     * [MAX_COLUMNS]) as it takes, with the text from 11 sp down to 9 sp (never under the panel's floor) before a column has to be crowded.
     * Nothing fits that way (a very small arena): columns of equal width at the smallest size, lines of up to two lines; a list that is still
     * taller than the arena scrolls.
     */
    fun build(sections: List<ConditionSections.Section>, maxW: Int, maxH: Int) {
        this.sections = sections
        unscroll()
        val all = items()
        val none = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val padW = paddingLeft + paddingRight
        val padH = paddingTop + paddingBottom
        fun measureBody() = body.measure(none, none)
        for (sp in SIZES) for (cols in 1..MAX_COLUMNS) {
            fill(all, cols, sp, wrap = false)
            measureBody()
            if (body.measuredWidth + padW <= maxW && body.measuredHeight + padH <= maxH) { measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST)); return }
        }
        for (cols in 1..MAX_COLUMNS) {
            val colW = ((maxW - padW - (cols - 1) * Ui.dp(context, 10)) / cols).coerceAtLeast(1)
            fill(all, cols, SIZES.last(), wrap = true, colW = colW)
            body.measure(MeasureSpec.makeMeasureSpec(maxW - padW, MeasureSpec.AT_MOST), none)
            if (body.measuredHeight + padH <= maxH || cols == MAX_COLUMNS) break
        }
        if (body.measuredHeight + padH > maxH) {   // still taller than the arena: it scrolls
            removeView(body)
            scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false; overScrollMode = OVER_SCROLL_NEVER; addView(body, LayoutParams(-2, -2)) }
            addView(scroll, LayoutParams(-2, -2))
        }
        measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST))
    }

    /** PLAN 369 B: a smaller compact card, with a scroll fallback for short battle panels. */
    fun buildStat(sections: List<ConditionSections.Section>, maxW: Int, maxH: Int, close: () -> Unit) {
        this.sections = sections
        unscroll()
        body.removeAllViews()
        columns = 1
        val pad = Ui.dp(context, 12)
        setPadding(pad, pad, pad, pad)
        val section = sections.single()
        val mark = section.marks.single()
        val key = mark.key.removePrefix("stat.")
        val name = StatPill.longName(key)
        val n = mark.value
        val multiplier = if (key == "accuracy" || key == "evasion") {
            if (n >= 0) (3.0 + n) / 3.0 else 3.0 / (3.0 - n)
        } else com.husarp.ankigo.game.BattleEngine.stageMult(n)
        val factor = "%.2f".format(java.util.Locale.US, multiplier).trimEnd('0').trimEnd('.')
        val hue = context.getColor(if (n > 0) R.color.ag_green else R.color.ag_red)
        fun label(value: String, sp: Float, color: Int, weight: Int = 700) = TextView(context).apply {
            text = value
            typeface = Ui.font(context, weight)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TextMath.at(sp, floorSp))
            setTextColor(color)
            includeFontPadding = false
        }
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val owner = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        owner.addView(label(section.title.orEmpty(), 11f, context.getColor(R.color.ag_sub0)), LinearLayout.LayoutParams(0, -2, 1f))
        owner.addView(label("×", 22f, context.getColor(R.color.ag_sub0)).apply {
            gravity = Gravity.CENTER
            contentDescription = "Close stat explanation"
            setOnClickListener { close() }
        }, LinearLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)))
        col.addView(owner, LinearLayout.LayoutParams(-1, -2))
        val title = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        title.addView(label(name, 15f, context.getColor(R.color.ag_text), 900), LinearLayout.LayoutParams(0, -2, 1f))
        title.addView(label("×$factor", 24f, hue, 900), LinearLayout.LayoutParams(-2, -2).apply { marginStart = Ui.dp(context, 8) })
        col.addView(title, LinearLayout.LayoutParams(-1, -2))
        val magnitude = kotlin.math.abs(n).coerceIn(0, 6)
        col.addView(label("●".repeat(magnitude) + "○".repeat(6 - magnitude) + "   ${if (n > 0) "+" else "−"}$magnitude / 6", 13f, hue, 900).apply {
            setPadding(0, Ui.dp(context, 6), 0, Ui.dp(context, 6))
            contentDescription = StatPill.spoken(key, n) + ", out of 6 stages"
        })
        col.addView(label("$name is $factor× its normal value.", 12f, context.getColor(R.color.ag_sub1)))
        col.addView(label("Until switching, fainting or a new opponent.", 12f, context.getColor(R.color.ag_sub1)).apply { setPadding(0, Ui.dp(context, 5), 0, 0) })
        val cardW = min(maxW, Ui.dp(context, 288))
        body.addView(col, LinearLayout.LayoutParams((cardW - pad * 2).coerceAtLeast(1), -2))
        body.measure(MeasureSpec.makeMeasureSpec(cardW - pad * 2, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        if (body.measuredHeight + pad * 2 > maxH) {
            removeView(body)
            scroll = ScrollView(context).apply { addView(body, LayoutParams(-1, -2)) }
            addView(scroll, LayoutParams(-1, -1))
        }
        measure(MeasureSpec.makeMeasureSpec(cardW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST))
    }

    companion object {
        const val MAX_COLUMNS = 4

        /** The list's text sizes to try, in design sp. */
        private val SIZES = floatArrayOf(11f, 10f, 9.5f, 9f)
    }
}
