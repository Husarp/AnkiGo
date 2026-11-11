package com.husarp.ankigo.shots

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.*
import com.husarp.ankigo.help.GameGuide
import com.husarp.ankigo.help.HelpRoute
import com.husarp.ankigo.help.HelpScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R33BShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun quantityMatchesBuyAtEveryQuantityAndFontSize() {
        for (theme in Theme.entries) for (font in listOf(1f, 2f)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme).copy(fontScale = font))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx); ShopCardKit.install()
            val (page, stage) = ShopCardKit.page(ctx, CardKeys.basic(ShopPlan.Tile.Item("potion")), cash = 20_000)
            page.onHide()
            repeat(3) { index ->
                paparazzi.snapshot(stage, "b-buy-$index-font$font-${theme.name.lowercase()}")
                // Snapshot callbacks can redraw the item actions; lay out the current views before measuring them.
                val width = ctx.resources.displayMetrics.widthPixels
                val height = ctx.resources.displayMetrics.heightPixels
                stage.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                stage.layout(0, 0, width, height)
                val window = page.itemWindow!!.view
                val buy = all(window).single { it.contentDescription == "Buy" }
                val quantity = all(window).single { it.contentDescription?.startsWith("Quantity ") == true }
                assertTrue(buy.height > 0)
                assertEquals(buy.height, quantity.height)
                assertEquals("×${listOf(1, 3, 5)[index]}", (quantity as ViewGroup).let { (it.getChildAt(0) as TextView).text.toString() })
                quantity.performClick(); stage.render()
            }
            page.closeCard(); page.onGone()
        }
    }

    @Test fun sortMigrationAndMenus() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("sort", 360, 900).device(theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val prefs = ctx.getSharedPreferences("r33b", Context.MODE_PRIVATE)
            for (where in SortPrefs.Where.entries) {
                prefs.edit().putString(where.key, "key=VS_WILD;basis=BASE;desc=1").commit()
                assertEquals(SortSpec.DEFAULT, SortPrefs.load(prefs, where))
            }
            val box = TeamPage(ctx, NoHost)
            val picker = TeamPage(ctx, NoHost).apply { preset(TeamSection.BOX, pick = emptyList()) }
            val dex = DexPage(ctx, NoHost)
            for ((name, menu) in listOf("box" to box.sortSheet(), "picker" to picker.sortSheet(), "dex" to dex.sortSheet())) {
                val sheet = menu.build(Ui.dp(ctx, 650))
                val labels = all(sheet).filterIsInstance<TextView>().map { it.text.toString() }
                assertFalse(labels.any { it.contains("Best vs", ignoreCase = true) })
                assertTrue(labels.contains("Pokédex no."))
                paparazzi.snapshot(sheet, "b-sort-$name-${theme.name.lowercase()}")
            }
        }
    }

    @Test fun itemsGuide() = paparazzi.shootPage("b-guide-items", listOf(Variant("guide", 412, 1800))) { ctx ->
        assertTrue(GameGuide.chapter(GameGuide.ITEMS)!!.visuals.any { it.caption == "Worn" })
        pageScreen(ctx, HelpScreen(ctx, NoHost, HelpRoute.Guide(GameGuide.ITEMS), false))
    }

    @Test fun retractionCancelsConfirmationAndCanRestart() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme))
            val ctx = Shots.files(paparazzi.context)
            val button = TextView(ctx).apply { text = "Release" }
            var now = 0L
            var count = 0
            val pending = linkedMapOf<Runnable, Long>()
            val timer = object : HoldToConfirm.Timer {
                override fun post(task: Runnable, delayMillis: Long) { pending[task] = now + delayMillis }
                override fun remove(task: Runnable) { pending.remove(task) }
            }
            fun advance(ms: Long) {
                now += ms
                pending.filterValues { it <= now }.keys.toList().forEach { pending.remove(it); it.run() }
            }
            fun touch(action: Int, x: Float = 10f) {
                val e = MotionEvent.obtain(0, now, action, x, 10f, 0)
                button.dispatchTouchEvent(e); e.recycle()
            }
            HoldToConfirm.bind(button, "release", timer = timer, uptime = { now }) { count++ }
            button.layout(0, 0, 300, 100)
            for (cancel in listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN)) {
                touch(MotionEvent.ACTION_DOWN); advance(600)
                assertEquals(6000, button.background.level)
                touch(cancel, if (cancel == MotionEvent.ACTION_MOVE) -1f else 10f)
                assertEquals(6000, button.background.level)
                advance(100); assertEquals(3000, button.background.level)
                paparazzi.snapshot(button, "b-retract-$cancel-${theme.name.lowercase()}")
                advance(100); assertEquals(0, button.background.level)
                assertTrue(pending.isEmpty()); assertEquals(0, count)
            }
            touch(MotionEvent.ACTION_DOWN); advance(600); touch(MotionEvent.ACTION_UP); advance(50)
            touch(MotionEvent.ACTION_DOWN); advance(999); assertEquals(0, count)
            advance(1); assertEquals(1, count); assertTrue(pending.isEmpty())
            button.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            advance(300)
            button.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            assertEquals(3000, button.background.level)
            advance(50); assertEquals(1500, button.background.level)
            advance(50); assertEquals(0, button.background.level); assertEquals(1, count)
        }
    }
}
