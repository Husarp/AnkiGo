package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R30OShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun texts(v: View) = all(v).filterIsInstance<TextView>().map { it.text.toString() }

    @Test fun freezeShopAndSavedMessage() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx); ShopCardKit.install()
            val (page, stage) = ShopCardKit.page(ctx, null, cash = 12_000) { GameRepo.state!!.bag[StreakFreeze.ITEM] = 1 }
            assertTrue(texts(stage).contains("Streak Freeze"))
            paparazzi.snapshot(stage, "o-freeze-shop-${theme.name.lowercase()}")
            page.openCard("streak-freeze"); stage.render()
            assertTrue(texts(stage).contains("1 left"))
            assertFalse(all(page.itemWindow!!.view).any { it.contentDescription?.startsWith("Quantity ") == true })
            paparazzi.snapshot(stage, "o-freeze-buy-${theme.name.lowercase()}")
            page.closeCard()
            GameRepo.state!!.bag[StreakFreeze.ITEM] = 3
            stage.render()
            assertFalse(texts(stage).contains("Streak Freeze"))
            paparazzi.snapshot(stage, "o-freeze-cap-${theme.name.lowercase()}")
            page.onGone()
            val saved = GameEvent.StreakSaved(17, 2, 1, "2026-11-03", "2026-11-04")
            val message = AchievementPopup.streakLayer(ctx, saved) {}
            assertTrue(texts(message).contains("Streak saved · 17 days"))
            assertTrue(texts(message).contains("Used 2 freezes · 1 left"))
            paparazzi.snapshot(message, "o-streak-saved-${theme.name.lowercase()}")
        }
    }

    @Test fun limitedCardsShowStockAndNoQuantityAndGreyAfterBuying() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx); val data = ShopCardKit.install()
            val day = "2026-11-07"
            val item = Shop.weekendFor(data, day)!!.item!!
            val (page, stage) = ShopCardKit.page(ctx, null, day = day, cash = 20_000)
            paparazzi.snapshot(stage, "o-weekend-stock-${theme.name.lowercase()}")
            page.openCard(CardKeys.WEEKEND); stage.render()
            assertFalse(all(page.itemWindow!!.view).any { it.contentDescription?.startsWith("Quantity ") == true })
            assertTrue(Shop.buy(data, GameRepo.state!!, item).ok)
            stage.render()
            assertTrue(texts(page.itemWindow!!.view).contains("Sold out"))
            assertFalse(texts(page.itemWindow!!.view).contains("Buy"))
            paparazzi.snapshot(stage, "o-weekend-soldout-card-${theme.name.lowercase()}")
            page.closeCard(); stage.render()
            assertTrue(all(stage).any { it.alpha == 0.45f && it.contentDescription?.contains("Sold out") == true })
            paparazzi.snapshot(stage, "o-weekend-soldout-shop-${theme.name.lowercase()}")
            page.openCard(CardKeys.basic(ShopPlan.Tile.Item("potion"))); stage.render()
            assertTrue(all(page.itemWindow!!.view).any { it.contentDescription?.startsWith("Quantity ") == true })
            page.closeCard(); page.onGone()
        }
    }

    @Test fun importSettingsRetainsImportWithoutOutgoingButton() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val page = SettingsPage(ShotCtx(ctx), NoHost)
            val method = SettingsPage::class.java.getDeclaredMethod("ankimonCard").apply { isAccessible = true }
            val card = method.invoke(page) as View
            assertTrue(texts(card).contains("Import Ankimon save (.db)…"))
            assertFalse(texts(card).any { it.contains("Export for Ankimon") })
            paparazzi.snapshot(card, "o-ankimon-import-${theme.name.lowercase()}")
            page.onGone()
        }
    }
}
