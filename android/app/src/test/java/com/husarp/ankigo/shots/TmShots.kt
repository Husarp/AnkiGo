package com.husarp.ankigo.shots

import android.content.Context
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.Bits
import com.husarp.ankigo.ui.MonActions
import com.husarp.ankigo.ui.Pickers
import com.husarp.ankigo.ui.ShopPage
import com.husarp.ankigo.ui.TeamPage
import org.junit.Rule
import org.junit.Test

/* T41: TM shop rows (owned / buyable / too expensive), Bag TM rows, and the three TM dialogs. */
class TmShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val st get() = GameRepo.state!!
    private val gd get() = GameRepo.data!!

    private fun tmState() {
        st.shopTms.clear(); st.shopTms += listOf("thunderbolt", "watergun", "earthquake", "shadowball", "bodyslam")
        st.tms.clear(); st.tms += listOf("bodyslam", "ember", "vinewhip")
    }

    private fun dialog(name: String, open: (Context) -> Unit) {
        paparazzi.shootPage(name) { ctx ->
            tmState()
            val d = DialogShots.capture { open(ctx) } ?: error("no dialog was shown")
            DialogShots.scene(ctx, d)
        }
    }

    @Test fun tmScreens() {
        paparazzi.shootPage("tm-shop") { ctx -> tmState(); pageScreen(ctx, ShopPage(ctx, NoHost)) }
        paparazzi.shootPage("tm-shop-poor") { ctx -> tmState(); st.cash = 300; pageScreen(ctx, ShopPage(ctx, NoHost)) }
        paparazzi.shootPage("tm-bag") { ctx ->
            tmState()
            val p = ShopPage(ctx, NoHost); setField(p, "showBag", true); pageScreen(ctx, p)
        }
        dialog("tm-dlg-moves") { ctx -> Pickers.pickMoveId(ctx, "Teach Sparkmouse", gd, listOf("bodyslam", "ember", "vinewhip")) {} }
        dialog("tm-dlg-forget") { ctx ->
            val mon = st.mon("t1")!!; mon.moves += "growl"
            MonActions(TeamPage(ctx, NoHost)).teach(mon, "bodyslam")
        }
        dialog("tm-dlg-mon") { ctx ->
            Pickers.pickMon(ctx, "Teach TM Body Slam to…", gd, st.mons.take(5), { "${it.moves.size}/4 moves" }) {}
        }
    }
}
