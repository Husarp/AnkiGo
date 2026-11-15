package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Shop
import com.husarp.ankigo.ui.CardKeys
import com.husarp.ankigo.ui.ShopPlan
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R34AShopShots {
    @get:Rule val paparazzi = Shots.rule()

    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()

    @Test fun shopOrderAndWeekendFreeRefreshLightDark() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx); ShopCardKit.install()
            val (page, stage) = ShopCardKit.page(ctx, null, day = "2026-11-07", cash = 20000) {
                val st = GameRepo.state!!
                st.shopRefreshes = 0
                val (items, tms) = Shop.stockFor(GameRepo.data!!, st.day, 1)
                st.shopItems.clear(); st.shopItems += items
                st.shopTms.clear(); st.shopTms += tms
            }
            val texts = all(stage).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue(texts.contains("Refresh · Free"))
            assertTrue(texts.containsAll(listOf("HEALING", "BALLS", "TMS")))
            assertFalse(texts.contains("BASICS"))
            assertTrue(texts.indexOf("BALLS") > texts.indexOf("HEALING"))
            assertTrue(texts.indexOf("TMS") > texts.indexOf("BALLS"))
            val tm = GameRepo.state!!.shopTms.first()
            assertTrue(all(stage).any { it.contentDescription?.contains(String.format(java.util.Locale.US, "%,d", Shop.tmPrice("2026-11-07", tm))) == true })
            paparazzi.snapshot(stage, "a-shop-${theme.name.lowercase()}")
            val scroll = all(stage).filterIsInstance<ScrollView>().first()
            scroll.fullScroll(View.FOCUS_DOWN)
            paparazzi.snapshot(stage, "a-shop-basics-${theme.name.lowercase()}")
            page.openCard(CardKeys.special(ShopPlan.Tile.Tm(tm)))
            stage.render()
            val priceText = String.format(java.util.Locale.US, "%,d", Shop.tmPrice("2026-11-07", tm))
            assertTrue(all(page.itemWindow!!.view).filterIsInstance<TextView>().any { it.text.toString() == priceText })
            page.onGone()
        }
    }
}
