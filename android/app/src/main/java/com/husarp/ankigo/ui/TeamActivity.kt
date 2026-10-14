package com.husarp.ankigo.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Badges
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.shownName
import com.husarp.ankigo.game.Progression
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.Trainer

// TICKET T14 owns this file: trainer card, team and box (mockup frame C).
// The list is one ListView: header = trainer card + team rows + "Box" title, rows = 6 box cells each (recycled).
// No drag and drop (framework Views have no easy drag list): the order is changed with Move up / Move down in the tap menu.
class TeamPage(base: Context, host: PageHost) : GamePage(base, host) {
    override val tab = BottomNav.Tab.TEAM

    private lateinit var appBar: AppBar
    private lateinit var list: ListView
    private lateinit var header: LinearLayout
    private val actions by lazy { MonActions(this) }
    private var box: List<Mon> = emptyList()

    private val adapter = object : BaseAdapter() {
        override fun getCount() = (box.size + COLS - 1) / COLS
        override fun getItem(position: Int): Any = position
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? CellRow) ?: CellRow(this@TeamPage, COLS, 1f, 4, 12) { f -> makeCell(f) }
            for (c in 0 until COLS) bindCell(row.cells[c], box.getOrNull(position * COLS + c))
            return row
        }
    }

    override fun buildBar(): View {
        appBar = Ui.appBar(this, "Team", "0/6")
        return appBar
    }

    override fun buildBody(): View {
        header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@TeamPage, 12), 0, Ui.dp(this@TeamPage, 12), 0)
        }
        list = ListView(this).apply {
            divider = null
            dividerHeight = 0
            setSelector(ColorDrawable(Color.TRANSPARENT))
            setPadding(0, 0, 0, Ui.dp(this@TeamPage, 12))
            clipToPadding = false
            addHeaderView(header, null, false)
            adapter = this@TeamPage.adapter
        }
        return list
    }

    override fun render() {
        val team = state.teamMons()
        appBar.setPill("${team.size}/6")
        box = state.boxMons().sortedWith(compareBy({ it.species }, { it.level }))
        header.removeAllViews()
        header.addView(trainerCard())
        header.addView(Bits.sect(this, "Team", "tap a Pokémon for options"))
        team.forEachIndexed { i, mon -> header.addView(teamRow(mon, i == 0)) }
        header.addView(Bits.sect(this, "Box", "${box.size} Pokémon · tap for options"))
        adapter.notifyDataSetChanged()
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
        bar.setGradient(Ui.c(this, R.color.ag_mauve), Ui.c(this, R.color.ag_pink))
        bar.set(st.trainerXp.toFloat() / need.coerceAtLeast(1), Ui.c(this, R.color.ag_mauve), false)
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

    private fun teamRow(mon: Mon, isMain: Boolean): View {
        val ctx = this
        val maxHp = StatCalc.maxHp(data, mon).coerceAtLeast(1)
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 14, ctx)
            setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 6), Ui.dp(ctx, 10), Ui.dp(ctx, 6))
            alpha = if (mon.hp <= 0) 0.55f else 1f
            layoutParams = LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(ctx, 6) }
            setOnClickListener { actions.menu(mon) }
        }
        val img = ImageView(ctx)
        MonPreview.show(ctx, mon.species, mon.shiny, img)
        row.addView(img, LinearLayout.LayoutParams(Ui.dp(ctx, 56), Ui.dp(ctx, 56)))

        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val r1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        r1.addView(Ui.text(ctx, 14f, 900, R.color.ag_text).apply { text = mon.shownName; maxLines = 1 })
        if (isMain) r1.addView(Ui.pill(ctx, "MAIN", R.color.ag_mauve), LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(ctx, 6) })
        if (mon.shiny) r1.addView(Ui.text(ctx, 13f, 900, R.color.ag_yellow).apply { text = "★" }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(ctx, 5) })
        mon.heldItem?.let { held ->
            val ic = ImageView(ctx)
            Ui.itemIcon(ctx, held, ic)
            r1.addView(ic, LinearLayout.LayoutParams(Ui.dp(ctx, 18), Ui.dp(ctx, 18)).also { it.marginStart = Ui.dp(ctx, 6) })
        }
        r1.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
        r1.addView(Ui.text(ctx, 12f, 800, R.color.ag_sub0).apply { text = "Lv ${mon.level}" })
        body.addView(r1)

        body.addView(Bits.typeRow(ctx, data.sp(mon.species).types), LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 4) })

        val frac = mon.hp.toFloat() / maxHp
        val hp = BarView(ctx)
        hp.set(frac, Ui.hpColor(ctx, frac), false)
        body.addView(hp, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 7)).also { it.topMargin = Ui.dp(ctx, 5) })

        val need = Progression.xpToNext(data, mon).coerceAtLeast(1)
        val xp = BarView(ctx)
        xp.set(if (mon.level >= 100) 1f else mon.xp.toFloat() / need, Ui.c(ctx, R.color.ag_sky), false)
        body.addView(xp, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 3)).also { it.topMargin = Ui.dp(ctx, 4) })

        row.addView(body, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }

    // ---- box cells (mockup .boxgrid) ----

    private class Cell(val img: ImageView, val lv: TextView, val star: TextView)

    private fun makeCell(f: SquareFrame) {
        val ctx = this
        f.background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 10, ctx)
        val img = ImageView(ctx).apply { setPadding(Ui.dp(ctx, 2), Ui.dp(ctx, 2), Ui.dp(ctx, 2), Ui.dp(ctx, 2)) }
        val lv = Ui.text(ctx, 9f, 900, R.color.ag_sub0)
        val star = Ui.text(ctx, 9f, 900, R.color.ag_yellow).apply { text = "★" }
        f.addView(img, FrameLayout.LayoutParams(-1, -1))
        f.addView(lv, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.BOTTOM).also {
            it.rightMargin = Ui.dp(ctx, 3); it.bottomMargin = Ui.dp(ctx, 1)
        })
        f.addView(star, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.TOP).also {
            it.leftMargin = Ui.dp(ctx, 3); it.topMargin = Ui.dp(ctx, 2)
        })
        f.tag = Cell(img, lv, star)
    }

    private fun bindCell(f: SquareFrame, mon: Mon?) {
        val cell = f.tag as Cell
        if (mon == null) {
            f.visibility = View.INVISIBLE
            f.setOnClickListener(null)
            return
        }
        f.visibility = View.VISIBLE
        Ui.staticSprite(this, mon.species, mon.shiny, cell.img)
        cell.lv.text = mon.level.toString()
        cell.star.visibility = if (mon.shiny) View.VISIBLE else View.GONE
        f.alpha = if (mon.hp <= 0) 0.55f else 1f
        f.setOnClickListener { actions.menu(mon) }
    }

    private companion object { const val COLS = 6 }
}

/** The Team screen as its own Activity: a thin shell around [TeamPage]. */
class TeamActivity : PageActivity() {
    override fun createPage(): GamePage = TeamPage(this, this)
}
