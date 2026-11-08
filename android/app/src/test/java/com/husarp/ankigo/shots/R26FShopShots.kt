package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R26FShopShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun quantityTotalsAndCountdowns() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("p360", 360, 780).device(theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.scenario = FakeGame.Scenario.MID_GAME
            FakeGame.install(ctx)
            val data = ShopCardKit.install()
            val day = ShopCardKit.dayWithDeal(data, "potion")
            val (page, stage) = ShopCardKit.page(ctx, null, day = "2026-10-31")
            val texts = ShopCardKit.texts(stage)
            assertTrue(texts.contains("WEEKEND SPECIAL"))
            assertFalse(texts.any { "SAT & SUN" in it || "ends at" in it.lowercase() })
            assertTrue(texts.any { it.matches(Regex("Ends in \\d+ h \\d+ min")) })
            assertTrue(texts.any { it.matches(Regex("New in \\d+ h \\d+ min")) })
            paparazzi.snapshot(stage, "r26f-shop-${theme.name.lowercase()}")
            GameRepo.state!!.day = day
            page.openCard(CardKeys.basic(ShopPlan.Tile.Item("potion")))
            val unit = ShopPlan.price(data, day, "potion").now
            for (q in listOf(1, 3, 5, 1)) {
                stage.render()
                val buy = ShopCardKit.findText(stage) { it == "Buy" }!!
                val amounts = all(buy.parent as View).filterIsInstance<TextView>().map { it.text.toString() }
                assertTrue("Quantity $q: $amounts", Bits.fmt(unit * q) in amounts)
                paparazzi.snapshot(stage, "r26f-quantity-$q-${theme.name.lowercase()}")
                all(stage).single { it.contentDescription == "Quantity $q. Cycle 1, 3, 5" }.performClick()
            }
            page.closeCard()
        }
    }
}
