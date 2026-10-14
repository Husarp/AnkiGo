package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.ComparePick
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TeamSection
import org.junit.Rule
import org.junit.Test

/*
 * U-08: the Team tab with its three sections (Team / Box / Pokédex), the team Edit mode, Compare picking (a mix of
 * team, box and Pokédex picks) and the tags in team rows. Light + dark at PHONE, SPLIT and the floating card.
 */
class TeamTabShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val FLOAT = Variant("float", Size.FLOAT.wDp, Size.FLOAT.hDp)
    private val sizes = listOf(Variants.PHONE, Variants.SPLIT, FLOAT)

    /** Tags for the rows: t1 main + holds an item + can evolve (stone), t2 can evolve (level), t3 Exp. Share, t4 new, t5 fainted. */
    private fun tweak() {
        val st = GameRepo.state ?: return
        st.mon("t2")?.level = 33
        st.settings.xpShareUid = "t3"
        st.mon("t4")?.caughtAt = System.currentTimeMillis()
        st.mon("b2")?.heldItem = "everstone"; st.mon("b7")?.heldItem = "amulet-coin"
        st.mon("b3")?.let { it.shiny = true }; st.mon("b5")?.hp = 0
    }

    private fun page(ctx: Context, v: Variant, section: TeamSection, edit: Boolean = false, pick: List<ComparePick>? = null): View {
        tweak()
        val p = TeamPage(ctx, NoHost)
        p.preset(section, edit, pick)
        return if (v === FLOAT) p.root.also { p.onShow() } else pageScreen(ctx, p)
    }

    private val picks = listOf(ComparePick.Owned("t1"), ComparePick.Owned("b3"), ComparePick.Dex(16))

    private fun shoot(name: String, section: TeamSection, edit: Boolean = false, pick: List<ComparePick>? = null,
                      vs: List<Variant> = sizes, scenario: FakeGame.Scenario = FakeGame.Scenario.MID_GAME, withSprites: Boolean = true) {
        for (v in vs) paparazzi.shootPage(name, listOf(v), scenario, withSprites) { page(it, v, section, edit, pick) }
    }

    @Test fun team() = shoot("tt-team", TeamSection.TEAM)
    @Test fun teamEdit() = shoot("tt-team-edit", TeamSection.TEAM, edit = true)
    @Test fun box() = shoot("tt-box", TeamSection.BOX)
    @Test fun dex() = shoot("tt-dex", TeamSection.DEX)

    @Test fun pickTeam() = shoot("tt-pick-team", TeamSection.TEAM, pick = picks)
    @Test fun pickBox() = shoot("tt-pick-box", TeamSection.BOX, pick = picks)
    @Test fun pickDex() = shoot("tt-pick-dex", TeamSection.DEX, pick = picks)
    @Test fun pickOne() = shoot("tt-pick-one", TeamSection.TEAM, pick = listOf(ComparePick.Owned("t2")), vs = listOf(Variants.PHONE))

    @Test fun emptyBox() = shoot("tt-empty-box", TeamSection.BOX, scenario = FakeGame.Scenario.EMPTY)
    @Test fun emptyTeam() = shoot("tt-empty-team", TeamSection.TEAM, scenario = FakeGame.Scenario.EMPTY)
    @Test fun noSprites() = shoot("tt-nosprites", TeamSection.BOX, vs = listOf(Variants.PHONE), withSprites = false)
}
