package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R26HShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun shopPricesReplaceBuyAndWholeRowStillOpensSheet() {
        for (theme in Theme.entries) for (scale in listOf(1f, 2f)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("phone", 360, 800).device(theme).copy(fontScale = scale))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.scenario = FakeGame.Scenario.MID_GAME
            FakeGame.install(ctx); ShopCardKit.install()
            val (_, stage) = ShopCardKit.page(ctx, null, day = "2026-10-31")
            paparazzi.snapshot(stage, "r26h-shop-${theme.name}-$scale")
            assertFalse(all(stage).filterIsInstance<TextView>().any { it.text == "Hold to buy" })
            val weekend = all(stage).filterIsInstance<TextView>().single { it.text == "WEEKEND SPECIAL" }.parent as View
            assertTrue(all(weekend).filterIsInstance<TextView>().any { it.text.startsWith("Ends in ") })
            assertFalse(all(stage).filterIsInstance<TextView>().any { it.text == "Weekend only · until Sunday night" })
            val tile = ShopViews.tile(ctx, ShopViews.itemArt(ctx, "potion", 36), "Potion",
                ShopViews.priceNormal(ctx, 100), "Potion", sub = "Heals 20 HP") { ShopCardKit.windowsShown++ }
            paparazzi.snapshot(tile, "r26h-price-${theme.name}-$scale")
            val row = tile as LinearLayout
            assertTrue(row.getChildAt(2).left >= row.getChildAt(1).right)
            val before = ShopCardKit.windowsShown
            tile.performClick()
            assertEquals(before + 1, ShopCardKit.windowsShown)
        }
    }

    @Test fun statPixelsKeepTheLostTailAndRoundedFillEndsWithoutUnchangedTicks() {
        val ctx = paparazzi.context
        val color = android.graphics.Color.rgb(100, 160, 200)
        fun pixels(stage: Int): android.graphics.Bitmap {
            val view = StatChangeBar(ctx, .48f, stage, color)
            view.layout(0, 0, 200, 28)
            return android.graphics.Bitmap.createBitmap(200, 28, android.graphics.Bitmap.Config.ARGB_8888).also {
                view.draw(android.graphics.Canvas(it))
            }
        }
        val unchanged = pixels(0); val lower = pixels(-1); val higher = pixels(1)
        assertEquals(0, android.graphics.Color.alpha(unchanged.getPixel(96, 1)))
        assertTrue(android.graphics.Color.alpha(lower.getPixel(96, 1)) > 0)
        assertTrue(android.graphics.Color.alpha(higher.getPixel(96, 1)) > 0)
        val top = Ui.dp(ctx, 3)
        assertEquals("Solid lowered fill remains opaque", color, lower.getPixel(56, 14))
        assertNotEquals("Lowered fill has a rounded cap", color, lower.getPixel(63, top))
        assertNotEquals("Raised fill has a rounded cap", higher.getPixel(143, 14), higher.getPixel(143, top))
        for (bar in listOf(unchanged, lower, higher)) {
            assertNotEquals("Left end stays rounded", bar.getPixel(1, 14), bar.getPixel(1, top))
        }
        assertNotEquals("Lost part is preserved", lower.getPixel(80, 14), lower.getPixel(150, 14))
        assertNotEquals("Raised fill continues after the normal tick", higher.getPixel(120, 14), higher.getPixel(180, 14))
        assertEquals("Lost segment meets tick without rounded notch", lower.getPixel(93, 14), lower.getPixel(93, top))
    }

    @Test fun changedAndUnchangedStatBarsAndCornerNotices() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("phone", 360, 700).device(theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            ctx.getSharedPreferences("look", 0).edit().clear().commit()
            val st = GameRepo.state!!
            st.achTiers.clear(); st.badges.clear()
            st.achTiers["answers"] = mutableListOf(123L)
            val page = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
            val screen = pageScreen(ctx, page); call(page, "render")
            paparazzi.snapshot(screen, "r26h-achievements-${theme.name}")
            val tab = all(screen).single { it.contentDescription == "Achievements, new achievements" }
            assertNotNull(tab.foreground)
            val card = all(screen).single { it.contentDescription?.startsWith("Answers given,") == true }
            assertNotNull(card.foreground)
            for (view in listOf(tab, card)) {
                val dot = (view.foreground as android.graphics.drawable.LayerDrawable).getDrawable(0).bounds
                assertEquals(Ui.dp(ctx, 8), dot.top)
                assertEquals(view.width - Ui.dp(ctx, 8), dot.right)
                assertEquals(Ui.dp(ctx, 8), dot.width())
                assertEquals(Ui.dp(ctx, 8), dot.height())
            }
            val stats = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 16), Ui.dp(ctx, 16), Ui.dp(ctx, 16))
                setBackgroundColor(Ui.c(ctx, R.color.ag_mantle))
            }
            for ((name, stage) in listOf("HP unchanged" to 0, "Attack lowered" to -1, "Speed raised" to 1)) {
                stats.addView(Ui.text(ctx, 14f, 800, R.color.ag_text).apply { text = name })
                stats.addView(StatChangeBar(ctx, .48f, stage, Ui.c(ctx, R.color.ag_peach)), LinearLayout.LayoutParams(-1, Ui.dp(ctx, 14)))
            }
            paparazzi.snapshot(stats, "r26h-bars-${theme.name}")
            AchievementNotice.seen(ctx, "answers"); call(page, "render")
            assertNull(all(screen).single { it.contentDescription?.startsWith("Answers given,") == true }.foreground)
            st.achTiers["answers"]!!.add(124L); call(page, "render")
            assertNotNull(all(screen).single { it.contentDescription?.startsWith("Answers given,") == true }.foreground)
        }
    }
}
