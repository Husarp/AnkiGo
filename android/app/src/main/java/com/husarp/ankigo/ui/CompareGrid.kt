package com.husarp.ankigo.ui

import com.husarp.ankigo.game.lookNum
import android.content.Context
import android.text.TextUtils
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
internal class CompareLook(ctx: Context, columns: Int) {
    private val cfg = ctx.resources.configuration
    private val wDp = if (cfg.screenWidthDp > 0) cfg.screenWidthDp else 412
    private val hDp = if (cfg.screenHeightDp > 0) cfg.screenHeightDp else 800

    val tight = wDp < 380 || hDp < 520
    val sideDp = 6
    val viewportDp = wDp - 2 * sideDp
    val labelDp = if (tight) 44 else 48
    /** How many columns fit next to the label column: about 5 on a phone, fewer in a small window. */
    private val visible = ((viewportDp - labelDp) / 66).coerceIn(2, 5)
    /** More columns than [visible] leave a sliver of the next one showing, so it is clear the table scrolls. */
    val colDp: Float = (viewportDp - labelDp) / (if (columns > visible) visible + 0.4f else columns.coerceAtLeast(1).toFloat())
    val narrow = colDp < 100f

    val valueSp = if (tight) 12f else 13f
    val subSp = if (tight) 9.5f else 10f
    val labelSp = if (tight) 10f else 11f
    val nameSp = if (tight) 10f else 11f
    val spriteDp = if (tight) 30 else 40
    val rowMinDp = if (tight) 34 else 40
    val headerDp = spriteDp + (if (tight) 34 else 40)
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

/** Section groups whose labels are numeric stats: tapping a row label sorts the columns by it. */
private val SORT_SECTIONS = setOf("Stats", "Base stats", "Build")

/**
 * Builds the scrolling table of one [CompareData] (already in display order). [mode] decides what a number cell shows: in
 * "vs reference" column 0 is the reference (pinned) and the others carry a green/red difference; in "best of all" the best
 * value of a row is gold, the next orange, and so on. Taps go out through [onHead] (position, the head cell) and [onLabel].
 */
internal class CompareGrid(
    private val page: GamePage,
    private val look: CompareLook,
    private val table: CompareData,
    private val mode: CompareMode,
    private val sortKey: String?,
    private val sortDesc: Boolean,
    private val onHead: (Int, View) -> Unit,
    private val onLabel: (CompareRow) -> Unit,
) {
    private val ctx: Context = page
    private fun dp(v: Int) = Ui.dp(ctx, v)
    private fun dp(v: Float) = Ui.dp(ctx, v).roundToInt()
    private fun c(res: Int) = Ui.c(ctx, res)

    private val n = table.columns.size
    private val vs = mode == CompareMode.VS_REFERENCE
    private val diffs = if (vs) CompareModes.diffs(table, 0) else emptyMap()
    private val ranks = if (!vs) CompareModes.rankRows(table) else emptyMap()
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

        whoRow()?.let { rows.addView(it, LinearLayout.LayoutParams(totalPx, -2)) }
        var band = 0
        for (row in table.rows) {
            if (row.section != null) {
                rows.addView(sectionBar(row.section), LinearLayout.LayoutParams(totalPx, -2))
                band = 0
            }
            rows.addView(dataRow(row, if (band++ % 2 == 0) bandA else bandB), LinearLayout.LayoutParams(totalPx, -2))
        }
        rows.addView(View(ctx), LinearLayout.LayoutParams(totalPx, dp(8)))

        val grid = FrameLayout(ctx).apply {
            addView(rows, FrameLayout.LayoutParams(totalPx, -2))
            addView(head, FrameLayout.LayoutParams(totalPx, dp(look.headerDp)))
        }
        hsv.step = colPx
        hsv.addView(grid, FrameLayout.LayoutParams(totalPx, -2))
        hsv.onScroll = { x ->
            for (v in stickyX) v.translationX = x.toFloat()
            onScrolled?.invoke(x, vsv.scrollY)
        }
        vsv.addView(hsv, FrameLayout.LayoutParams(-1, -2))
        vsv.isVerticalScrollBarEnabled = false
        vsv.setOnScrollChangeListener { _, _, y, _, _ ->
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

    /** The reference column's background: the band with a little accent, opaque so scrolled columns don't show through. */
    private fun refBg(band: Int): Int = Bits.blend(c(R.color.ag_mauve), band, 0.13f)

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
            this.text = if (active) text + "\n" + (if (sortDesc) "↓" else "↑") else text
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(2), dp(4))
        }
        addView(label, FrameLayout.LayoutParams(-1, -1))
        addView(line(), FrameLayout.LayoutParams(dp(1), -1, Gravity.END))
        sticky(this, 3f)
        if (row != null) {
            val info = StatInfo.forKey(row.key)
            contentDescription = (if (row.numeric) "Sort by ${row.label}. " else "") + (info?.let { "${it.title}: ${it.text}" } ?: row.label)
            if (row.numeric || info != null) setOnClickListener { onLabel(row) }
            if (info != null) setOnLongClickListener { DetailParts.showInfo(ctx, info); true }
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
                if (n <= 5) MonPreview.show(ctx, picNum, col.shiny, img) else Ui.staticSprite(ctx, picNum, col.shiny, img)
                addView(img, LinearLayout.LayoutParams(dp(look.spriteDp), dp(look.spriteDp)))
                addView(Ui.text(ctx, look.nameSp, 900, R.color.ag_text).apply {
                    text = (if (col.shiny) "★ " else "") + col.name
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(2) })
                if (vs && i == 0) addView(Ui.text(ctx, 9f, 900, R.color.ag_mauve).apply { text = "reference"; maxLines = 1; gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(1) })
                contentDescription = "${col.name}. Tap for options."
                setOnClickListener { onHead(i, this) }
            }
            row.addView(cell, LinearLayout.LayoutParams(colPx, h))
            if (vs && i == 0) { cell.setBackgroundColor(refBg(bandA)); sticky(cell) }
        }
        return FrameLayout(ctx).apply {
            addView(row, FrameLayout.LayoutParams(totalPx, h))
            addView(line(), FrameLayout.LayoutParams(totalPx, dp(1), Gravity.BOTTOM))
        }
    }

    /** Tags (owned) or the "Pokédex" pill, plus the evolved note. Null when there is nothing to show. */
    private fun whoRow(): View? {
        val cells = table.columns.map { col ->
            val box = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(pad, dp(5), pad, dp(3))
            }
            val maxW = colPx - 2 * pad
            val mon = col.mon
            if (mon != null) {
                for (t in MonTags.of(page.data, page.state, mon).take(if (look.narrow) 1 else 2)) {
                    box.addView(MonTags.pill(ctx, t).also { clampWidth(it, maxW) },
                        LinearLayout.LayoutParams(-2, -2).also { it.bottomMargin = dp(3) })
                }
            } else {
                box.addView(Ui.pill(ctx, "DEX").also { it.maxLines = 1 }, LinearLayout.LayoutParams(-2, -2))
            }
            col.note?.let {
                box.addView(Ui.text(ctx, look.subSp, 700, R.color.ag_sub0).apply { text = it; gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(-2, -2).also { p -> p.topMargin = dp(1) })
            }
            box
        }
        if (cells.all { it.childCount == 0 }) return null
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(bandA)
            addView(labelCell("", bandA), LinearLayout.LayoutParams(labelPx, -1))
            cells.forEachIndexed { i, cell ->
                addView(cell, LinearLayout.LayoutParams(colPx, -2))
                pinIfRef(cell, i, bandA)
            }
        }
    }

    private fun clampWidth(v: View, maxPx: Int) {
        when (v) {
            is TextView -> { v.maxWidth = maxPx; v.ellipsize = TextUtils.TruncateAt.END; v.maxLines = 1 }
            is ViewGroup -> for (i in 0 until v.childCount) (v.getChildAt(i) as? TextView)?.let { clampWidth(it, maxPx - dp(24)) }
        }
    }

    private fun sectionBar(title: String): View = LinearLayout(ctx).apply {
        setBackgroundColor(c(R.color.ag_mantle))
        addView(Ui.text(ctx, look.labelSp - 0.5f, 900, R.color.ag_sub0).apply {
            text = title.uppercase() + if (title in SORT_SECTIONS) " · TAP A NAME TO SORT" else ""
            letterSpacing = 0.06f
            setPadding(dp(8), dp(6), dp(8), dp(4))
            stickyX += this
        }, LinearLayout.LayoutParams(-2, -2))
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
                addView(v, LinearLayout.LayoutParams(colPx, -1))
                pinIfRef(v, i, band)
            }
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

    /** The fill of a rank tier in "best of all": gold, orange, light orange; lower ranks stay plain. */
    private fun tierFill(tier: Int, band: Int): Int? = when (tier) {
        0 -> Bits.blend(c(R.color.ag_yellow), band, 0.5f)
        1 -> Bits.blend(c(R.color.ag_peach), band, 0.36f)
        2 -> Bits.blend(c(R.color.ag_peach), band, 0.16f)
        else -> null
    }

    private fun small(text: String, tone: Int, weight: Int = 700) = Ui.text(ctx, look.subSp, weight, tone).apply { this.text = text; gravity = Gravity.CENTER }

    private fun cellView(row: CompareRow, cell: Cell, i: Int, band: Int, minH: Int): View {
        val box = cellBox(minH)
        when (cell) {
            is Cell.Text -> {
                if (cell.empty) box.addView(dash())
                else {
                    val tier = ranks[row.key]?.getOrNull(i)
                    val diff = diffs[row.key]?.getOrNull(i)
                    val top = tier == 0 || (vs && i == 0 && row.numeric)
                    box.addView(Ui.text(ctx, look.valueSp, if (top) 900 else 700, R.color.ag_text).apply {
                        text = cell.text
                        gravity = Gravity.CENTER
                        // level cells wear their tier colour (plain on a rank fill: the fill would hide it)
                        cell.level?.let { if (tier == null || tier > 2) setTextColor(LevelTier.color(ctx, it)) }
                    }, LinearLayout.LayoutParams(-2, -2))
                    if (vs && row.numeric) {
                        if (i == 0) box.addView(small("ref", R.color.ag_sub0), LinearLayout.LayoutParams(-2, -2))
                        else if (diff != null) box.addView(small(CompareModes.diffLabel(diff), when (CompareModes.diffSign(diff)) {
                            1 -> R.color.ag_green; -1 -> R.color.ag_red; else -> R.color.ag_sub0
                        }, 900), LinearLayout.LayoutParams(-2, -2))
                    }
                    if (tier != null) tierFill(tier, band)?.let { box.setBackgroundColor(it) }
                    for (s in cell.sub) box.addView(small(s, R.color.ag_sub0), LinearLayout.LayoutParams(-2, -2).also { it.topMargin = dp(1) })
                }
            }
            is Cell.TypeChips -> {
                // narrow columns stack the chips: two don't fit side by side
                if (look.narrow && cell.types.size > 1) {
                    cell.types.forEachIndexed { k, t ->
                        box.addView(Ui.typeChip(ctx, t), LinearLayout.LayoutParams(-2, -2).also { if (k > 0) it.topMargin = dp(3) })
                    }
                } else box.addView(Bits.typeRow(ctx, cell.types), LinearLayout.LayoutParams(-2, -2))
            }
            is Cell.Moves -> {
                if (cell.moves.isEmpty()) box.addView(Ui.text(ctx, look.valueSp, 700, R.color.ag_sub0).apply { text = "none" })
                cell.moves.forEachIndexed { k, m ->
                    box.addView(Ui.text(ctx, look.subSp + 0.5f, 800, R.color.ag_text).apply {
                        text = m.name; maxLines = 2; gravity = Gravity.CENTER
                    }, LinearLayout.LayoutParams(-2, -2).also { if (k > 0) it.topMargin = dp(6) })
                    box.addView(LinearLayout(ctx).apply {
                        orientation = if (look.narrow) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        m.type?.let { addView(Ui.typeChip(ctx, it)) }
                        addView(Ui.text(ctx, look.subSp, 700, R.color.ag_sub0).apply { text = "Pow ${m.power}" },
                            LinearLayout.LayoutParams(-2, -2).also { if (m.type != null) { if (look.narrow) it.topMargin = dp(1) else it.marginStart = dp(4) } })
                    }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = dp(1) })
                }
            }
            is Cell.Matchups -> {
                if (cell.entries.isEmpty()) box.addView(dash())
                val stacked = look.narrow
                val perLine = if (stacked) 1 else ((colPx - 2 * pad) / dp(78)).coerceAtLeast(1)
                for (chunk in cell.entries.chunked(perLine)) {
                    box.addView(LinearLayout(ctx).apply {
                        orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        chunk.forEachIndexed { k, m ->
                            addView(Ui.typeChip(ctx, m.type), LinearLayout.LayoutParams(-2, -2).also { if (k > 0 && !stacked) it.marginStart = dp(8) })
                            val tone = if (m.mult > 1.0) R.color.ag_red else if (m.mult == 0.0) R.color.ag_sub0 else R.color.ag_green
                            addView(Ui.text(ctx, look.subSp, 900, tone).apply { text = m.label },
                                LinearLayout.LayoutParams(-2, -2).also { if (!stacked) it.marginStart = dp(3) })
                        }
                    }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = dp(if (stacked) 4 else 2) })
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
