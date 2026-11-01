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
            val daily = body.parent as View
            val weekendTitle = views.filterIsInstance<TextView>().single { it.text.startsWith("WEEKEND SPECIAL") }
            val weekend = weekendTitle.parent as LinearLayout
            assertEquals("Offer heights", daily.height, weekend.height)
            // 1.0.0: 10 dp top + title + 6 dp gap + 52 dp icon slot + 10 dp bottom.
            assertTrue("Historical deal height is retained as a minimum", daily.height >= label.height + Ui.dp(ctx, 78))
            if (body.getChildAt(1).height == Ui.dp(ctx, 64))
                assertEquals("Historical deal height without extra wrapping", label.height + Ui.dp(ctx, 78), body.height)
            assertEquals("Daily heading centered", body.width / 2f, label.left + label.width / 2f, 1f)
            assertEquals("Weekend heading centered", weekend.width / 2f, weekendTitle.left + weekendTitle.width / 2f, 1f)

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
