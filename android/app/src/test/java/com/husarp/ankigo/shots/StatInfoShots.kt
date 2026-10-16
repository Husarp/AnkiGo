package com.husarp.ankigo.shots

import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Stat
import com.husarp.ankigo.ui.DetailParts
import com.husarp.ankigo.ui.MonDetail
import com.husarp.ankigo.ui.StatInfo
import com.husarp.ankigo.ui.TeamPage
import org.junit.Rule
import org.junit.Test

/* R2-10: the stat-name popup and the details with the hint. */
class StatInfoShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val variants = listOf(Variants.PHONE, Variant("float", 340, 480))

    @Test fun popup() {
        paparazzi.shootPage("statinfo-popup", variants) { ctx ->
            val d = DialogShots.capture { DetailParts.showInfo(ctx, StatInfo.EV) } ?: error("no dialog")
            DialogShots.scene(ctx, d)
        }
        paparazzi.shootPage("statinfo-popup-spe", variants) { ctx ->
            val d = DialogShots.capture { DetailParts.showInfo(ctx, StatInfo.of(Stat.SPE)) } ?: error("no dialog")
            DialogShots.scene(ctx, d)
        }
    }

    @Test fun details() {
        paparazzi.shootPage("statinfo-details", variants) { ctx ->
            val gd = GameRepo.data!!
            val d = DialogShots.capture { MonDetail.show(TeamPage(ctx, NoHost), gd, GameRepo.state!!.mon("t1")!!) } ?: error("no dialog")
            DialogShots.scene(ctx, d)
        }
    }
}
