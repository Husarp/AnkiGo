package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R25LShopShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun shopOfferGeometryAndWrappedTypeChips() {
        for (theme in Theme.entries) for ((width, scale) in listOf(320 to 1f, 360 to 1f, 412 to 1f, 360 to 2f)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("p$width", width, 780).device(theme).copy(fontScale = scale))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.scenario = FakeGame.Scenario.MID_GAME
            FakeGame.withSprites = true
            FakeGame.install(ctx)
            ShopCardKit.install()
            val (_, stage) = ShopCardKit.page(ctx, null, day = "2026-10-31")
            paparazzi.snapshot(stage, "r25l-shop-$width-$scale-${theme.name.lowercase()}")
            val views = all(stage)
            val dailyTitle = views.filterIsInstance<TextView>().single { it.text.startsWith(ShopCardKit.dealHeading()) }
            val label = dailyTitle.parent as LinearLayout
            val body = label.parent as LinearLayout
            val daily = body
            val weekendTitle = views.filterIsInstance<TextView>().single { it.text.startsWith("WEEKEND SPECIAL") }
            val weekend = weekendTitle.parent.parent as LinearLayout
            for (offer in listOf(daily, weekend)) {
                assertTrue("Separated header and body", offer.childCount >= 3)
                val header = offer.getChildAt(0)
                val content = offer.getChildAt(2)
                assertTrue("Body follows title bar", content.top >= header.bottom)
                assertTrue("Frame encloses content", content.bottom <= offer.height - offer.paddingBottom)
                for (text in all(offer).filterIsInstance<TextView>()) {
                    val layout = text.layout ?: continue
                    assertTrue("Text fits: ${text.text}", layout.getLineBottom(layout.lineCount - 1) <= text.height - text.paddingTop - text.paddingBottom)
                    assertTrue("Text has width: ${text.text}", text.width > 0)
                }
            }

            val chip = Ui.typeChip(ctx, com.husarp.ankigo.game.Type.FIRE)
            val tile = ShopViews.tile(ctx, ShopViews.itemArt(ctx, "potion", 64), "TM · Ember",
                ShopViews.owned(ctx), "Test TM", subView = chip) {}
            tile.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(ctx, width - 24), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            tile.layout(0, 0, tile.measuredWidth, tile.measuredHeight)
            assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, chip.layoutParams.width)
            assertTrue("Chip hugs its text", chip.width < (chip.parent as View).width)
            paparazzi.snapshot(tile, "r25l-tm-$width-$scale-${theme.name.lowercase()}")
        }
    }
}
