package com.husarp.ankigo.ui

import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.R
import com.husarp.ankigo.game.AchFilter
import com.husarp.ankigo.game.AchGroup
import com.husarp.ankigo.game.AchPageMath
import com.husarp.ankigo.game.AchView
import com.husarp.ankigo.game.Achievements
import com.husarp.ankigo.game.AchievementTable
import java.time.ZoneId

// R4-V2 (PLAN 79, pick 2A): the Achievements section of the Stats tab: a header with the tiers reached per tier, the filter chips
// (All / Close to next / Recently reached), then the rows grouped Study / Battle / Collection / Economy / Special / Badges. A tap on a row
// opens its detail sheet ([AchievementDetail]) over the page. The data is Achievements.view (R4-03).
// R4-V2b: ONE long scrolling list; under the filters a row of group chips JUMPS to each group, and the header of the group being read
// stays pinned at the top while its rows scroll (the next header pushes it out). A filter that leaves a group empty hides the group
// and greys its chip.

/**
 * The Achievements section: [build] makes the body once (scrolling list + the sheet layer), [render] fills it from the game state (call it
 * on every page render while the section is on). [page] gives the context, colours and the game state.
 */
class AchievementsView(private val page: GamePage) {
    private lateinit var col: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var layer: FrameLayout
    private lateinit var sticky: FrameLayout

    // R4-V2b: where the group headers are (filled by render), the chips, and what is pinned now
    private val headers = LinkedHashMap<String, View>()
    private val counts = HashMap<String, String>()
    private val chips = LinkedHashMap<String, TextView>()
    private val chipOff = HashSet<String>()
    private var current: String? = null
    private var pending: (() -> Unit)? = null

    private var filter = AchFilter.ALL
    private var openId: String? = null

    /** The clock and the time zone the dates are worked out in (tests set them). */
    internal var now: () -> Long = { System.currentTimeMillis() }
    internal var zone: ZoneId = ZoneId.systemDefault()

    private fun dp(v: Int) = Ui.dp(page, v)

    fun build(): View {
        col = LinearLayout(page).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(2), dp(16), dp(28))
        }
        scroll = ScrollView(page).apply {
            addView(col)
            setOnScrollChangeListener { _, _, _, _, _ -> updateSticky() }
        }
        // the pinned header: same look as a group header, on the page colour, full width
        sticky = FrameLayout(page).apply {
            setBackgroundColor(Ui.c(page, R.color.ag_base))
            setPadding(dp(16), 0, dp(16), 0)
            visibility = View.GONE
        }
        col.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            pending?.let { pending = null; it() }
            updateSticky()
        }
        layer = FrameLayout(page).apply { visibility = View.GONE }
        return FrameLayout(page).apply {
            addView(scroll, FrameLayout.LayoutParams(-1, -1))
            addView(sticky, FrameLayout.LayoutParams(-1, -2))
            addView(layer, FrameLayout.LayoutParams(-1, -1))
        }
    }

    // ---- jump chips and the pinned header (R4-V2b) ----

    /** Scrolls the list so that group [name] starts at the top. Once the page is laid out it glides; before that it waits for the layout. */
    fun jumpTo(name: String) {
        val h = headers[name] ?: return
        if (h.height > 0) scroll.smoothScrollTo(0, h.top) else pending = { scroll.scrollTo(0, headers[name]?.top ?: 0) }
    }

    /** Test hook: scrolls to [plusPx] below the header of group [name] (after the layout, when there is none yet). */
    internal fun scrollIntoGroup(name: String, plusPx: Int) {
        fun go() { scroll.scrollTo(0, (headers[name]?.top ?: 0) + plusPx) }
        if (headers[name]?.height ?: 0 > 0) go() else pending = { go() }
    }

    /** Test hook: scrolls to [y] px (after the layout, when there is none yet). */
    internal fun scrollToY(y: Int) {
        if (col.height > 0) scroll.scrollTo(0, y) else pending = { scroll.scrollTo(0, y) }
    }

    /** The group names whose chip is greyed (the filter leaves nothing in them). */
    internal fun greyedChips(): Set<String> = chipOff

    /** The group whose header is pinned now, or null above the first one. */
    internal fun pinned(): String? = current

    /** Test hook: true while a pinned header is on screen. */
    internal fun stickyShown(): Boolean = sticky.visibility == View.VISIBLE && sticky.childCount > 0

    /** Takes the pinned header off the screen (the group it was for is [current]'s business). */
    private fun hideSticky() {
        sticky.removeAllViews()
        sticky.visibility = View.GONE
        sticky.translationY = 0f
    }

    /** Pins the header of the group being read at the top (replaced by the next header as it arrives), and marks its chip. */
    private fun updateSticky() {
        val y = scroll.scrollY
        var cur: String? = null
        var next: View? = null
        for ((name, h) in headers) {
            if (h.height == 0) return // not laid out yet: the layout listener comes back
            if (h.top <= y) cur = name else { next = h; break }
        }
        if (cur == null && stickyShown()) hideSticky() // no group to pin (a filter left none, or the list is back above the first): nothing may stay
        if (cur != current) {
            current = cur
            sticky.removeAllViews()
            if (cur != null) sticky.addView(Bits.sect(page, cur, counts[cur]), FrameLayout.LayoutParams(-1, -2))
            sticky.visibility = if (cur == null) View.GONE else View.VISIBLE
            for ((name, tv) in chips) styleChip(tv, name)
        }
        if (cur != null) {
            if (sticky.height == 0) sticky.measure(View.MeasureSpec.makeMeasureSpec(scroll.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
            val sh = if (sticky.height > 0) sticky.height else sticky.measuredHeight
            val gap = if (next == null) Int.MAX_VALUE else next.top - y
            sticky.translationY = if (gap < sh) (gap - sh).toFloat() else 0f
        }
    }

    private fun styleChip(tv: TextView, name: String) {
        val off = name in chipOff
        val on = !off && name == current
        tv.setTextColor(Ui.c(page, if (on) R.color.ag_crust else if (off) R.color.ag_ov else R.color.ag_sub1))
        Ui.touchPill(tv, Ui.rounded(Ui.c(page, if (on) R.color.ag_blue else R.color.ag_card), 12, page), 32, 12)
        tv.alpha = if (off) 0.55f else 1f
        tv.isClickable = !off
    }

    /** One chip per group, in a row that scrolls sideways when the window is narrow. */
    private fun groupChips(groups: List<AchGroup>): View {
        chips.clear(); chipOff.clear()
        val row = LinearLayout(page).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        for (g in groups) {
            val tv = Ui.text(page, 12f, 800, R.color.ag_sub1).apply {
                text = g.name
                if (g.empty) chipOff += g.name
                setOnClickListener { if (g.name !in chipOff) jumpTo(g.name) }
                contentDescription = "Jump to ${g.name}"
            }
            chips[g.name] = tv
            styleChip(tv, g.name)
            row.addView(tv, LinearLayout.LayoutParams(-2, -2).also { if (row.childCount > 0) it.marginStart = dp(6) })
        }
        return HorizontalScrollView(page).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false // the chips glide out to the screen edge, not to the page padding
            setPadding(dp(16), 0, dp(16), 0)
            addView(row)
        }
    }

    /** Opens the detail sheet of achievement [id] (the "See it" of the popup); shown at the next [render]. */
    fun open(id: String?) { openId = id }

    /** Closes the detail sheet. */
    fun close() { openId = null; layer.removeAllViews(); layer.visibility = View.GONE }

    /** Test hook: the filter chip that is on. */
    internal fun setFilter(f: AchFilter) { filter = f }

    fun render() {
        val st = page.state
        val views = Achievements.view(st, page.data)
        val at = now()
        col.removeAllViews()
        current = null; hideSticky() // the pinned header is worked out again after the layout (R4 fix B9: the old one must not stay on screen meanwhile)
        col.addView(summary(views))
        col.addView(filters(), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(10) })

        val groups = AchPageMath.groups(views, filter, at, st.achSince)
        headers.clear(); counts.clear()
        col.addView(groupChips(groups), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(2); it.marginStart = -dp(16); it.marginEnd = -dp(16) })
        var any = false
        for (g in groups) {
            if (g.empty) continue
            any = true
            counts[g.name] = AchPageMath.groupCount(g.all)
            val head = Bits.sect(page, g.name, counts[g.name])
            headers[g.name] = head
            col.addView(head)
            for (v in g.shown) col.addView(row(v), LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = dp(8) })
        }
        if (!any) col.addView(Ui.text(page, 13f, 700, R.color.ag_sub0).apply {
            text = if (filter == AchFilter.CLOSE) "Nothing is close to its next tier yet." else "Nothing reached in the last ${AchPageMath.RECENT_DAYS} days."
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, dp(12))
        }, LinearLayout.LayoutParams(-1, -2))

        layer.removeAllViews()
        val v = openId?.let { id -> views.firstOrNull { it.id == id } }
        if (v == null) { openId = null; layer.visibility = View.GONE }
        else { layer.addView(AchievementDetail.layer(page, v, st.achSince, zone) { close() }, FrameLayout.LayoutParams(-1, -1)); layer.visibility = View.VISIBLE }
    }

    // ---- header ----

    private fun summary(views: List<AchView>): View {
        val card = Ui.card(page).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10)) }
        val left = LinearLayout(page).apply { orientation = LinearLayout.VERTICAL }
        left.addView(Ui.text(page, 26f, 900, R.color.ag_text).apply { text = AchPageMath.tiersReached(views).toString() })
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

    private fun filters(): View {
        val flow = StatsSheets.FlowLayout(page)
        for (f in AchFilter.entries) {
            val on = f == filter
            flow.addView(Ui.text(page, 12f, 800, if (on) R.color.ag_crust else R.color.ag_sub1).apply {
                text = f.label
                Ui.touchPill(this, Ui.rounded(Ui.c(page, if (on) R.color.ag_mauve else R.color.ag_card), 12, page), 32, 12)
                setOnClickListener { if (filter != f) { filter = f; page.requestRender() } }
            })
        }
        return flow
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
