package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.ShopPlan
import com.husarp.ankigo.ui.ShopViews
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R23KPriceShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun texts(v: View): List<TextView> = if (v is TextView) listOf(v) else
        if (v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList()

    @Test fun purchaseCardKeepsDailyPriceAndQuantityConfirmation() {
        paparazzi.shoot("r1-purchase-card", sizes = listOf(Size.PHONE)) { ctx ->
            ShopCardKit.install()
            val day = (0L..100L).map { java.time.LocalDate.parse(ShopCardKit.WEEKDAY).plusDays(it).toString() }
                .first { "great-ball" in com.husarp.ankigo.game.Shop.dailyBalls(it) && com.husarp.ankigo.game.Shop.dailyPrice(com.husarp.ankigo.GameRepo.data!!, it, "great-ball") != com.husarp.ankigo.GameRepo.data!!.price("great-ball") }
            val (page, stage) = ShopCardKit.page(ctx, com.husarp.ankigo.ui.CardKeys.special(ShopPlan.Tile.Item("great-ball")), day = day)
            val price = ShopPlan.price(page.data, day, "great-ball")
            assertFalse(texts(page.itemWindow!!.view).any { it.text.toString().contains("↑") || it.text.toString().contains("↓") })
            val buy = texts(page.itemWindow!!.view).first { it.text == "Buy" }
            buy.parent.let { it as View }.performClick()
            stage.render()
            assertTrue(ShopCardKit.texts(stage).contains("Buy"))
            assertTrue(ShopCardKit.texts(stage).contains("×1"))
            stage
        }
    }

    @Test fun dailyPricesAndDealRenderWithoutArrows() {
        val ctx = paparazzi.context
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(ctx, R.color.ag_card))
        }
        panel.addView(ShopViews.price(ctx, ShopPlan.Price(540)))
        panel.addView(ShopViews.price(ctx, ShopPlan.Price(660)))
        panel.addView(ShopViews.price(ctx, ShopPlan.Price(378, was = 540)))
        assertFalse(texts(panel).any { it.text.toString().contains("↑") || it.text.toString().contains("↓") })
        val fixed = ShopViews.price(ctx, ShopPlan.tmPrice())
        assertFalse(texts(fixed).any { it.text.toString().contains("↑") || it.text.toString().contains("↓") })
        panel.addView(fixed)
        paparazzi.snapshot(panel, name = "r23k-daily-prices")
    }
}
