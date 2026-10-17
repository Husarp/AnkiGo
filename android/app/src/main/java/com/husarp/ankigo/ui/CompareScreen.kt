package com.husarp.ankigo.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.R

// PLAN item 53 (R3-V5, pick 5A): the Compare screen. Mode switch on top (vs reference / best of all), a chip row (rows, sort,
// filter, fully evolved) and the table of CompareGrid. All the choices live in CompareState; the screen only draws them.

class ComparePage(base: Context, host: PageHost, private val picks: List<ComparePick>) : GamePage(base, host) {
    override val tab: BottomNav.Tab? = null

    private lateinit var bar: AppBar
    private lateinit var holder: FrameLayout
    private var cmp: CompareState? = null
    private var evolved = false
    private var rows: RowChoice = CompareStore.loadRows(this)
    private var lastX = 0
    private var lastY = 0
    private var full: CompareData? = null

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun c(res: Int) = Ui.c(this, res)

    override fun buildBar(): View {
        bar = Ui.appBar(this, "Compare", back = host.hostActivity != null, onBack = { host.closePage() })
        return bar
    }

    override fun buildBody(): View {
        holder = FrameLayout(this)
        return holder
    }

    public override fun render() {
        val data = CompareTable.build(data, state, picks, evolved)
        full = data
        val st = cmp?.also { it.fit(data) }
            ?: CompareState(CompareStore.loadMode(this)).also { cmp = it; it.fit(data); onNewState?.invoke(it) }
        holder.removeAllViews()
        val display = st.display(data)
        bar.titleView.text = "Compare · ${st.size}"
        if (display.isEmpty()) {
            // keep the controls (filter chip) and offer a way back, so a filter can never leave the screen stuck
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val side = dp(6)
            if (st.size > 0) {
                col.addView(SortViews.scrollRow(this, chipViews(data, st)).also { it.setPadding(side, 0, side, 0); (it as? android.view.ViewGroup)?.clipToPadding = false },
                    LinearLayout.LayoutParams(-1, -2))
            }
            val msg = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            msg.addView(Ui.text(this, 14f, 700, R.color.ag_sub0).apply { gravity = Gravity.CENTER; text = "Nothing to compare" })
            if (st.size > 0) {
                msg.addView(Ui.text(this, 14f, 900, R.color.ag_crust).apply {
                    text = "Show all columns"
                    gravity = Gravity.CENTER
                    minHeight = dp(48)
                    setPadding(dp(20), 0, dp(20), 0)
                    background = Ui.rounded(c(R.color.ag_mauve), 14, this@ComparePage)
                    setOnClickListener { st.resetView(); requestRender() }
                }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = dp(12) })
            }
            col.addView(msg, LinearLayout.LayoutParams(-1, 0, 1f))
            holder.addView(col, FrameLayout.LayoutParams(-1, -1))
            return
        }
        val view = CompareModes.view(data, display, rows)
        val look = CompareLook(this, view.columns.size)
        val grid = CompareGrid(this, look, view, st.mode, st.sortKey, st.sortDesc,
            onHead = { pos, anchor -> headMenu(anchor, data, display, pos) },
            onLabel = { row -> tapLabel(row) })
        grid.restore(lastX, lastY)
        grid.onScrolled = { x, y -> lastX = x; lastY = y }

        val side = dp(look.sideDp)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val chips = chipViews(data, st)
        if (Ui.shortWindow(this)) {
            // little height: the mode switch becomes two chips at the start of the one chip row
            val all = modeChips(st) + chips
            col.addView(SortViews.scrollRow(this, all).also { it.setPadding(side, 0, side, 0); (it as? android.view.ViewGroup)?.clipToPadding = false }, LinearLayout.LayoutParams(-1, -2))
        } else {
            col.addView(modeSwitch(st), LinearLayout.LayoutParams(-1, -2).also { it.setMargins(side + dp(4), dp(2), side + dp(4), dp(0)) })
            col.addView(SortViews.scrollRow(this, chips).also { it.setPadding(side, 0, side, 0); (it as? android.view.ViewGroup)?.clipToPadding = false }, LinearLayout.LayoutParams(-1, -2))
        }
        col.addView(grid.root, LinearLayout.LayoutParams(-1, 0, 1f).also { it.setMargins(side, 0, side, dp(4)) })
        if (st.mode == CompareMode.BEST_OF_ALL) col.addView(legend(), LinearLayout.LayoutParams(-1, -2).also { it.setMargins(side + dp(4), 0, side, dp(6)) })
        holder.addView(col, FrameLayout.LayoutParams(-1, -1))
    }

    // ---------- the controls ----------

    private fun setMode(st: CompareState, m: CompareMode) {
        if (st.mode == m) return
        st.mode = m
        CompareStore.saveMode(this, m)
        requestRender()
    }

    private fun modeLabel(m: CompareMode) = if (m == CompareMode.VS_REFERENCE) "Vs reference" else "Best of all"

    /** The two modes as one switch (the look of the sort sheet's basis switch). */
    private fun modeSwitch(st: CompareState): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.rounded(c(R.color.ag_mantle), 12, this@ComparePage)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        for (m in CompareMode.entries) {
            val on = m == st.mode
            row.addView(Ui.text(this, 13f, 900, if (on) R.color.ag_text else R.color.ag_sub0).apply {
                text = modeLabel(m)
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(dp(2), dp(11), dp(2), dp(11))
                background = if (on) Ui.rounded(c(R.color.ag_card), 10, this@ComparePage) else null
                setOnClickListener { setMode(st, m) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        return row
    }

    private fun modeChips(st: CompareState): List<View> =
        CompareMode.entries.map { m -> BoxViews.chip(this, modeLabel(m), m == st.mode) { setMode(st, m) } }

    private fun chipViews(data: CompareData, st: CompareState): List<View> {
        val out = ArrayList<View>()
        val all = data.rows.size
        val hidden = data.rows.count { !rows.visible(it.key) }
        out += BoxViews.chip(this, if (hidden == 0) "Rows: all" else "Rows: ${all - hidden} of $all", hidden > 0) { showRows(data) }

        val key = st.sortKey
        val sortRow = key?.let { data.rowOrNull(it) }
        lateinit var sortChip: View
        sortChip = BoxViews.chip(this, if (sortRow == null) "Sort columns" else "Sort: ${sortName(sortRow)} ${if (st.sortDesc) "↓" else "↑"}", sortRow != null) {
            sortMenu(sortChip, st, sortRow)
        }
        out += sortChip

        val mixed = data.columns.any { it.mon != null } && data.columns.any { it.mon == null }
        if (mixed || st.filter.owner != OwnerFilter.ALL) {
            lateinit var filterChip: View
            filterChip = BoxViews.chip(this, when (st.filter.owner) { OwnerFilter.ALL -> "Show: all"; OwnerFilter.OWNED -> "Show: owned"; OwnerFilter.DEX -> "Show: Pokédex" },
                st.filter.owner != OwnerFilter.ALL) { filterMenu(filterChip, st) }
            out += filterChip
        }
        out += BoxViews.chip(this, "Fully evolved", evolved) { evolved = !evolved; requestRender() }
        return out
    }

    private fun sortName(row: CompareRow): String = row.label + if (row.key.startsWith("base_")) " (base)" else ""

    private fun tapLabel(row: CompareRow) {
        val st = cmp ?: return
        if (row.numeric) { st.tapRow(row.key); requestRender() }
        else StatInfo.forKey(row.key)?.let { DetailParts.showInfo(this, it) }
    }

    private fun sortMenu(anchor: View, st: CompareState, row: CompareRow?) {
        if (row == null) { Ui.toast(this, "Tap a row name in the table to sort by it"); return }
        AppMenu(anchor).header("Sort by ${sortName(row)}")
            .item(0, "High → low", if (st.sortDesc) R.drawable.ic_check else R.drawable.ic_blank)
            .item(1, "Low → high", if (!st.sortDesc) R.drawable.ic_check else R.drawable.ic_blank)
            .item(2, "Clear sort", R.drawable.ic_close, dividerAfter = false)
            .show { id ->
                when (id) { 0 -> st.setSort(row.key, true); 1 -> st.setSort(row.key, false); else -> st.setSort(null) }
                requestRender()
            }
    }

    private fun filterMenu(anchor: View, st: CompareState) {
        val cur = st.filter.owner
        val opts = listOf(OwnerFilter.ALL to "All", OwnerFilter.OWNED to "Owned only", OwnerFilter.DEX to "Pokédex only")
        val m = AppMenu(anchor).header("Show")
        opts.forEachIndexed { i, (f, label) -> m.item(i, label, if (f == cur) R.drawable.ic_check else R.drawable.ic_blank) }
        m.show { id -> st.filter = st.filter.copy(owner = opts[id].first); requestRender() }
    }

    /** The menu of a column head: make it the reference, move it, take it off. [pos] is its place among the shown columns. */
    private fun headMenu(anchor: View, data: CompareData, display: List<Int>, pos: Int) {
        val st = cmp ?: return
        val idx = display[pos]
        val col = data.columns[idx]
        val vs = st.mode == CompareMode.VS_REFERENCE
        val m = AppMenu(anchor).header(col.name)
        if (vs && pos != 0) m.item(0, "Make reference", R.drawable.ic_star)
        if (st.canMove(data, idx, -1)) m.item(1, "Move left", R.drawable.ic_back)
        if (st.canMove(data, idx, 1)) m.item(2, "Move right", R.drawable.ic_chevron)
        if (st.size > Compare.MIN) m.item(3, "Remove", R.drawable.ic_trash, danger = true)
        else m.item(3, "Remove", R.drawable.ic_trash, enabled = false, sub = "Compare needs at least ${Compare.MIN}", danger = true)
        m.show { id ->
            when (id) {
                0 -> st.makeReference(data, idx)
                1 -> st.move(data, idx, -1)
                2 -> st.move(data, idx, 1)
                else -> st.remove(data, idx)
            }
            requestRender()
        }
    }

    /** "best / 2nd / 3rd" under the table of "best of all". */
    private fun legend(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun key(label: String, color: Int?) {
            if (color != null) row.addView(View(this).apply { background = Ui.rounded(color, 3, this@ComparePage) }, LinearLayout.LayoutParams(dp(12), dp(12)).also { it.marginEnd = dp(4) })
            row.addView(Ui.text(this, 11f, 800, R.color.ag_sub0).apply { text = label }, LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = dp(10) })
        }
        val card = c(R.color.ag_card)
        key("best", Bits.blend(c(R.color.ag_yellow), card, 0.5f))
        key("2nd", Bits.blend(c(R.color.ag_peach), card, 0.36f))
        key("3rd", Bits.blend(c(R.color.ag_peach), card, 0.16f))
        key("ties share a colour", null)
        return row
    }

    // ---------- the Rows sheet ----------

    private fun showRows(data: CompareData) {
        val items = rowItems(data)
        val dialog = Dialog(this).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        val maxBody = (resources.displayMetrics.heightPixels * 0.9f).toInt() - dp(150)
        dialog.setContentView(rowsSheet(items, maxBody.coerceAtLeast(dp(120)), { dialog.dismiss() }))
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(.48f); setGravity(Gravity.BOTTOM)
            if (Ui.findActivity(this@ComparePage) == null) setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        dialog.show()
        dialog.window?.setLayout(minOf(resources.displayMetrics.widthPixels, dp(600)), -2)
    }

    /** One entry of the Rows sheet: the row's key, its label and the section it sits in (null = the top group). */
    internal class RowItem(val key: String, val label: String, val section: String?)

    internal fun rowItems(data: CompareData): List<RowItem> {
        var section: String? = null
        return data.rows.map { r ->
            if (r.section != null) section = r.section
            RowItem(r.key, r.label, section)
        }
    }

    /** The sheet's content (tests render this). Every switch applies at once: it saves and the screen behind redraws. */
    internal fun rowsSheet(items: List<RowItem>, maxBodyPx: Int, close: () -> Unit): View {
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(c(R.color.ag_card))
                val r = Ui.dp(this@ComparePage, 22f); cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(dp(16), dp(6), dp(16), dp(12))
        }
        sheet.addView(View(this).apply { background = Ui.rounded(c(R.color.ag_ov), 2, this@ComparePage) },
            LinearLayout.LayoutParams(dp(40), dp(4)).also { it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = dp(4); it.bottomMargin = dp(6) })
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(Ui.text(this, 17f, 900, R.color.ag_text).apply { text = "Rows"; maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val switches = ArrayList<Pair<String, android.widget.Switch>>()
        head.addView(Ui.text(this, 13f, 800, R.color.ag_sub1).apply {
            text = "Show all"
            minHeight = dp(48)
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(4), 0)
            setOnClickListener { setRows(RowChoice()); switches.forEach { (_, s) -> s.isChecked = true } }
        }, LinearLayout.LayoutParams(-2, -2))
        sheet.addView(head, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = -dp(6) })

        var last: String? = "\u0000"
        for (item in items) {
            if (item.section != last) {
                last = item.section
                body.addView(Ui.text(this, 11f, 900, R.color.ag_sub0).apply {
                    text = (item.section ?: "Overview").uppercase()
                    letterSpacing = 0.06f
                    setPadding(dp(2), dp(10), 0, dp(2))
                })
            }
            val sw = android.widget.Switch(this).apply {
                isChecked = rows.visible(item.key)
                Ui.tintSwitch(this)
            }
            switches += item.key to sw
            sw.setOnCheckedChangeListener { _, on -> if (on != rows.visible(item.key)) setRows(rows.toggle(item.key)) }
            body.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                addView(Ui.text(this@ComparePage, 13f, 800, R.color.ag_text).apply { text = item.label }, LinearLayout.LayoutParams(0, -2, 1f).also { p -> p.marginStart = dp(2) })
                addView(sw, LinearLayout.LayoutParams(-2, -2))
                setOnClickListener { sw.toggle() }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        sheet.addView(object : ScrollView(this) {
            override fun onMeasure(w: Int, h: Int) {
                val cap = if (MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED) maxBodyPx else minOf(maxBodyPx, MeasureSpec.getSize(h))
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST))
            }
        }.apply {
            isVerticalScrollBarEnabled = false
            addView(body, FrameLayout.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(-1, -2))
        sheet.addView(Ui.text(this, 14f, 900, R.color.ag_crust).apply {
            text = "Done"
            gravity = Gravity.CENTER
            minHeight = dp(48)
            background = Ui.rounded(c(R.color.ag_mauve), 14, this@ComparePage)
            setOnClickListener { close() }
        }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(10) })
        return sheet
    }

    private fun setRows(r: RowChoice) {
        rows = r
        CompareStore.saveRows(this, r)
        requestRender()
    }

    /** For tests: called once when the screen creates its state, so a render can start sorted / filtered. */
    internal var onNewState: ((CompareState) -> Unit)? = null
}
