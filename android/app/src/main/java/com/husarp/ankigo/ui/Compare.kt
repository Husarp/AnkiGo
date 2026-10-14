package com.husarp.ankigo.ui

import android.app.AlertDialog
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

/** One Pokémon picked for Compare: an owned one (by uid) or a Pokédex entry (by species number). */
sealed class ComparePick {
    data class Owned(val uid: String) : ComparePick()
    data class Dex(val species: Int) : ComparePick()
}

/** Compare 2–4 Pokémon side by side (PLAN item 9): a large dialog with a table and a "Now / Fully evolved" switch. */
object Compare {
    const val MIN = 2
    const val MAX = 4

    /** Opens the comparison of [picks] (MIN..MAX entries) from [page]. */
    fun open(page: GamePage, picks: List<ComparePick>) {
        val view = CompareView(page, picks.take(MAX))
        if (view.columns < MIN) {
            Ui.toast(page, "Pick at least $MIN Pokémon to compare")
            return
        }
        val dlg = AlertDialog.Builder(page).setView(view).setPositiveButton("Close", null).create()
        Pickers.show(dlg)
        // a real dialog: nearly the full width of the window (the render tests size their own scene)
        if (Pickers.intercept == null) dlg.window?.setLayout(Ui.dp(page, view.look.dialogWDp), ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}

/** Sizes of the Compare table, from the window: tighter in split screen and small floating windows. */
internal class CompareLook(ctx: Context, columns: Int) {
    private val cfg = ctx.resources.configuration
    private val wDp = if (cfg.screenWidthDp > 0) cfg.screenWidthDp else 412
    private val hDp = if (cfg.screenHeightDp > 0) cfg.screenHeightDp else 800

    val tight = wDp < 380 || hDp < 520
    val dialogWDp = min(wDp - 16, 640)
    val viewportDp = dialogWDp - 16
    val labelDp = if (tight) 54 else 62
    private val visible = ((viewportDp - labelDp) / 150).coerceIn(2, 4)
    /** Columns beyond [visible] leave a sliver of the next one showing, so it is clear the table scrolls. */
    val colDp: Float = (viewportDp - labelDp) / (if (columns > visible) visible + 0.3f else min(columns, visible).toFloat())
    val heightDp = (hDp - (if (tight) 40 else 170)).coerceIn(200, 760)

    val valueSp = if (tight) 11f else 13f
    val subSp = if (tight) 10f else 10.5f
    val labelSp = if (tight) 10f else 11f
    val nameSp = if (tight) 11f else 13f
    val titleSp = if (tight) 16f else 18f
    val spriteDp = if (tight) 32 else 52
    val rowMinDp = if (tight) 30 else 36
    val headerDp = spriteDp + (if (tight) 20 else 28)
}

/** The content of the dialog: title + Now / Fully evolved switch + the scrolling table. */
internal class CompareView(private val page: GamePage, private val picks: List<ComparePick>) : LinearLayout(page) {
    val columns: Int = CompareTable.build(page.data, page.state, picks, false).columns.size
    val look = CompareLook(page, columns)

    private var evolved = false
    private val body = FrameLayout(page)
    private val nowPill = pillView("Now")
    private val finalPill = pillView("Fully evolved")
    private var lastX = 0
    private var lastY = 0

    private fun dp(v: Int) = Ui.dp(page, v)
    private fun c(res: Int) = Ui.c(page, res)

    init {
        orientation = VERTICAL
        setPadding(dp(8), dp(4), dp(8), 0)
        val head = LinearLayout(page).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, 0, 0)
            addView(Ui.text(page, look.titleSp, 900, R.color.ag_text).apply { text = "Compare"; maxLines = 1 },
                LayoutParams(0, -2, 1f))
            addView(nowPill, LayoutParams(-2, -2))
            addView(finalPill, LayoutParams(-2, -2).also { it.marginStart = dp(2) })
        }
        addView(head, LayoutParams(-1, -2))
        addView(body, LayoutParams(-1, 0, 1f))
        nowPill.setOnClickListener { select(false) }
        finalPill.setOnClickListener { select(true) }
        render()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val want = dp(look.heightDp)
        val mode = MeasureSpec.getMode(heightMeasureSpec)
        val h = if (mode == MeasureSpec.UNSPECIFIED) want else min(want, MeasureSpec.getSize(heightMeasureSpec))
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }

    private fun pillView(label: String): TextView = Ui.text(page, 12f, 800, R.color.ag_sub1).apply {
        text = label
        maxLines = 1
    }

    private fun stylePills() {
        for ((pill, on) in listOf(nowPill to !evolved, finalPill to evolved)) {
            val fill = if (on) Bits.blend(c(R.color.ag_mauve), c(R.color.ag_mantle), 0.28f) else c(R.color.ag_mantle)
            pill.setTextColor(if (on) c(R.color.ag_mauve) else c(R.color.ag_sub1))
            pill.typeface = Ui.font(page, if (on) 900 else 800)
            Ui.touchPill(pill, Ui.rounded(fill, 10, page), 30, 10)
        }
    }

    private fun select(toEvolved: Boolean) {
        if (evolved == toEvolved) return
        evolved = toEvolved
        render()
    }

    private fun render() {
        stylePills()
        body.removeAllViews()
        val table = CompareTable.build(page.data, page.state, picks, evolved)
        val grid = TableGrid(page, look, table)
        body.addView(grid.root, FrameLayout.LayoutParams(-1, -1))
        grid.restore(lastX, lastY)
        grid.onScrolled = { x, y -> lastX = x; lastY = y }
    }
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

/** Builds the scrolling table of one [CompareData]: sticky label column and sticky column heads. */
private class TableGrid(private val page: GamePage, private val look: CompareLook, private val table: CompareData) {
    private val ctx: Context = page
    private fun dp(v: Int) = Ui.dp(ctx, v)
    private fun dp(v: Float) = Ui.dp(ctx, v).roundToInt()
    private fun c(res: Int) = Ui.c(ctx, res)

    private val n = table.columns.size
    private val labelPx = dp(look.labelDp)
    private val colPx = dp(look.colDp)
    private val totalPx = labelPx + n * colPx
    private val pad = dp(6)
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

    private fun labelCell(text: String, bg: Int): View = FrameLayout(ctx).apply {
        setBackgroundColor(bg)
        addView(Ui.text(ctx, look.labelSp, 800, R.color.ag_sub0).apply {
            this.text = text
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(2), dp(4))
        }, FrameLayout.LayoutParams(-1, -1))
        addView(line(), FrameLayout.LayoutParams(dp(1), -1, Gravity.END))
        stickyX += this
    }

    private fun headRow(): View {
        val h = dp(look.headerDp)
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(bandA) }
        row.addView(FrameLayout(ctx).apply {
            setBackgroundColor(bandA)
            addView(line(), FrameLayout.LayoutParams(dp(1), -1, Gravity.END))
            stickyX += this
        }, LinearLayout.LayoutParams(labelPx, h))
        for (col in table.columns) {
            val cell = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(pad / 2, dp(4), pad / 2, 0)
                val img = ImageView(ctx)
                MonPreview.show(ctx, col.shown.num, col.shiny, img)
                addView(img, LinearLayout.LayoutParams(dp(look.spriteDp), dp(look.spriteDp)))
                addView(Ui.text(ctx, look.nameSp, 900, R.color.ag_text).apply {
                    text = (if (col.shiny) "★ " else "") + col.name
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(2) })
            }
            row.addView(cell, LinearLayout.LayoutParams(colPx, h))
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
                for (t in MonTags.of(page.data, page.state, mon).take(2)) {
                    box.addView(MonTags.pill(ctx, t).also { clampWidth(it, maxW) },
                        LinearLayout.LayoutParams(-2, -2).also { it.bottomMargin = dp(3) })
                }
            } else {
                box.addView(Ui.pill(ctx, "POKÉDEX").also { it.maxLines = 1 }, LinearLayout.LayoutParams(-2, -2))
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
            cells.forEach { addView(it, LinearLayout.LayoutParams(colPx, -2)) }
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
            text = title.uppercase()
            letterSpacing = 0.06f
            setPadding(dp(8), dp(6), dp(8), dp(4))
            stickyX += this
        }, LinearLayout.LayoutParams(-2, -2))
    }

    private fun dataRow(row: CompareRow, bg: Int): View {
        val minH = dp(look.rowMinDp)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            minimumHeight = minH
            setBackgroundColor(bg)
            addView(labelCell(row.label, bg), LinearLayout.LayoutParams(labelPx, -1))
            for (cell in row.cells) addView(cellView(cell, minH), LinearLayout.LayoutParams(colPx, -2))
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

    private fun cellView(cell: Cell, minH: Int): View {
        val box = cellBox(minH)
        when (cell) {
            is Cell.Text -> {
                if (cell.empty) box.addView(dash())
                else {
                    box.addView(Ui.text(ctx, look.valueSp, if (cell.best) 900 else 700, if (cell.best) R.color.ag_mauve else R.color.ag_text).apply {
                        text = cell.text
                        gravity = Gravity.CENTER
                        if (cell.best) {
                            background = Ui.rounded(Bits.blend(c(R.color.ag_mauve), c(R.color.ag_card), 0.2f), 8, ctx)
                            setPadding(dp(8), dp(2), dp(8), dp(2))
                        }
                    }, LinearLayout.LayoutParams(-2, -2))
                    for (s in cell.sub) box.addView(Ui.text(ctx, look.subSp, 700, R.color.ag_sub0).apply {
                        text = s; gravity = Gravity.CENTER
                    }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = dp(1) })
                }
            }
            is Cell.TypeChips -> box.addView(Bits.typeRow(ctx, cell.types), LinearLayout.LayoutParams(-2, -2))
            is Cell.Moves -> {
                if (cell.moves.isEmpty()) box.addView(Ui.text(ctx, look.valueSp, 700, R.color.ag_sub0).apply { text = "none" })
                cell.moves.forEachIndexed { i, m ->
                    box.addView(Ui.text(ctx, look.subSp + 0.5f, 800, R.color.ag_text).apply {
                        text = m.name; maxLines = 2; gravity = Gravity.CENTER
                    }, LinearLayout.LayoutParams(-2, -2).also { if (i > 0) it.topMargin = dp(6) })
                    box.addView(LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        m.type?.let { addView(Ui.typeChip(ctx, it)) }
                        addView(Ui.text(ctx, look.subSp, 700, R.color.ag_sub0).apply { text = "Pow ${m.power}" },
                            LinearLayout.LayoutParams(-2, -2).also { if (m.type != null) it.marginStart = dp(4) })
                    }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = dp(1) })
                }
            }
            is Cell.Matchups -> {
                if (cell.entries.isEmpty()) box.addView(dash())
                val perLine = ((colPx - 2 * pad) / dp(78)).coerceAtLeast(1)
                for (chunk in cell.entries.chunked(perLine)) {
                    box.addView(LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        chunk.forEachIndexed { i, m ->
                            addView(Ui.typeChip(ctx, m.type), LinearLayout.LayoutParams(-2, -2).also { if (i > 0) it.marginStart = dp(8) })
                            val tone = if (m.mult > 1.0) R.color.ag_red else if (m.mult == 0.0) R.color.ag_sub0 else R.color.ag_green
                            addView(Ui.text(ctx, look.subSp, 900, tone).apply { text = m.label },
                                LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dp(3) })
                        }
                    }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = dp(2) })
                }
            }
            is Cell.Evo -> {
                cell.names.forEachIndexed { i, name ->
                    val mark = i == cell.mark
                    box.addView(Ui.text(ctx, look.subSp + 1f, if (mark) 900 else 700, if (mark) R.color.ag_mauve else R.color.ag_sub1).apply {
                        text = (if (i > 0) "↓ " else "") + name
                        gravity = Gravity.CENTER
                    }, LinearLayout.LayoutParams(-2, -2).also { if (i > 0) it.topMargin = dp(1) })
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
