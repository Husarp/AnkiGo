package com.husarp.ankigo.ui

import com.husarp.ankigo.game.lookNum
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
import com.husarp.ankigo.Anki
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.shownName
import com.husarp.ankigo.game.Progression
import com.husarp.ankigo.game.StatCalc

// The Pokémon tab (the Team tab until round 4): one screen with four sections (Team / Box / Pokedex / Shop), a Compare picking
// mode and a team Edit mode. Team = battle card + team rows (a ScrollView). Box = a ListView of 6-cell rows (recycled).
// Pokedex = DexPage embedded. Shop = ShopPage embedded (its bag and buy flow work as on its own screen).
// Tap = details, long-press = the actions menu. Edit mode shows up/down arrows on the team rows (no drag and drop:
// framework Views have no easy drag list).
class TeamPage(base: Context, host: PageHost, start: TeamSection? = null) : GamePage(base, host), PickHost {
    override val tab = BottomNav.Tab.POKEMON

    private lateinit var appBar: AppBar
    private lateinit var shopHelp: ImageView      // the Shop's "?" (money guide), only on the Shop section
    private lateinit var switchBox: LinearLayout
    private lateinit var teamScroll: ScrollView
    private lateinit var teamCol: LinearLayout
    private lateinit var cardSlot: FrameLayout // the battle card's place at the top of the Team section (never emptied by drawTeam)
    private lateinit var boxList: ListView
    private lateinit var boxHeader: LinearLayout
    private lateinit var unsupportedBox: LinearLayout // "Not supported yet" (PLAN 67), the end of the Box list
    private lateinit var dexBody: View
    private lateinit var shopBody: View
    private lateinit var pickBar: LinearLayout
    private lateinit var pickHint: TextView
    private lateinit var pickGo: TextView
    private val actions by lazy { MonActions(this) }
    private val dexPage by lazy { DexPage(this, host, this) }
    private val shopPage by lazy { ShopPage(this, host, embedded = true) }
    private var tiles: List<BoxTile> = emptyList()
    private var shown = 0 // Pokémon behind the tiles (after the filter)

    // A plain open of the tab is on Team (R1-02); a jump through Nav can ask for a section. The section chosen inside a visit is kept only while this page lives.
    private var section: TeamSection = start ?: TeamSection.TEAM
    private var editing = false
    private var pickOn = false
    private val picks = PickSet()
    private val detailsOnce = DetailsOnce()

    // The Box lists every owned Pokémon (team members carry a mark); copies of a species collapse into one "×N" tile.
    private var boxFilter = BoxFilter.ALL
    private var grouped = AppPrefs.boxGrouped(base)
    private var boxSort = SortPrefs.load(base, SortPrefs.Where.BOX) // PLAN 48/55: remembered for the Box
    private var tileValues: Map<BoxTile, String> = emptyMap() // the number a stat sort shows on each tile
    private val cols = GridMath.columns(base.resources.configuration.screenWidthDp) // 3 / 4 / 5 / 6 by the window width (R4-V5)

    override val picking: Boolean get() = pickOn
    override fun isPicked(p: ComparePick): Boolean = picks.has(p)

    private val adapter = object : BaseAdapter() {
        override fun getCount() = (tiles.size + cols - 1) / cols
        override fun getItem(position: Int): Any = position
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? CellRow) ?: CellRow(this@TeamPage, cols, CELL_RATIO, 4, 12) { f -> makeCell(f) }
            for (c in 0 until cols) bindCell(row.cells[c], tiles.getOrNull(position * cols + c))
            return row
        }
    }

    override fun buildBar(): View {
        appBar = Ui.appBar(this, "Pokémon", "0", pillIconRes = R.drawable.ic_coin) // the coins on every section; the counts sit in the sections
        shopHelp = appBar.addAction(R.drawable.ic_help, "Help: how money works", R.color.ag_mauve) { shopPage.openMoneyHelp() }
        return appBar
    }

    override fun buildBody(): View {
        val gap = Ui.dp(this, 12)
        switchBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        teamCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(gap, 0, gap, gap)
        }
        cardSlot = FrameLayout(this).apply { visibility = View.GONE }
        teamCol.addView(cardSlot, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(this, 10) })
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
        shopBody = shopPage.embeddedBody()

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
            addView(shopBody, FrameLayout.LayoutParams(-1, -1))
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
        val mons = BoxTiles.filter(st.mons, boxFilter, st.team, st.settings.xpShareUid, ::canEvolve)
        shown = mons.size
        // while picking for Compare every copy is its own tile
        tiles = BoxTiles.sorted(data, st, BoxTiles.build(data, mons, grouped && !pickOn), boxSort)
        tileValues = SortText.tileValues(data, st, tiles, boxSort)
        if (team.size < 2) editing = false
        drawSwitch()
        drawBar(team.size)
        drawTeam(team)
        drawBoxHeader()
        UnsupportedBox.bind(unsupportedBox, st)
        adapter.notifyDataSetChanged()
        dexPage.renderEmbedded()
        if (section == TeamSection.SHOP) shopPage.renderEmbedded()
        teamScroll.visibility = if (section == TeamSection.TEAM) View.VISIBLE else View.GONE
        boxList.visibility = if (section == TeamSection.BOX) View.VISIBLE else View.GONE
        dexBody.visibility = if (section == TeamSection.DEX) View.VISIBLE else View.GONE
        shopBody.visibility = if (section == TeamSection.SHOP) View.VISIBLE else View.GONE
        drawPickBar()
        syncBattleCard()
        // A battle asked for a Pokémon's details (PLAN 86): open them once, after the first render.
        detailsOnce.take { st.mon(it) != null }?.let { uid -> st.mon(uid)?.let { MonDetail.show(this, data, it) } }
    }

    // ---- sections ----

    override fun onShow() {
        super.onShow()
        visible = true
        if (section == TeamSection.SHOP) shopPage.rollDay()
    }

    override fun onHide() {
        visible = false
        battleCard?.stop() // nothing animates, nothing holds the live session while the page is not in front
        super.onHide()
    }

    override fun onGone() {
        battleCard?.stop()
        super.onGone()
    }

    internal fun showSection(s: TeamSection) {
        if (s == section) return
        section = s
        editing = false
        if (s == TeamSection.SHOP) shopPage.rollDay() // a new game day restocks the shop when it is opened
        requestRender()
    }

    /** Test hook: opens the page on [s]. */
    internal fun preset(
        s: TeamSection = section, edit: Boolean = false, pick: List<ComparePick>? = null,
        filter: BoxFilter = boxFilter, group: Boolean = grouped, sort: SortSpec = boxSort,
    ) {
        section = s
        editing = edit
        boxFilter = filter
        grouped = group
        boxSort = sort
        if (pick != null) {
            pickOn = true
            picks.clear()
            pick.forEach { picks.toggle(it) }
        }
    }

    /** The Team / Box / Pokédex / Shop tabs: four equal tabs with an icon over each label, the selected one on an accent rounded square. */
    private fun drawSwitch() {
        SectionSwitch.draw(this, switchBox, TeamSection.entries.map { SectionSwitch.Seg(it.label, it.icon) }, section.ordinal, SectionSwitch.Style.TABS) { showSection(TeamSection.entries[it]) }
    }

    // ---- the battle card (Team section, top) ----

    private var battleCard: BattleCard? = null
    internal var cardSession: BattleCard.Live = BattleCard.RealLive // tests count who holds the live session
    internal val cardLive: Boolean get() = battleCard?.isLive == true
    private var visible = false // between onShow and onHide

    /** Tap on the card / its expand icon, and Pop out; both go through the home screen, which owns the full battle and the pop-out. */
    private val cardHost = object : BattleCard.Host {
        override fun expand() { Nav.open(this@TeamPage, Dest.BATTLE, Dest.TEAM) }
        // the tab closes behind it (as a tab switch does), so Back from the home screen does not land on a stale Pokémon tab
        override fun popOut() { startScreen(Nav.popOutIntent(this@TeamPage)); host.closePage() }
        // the panel's "Grant" (live status: AnkiDroid permission missing): the activity asks, its onRequestPermissionsResult calls [accessAnswered]
        override fun grantAccess() { host.hostActivity?.requestPermissions(arrayOf(Anki.permission), TeamActivity.REQ_ANKI) }
    }

    /** The AnkiDroid permission dialog was answered (from [TeamActivity]): the card starts live detection again when it was granted. */
    internal fun accessAnswered(granted: Boolean) { battleCard?.accessAnswered(granted) }

    /**
     * Shows the battle card while the Team section is on screen and live (the game is ready, the page is in an Activity; also in
     * a short window, where the card's panel is smaller), hides it and lets the live session go otherwise.
     */
    private fun syncBattleCard() {
        val want = section == TeamSection.TEAM && BattleCard.wanted(GameRepo.status == GameRepo.Status.READY, host.hostActivity != null)
        if (want) {
            val card = battleCard ?: BattleCard(this, cardHost, cardSession).also { battleCard = it; cardSlot.addView(it.view, FrameLayout.LayoutParams(-1, -2)) }
            cardSlot.visibility = View.VISIBLE
            if (visible) { card.start(); card.refresh() } else card.stop()
        } else {
            cardSlot.visibility = View.GONE
            battleCard?.stop()
        }
    }

    private fun drawBar(teamSize: Int) {
        appBar.setPill(Bits.fmt(state.cash)) // the coins on every section (the counts are in the sections themselves)
        shopHelp.visibility = if (section == TeamSection.SHOP) View.VISIBLE else View.GONE
    }

    // ---- Compare picking ----

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
        picks.toggle(p)
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
        val show = pickOn && section != TeamSection.SHOP // ticks are kept while the Shop is open, the bar comes back with the other sections
        pickBar.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return
        val n = picks.size
        pickHint.text = if (n < Compare.MIN) "Tick at least ${Compare.MIN} Pokémon" else "$n ticked"
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
        if (teamCol.childCount > 1) teamCol.removeViews(1, teamCol.childCount - 1) // child 0 is the battle card's slot
        teamCol.addView(teamHeader(team.size))
        team.forEachIndexed { i, mon -> teamCol.addView(teamRow(mon, i, team.size)) }
    }

    /** "TEAM" + the Edit / Done pill, and a hint line. */
    private fun teamHeader(size: Int): View {
        val ctx = this
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(Ui.text(ctx, 12f, 900, R.color.ag_sub0).apply {
            text = "TEAM · $size / 6"
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
        // the count that was in the app bar's pill: all of them, or "n of N" under a filter
        val total = state.mons.size
        boxHeader.addView(Bits.sect(this, "Box", (if (shown == total) "$shown Pokémon" else "$shown of $total Pokémon") + if (someGroup) " · grouped" else ""))
        boxHeader.addView(boxControls(), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = -Ui.dp(this, 6) })
        // only the Compare picking hint is left; the "tap for details · hold for actions" hint is gone
        if (pickOn) boxHeader.addView(Ui.text(this, 11f, 700, R.color.ag_sub0).apply {
            text = "Tap a Pokémon to tick it for Compare."
            setPadding(Ui.dp(this@TeamPage, 4), 0, Ui.dp(this@TeamPage, 4), Ui.dp(this@TeamPage, 8))
            visibility = if (tiles.isEmpty() || Ui.shortWindow(this@TeamPage)) View.GONE else View.VISIBLE
        })
        if (tiles.isEmpty()) {
            boxHeader.addView(Ui.text(this, 13f, 700, R.color.ag_sub0).apply {
                text = when {
                    boxFilter == BoxFilter.FAVOURITES -> "No favourites yet. Hold a Pokémon and pick “Add to favourites”."
                    boxFilter != BoxFilter.ALL -> boxFilter.none ?: "No ${boxFilter.label.lowercase()} Pokémon in your box."
                    else -> "Your box is empty. Pokémon you catch beyond six wait here."
                }
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(this@TeamPage, 28), 0, Ui.dp(this@TeamPage, 12))
            })
        }
    }

    /**
     * The chip row under the tabs (6A): the sort chip (name = small list of sorts, arrow = flip), "Filters" = the sheet with
     * everything, ♥ Favourites, Shiny, and "In team" while that filter is on. Scrolls sideways when it is long.
     */
    private fun boxControls(): View {
        val ctx = this
        val st = state
        val shownSpec = boxSort.copy(key = MonSort.effectiveKey(boxSort, st)) // no battle: "vs the wild one" works as "strongest attack"
        val chips = ArrayList<View>()
        chips += TileViews.legendChip(ctx, dex = false)
        sortPill = SortViews.sortChip(ctx, shownSpec) { openSortSheet(sortOnly = true) }
        chips += sortPill
        chips += BoxViews.chip(ctx, "Filters", false) { openSortSheet() } // next to the sort chip, so a narrow window still shows it
        chips += BoxViews.chip(ctx, "Favourites", boxFilter == BoxFilter.FAVOURITES, heart = true) { toggleBoxFilter(BoxFilter.FAVOURITES) }
        chips += BoxViews.chip(ctx, "Shiny", boxFilter == BoxFilter.SHINY) { toggleBoxFilter(BoxFilter.SHINY) }
        chips += BoxViews.chip(ctx, BoxFilter.HOLDS_ITEM.label, boxFilter == BoxFilter.HOLDS_ITEM) { toggleBoxFilter(BoxFilter.HOLDS_ITEM) }
        chips += BoxViews.chip(ctx, BoxFilter.HOLDS_XP_SHARE.label, boxFilter == BoxFilter.HOLDS_XP_SHARE) { toggleBoxFilter(BoxFilter.HOLDS_XP_SHARE) }
        chips += BoxViews.chip(ctx, BoxFilter.CAN_EVOLVE.label, boxFilter == BoxFilter.CAN_EVOLVE) { toggleBoxFilter(BoxFilter.CAN_EVOLVE) }
        if (boxFilter == BoxFilter.IN_TEAM) chips += BoxViews.chip(ctx, "In team", true) { toggleBoxFilter(BoxFilter.IN_TEAM) }
        return SortViews.scrollRow(ctx, chips)
    }

    private lateinit var sortPill: View // the sort chip as last drawn (tests click it)

    private fun canEvolve(mon: Mon): Boolean = MonTags.canEvolve(data, state, mon)

    private fun toggleBoxFilter(f: BoxFilter) = setBoxFilter(if (boxFilter == f) BoxFilter.ALL else f)

    private fun setBoxFilter(f: BoxFilter) {
        if (f == boxFilter) return
        boxFilter = f
        requestRender()
    }

    private fun setBoxSort(s: SortSpec) {
        boxSort = s
        SortPrefs.save(this, SortPrefs.Where.BOX, s)
        requestRender()
        boxList.setSelection(0)
    }

    /** The one "Sort & filter" sheet. Changes apply at once; the Box behind it follows. */
    private fun openSortSheet(sortOnly: Boolean = false) {
        val labels = BoxFilter.entries.map { it.label }
        SortSheet(
            this, SortText.boxKeys, boxSort, SortSpec.DEFAULT, MonSort.vsWildAvailable(state), labels, boxFilter.ordinal,
            if (pickOn) null else SortSheet.GroupToggle(grouped) { on -> grouped = on; AppPrefs.setBoxGrouped(this, on); requestRender() },
            count = { BoxTiles.filter(state.mons, boxFilter, state.team, state.settings.xpShareUid, ::canEvolve).size },
            onChange = { spec, show ->
                if (spec != boxSort) setBoxSort(spec)
                BoxFilter.entries.getOrNull(show)?.let { setBoxFilter(it) }
            },
            sortOnly = sortOnly,
        ).show()
    }

    /** A group tile (hold or tap): the sheet with all copies. A copy's details / actions work as everywhere else. */
    private fun openCopies(tile: BoxTile) {
        BoxCopiesSheet.show(this, tile,
            onOpen = { MonDetail.show(this, data, it) },
            onMenu = { mon, anchor, done -> actions.menu(mon, anchor, done) })
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
        MonPreview.show(ctx, mon.lookNum(data), mon.shiny, img)
        // the red NEW sticker sits on the sprite's top-left corner until the Pokémon's details were opened (6A)
        val marks = TileMarks.of(state, listOf(mon))
        val spriteBox = FrameLayout(ctx).apply { clipChildren = false }
        spriteBox.addView(img, FrameLayout.LayoutParams(-1, -1))
        if (TileMark.NEW in marks) spriteBox.addView(TileViews.newSticker(ctx), FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.TOP).also {
            it.leftMargin = Ui.dp(ctx, 1); it.topMargin = Ui.dp(ctx, 1)
        })
        row.addView(spriteBox, LinearLayout.LayoutParams(Ui.dp(ctx, 56), Ui.dp(ctx, 56)))

        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        // the name, then its marks right after it (the same icons and order as on the Box tiles; everyone here is in the team, so only Main shows), then the level
        val r1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val nameMarks = NameMarksRow(ctx)
        nameMarks.addView(Ui.text(ctx, 14f, 900, R.color.ag_text).apply {
            text = mon.shownName
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-2, -2))
        for (m in marks) {
            val mark: View = when (m) {
                TileMark.SHINY -> Ui.text(ctx, 12f, 900, R.color.ag_yellow).apply { text = "★"; contentDescription = "Shiny" }
                TileMark.MAIN, TileMark.FAVOURITE -> TileViews.markIcon(ctx, m, 13)
                else -> null
            } ?: continue
            nameMarks.addView(mark, LinearLayout.LayoutParams(if (mark is ImageView) Ui.dp(ctx, 13) else -2, if (mark is ImageView) Ui.dp(ctx, 13) else -2).also { it.marginStart = Ui.dp(ctx, 5) })
        }
        r1.addView(nameMarks, LinearLayout.LayoutParams(0, -2, 1f))
        r1.addView(Ui.text(ctx, 12f, 800, R.color.ag_sub0).apply { LevelTier.style(this, mon.level) }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(ctx, 6) })
        body.addView(r1)

        // the types stay chips
        val r2 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        r2.addView(Bits.typeRow(ctx, data.sp(mon.species).types))
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

    // ---- box cells (mockup .boxgrid, R4 10A + 6A) ----
    // A cell is a card: a top row for the marks (NEW + ♥ left, team / main right), the sprite filling the room between that row and
    // the label strip (a ×N count on the left, the level on the right), so the sprite never covers a mark or the level. A shiny
    // has a soft golden glow behind its sprite. Held item, can-evolve, fainted and regional form are in the details only.

    private class Cell(
        val img: ImageView, val glow: View, val lv: TextView, val count: TextView, val marksLeft: LinearLayout, val marksRight: LinearLayout,
        val check: ImageView, val value: TextView, val strip: View,
    ) {
        var uid: String? = null
    }

    private fun makeCell(f: SquareFrame) {
        val ctx = this
        f.background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 10, ctx)
        val glow = ShinyGlow(ctx).apply { visibility = View.GONE }
        val img = TileSprite(ctx)
        val lv = Ui.text(ctx, 10f, 900, R.color.ag_sub0)
        val count = Ui.text(ctx, 10f, 900, R.color.ag_text) // plain text: no background, never over the sprite
        val value = Ui.text(ctx, 10f, 900, R.color.ag_text) // the number a stat sort sorts by (bottom-left, next to ×N)
        val check = PickKit.badge(ctx)
        val spriteBox = FrameLayout.LayoutParams(-1, -1).also {
            it.topMargin = Ui.dp(ctx, TOP_DP); it.bottomMargin = Ui.dp(ctx, STRIP_DP)
            it.leftMargin = Ui.dp(ctx, 2); it.rightMargin = Ui.dp(ctx, 2)
        }
        f.addView(glow, FrameLayout.LayoutParams(spriteBox))
        f.addView(img, spriteBox)
        // One label strip: "×N" keeps its width, the level takes what is left and shrinks its text, so the two never overlap.
        count.maxLines = 1
        value.maxLines = 1
        lv.maxLines = 1
        lv.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        lv.setAutoSizeTextTypeUniformWithConfiguration(6, 10, 1, TypedValue.COMPLEX_UNIT_SP)
        val strip = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        strip.addView(count, LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = Ui.dp(ctx, 3) })
        strip.addView(value, LinearLayout.LayoutParams(-2, -2))
        strip.addView(lv, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 2) })
        f.addView(strip, FrameLayout.LayoutParams(-1, Ui.dp(ctx, STRIP_DP), Gravity.BOTTOM).also {
            it.leftMargin = Ui.dp(ctx, 4); it.rightMargin = Ui.dp(ctx, 4)
        })
        // top row: left = NEW sticker, ♥ ; right = in team, main (filled in by bindCell)
        val marksLeft = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        f.addView(marksLeft, FrameLayout.LayoutParams(-2, Ui.dp(ctx, TOP_DP), Gravity.START or Gravity.TOP).also {
            it.leftMargin = Ui.dp(ctx, 4)
        })
        val marksRight = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        f.addView(marksRight, FrameLayout.LayoutParams(-2, Ui.dp(ctx, TOP_DP), Gravity.END or Gravity.TOP).also {
            it.rightMargin = Ui.dp(ctx, 4)
        })
        f.addView(check, FrameLayout.LayoutParams(Ui.dp(ctx, 18), Ui.dp(ctx, 18), Gravity.START or Gravity.BOTTOM).also {
            it.leftMargin = Ui.dp(ctx, 3); it.bottomMargin = Ui.dp(ctx, 3)
        })
        f.tag = Cell(img, glow, lv, count, marksLeft, marksRight, check, value, strip)
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
        Ui.staticSprite(this, mon.lookNum(data), mon.shiny, cell.img)
        LevelTier.style(cell.lv, mon.level)
        cell.count.text = if (tile.isGroup) "×${tile.count}" else ""
        cell.count.visibility = if (tile.isGroup) View.VISIBLE else View.GONE
        cell.value.text = tileValues[tile].orEmpty()
        cell.value.visibility = if (tileValues[tile] != null) View.VISIBLE else View.GONE
        // the marks (only these five, always in this order; R4-X1 item 3: ♥ and team icons 16 dp): NEW + ♥ top-left, team / main top-right, shiny = the glow behind the sprite
        val marks = TileMarks.of(state, tile.members)
        cell.marksLeft.removeAllViews()
        cell.marksRight.removeAllViews()
        for (m in marks) {
            val gap = Ui.dp(this, 2)
            when (m) {
                TileMark.NEW -> cell.marksLeft.addView(TileViews.newSticker(this), LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = Ui.dp(this, 4) })
                TileMark.FAVOURITE -> cell.marksLeft.addView(TileViews.markIcon(this, m, 16)!!, LinearLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16)))
                TileMark.IN_TEAM, TileMark.MAIN -> cell.marksRight.addView(TileViews.markIcon(this, m, 16)!!, LinearLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16)).also { it.marginStart = gap })
                TileMark.SHINY -> Unit
            }
        }
        cell.glow.visibility = if (TileMark.SHINY in marks) View.VISIBLE else View.GONE
        // the tick badge takes the bottom-left corner while picking: the strip starts after it
        (cell.strip.layoutParams as FrameLayout.LayoutParams).leftMargin = Ui.dp(this, if (pickOn) 22 else 4)
        f.alpha = if (mon.hp <= 0) 0.55f else 1f
        f.contentDescription = (listOfNotNull(
            mon.shownName, "level ${mon.level}", if (tile.isGroup) "${tile.count} copies" else null,
        ) + marks.map { it.label.lowercase() }).joinToString(", ")
        f.setOnClickListener { if (pickOn) togglePick(pick) else if (tile.isGroup) openCopies(tile) else MonDetail.show(this, data, mon) }
        f.setOnLongClickListener { if (tile.isGroup && !pickOn) openCopies(tile) else actions.menu(mon, it); true }
    }

    private companion object {
        /** A Box cell is taller than wide: the top row (marks) and the label strip (×N, level) sit around the sprite, which fills the rest (10A). */
        const val CELL_RATIO = 1.25f
        const val TOP_DP = 17
        const val STRIP_DP = 14
    }
}

/** The Team screen as its own Activity: a thin shell around [TeamPage]. */
class TeamActivity : PageActivity() {
    private var teamPage: TeamPage? = null

    override fun createPage(): GamePage = TeamPage(this, this, TeamSection.of(intent.getStringExtra(EXTRA_SECTION))).also { p ->
        teamPage = p
        intent.getStringExtra(EXTRA_PICK_UID)?.let { p.startPickWith(it) }
        intent.getStringExtra(EXTRA_SHOW_UID)?.let {
            intent.removeExtra(EXTRA_SHOW_UID) // once: a rotation re-creates the page with the same intent
            p.showDetailsOnce(it)
        }
    }

    /** An open that finds this screen already there (brought to the front, [Nav.TAB_FLAGS]) may ask for another section, for Compare picking or for a Pokémon's details. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_SECTION)?.let { teamPage?.showSection(TeamSection.of(it)) }
        intent.getStringExtra(EXTRA_PICK_UID)?.let { intent.removeExtra(EXTRA_PICK_UID); teamPage?.startPickWith(it) }
        intent.getStringExtra(EXTRA_SHOW_UID)?.let { intent.removeExtra(EXTRA_SHOW_UID); teamPage?.showDetailsOnce(it) }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_ANKI) teamPage?.accessAnswered(grantResults.firstOrNull() == 0)
    }

    companion object {
        /** Opens the Pokémon tab on this [TeamSection] (its name); without it the tab opens on Team. [Nav] sets it. */
        const val EXTRA_SECTION = "section"

        /** Opens the Team tab in Compare picking mode with this Pokémon ticked (menu "Compare" from another screen). */
        const val EXTRA_PICK_UID = "pick_uid"

        /** Opens the Team tab with this Pokémon's details shown (tap on your Pokémon in a battle). */
        const val EXTRA_SHOW_UID = "show_uid"

        /** Request code of the AnkiDroid permission asked from the battle card's "Grant" button. */
        internal const val REQ_ANKI = 41
    }
}
