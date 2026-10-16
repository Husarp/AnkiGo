package com.husarp.ankigo.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.util.TypedValue
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Badges
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.shownName
import com.husarp.ankigo.game.Progression
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.Trainer

// The Team tab: one screen with three sections (Team / Box / Pokedex), a Compare picking mode and a team Edit mode.
// Team = trainer card + team rows (a ScrollView). Box = a ListView of 6-cell rows (recycled). Pokedex = DexPage embedded.
// Tap = details, long-press = the actions menu. Edit mode shows up/down arrows on the team rows (no drag and drop:
// framework Views have no easy drag list).
class TeamPage(base: Context, host: PageHost, start: TeamSection? = null) : GamePage(base, host), PickHost {
    override val tab = BottomNav.Tab.TEAM

    private lateinit var appBar: AppBar
    private lateinit var compareBtn: ImageView
    private lateinit var switchBox: LinearLayout
    private lateinit var teamScroll: ScrollView
    private lateinit var teamCol: LinearLayout
    private lateinit var boxList: ListView
    private lateinit var boxHeader: LinearLayout
    private lateinit var unsupportedBox: LinearLayout // "Not supported yet" (PLAN 67), the end of the Box list
    private lateinit var dexBody: View
    private lateinit var pickBar: LinearLayout
    private lateinit var pickHint: TextView
    private lateinit var pickGo: TextView
    private val actions by lazy { MonActions(this) }
    private val dexPage by lazy { DexPage(this, host, this) }
    private var tiles: List<BoxTile> = emptyList()
    private var shown = 0 // Pokémon behind the tiles (after the filter)

    // The Team tab always opens on Team (R1-02): the section chosen inside a visit is kept only while this page lives.
    private var section: TeamSection = start ?: TeamSection.TEAM
    private var editing = false
    private var pickOn = false
    private val picks = PickSet()
    private val detailsOnce = DetailsOnce()

    // The Box lists every owned Pokémon (team members carry a mark); copies of a species collapse into one "×N" tile.
    private var boxFilter = BoxFilter.ALL
    private var grouped = AppPrefs.boxGrouped(base)

    override val picking: Boolean get() = pickOn
    override fun isPicked(p: ComparePick): Boolean = picks.has(p)

    private val adapter = object : BaseAdapter() {
        override fun getCount() = (tiles.size + COLS - 1) / COLS
        override fun getItem(position: Int): Any = position
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? CellRow) ?: CellRow(this@TeamPage, COLS, CELL_RATIO, 4, 12) { f -> makeCell(f) }
            for (c in 0 until COLS) bindCell(row.cells[c], tiles.getOrNull(position * COLS + c))
            return row
        }
    }

    override fun buildBar(): View {
        appBar = Ui.appBar(this, section.label, "0/6")
        compareBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_compare)
            contentDescription = "Compare"
            val pad = Ui.dp(this@TeamPage, 12)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(Ui.dp(this@TeamPage, 48), Ui.dp(this@TeamPage, 48)).also {
                it.setMargins(0, -Ui.dp(this@TeamPage, 8), Ui.dp(this@TeamPage, 2), -Ui.dp(this@TeamPage, 8))
            }
            setOnClickListener { if (pickOn) endPick() else startPick() }
        }
        appBar.addView(compareBtn, appBar.childCount - 1) // before the right-hand pill
        tintCompare()
        return appBar
    }

    override fun buildBody(): View {
        val gap = Ui.dp(this, 12)
        switchBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        teamCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(gap, 0, gap, gap)
        }
        teamScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(teamCol, FrameLayout.LayoutParams(-1, -2))
        }

        boxHeader = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(gap, 0, gap, 0)
        }
        unsupportedBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(gap, Ui.dp(this@TeamPage, 8), gap, 0)
        }
        boxList = ListView(this).apply {
            divider = null
            dividerHeight = 0
            setSelector(ColorDrawable(Color.TRANSPARENT))
            setPadding(0, 0, 0, gap)
            clipToPadding = false
            addHeaderView(boxHeader, null, false)
            addFooterView(unsupportedBox, null, false)
            adapter = this@TeamPage.adapter
        }
        dexBody = dexPage.embeddedBody()

        pickHint = Ui.text(this, 12f, 700, R.color.ag_sub0).apply { maxLines = 2 }
        pickGo = Ui.text(this, 13f, 900, R.color.ag_crust).apply { maxLines = 1 }
        val cancel = Ui.text(this, 13f, 800, R.color.ag_text).apply {
            text = "Cancel"
            maxLines = 1
            Ui.touchPill(this, Ui.rounded(Ui.c(this@TeamPage, R.color.ag_s0), 12, this@TeamPage), 40, 16)
            setOnClickListener { endPick() }
        }
        pickBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setBackgroundColor(Ui.c(this@TeamPage, R.color.ag_mantle))
            setPadding(Ui.dp(this@TeamPage, 16), Ui.dp(this@TeamPage, 4), Ui.dp(this@TeamPage, 12), Ui.dp(this@TeamPage, 4))
            addView(pickHint, LinearLayout.LayoutParams(0, -2, 1f))
            addView(cancel, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(this@TeamPage, 8) })
            addView(pickGo, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(this@TeamPage, 8) })
        }
        pickGo.setOnClickListener { if (picks.canCompare) Compare.open(this, picks.picks) }

        val content = FrameLayout(this).apply {
            addView(teamScroll, FrameLayout.LayoutParams(-1, -1))
            addView(boxList, FrameLayout.LayoutParams(-1, -1))
            addView(dexBody, FrameLayout.LayoutParams(-1, -1))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(switchBox, LinearLayout.LayoutParams(-1, -2).also { it.setMargins(gap, 0, gap, Ui.dp(this@TeamPage, 4)) })
            addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(pickBar, LinearLayout.LayoutParams(-1, -2))
        }
    }

    override fun render() {
        val st = state
        picks.keepOwned { st.mon(it) != null }
        val team = st.teamMons()
        val mons = BoxTiles.filter(st.mons, boxFilter)
        shown = mons.size
        tiles = BoxTiles.build(data, mons, grouped && !pickOn) // while picking for Compare every copy is its own tile
        if (team.size < 2) editing = false
        drawSwitch()
        drawBar(team.size)
        drawTeam(team)
        drawBoxHeader()
        UnsupportedBox.bind(unsupportedBox, st)
        adapter.notifyDataSetChanged()
        dexPage.renderEmbedded()
        teamScroll.visibility = if (section == TeamSection.TEAM) View.VISIBLE else View.GONE
        boxList.visibility = if (section == TeamSection.BOX) View.VISIBLE else View.GONE
        dexBody.visibility = if (section == TeamSection.DEX) View.VISIBLE else View.GONE
        drawPickBar()
        // A battle asked for a Pokémon's details (PLAN 86): open them once, after the first render.
        detailsOnce.take { st.mon(it) != null }?.let { uid -> st.mon(uid)?.let { MonDetail.show(this, data, it) } }
    }

    // ---- sections ----

    private fun showSection(s: TeamSection) {
        if (s == section) return
        section = s
        editing = false
        requestRender()
    }

    /** Test hook: opens the page on [s]. */
    internal fun preset(
        s: TeamSection = section, edit: Boolean = false, pick: List<ComparePick>? = null,
        filter: BoxFilter = boxFilter, group: Boolean = grouped,
    ) {
        section = s
        editing = edit
        boxFilter = filter
        grouped = group
        if (pick != null) {
            pickOn = true
            picks.clear()
            pick.forEach { picks.toggle(it) }
        }
    }

    /** The segmented Team / Box / Pokédex switch (same look as the Pokédex All / Owned / Not owned switch). */
    private fun drawSwitch() {
        val ctx = this
        switchBox.removeAllViews()
        switchBox.background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 12, ctx)
        switchBox.setPadding(Ui.dp(ctx, 3), Ui.dp(ctx, 3), Ui.dp(ctx, 3), Ui.dp(ctx, 3))
        for (s in TeamSection.entries) {
            val on = s == section
            val tone = if (on) R.color.ag_text else R.color.ag_sub0
            val seg = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                minimumHeight = Ui.dp(ctx, 42) // 42 dp segments in a 48 dp control
                background = if (on) Ui.rounded(Ui.c(ctx, R.color.ag_card), 10, ctx) else null
                contentDescription = s.label
                setOnClickListener { showSection(s) }
            }
            seg.addView(ImageView(ctx).apply {
                setImageResource(s.icon)
                setColorFilter(Ui.c(ctx, if (on) R.color.ag_mauve else R.color.ag_sub0), PorterDuff.Mode.SRC_IN)
            }, LinearLayout.LayoutParams(Ui.dp(ctx, 18), Ui.dp(ctx, 18)).also { it.marginEnd = Ui.dp(ctx, 5) })
            seg.addView(Ui.text(ctx, 12f, 900, tone).apply { text = s.label; maxLines = 1 })
            switchBox.addView(seg, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun drawBar(teamSize: Int) {
        appBar.titleView.text = section.label
        appBar.setPill(
            when (section) {
                TeamSection.TEAM -> "$teamSize/6"
                TeamSection.BOX -> "${state.mons.size} Pokémon"
                TeamSection.DEX -> dexPage.pillText()
            },
        )
        tintCompare()
    }

    private fun tintCompare() {
        compareBtn.setColorFilter(Ui.c(this, if (pickOn) R.color.ag_mauve else R.color.ag_sub1), PorterDuff.Mode.SRC_IN)
    }

    // ---- Compare picking ----

    private fun startPick() {
        pickOn = true
        editing = false
        requestRender()
    }

    /** Tap on your Pokémon in a battle: open [uid]'s details once, after the next render. */
    fun showDetailsOnce(uid: String) {
        detailsOnce.ask(uid)
        requestRender()
    }

    /** Compare from a Pokémon's actions menu: picking mode on, with [uid] already ticked. */
    override fun startPickWith(uid: String) {
        pickOn = true
        editing = false
        picks.clear()
        picks.toggle(ComparePick.Owned(uid))
        requestRender()
    }

    private fun endPick() {
        pickOn = false
        picks.clear()
        requestRender()
    }

    override fun togglePick(p: ComparePick) {
        if (picks.toggle(p) == PickSet.Result.FULL) Ui.toast(this, "You can compare up to ${Compare.MAX} Pokémon")
        refreshPicks()
    }

    /**
     * A tick changed: update only the tick (frame + badge) of the rows and cells, and the pick bar. The rows are not rebuilt
     * (that would restart their running sprite animations) and the lists are not re-bound.
     */
    private fun refreshPicks() {
        for ((uid, r) in teamRows) {
            val picked = picks.has(ComparePick.Owned(uid))
            r.row.background = PickKit.cardBg(this, 14, picked)
            PickKit.bindBadge(r.badge, true, picked)
        }
        for (i in 0 until boxList.childCount) {
            val row = boxList.getChildAt(i) as? CellRow ?: continue
            for (f in row.cells) bindPick(f)
        }
        dexPage.refreshCells()
        drawPickBar()
    }

    private fun drawPickBar() {
        pickBar.visibility = if (pickOn) View.VISIBLE else View.GONE
        if (!pickOn) return
        val n = picks.size
        pickHint.text = if (n < Compare.MIN) "Tick ${Compare.MIN}–${Compare.MAX} Pokémon" else "$n ticked"
        pickGo.text = "Compare ($n)"
        val ok = picks.canCompare
        pickGo.setTextColor(Ui.c(this, if (ok) R.color.ag_crust else R.color.ag_sub0))
        Ui.touchPill(pickGo, Ui.rounded(Ui.c(this, if (ok) R.color.ag_mauve else R.color.ag_s0), 12, this), 40, 16)
        pickGo.isEnabled = ok
    }

    // ---- team section ----

    private class TeamRow(val row: View, val badge: ImageView)

    /** The team rows by uid, for [refreshPicks]; only filled while picking (the badge exists then). */
    private val teamRows = HashMap<String, TeamRow>()

    private fun drawTeam(team: List<Mon>) {
        teamRows.clear()
        teamCol.removeAllViews()
        teamCol.addView(trainerCard())
        teamCol.addView(teamHeader(team.size))
        team.forEachIndexed { i, mon -> teamCol.addView(teamRow(mon, i, team.size)) }
    }

    /** "TEAM" + the Edit / Done pill, and a hint line. */
    private fun teamHeader(size: Int): View {
        val ctx = this
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(Ui.text(ctx, 12f, 900, R.color.ag_sub0).apply {
            text = "TEAM"
            letterSpacing = 0.058f
            setPadding(Ui.dp(ctx, 4), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (size > 1 && !pickOn) row.addView(iconPill(if (editing) "Done" else "Edit", if (editing) R.drawable.ic_check else R.drawable.ic_pencil, editing) {
            editing = !editing
            drawTeam(state.teamMons())
        })
        col.addView(row, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 6) })
        col.addView(Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply {
            text = when {
                editing -> "Use the arrows to change the order. Place 1 is your main."
                pickOn -> "Tap a Pokémon to tick it for Compare."
                else -> "Tap for details · hold for actions"
            }
            setPadding(Ui.dp(ctx, 4), 0, Ui.dp(ctx, 4), Ui.dp(ctx, 8))
            if (Ui.shortWindow(ctx)) visibility = View.GONE // split screen: no room for the hint line
        })
        return col
    }

    /** A small pill with an icon and a label; 48 dp touch height around the 28 dp look. */
    private fun iconPill(label: String, icon: Int, strong: Boolean, onClick: () -> Unit): View {
        val ctx = this
        val fill = if (strong) Ui.c(ctx, R.color.ag_mauve) else Ui.c(ctx, R.color.ag_mantle)
        val ink = if (strong) R.color.ag_crust else R.color.ag_text
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = Ui.dp(ctx, 48)
            background = InsetDrawable(Ui.rounded(fill, 10, ctx), 0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 10))
            setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12), 0)
            addView(ImageView(ctx).apply {
                setImageResource(icon)
                setColorFilter(Ui.c(ctx, ink), PorterDuff.Mode.SRC_IN)
            }, LinearLayout.LayoutParams(Ui.dp(ctx, 14), Ui.dp(ctx, 14)).also { it.marginEnd = Ui.dp(ctx, 5) })
            addView(Ui.text(ctx, 12f, 800, ink).apply { text = label; maxLines = 1 })
            setOnClickListener { onClick() }
        }
    }

    private fun drawBoxHeader() {
        boxHeader.removeAllViews()
        val someGroup = tiles.any { it.isGroup }
        boxHeader.addView(Bits.sect(this, "Box", "$shown Pokémon" + if (someGroup) " · grouped" else ""))
        boxHeader.addView(boxControls(), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = -Ui.dp(this, 6) })
        boxHeader.addView(Ui.text(this, 11f, 700, R.color.ag_sub0).apply {
            text = when {
                pickOn -> "Tap a Pokémon to tick it for Compare."
                someGroup -> "Tap for details · hold for actions · a ×N tile opens its copies"
                else -> "Tap for details · hold for actions"
            }
            setPadding(Ui.dp(this@TeamPage, 4), 0, Ui.dp(this@TeamPage, 4), Ui.dp(this@TeamPage, 8))
            visibility = if (tiles.isEmpty() || Ui.shortWindow(this@TeamPage)) View.GONE else View.VISIBLE
        })
        if (tiles.isEmpty()) {
            boxHeader.addView(Ui.text(this, 13f, 700, R.color.ag_sub0).apply {
                text = if (boxFilter == BoxFilter.FAVOURITES) "No favourites yet. Hold a Pokémon and pick “Add to favourites”."
                else "Your box is empty. Pokémon you catch beyond six wait here."
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(this@TeamPage, 28), 0, Ui.dp(this@TeamPage, 12))
            })
        }
    }

    /** The filter chips "All" / "♥ Favourites" and the "Group duplicates" switch (hidden while picking: copies are ungrouped then). */
    private fun boxControls(): View {
        val ctx = this
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(BoxViews.chip(ctx, "All", boxFilter == BoxFilter.ALL) { setBoxFilter(BoxFilter.ALL) })
        row.addView(BoxViews.chip(ctx, "Favourites", boxFilter == BoxFilter.FAVOURITES, heart = true) { setBoxFilter(BoxFilter.FAVOURITES) },
            LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(ctx, 6) })
        if (pickOn) return row
        val sw = android.widget.Switch(ctx).apply {
            isChecked = grouped
            Ui.tintSwitch(this)
            setOnCheckedChangeListener { _, on -> grouped = on; AppPrefs.setBoxGrouped(ctx, on); requestRender() }
        }
        row.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            minimumHeight = Ui.dp(ctx, 48)
            addView(Ui.text(ctx, 11f, 800, R.color.ag_sub1).apply {
                text = "Group duplicates"
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginEnd = Ui.dp(ctx, 6) })
            addView(sw, LinearLayout.LayoutParams(-2, -2))
            setOnClickListener { sw.toggle() }
        }, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 8) })
        return row
    }

    private fun setBoxFilter(f: BoxFilter) {
        if (f == boxFilter) return
        boxFilter = f
        requestRender()
    }

    /** A group tile (hold or tap): the sheet with all copies. A copy's details / actions work as everywhere else. */
    private fun openCopies(tile: BoxTile) {
        BoxCopiesSheet.show(this, tile,
            onOpen = { MonDetail.show(this, data, it) },
            onMenu = { mon, anchor, done -> actions.menu(mon, anchor, done) })
    }

    // ---- trainer card ----

    private fun trainerCard(): View {
        val st = state
        val card = Ui.card(this).apply {
            setPadding(Ui.dp(this@TeamPage, 14), Ui.dp(this@TeamPage, 12), Ui.dp(this@TeamPage, 14), Ui.dp(this@TeamPage, 12))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(Ui.text(this, 17f, 900, R.color.ag_text).apply { text = st.settings.trainerName; maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
        val short = Ui.shortWindow(this) // split screen: badges go next to the coins, saving a row
        val badges = Ui.chip(this, R.drawable.ic_star, R.color.ag_yellow).apply { setText("Badges ${st.badges.size}/${Badges.TITLES.size}") }
        // 48 dp touch height around the small chip
        val badgesBox = FrameLayout(this).apply {
            minimumHeight = Ui.dp(this@TeamPage, 48)
            isClickable = true
            setOnClickListener { showBadges() }
            addView(badges, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL))
        }
        // Stats chip (T40): same 48 dp touch height as Badges, opens the Stats screen
        val statsBox = FrameLayout(this).apply {
            minimumHeight = Ui.dp(this@TeamPage, 48)
            isClickable = true
            setOnClickListener { startScreen(Intent(this@TeamPage, StatsActivity::class.java)) }
            addView(Ui.chip(this@TeamPage, R.drawable.ic_flame, R.color.ag_peach).also { it.setText("Stats") }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL))
        }
        if (short) {
            top.addView(badgesBox, LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = Ui.dp(this, 6); it.topMargin = -Ui.dp(this, 12); it.bottomMargin = -Ui.dp(this, 12) })
            top.addView(statsBox, LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = Ui.dp(this, 6); it.topMargin = -Ui.dp(this, 12); it.bottomMargin = -Ui.dp(this, 12) })
        }
        top.addView(Ui.chip(this, R.drawable.ic_coin, R.color.ag_yellow).also { it.setText(Bits.fmt(st.cash)) })
        card.addView(top)

        val need = Trainer.xpForNext(st.trainerLevel)
        val mid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        mid.addView(Ui.text(this, 12f, 800, R.color.ag_sub1).apply { text = "Trainer Lv ${st.trainerLevel} · ${Trainer.rank(st)}" }, LinearLayout.LayoutParams(0, -2, 1f))
        mid.addView(Ui.text(this, 11f, 800, R.color.ag_sub0).apply { text = "XP ${st.trainerXp} / $need" })
        card.addView(mid, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(this, 4) })

        val bar = BarView(this)
        bar.setMeaning(BarMeaning.XP, st.trainerXp.toFloat() / need.coerceAtLeast(1), false)
        card.addView(bar, LinearLayout.LayoutParams(-1, Ui.dp(this, 7)).also { it.topMargin = Ui.dp(this, 7) })

        if (!short) {
            val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            chips.addView(badgesBox, LinearLayout.LayoutParams(-2, -2))
            chips.addView(statsBox, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(this, 8) })
            card.addView(chips, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = -Ui.dp(this, 2); it.bottomMargin = -Ui.dp(this, 8) })
        }
        return card
    }

    private fun showBadges() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@TeamPage, 22), Ui.dp(this@TeamPage, 8), Ui.dp(this@TeamPage, 22), Ui.dp(this@TeamPage, 8))
        }
        for ((id, title) in Badges.TITLES) {
            val got = id in state.badges
            col.addView(Ui.text(this, 14f, if (got) 800 else 600, if (got) R.color.ag_green else R.color.ag_sub0).apply {
                text = (if (got) "✓  " else "○  ") + title
                setPadding(0, Ui.dp(this@TeamPage, 5), 0, Ui.dp(this@TeamPage, 5))
            })
        }
        Pickers.info(this, "Badges ${state.badges.size}/${Badges.TITLES.size}", Bits.scroll(this, col))
    }

    // ---- team rows (mockup .mon) ----

    private fun teamRow(mon: Mon, index: Int, count: Int): View {
        val ctx = this
        val maxHp = StatCalc.maxHp(data, mon).coerceAtLeast(1)
        val pick = ComparePick.Owned(mon.uid)
        val picked = pickOn && picks.has(pick)
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = PickKit.cardBg(ctx, 14, picked)
            setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 6), Ui.dp(ctx, 6), Ui.dp(ctx, 6))
            alpha = if (mon.hp <= 0) 0.55f else 1f
            layoutParams = LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(ctx, 6) }
            setOnClickListener { if (pickOn) togglePick(pick) else MonDetail.show(ctx, data, mon) }
            setOnLongClickListener { actions.menu(mon, it); true }
        }
        val img = ImageView(ctx)
        MonPreview.show(ctx, mon.species, mon.shiny, img)
        row.addView(img, LinearLayout.LayoutParams(Ui.dp(ctx, 56), Ui.dp(ctx, 56)))

        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val r1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        r1.addView(Ui.text(ctx, 14f, 900, R.color.ag_text).apply {
            text = mon.shownName
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (mon.favourite) r1.addView(BoxViews.heartMark(ctx), LinearLayout.LayoutParams(Ui.dp(ctx, 13), Ui.dp(ctx, 13)).also { it.marginStart = Ui.dp(ctx, 6) })
        r1.addView(Ui.text(ctx, 12f, 800, R.color.ag_sub0).apply { LevelTier.style(this, mon.level) }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(ctx, 6) })
        body.addView(r1)

        // types, then the tags (most useful first) as many as fit in the rest of the line, at most 3
        val r2 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        r2.addView(Bits.typeRow(ctx, data.sp(mon.species).types))
        val tags = FitRow(ctx).apply {
            MonTags.of(data, state, mon).take(3).forEach { t ->
                addView(if (t.kind == TagKind.HELD && t.item != null) heldChip(t) else MonTags.pill(ctx, t),
                    LinearLayout.LayoutParams(-2, -2).also { lp -> lp.marginStart = Ui.dp(ctx, 4) })
            }
        }
        r2.addView(tags, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 3) })
        body.addView(r2, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 4) })

        val frac = mon.hp.toFloat() / maxHp
        val hp = BarView(ctx)
        hp.set(frac, Ui.hpColor(ctx, frac), false)
        body.addView(hp, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 7)).also { it.topMargin = Ui.dp(ctx, 5) })

        val need = Progression.xpToNext(data, mon).coerceAtLeast(1)
        val xp = BarView(ctx)
        xp.setMeaning(BarMeaning.XP, if (mon.level >= 100) 1f else mon.xp.toFloat() / need, false)
        body.addView(xp, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 3)).also { it.topMargin = Ui.dp(ctx, 4) })

        row.addView(body, LinearLayout.LayoutParams(0, -2, 1f))

        if (editing) {
            row.addView(arrow(R.drawable.ic_up, "Move up", index > 0) { act { it.moveInTeam(mon.uid, -1) } })
            row.addView(arrow(R.drawable.ic_down, "Move down", index < count - 1) { act { it.moveInTeam(mon.uid, 1) } })
        }
        if (pickOn) {
            val badge = PickKit.badge(ctx)
            PickKit.bindBadge(badge, true, picked)
            row.addView(badge, LinearLayout.LayoutParams(Ui.dp(ctx, 26), Ui.dp(ctx, 26)).also { it.marginStart = Ui.dp(ctx, 6) })
            teamRows[mon.uid] = TeamRow(row, badge)
        }
        return row
    }

    /** The held-item tag in a row: just the item icon on the neutral pill (the label is in the details), so it always fits. */
    private fun heldChip(tag: MonTag): View {
        val ctx = this
        return LinearLayout(ctx).apply {
            gravity = Gravity.CENTER
            contentDescription = tag.label
            background = Ui.rounded(Ui.c(ctx, R.color.ag_s0), 7, ctx)
            setPadding(Ui.dp(ctx, 5), Ui.dp(ctx, 2), Ui.dp(ctx, 5), Ui.dp(ctx, 2))
            addView(ImageView(ctx).also { Ui.itemIcon(ctx, tag.item!!, it) }, LinearLayout.LayoutParams(Ui.dp(ctx, 16), Ui.dp(ctx, 16)))
        }
    }

    /** An Edit-mode arrow: 48 dp touch, 36 dp look; greyed out and dead at the ends. */
    private fun arrow(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit): View {
        val ctx = this
        return ImageView(ctx).apply {
            setImageResource(icon)
            contentDescription = label
            val pad = Ui.dp(ctx, 14)
            setPadding(pad, pad, pad, pad)
            val inset = Ui.dp(ctx, 6)
            background = InsetDrawable(Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 10, ctx), inset)
            setColorFilter(Ui.c(ctx, if (enabled) R.color.ag_text else R.color.ag_ov), PorterDuff.Mode.SRC_IN)
            alpha = if (enabled) 1f else 0.45f
            isEnabled = enabled
            isClickable = enabled
            if (enabled) setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 48), Ui.dp(ctx, 48)).also { it.marginStart = Ui.dp(ctx, 2) }
        }
    }

    /** Shows as many children as fit the width, in order (the first ones are the most useful); the rest are hidden. */
    internal class FitRow(ctx: Context) : LinearLayout(ctx) {
        init { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val avail = MeasureSpec.getSize(widthMeasureSpec)
            var used = 0
            var full = false
            for (i in 0 until childCount) {
                val c = getChildAt(i)
                c.visibility = View.VISIBLE
                c.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
                val need = c.measuredWidth + ((c.layoutParams as? MarginLayoutParams)?.marginStart ?: 0)
                if (full || (avail > 0 && used + need > avail)) { c.visibility = View.GONE; full = true } else used += need
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    // ---- box cells (mockup .boxgrid) ----
    // A cell is a card with the sprite on top and a label strip under it (a ×N count on the left, the level on the right), so
    // nothing but the small marks ever sits over the sprite: ★ shiny and ♥ favourite top-left, the team mark top-right.

    private class Cell(
        val img: ImageView, val lv: TextView, val count: TextView, val star: TextView, val heart: ImageView,
        val team: ImageView, val held: ImageView, val check: ImageView,
    ) {
        var uid: String? = null
    }

    private fun makeCell(f: SquareFrame) {
        val ctx = this
        f.background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 10, ctx)
        val img = ImageView(ctx).apply { setPadding(Ui.dp(ctx, 2), Ui.dp(ctx, 2), Ui.dp(ctx, 2), Ui.dp(ctx, 2)) }
        val lv = Ui.text(ctx, 10f, 900, R.color.ag_sub0)
        val count = Ui.text(ctx, 10f, 900, R.color.ag_text) // plain text: no background, never over the sprite
        val star = Ui.text(ctx, 9f, 900, R.color.ag_yellow).apply { text = "★" }
        val heart = BoxViews.heartMark(ctx)
        val team = BoxViews.teamMark(ctx)
        val held = ImageView(ctx).apply { visibility = View.GONE }
        val check = PickKit.badge(ctx)
        f.addView(img, FrameLayout.LayoutParams(-1, -1).also { it.bottomMargin = Ui.dp(ctx, STRIP_DP) })
        // One label strip: "×N" keeps its width, the level takes what is left and shrinks its text, so the two never overlap.
        count.maxLines = 1
        lv.maxLines = 1
        lv.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        lv.setAutoSizeTextTypeUniformWithConfiguration(6, 10, 1, TypedValue.COMPLEX_UNIT_SP)
        val strip = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        strip.addView(count, LinearLayout.LayoutParams(-2, -2))
        strip.addView(lv, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 2) })
        f.addView(strip, FrameLayout.LayoutParams(-1, Ui.dp(ctx, STRIP_DP), Gravity.BOTTOM).also {
            it.leftMargin = Ui.dp(ctx, 4); it.rightMargin = Ui.dp(ctx, 4)
        })
        f.addView(held, FrameLayout.LayoutParams(Ui.dp(ctx, 12), Ui.dp(ctx, 12), Gravity.START or Gravity.BOTTOM).also {
            it.leftMargin = Ui.dp(ctx, 3); it.bottomMargin = Ui.dp(ctx, 2)
        })
        val marks = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        marks.addView(star)
        marks.addView(heart, LinearLayout.LayoutParams(Ui.dp(ctx, 10), Ui.dp(ctx, 10)).also { it.marginStart = Ui.dp(ctx, 2) })
        f.addView(marks, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.TOP).also {
            it.leftMargin = Ui.dp(ctx, 3); it.topMargin = Ui.dp(ctx, 3)
        })
        f.addView(team, FrameLayout.LayoutParams(Ui.dp(ctx, 13), Ui.dp(ctx, 13), Gravity.END or Gravity.TOP).also {
            it.rightMargin = Ui.dp(ctx, 3); it.topMargin = Ui.dp(ctx, 3)
        })
        f.addView(check, FrameLayout.LayoutParams(Ui.dp(ctx, 18), Ui.dp(ctx, 18), Gravity.START or Gravity.BOTTOM).also {
            it.leftMargin = Ui.dp(ctx, 3); it.bottomMargin = Ui.dp(ctx, 3)
        })
        f.tag = Cell(img, lv, count, star, heart, team, held, check)
    }

    /** The tick part of a box cell: frame stroke + badge. */
    private fun bindPick(f: SquareFrame) {
        val cell = f.tag as Cell
        val uid = cell.uid ?: return
        val picked = pickOn && picks.has(ComparePick.Owned(uid))
        f.background = PickKit.cardBg(this, 10, picked)
        PickKit.bindBadge(cell.check, pickOn, picked)
    }

    private fun bindCell(f: SquareFrame, tile: BoxTile?) {
        val cell = f.tag as Cell
        cell.uid = tile?.best?.uid
        if (tile == null) {
            f.visibility = View.INVISIBLE
            f.setOnClickListener(null)
            f.setOnLongClickListener(null)
            return
        }
        val mon = tile.best
        f.visibility = View.VISIBLE
        val pick = ComparePick.Owned(mon.uid)
        bindPick(f)
        Ui.staticSprite(this, mon.species, mon.shiny, cell.img)
        LevelTier.style(cell.lv, mon.level)
        cell.count.text = if (tile.isGroup) "×${tile.count}" else ""
        cell.count.visibility = if (tile.isGroup) View.VISIBLE else View.GONE
        cell.star.visibility = if (tile.anyShiny()) View.VISIBLE else View.GONE
        cell.heart.visibility = if (tile.anyFavourite()) View.VISIBLE else View.GONE
        cell.team.visibility = if (tile.anyInTeam(state.team)) View.VISIBLE else View.GONE
        // the held item of a single Pokémon (a group has several; the tick badge takes its corner while picking)
        val item = mon.heldItem.takeIf { !tile.isGroup && !pickOn }
        if (item != null) { Ui.itemIcon(this, item, cell.held); cell.held.visibility = View.VISIBLE } else cell.held.visibility = View.GONE
        f.alpha = if (mon.hp <= 0) 0.55f else 1f
        f.contentDescription = listOfNotNull(
            mon.shownName, "level ${mon.level}", if (tile.isGroup) "${tile.count} copies" else null,
            if (tile.anyInTeam(state.team)) "in your team" else null, if (tile.anyFavourite()) "favourite" else null,
        ).joinToString(", ")
        f.setOnClickListener { if (pickOn) togglePick(pick) else if (tile.isGroup) openCopies(tile) else MonDetail.show(this, data, mon) }
        f.setOnLongClickListener { if (tile.isGroup && !pickOn) openCopies(tile) else actions.menu(mon, it); true }
    }

    private companion object {
        const val COLS = 6

        /** A Box cell is taller than wide: the label strip (×N, level) sits under the sprite, which keeps its size. */
        const val CELL_RATIO = 1.2f
        const val STRIP_DP = 14
    }
}

/** The Team screen as its own Activity: a thin shell around [TeamPage]. */
class TeamActivity : PageActivity() {
    override fun createPage(): GamePage = TeamPage(this, this).also { p ->
        intent.getStringExtra(EXTRA_PICK_UID)?.let { p.startPickWith(it) }
        intent.getStringExtra(EXTRA_SHOW_UID)?.let {
            intent.removeExtra(EXTRA_SHOW_UID) // once: a rotation re-creates the page with the same intent
            p.showDetailsOnce(it)
        }
    }

    companion object {
        /** Opens the Team tab in Compare picking mode with this Pokémon ticked (menu "Compare" from another screen). */
        const val EXTRA_PICK_UID = "pick_uid"

        /** Opens the Team tab with this Pokémon's details shown (tap on your Pokémon in a battle). */
        const val EXTRA_SHOW_UID = "show_uid"
    }
}
