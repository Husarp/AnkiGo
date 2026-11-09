package com.husarp.ankigo.shots

import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.ItemInfo
import com.husarp.ankigo.game.Shop
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R32QShopShots {
    @get:Rule val paparazzi = Shots.rule()

    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()

    @Test fun weekendDescriptionAndShopWithoutHelpLightDark() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx); ShopCardKit.install()
            val (page, stage) = ShopCardKit.page(ctx, null, day = "2026-11-07", cash = 20_000)
            page.onHide()
            val item = Shop.weekendFor(GameRepo.data!!, "2026-11-07")!!.item!!
            val expected = ItemInfo.plain(ItemInfo.short(GameRepo.data!!, item, GameRepo.state!!.caught))
            val weekend = all(stage).single { it.contentDescription?.startsWith("WEEKEND SPECIAL,") == true }
            val detail = all(weekend).filterIsInstance<TextView>().single { it.text.toString() == expected }
            assertTrue(expected.isNotBlank())
            assertEquals(2, detail.maxLines)
            assertEquals(TextUtils.TruncateAt.END, detail.ellipsize)
            assertFalse(all(stage).filterIsInstance<TextView>().any { it.text.toString() == "Weekend exclusive" })
            assertFalse(all(stage).any { it.contentDescription == "Help: how money works" })
            val stock = all(weekend).filterIsInstance<TextView>().single { it.text.toString() == "1 left" }
            assertEquals(Gravity.END, stock.gravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)
            paparazzi.snapshot(stage, "q-shop-weekend-${theme.name.lowercase()}")
            val column = stock.parent as ViewGroup
            assertEquals(stock.right, column.getChildAt(column.childCount - 1).right)
            page.onGone()

            val bag = ShopPage(ctx, NoHost, bagOnly = true)
            assertTrue(all(pageScreen(ctx, bag)).any { it.contentDescription == "Help: how money works" })
            bag.onGone()
        }
    }
}
