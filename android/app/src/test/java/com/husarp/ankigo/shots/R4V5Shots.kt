package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.ui.BoxFilter
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TeamSection
import com.husarp.ankigo.ui.TileViews
import org.junit.Rule
import org.junit.Test

/*
 * R4-V5 (10A tiles + 6A marks): Box and Pokédex at phone / split / small (3 columns) / tablet (5) / wide tablet (6), a shiny glow tile,
 * NEW / favourite / team / main marks, the new filters, team rows and the legend. PNGs: r4v5-*.
 */
class R4V5Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val SMALL = Variant("small", 320, 600)
    private val TABLET = Variant("tablet", Size.TABLET.wDp, Size.TABLET.hDp)
    private val WIDE = Variant("wide", Size.TABLET_LAND.wDp, Size.TABLET_LAND.hDp)
    private val st get() = GameRepo.state!!
    private val gd get() = GameRepo.data!!

    /** Marks of every kind: a shiny, favourites, a fresh catch (NEW), a held item, a fainted one, a group with a shiny copy. */
    private fun rich() {
        fun more(sp: Int, lv: Int, uid: String, at: Long, shiny: Boolean = false) =
            TestData.mon(gd, sp, lv, listOf("tackle"), uid).also { it.caughtAt = at; it.shiny = shiny; st.mons += it }
        more(25, 31, "x1", 1_760_100_000_000)
        more(25, 12, "x2", 1_760_200_000_000, shiny = true)
        more(16, 30, "x3", System.currentTimeMillis())
        more(54, 22, "x5", System.currentTimeMillis()).also { it.favourite = true; it.shiny = true }
        st.mon("t1")!!.favourite = true
        st.mon("t2")?.shiny = true
        st.mon("t3")?.caughtAt = System.currentTimeMillis()
        st.mon("b3")!!.also { it.favourite = true; it.shiny = true }
        st.mon("b7")!!.favourite = true
        st.mon("b2")!!.heldItem = "everstone"
        st.mon("b5")!!.hp = 0
    }

    private fun page(ctx: Context, section: TeamSection, filter: BoxFilter = BoxFilter.ALL, pick: Boolean = false): View {
        rich()
        val p = TeamPage(ctx, NoHost)
        p.preset(section, filter = filter, group = true)
        return pageScreen(ctx, p)
    }

    private val sizes = listOf(Variants.PHONE, Variants.SPLIT, SMALL, TABLET, WIDE)

    @Test fun box() = paparazzi.shootPage("r4v5-box", sizes) { page(it, TeamSection.BOX) }
    @Test fun dex() = paparazzi.shootPage("r4v5-dex", sizes) { page(it, TeamSection.DEX) }
    @Test fun team() = paparazzi.shootPage("r4v5-team", listOf(Variants.PHONE, SMALL)) { page(it, TeamSection.TEAM) }
    @Test fun holdsAnItem() = paparazzi.shootPage("r4v5-box-holds", listOf(Variants.PHONE)) { page(it, TeamSection.BOX, BoxFilter.HOLDS_ITEM) }
    @Test fun canEvolve() = paparazzi.shootPage("r4v5-box-evolve", listOf(Variants.PHONE)) { page(it, TeamSection.BOX, BoxFilter.CAN_EVOLVE) }

    @Test fun legend() {
        for (dex in listOf(false, true)) paparazzi.shootPage("r4v5-legend${if (dex) "-dex" else ""}", listOf(Variants.PHONE, Variants.SPLIT)) { ctx ->
            val d = DialogShots.capture { TileViews.showLegend(ctx, dex) }!!
            DialogShots.scene(ctx, d)
        }
    }
}
