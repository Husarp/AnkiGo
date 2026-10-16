package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.Type

// TICKET T14 owns this file: the Pokédex (mockup frame D).
// Standalone it is a page of its own; given a [pickHost] it is embedded as the Pokédex section of the Team tab (TeamPage
// builds [embeddedBody], calls [renderEmbedded] and owns the bar). One ListView: header = hero card + progress + filters (segmented, search + sort, chips), rows = 4 cells each (recycled, so ~1000 entries scroll smoothly).
class DexPage(base: Context, host: PageHost, internal val pickHost: PickHost? = null) : GamePage(base, host) {
    override val tab = BottomNav.Tab.DEX

    private val embedded get() = pickHost != null

    private lateinit var appBar: AppBar
    private lateinit var list: ListView
    private lateinit var header: LinearLayout
    private lateinit var topBox: LinearLayout     // hero + progress, rebuilt on every render
    private lateinit var segBox: LinearLayout     // All / Owned / Not owned
    private lateinit var chipsBox: LinearLayout   // Wishlist + gen chips, type chips
    private lateinit var search: EditText         // built once so typing keeps focus
    private lateinit var sortPill: TextView
    private lateinit var filtersPill: TextView   // short windows only: shows / hides the chip rows
    private var filtersOpen = false
    private lateinit var emptyView: TextView
    // Kept while the page lives. Search + sort re-filter in place (render would rebuild the header and drop the keyboard).
    private var filter = DexFilter()
    private var shown: List<Species> = emptyList()

    private val adapter = object : BaseAdapter() {
        override fun getCount() = (shown.size + COLS - 1) / COLS
        override fun getItem(position: Int): Any = position
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? CellRow) ?: CellRow(this@DexPage, COLS, 1.08f, 6, 12) { f -> makeCell(f) }
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
            setPadding(Ui.dp(this@DexPage, 12), 0, Ui.dp(this@DexPage, 12), Ui.dp(this@DexPage, 6))
        }
        topBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        segBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chipsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        emptyView = Ui.text(this, 13f, 700, R.color.ag_sub0).apply {
            text = "No Pokémon match"
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, Ui.dp(this@DexPage, 24), 0, Ui.dp(this@DexPage, 12))
        }
        header.addView(topBox)
        val gapTop = if (Ui.shortWindow(this)) 6 else 12 // a short window (split screen) packs the header tighter
        header.addView(segBox, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(this, gapTop) })
        header.addView(searchRow(), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(this, gapTop - 4) })
        header.addView(chipsBox, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = -Ui.dp(this, 2) })
        header.addView(emptyView)
        list = ListView(this).apply {
            divider = null
            dividerHeight = 0
            setSelector(ColorDrawable(Color.TRANSPARENT))
            setPadding(0, 0, 0, Ui.dp(this@DexPage, 12))
            clipToPadding = false
            addHeaderView(header, null, false)
            adapter = this@DexPage.adapter
        }
        return list
    }

    /** The right-hand app bar pill: the enabled generations. */
    fun pillText(): String {
        val gens = state.settings.gens.sorted()
        return if (gens.isEmpty()) "No gens" else if (gens.size == 1) "Gen ${gens[0]}" else "Gen ${gens.first()}–${gens.last()}"
    }

    /** The body for the Team tab (no bar). */
    internal fun embeddedBody(): View = buildBody()

    /** Draws the embedded page; the Team tab calls this on every render. */
    internal fun renderEmbedded() = render()

    /** Pick mode or ticks changed: bind the visible cells again. */
    internal fun refreshCells() {
        for (i in 0 until list.childCount) {
            val row = list.getChildAt(i) as? CellRow ?: continue // the header view is not a CellRow
            for (f in row.cells) bindPick(f)
        }
    }

    /** A filter changed: an embedded page draws itself (nobody listens for it), a standalone one asks to be drawn. */
    private fun redraw() { if (embedded) render() else requestRender() }

    override fun render() {
        val st = state
        val gens = st.settings.gens.sorted()
        if (!embedded) appBar.setPill(pillText())
        val enabled = data.species.values.filter { it.gen in st.settings.gens }.sortedBy { it.num }
        // drop selections that no longer exist (a gen switched off in Settings)
        if (filter.gens.any { it !in st.settings.gens }) filter = filter.copy(gens = filter.gens.filter { it in st.settings.gens }.toSet())
        refilter()

        topBox.removeAllViews()
        if (!Ui.shortWindow(this)) cachedHero()?.let { topBox.addView(it) } // no room for the "last caught" card in a short window
        topBox.addView(progress(enabled, compact = Ui.shortWindow(this)))
        segBox.removeAllViews()
        segmented()
        sortPill.text = "↕ ${filter.sort.label}"
        chipsBox.removeAllViews()
        filters(gens)
        // a short window keeps only the progress line, the segmented control and the search row; "Filters" opens the chip rows
        val short = Ui.shortWindow(this)
        filtersPill.visibility = if (short) View.VISIBLE else View.GONE
        chipsBox.visibility = if (!short || filtersOpen) View.VISIBLE else View.GONE
        val active = filter.gens.size + filter.types.size + (if (filter.wishlistOnly) 1 else 0)
        filtersPill.text = "Filters" + (if (active > 0) " · $active" else "") + (if (filtersOpen) " ▴" else " ▾")
    }

    /** Recomputes the grid from [filter] and shows the "No Pokémon match" line when it is empty. */
    private fun refilter() {
        val st = state
        shown = filter.apply(data.species.values.toList(), DexFilter.lastCaught(st.mons), st.settings.wishlist, st.settings.gens)
        emptyView.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
    }

    // ---- hero ----

    // The hero card keeps its running sprite animation across filter changes: it is rebuilt only when what it shows changed.
    private var heroView: View? = null
    private var heroKey: List<Any?>? = null

    private fun cachedHero(): View? {
        val last = state.mons.maxByOrNull { it.caughtAt }
        val mon = last ?: state.main
        val key = if (mon == null) null else listOf(mon.uid, mon.species, mon.shiny, mon.level, mon.name, mon.caughtAt, last == null)
        if (key != heroKey || (key != null && heroView == null)) {
            heroKey = key
            heroView = hero()
        }
        return heroView
    }

    private fun hero(): View? {
        val last = state.mons.maxByOrNull { it.caughtAt }
        val mon = last ?: state.main ?: return null
        val sp = data.sp(mon.species)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(this@DexPage, 14), Ui.dp(this@DexPage, 12), Ui.dp(this@DexPage, 14), Ui.dp(this@DexPage, 12))
            // mauve 22 % over the card colour, fading to the card colour (mockup .dexhero gradient)
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(Bits.blend(Ui.c(this@DexPage, R.color.ag_mauve), Ui.c(this@DexPage, R.color.ag_card), 0.22f), Ui.c(this@DexPage, R.color.ag_card)),
            ).apply { cornerRadius = Ui.dp(this@DexPage, 16f) }
        }
        val img = ImageView(this)
        MonPreview.show(this, mon.species, mon.shiny, img)
        card.addView(img, LinearLayout.LayoutParams(Ui.dp(this, 84), Ui.dp(this, 84)).also { it.marginEnd = Ui.dp(this, 10) })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.text(this, 12f, 900, R.color.ag_sub0).apply {
            text = "#%03d · %s".format(sp.baseNum, if (last != null) "last caught" else "your main")
        })
        col.addView(Ui.text(this, 20f, 900, R.color.ag_text).apply { text = (if (mon.shiny) "★ " else "") + mon.name })
        col.addView(Bits.typeRow(this, sp.types), LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(this, 4) })
        val caught = if (mon.caughtAt > 0) "Caught ${MonDetail.date(mon.caughtAt)} · " else ""
        col.addView(Ui.text(this, 12f, 700, R.color.ag_sub0).apply {
            text = LevelTier.line(this@DexPage, caught, mon.level, " now\n${MonDetail.nextEvoLine(data, sp)}")
            setLineSpacing(0f, 1.1f)
        }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(this, 4) })
        card.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        // opens that Pokémon's details (taps tick instead while picking for Compare)
        card.setOnClickListener { if (pickHost?.picking != true) MonDetail.show(this, data, mon) }
        return card
    }

    // ---- progress ----

    private fun progress(enabled: List<Species>, compact: Boolean = false): View {
        val st = state
        val caught = enabled.count { it.num in st.caught }
        val seen = enabled.count { it.num in st.seen || it.num in st.caught }
        val total = enabled.size.coerceAtLeast(1)
        val card = Ui.card(this).apply {
            val v = if (compact) 6 else 10
            setPadding(Ui.dp(this@DexPage, 12), Ui.dp(this@DexPage, v), Ui.dp(this@DexPage, 12), Ui.dp(this@DexPage, v))
        }
        val nums = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        fun big(n: Int, tone: Int) = Ui.text(this, if (compact) 17f else 22f, 900, tone).apply { text = n.toString() }
        fun small(s: String) = Ui.text(this, 12f, 700, R.color.ag_sub0).apply { text = s }
        val gap = Ui.dp(this, 4)
        nums.addView(big(caught, R.color.ag_text))
        nums.addView(small("caught"), LinearLayout.LayoutParams(-2, -2).also { it.marginStart = gap; it.bottomMargin = Ui.dp(this, 3) })
        nums.addView(big(seen, R.color.ag_sub0), LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(this, 14) })
        nums.addView(small("seen"), LinearLayout.LayoutParams(-2, -2).also { it.marginStart = gap; it.bottomMargin = Ui.dp(this, 3) })
        nums.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        nums.addView(small("of ${enabled.size}"), LinearLayout.LayoutParams(-2, -2).also { it.bottomMargin = Ui.dp(this, 3) })
        card.addView(nums)

        // two layers: seen (ag_s2) under caught (the collection colour)
        val bars = FrameLayout(this)
        val seenBar = BarView(this).also { it.set(seen.toFloat() / total, Ui.c(this, R.color.ag_s2), false) }
        val caughtBar = BarView(this).also {
            it.setTrackColor(0)
            it.setMeaning(BarMeaning.COLLECTION, caught.toFloat() / total, false)
        }
        bars.addView(seenBar, FrameLayout.LayoutParams(-1, Ui.dp(this, 8)))
        bars.addView(caughtBar, FrameLayout.LayoutParams(-1, Ui.dp(this, 8)))
        card.addView(bars, LinearLayout.LayoutParams(-1, Ui.dp(this, 8)).also { it.topMargin = Ui.dp(this, if (compact) 4 else 6) })
        return card.also { it.layoutParams = LinearLayout.LayoutParams(-1, -2).also { lp -> lp.topMargin = Ui.dp(this, if (compact) 6 else 12) } }
    }

    // ---- filters ----

    private fun segmented() {
        val ctx = this
        segBox.background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 12, ctx)
        segBox.setPadding(Ui.dp(ctx, 3), Ui.dp(ctx, 3), Ui.dp(ctx, 3), Ui.dp(ctx, 3))
        for ((key, label) in listOf(DexOwned.ALL to "All", DexOwned.OWNED to "Owned", DexOwned.NOT_OWNED to "Not owned")) {
            val on = filter.owned == key
            segBox.addView(Ui.text(ctx, 12f, 900, if (on) R.color.ag_text else R.color.ag_sub0).apply {
                text = label
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12)) // 42 dp segments in a 48 dp control
                background = if (on) Ui.rounded(Ui.c(ctx, R.color.ag_card), 10, ctx) else null
                setOnClickListener { filter = filter.copy(owned = key); redraw(); list.setSelection(0) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun searchRow(): View {
        val ctx = this
        search = EditText(ctx).apply {
            hint = "Search name or number"
            typeface = Ui.font(ctx, 700)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Ui.c(ctx, R.color.ag_text))
            setHintTextColor(Ui.c(ctx, R.color.ag_ov))
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 12, ctx)
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 8))
            setText(filter.query)
            Ui.tintEdit(this)
            backgroundTintList = null // tintEdit tints the underline of a plain field; this one has its own rounded fill
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { filter = filter.copy(query = s?.toString().orEmpty()); refilter() }
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
            })
            setOnEditorActionListener { v, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) { v.clearFocus(); true } else false
            }
        }
        sortPill = Ui.text(ctx, 12f, 800, R.color.ag_text).apply {
            maxLines = 1
            gravity = Gravity.CENTER
            Ui.touchPill(this, Ui.rounded(Bits.blend(Ui.c(ctx, R.color.ag_mauve), Ui.c(ctx, R.color.ag_mantle), 0.22f), 12, ctx), 36, 12)
            setOnClickListener {
                val sorts = DexSort.entries
                val m = AppMenu(this).header("Sort by")
                // the current sort carries a check; the others keep a blank icon so the labels line up
                sorts.forEachIndexed { i, so -> m.item(i, so.label, if (so == filter.sort) R.drawable.ic_check else R.drawable.ic_blank) }
                m.show { i -> filter = filter.copy(sort = sorts[i]); redraw(); list.setSelection(0) }
            }
        }
        filtersPill = Ui.text(ctx, 12f, 800, R.color.ag_text).apply {
            maxLines = 1
            visibility = View.GONE
            Ui.touchPill(this, Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 12, ctx), 36, 12)
            setOnClickListener { filtersOpen = !filtersOpen; redraw() }
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(search, LinearLayout.LayoutParams(0, -2, 1f))
            addView(filtersPill, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(ctx, 8) })
            addView(sortPill, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(ctx, 8) })
        }
    }

    /** One filter chip; [tint] = the selected colour (default mauve) with its text colour. */
    private fun chip(label: String, on: Boolean, tint: Pair<Int, Int>? = null, onClick: () -> Unit): TextView {
        val ctx = this
        val (fill, ink) = tint ?: (Ui.c(ctx, R.color.ag_mauve) to Ui.c(ctx, R.color.ag_crust))
        return Ui.text(ctx, 12f, 800, R.color.ag_sub1).apply {
            text = label
            maxLines = 1
            setTextColor(if (on) ink else Ui.c(ctx, R.color.ag_sub1))
            Ui.touchPill(this, Ui.rounded(if (on) fill else Ui.c(ctx, R.color.ag_mantle), 10, ctx), 28, 10) // 48 dp touch height
            setOnClickListener { onClick(); redraw(); list.setSelection(0) }
        }
    }

    private fun chipRow(chips: List<View>): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (c in chips) row.addView(c, LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = Ui.dp(this, 6) })
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun <T> Set<T>.toggle(v: T) = if (v in this) this - v else this + v

    private fun filters(gens: List<Int>) {
        val f = filter
        val firstRow = listOf(chip("Wishlist", f.wishlistOnly) { filter = filter.copy(wishlistOnly = !filter.wishlistOnly) }) +
            gens.map { g -> chip("Gen $g", g in f.gens) { filter = filter.copy(gens = filter.gens.toggle(g)) } }
        chipsBox.addView(chipRow(firstRow))
        val types = Type.entries.map { t ->
            chip(t.label, t in f.types, Ui.typeColor(t) to Ui.typeTextColor(t)) { filter = filter.copy(types = filter.types.toggle(t)) }
        }
        chipsBox.addView(chipRow(types), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = -Ui.dp(this, 4) }) // chips are 48 dp touch rows
    }

    // ---- grid cells (mockup .dexgrid) ----

    private class Cell(val num: TextView, val img: ImageView, val name: TextView, val star: TextView, val check: ImageView) {
        var sp: Species? = null
    }

    private fun makeCell(f: SquareFrame) {
        val ctx = this
        f.background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 12, ctx)
        val num = Ui.text(ctx, 10f, 900, R.color.ag_sub0)
        val img = ImageView(ctx)
        val name = Ui.text(ctx, 10f, 800, R.color.ag_sub1).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            // a long regional-form name ("Galarian Darmanitan") shrinks a little before it is cut off
            setAutoSizeTextTypeUniformWithConfiguration(8, 10, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        }
        f.addView(img, FrameLayout.LayoutParams(Ui.dp(ctx, 68), Ui.dp(ctx, 68), Gravity.CENTER_HORIZONTAL or Gravity.TOP).also { it.topMargin = Ui.dp(ctx, 4) })
        f.addView(num, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.TOP).also { it.leftMargin = Ui.dp(ctx, 6); it.topMargin = Ui.dp(ctx, 4) })
        f.addView(name, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).also {
            it.bottomMargin = Ui.dp(ctx, 4); it.leftMargin = Ui.dp(ctx, 2); it.rightMargin = Ui.dp(ctx, 2)
        })
        // wishlist mark under the number
        val star = Ui.text(ctx, 10f, 900, R.color.ag_yellow).apply { text = "★" }
        f.addView(star, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.TOP).also { it.leftMargin = Ui.dp(ctx, 6); it.topMargin = Ui.dp(ctx, 17) })
        val check = PickKit.badge(ctx)
        f.addView(check, FrameLayout.LayoutParams(Ui.dp(ctx, 20), Ui.dp(ctx, 20), Gravity.END or Gravity.TOP).also {
            it.rightMargin = Ui.dp(ctx, 3); it.topMargin = Ui.dp(ctx, 3)
        })
        f.tag = Cell(num, img, name, star, check)
    }

    private val greyed = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

    /** The tick part of a cell: frame stroke and badge. */
    private fun bindPick(f: SquareFrame) {
        val cell = f.tag as Cell
        val sp = cell.sp ?: return
        val st = state
        val caught = sp.num in st.caught
        val seen = caught || sp.num in st.seen
        val picking = pickHost?.picking == true
        val picked = picking && seen && pickHost?.isPicked(ComparePick.Dex(sp.num)) == true
        f.background = PickKit.cardBg(this, 12, picked)
        PickKit.bindBadge(cell.check, picking && seen, picked)
    }

    private fun bindCell(f: SquareFrame, sp: Species?) {
        val cell = f.tag as Cell
        cell.sp = sp
        if (sp == null) {
            f.visibility = View.INVISIBLE
            f.setOnClickListener(null)
            return
        }
        f.visibility = View.VISIBLE
        val st = state
        val caught = sp.num in st.caught
        val seen = caught || sp.num in st.seen
        cell.num.text = "#%03d".format(sp.baseNum)
        cell.star.visibility = if (sp.num in st.settings.wishlist) View.VISIBLE else View.GONE
        val picking = pickHost?.picking == true
        val pick = ComparePick.Dex(sp.num)
        bindPick(f)
        Ui.staticSprite(this, sp.num, false, cell.img, silhouette = !seen)
        if (seen && !caught) {
            // seen only: grey sprite (mockup .seen)
            cell.img.colorFilter = greyed
            cell.img.alpha = 0.55f
        }
        cell.name.text = if (seen) sp.name else "???"
        cell.name.setTextColor(Ui.c(this, when { caught -> R.color.ag_sub1; seen -> R.color.ag_sub0; else -> R.color.ag_ov }))
        f.setOnClickListener {
            when {
                !seen -> Ui.toast(this, "Not seen yet")
                picking -> pickHost?.togglePick(pick)
                else -> DexDetail.show(this, sp)
            }
        }
    }

    private companion object { const val COLS = 4 }
}

/** The Pokédex opens as the Pokédex section of the Team tab (the bottom bar highlights Team). */
class DexActivity : PageActivity() {
    override fun createPage(): GamePage = TeamPage(this, this, TeamSection.DEX)
}
