package com.husarp.ankigo.shots

import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R24BShopShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun inlineCoinKeepsItsSourceAspectRatio() {
        val text = CoinText.of(paparazzi.context, 40f, "100") as android.text.Spanned
        val span = text.getSpans(0, 1, android.text.style.DynamicDrawableSpan::class.java).single()
        val bounds = span.drawable.bounds
        assertEquals(12f / 13f, bounds.width().toFloat() / bounds.height(), 0.03f)
        assertTrue(bounds.height() > 0)
    }

    @Test fun nonBattleItemWindowsHaveNoFightSection() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("p360", 360, 780).device(theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.scenario = FakeGame.Scenario.MID_GAME
            FakeGame.withSprites = true
            FakeGame.install(ctx)
            ShopCardKit.install()
            for (item in listOf("tart-apple", "helix-fossil", "amulet-coin")) {
                val (page, stage) = ShopCardKit.page(ctx, CardKeys.bagItem(item), bag = true, setup = { com.husarp.ankigo.GameRepo.state!!.bag[item] = 1 })
                assertNotNull(page.itemWindow)
                assertFalse(ShopCardKit.texts(stage).contains("In a fight"))
                paparazzi.snapshot(stage, "r24b-$item-${theme.name.lowercase()}")
                page.closeCard()
            }
        }
    }

    @Test fun shopAndBuyStayCompactWithoutPriceArrows() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("p360", 360, 780).device(theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.scenario = FakeGame.Scenario.MID_GAME
            FakeGame.withSprites = true
            FakeGame.install(ctx)
            ShopCardKit.install()
            val (page, stage) = ShopCardKit.page(ctx, null)
            val texts = ShopCardKit.texts(stage)
            assertFalse(texts.contains("Always in stock"))
            assertFalse(texts.any { it.contains("↑") || it.contains("↓") })
            assertTrue(texts.contains("Potion"))
            paparazzi.snapshot(stage, "r24b-shop-${theme.name.lowercase()}")
            page.openCard(CardKeys.basic(ShopPlan.Tile.Item("potion")))
            stage.render()
            stage.measure(android.view.View.MeasureSpec.makeMeasureSpec(Ui.dp(ctx, 360), android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(Ui.dp(ctx, 780), android.view.View.MeasureSpec.EXACTLY))
            stage.layout(0, 0, stage.measuredWidth, stage.measuredHeight)
            val buy = ShopCardKit.findText(stage) { it == "Hold to buy" }!!
            val button = buy.parent as LinearLayout
            assertEquals(LinearLayout.HORIZONTAL, button.orientation)
            assertEquals(2, button.childCount)
            assertEquals("56 dp action plus 30 dp hold hint", Ui.dp(ctx, 56) + Ui.dp(ctx, 30), button.height)
            val price = button.getChildAt(1)
            assertTrue("Price ${price.width}/${price.right} must fit Buy ${button.width}/${button.paddingRight}", price.width > 0 && price.right <= button.width - button.paddingRight)
            assertFalse(ShopCardKit.texts(stage).any { it.contains("↑") || it.contains("↓") })
            paparazzi.snapshot(stage, "r24b-buy-${theme.name.lowercase()}")
        }
    }
}
