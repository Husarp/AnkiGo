package com.husarp.ankigo.ui

import android.content.Context
import android.view.Gravity
import android.view.View
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
 */
class EvoTreeView(ctx: Context, data: GameData, root: EvoNode, private val current: Species, private val seen: Set<Int>,
                  private val compact: Boolean, private val onTap: ((Species) -> Unit)?) : LinearLayout(ctx), FitWidth.Tunable {
    private class Spot(val box: LinearLayout, val img: ImageView, val name: TextView)
    /** A node with children: [view] holds the node and its branches; [single] = exactly one branch (it can stand below). */
    private class Join(val view: LinearLayout, val single: Boolean)
    /** One branch: [row] holds the arrow column [col] and the subtree. */
    private class Hop(val row: LinearLayout, val col: LinearLayout, val arrow: DetailSections.ArrowView, val label: TextView?, val single: Boolean)

    private val spots = ArrayList<Spot>()
    private val joins = ArrayList<Join>()
    private val hops = ArrayList<Hop>()
    private var level = -1

    override val levels: Int get() = LEVELS

    init {
        addView(build(ctx, data, root))
        tune(0)
    }

    private fun build(a: Context, data: GameData, node: EvoNode): View {
        val me = spot(a, node)
        if (node.children.isEmpty()) return me
        val single = node.children.size == 1
        val kids = LinearLayout(a).apply {
            orientation = VERTICAL
            node.children.forEach { c ->
                val col = LinearLayout(a).apply { orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
                val arrow = DetailSections.ArrowView(a, 30)
                col.addView(arrow, LayoutParams(-2, -2))
                val label = c.via?.let { v ->
                    Ui.text(a, 9.5f, 800, R.color.ag_sub0).apply { text = EvoLine.method(data, v); gravity = Gravity.CENTER }
                        .also { col.addView(it, LayoutParams(-2, -2)) }
                }
                val row = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                row.addView(col, LayoutParams(-2, -2))
                row.addView(build(a, data, c), LayoutParams(-2, -2))
                hops += Hop(row, col, arrow, label, single)
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

    private fun spot(a: Context, node: EvoNode): View {
        val here = node.species.num == current.num
        val img = ImageView(a)
        MonPreview.show(a, node.species.num, false, img, silhouette = node.species.num !in seen && node.species.baseNum !in seen)
        val name = Ui.text(a, 10.5f, 800, if (here) R.color.ag_text else R.color.ag_sub1).apply {
            text = node.species.name; maxLines = 1; gravity = Gravity.CENTER
        }
        val box = LinearLayout(a).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            if (here) background = Ui.rounded(Bits.blend(Ui.c(a, R.color.ag_mauve), Ui.c(a, R.color.ag_mantle), 0.22f), 12, a)
            addView(img, LayoutParams(Ui.dp(a, 48), Ui.dp(a, 48)))
            addView(name, LayoutParams(-2, -2))
            contentDescription = node.species.name + if (here) " (this one)" else ""
            if (!here && onTap != null && (node.species.num in seen || node.species.baseNum in seen)) setOnClickListener { onTap.invoke(node.species) }
        }
        spots += Spot(box, img, name)
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
            val px = Ui.dp(a, if (small) 2 else 4)
            s.box.setPadding(px, Ui.dp(a, 4), px, Ui.dp(a, 4))
            (s.img.layoutParams as LayoutParams).let { lp -> lp.width = Ui.dp(a, spriteDp); lp.height = Ui.dp(a, spriteDp); s.img.layoutParams = lp }
            s.name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (small) 9.5f else 10.5f)
        }
        joins.forEach { j ->
            val down = stacked && j.single
            j.view.orientation = if (down) VERTICAL else HORIZONTAL
            j.view.gravity = if (down) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
        }
        hops.forEach { h ->
            val down = stacked && h.single
            h.row.orientation = if (down) VERTICAL else HORIZONTAL
            h.row.gravity = if (down) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
            h.col.orientation = if (down) HORIZONTAL else VERTICAL
            h.col.gravity = if (down) Gravity.CENTER_VERTICAL else Gravity.CENTER_HORIZONTAL
            h.col.setPadding(Ui.dp(a, if (small) 0 else 2), 0, Ui.dp(a, if (small) 0 else 2), 0)
            h.arrow.set(arrowDp, down)
            h.label?.let { t ->
                t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (small) 9f else 9.5f)
                t.maxWidth = Ui.dp(a, labelDp)
                (t.layoutParams as LayoutParams).let { lp -> lp.leftMargin = if (down) Ui.dp(a, 6) else 0; t.layoutParams = lp }
            }
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
    }
}
