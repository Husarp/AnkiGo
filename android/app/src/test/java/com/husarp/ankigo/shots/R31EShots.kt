package com.husarp.ankigo.shots

import android.view.Gravity
import android.view.MotionEvent
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

class R31EShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun texts(v: View) = all(v).filterIsInstance<TextView>().map { it.text.toString() }
    private fun hold(v: View, label: String) = all(v).single { it.contentDescription == label.replaceFirstChar { c -> c.uppercase() } }
    private fun fillShots(root: View, button: View, name: String) {
        val description = button.contentDescription
        for (level in listOf(0, 5000, 10000)) {
            val current = all(root).single { it.contentDescription == description }
            current.background.level = level
            assertEquals(level, current.background.level)
            paparazzi.snapshot(root, "$name-$level")
        }
    }

    @Test fun basicsFreezeAndDailyBallsAndRepeatedPurchases() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx); ShopCardKit.install()
            val (page, stage) = ShopCardKit.page(ctx, null, cash = 20_000) {
                GameRepo.state!!.shopItems.apply { clear(); add("nest-ball"); add("everstone") }
            }
            val labels = texts(stage)
            assertTrue(labels.indexOf("BASICS") < labels.indexOf("Streak Freeze"))
            assertTrue(labels.indexOf("Streak Freeze") < labels.indexOf("TODAY'S SPECIALS"))
            assertTrue(labels.indexOf("Nest Ball") > labels.indexOf("TODAY'S SPECIALS"))
            paparazzi.snapshot(stage, "e-basics-${theme.name.lowercase()}")
            fillShots(stage, hold(stage, "refresh shop"), "e-refresh-${theme.name.lowercase()}")
            page.openCard("streak-freeze"); stage.render()
            for (owned in 0..2) {
                val window = page.itemWindow!!.view
                assertFalse(texts(window).contains("1 left"))
                assertFalse(all(window).any { it.contentDescription?.startsWith("Quantity ") == true })
                assertTrue(texts(window).contains(Bits.fmt(2000 + owned * 1000)))
                val buy = hold(window, "buy")
                fillShots(stage, buy, "e-freeze-$owned-${theme.name.lowercase()}")
                if (owned == 0) {
                    all(window).filterIsInstance<android.widget.ScrollView>().forEach { it.fullScroll(View.FOCUS_DOWN) }
                    paparazzi.snapshot(stage, "e-freeze-sources-${theme.name.lowercase()}")
                }
                hold(page.itemWindow!!.view, "buy").performLongClick()
                stage.render()
                assertEquals(owned + 1, GameRepo.state!!.count(StreakFreeze.ITEM))
            }
            assertNull(page.itemWindow)
            assertFalse(texts(stage).contains("Streak Freeze"))
            paparazzi.snapshot(stage, "e-freeze-cap-${theme.name.lowercase()}")
            page.onGone()
        }
    }

    @Test fun limitedWindowHasOnlyBuyThenSoldOutAndStockAlignsWithPrice() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme))
            val ctx = Shots.files(paparazzi.context)
            val keys = listOf(CardKeys.WEEKEND, CardKeys.special(ShopPlan.Tile.Item("everstone")), CardKeys.special(ShopPlan.Tile.Tm("thunderbolt")))
            for (key in keys) {
                FakeGame.install(ctx); ShopCardKit.install()
                val (page, stage) = ShopCardKit.page(ctx, null, day = "2026-11-07", cash = 20_000) {
                    val weekend = Shop.weekendFor(GameRepo.data!!, "2026-11-07")!!.item!!
                    GameRepo.state!!.bag[weekend] = 1
                    GameRepo.state!!.main!!.heldItem = weekend
                    GameRepo.state!!.shopItems += "everstone"
                }
                page.onHide() // Keep dated stock fixed while snapshot frames advance time.
                if (key == CardKeys.WEEKEND) {
                    paparazzi.snapshot(stage, "e-limited-stock-${theme.name.lowercase()}")
                    for (stock in all(stage).filterIsInstance<TextView>().filter { it.text.toString() == "1 left" }) {
                        assertEquals(Gravity.END, stock.gravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)
                        val parent = stock.parent as ViewGroup
                        assertEquals(stock.right, parent.getChildAt(parent.childCount - 1).right)
                    }
                }
                page.openCard(key); stage.render()
                val window = requireNotNull(page.itemWindow) {
                    "$key did not open: day=${GameRepo.state!!.day}, stock=${GameRepo.state!!.shopItems}, TMs=${GameRepo.state!!.shopTms}, open=${page.open.key}"
                }.view
                assertFalse(texts(window).contains("1 left"))
                assertFalse(texts(window).any { it == "Take off" || it.startsWith("Give to ") })
                assertFalse(all(window).any { it.contentDescription?.startsWith("Quantity ") == true })
                fillShots(stage, hold(window, "buy"), "e-limited-${key.replace(':', '-')}-${theme.name.lowercase()}")
                hold(page.itemWindow!!.view, "buy").performLongClick(); stage.render()
                assertTrue(texts(page.itemWindow!!.view).contains("Sold out"))
                assertFalse(texts(page.itemWindow!!.view).contains("Hold to buy"))
                paparazzi.snapshot(stage, "e-sold-out-${key.replace(':', '-')}-${theme.name.lowercase()}")
                page.closeCard(); page.onGone()
            }
            FakeGame.install(ctx); ShopCardKit.install()
            val (page, stage) = ShopCardKit.page(ctx, CardKeys.basic(ShopPlan.Tile.Item("potion")))
            page.onHide()
            assertTrue(all(page.itemWindow!!.view).any { it.contentDescription?.startsWith("Quantity ") == true })
            fillShots(stage, hold(page.itemWindow!!.view, "buy"), "e-potion-${theme.name.lowercase()}")
            page.closeCard(); page.onGone()
            val (bag, bagStage) = ShopCardKit.page(ctx, CardKeys.bagItem("amulet-coin"), bag = true) {
                GameRepo.state!!.bag["amulet-coin"] = 1
                GameRepo.state!!.main!!.heldItem = "amulet-coin"
            }
            bag.onHide()
            assertTrue(texts(bag.itemWindow!!.view).contains("Take off"))
            assertTrue(texts(bag.itemWindow!!.view).contains("Give to another Pokémon"))
            paparazzi.snapshot(bagStage, "e-bag-held-actions-${theme.name.lowercase()}")
            bag.closeCard(); bag.onGone()
        }
    }

    @Test fun largeFontStockEndsAtThePriceEdge() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme).copy(fontScale = 2f))
            val ctx = Shots.files(paparazzi.context)
            val column = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                addView(ShopViews.tile(ctx, ShopViews.itemArt(ctx, "everstone", 48), "Everstone",
                    ShopViews.priceNormal(ctx, 3000), "Everstone", stock = "1 left") {})
                addView(ShopViews.dealCard(ctx, "everstone", "Everstone", ShopPlan.Price(2000, 3000, 33), "Ends in 4 h", "Everstone", stock = "1 left") {})
                addView(ShopViews.weekendCard(ctx, ShopViews.itemArt(ctx, "amulet-coin", 48), "Amulet Coin", "Ends in 1 d",
                    ShopViews.priceNormal(ctx, 6000), "Amulet Coin", stock = "1 left") {})
            }
            paparazzi.snapshot(column, "e-stock-large-${theme.name.lowercase()}")
            val stocks = all(column).filterIsInstance<TextView>().filter { it.text.toString() == "1 left" }
            assertEquals(3, stocks.size)
            for (stock in stocks) {
                assertEquals(Gravity.END, stock.gravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)
                val parent = stock.parent as ViewGroup
                assertEquals(stock.right, parent.getChildAt(parent.childCount - 1).right)
            }
        }
    }

    @Test fun pickerAndEvolutionUseAnimatedBackgrounds() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme))
            val ctx = Shots.files(paparazzi.context)
            for (action in listOf("Release", "Teach", "Use", "Revive", "Throw")) {
                val dialog = DialogShots.capture { Pickers.hold(ctx, action, "Confirm $action", action) {} }!!
                fillShots(dialog.content(), dialog.button(AppDialog.BUTTON_POSITIVE)!!, "e-picker-${action.lowercase()}-${theme.name.lowercase()}")
                dialog.dismiss()
            }
            val evolution = Evolution(135, EvoKind.ITEM, item = "thunder-stone")
            val root = TestData.species(133, "Eevee", evolutions = listOf(evolution))
            val data = TestData.data(listOf(root, TestData.species(135, "Jolteon", prevo = 133)))
            val state = TestData.state(data, 133, 20).apply { bag["thunder-stone"] = 1; main!!.bondPoints = 500 }
            val rows = EvolutionRows(ctx, data, EvoLine.of(data, root).root, root, setOf(133, 135), null, state, state.main) {}
            val dialog = DialogShots.capture {
                all(rows).single { it.contentDescription?.startsWith("Explain Use Thunder Stone") == true }.performClick()
            }!!
            fillShots(dialog.content(), dialog.button(AppDialog.BUTTON_POSITIVE)!!, "e-evolve-${theme.name.lowercase()}")
            dialog.dismiss()
        }
    }

    @Test fun realElapsedTimeDrivesFillAndCancellationResetsIt() {
        val button = TextView(Shots.files(paparazzi.context))
        var now = 0L
        var confirmed = 0
        var allowed = true
        val pending = linkedMapOf<Runnable, Long>()
        val timer = object : HoldToConfirm.Timer {
            override fun post(task: Runnable, delayMillis: Long) { pending[task] = now + delayMillis }
            override fun remove(task: Runnable) { pending.remove(task) }
        }
        fun advance(to: Long) {
            now = to
            pending.filterValues { it <= now }.keys.toList().forEach { pending.remove(it); it.run() }
        }
        fun touch(action: Int) {
            val event = MotionEvent.obtain(0, now, action, 10f, 10f, 0)
            button.dispatchTouchEvent(event); event.recycle()
        }
        HoldToConfirm.bind(button, "test", { allowed }, timer, uptime = { now }) { confirmed++ }
        button.layout(0, 0, 200, 120)
        touch(MotionEvent.ACTION_DOWN)
        advance(250); assertEquals(2500, button.background.level)
        advance(500); assertEquals(5000, button.background.level)
        assertEquals(0, confirmed)
        touch(MotionEvent.ACTION_UP)
        assertEquals(0, button.background.level); assertTrue(pending.isEmpty())
        touch(MotionEvent.ACTION_DOWN)
        advance(1250); assertEquals(7500, button.background.level)
        allowed = false; advance(1300)
        assertEquals(0, button.background.level); assertTrue(pending.isEmpty())
        allowed = true; touch(MotionEvent.ACTION_DOWN)
        advance(1800); assertEquals(5000, button.background.level)
        advance(2300); assertEquals(1, confirmed)
        assertEquals(0, button.background.level); assertTrue(pending.isEmpty())
    }
}
