package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.MonActions
import com.husarp.ankigo.ui.MonDetail
import com.husarp.ankigo.ui.ShopInfo
import com.husarp.ankigo.ui.ShopPage
import com.husarp.ankigo.ui.TeamPage
import org.junit.Rule
import org.junit.Test

/* R3-X2: readable rename field (1), gender sign before the name (2), Shop info popup (3). (The Sync button of the Study bar was removed in Round 9.) */
class R3X2Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val st get() = GameRepo.state!!
    private val gd get() = GameRepo.data!!

    private fun dialog(name: String, open: (Context) -> Unit) {
        paparazzi.shootPage(name, listOf(Variants.PHONE)) { ctx ->
            val d = DialogShots.capture { open(ctx) } ?: error("no dialog was shown")
            DialogShots.scene(ctx, d)
        }
    }

    @Test fun renameField() {
        dialog("r3x2-rename-empty") { ctx -> call(MonActions(TeamPage(ctx, NoHost)), "rename", st.mon("t1")!!, {}) }
        dialog("r3x2-rename-text") { ctx ->
            st.mon("t1")!!.nickname = "Zappy the Great"
            call(MonActions(TeamPage(ctx, NoHost)), "rename", st.mon("t1")!!, {})
        }
    }

    @Test fun genderBeforeTheName() {
        for ((uid, g) in listOf("t1" to 'M', "t2" to 'F', "t3" to 'N')) {
            dialog("r3x2-gender-$g") { ctx ->
                st.mon(uid)!!.gender = g
                MonDetail.show(TeamPage(ctx, NoHost), gd, st.mon(uid)!!)
            }
        }
        dialog("r3x2-gender-shiny-nick") { ctx ->
            st.mon("t4")!!.apply { gender = 'F'; shiny = true; nickname = "Mr. Longest Nick Ever 12345" }
            MonDetail.show(TeamPage(ctx, NoHost), gd, st.mon("t4")!!)
        }
    }

    @Test fun shopInfoPopup() {
        dialog("r3x2-shop-item") { ctx -> ShopInfo.item(ShopPage(ctx, NoHost), "thunder-stone") {} }
        dialog("r3x2-shop-item-poor") { ctx -> st.cash = 100; ShopInfo.item(ShopPage(ctx, NoHost), "lucky-egg") {} }
        dialog("r3x2-shop-tm") { ctx -> ShopInfo.tm(ShopPage(ctx, NoHost), "thunderbolt") {} }
        dialog("r3x2-shop-tm-owned") { ctx -> st.tms += "thunderbolt"; ShopInfo.tm(ShopPage(ctx, NoHost), "thunderbolt") {} }
    }

    @Test fun shopPageStillShowsTheSpecials() {
        paparazzi.shootPage("r3x2-shop", listOf(Variants.PHONE)) { ctx ->
            st.shopTms += listOf("thunderbolt", "bodyslam")
            pageScreen(ctx, ShopPage(ctx, NoHost)) as View
        }
    }
}
