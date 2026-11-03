package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.game.Shop
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R29GDiscountShots {
    @get:Rule val paparazzi = Shots.rule()

    private fun texts(view: View): List<TextView> =
        (if (view is TextView) listOf(view) else emptyList()) +
            (if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList())

    @Test fun discountPillIsLeftOfThePriceAndAbsentFromTheEffectInBothThemes() {
        for (percent in listOf(20, 40)) for (night in listOf(false, true)) {
            paparazzi.shoot("r29g-${if (night) "night" else "day"}-$percent", listOf(Size.SMALL)) { ctx ->
                var tapped = false
                val price = ShopPlan.Price(Shop.dealPrice(700, percent), 700, percent)
                val card = ShopViews.dealCard(ctx, "super-potion", "Super Potion", price,
                    "Ends in 2 h 53 min", ShopPlan.describe("Super Potion", price),
                    "Restores 60 HP", night) { tapped = true }
                val labels = texts(card)
                assertEquals(1, labels.count { it.text.toString() == "−$percent %" })
                assertTrue(labels.any { it.text.toString() == "Restores 60 HP" })
                assertFalse(labels.any { it.text.contains("% ·") })
                val pill = labels.single { it.text.toString() == "−$percent %" }
                val amount = labels.single { it.text.toString() == price.now.toString() }
                val priceRow = pill.parent as LinearLayout
                assertEquals(LinearLayout.HORIZONTAL, priceRow.orientation)
                assertSame(priceRow, amount.parent.parent.parent)
                assertEquals(0, priceRow.indexOfChild(pill))
                assertEquals(1, priceRow.indexOfChild(amount.parent.parent as View))
                val pillLayout = pill.layoutParams as LinearLayout.LayoutParams
                assertEquals(Ui.dp(ctx, 5), pillLayout.marginEnd)
                card.performClick()
                assertTrue(tapped)
                LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12), 0)
                    addView(card, LinearLayout.LayoutParams(-1, -2))
                }
            }
        }
    }
}
