package com.husarp.ankigo.ui

import com.husarp.ankigo.game.lookNum
import android.content.Context
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.R
import kotlin.math.min
import kotlin.math.roundToInt

// PLAN item 53 (R3-V5, pick 5A): the Compare table. Pokémon are columns, stats are rows; the label column (and in "vs reference"
// the reference column) stay put while the rest scrolls sideways; the column heads stay at the top while it scrolls down.

/** Sizes of the Compare table, from the window: tighter in split screen and small floating windows. */
/** [headNotes]: some column head carries a small note line ("from Pikachu" in "Fully evolved"); the heads then get one line more, in every column. */
internal class CompareLook(ctx: Context, columns: Int, val fit: Int = CompareFit.AUTO, headNotes: Boolean = false) {
    private val cfg = ctx.resources.configuration
    private val wDp = if (cfg.screenWidthDp > 0) cfg.screenWidthDp else 412
    private val hDp = if (cfg.screenHeightDp > 0) cfg.screenHeightDp else 800

    val tight = wDp < 380 || hDp < 520
    val sideDp = 6
    val viewportDp = wDp - 2 * sideDp
    /** R4-X1 item 8: wide enough for "Resists" / "Immune" on one line. */
    val labelDp = if (tight) 52 else 56
    /**
     * How many columns fit next to the label column: about 5 on a phone, fewer in a small window, or the zoom the player chose
     * ([fit], R4-X1 item 12). More columns than fit leave a sliver of the next one showing, so it is clear the table scrolls.
     */
    val colDp: Float = CompareSizing.colDp(viewportDp, labelDp, columns, fit)
    val narrow = colDp < 100f
    /** Type chips (symbol + name) are wider than the old text chips: two do not fit side by side in a column under 150 dp. */
    val stackTypes = narrow || (colDp < 150f && AppPrefs.typeStyle(ctx) != TypeStyle.ICON)

    /** Text, sprites and row heights follow the chosen zoom (1 in automatic mode). */
    private val zoom = CompareSizing.zoom(colDp, fit)
    val valueSp = (if (tight) 12f else 13f) * zoom
    val subSp = (if (tight) 9.5f else 10f) * zoom
    val labelSp = if (tight) 10f else 11f
    val nameSp = (if (tight) 10f else 11f) * zoom
    val spriteDp = ((if (tight) 30 else 40) * zoom).roundToInt()
    val rowMinDp = ((if (tight) 34 else 40) * zoom).roundToInt()
    val headerDp = spriteDp + ((if (tight) 34 else 40) * zoom).roundToInt() + if (headNotes) (12 * zoom).roundToInt() else 0
}

/** A horizontal scroller that stops on whole columns ([step] px) and reports its position. */
private class SnapScroll(ctx: Context) : HorizontalScrollView(ctx) {
    var step = 0
    var onScroll: ((Int) -> Unit)? = null
    private var flung = false

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
    }

    private fun maxScroll(): Int = ((getChildAt(0)?.width ?: 0) - width).coerceAtLeast(0)

    private fun snap(x: Float): Int =
        if (step <= 0) x.roundToInt() else ((x / step).roundToInt() * step).coerceIn(0, maxScroll())

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScroll?.invoke(l)
    }

    override fun fling(velocityX: Int) {
        flung = true
        val dir = if (velocityX > 0) 1 else -1
        smoothScrollTo(snap(scrollX + dir * step * 0.5f + dir), 0)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) flung = false
        val r = super.onTouchEvent(ev)
        if ((ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) && !flung) {
            smoothScrollTo(snap(scrollX.toFloat()), 0)
        }
        return r
    }
}

/** R10-F (B9): the page-background gap above every section header (except the first thing in the table). */
internal const val SECTION_GAP_DP = 11

/** Section groups whose labels are numeric stats: tapping a row label sorts the columns by it. */
private val SORT_SECTIONS = setOf("Stats", "Base stats", "Build")

/**
 * Builds the scrolling table of one [CompareData] (already in display order). [mode] decides what a number cell shows: in
 * "vs reference" column 0 is the reference (pinned) and the others carry a green/red difference; in "best of all" every number
 * cell is coloured on a red (worst) to green (best) scale by its value. Taps go out through [onHead] (position, the head cell)
 * and [onLabel] (the row, its label cell); a long press on a column head lifts the column and [onMove] (from, to) reports
 * where it was dropped.
 */
internal class CompareGrid(
    private val page: GamePage,
    private val look: CompareLook,
    private val table: CompareData,
    private val mode: CompareMode,
    private val sortKey: String?,
    private val sortDesc: Boolean,
    private val onHead: (Int, View) -> Unit,
    private val onLabel: (CompareRow, View) -> Unit,
    private val onMove: (Int, Int) -> Unit = { _, _ -> },
    /** R10-F (B9): the sections that are folded (their rows are not built) and what a tap on a section header does (gets the title). */
    private val collapsed: SectionChoice = SectionChoice(),
    private val onSection: (String) -> Unit = {},
    /** R10-G (B12): a long press on a row label (a number row: the sort menu; another row: its info). */
    private val onLabelLong: (CompareRow, View) -> Unit = { _, _ -> },
) {
    private val ctx: Context = page
    private fun dp(v: Int) = Ui.dp(ctx, v)
    private fun dp(v: Float) = Ui.dp(ctx, v).roundToInt()
    private fun c(res: Int) = Ui.c(ctx, res)

    private val n = table.columns.size
    private val vs = mode == CompareMode.VS_REFERENCE
    private val diffs = if (vs) CompareModes.diffs(table, 0) else emptyMap()
    private val scales = if (!vs) CompareModes.scaleRows(table) else emptyMap()
    /** The views of every column (head, tags, cells), so a dragged column moves as one. */
    private val colViews = Array(n) { ArrayList<View>() }
    private val labelPx = dp(look.labelDp)
    private val colPx = dp(look.colDp)
    private val totalPx = labelPx + n * colPx
    private val pad = dp(if (look.narrow) 2 else 6)
    private val bandA = c(R.color.ag_card)
    private val bandB = Bits.blend(c(R.color.ag_mantle), c(R.color.ag_card), 0.55f)

    private val stickyX = ArrayList<View>()
    private val hsv = SnapScroll(ctx)
    private val vsv = ScrollView(ctx)
    private lateinit var head: View
    var onScrolled: ((Int, Int) -> Unit)? = null

    val root: View = build()

    fun restore(x: Int, y: Int) {
        hsv.post { hsv.scrollTo(x, 0); vsv.scrollTo(0, y) }
    }

    private fun build(): View {
        val rows = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        // the spacer is where the sticky head sits at the top
        rows.addView(View(ctx), LinearLayout.LayoutParams(totalPx, dp(look.headerDp)))
        head = headRow()

        var band = 0
        var open = true // rows before the first section header (level, types) are not in a section and always show
        for (row in table.rows) {
            if (row.section != null) {
                // R10-F (B9): a gap of page background above every header but the first thing in the table; the header's rows follow unless it is folded
                if (rows.childCount > 1) rows.addView(View(ctx), LinearLayout.LayoutParams(totalPx, dp(SECTION_GAP_DP)))
                open = collapsed.isOpen(row.section)
                rows.addView(sectionBar(row.section, open), LinearLayout.LayoutParams(totalPx, -2))
                band = 0
            }
            if (open) rows.addView(dataRow(row, if (band++ % 2 == 0) bandA else bandB), LinearLayout.LayoutParams(totalPx, -2))
        }
        rows.addView(View(ctx), LinearLayout.LayoutParams(totalPx, dp(8)))

        val grid = FrameLayout(ctx).apply {
            addView(rows, FrameLayout.LayoutParams(totalPx, -2))
            addView(head, FrameLayout.LayoutParams(totalPx, dp(look.headerDp)))
        }
        hsv.step = colPx
        hsv.addView(grid, FrameLayout.LayoutParams(totalPx, -2))
        hsv.onScroll = { x ->
            if (CompareMovePopup.isShowing) CompareMovePopup.dismiss()
            for (v in stickyX) v.translationX = x.toFloat()
            onScrolled?.invoke(x, vsv.scrollY)
        }
        vsv.addView(hsv, FrameLayout.LayoutParams(-1, -2))
        vsv.isVerticalScrollBarEnabled = false
        vsv.setOnScrollChangeListener { _, _, y, _, _ ->
            if (CompareMovePopup.isShowing) CompareMovePopup.dismiss()
            head.translationY = y.toFloat()
            onScrolled?.invoke(hsv.scrollX, y)
        }
        return vsv
    }

    // ---------- pieces ----------

    private fun line(): View = View(ctx).apply { setBackgroundColor(c(R.color.ag_s0)) }

    /** Keeps [v] at its place while the table scrolls sideways, drawn over the columns that pass under it. */
    private fun sticky(v: View, z: Float = 2f) {
        stickyX += v
        v.z = z
        v.outlineProvider = null
    }

    /** The reference column's background (R10-F B8 / B11): the band with a neutral grey mixed in, opaque so scrolled columns don't show through. Only behind its cells. */
    private fun refBg(band: Int): Int = CompareScale.neutral(band, c(R.color.ag_ov))

    /** [v] is the cell of column [i] in a row on [band]: pinned and tinted when it is the reference column. */
    private fun pinIfRef(v: View, i: Int, band: Int) {
        if (!vs || i != 0) return
        v.setBackgroundColor(refBg(band))
        sticky(v)
    }

    private fun labelCell(text: String, bg: Int, row: CompareRow? = null): View = FrameLayout(ctx).apply {
        setBackgroundColor(bg)
        val active = row != null && row.key == sortKey
        val label = Ui.text(ctx, look.labelSp, 800, if (active) R.color.ag_mauve else R.color.ag_sub0).apply {
            this.text = text
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(2), if (active) dp(1) else dp(4))
            hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
            // R4-X1 item 8: a word is never broken in the middle: the text shrinks until its longest word fits the column
            val word = CompareLabelFit.longestWord(this.text.toString())
            val avail = labelPx - dp(6) - dp(2) - dp(1)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, CompareLabelFit.sp(textSize, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 8f, ctx.resources.displayMetrics), paint.measureText(word), avail.toFloat()))
        }
        if (active) {
            // R6-05 item 6: the direction is a drawn icon under the name (not a text arrow)
            val arrow = ImageView(ctx).apply {
                setImageResource(if (sortDesc) R.drawable.ic_sort_desc else R.drawable.ic_sort_asc)
                setColorFilter(c(R.color.ag_mauve), android.graphics.PorterDuff.Mode.SRC_IN)
            }
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(label, LinearLayout.LayoutParams(-1, -2))
                addView(arrow, LinearLayout.LayoutParams(dp(14), dp(14)).also { it.marginStart = dp(6); it.bottomMargin = dp(3) })
            }, FrameLayout.LayoutParams(-1, -1))
        } else addView(label, FrameLayout.LayoutParams(-1, -1))
        addView(line(), FrameLayout.LayoutParams(dp(1), -1, Gravity.END))
        sticky(this, 3f)
        if (row != null) {
            val info = StatInfo.forKey(row.key)
            val says = if (row.numeric) CompareSortCycle.tapSays(sortKey.takeIf { active }, sortDesc, row.key) + " Hold for the sort menu." else "Tap or hold to read what it is."
            contentDescription = (if (row.numeric) "Sort by ${row.label}. " else "") + (if (active) (if (sortDesc) "Sorted high to low. " else "Sorted low to high. ") else "") +
                (info?.let { "${it.title}: ${it.text.trimEnd('.')}. " } ?: "${row.label}. ") + says
            setOnClickListener { onLabel(row, this) }
            setOnLongClickListener { onLabelLong(row, this); true }
        }
    }

    private fun headRow(): View {
        val h = dp(look.headerDp)
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(bandA) }
        row.addView(FrameLayout(ctx).apply {
            setBackgroundColor(bandA)
            addView(line(), FrameLayout.LayoutParams(dp(1), -1, Gravity.END))
            sticky(this, 3f)
        }, LinearLayout.LayoutParams(labelPx, h))
        table.columns.forEachIndexed { i, col ->
            val cell = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(pad, dp(4), pad, 0)
                val img = ImageView(ctx)
                // animated previews only for a few columns; dozens of decoded GIFs would cost memory
                // an owned Pokémon shows its chosen look; the "Fully evolved" preview of a Pokémon that evolves shows the evolved form
                val picNum = if (col.mon != null && col.shown.num == col.picked.num) col.mon.lookNum(com.husarp.ankigo.GameRepo.data) else col.shown.num
                if (n <= 5) MonPreview.show(ctx, picNum, col.shiny, img, silhouette = col.hidden) else Ui.staticSprite(ctx, picNum, col.shiny, img, silhouette = col.hidden)
                addView(img, LinearLayout.LayoutParams(dp(look.spriteDp), dp(look.spriteDp)))
                addView(Ui.text(ctx, look.nameSp, 900, R.color.ag_text).apply {
                    text = (if (col.shiny) "★ " else "") + col.name
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(2) })
                if (vs && i == 0) addView(Ui.text(ctx, 9f, 900, R.color.ag_mauve).apply { text = "reference"; maxLines = 1; gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(1) })
                // R6-05 item 8: no tags under the heads; the "Fully evolved" note is one small line here (all heads are as tall as it needs)
                col.note?.let {
                    addView(Ui.text(ctx, 9f, 700, R.color.ag_sub0).apply { text = it; maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER },
                        LinearLayout.LayoutParams(-1, -2).also { p -> p.topMargin = dp(1) })
                }
                contentDescription = "${col.name}. Tap for options." + if (canDrag(i)) " Hold and drag to move." else ""
                setOnClickListener { onHead(i, this) }
                if (canDrag(i)) wireDrag(this, i)
            }
            colViews[i].add(cell)
            row.addView(cell, LinearLayout.LayoutParams(colPx, h))
            if (vs && i == 0) { cell.setBackgroundColor(refBg(bandA)); sticky(cell) }
        }
        return FrameLayout(ctx).apply {
            addView(row, FrameLayout.LayoutParams(totalPx, h))
            addView(line(), FrameLayout.LayoutParams(totalPx, dp(1), Gravity.BOTTOM))
        }
    }

    /**
     * R10-F (B9): a section header (a bigger title than the row labels, a chevron for open / folded), tappable to fold its rows. The title
     * stays where it is while the table scrolls sideways. The reference column's tint does NOT run through it (B11).
     */
    private fun sectionBar(title: String, open: Boolean): View {
        val mantle = c(R.color.ag_mantle)
        return FrameLayout(ctx).apply {
            setBackgroundColor(mantle)
            minimumHeight = dp(40)
            val name = SpannableStringBuilder(title.uppercase()).apply {
                setSpan(ForegroundColorSpan(c(R.color.ag_text)), 0, length, 0)
                if (title in SORT_SECTIONS) {
                    val start = length
                    append(" · TAP A NAME TO SORT")
                    setSpan(RelativeSizeSpan(0.68f), start, length, 0)
                    setSpan(ForegroundColorSpan(c(R.color.ag_sub0)), start, length, 0)
                }
            }
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                addView(ImageView(ctx).apply {
                    setImageResource(if (open) R.drawable.ic_expand else R.drawable.ic_chevron)
                    setColorFilter(c(R.color.ag_mauve), android.graphics.PorterDuff.Mode.SRC_IN)
                }, LinearLayout.LayoutParams(dp(20), dp(20)).also { it.marginEnd = dp(4) })
                addView(Ui.text(ctx, look.labelSp + 3f, 900, R.color.ag_text).apply {
                    text = name
                    letterSpacing = 0.05f
                    maxLines = 1
                }, LinearLayout.LayoutParams(-2, -2))
                sticky(this, 3f)
            }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL))
            contentDescription = "$title, ${if (open) "expanded" else "collapsed"}"
            isClickable = true
            setOnClickListener { onSection(title) }
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = android.widget.Button::class.java.name // TalkBack: a button, "Stats, expanded"
                }
            }
        }
    }

    private fun dataRow(row: CompareRow, band: Int): View {
        val minH = dp(look.rowMinDp)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            minimumHeight = minH
            setBackgroundColor(band)
            addView(labelCell(row.label, band, row), LinearLayout.LayoutParams(labelPx, -1))
            row.cells.forEachIndexed { i, cell ->
                val v = cellView(row, cell, i, band, minH)
                colViews[i].add(v)
                addView(v, LinearLayout.LayoutParams(colPx, -1))
                pinIfRef(v, i, band)
            }
        }
    }

    // ---------- what a tap on an icon says (R4-X1 item 9) ----------

    private fun tapText(v: View, text: String) {
        v.contentDescription = text
        v.setOnClickListener { Ui.toast(ctx, text) }
    }

    /**
     * R6-05 item 10: a type chip says its name already, so a tap on it shows nothing (the icon-only style keeps the chip's own
     * "name" popup). The name stays as the spoken description. Text is shrunk a little before it would be cut ([fitChip]).
     */
    private fun typeTap(v: View, t: com.husarp.ankigo.game.Type, maxPx: Int = colPx - 2 * pad): View = v.also {
        it.contentDescription = CompareInfo.typeText(t)
        (it as? TextView)?.let { tv -> fitChip(tv, maxPx) }
    }

    /** R6-05 item 9: the chip shows its full name: the text shrinks (down to 7 sp) until chip + symbol fit [maxPx], before it could ever get a "…". */
    private fun fitChip(tv: TextView, maxPx: Int) {
        if (tv.text.isNullOrEmpty()) return // icon-only style: nothing to cut
        val min = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 7f, ctx.resources.displayMetrics)
        fun need(): Float = tv.paint.measureText(tv.text.toString()) + tv.paddingLeft + tv.paddingRight + tv.compoundDrawablePadding +
            (tv.compoundDrawablesRelative[0]?.bounds?.width() ?: 0)
        var size = tv.textSize
        while (need() > maxPx && size > min) { size = (size - 0.5f).coerceAtLeast(min); tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, size) }
    }

    // ---------- long-press drag of a column (R4-X1 item 7) ----------

    /** The first place a column can be dragged to: the reference column of "vs reference" stays at 0. */
    private val minPos get() = if (vs) 1 else 0

    /** True when the column at [pos] can be dragged (there is at least one other movable column). */
    internal fun canDrag(pos: Int): Boolean = pos >= minPos && n - minPos >= 2

    private var dragFrom = -1
    private var dragTarget = -1
    private var dragStartX = 0f
    private var dragRawX = 0f
    private val hsvPos = IntArray(2)

    /** The screen x of [rawX] in the table's own coordinates (scroll included). */
    private fun gridX(rawX: Float): Float {
        hsv.getLocationOnScreen(hsvPos)
        return rawX - hsvPos[0] + hsv.scrollX
    }

    private fun wireDrag(head: View, pos: Int) {
        var lastRaw = 0f
        head.setOnTouchListener { v, e ->
            if (dragFrom != pos) { lastRaw = e.rawX; false }
            else when (e.actionMasked) {
                MotionEvent.ACTION_MOVE -> { dragTo(e.rawX); true }
                MotionEvent.ACTION_UP -> { endDrag(true); true }
                MotionEvent.ACTION_CANCEL -> { endDrag(false); true }
                else -> true
            }
        }
        head.setOnLongClickListener { v ->
            val on = beginDrag(pos, lastRaw)
            if (on) v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            on
        }
    }

    /** The column at [pos] lifts (accent tint) and starts to follow the finger. */
    internal fun beginDrag(pos: Int, rawX: Float): Boolean {
        if (!canDrag(pos) || dragFrom >= 0) return false
        dragFrom = pos; dragTarget = pos; dragStartX = gridX(rawX); dragRawX = rawX
        val tint = (c(R.color.ag_mauve) and 0x00FFFFFF) or (0x4A shl 24)
        for (v in colViews[pos]) { v.foreground = android.graphics.drawable.ColorDrawable(tint); v.translationZ = 1f }
        hsv.requestDisallowInterceptTouchEvent(true)
        vsv.requestDisallowInterceptTouchEvent(true)
        root.post(scrollTick)
        return true
    }

    /** The finger is at [rawX]: the lifted column follows it, the others step aside to make room. */
    internal fun dragTo(rawX: Float) {
        val from = dragFrom
        if (from < 0) return
        dragRawX = rawX
        val home = (labelPx + from * colPx).toFloat()
        val left = (home + gridX(rawX) - dragStartX).coerceIn((labelPx + minPos * colPx).toFloat(), (labelPx + (n - 1) * colPx).toFloat())
        for (v in colViews[from]) v.translationX = left - home
        val t = CompareDrag.target(left, labelPx.toFloat(), colPx.toFloat(), n, minPos)
        if (t == dragTarget) return
        dragTarget = t
        for (j in 0 until n) {
            if (j == from) continue
            val to = CompareDrag.shift(j, from, t) * colPx.toFloat()
            for (v in colViews[j]) v.animate().translationX(to).setDuration(120).start()
        }
    }

    /** The finger is up: the column is dropped at its place ([commit]) and [onMove] hears of it, or the drag is cancelled. */
    internal fun endDrag(commit: Boolean) {
        val from = dragFrom
        if (from < 0) return
        val target = dragTarget
        dragFrom = -1; dragTarget = -1
        root.removeCallbacks(scrollTick)
        for (list in colViews) for (v in list) { v.animate().cancel(); v.translationX = 0f; v.translationZ = 0f; v.foreground = null }
        if (commit && target != from) onMove(from, target)
    }

    /** While a column is held near the left or right edge, the table scrolls so it can travel further. */
    private val scrollTick = object : Runnable {
        override fun run() {
            if (dragFrom < 0) return
            hsv.getLocationOnScreen(hsvPos)
            val sticky = (labelPx + if (vs) colPx else 0).toFloat()
            val step = CompareDrag.autoScroll(dragRawX - hsvPos[0], hsv.width.toFloat(), sticky, dp(48).toFloat(), dp(14).toFloat())
            if (step != 0f) { hsv.scrollBy(step.toInt(), 0); dragTo(dragRawX) }
            root.postOnAnimation(this)
        }
    }

    // ---------- cells ----------

    private fun cellBox(minH: Int): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        minimumHeight = minH
        setPadding(pad, dp(4), pad, dp(4))
    }

    private fun dash(): TextView = Ui.text(ctx, look.valueSp, 700, R.color.ag_ov).apply { text = DASH; gravity = Gravity.CENTER }

    /** R9-W3-A (D7-A): the look of a value in "best of all": [pos] 0 = the row's worst value .. 1 = its best; the middle stays the plain [band]. */
    private fun scaleLook(pos: Float, band: Int): CompareScale.Look = CompareScale.look(pos, band, TypeChips.isDark(ctx))

    private fun small(text: String, tone: Int, weight: Int = 700) = Ui.text(ctx, look.subSp, weight, tone).apply { this.text = text; gravity = Gravity.CENTER }

    private fun cellView(row: CompareRow, cell: Cell, i: Int, band: Int, minH: Int): View {
        val box = cellBox(minH)
        when (cell) {
            is Cell.Text -> {
                if (cell.empty) box.addView(dash())
                else {
                    val pos = scales[row.key]?.getOrNull(i)
                    val diff = diffs[row.key]?.getOrNull(i)
                    val top = pos == 1f || (vs && i == 0 && row.numeric)
                    // R10-F (B8): in "vs reference" a number is filled by how much better / worse it is than the reference (relative, not ranked)
                    val refValue = if (vs && row.numeric) (row.cells.getOrNull(0) as? Cell.Text)?.value else null
                    val scale = pos?.let { scaleLook(it, band) }
                        ?: if (refValue != null && i != 0 && cell.value != null) CompareScale.vsRef(cell.value, refValue, band, TypeChips.isDark(ctx)) else null
                    box.addView(Ui.text(ctx, look.valueSp, if (top) 900 else 700, R.color.ag_text).apply {
                        text = cell.text
                        gravity = Gravity.CENTER
                        // level cells wear their tier colour (plain on a value fill: the fill would hide it)
                        cell.level?.let { if (pos == null && scale?.fill == null) setTextColor(LevelTier.color(ctx, it)) }
                        scale?.ink?.let { setTextColor(it) } // a strong fill takes white / dark ink
                    }, LinearLayout.LayoutParams(-2, -2))
                    if (vs && row.numeric) {
                        if (i == 0) box.addView(small("ref", R.color.ag_sub0), LinearLayout.LayoutParams(-2, -2))
                        else if (CompareModes.diffLabel(diff).isNotEmpty()) box.addView(small(CompareModes.diffLabel(diff), when (CompareModes.diffSign(diff)) {
                            1 -> R.color.ag_green; -1 -> R.color.ag_red; else -> R.color.ag_sub0
                        }, 900).also { tv -> scale?.ink?.let { tv.setTextColor(it) } }, LinearLayout.LayoutParams(-2, -2)) // an equal value shows nothing (no "±0"); on a strong fill it takes the fill's ink
                    }
                    scale?.fill?.let { box.setBackgroundColor(it) }
                    for (s in cell.sub) box.addView(small(s, R.color.ag_sub0), LinearLayout.LayoutParams(-2, -2).also { it.topMargin = dp(1) })
                }
            }
            is Cell.TypeChips -> {
                // narrow columns stack the chips: two don't fit side by side
                if (look.stackTypes && cell.types.size > 1) {
                    cell.types.forEachIndexed { k, t ->
                        box.addView(typeTap(Ui.typeChip(ctx, t), t), LinearLayout.LayoutParams(-2, -2).also { if (k > 0) it.topMargin = dp(3) })
                    }
                } else box.addView(Bits.typeRow(ctx, cell.types).also { r -> for (k in 0 until r.childCount) typeTap(r.getChildAt(k), cell.types[k], (colPx - 2 * pad) / cell.types.size - dp(3)) }, LinearLayout.LayoutParams(-2, -2))
            }
            is Cell.Moves -> {
                if (cell.moves.isEmpty()) box.addView(Ui.text(ctx, look.valueSp, 700, R.color.ag_sub0).apply { text = "none" })
                // R10-F (B10): a move shows its name and its type only; a tap opens the move's popup (name, type, category, power, accuracy, effect)
                cell.moves.forEachIndexed { k, m ->
                    box.addView(LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        addView(Ui.text(ctx, look.subSp + 0.5f, 800, R.color.ag_text).apply {
                            text = m.name; maxLines = 2; gravity = Gravity.CENTER
                            hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
                            // a word is not broken in the middle: it shrinks (not below 7 sp) until it fits the column
                            setTextSize(TypedValue.COMPLEX_UNIT_PX, CompareLabelFit.sp(textSize, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 7f, ctx.resources.displayMetrics),
                                paint.measureText(CompareLabelFit.longestWord(m.name)), (colPx - 2 * pad).toFloat()))
                        }, LinearLayout.LayoutParams(-2, -2))
                        // R6-05 item 9: the chip gets wrap-content and the room that is left
                        m.type?.let { addView(typeTap(Ui.typeChip(ctx, it), it, colPx - 2 * pad), LinearLayout.LayoutParams(-2, -2).also { p -> p.topMargin = dp(2) }) }
                        val mv = page.data.move(m.id)
                        if (mv != null) {
                            contentDescription = CompareMovePopup.said(mv) + ". Tap for details"
                            setOnClickListener { CompareMovePopup.show(it, mv) }
                        }
                    }, LinearLayout.LayoutParams(-2, -2).also { if (k > 0) it.topMargin = dp(6) })
                }
            }
            is Cell.Matchups -> {
                if (cell.entries.isEmpty()) box.addView(dash())
                else {
                    // R9-W3-A (D8-V1): one box per multiplier of this row; pill only (the row label says the words)
                    val room = colPx - 2 * pad
                    val name = table.columns.getOrNull(i)?.name ?: ""
                    MatchupGroups.of(cell.entries).forEachIndexed { k, (g, types) ->
                        box.addView(MatchupBoxes.box(ctx, g, types, name, compact = true, roomPx = room, textSp = look.subSp + 1f, chipFit = { chip, w -> fitChip(chip, w) }),
                            LinearLayout.LayoutParams(-1, -2).also { if (k > 0) it.topMargin = dp(3) })
                    }
                }
            }
            is Cell.Evo -> {
                cell.names.forEachIndexed { k, name ->
                    val mark = k == cell.mark
                    box.addView(Ui.text(ctx, look.subSp + 1f, if (mark) 900 else 700, if (mark) R.color.ag_mauve else R.color.ag_sub1).apply {
                        text = (if (k > 0) "↓ " else "") + name
                        gravity = Gravity.CENTER
                    }, LinearLayout.LayoutParams(-2, -2).also { if (k > 0) it.topMargin = dp(1) })
                }
                cell.note?.let {
                    box.addView(Ui.text(ctx, look.subSp, 700, R.color.ag_sub0).apply { text = it; gravity = Gravity.CENTER },
                        LinearLayout.LayoutParams(-2, -2).also { p -> p.topMargin = dp(3) })
                }
            }
        }
        return box
    }
}
