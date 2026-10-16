package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.BattlePanelView
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * R2-V03: the red "NEW" sticker on the wild card's corner (no catch line under the card any more).
 * PNGs: r2-newtag-new|caught (full panel, PHONE + SPLIT), r2-newtag-small-new|caught (picture-in-picture, floating battle),
 * r2-newtag-cc1 (compact stat cards).
 */
class NewTagShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private val full = listOf(Size.PHONE, Size.SPLIT)
    private val small = listOf(Size.PIP_169, Size.PIP_169M, Size.PIP, Size.FLOATB, Size.FLOATBATTLE)

    private fun markNew() { val st = GameRepo.state!!; st.caught -= st.battle!!.wild.species }

    private fun markCaught() { val st = GameRepo.state!!; st.caught += st.battle!!.wild.species }

    private fun bare(ctx: Context): View {
        val p = BattlePanelView(ctx)
        p.setCompact(true)
        p.renderAll()
        return p
    }

    @Test fun fullNew() = paparazzi.shootWith("r2-newtag-new", full) { ctx -> val b = BattleShotKit.bound(ctx); markNew(); b.panel!!.renderAll(); b.root }
    @Test fun fullCaught() = paparazzi.shootWith("r2-newtag-caught", full) { ctx -> val b = BattleShotKit.bound(ctx); markCaught(); b.panel!!.renderAll(); b.root }
    @Test fun smallNew() = paparazzi.shootWith("r2-newtag-small-new", small) { ctx -> markNew(); bare(ctx) }
    @Test fun smallCaught() = paparazzi.shootWith("r2-newtag-small-caught", small) { ctx -> markCaught(); bare(ctx) }
    @Test fun compactCards() = paparazzi.shootWith("r2-newtag-cc1", listOf(Size.SPLIT, Size.PIP_169M, Size.FLOATB)) { ctx ->
        AppPrefs.setCompactCards(ctx, true)
        val b = BattleShotKit.bound(ctx); markNew(); b.panel!!.renderAll(); b.root
    }
}
