package com.husarp.ankigo.shots

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test

class R32CShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun bounds(v: View): Rect = Rect().also { v.getGlobalVisibleRect(it) }
    @After fun clean() { Pickers.interceptApp = null; ItemWindow.interceptShow = null }

    @Test fun bagOverlayLeavesTilesStillAndOutsideTapsOnlyDismiss() {
        for (theme in Theme.entries) for (width in listOf(320, 360)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("c$width", width, 780).device(theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx); ShopCardKit.install()
            GameRepo.state!!.bag["poke-ball"] = 12345
            val page = ShopPage(ctx, NoHost, bagOnly = true)
            val stage = ShopCardKit.Stage(ctx, page, page.root).render()
            val suffix = "$width-${theme.name.lowercase()}"
            paparazzi.snapshot(stage, "c-bag-closed-$suffix")
            val count = all(stage).filterIsInstance<TextView>().single { it.text == "×12345" }
            assertTrue(count.textSize / ctx.resources.displayMetrics.scaledDensity >= 18f)
            assertTrue(count.width >= count.paint.measureText(count.text.toString()))
            val tile = count.parent as View
            val before = bounds(tile)
            all(stage).single { it.contentDescription == "Expand categories" }.performClick(); stage.render()
            paparazzi.snapshot(stage, "c-bag-overlay-$suffix")
            val afterCount = all(stage).filterIsInstance<TextView>().single { it.text == "×12345" }
            assertEquals(before, bounds(afterCount.parent as View))
            assertEquals(all(stage).single { it.tag == "bag-body" }.width, all(stage).single { it.tag == "bag-results" }.width)
            val drawer = all(stage).single { it.tag == "bag-category-drawer" }
            val drawerBounds = bounds(drawer)
            assertTrue(drawerBounds.height() > 0)
            val outside = all(stage).single { it.contentDescription == "Close categories" }
            assertEquals(bounds(page.root), bounds(outside))
            assertTrue(outside.isClickable)
            // Snapshots detach Views; activate the real click listener without posting a detached View's touch callback.
            outside.performClick()
            stage.render(); paparazzi.snapshot(stage, "c-bag-dismissed-$suffix")
            assertFalse(all(stage).any { it.tag == "bag-categories" })
            assertNull(page.itemWindow)
            all(stage).single { it.contentDescription == "Expand categories" }.performClick(); stage.render()
            page.onTabReselected(); stage.render()
            assertFalse(all(stage).any { it.tag == "bag-categories" })
            all(stage).single { it.contentDescription == "Expand categories" }.performClick(); stage.render()
            page.onHide(); stage.render()
            assertFalse(all(stage).any { it.tag == "bag-categories" })
            page.onGone()
        }
    }

    @Test fun floatingBattleBagSharesOverlayAndCounts() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("c360", 360, 780).device(theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx); ShopCardKit.install()
            GameRepo.state!!.bag["poke-ball"] = 12
            var dialog: AppDialog? = null
            Pickers.interceptApp = { dialog = it }
            BattleBag.show(ctx) {}
            val body = dialog!!.content()
            val page = all(body).mapNotNull { it.context as? ShopPage }.first()
            call(page, "render")
            paparazzi.snapshot(body, "c-battle-bag-closed-${theme.name.lowercase()}")
            val before = bounds(all(body).single { it.tag == "bag-results" })
            all(body).single { it.contentDescription == "Expand categories" }.performClick(); call(page, "render")
            paparazzi.snapshot(body, "c-battle-bag-overlay-${theme.name.lowercase()}")
            assertEquals(before, bounds(all(body).single { it.tag == "bag-results" }))
            all(body).single { it.contentDescription == "Close categories" }.performClick(); call(page, "render")
            assertFalse(all(body).any { it.tag == "bag-categories" })
            dialog!!.dismiss(); page.onHide(); page.onGone()
        }
    }

    @Test fun countsLeaveRoomForArtAtLargeFontSize() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("c320", 320, 780).device(theme).copy(fontScale = 2f))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx); ShopCardKit.install()
            GameRepo.state!!.bag["poke-ball"] = 12
            val page = ShopPage(ctx, NoHost, bagOnly = true)
            val stage = ShopCardKit.Stage(ctx, page, page.root).render()
            paparazzi.snapshot(stage, "c-bag-count-large-font-${theme.name.lowercase()}")
            val count = all(stage).filterIsInstance<TextView>().single { it.text == "×12" }
            val tile = count.parent as ViewGroup
            val body = tile.getChildAt(0) as ViewGroup
            val art = body.getChildAt(0)
            assertTrue(count.bottom <= body.top + art.top)
            assertTrue(count.width >= count.paint.measureText(count.text.toString()))
            page.onHide(); page.onGone()
        }
    }

    @Test fun shopShowsPokeBallInBasicsInBothThemes() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("c360", 360, 780).device(theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx); ShopCardKit.install()
            val (page, stage) = ShopCardKit.page(ctx, null)
            paparazzi.snapshot(stage, "c-shop-basics-${theme.name.lowercase()}")
            assertTrue(ShopCardKit.texts(stage).contains("Poké Ball"))
            page.openCard(CardKeys.basic(ShopPlan.Tile.Item("poke-ball"))); stage.render()
            assertNotNull(page.itemWindow)
            page.closeCard(); page.onHide(); page.onGone()
        }
    }
}
