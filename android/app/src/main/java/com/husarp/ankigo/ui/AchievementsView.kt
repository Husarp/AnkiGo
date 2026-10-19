package com.husarp.ankigo.ui

import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import com.husarp.ankigo.R
import com.husarp.ankigo.game.AchFilter
import com.husarp.ankigo.game.AchPageMath
import com.husarp.ankigo.game.AchView
import com.husarp.ankigo.game.Achievements
import com.husarp.ankigo.game.AchievementTable
import java.time.ZoneId

// R4-V2 (PLAN 79, pick 2A): the Achievements section of the Stats tab: a header with the tiers reached per tier, then the rows grouped
// Study / Battle / Collection / Economy / Special / Badges. A tap on a row opens its detail sheet ([AchievementDetail]) over the page.
// The data is Achievements.view (R4-03).
// R10 W2 (D3-A): the groups are TABS with an underline (All · Study · Battle · ...). A tab SHOWS ONLY its group (All = every group under
// its header); nothing jumps and no header is pinned. Under the tabs: "Battle · 12 of 50 tiers" and a "Show: All" button whose menu holds the
// filter (All / Close to next / Recently reached); the filter applies inside the tab. A group the filter leaves empty keeps its tab and says so.

/**
 * The Achievements section: [build] makes the body once (scrolling list + the sheet layer), [render] fills it from the game state (call it
 * on every page render while the section is on). [page] gives the context, colours and the game state.
 */
class AchievementsView(private val page: GamePage) {
    private lateinit var col: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var layer: FrameLayout

    // the tab strip lives as long as the view, so its sideways scroll survives a render
    private lateinit var tabScroll: HorizontalScrollView
    private lateinit var tabRow: LinearLayout
    private lateinit var tabFrame: FrameLayout
    private var selectedTab: View? = null
    private var revealTab = false

    private val rowViews = HashMap<String, View>()
    private var pending: (() -> Unit)? = null

    private var tab = AchPageMath.ALL_TAB
    private var filter = AchFilter.ALL
    private var openId: String? = null
    private var focusId: String? = null

    /** The clock and the time zone the dates are worked out in (tests set them). */
    internal var now: () -> Long = { System.currentTimeMillis() }
    internal var zone: ZoneId = ZoneId.systemDefault()

    private fun dp(v: Int) = Ui.dp(page, v)

    fun build(): View {
        col = LinearLayout(page).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(2), dp(16), dp(28))
        }
        scroll = ScrollView(page).apply { addView(col) }
        tabRow = LinearLayout(page).apply { orientation = LinearLayout.HORIZONTAL }
        tabScroll = HorizontalScrollView(page).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(dp(7), 0, dp(7), 0) // the first tab's own padding brings its text to the page edge (16 dp)
            addView(tabRow)
        }
        tabFrame = FrameLayout(page).apply {
            addView(View(page).apply { setBackgroundColor(Ui.c(page, R.color.ag_s0)) }, FrameLayout.LayoutParams(-1, dp(1), Gravity.BOTTOM))
            addView(tabScroll, FrameLayout.LayoutParams(-1, -2))
        }
        col.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            pending?.let { pending = null; it() }
            if (revealTab) {
                revealTab = false
                selectedTab?.let { t -> tabScroll.scrollTo((t.left + t.width / 2 - tabScroll.width / 2).coerceAtLeast(0), 0) }
            }
        }
        layer = FrameLayout(page).apply { visibility = View.GONE }
        return FrameLayout(page).apply {
            addView(scroll, FrameLayout.LayoutParams(-1, -1))
            addView(layer, FrameLayout.LayoutParams(-1, -1))
        }
    }

    /**
     * Opens the detail sheet of achievement [id] (the "See it" of the popup); shown at the next [render]. That render also selects the row's
     * tab (and lets the row through the filter) and scrolls the row into view behind the sheet. null just clears the sheet.
     */
    fun open(id: String?) {
        openId = id
        if (id != null) focusId = id
    }

    /** Closes the detail sheet. */
    fun close() { openId = null; layer.removeAllViews(); layer.visibility = View.GONE }

    /** Test hook: the filter that is on. */
    internal fun setFilter(f: AchFilter) { filter = f }

    /** Test hook: the filter that is on. */
    internal fun filter(): AchFilter = filter

    /** Test hook: picks a tab (as a tap does, without the render). */
    internal fun setTab(name: String) { tab = name }

    /** Test hook: the tab that is on. */
    internal fun tab(): String = tab

    /** Test hook: scrolls the list to [y] px (after the layout, when there is none yet). */
    internal fun scrollToY(y: Int) {
        if (col.height > 0) scroll.scrollTo(0, y) else pending = { scroll.scrollTo(0, y) }
    }

    /** Test hook: the list's scroll position. */
    internal fun scrollY(): Int = scroll.scrollY

    fun render() {
        val st = page.state
        val views = Achievements.view(st, page.data)
        val at = now()
        // "See it": the row's tab, and the filter out of the way when it would hide the row
        focusId?.let { fid ->
            AchPageMath.tabOf(fid)?.let { tab = it }
            if (!AchPageMath.shows(AchPageMath.groups(views, filter, at, st.achSince), fid)) filter = AchFilter.ALL
        }
        val groups = AchPageMath.groups(views, filter, at, st.achSince)
        tab = AchPageMath.tabOrAll(groups, tab)
        col.removeAllViews()
        rowViews.clear()
        col.addView(summary(views))
        col.addView(tabs(AchPageMath.tabs(groups)), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(8); it.marginStart = -dp(16); it.marginEnd = -dp(16) })
        col.addView(countRow(AchPageMath.countLine(groups, tab)))

        val shown = AchPageMath.tabGroups(groups, tab)
        var any = false
        for (g in shown) {
            if (g.empty) continue
            any = true
            if (tab == AchPageMath.ALL_TAB) col.addView(Bits.sect(page, g.name, AchPageMath.groupCount(g.all)))
            for (v in g.shown) col.addView(row(v).also { rowViews[v.id] = it }, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = dp(8) })
        }
        if (!any) col.addView(Ui.text(page, 13f, 700, R.color.ag_sub0).apply {
            text = AchPageMath.emptyText(filter, tab)
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, dp(12))
        }, LinearLayout.LayoutParams(-1, -2))

        focusId?.let { fid ->
            pending = {
                rowViews[fid]?.let { scroll.scrollTo(0, (it.top - dp(8)).coerceAtLeast(0)) }
                if (focusId == fid) focusId = null
            }
            revealTab = true
        }

        layer.removeAllViews()
        val v = openId?.let { id -> views.firstOrNull { it.id == id } }
        if (v == null) { openId = null; layer.visibility = View.GONE }
        else { layer.addView(AchievementDetail.layer(page, v, st.achSince, zone) { close() }, FrameLayout.LayoutParams(-1, -1)); layer.visibility = View.VISIBLE }
    }

    // ---- header ----

    private fun summary(views: List<AchView>): View {
        val card = Ui.card(page).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(8)) }
        val left = LinearLayout(page).apply { orientation = LinearLayout.VERTICAL }
        left.addView(Ui.text(page, 22f, 900, R.color.ag_text).apply { text = AchPageMath.tiersReached(views).toString() })
        left.addView(Ui.text(page, 11f, 700, R.color.ag_sub0).apply { text = "of ${AchPageMath.tiersTotal(views)} tiers reached" })
        card.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        val counts = AchPageMath.tierCounts(views)
        for (t in counts.indices) {
            val c = LinearLayout(page).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            c.addView(ImageView(page).apply { setImageDrawable(AchStyle.shape(t, AchStyle.tierColor(page, t))); contentDescription = AchievementTable.tierName(t) }, LinearLayout.LayoutParams(dp(16), dp(16)))
            c.addView(Ui.text(page, 12f, 900, R.color.ag_text).apply { text = counts[t].toString() }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = dp(3) })
            card.addView(c, LinearLayout.LayoutParams(dp(34), -2))
        }
        return card
    }

    /** The tabs: text with a 3 dp underline under the one that is on, a hairline under the whole strip; slides sideways when it doesn't fit. */
    private fun tabs(names: List<String>): View {
        tabRow.removeAllViews()
        selectedTab = null
        for (n in names) {
            val on = n == tab
            val t = LinearLayout(page).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = true
                isSelected = on
                contentDescription = if (on) "$n, selected tab" else "$n tab"
                setPadding(dp(9), 0, dp(9), 0)
                setOnClickListener { if (tab != n) { tab = n; revealTab = true; page.requestRender() } }
            }
            t.addView(Ui.text(page, 13.5f, 800, if (on) R.color.ag_text else R.color.ag_sub0).apply {
                text = n
                gravity = Gravity.CENTER
                maxLines = 1
                minHeight = dp(45)
            }, LinearLayout.LayoutParams(-2, -2))
            t.addView(View(page).apply { if (on) background = Ui.rounded(Ui.c(page, R.color.ag_mauve), 2, page) }, LinearLayout.LayoutParams(-1, dp(3)))
            if (on) selectedTab = t
            tabRow.addView(t, LinearLayout.LayoutParams(-2, -2))
        }
        return tabFrame
    }

    /** "Battle · 12 of 50 tiers" on the left, the "Show: All" button on the right (below it when the two don't fit side by side). */
    private fun countRow(line: String): View {
        val row = CountRow(page, dp(8)).apply { setPadding(dp(2), dp(8), dp(2), dp(4)) }
        row.addView(Ui.text(page, 12f, 800, R.color.ag_sub0).apply { text = line }, LinearLayout.LayoutParams(0, -2, 1f))
        val btn = Ui.text(page, 12f, 800, R.color.ag_sub1).apply {
            text = AchPageMath.showLabel(filter)
            Ui.touchPill(this, Ui.rounded(Ui.c(page, R.color.ag_card), 12, page), 30, 12)
            page.getDrawable(R.drawable.ic_expand)?.mutate()?.let { d ->
                d.setBounds(0, 0, dp(16), dp(16))
                d.setTint(Ui.c(page, R.color.ag_sub1))
                setCompoundDrawablesRelative(null, null, d, null)
                compoundDrawablePadding = dp(4)
            }
            contentDescription = "${AchPageMath.showLabel(filter)}, filter menu"
            setOnClickListener { filterMenu(this) }
        }
        row.addView(btn, LinearLayout.LayoutParams(-2, -2))
        return row
    }

    private fun filterMenu(anchor: View) {
        val opts = AchFilter.entries
        val m = AppMenu(anchor).header("Show")
        opts.forEachIndexed { i, f -> m.item(i, f.label, if (f == filter) R.drawable.ic_check else R.drawable.ic_blank) }
        m.show { id -> opts.getOrNull(id)?.let { if (it != filter) { filter = it; page.requestRender() } } }
    }

    /** The count line and the "Show" button: side by side, or the button under the line (right-aligned) when they would not fit. */
    private class CountRow(ctx: android.content.Context, private val gapPx: Int) : LinearLayout(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val avail = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            val label = getChildAt(0)
            val btn = getChildAt(1)
            val un = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            label.measure(un, un); btn.measure(un, un)
            val stack = label.measuredWidth + btn.measuredWidth + gapPx > avail
            val lp = label.layoutParams as LayoutParams
            val bp = btn.layoutParams as LayoutParams
            if (stack) {
                if (orientation != VERTICAL) orientation = VERTICAL
                lp.width = -1; lp.weight = 0f; lp.gravity = Gravity.NO_GRAVITY; bp.gravity = Gravity.END; bp.topMargin = gapPx / 2
            } else {
                if (orientation != HORIZONTAL) orientation = HORIZONTAL
                lp.width = 0; lp.weight = 1f; lp.gravity = Gravity.CENTER_VERTICAL; bp.gravity = Gravity.CENTER_VERTICAL; bp.topMargin = 0; bp.marginStart = gapPx
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    // ---- rows ----

    private fun row(v: AchView): View {
        val card = LinearLayout(page).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(Ui.c(page, R.color.ag_card), 14, page)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            isClickable = true
            setOnClickListener { openId = v.id; page.requestRender() }
        }
        val special = v.oneOff && v.tier >= 0
        card.addView(AchMedal(page, 44).apply { set(AchStyle.icon(v.icon), v.tier, special) }, LinearLayout.LayoutParams(dp(44), dp(44)))
        val body = LinearLayout(page).apply { orientation = LinearLayout.VERTICAL }
        card.addView(body, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = dp(12) })

        val r1 = LinearLayout(page).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        r1.addView(Ui.text(page, 14f, 800, R.color.ag_text).apply { text = v.title; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(r1, LinearLayout.LayoutParams(-1, -2))

        if (v.oneOff) {
            r1.addView(Ui.text(page, 11f, 900, if (special) R.color.ag_green else R.color.ag_sub0).apply { text = if (special) "Earned" else "Not yet" }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dp(8) })
            body.addView(Ui.text(page, 11f, 700, R.color.ag_sub0).apply {
                text = if (special) AchPageMath.reachedText(v.dates.firstOrNull(), page.state.achSince, zone, 0 in v.inferred).replace("Reached", "Earned") else v.desc
                maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(3) })
            return card
        }

        r1.addView(tierTag(v), LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dp(8) })
        val nextTier = (v.tier + 1).coerceAtMost(4)
        body.addView(BarView(page).apply { set(v.progress, AchStyle.tierColor(page, if (v.next == null) v.tier else nextTier), false) },
            LinearLayout.LayoutParams(-1, dp(6)).also { it.topMargin = dp(7) })
        val r3 = LinearLayout(page).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        r3.addView(Ui.text(page, 11f, 700, R.color.ag_sub0).apply {
            text = if (v.next == null) AchPageMath.fmtValue(v) else "${AchPageMath.fmtValue(v)} / ${AchPageMath.fmtNext(v)}"
            maxLines = 1
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (v.next == null) r3.addView(Ui.text(page, 11f, 800, R.color.ag_sub0).apply { text = "Top tier" })
        else {
            r3.addView(Ui.text(page, 11f, 700, R.color.ag_sub0).apply { text = "next" })
            r3.addView(ImageView(page).apply { setImageDrawable(AchStyle.shape(nextTier, AchStyle.tierColor(page, nextTier))) }, LinearLayout.LayoutParams(dp(10), dp(10)).also { it.marginStart = dp(4) })
            r3.addView(Ui.text(page, 11f, 700, R.color.ag_sub0).apply { text = AchievementTable.tierName(nextTier) }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dp(3) })
        }
        body.addView(r3, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(4) })
        return card
    }

    /** "◆ Rare" in the tier's colour, or "Not yet" before the first tier. */
    private fun tierTag(v: AchView): View {
        val tag = LinearLayout(page).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (v.tier < 0) {
            tag.addView(Ui.text(page, 11f, 800, R.color.ag_sub0).apply { text = "Not yet" })
            return tag
        }
        val color = AchStyle.tierColor(page, v.tier)
        tag.addView(ImageView(page).apply { setImageDrawable(AchStyle.shape(v.tier, color)) }, LinearLayout.LayoutParams(dp(11), dp(11)))
        tag.addView(Ui.text(page, 11f, 900, R.color.ag_text).apply { text = AchievementTable.tierName(v.tier); setTextColor(color) }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dp(4) })
        return tag
    }
}
