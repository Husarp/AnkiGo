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
import androidx.core.view.ViewCompat
import com.husarp.ankigo.R
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.SwitchQueue
import com.husarp.ankigo.game.quietUnlessHit
import com.husarp.ankigo.game.shownName

// PLAN item 53 (R3-V5, pick 5A): the Compare screen. Mode switch on top (vs reference / best of all), a chip row (rows, sort,
// filter, fully evolved) and the table of CompareGrid. All the choices live in CompareState; the screen only draws them.

class ComparePage(base: Context, host: PageHost, private val picks: List<ComparePick>) : GamePage(base, host) {
    override val tab: BottomNav.Tab? = null

    private lateinit var bar: AppBar
    private lateinit var holder: FrameLayout
    private var cmp: CompareState? = null
    private var evolved = false
    private var rows: RowChoice = CompareStore.loadRows(this)
    /** How many columns fit (0 = automatic); always automatic when the screen opens (R9 C1), the choice lives as long as the screen. */
    internal var fit: Int = CompareFit.atOpen()
    /** R6-05 item 7: which of the two number groups shows ("Stats" / "Base stats"); remembered. */
    private var group: CompareGroup = CompareStore.loadGroup(this)
    /** R10-F (B9): the folded table sections; remembered. */
    private var folded: SectionChoice = CompareStore.loadCollapsed(this)
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
        // the shown group (a table of Pokédex entries only has base stats); a sort on a row of the hidden group moves to the same row of the shown one
        val shownGroup = CompareGroups.effective(data, group)
        val fixedSort = CompareGroups.sortKeyFor(st.sortKey, shownGroup)
        if (fixedSort != st.sortKey) st.setSort(fixedSort, st.sortDesc)
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
        val view = CompareModes.view(data, display, rows, shownGroup)
        val look = CompareLook(this, view.columns.size, fit, headNotes = view.columns.any { it.note != null })
        val grid = CompareGrid(this, look, view, st.mode, st.sortKey, st.sortDesc,
            onHead = { pos, anchor -> headMenu(anchor, data, display, pos) },
            onLabel = { row, anchor -> tapLabel(row, anchor) },
            onMove = { from, to -> display.getOrNull(from)?.let { st.moveTo(data, it, to); requestRender() } },
            collapsed = folded, onSection = { title -> toggleSection(title) },
            onLabelLong = { row, anchor -> holdLabel(row, anchor) })
        grid.restore(lastX, lastY)
        grid.onScrolled = { x, y -> lastX = x; lastY = y }

        val side = dp(look.sideDp)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val chips = chipViews(data, st)
        if (Ui.shortWindow(this)) {
            // little height: the mode switch becomes two chips at the start of the one chip row
            val all = modeChips(st) + groupChips(data, shownGroup) + chips
            col.addView(SortViews.scrollRow(this, all).also { it.setPadding(side, 0, side, 0); (it as? android.view.ViewGroup)?.clipToPadding = false }, LinearLayout.LayoutParams(-1, -2))
        } else {
            col.addView(switches(data, st, shownGroup), LinearLayout.LayoutParams(-1, -2).also { it.setMargins(side + dp(4), dp(2), side + dp(4), dp(0)) })
            col.addView(SortViews.scrollRow(this, chips).also { it.setPadding(side, 0, side, 0); (it as? android.view.ViewGroup)?.clipToPadding = false }, LinearLayout.LayoutParams(-1, -2))
        }
        col.addView(grid.root, LinearLayout.LayoutParams(-1, 0, 1f).also { it.setMargins(side, 0, side, dp(4)) })
        col.addView(legend(st.mode == CompareMode.VS_REFERENCE), LinearLayout.LayoutParams(-1, -2).also { it.setMargins(side + dp(4), 0, side, dp(6)) })
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

    /** R10-F (B9): a tap on a section header folds / opens its rows; remembered. */
    private fun toggleSection(title: String) {
        folded = folded.toggle(title)
        CompareStore.saveCollapsed(this, folded)
        requestRender()
    }

    private fun setGroup(g: CompareGroup) {
        if (group == g) return
        group = g
        CompareStore.saveGroup(this, g)
        requestRender()
    }

    private fun groupLabel(g: CompareGroup) = if (g == CompareGroup.STATS) "Stats" else "Base stats"

    /** [labels] as one switch (the look of the sort sheet's basis switch); [on] is the lit one. */
    private fun segmented(labels: List<String>, on: Int, sp: Float, pick: (Int) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.rounded(c(R.color.ag_mantle), 12, this@ComparePage)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        labels.forEachIndexed { i, label ->
            val lit = i == on
            row.addView(Ui.text(this, sp, 900, if (lit) R.color.ag_text else R.color.ag_sub0).apply {
                text = label
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(dp(2), dp(11), dp(2), dp(11))
                background = if (lit) Ui.rounded(c(R.color.ag_card), 10, this@ComparePage) else null
                setOnClickListener { pick(i) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        return row
    }

    /** The mode switch, and next to it (when both groups have rows) the "Stats | Base stats" switch; stacked in a very narrow window. */
    private fun switches(data: CompareData, st: CompareState, shown: CompareGroup): View {
        val modes = segmented(CompareMode.entries.map(::modeLabel), st.mode.ordinal, if (CompareGroups.switchable(data)) 12f else 13f) { setMode(st, CompareMode.entries[it]) }
        if (!CompareGroups.switchable(data)) return modes
        val groups = segmented(CompareGroup.entries.map(::groupLabel), shown.ordinal, 12f) { setGroup(CompareGroup.entries[it]) }
        val narrow = resources.configuration.screenWidthDp in 1..339
        return LinearLayout(this).apply {
            orientation = if (narrow) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            if (narrow) {
                addView(modes, LinearLayout.LayoutParams(-1, -2))
                addView(groups, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(4) })
            } else {
                addView(modes, LinearLayout.LayoutParams(0, -2, 1f))
                addView(groups, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = dp(8) })
            }
        }
    }

    private fun modeChips(st: CompareState): List<View> =
        CompareMode.entries.map { m -> BoxViews.chip(this, modeLabel(m), m == st.mode) { setMode(st, m) } }

    private fun groupChips(data: CompareData, shown: CompareGroup): List<View> =
        if (!CompareGroups.switchable(data)) emptyList() else CompareGroup.entries.map { g -> BoxViews.chip(this, groupLabel(g), g == shown) { setGroup(g) } }

    private fun chipViews(data: CompareData, st: CompareState): List<View> {
        val out = ArrayList<View>()
        val all = data.rows.size
        val hidden = data.rows.count { !rows.visible(it.key) }
        out += BoxViews.chip(this, if (hidden == 0) "Rows: all" else "Rows: ${all - hidden} of $all", hidden > 0) { showRows(data) }

        val mixed = data.columns.any { it.mon != null } && data.columns.any { it.mon == null }
        if (mixed || st.filter.owner != OwnerFilter.ALL) {
            lateinit var filterChip: View
            filterChip = BoxViews.chip(this, when (st.filter.owner) { OwnerFilter.ALL -> "Show: all"; OwnerFilter.OWNED -> "Show: owned"; OwnerFilter.DEX -> "Show: Pokédex" },
                st.filter.owner != OwnerFilter.ALL) { filterMenu(filterChip, st) }
            out += filterChip
        }
        out += BoxViews.chip(this, "Fully evolved", evolved) { evolved = !evolved; requestRender() }
        lateinit var zoomChip: View
        zoomChip = BoxViews.chip(this, "Columns: ${CompareFit.label(fit).lowercase()}", fit != CompareFit.AUTO) { zoomMenu(zoomChip) }
        out += zoomChip
        return out
    }

    /**
     * R10-G (B12): a tap on a number row's label sorts the columns by it: high to low, then low to high, then no sort ([CompareSortCycle]).
     * Another row says what it is.
     */
    private fun tapLabel(row: CompareRow, anchor: View) {
        val st = cmp ?: return
        if (!row.numeric) { rowInfo(row); return }
        val (key, desc) = CompareSortCycle.next(st.sortKey, st.sortDesc, row.key)
        st.setSort(key, desc)
        requestRender()
    }

    /** R10-G (B12): a long press on a label: a number row opens its sort menu (with the description), another row says what it is. One action only. */
    private fun holdLabel(row: CompareRow, anchor: View) {
        if (row.numeric) sortMenu(row, anchor) else rowInfo(row)
    }

    private fun rowInfo(row: CompareRow) {
        CompareInfo.forRow(row.key, row.label)?.let { DetailParts.showInfo(this, it) }
    }

    /** R4-X1 item 9: the stat's title and text on top, then High to low / Low to high / Clear sort. */
    private fun sortMenu(row: CompareRow, anchor: View) {
        val st = cmp ?: return
        val info = CompareInfo.forRow(row.key, row.label)
        val on = st.sortKey == row.key
        val m = AppMenu(anchor).header(info?.title ?: row.label, info?.text)
            .item(0, "High to low", R.drawable.ic_sort_desc, sub = if (on && st.sortDesc) "Sorted like this now" else null, tone = if (on && st.sortDesc) R.color.ag_mauve else 0)
            .item(1, "Low to high", R.drawable.ic_sort_asc, sub = if (on && !st.sortDesc) "Sorted like this now" else null, tone = if (on && !st.sortDesc) R.color.ag_mauve else 0)
        if (on) m.item(2, "Clear sort", R.drawable.ic_close)
        m.show { id ->
            when (id) { 0 -> st.setSort(row.key, true); 1 -> st.setSort(row.key, false); else -> st.setSort(null) }
            requestRender()
        }
    }

    /** R4-X1 item 12: how many columns fit on the screen; everything in the table scales with it. Not remembered for the next time (R9 C1). */
    private fun zoomMenu(anchor: View) {
        val opts = listOf(CompareFit.AUTO) + CompareFit.OPTIONS
        val m = AppMenu(anchor).header("Columns on screen", "Everything in the table scales to fit")
        opts.forEachIndexed { i, o ->
            m.item(i, if (o == CompareFit.AUTO) "Auto (by window width)" else "$o columns", if (o == fit) R.drawable.ic_check else R.drawable.ic_blank)
        }
        m.show { id ->
            fit = opts[id]
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

    /**
     * The menu of a column head: make it the reference or take it off (moving is a long-press drag on the head, see [CompareGrid]).
     * R10-G (B13): under those, the team actions of an owned Pokémon (same rules and messages as [MonActions]) and "Details".
     * [pos] is its place among the shown columns.
     */
    private fun headMenu(anchor: View, data: CompareData, display: List<Int>, pos: Int) {
        val st = cmp ?: return
        val idx = display[pos]
        val col = data.columns[idx]
        val vs = st.mode == CompareMode.VS_REFERENCE
        val tags = col.mon?.let { MonTags.of(this.data, this.state, it).map { t -> t.label } }.orEmpty()
        val m = AppMenu(anchor).header(col.name, CompareInfo.headInfo(col.mon == null, tags, col.note))
        val acts = HashMap<Int, () -> Unit>()
        fun add(id: Int, label: String, icon: Int, enabled: Boolean = true, sub: String? = null, danger: Boolean = false, tone: Int = 0, f: () -> Unit = {}) {
            m.item(id, label, icon, enabled = enabled, sub = sub, danger = danger, tone = tone)
            acts[id] = f
        }
        if (vs && pos != 0) add(0, "Make reference", R.drawable.ic_star) { st.makeReference(data, idx); requestRender() }
        if (st.size > Compare.MIN) add(3, "Remove from compare", R.drawable.ic_trash, danger = true) { st.remove(data, idx); requestRender() }
        else add(3, "Remove from compare", R.drawable.ic_trash, enabled = false, sub = "Compare needs at least ${Compare.MIN}", danger = true)

        val mon = col.mon?.let { this.state.mon(it.uid) }
        if (mon != null) {
            val team = this.state.team
            val inTeam = mon.uid in team
            val main = this.state.main
            if (inTeam) {
                if (team.size > 1) add(4, "Remove from team", R.drawable.ic_team) { act { it.toBox(mon.uid) } }
                else add(4, "Remove from team", R.drawable.ic_team, enabled = false, sub = "Your team needs at least one Pokémon.")
            } else if (team.size < 6) {
                add(4, "Add to team", R.drawable.ic_team) { act { it.toTeam(mon.uid) } }
            } else {
                add(4, "Add to team…", R.drawable.ic_team) {
                    SwitchPopup.show(this, this.data, this.state, header = "Pick a team Pokémon to swap out", swap = true) { outUid -> act { g -> g.swapIntoTeam(mon.uid, outUid) } }
                }
            }
            if (!inTeam || team.indexOf(mon.uid) > 0) {
                when {
                    inTeam -> add(5, "Set as main", R.drawable.ic_star, sub = SwitchQueue.MENU_SUB.takeIf { SwitchQueue.fightOn(this.state) }, tone = R.color.ag_yellow) { act { g -> g.requestMain(mon.uid).quietUnlessHit() } } // PLAN 206: in a fight it waits for the next answer; the change is visible: no toast (unless it cost a hit)
                    team.size < 6 -> add(5, "Set as main", R.drawable.ic_star, sub = SwitchQueue.MENU_SUB.takeIf { SwitchQueue.fightOn(this.state) }, tone = R.color.ag_yellow) {
                        act { g -> g.toTeam(mon.uid).let { r -> if (r.ok) g.requestMain(mon.uid).quietUnlessHit() else r } }
                    }
                    main != null -> add(5, "Set as main", R.drawable.ic_star, sub = "${main.shownName} goes to the box", tone = R.color.ag_yellow) {
                        act { g -> g.swapIntoTeam(mon.uid, main.uid).quietUnlessHit() }
                    }
                }
            }
            add(6, "Details", R.drawable.ic_info) { openMon(this, this.data, mon) }
        } else if (col.mon == null) {
            add(6, "Details", R.drawable.ic_info) { openDex(this, col.picked) }
        }
        m.show { id -> acts[id]?.invoke() }
    }

    /** Where "Details" goes (the tests swap them to see which Pokémon / species is opened). */
    internal var openMon: (GamePage, GameData, Mon) -> Unit = { a, data, mon -> MonDetail.show(a, data, mon) }
    internal var openDex: (GamePage, Species) -> Unit = { a, sp -> DexDetail.show(a, sp) }

    /**
     * The colour bar under the table. "Best of all": worst - best, every number is coloured by its value in its row. "Vs reference"
     * (R10-F B8): worse - better than the reference, every number is coloured by how far it is from the reference's.
     */
    private fun legend(vsRef: Boolean): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun word(label: String, end: Int) = row.addView(Ui.text(this, 11f, 800, R.color.ag_sub0).apply { text = label },
            LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = end })
        val card = c(R.color.ag_card)
        val steps = CompareScale.legend(card, TypeChips.isDark(this)) // the mockup's 21 steps, worst to best
        word(if (vsRef) "worse" else "worst", dp(6))
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply { setColor(card); cornerRadius = Ui.dp(this@ComparePage, 4f) }
            clipToOutline = true
            for (col in steps) addView(View(this@ComparePage).apply { setBackgroundColor(col) }, LinearLayout.LayoutParams(0, -1, 1f))
        }, LinearLayout.LayoutParams(dp(105), dp(12)).also { it.marginEnd = dp(6) })
        if (vsRef) word("better than the reference", 0)
        else { word("best", dp(10)); word("same value, same colour", 0) }
        return row
    }

    // ---------- the Rows sheet ----------

    private fun showRows(data: CompareData) {
        val shown = CompareGroups.effective(data, group)
        val items = rowItems(data).filter { CompareGroups.shows(it.key, shown) } // the rows of the hidden group would change nothing on screen
        val dialog = Dialog(this).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        val maxBody = (resources.displayMetrics.heightPixels * 0.9f).toInt() - dp(150)
        dialog.setContentView(SheetKit.dialog(dialog, rowsSheet(items, maxBody.coerceAtLeast(dp(120)), { dialog.dismiss() })))
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
        // R10-G (B7): the abbreviation as the table shows it, big, in a fixed column; beside it the full name and one sentence, small and grey
        val labelCol = dp(84)
        val gap = dp(10)
        for (item in items) {
            if (item.section != last) {
                val first = last == "\u0000"
                last = item.section
                // a section header: accent, bigger, with a rule and room above it, so it never reads like a row
                if (!first) body.addView(View(this).apply { setBackgroundColor(c(R.color.ag_ov)) }, LinearLayout.LayoutParams(-1, dp(1)).also { it.topMargin = dp(16) })
                body.addView(Ui.text(this, 14f, 900, R.color.ag_mauve).apply {
                    text = (item.section ?: "Overview").uppercase()
                    letterSpacing = 0.08f
                    setPadding(dp(2), if (first) dp(8) else dp(12), 0, dp(4))
                    ViewCompat.setAccessibilityHeading(this, true)
                })
            }
            val (full, sentence) = CompareRowText.of(item.key, item.label)
            val sw = android.widget.Switch(this).apply {
                isChecked = rows.visible(item.key)
                contentDescription = "Show $full"
                Ui.tintSwitch(this)
            }
            switches += item.key to sw
            sw.setOnCheckedChangeListener { _, on -> if (on != rows.visible(item.key)) setRows(rows.toggle(item.key)) }
            body.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(52)
                setPadding(0, dp(4), 0, dp(4))
                addView(Ui.text(this@ComparePage, 20f, 900, R.color.ag_text).apply {
                    text = item.label
                    gravity = Gravity.CENTER_VERTICAL
                    hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
                    // a word is never broken: a long label ("against") shrinks until its longest word fits the column
                    val avail = (labelCol - dp(2)).toFloat()
                    val word = CompareLabelFit.longestWord(item.label)
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, CompareLabelFit.sp(textSize, android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics), paint.measureText(word), avail))
                }, LinearLayout.LayoutParams(labelCol, -2).also { p -> p.marginStart = dp(2) })
                addView(LinearLayout(this@ComparePage).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(Ui.text(this@ComparePage, 12f, 800, R.color.ag_sub1).apply { text = full })
                    if (sentence.isNotEmpty()) addView(Ui.text(this@ComparePage, 11f, 700, R.color.ag_sub0).apply { text = sentence })
                }, LinearLayout.LayoutParams(0, -2, 1f).also { p -> p.marginStart = gap; p.marginEnd = gap })
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
