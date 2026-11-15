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

class R36NShopShots {
    @get:Rule val paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun shopStockAndPurchaseRemainderLightDark() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
            FakeGame.install(ctx); ShopCardKit.install()
            val offers = listOf("super-potion", "fire-stone", "helix-fossil", "great-ball")
            val day = generateSequence(java.time.LocalDate.parse("2026-11-07")) { it.plusWeeks(1) }.first { date ->
                listOf(12, 22).none { hour ->
                    val now = date.atTime(hour, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    Shop.dealFor(GameRepo.data!!, date.toString(), now)?.item in offers
                }
            }.toString()
            val (page, stage) = ShopCardKit.page(ctx, null, day = day, cash = 20000) {
                val st = GameRepo.state!!
                st.shopRefreshes = 0; st.shopPurchases.clear()
                st.shopItems.clear(); st.shopItems += offers
                st.shopTms.clear(); st.shopTms += "thunderbolt"
                assertTrue(Shop.buy(GameRepo.data!!, st, "super-potion", quantity = 3).ok)
            }
            fun texts(v: View) = all(v).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue(texts(stage).containsAll(listOf("2 left", "1 left", "3 left")))
            paparazzi.snapshot(stage, "n-shop-top-${theme.name.lowercase()}")
            val scroll = all(stage).filterIsInstance<ScrollView>().first()
            scroll.scrollTo(0, 480)
            paparazzi.snapshot(stage, "n-shop-stock-${theme.name.lowercase()}")
            scroll.fullScroll(View.FOCUS_DOWN)
            paparazzi.snapshot(stage, "n-shop-bottom-${theme.name.lowercase()}")
            page.openCard(CardKeys.special(ShopPlan.Tile.Item("super-potion"))); stage.render()
            assertTrue("button shows remainder", "2 left" in texts(page.itemWindow!!.view))
            val quantityLabel = all(page.itemWindow!!.view).filterIsInstance<TextView>().first { it.text.toString() == "×1" }
            (quantityLabel.parent as View).performClick(); stage.render()
            assertTrue("quantity cycle skips amounts beyond stock", "×1" in texts(page.itemWindow!!.view))
            assertFalse("cannot select more than remaining stock", "×3" in texts(page.itemWindow!!.view))
            paparazzi.snapshot(stage, "n-shop-buy-${theme.name.lowercase()}")
            assertTrue(Shop.buy(GameRepo.data!!, GameRepo.state!!, "super-potion").ok)
            assertTrue(Shop.buy(GameRepo.data!!, GameRepo.state!!, "super-potion").ok)
            stage.render()
            assertTrue("Sold out" in texts(page.itemWindow!!.view))
            paparazzi.snapshot(stage, "n-shop-sold-out-${theme.name.lowercase()}")
            page.onGone()
        }
    }
}
