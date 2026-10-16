package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.ui.BoxCopiesSheet
import com.husarp.ankigo.ui.BoxFilter
import com.husarp.ankigo.ui.BoxTiles
import com.husarp.ankigo.ui.MonActions
import com.husarp.ankigo.ui.MonDetail
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TeamSection
import org.junit.Rule
import org.junit.Test

/*
 * R2-V05 / V04 / V10: the Box lists everything (team marks), copies collapse into ×N tiles and open a bottom sheet (both
 * sorts), favourites (♥) in the Box, the sheet, team rows, the details header and the long-press menu. PNGs: box-*.
 */
class BoxShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val FLOAT = Variant("float", Size.FLOAT.wDp, Size.FLOAT.hDp)
    private val sizes = listOf(Variants.PHONE, Variants.SPLIT, FLOAT)
    private val st get() = GameRepo.state!!
    private val gd get() = GameRepo.data!!

    /** More copies (one with the team's species, a shiny, a fresh one), favourites, a held item, a fainted one. */
    private fun rich() {
        fun more(sp: Int, lv: Int, uid: String, at: Long, shiny: Boolean = false) =
            TestData.mon(gd, sp, lv, listOf("tackle"), uid).also { it.caughtAt = at; it.shiny = shiny; st.mons += it }
        more(25, 31, "x1", 1_760_100_000_000)
        more(25, 12, "x2", 1_760_200_000_000, shiny = true)
        more(16, 30, "x3", System.currentTimeMillis())
        more(16, 11, "x4", 1_760_300_000_000)
        st.mon("t1")!!.favourite = true
        st.mon("b3")!!.also { it.favourite = true; it.shiny = true }
        st.mon("b7")!!.favourite = true
        st.mon("t3")!!.favourite = true
        st.mon("b2")!!.heldItem = "everstone"
        st.mon("b5")!!.hp = 0
    }

    private fun page(ctx: Context, v: Variant, section: TeamSection = TeamSection.BOX, filter: BoxFilter = BoxFilter.ALL,
                     group: Boolean = true): View {
        rich()
        val p = TeamPage(ctx, NoHost)
        p.preset(section, filter = filter, group = group)
        return if (v === FLOAT) p.root.also { p.onShow() } else pageScreen(ctx, p)
    }

    private fun shoot(name: String, section: TeamSection = TeamSection.BOX, filter: BoxFilter = BoxFilter.ALL, group: Boolean = true,
                      vs: List<Variant> = sizes, scenario: FakeGame.Scenario = FakeGame.Scenario.MID_GAME) {
        for (v in vs) paparazzi.shootPage(name, listOf(v), scenario) { page(it, v, section, filter, group) }
    }

    @Test fun grouped() = shoot("box-grouped")
    /** A ×12 group whose best is Lv 100, in the narrow floating window (6 columns): the strip must not overlap. */
    @Test fun wideLabels() {
        for (v in listOf(Variants.PHONE, FLOAT)) paparazzi.shootPage("box-wide-labels", listOf(v)) { ctx ->
            for (i in 0 until 12) TestData.mon(gd, 25, if (i == 0) 100 else 20 + i, listOf("tackle"), "w$i").also { it.caughtAt = 1_760_000_000_000L + i; st.mons += it }
            val p = TeamPage(ctx, NoHost)
            p.preset(TeamSection.BOX, filter = BoxFilter.ALL, group = true)
            if (v === FLOAT) p.root.also { p.onShow() } else pageScreen(ctx, p)
        }
    }
    @Test fun ungrouped() = shoot("box-ungrouped", group = false)
    @Test fun favouritesOn() = shoot("box-fav-on", filter = BoxFilter.FAVOURITES)
    @Test fun favouritesEmpty() {
        for (v in sizes) paparazzi.shootPage("box-fav-none", listOf(v)) { ctx ->
            val p = TeamPage(ctx, NoHost); p.preset(TeamSection.BOX, filter = BoxFilter.FAVOURITES)
            if (v === FLOAT) p.root.also { p.onShow() } else pageScreen(ctx, p)
        }
    }
    @Test fun team() = shoot("box-team", TeamSection.TEAM, vs = listOf(Variants.PHONE, FLOAT))

    /** The copies sheet over the Box, [recent] = after tapping the "Recent" chip. */
    private fun sheet(name: String, recent: Boolean, species: Int = 25, vs: List<Variant> = listOf(Variants.PHONE, FLOAT)) {
        for (v in vs) paparazzi.shootPage(name, listOf(v)) { ctx ->
            rich()
            val p = TeamPage(ctx, NoHost); p.preset(TeamSection.BOX)
            val screen = if (v === FLOAT) p.root.also { p.onShow() } else pageScreen(ctx, p)
            val tile = BoxTiles.build(gd, st.mons.filter { it.species == species }, true).single()
            val maxList = (ctx.resources.displayMetrics.heightPixels * BoxCopiesSheet.LIST_SHARE).toInt()
            val view = BoxCopiesSheet(p, tile, {}, { _, _, _ -> }).build(maxList)
            if (recent) find(view) { it is TextView && it.text == "Recent" }?.performClick()
            FrameLayout(ctx).apply {
                addView(screen, FrameLayout.LayoutParams(-1, -1))
                addView(View(ctx).apply { setBackgroundColor(Color.argb(122, 0, 0, 0)) }, FrameLayout.LayoutParams(-1, -1))
                addView(view, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            }
        }
    }

    private fun find(v: View, pred: (View) -> Boolean): View? {
        if (pred(v)) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), pred)?.let { return it }
        return null
    }

    @Test fun sheetLevel() = sheet("box-sheet-level", recent = false)
    @Test fun sheetRecent() = sheet("box-sheet-recent", recent = true)
    @Test fun sheetMany() {
        // many copies: the list scrolls under the cap
        for (i in 0 until 9) GameRepo.state?.let { }
        for (v in listOf(Variants.PHONE, FLOAT)) paparazzi.shootPage("box-sheet-many", listOf(v)) { ctx ->
            rich()
            for (i in 0 until 12) st.mons += TestData.mon(gd, 19, 5 + i, listOf("tackle"), "y$i").also { it.caughtAt = 1_760_000_000_000L + i }
            val p = TeamPage(ctx, NoHost); p.preset(TeamSection.BOX)
            val screen = if (v === FLOAT) p.root.also { p.onShow() } else pageScreen(ctx, p)
            val tile = BoxTiles.build(gd, st.mons.filter { it.species == 19 }, true).single()
            val view = BoxCopiesSheet(p, tile, {}, { _, _, _ -> }).build((ctx.resources.displayMetrics.heightPixels * BoxCopiesSheet.LIST_SHARE).toInt())
            FrameLayout(ctx).apply {
                addView(screen, FrameLayout.LayoutParams(-1, -1))
                addView(View(ctx).apply { setBackgroundColor(Color.argb(122, 0, 0, 0)) }, FrameLayout.LayoutParams(-1, -1))
                addView(view, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            }
        }
    }

    /** The details dialog (phone = roomy, float = compact) with the heart off / on. */
    private fun details(name: String, uid: String, fav: Boolean) {
        for (v in listOf(Variants.PHONE, FLOAT)) paparazzi.shootPage(name, listOf(v)) { ctx ->
            st.mon(uid)!!.favourite = fav
            val d = DialogShots.capture { MonDetail.show(TeamPage(ctx, NoHost), gd, st.mon(uid)!!) } ?: error("no dialog")
            DialogShots.scene(ctx, d)
        }
    }

    @Test fun detailsHeartOff() = details("box-detail-heart-off", "t2", false)
    @Test fun detailsHeartOn() = details("box-detail-heart-on", "t2", true)

    @Test fun menuNotFavourite() {
        paparazzi.shootPage("box-menu-add", listOf(Variants.PHONE)) { ctx ->
            MenuKit.scene(ctx, MenuKit.capture { MonActions(TeamPage(ctx, NoHost)).menu(st.mon("b1")!!, View(ctx)) })
        }
    }

    @Test fun menuFavourite() {
        paparazzi.shootPage("box-menu-remove", listOf(Variants.PHONE)) { ctx ->
            st.mon("b1")!!.favourite = true
            MenuKit.scene(ctx, MenuKit.capture { MonActions(TeamPage(ctx, NoHost)).menu(st.mon("b1")!!, View(ctx)) })
        }
    }
}
