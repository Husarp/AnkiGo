package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R24EShopBagShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun setup() = Shots.files(paparazzi.context).also {
        FakeGame.scenario = FakeGame.Scenario.MID_GAME
        FakeGame.withSprites = true
        FakeGame.install(it)
        ShopCardKit.install()
    }
    @Test fun shopCardsAndBagFilters() {
        for (theme in Theme.entries) for ((width, scale) in listOf(320 to 1f, 360 to 1f, 412 to 1f, 360 to 2f)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("p$width", width, 780).device(theme).copy(fontScale = scale))
            val ctx = setup()
            val (page, stage) = ShopCardKit.page(ctx, null, day = "2026-10-31")
            paparazzi.snapshot(stage, "r24e-shop-$width-$scale-${theme.name.lowercase()}")
            assertFalse("Shop tiles no longer have separate Buy controls", all(stage).any { it.contentDescription == "Buy Potion" })
            val title = all(stage).filterIsInstance<android.widget.TextView>().first { it.text.toString() == "Potion" }
            var card = title.parent as View
            while (!card.isClickable && card.parent is View) card = card.parent as View
            assertTrue(card.isClickable)
            assertTrue(card.height >= Ui.dp(ctx, 48))
            assertTrue(card.width >= Ui.dp(ctx, width - 30))
            card.performClick(); stage.render()
            assertNotNull(page.itemWindow)
            page.closeCard()
            page.openBag(); stage.render()
            paparazzi.snapshot(stage, "r24e-bag-$width-$scale-${theme.name.lowercase()}")
            assertFalse(all(stage).any { it.tag == "bag-categories" })
            val results = all(stage).single { it.tag == "bag-results" }
            val body = all(stage).single { it.tag == "bag-body" }
            assertEquals(body.width, results.width)
            ShopCardKit.findDesc(stage, "Expand categories")!!.performClick(); stage.render()
            paparazzi.snapshot(stage, "r24e-bag-open-$width-$scale-${theme.name.lowercase()}")
            val list = all(stage).single { it.tag == "bag-categories" } as LinearLayout
            assertEquals(9, list.childCount)
            assertEquals("All items category", list.getChildAt(0).contentDescription)
            for (i in 0 until list.childCount) {
                val control = list.getChildAt(i)
                assertTrue(control.width >= Ui.dp(ctx, 48))
                assertTrue(control.height >= Ui.dp(ctx, 48))
                assertTrue(control.right <= list.width)
            }
            ShopCardKit.findDesc(stage, "Balls category")!!.performClick(); stage.render()
            assertTrue(ShopCardKit.findDesc(stage, "Balls category")!!.isSelected)
            assertFalse(ShopCardKit.texts(stage).contains("Potion"))
            ShopCardKit.findDesc(stage, "Worn")!!.performClick(); stage.render()
            assertFalse(ShopCardKit.texts(stage).contains("Poké Ball"))
            ShopCardKit.findDesc(stage, "All items category")!!.performClick(); stage.render()
            ShopCardKit.findDesc(stage, "All")!!.performClick(); stage.render()
            assertTrue(ShopCardKit.texts(stage).contains("Potion"))
        }
    }
}
