package com.husarp.ankigo.ui

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Species

// TICKET T14 owns this file: the Pokédex (mockup frame D).
// One ListView: header = hero card + progress + filter chips, rows = 4 cells each (recycled, so ~1000 entries scroll smoothly).
class DexActivity : GameScreen() {
    override val tab = BottomNav.Tab.DEX

    private lateinit var appBar: AppBar
    private lateinit var list: ListView
    private lateinit var header: LinearLayout
    private var filter = "all"
    private var shown: List<Species> = emptyList()

    private val adapter = object : BaseAdapter() {
        override fun getCount() = (shown.size + COLS - 1) / COLS
        override fun getItem(position: Int): Any = position
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? CellRow) ?: CellRow(this@DexActivity, COLS, 1.08f, 6, 12) { f -> makeCell(f) }
            for (c in 0 until COLS) bindCell(row.cells[c], shown.getOrNull(position * COLS + c))
            return row
        }
    }

    override fun buildBar(): View {
        appBar = Ui.appBar(this, "Pokédex", "")
        return appBar
    }

    override fun buildBody(): View {
        header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@DexActivity, 12), 0, Ui.dp(this@DexActivity, 12), Ui.dp(this@DexActivity, 6))
        }
        list = ListView(this).apply {
            divider = null
            dividerHeight = 0
            setSelector(ColorDrawable(Color.TRANSPARENT))
            setPadding(0, 0, 0, Ui.dp(this@DexActivity, 12))
            clipToPadding = false
            addHeaderView(header, null, false)
            adapter = this@DexActivity.adapter
        }
        return list
    }

    override fun render() {
        val st = state
        val gens = st.settings.gens.sorted()
        appBar.setPill(if (gens.isEmpty()) "No gens" else if (gens.size == 1) "Gen ${gens[0]}" else "Gen ${gens.first()}–${gens.last()}")
        val enabled = data.species.values.filter { it.gen in st.settings.gens }.sortedBy { it.num }
        val keys = listOf("all" to "All", "caught" to "Caught", "missing" to "Missing", "wish" to "Wishlist") +
            gens.map { "gen$it" to "Gen $it" }
        if (keys.none { it.first == filter }) filter = "all"
        shown = enabled.filter {
            when {
                filter == "caught" -> it.num in st.caught
                filter == "missing" -> it.num !in st.caught
                filter == "wish" -> it.num in st.settings.wishlist
                filter.startsWith("gen") -> it.gen == filter.removePrefix("gen").toInt()
                else -> true
            }
        }

        header.removeAllViews()
        hero()?.let { header.addView(it) }
        header.addView(progress(enabled))
        header.addView(filters(keys), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(this, 12) })
        adapter.notifyDataSetChanged()
    }

    // ---- hero ----

    private fun hero(): View? {
        val last = state.mons.maxByOrNull { it.caughtAt }
        val mon = last ?: state.main ?: return null
        val sp = data.sp(mon.species)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(this@DexActivity, 14), Ui.dp(this@DexActivity, 12), Ui.dp(this@DexActivity, 14), Ui.dp(this@DexActivity, 12))
            // mauve 22 % over the card colour, fading to the card colour (mockup .dexhero gradient)
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(Bits.blend(Ui.c(this@DexActivity, R.color.ag_mauve), Ui.c(this@DexActivity, R.color.ag_card), 0.22f), Ui.c(this@DexActivity, R.color.ag_card)),
            ).apply { cornerRadius = Ui.dp(this@DexActivity, 16f) }
        }
        val img = ImageView(this)
        Ui.staticSprite(this, mon.species, mon.shiny, img)
        card.addView(img, LinearLayout.LayoutParams(Ui.dp(this, 84), Ui.dp(this, 84)).also { it.marginEnd = Ui.dp(this, 10) })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.text(this, 12f, 900, R.color.ag_sub0).apply {
            text = "#%03d · %s".format(sp.num, if (last != null) "last caught" else "your main")
        })
        col.addView(Ui.text(this, 20f, 900, R.color.ag_text).apply { text = (if (mon.shiny) "★ " else "") + mon.name })
        col.addView(Bits.typeRow(this, sp.types), LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(this, 4) })
        val caught = if (mon.caughtAt > 0) "Caught ${MonDetail.date(mon.caughtAt)} · " else ""
        col.addView(Ui.text(this, 12f, 700, R.color.ag_sub0).apply {
            text = "${caught}Lv ${mon.level} now\n${MonDetail.nextEvoLine(data, sp)}"
            setLineSpacing(0f, 1.1f)
        }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(this, 4) })
        card.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        return card
    }

    // ---- progress ----

    private fun progress(enabled: List<Species>): View {
        val st = state
        val caught = enabled.count { it.num in st.caught }
        val seen = enabled.count { it.num in st.seen || it.num in st.caught }
        val total = enabled.size.coerceAtLeast(1)
        val card = Ui.card(this).apply {
            setPadding(Ui.dp(this@DexActivity, 12), Ui.dp(this@DexActivity, 10), Ui.dp(this@DexActivity, 12), Ui.dp(this@DexActivity, 10))
        }
        val nums = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        fun big(n: Int, tone: Int) = Ui.text(this, 22f, 900, tone).apply { text = n.toString() }
        fun small(s: String) = Ui.text(this, 12f, 700, R.color.ag_sub0).apply { text = s }
        val gap = Ui.dp(this, 4)
        nums.addView(big(caught, R.color.ag_text))
        nums.addView(small("caught"), LinearLayout.LayoutParams(-2, -2).also { it.marginStart = gap; it.bottomMargin = Ui.dp(this, 3) })
        nums.addView(big(seen, R.color.ag_sub0), LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(this, 14) })
        nums.addView(small("seen"), LinearLayout.LayoutParams(-2, -2).also { it.marginStart = gap; it.bottomMargin = Ui.dp(this, 3) })
        nums.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        nums.addView(small("of ${enabled.size}"), LinearLayout.LayoutParams(-2, -2).also { it.bottomMargin = Ui.dp(this, 3) })
        card.addView(nums)

        // two layers: seen (ag_s2) under caught (mauve -> pink)
        val bars = FrameLayout(this)
        val seenBar = BarView(this).also { it.set(seen.toFloat() / total, Ui.c(this, R.color.ag_s2), false) }
        val caughtBar = BarView(this).also {
            it.setTrackColor(0)
            it.setGradient(Ui.c(this, R.color.ag_mauve), Ui.c(this, R.color.ag_pink))
            it.set(caught.toFloat() / total, Ui.c(this, R.color.ag_mauve), false)
        }
        bars.addView(seenBar, FrameLayout.LayoutParams(-1, Ui.dp(this, 8)))
        bars.addView(caughtBar, FrameLayout.LayoutParams(-1, Ui.dp(this, 8)))
        card.addView(bars, LinearLayout.LayoutParams(-1, Ui.dp(this, 8)).also { it.topMargin = Ui.dp(this, 6) })
        return card.also { it.layoutParams = LinearLayout.LayoutParams(-1, -2).also { lp -> lp.topMargin = Ui.dp(this, 12) } }
    }

    // ---- filter chips ----

    private fun filters(keys: List<Pair<String, String>>): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((key, label) in keys) {
            val on = key == filter
            row.addView(Ui.text(this, 12f, 800, if (on) R.color.ag_crust else R.color.ag_sub1).apply {
                text = label
                maxLines = 1
                setPadding(Ui.dp(this@DexActivity, 10), Ui.dp(this@DexActivity, 5), Ui.dp(this@DexActivity, 10), Ui.dp(this@DexActivity, 5))
                background = Ui.rounded(Ui.c(this@DexActivity, if (on) R.color.ag_mauve else R.color.ag_mantle), 10, this@DexActivity)
                setOnClickListener { filter = key; requestRender(); list.setSelection(0) }
            }, LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = Ui.dp(this, 6) })
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    // ---- grid cells (mockup .dexgrid) ----

    private class Cell(val num: TextView, val img: ImageView, val name: TextView, val dot: View)

    private fun makeCell(f: SquareFrame) {
        val ctx = this
        f.background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 12, ctx)
        val num = Ui.text(ctx, 10f, 900, R.color.ag_sub0)
        val img = ImageView(ctx)
        val name = Ui.text(ctx, 10f, 800, R.color.ag_sub1).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
        }
        val dot = View(ctx).apply { background = Ui.rounded(Ui.c(ctx, R.color.ag_green), 4, ctx) }
        f.addView(img, FrameLayout.LayoutParams(Ui.dp(ctx, 68), Ui.dp(ctx, 68), Gravity.CENTER_HORIZONTAL or Gravity.TOP).also { it.topMargin = Ui.dp(ctx, 4) })
        f.addView(num, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.TOP).also { it.leftMargin = Ui.dp(ctx, 6); it.topMargin = Ui.dp(ctx, 4) })
        f.addView(name, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).also {
            it.bottomMargin = Ui.dp(ctx, 4); it.leftMargin = Ui.dp(ctx, 2); it.rightMargin = Ui.dp(ctx, 2)
        })
        f.addView(dot, FrameLayout.LayoutParams(Ui.dp(ctx, 8), Ui.dp(ctx, 8), Gravity.END or Gravity.TOP).also {
            it.rightMargin = Ui.dp(ctx, 6); it.topMargin = Ui.dp(ctx, 6)
        })
        f.tag = Cell(num, img, name, dot)
    }

    private val greyed = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

    private fun bindCell(f: SquareFrame, sp: Species?) {
        val cell = f.tag as Cell
        if (sp == null) {
            f.visibility = View.INVISIBLE
            f.setOnClickListener(null)
            return
        }
        f.visibility = View.VISIBLE
        val st = state
        val caught = sp.num in st.caught
        val seen = caught || sp.num in st.seen
        cell.num.text = "#%03d".format(sp.num)
        cell.dot.visibility = if (caught) View.VISIBLE else View.GONE
        Ui.staticSprite(this, sp.num, false, cell.img, silhouette = !seen)
        if (seen && !caught) {
            // seen only: grey sprite (mockup .seen)
            cell.img.colorFilter = greyed
            cell.img.alpha = 0.55f
        }
        cell.name.text = if (seen) sp.name else "???"
        cell.name.setTextColor(Ui.c(this, when { caught -> R.color.ag_sub1; seen -> R.color.ag_sub0; else -> R.color.ag_ov }))
        f.setOnClickListener {
            if (seen) DexDetail.show(this, sp) else Ui.toast(this, "Not seen yet")
        }
    }

    private companion object { const val COLS = 4 }
}
