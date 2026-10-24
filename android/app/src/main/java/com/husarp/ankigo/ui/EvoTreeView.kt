package com.husarp.ankigo.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.R
import com.husarp.ankigo.game.EvoLine
import com.husarp.ankigo.game.EvoNode
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.Species

/**
 * The evolution line of the details as Views that re-tune themselves for the room they get ([FitWidth] asks, level by level, until
 * the line fits): 0 = the normal look; 1-4 = a shorter arrow with the method text wrapped narrower, then smaller sprites (floor 28 dp);
 * 5 = the stages stacked downwards (arrows point down, the method beside them); only after that is the whole line drawn smaller.
 * A method text is never cut: the arrow and its text always get their own room in the column they stand in (R9-B11).
 *
 * R18-G: PLAN 260 (the arrow stands on the pictures' centre whatever its label, [ArrowCol]) and PLAN 243B (more than two last forms
 * from one Pokémon wrap into a grid, three per line, the method under each name: [EvoGrid]).
 */
class EvoTreeView(ctx: Context, data: GameData, root: EvoNode, private val current: Species, private val seen: Set<Int>,
                  private val compact: Boolean, private val onTap: ((Species) -> Unit)?) : LinearLayout(ctx), FitWidth.Tunable {
    /** A Pokémon box. [grid] = one of the targets in a grid: a smaller picture, never wider than [GRID_SPRITE]. */
    private class Spot(val box: LinearLayout, val img: ImageView, val name: TextView, val grid: Boolean)
    /** A node with children: [view] holds the node and its branches; [single] = exactly one branch (it can stand below). */
    private class Join(val view: LinearLayout, val single: Boolean)
    /** One branch: [row] holds the arrow column [col] and the subtree ([row] is null for the one arrow into a grid, which sits in the join). */
    private class Hop(val row: LinearLayout?, val col: ArrowCol, val single: Boolean)

    private val spots = ArrayList<Spot>()
    private val joins = ArrayList<Join>()
    private val hops = ArrayList<Hop>()
    private val gridLabels = ArrayList<TextView>()
    private var level = -1

    override val levels: Int get() = LEVELS

    init {
        addView(build(ctx, data, root))
        tune(0)
    }

    private fun build(a: Context, data: GameData, node: EvoNode): View {
        val me = spot(a, node, grid = false)
        if (node.children.isEmpty()) return me
        if (EvoGrid.isGrid(node)) return buildGrid(a, data, node, me)
        val single = node.children.size == 1
        val kids = LinearLayout(a).apply {
            orientation = VERTICAL
            node.children.forEach { c ->
                val arrow = DetailSections.ArrowView(a, 30)
                val label = c.via?.let { v -> Ui.text(a, 9.5f, 800, R.color.ag_sub0).apply { text = EvoLine.method(data, v); gravity = Gravity.CENTER } }
                val col = ArrowCol(a, arrow, label)
                val row = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                row.addView(col, LayoutParams(-2, -2))
                row.addView(build(a, data, c), LayoutParams(-2, -2))
                hops += Hop(row, col, single)
                addView(row, LayoutParams(-2, -2))
            }
        }
        return LinearLayout(a).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(me, LayoutParams(-2, -2))
            addView(kids, LayoutParams(-2, -2))
            joins += Join(this, single)
        }
    }

    /** PLAN 243B: [node] and its targets as a grid: the node, one arrow, the targets three per line with the method under each name. */
    private fun buildGrid(a: Context, data: GameData, node: EvoNode, me: View): View {
        val grid = GridKids(a, EvoGrid.PER_ROW)
        node.children.forEach { c ->
            grid.addView(LinearLayout(a).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(spot(a, c, grid = true), LayoutParams(-2, -2))
                c.via?.let { v ->
                    addView(Ui.text(a, 9.5f, 800, R.color.ag_sub0).apply { text = EvoLine.method(data, v); gravity = Gravity.CENTER }.also { gridLabels += it },
                        LayoutParams(-2, -2))
                }
            })
        }
        val col = ArrowCol(a, DetailSections.ArrowView(a, 30), null)
        hops += Hop(null, col, true)
        return LinearLayout(a).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(me, LayoutParams(-2, -2))
            addView(col, LayoutParams(-2, -2))
            addView(grid, LayoutParams(-2, -2))
            joins += Join(this, true)
        }
    }

    private fun spot(a: Context, node: EvoNode, grid: Boolean): View {
        val here = node.species.num == current.num
        val img = ImageView(a)
        MonPreview.show(a, node.species.num, false, img, silhouette = !EvoLine.isSeen(node.species, seen))
        val name = Ui.text(a, 10.5f, 800, if (here) R.color.ag_text else R.color.ag_sub1).apply {
            text = EvoLine.shownName(node.species, seen); maxLines = 1; gravity = Gravity.CENTER
        }
        val box = LinearLayout(a).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            if (here) background = Ui.rounded(Bits.blend(Ui.c(a, R.color.ag_mauve), Ui.c(a, R.color.ag_mantle), 0.22f), 12, a)
            addView(img, LayoutParams(Ui.dp(a, 48), Ui.dp(a, 48)))
            addView(name, LayoutParams(-2, -2))
            contentDescription = EvoLine.shownName(node.species, seen) + if (here) " (this one)" else ""
            if (!here && onTap != null && EvoLine.isSeen(node.species, seen)) setOnClickListener { onTap.invoke(node.species) }
        }
        spots += Spot(box, img, name, grid)
        return box
    }

    /** Sets the look of [lvl] (see the class text). Cheap when it is already set; safe to call while being measured. */
    override fun tune(lvl: Int) {
        val l = lvl.coerceIn(0, LEVELS - 1)
        if (l == level) return
        level = l
        val stacked = l == LEVELS - 1
        val i = if (stacked) 0 else l
        val spriteDp = (if (compact) SPRITE_COMPACT else SPRITE)[i]
        val arrowDp = (if (compact) ARROW_COMPACT else ARROW)[i]
        val labelDp = if (stacked) STACKED_LABEL else (if (compact) LABEL_COMPACT else LABEL)[i]
        val small = spriteDp < 44
        val a = context
        spots.forEach { s ->
            val sd = if (s.grid) minOf(spriteDp, if (compact) GRID_SPRITE_COMPACT else GRID_SPRITE) else spriteDp
            val px = Ui.dp(a, if (s.grid || sd < 44) 2 else 4)
            s.box.setPadding(px, Ui.dp(a, 4), px, Ui.dp(a, 4))
            (s.img.layoutParams as LayoutParams).let { lp -> lp.width = Ui.dp(a, sd); lp.height = Ui.dp(a, sd); s.img.layoutParams = lp }
            s.name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (sd < 44) 9.5f else 10.5f)
        }
        // the Pokémon box beside an arrow: the arrow stands on its picture's centre (PLAN 260)
        val ref = spots.first { !it.grid }
        ref.box.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val boxH = ref.box.measuredHeight
        val anchorY = ref.box.paddingTop + ref.img.layoutParams.height / 2
        joins.forEach { j ->
            val down = stacked && j.single
            j.view.orientation = if (down) VERTICAL else HORIZONTAL
            j.view.gravity = if (down) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
        }
        hops.forEach { h ->
            val down = stacked && h.single
            h.row?.let { r ->
                r.orientation = if (down) VERTICAL else HORIZONTAL
                r.gravity = if (down) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
            }
            h.col.setPadding(Ui.dp(a, if (small) 0 else 2), 0, Ui.dp(a, if (small) 0 else 2), 0)
            h.col.setLook(arrowDp, down, boxH, anchorY)
            h.col.label?.let { t ->
                t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (small) 9f else 9.5f)
                t.maxWidth = Ui.dp(a, labelDp)
            }
        }
        gridLabels.forEach { t ->
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (small) 9f else 9.5f)
            t.maxWidth = Ui.dp(a, labelDp + GRID_LABEL_EXTRA)
        }
    }

    companion object {
        const val LEVELS = 6
        private val SPRITE = intArrayOf(48, 48, 40, 34, 28)
        private val SPRITE_COMPACT = intArrayOf(36, 36, 32, 30, 28)
        private val ARROW = intArrayOf(30, 24, 20, 16, 14)
        private val ARROW_COMPACT = intArrayOf(22, 20, 18, 16, 14)
        private val LABEL = intArrayOf(66, 54, 48, 44, 42)
        private val LABEL_COMPACT = intArrayOf(52, 48, 44, 42, 40)
        private const val STACKED_LABEL = 150
        /** The pictures of a grid's targets (R18-G, 243B): smaller than the line's own, so three fit beside the arrow. */
        private const val GRID_SPRITE = 40
        private const val GRID_SPRITE_COMPACT = 32
        /** A grid cell's method text may be a little wider than a stacked row's: it hangs under a name, not beside an arrow. */
        private const val GRID_LABEL_EXTRA = 6
    }
}

/**
 * R18-G (PLAN 260): where the evolution arrow and its method text stand, pure. The column is laid beside a Pokémon box [boxH] tall whose
 * picture centre is [anchorY] from the box top, and the row centres the column on the BOX. The arrow must stand on the PICTURE's centre
 * whatever the label: the column is made tall enough (the arrow [arrowH] tall, the label [labelH] tall, hanging under it) that the arrow's
 * centre is the box centre minus the same constant, so one, two or three lines of label never move it.
 */
internal object ArrowGeometry {
    /** [height] of the column, [arrowTop] and [labelTop] inside it. */
    class Col(val height: Int, val arrowTop: Int, val labelTop: Int)

    fun column(boxH: Int, anchorY: Int, arrowH: Int, labelH: Int): Col {
        // twice the distance of the box centre below the picture centre
        val below2 = boxH - 2 * anchorY
        var h = maxOf(boxH, arrowH + below2)
        if (labelH > 0) h = maxOf(h, arrowH + 2 * labelH - below2) // the label hangs under the arrow and must fit in the column
        if ((h - boxH) % 2 != 0) h++                                // same parity as the box: the centring never rounds differently
        val arrowTop = (h - below2 - arrowH) / 2
        return Col(h, arrowTop, arrowTop + arrowH)
    }
}

/**
 * The arrow with its method text under it (PLAN 260). Beside a Pokémon box ([setLook]'s box height and picture centre) the arrow sits
 * on the picture's centre ([ArrowGeometry]); stacked downwards ([setLook] `down`) the arrow points down with the text beside it.
 */
internal class ArrowCol(ctx: Context, val arrow: DetailSections.ArrowView, val label: TextView?) : ViewGroup(ctx) {
    private var down = false
    private var boxH = 0
    private var anchorY = 0
    private var col = ArrowGeometry.Col(0, 0, 0)
    private val sideGap = Ui.dp(ctx, 6)

    init {
        addView(arrow)
        label?.let { addView(it) }
    }

    fun setLook(lengthDp: Int, down: Boolean, boxH: Int, anchorY: Int) {
        arrow.set(lengthDp, down)
        if (down == this.down && boxH == this.boxH && anchorY == this.anchorY) return
        this.down = down; this.boxH = boxH; this.anchorY = anchorY
        requestLayout()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        arrow.measure(free, free)
        label?.measure(free, free) // its own maxWidth wraps it
        val lw = label?.measuredWidth ?: 0
        val lh = label?.measuredHeight ?: 0
        val w: Int
        val h: Int
        if (down) {
            w = arrow.measuredWidth + (if (label != null) sideGap + lw else 0)
            h = maxOf(arrow.measuredHeight, lh)
        } else {
            col = ArrowGeometry.column(boxH, anchorY, arrow.measuredHeight, lh)
            w = maxOf(arrow.measuredWidth, lw)
            h = col.height
        }
        setMeasuredDimension(w + paddingLeft + paddingRight, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val inner = r - l - paddingLeft - paddingRight
        val h = b - t
        if (down) {
            arrow.layout(paddingLeft, (h - arrow.measuredHeight) / 2, paddingLeft + arrow.measuredWidth, (h - arrow.measuredHeight) / 2 + arrow.measuredHeight)
            label?.let {
                val x = paddingLeft + arrow.measuredWidth + sideGap
                it.layout(x, (h - it.measuredHeight) / 2, x + it.measuredWidth, (h - it.measuredHeight) / 2 + it.measuredHeight)
            }
        } else {
            val ax = paddingLeft + (inner - arrow.measuredWidth) / 2
            arrow.layout(ax, col.arrowTop, ax + arrow.measuredWidth, col.arrowTop + arrow.measuredHeight)
            label?.let {
                val x = paddingLeft + (inner - it.measuredWidth) / 2
                it.layout(x, col.labelTop, x + it.measuredWidth, col.labelTop + it.measuredHeight)
            }
        }
    }
}

/** The targets of a grid (PLAN 243B): [perRow] cells per line, all cells as wide as the widest, lines as tall as their tallest cell. */
private class GridKids(ctx: Context, private val perRow: Int) : ViewGroup(ctx) {
    private val hGap = Ui.dp(ctx, 2)
    private val vGap = Ui.dp(ctx, 6)
    private var cellW = 0
    private var lineH = IntArray(0)

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        cellW = 0
        for (i in 0 until childCount) { getChildAt(i).measure(free, free); cellW = maxOf(cellW, getChildAt(i).measuredWidth) }
        val lines = (childCount + perRow - 1) / perRow
        lineH = IntArray(lines)
        for (i in 0 until childCount) lineH[i / perRow] = maxOf(lineH[i / perRow], getChildAt(i).measuredHeight)
        val cols = minOf(childCount, perRow)
        setMeasuredDimension(cols * cellW + maxOf(0, cols - 1) * hGap, lineH.sum() + maxOf(0, lines - 1) * vGap)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var y = 0
        for (line in lineH.indices) {
            for (k in 0 until perRow) {
                val i = line * perRow + k
                if (i >= childCount) break
                val c = getChildAt(i)
                val x = k * (cellW + hGap) + (cellW - c.measuredWidth) / 2 // centred in its cell
                c.layout(x, y, x + c.measuredWidth, y + c.measuredHeight)
            }
            y += lineH[line] + vGap
        }
    }
}
