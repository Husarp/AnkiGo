package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.ui.CompareFit
import com.husarp.ankigo.ui.CompareGroup
import com.husarp.ankigo.ui.CompareMode
import com.husarp.ankigo.ui.ComparePage
import com.husarp.ankigo.ui.ComparePick
import com.husarp.ankigo.ui.CompareStore
import com.husarp.ankigo.ui.RowChoice
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TeamSection
import com.husarp.ankigo.ui.TileViews
import org.junit.Rule
import org.junit.Test

/*
 * R9-B10: Compare "Fully evolved" at the level the final form is reached ("evolves" sub line), and the Exp. Share mark on the Team
 * rows, the Box tiles and in the legend. PNGs: r9compare-<what>-<size>-<theme>.
 */
class R9CompareShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val phoneSplit = listOf(Variants.PHONE, Variants.SPLIT)

    /** A Lv 5 first form of the 3-stage line (so the final form is first reached at Lv 32). */
    private fun giveBaseForm() {
        val s = GameRepo.state!!
        val m = TestData.mon(GameRepo.data!!, 1, 5, listOf("tackle", "growl"), "r9a")
        s.mons += m; s.seen += 1; s.caught += 1
    }

    private fun compare(ctx: Context, picks: List<ComparePick>, mode: CompareMode, group: CompareGroup): View {
        CompareStore.saveMode(ctx, mode); CompareStore.saveRows(ctx, RowChoice()); CompareStore.saveGroup(ctx, group); CompareStore.saveFit(ctx, CompareFit.AUTO)
        giveBaseForm()
        val p = ComparePage(ctx, NoHost, picks)
        setField(p, "evolved", true)
        return pageScreen(ctx, p)
    }

    private val picks = listOf(ComparePick.Owned("r9a"), ComparePick.Owned("t2"), ComparePick.Owned("t3"), ComparePick.Dex(150))

    @Test fun evolvedVs() = paparazzi.shootPage("r9compare-evolved-vs", phoneSplit) { compare(it, picks, CompareMode.VS_REFERENCE, CompareGroup.STATS) }
    @Test fun evolvedBest() = paparazzi.shootPage("r9compare-evolved-best", listOf(Variants.PHONE)) { compare(it, picks, CompareMode.BEST_OF_ALL, CompareGroup.STATS) }

    private fun holder(uid: String) { GameRepo.state!!.settings.xpShareUid = uid }

    @Test fun teamRows() {
        paparazzi.shootPage("r9compare-xpshare-team", phoneSplit) { ctx ->
            holder("t1")
            pageScreen(ctx, TeamPage(ctx, NoHost).also { it.preset(TeamSection.TEAM, false, null) })
        }
    }

    @Test fun teamRowsOtherHolder() {
        paparazzi.shootPage("r9compare-xpshare-team2", listOf(Variants.PHONE)) { ctx ->
            holder("t3")
            pageScreen(ctx, TeamPage(ctx, NoHost).also { it.preset(TeamSection.TEAM, false, null) })
        }
    }

    @Test fun boxTiles() {
        paparazzi.shootPage("r9compare-xpshare-box", phoneSplit) { ctx ->
            holder("t1") // main + in team + Exp. Share: the three right-hand marks
            giveBaseForm()
            pageScreen(ctx, TeamPage(ctx, NoHost).also { it.preset(TeamSection.BOX, false, null) })
        }
    }

    @Test fun legend() {
        paparazzi.shootPage("r9compare-legend", listOf(Variants.PHONE)) { ctx ->
            val d = DialogShots.capture { TileViews.showLegend(ctx, false) }!!
            DialogShots.scene(ctx, d)
        }
    }
}
